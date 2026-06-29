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

#include <linux/utsname.h>

#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h"
#include "util.h"
#include "klog.h" // IWYU pragma: keep

#include "manager/manager_identity.h"
#include "supercall/supercall.h"

struct sulog_entry {
    uint32_t s_time;
    uint32_t data;
} __attribute__((packed));

#define SULOG_ENTRY_MAX 250
#define SULOG_BUFSIZ (SULOG_ENTRY_MAX * sizeof(struct sulog_entry))

static void *sulog_buf_ptr = NULL;
static uint8_t sulog_index_next = 0;
static DEFINE_SPINLOCK(sulog_lock);

void sulog_init_heap(void)
{
    sulog_buf_ptr = kzalloc(SULOG_BUFSIZ, GFP_KERNEL);
    if (!sulog_buf_ptr)
        return;
    pr_info("sulog_init: allocated %lu bytes on 0x%p\n", SULOG_BUFSIZ, sulog_buf_ptr);
}

void write_sulog(uint8_t sym)
{
    if (!sulog_buf_ptr)
        return;
    unsigned int offset = sulog_index_next * sizeof(struct sulog_entry);
    struct sulog_entry entry = {0};
    entry.s_time = (uint32_t)(ktime_get_boottime() / 1000000000);
    entry.data = (uint32_t)current_uid().val;
    *((char *)&entry.data + 3) = sym;
    spin_lock(&sulog_lock);
    *(volatile uint64_t *)(sulog_buf_ptr + offset) = *(volatile uint64_t *)&entry;
    spin_unlock(&sulog_lock);
    sulog_index_next = sulog_index_next + 1;
    if (sulog_index_next >= SULOG_ENTRY_MAX)
        sulog_index_next = 0;
}

struct sulog_entry_rcv_ptr {
    uint64_t index_ptr;
    uint64_t buf_ptr;
    uint64_t uptime_ptr;
};

int send_sulog_dump(void __user *uptr)
{
    if (!sulog_buf_ptr)
        return 1;
    struct sulog_entry_rcv_ptr sbuf = {0};
    if (copy_from_user(&sbuf, uptr, sizeof(sbuf)))
        return 1;
    if (!sbuf.index_ptr || !sbuf.buf_ptr || !sbuf.uptime_ptr)
        return 1;
    uint32_t uptime = (uint32_t)(ktime_get_boottime() / 1000000000);
    if (copy_to_user((void __user *)sbuf.uptime_ptr, &uptime, sizeof(uptime)))
        return 1;
    if (copy_to_user((void __user *)sbuf.index_ptr, &sulog_index_next, sizeof(sulog_index_next)))
        return 1;
    spin_lock(&sulog_lock);
    if (copy_to_user((void __user *)sbuf.buf_ptr, sulog_buf_ptr, SULOG_BUFSIZ)) {
        spin_unlock(&sulog_lock);
        return 1;
    }
    spin_unlock(&sulog_lock);
    return 0;
}

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

uint32_t ksuver_override = 0;
uint32_t ksuflags_override = 0;

static int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);
    unsigned int cmd = (unsigned int)PT_REGS_PARM3(real_regs);
    unsigned long arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(real_regs);

    if (magic1 == KSU_INSTALL_MAGIC1 && magic2 == KSU_INSTALL_MAGIC2) {
        struct ksu_install_fd_tw *tw;
        tw = kzalloc(sizeof(*tw), GFP_ATOMIC);
        if (!tw)
            return 0;
        tw->outp = (int __user *)arg4;
        tw->cb.func = ksu_install_fd_tw_func;
        if (task_work_add(current, &tw->cb, TWA_RESUME)) {
            kfree(tw);
            pr_warn("install fd add task_work failed\n");
        }
        return 0;
    }

    unsigned long reply = (unsigned long)arg4;

    if (magic2 == CHANGE_MANAGER_UID) {
        if (current_uid().val != 0)
            return 0;
        pr_info("sys_reboot: ksu_set_manager_appid to: %d\n", cmd);
        ksu_set_manager_appid(cmd);
        if (cmd == ksu_get_manager_appid()) {
            if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
                pr_info("sys_reboot: reply fail\n");
        }
        return 0;
    }

    if (magic2 == GET_SULOG_DUMP_V2) {
        if (current_uid().val != 0)
            return 0;
        int ret = send_sulog_dump((void __user *)arg4);
        if (ret)
            return 0;
        if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
            return 0;
        return 0;
    }

    if (magic2 == CHANGE_KSUVER) {
        if (current_uid().val != 0)
            return 0;
        pr_info("sys_reboot: ksu_change_ksuver to: %d\n", cmd);
        ksuver_override = cmd;
        if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
            return 0;
        return 0;
    }

    if (magic2 == CHANGE_SPOOF_UNAME) {
        if (current_uid().val != 0)
            return 0;
        char release_buf[65];
        char version_buf[65];
        static char original_release_buf[65] = {0};
        static char original_version_buf[65] = {0};
        void ***ppptr = (uintptr_t)&arg4;
        uint64_t u_pptr = 0;
        uint64_t u_ptr = 0;
        if (copy_from_user(&u_pptr, (void __user *)*ppptr, sizeof(u_pptr)))
            return 0;
        if (copy_from_user(&u_ptr, (void __user *)u_pptr, sizeof(u_ptr)))
            return 0;
        if (strncpy_from_user(release_buf, (char __user *)u_ptr, sizeof(release_buf)) < 0)
            return 0;
        release_buf[sizeof(release_buf) - 1] = '\0';
        if (strncpy_from_user(version_buf, (char __user *)(u_ptr + strlen(release_buf) + 1), sizeof(version_buf)) < 0)
            return 0;
        version_buf[sizeof(version_buf) - 1] = '\0';
        if (original_release_buf[0] == '\0') {
            struct new_utsname *u_curr = utsname();
            strncpy(original_release_buf, u_curr->release, sizeof(original_release_buf));
            strncpy(original_version_buf, u_curr->version, sizeof(original_version_buf));
        }
        if (!strcmp(release_buf, "default"))
            memcpy(release_buf, original_release_buf, sizeof(release_buf));
        if (!strcmp(version_buf, "default"))
            memcpy(version_buf, original_version_buf, sizeof(version_buf));
        struct new_utsname *u = utsname();
        down_write(&uts_sem);
        strncpy(u->release, release_buf, sizeof(u->release));
        strncpy(u->version, version_buf, sizeof(u->version));
        up_write(&uts_sem);
        if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
            return 0;
        return 0;
    }

    if (magic2 == CHANGE_KSUFLAGS) {
        if (current_uid().val != 0)
            return 0;
        pr_info("sys_reboot: ksu_change_ksuflags to: %d\n", cmd);
        ksuflags_override = cmd;
        if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
            return 0;
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

    sulog_init_heap();

    rc = register_kprobe(&reboot_kp);
    if (rc) {
        pr_err("reboot kprobe failed: %d\n", rc);
    } else {
        pr_info("reboot kprobe registered successfully\n");
    }
}

void __exit ksu_supercalls_exit(void)
{
    if (sulog_buf_ptr) {
        memzero_explicit(sulog_buf_ptr, SULOG_BUFSIZ);
        kfree(sulog_buf_ptr);
    }

    unregister_kprobe(&reboot_kp);
    ksu_supercall_cleanup_state();
}
