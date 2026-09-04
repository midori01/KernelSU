#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/kprobes.h>
#include <linux/pid.h>
#include <linux/slab.h>
#include <linux/syscalls.h>
#include <linux/task_work.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include "toolkit.h"

#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h"
#include "util.h"
#include "klog.h" // IWYU pragma: keep
#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs.h>
#include <linux/preempt.h>
#endif // #ifdef CONFIG_KSU_SUSFS

struct ksu_install_fd_tw {
    struct callback_head cb;
    int __user *outp;
};

static int anon_ksu_release(struct inode *inode, struct file *filp)
{
    pr_info("ksu fd released\n");
    return 0;
}

static long anon_ksu_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    return ksu_supercall_handle_ioctl(cmd, (void __user *)arg);
}

static const struct file_operations anon_ksu_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = anon_ksu_ioctl,
    .compat_ioctl = anon_ksu_ioctl,
    .release = anon_ksu_release,
};

int ksu_install_fd(void)
{
    struct file *filp;
    int fd;

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("ksu_install_fd: failed to get unused fd\n");
        return fd;
    }

    filp = anon_inode_getfile("[ksu_driver]", &anon_ksu_fops, NULL, O_RDWR | O_CLOEXEC);
    if (IS_ERR(filp)) {
        pr_err("ksu_install_fd: failed to create anon inode file\n");
        put_unused_fd(fd);
        return PTR_ERR(filp);
    }

    fd_install(fd, filp);
    pr_info("ksu fd installed: %d for pid %d\n", fd, current->pid);
    return fd;
}

static void ksu_install_fd_tw_func(struct callback_head *cb)
{
    struct ksu_install_fd_tw *tw = container_of(cb, struct ksu_install_fd_tw, cb);
    int fd = ksu_install_fd();

    pr_info("[%d] install ksu fd: %d\n", current->pid, fd);
    if (copy_to_user(tw->outp, &fd, sizeof(fd))) {
        pr_err("install ksu fd reply err\n");
        ksu_close_fd(fd);
    }

    kfree(tw);
}

static int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);
#ifdef CONFIG_KSU_SUSFS
    int cmd = (int)PT_REGS_PARM3(real_regs);
    void __user **arg = (void __user **)&PT_REGS_SYSCALL_PARM4(real_regs);

	if (magic1 == KSU_INSTALL_MAGIC1 && magic2 == SUSFS_MAGIC && current_uid().val == 0) {
		bool got_flipped = false;
		if (likely(!preemptible())) {
			preempt_enable();
			got_flipped = true;
		}

#ifdef CONFIG_KSU_SUSFS_SUS_PATH
		if (cmd == CMD_SUSFS_ADD_SUS_PATH) {
			susfs_add_sus_path(arg);
			goto out_susfs;
		}
		if (cmd == CMD_SUSFS_ADD_SUS_PATH_LOOP) {
			susfs_add_sus_path_loop(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_PATH
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
		if (cmd == CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS) {
			susfs_set_hide_sus_mnts_for_non_su_procs(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
		if (cmd == CMD_SUSFS_ADD_SUS_KSTAT) {
			susfs_add_sus_kstat(arg);
			goto out_susfs;
		}
		if (cmd == CMD_SUSFS_UPDATE_SUS_KSTAT) {
			susfs_update_sus_kstat(arg);
			goto out_susfs;
		}
		if (cmd == CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY) {
			susfs_add_sus_kstat(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
#ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
		if (cmd == CMD_SUSFS_ADD_TRY_UMOUNT) {
			susfs_add_try_umount(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_TRY_UMOUNT
#ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
		if (cmd == CMD_SUSFS_SET_UNAME) {
			susfs_set_uname(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
		if (cmd == CMD_SUSFS_ENABLE_LOG) {
			susfs_enable_log(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
#ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
		if (cmd == CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG) {
			susfs_set_cmdline_or_bootconfig(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
#ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
		if (cmd == CMD_SUSFS_ADD_OPEN_REDIRECT) {
			susfs_add_open_redirect(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
#ifdef CONFIG_KSU_SUSFS_SUS_MAP
		if (cmd == CMD_SUSFS_ADD_SUS_MAP) {
			susfs_add_sus_map(arg);
			goto out_susfs;
		}
#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MAP
		if (cmd == CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING) {
			susfs_set_avc_log_spoofing(arg);
			goto out_susfs;
		}
		if (cmd == CMD_SUSFS_SHOW_ENABLED_FEATURES) {
			susfs_get_enabled_features(arg);
			goto out_susfs;
		}
		if (cmd == CMD_SUSFS_SHOW_VARIANT) {
			susfs_show_variant(arg);
			goto out_susfs;
		}
		if (cmd == CMD_SUSFS_SHOW_VERSION) {
			susfs_show_version(arg);
			goto out_susfs;
		}

out_susfs:
		if (got_flipped)
			preempt_disable();
		return 0;
	}
#endif // #ifdef CONFIG_KSU_SUSFS

    if (magic1 == KSU_INSTALL_MAGIC1 && magic2 == KSU_INSTALL_MAGIC2) {
        struct ksu_install_fd_tw *tw;
        unsigned long arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(real_regs);

        tw = kzalloc(sizeof(*tw), GFP_ATOMIC);
        if (!tw)
            return 0;

        tw->outp = (int __user *)arg4;
        tw->cb.func = ksu_install_fd_tw_func;

        if (task_work_add(current, &tw->cb, TWA_RESUME)) {
            kfree(tw);
            pr_warn("install fd add task_work failed\n");
        }
    }

    if (magic1 == KSU_INSTALL_MAGIC1 && current_uid().val == 0) {
        if (handle_toolkit_reboot(magic2, (unsigned int)PT_REGS_PARM3(real_regs), (unsigned long)PT_REGS_SYSCALL_PARM4(real_regs)))
            return 0;
    }

    return 0;
}

static struct kprobe reboot_kp = {
    .symbol_name = REBOOT_SYMBOL,
    .pre_handler = reboot_handler_pre,
};

void __init ksu_supercalls_init(void)
{
    int rc;

    ksu_supercall_dump_commands();
    tiny_sulog_init_heap();

    rc = register_kprobe(&reboot_kp);
    if (rc) {
        pr_err("reboot kprobe failed: %d\n", rc);
    } else {
        pr_info("reboot kprobe registered successfully\n");
    }
}

void __exit ksu_supercalls_exit(void)
{
    unregister_kprobe(&reboot_kp);
    ksu_supercall_cleanup_state();
    tiny_sulog_cleanup();
}
