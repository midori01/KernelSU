#include <linux/compiler_types.h>
#include <linux/preempt.h>
#include <linux/printk.h>
#include <linux/mm.h>
#include <linux/pgtable.h>
#include <linux/uaccess.h>
#include <asm/current.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/types.h>
#include <linux/version.h>
#include <linux/sched/task_stack.h>
#include <linux/ptrace.h>
#include <linux/namei.h>
#include <linux/minmax.h>
#include <linux/fs_struct.h>
#include <linux/susfs_def.h>
#include "selinux/selinux.h"
#include "objsec.h"

#include "arch.h"
#include "policy/allowlist.h"
#include "policy/feature.h"
#include "klog.h" // IWYU pragma: keep
#include "runtime/ksud.h"
#include "feature/sucompat.h"
#include "feature/adb_root.h"
#include "policy/app_profile.h"
#include "hook/syscall_hook.h"
#include "sulog/event.h"

#define SU_PATH "/system/bin/su"
#define SH_PATH "/system/bin/sh"

extern void write_sulog(uint8_t sym);
static const char sh_path[] = SH_PATH;
static const char su_path[] = SU_PATH;
static const char ksud_path[] = KSUD_PATH;

DEFINE_STATIC_KEY_TRUE(ksu_su_compat_enabled);

static int su_compat_feature_get(u64 *value)
{
    if (static_key_enabled(&ksu_su_compat_enabled))
        *value = 1;
    else
        *value = 0;
    return 0;
}

static int su_compat_feature_set(u64 value)
{
    bool enable = value != 0;
    if (enable)
        static_branch_enable(&ksu_su_compat_enabled);
    else
        static_branch_disable(&ksu_su_compat_enabled);
    pr_info("su_compat: set to %d\n", enable);
    return 0;
}

static const struct ksu_feature_handler su_compat_handler = {
    .feature_id = KSU_FEATURE_SU_COMPAT,
    .name = "su_compat",
    .get_handler = su_compat_feature_get,
    .set_handler = su_compat_feature_set,
};

static void __user *userspace_stack_buffer(const void *d, size_t len)
{
    // To avoid having to mmap a page in userspace, just write below the stack
    // pointer.
    char __user *p = (void __user *)current_user_stack_pointer() - len;

    return copy_to_user(p, d, len) ? NULL : p;
}

static char __user *sh_user_path(void)
{
    static const char sh_path[] = "/system/bin/sh";
    return userspace_stack_buffer(sh_path, sizeof(sh_path));
}

static char __user *ksud_user_path(void)
{
    static const char ksud_path[] = KSUD_PATH;

    return userspace_stack_buffer(ksud_path, sizeof(ksud_path));
}

static const char __user *get_user_arg_ptr(struct user_arg_ptr argv, int nr)
{
	const char __user *native;

#ifdef CONFIG_COMPAT
	if (unlikely(argv.is_compat)) {
		compat_uptr_t compat;
		if (get_user(compat, argv.ptr.compat + nr))
			return ERR_PTR(-EFAULT);
		return compat_ptr(compat);
	}
#endif

	if (get_user(native, argv.ptr.native + nr))
		return ERR_PTR(-EFAULT);

	return native;
}

static __always_inline bool ksu_is_user_string_su(const char __user **filename_user)
{
	uintptr_t buf;
	const char su[16] = SU_PATH;

	// sugar prep
	uintptr_t *su_p = (uintptr_t *)su;
	uintptr_t __user *fn_p = (uintptr_t __user *)untagged_addr(*(char **)filename_user);

	// assert /system/bin/su\0 = 15 bytes.
	BUILD_BUG_ON(sizeof(SU_PATH) + 1 != 16);

	/*
	 * it seems this is actually the slowest part, so we peek last word first to speed it up
	 * NOTE: get_user rets EFAULT on err, so if we are copying a pointer
	 * that goes to nothing, we also detect that and ret fast
	 *
	 * first read overreads, reading 8 bytes, "bin/su\0?" /  4 bytes, "su\0?" when we only need 7/3
	 * but this is fine as we are guaranteed alignment, hardware provides trailing garbeg
	 * if it is specially crafted and hits a page guard, we just get EFAULT anyway
	 *
	 * on 64-bit we do this in 2 word compare, 4 on 32-bit, little endian only!
	 *
	 */

#ifdef CONFIG_64BIT
	if (get_user(buf, &fn_p[1]))
		return false;

	if (likely((buf & 0x00FFFFFFFFFFFFFFUL) != (su_p[1] & 0x00FFFFFFFFFFFFFFUL)))
		return false;

#else
	if (get_user(buf, &fn_p[3]))
		return false;

	if (likely((buf & 0x00FFFFFFUL) != (su_p[3] & 0x00FFFFFFUL)))
		return false;

	if (unlikely(get_user(buf, &fn_p[2])))
		return false;

	if (buf != su_p[2])
		return false;

	if (unlikely(get_user(buf, &fn_p[1])))
		return false;

	if (unlikely(buf != su_p[1]))
		return false;
#endif
	// last word
	if (unlikely(get_user(buf, &fn_p[0])))
		return false;

	if (unlikely(buf != su_p[0]))
		return false;
	
	return true;
}

int ksu_handle_execveat_init(struct filename *filename, struct user_arg_ptr *argv_user, struct user_arg_ptr *envp_user) {
    if (current->pid != 1 && is_init(get_current_cred())) {
        int ret;
        if (unlikely(strcmp(filename->name, KSUD_PATH) == 0)) {
            const char __user *argv_user_ptr = get_user_arg_ptr(*argv_user, 0);
            struct ksu_sulog_pending_event *pending_root = NULL;
            struct ksu_sulog_pending_event *pending_sucompat = NULL;

            pr_info("hook_manager: escape to root for init executing ksud: %d\n", current->pid);
            pending_root = ksu_sulog_capture_root_execve(filename->name, (struct user_arg_ptr*)argv_user, GFP_KERNEL);
            pending_sucompat = ksu_sulog_capture_sucompat(filename->name, argv_user, GFP_KERNEL);
            ret = escape_to_root_for_init();
            if (ret) {
                pr_err("escape_to_root_for_init() failed: %d\n", ret);
                ksu_sulog_emit_pending(pending_root, ret, GFP_KERNEL);
                ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
                return ret;
            }
            if (!argv_user_ptr || IS_ERR(argv_user_ptr)) {
                pr_err("!argv_user_ptr || IS_ERR(argv_user_ptr)\n");
                ksu_sulog_emit_pending(pending_root, -EFAULT, GFP_KERNEL);
                ksu_sulog_emit_pending(pending_sucompat, -EFAULT, GFP_KERNEL);
                return -EFAULT;
            }
            ksu_sulog_emit_pending(pending_root, ret, GFP_KERNEL);
            ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
            return 0;
        } else if (likely(strstr(filename->name, "/app_process") == NULL &&
                    strstr(filename->name, "/adbd") == NULL) &&
                    !susfs_is_current_proc_umounted())
        {
            pr_info("susfs: mark no sucompat checks for pid: '%d', exec: '%s'\n", current->pid, filename->name);
            susfs_set_current_proc_umounted();
            return 0;
        }

#ifdef CONFIG_COMPAT
        if (unlikely(envp_user->is_compat))
            ret = ksu_adb_root_handle_execve(filename->name, (void ***)&envp_user->ptr.compat);
        else
            ret = ksu_adb_root_handle_execve(filename->name, (void ***)&envp_user->ptr.native);    
#else
        ret = ksu_adb_root_handle_execve(filename->name, (void ***)&envp_user->ptr.native);
#endif

        if (ret) {
            pr_err("adb root failed: %d\n", ret);
            return ret;
        }
        return ret;
    }

    return -EINVAL;
}

int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr,
                 void *argv_user, void *envp_user,
                 int *__never_use_flags)
{
    struct filename *filename;
    const char __user *argv_user_ptr = get_user_arg_ptr(*((struct user_arg_ptr*)argv_user), 0);
    struct ksu_sulog_pending_event *pending_root = NULL;
    struct ksu_sulog_pending_event *pending_sucompat = NULL;
    int ret;

    if (unlikely(!filename_ptr))
        return 0;

    filename = *filename_ptr;
    if (IS_ERR(filename))
        return 0;

    if (!ksu_handle_execveat_init(filename, (struct user_arg_ptr*)argv_user, (struct user_arg_ptr*)envp_user))
        return 0;

    if (!ksu_is_allow_uid_for_current(current_uid().val)) {
        if (!argv_user_ptr || IS_ERR(argv_user_ptr))
            return 0;
        pending_root = ksu_sulog_capture_root_execve(filename->name, (struct user_arg_ptr*)argv_user, GFP_KERNEL);
        ksu_sulog_emit_pending(pending_root, 0, GFP_KERNEL);
        return 0;
    }

    if (likely(memcmp(filename->name, su_path, sizeof(su_path)))) {
        if (!argv_user_ptr || IS_ERR(argv_user_ptr))
            return 0;
        pending_root = ksu_sulog_capture_root_execve(filename->name, (struct user_arg_ptr*)argv_user, GFP_KERNEL);
        ksu_sulog_emit_pending(pending_root, 0, GFP_KERNEL);
        return 0;
    }

    if (current_chrooted())
    {
        pr_err("ksu_handle_execveat_sucompat: su found but NOT allowed! Because current process is running in chrooted environment\n");
        return 0;
    }

    pr_info("ksu_handle_execveat_sucompat: su found\n");

    write_sulog('x');
    memcpy((void *)filename->name, ksud_path, sizeof(ksud_path));

    pending_root = ksu_sulog_capture_root_execve(filename->name, (struct user_arg_ptr*)argv_user, GFP_KERNEL);
    pending_sucompat = ksu_sulog_capture_sucompat(filename->name, (struct user_arg_ptr*)argv_user, GFP_KERNEL);
    ret = escape_with_root_profile();
    if (ret)
        pr_err("escape_with_root_profile() failed: %d\n", ret);

    if (!argv_user_ptr || IS_ERR(argv_user_ptr)) {
        pr_err("!argv_user_ptr || IS_ERR(argv_user_ptr)\n");
        ksu_sulog_emit_pending(pending_root, ret, GFP_KERNEL);
        ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
        return 0;
    }

    ksu_sulog_emit_pending(pending_root, ret, GFP_KERNEL);
    ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
    return 0;
}

int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv,
            void *envp, int *flags)
{
    if (ksu_handle_execveat_ksud(fd, filename_ptr, argv, envp, flags))
        return 0;

    return ksu_handle_execveat_sucompat(fd, filename_ptr, argv, envp,
                        flags);
}

int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode,
             int *__unused_flags)
{
    if (!ksu_is_allow_uid_for_current(current_uid().val)) {
        return 0;
    }

    if (ksu_is_user_string_su(filename_user)) {
        if (current_chrooted())
        {
            pr_err("ksu_handle_faccessat: su found but NOT allowed! Because current process is running in chrooted environment\n");
            return 0;
        }
        write_sulog('a');
        pr_info("ksu_handle_faccessat: su->sh!\n");
        *filename_user = sh_user_path();
    }

    return 0;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags) {
    if (!ksu_is_allow_uid_for_current(current_uid().val)) {
        return 0;
    }

    if (unlikely(IS_ERR(*filename) || (*filename)->name == NULL))
        return 0;

    if (likely(memcmp((*filename)->name, su_path, sizeof(su_path))))
        return 0;

    if (current_chrooted())
    {
        pr_err("ksu_handle_stat: su found but NOT allowed! Because current process is running in chrooted environment\n");
        return 0;
    }
    write_sulog('s');
    pr_info("ksu_handle_stat: su->sh!\n");
    memcpy((void *)((*filename)->name), sh_path, sizeof(sh_path));
    return 0;
}
#else
int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags)
{
    if (!ksu_is_allow_uid_for_current(current_uid().val)) {
        return 0;
    }

    if (unlikely(!filename_user))
        return 0;

    if (ksu_is_user_string_su(filename_user)) {
        if (current_chrooted())
        {
            pr_err("ksu_handle_stat: su found but NOT allowed! Because current process is running in chrooted environment\n");
            return 0;
        }
        write_sulog('s');
        pr_info("ksu_handle_stat: su->sh!\n");
        *filename_user = sh_user_path();
    }

    return 0;
}
#endif // #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)

// sucompat: permitted process can execute 'su' to gain root access.
void __init ksu_sucompat_init()
{
    if (ksu_register_feature_handler(&su_compat_handler)) {
        pr_err("Failed to register su_compat feature handler\n");
    }
}

void __exit ksu_sucompat_exit()
{
    ksu_unregister_feature_handler(KSU_FEATURE_SU_COMPAT);
}
