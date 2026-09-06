// SPDX-License-Identifier: GPL-2.0-only
#ifndef __KSU_H_TOOLKIT
#define __KSU_H_TOOLKIT

#include <linux/types.h>
#include <linux/slab.h>
#include <linux/ktime.h>
#include <linux/uaccess.h>
#include <linux/math64.h>
#include <linux/cred.h>
#include <linux/utsname.h>
#include <linux/preempt.h>
#include "manager/manager_identity.h"
#include "supercall/supercall.h"

uint32_t ksuver_override = 0;
uint32_t ksuflags_override = 0;

struct sulog_entry {
    uint32_t s_time;    // uptime in seconds
    uint32_t data;      // uint8_t[0,1,2] = uid, uint8_t[3] = symbol
} __attribute__((aligned(8)));

#define SULOG_ENTRY_MAX 250
#define SULOG_BUFSIZ (SULOG_ENTRY_MAX * (sizeof(struct sulog_entry)))

static void *sulog_buf_ptr = NULL;
static uint32_t sulog_index_next = 0;

static inline void tiny_sulog_init_heap(void)
{
    sulog_buf_ptr = kzalloc(SULOG_BUFSIZ, GFP_KERNEL);
    if (!sulog_buf_ptr)
        return;

    pr_info("sulog_init: allocated %lu bytes on 0x%lx \n", (unsigned long)SULOG_BUFSIZ, (uintptr_t)sulog_buf_ptr);
}

static inline void tiny_sulog_cleanup(void)
{
    if (sulog_buf_ptr) {
        kfree(sulog_buf_ptr);
        sulog_buf_ptr = NULL;
    }
}

static inline uint32_t boottime_s_get(void)
{
    ktime_t boottime_kt = ktime_get_boottime();

#ifdef CONFIG_64BIT
    uint64_t boottime_s = *(uint64_t *)&boottime_kt / 1000000000;
#else
    uint64_t boottime_s = *(uint64_t *)&boottime_kt;
    do_div(boottime_s, 1000000000);
#endif

    return (uint32_t)boottime_s;
}

void write_sulog(uint8_t sym)
{
    if (!sulog_buf_ptr)
        return;

    struct sulog_entry entry = {0};

    entry.s_time = boottime_s_get();
    entry.data = (uint32_t)current_uid().val;
    *((char *)&entry.data + 3) = sym;

    uint32_t slot = __atomic_load_n(&sulog_index_next, __ATOMIC_RELAXED);
    uint32_t next_slot;

retry:
    if (slot + 1 >= SULOG_ENTRY_MAX)
        next_slot = 0;
    else
        next_slot = slot + 1;

    bool success = __atomic_compare_exchange(&sulog_index_next, &slot, &next_slot,
                            true,
                            __ATOMIC_RELEASE,
                            __ATOMIC_RELAXED);
    if (!success)
        goto retry;

    __atomic_store((uint64_t *)sulog_buf_ptr + slot, (uint64_t *)&entry, __ATOMIC_RELEASE);
}

struct sulog_entry_rcv_ptr {
    uint64_t index_ptr; // send index here
    uint64_t buf_ptr;   // send buf here
    uint64_t uptime_ptr;// uptime
};

static noinline int send_sulog_dump(void __user *uptr)
{
    if (!sulog_buf_ptr)
        return 1;

    struct sulog_entry_rcv_ptr sbuf = {0};

    if (copy_from_user(&sbuf, uptr, sizeof(sbuf)))
        return 1;

    if (!sbuf.index_ptr || !sbuf.buf_ptr || !sbuf.uptime_ptr)
        return 1;

    void *memory = kmalloc(SULOG_BUFSIZ, GFP_KERNEL);
    if (!memory)
        return -ENOMEM;

    uint32_t uptime = boottime_s_get();
    uint32_t current_idx = __atomic_load_n(&sulog_index_next, __ATOMIC_ACQUIRE);
    memcpy(memory, sulog_buf_ptr, SULOG_BUFSIZ);

    int ret = 0;
    if (copy_to_user((void __user *)(uintptr_t)sbuf.uptime_ptr, &uptime, sizeof(uptime)))
        ret = 1;
    else if (copy_to_user((void __user *)(uintptr_t)sbuf.index_ptr, &current_idx, sizeof(current_idx)))
        ret = 1;
    else if (copy_to_user((void __user *)(uintptr_t)sbuf.buf_ptr, memory, SULOG_BUFSIZ))
        ret = 1;

    kfree(memory);
    return ret;
}

static inline int handle_toolkit_reboot(int magic2, unsigned int cmd, unsigned long arg4)
{
    u64 reply = arg4;

    if (magic2 == CHANGE_MANAGER_UID || magic2 == GET_SULOG_DUMP_V2 ||
        magic2 == CHANGE_KSUVER || magic2 == CHANGE_SPOOF_UNAME ||
        magic2 == CHANGE_KSUFLAGS) {
        bool got_flipped = false;
        if (likely(!preemptible())) {
            preempt_enable();
            got_flipped = true;
        }

        if (magic2 == CHANGE_MANAGER_UID) {
            pr_info("sys_reboot: ksu_set_manager_appid to: %d\n", cmd);
            ksu_set_manager_appid(cmd);

            if (cmd == ksu_get_manager_appid()) {
                if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
                    pr_info("sys_reboot: reply fail\n");
            }
        } else if (magic2 == GET_SULOG_DUMP_V2) {
            int ret = send_sulog_dump((void __user *)arg4);
            if (!ret) {
                if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
                    pr_info("sys_reboot: reply fail\n");
            }
        } else if (magic2 == CHANGE_KSUVER) {
            pr_info("sys_reboot: ksu_change_ksuver to: %d\n", cmd);
            ksuver_override = cmd;

            if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
                pr_info("sys_reboot: reply fail\n");
        } else if (magic2 == CHANGE_SPOOF_UNAME) {
            char release_buf[65];
            char version_buf[65];
            static char original_release_buf[65] = {0};
            static char original_version_buf[65] = {0};

            void ***ppptr = (void ***)&arg4;
            uint64_t u_pptr = 0;
            uint64_t u_ptr = 0;

            pr_info("sys_reboot: ppptr: 0x%lx \n", (uintptr_t)ppptr);

            if (!copy_from_user(&u_pptr, (void __user *)*ppptr, sizeof(u_pptr))) {
                pr_info("sys_reboot: u_pptr: 0x%lx \n", (uintptr_t)u_pptr);

                if (!copy_from_user(&u_ptr, (void __user *)u_pptr, sizeof(u_ptr))) {
                    pr_info("sys_reboot: u_ptr: 0x%lx \n", (uintptr_t)u_ptr);

                    if (strncpy_from_user(release_buf, (char __user *)u_ptr, sizeof(release_buf)) >= 0) {
                        release_buf[sizeof(release_buf) - 1] = '\0';

                        if (strncpy_from_user(version_buf, (char __user *)(u_ptr + strlen(release_buf) + 1), sizeof(version_buf)) >= 0) {
                            version_buf[sizeof(version_buf) - 1] = '\0';

                            if (original_release_buf[0] == '\0') {
                                struct new_utsname *u_curr = utsname();
                                strscpy(original_release_buf, u_curr->release, sizeof(original_release_buf));
                                strscpy(original_version_buf, u_curr->version, sizeof(original_version_buf));
                                pr_info("sys_reboot: original uname saved: %s %s\n", original_release_buf, original_version_buf);
                            }

                            if (!strcmp(release_buf, "default")) {
                                memcpy(release_buf, original_release_buf, sizeof(release_buf));
                            }
                            if (!strcmp(version_buf, "default")) {
                                memcpy(version_buf, original_version_buf, sizeof(version_buf));
                            }

                            pr_info("sys_reboot: spoofing kernel to: %s - %s\n", release_buf, version_buf);

                            struct new_utsname *u = utsname();
                            down_write(&uts_sem);
                            strscpy(u->release, release_buf, sizeof(u->release));
                            strscpy(u->version, version_buf, sizeof(u->version));
                            up_write(&uts_sem);

                            if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
                                pr_info("sys_reboot: reply fail\n");
                        }
                    }
                }
            }
        } else if (magic2 == CHANGE_KSUFLAGS) {
            pr_info("sys_reboot: ksu_change_ksuflags to: %d\n", cmd);
            ksuflags_override = cmd;

            if (copy_to_user((void __user *)arg4, &reply, sizeof(reply)))
                pr_info("sys_reboot: reply fail\n");
        }

        if (got_flipped) {
            preempt_disable();
        }
        return 1;
    }

    return 0;
}

#endif // __KSU_H_TOOLKIT
