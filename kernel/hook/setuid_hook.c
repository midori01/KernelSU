#include <linux/compiler.h>
#include <linux/version.h>
#include <linux/slab.h>
#include <linux/task_work.h>
#include <linux/thread_info.h>
#include <linux/seccomp.h>
#include <linux/printk.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/uidgid.h>

#include "policy/allowlist.h"
#include "hook/setuid_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "infra/seccomp_cache.h"
#include "supercall/supercall.h"
#include "hook/tp_marker.h"
#include "feature/kernel_umount.h"
#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs_def.h>
#include "selinux/selinux.h"

extern struct work_struct susfs_extra_works;
#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
extern void susfs_try_umount(uid_t uid);
#endif // #ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT

static inline void ksu_handle_extra_susfs_work(void)
{
    if (!work_pending(&susfs_extra_works))
        schedule_work(&susfs_extra_works);
}

static inline void handle_susfs_setresuid(uid_t new_uid, bool is_zygote_next)
{
    bool is_isolated = is_isolated_process(new_uid);
    bool should_umount = likely((is_appuid(new_uid) || new_uid == WEBVIEW_ZYGOTE_UID) && ksu_uid_should_umount(new_uid));

    susfs_set_current_proc_no_su();

    if (is_isolated || should_umount) {
        susfs_set_current_proc_umounted();
        if (is_zygote_next)
            susfs_set_current_proc_umounted_for_zygote_next();
#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
        susfs_try_umount(new_uid);
#endif // #ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
        if (!is_zygote_next)
            ksu_handle_umount(0, new_uid);
        ksu_handle_extra_susfs_work();
    }
}
#endif // #ifdef CONFIG_KSU_SUSFS

int ksu_handle_setresuid(uid_t old_uid, uid_t new_uid)
{
    // we rely on the fact that zygote always call setresuid(3) with same uids

    pr_info("handle_setresuid from %d to %d\n", old_uid, new_uid);

    if (unlikely(is_uid_manager(new_uid))) {
        spin_lock_irq(&current->sighand->siglock);
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
        ksu_set_task_tracepoint_flag(current);
        spin_unlock_irq(&current->sighand->siglock);

        pr_info("install fd for manager: %d\n", new_uid);
        ksu_install_fd();
        return 0;
    }

    if (ksu_is_allow_uid_for_current(new_uid)) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER &&
            current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
        ksu_set_task_tracepoint_flag(current);
    } else {
        ksu_clear_task_tracepoint_flag_if_needed(current);
    }

    // Handle kernel umount
#ifdef CONFIG_KSU_SUSFS
    if (susfs_is_current_zygote_domain() || new_uid == WEBVIEW_ZYGOTE_UID) {
        handle_susfs_setresuid(new_uid, false);
    } else if (susfs_is_current_zygote_next_domain()) {
        handle_susfs_setresuid(new_uid, true);
    }
#else
    ksu_handle_umount(old_uid, new_uid);
#endif // #ifdef CONFIG_KSU_SUSFS

    return 0;
}

void __init ksu_setuid_hook_init(void)
{
    ksu_kernel_umount_init();
}

void __exit ksu_setuid_hook_exit(void)
{
    pr_info("ksu_core_exit\n");
    ksu_kernel_umount_exit();
}
