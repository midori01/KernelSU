/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * MidoriSU: once el0_svc reads xxKSU's sys_call_table mirror, a module loaded later that
 * hooks the kernel's table (e.g. susfs_guard_lkm) is never reached. Carry its entries over
 * to the mirror, keep the slots that hold our own hooks, and let our reboot hooks hand over
 * to whatever the kernel's table holds there.
 */
#ifndef __MIDORI_SCT_SYNC_H
#define __MIDORI_SCT_SYNC_H

#if defined(MODULE) && defined(CONFIG_ARM64) && LINUX_VERSION_CODE >= KERNEL_VERSION(4, 19, 0)

#define MIDORI_COMPAT_NR_REBOOT 88

#if defined(CONFIG_COMPAT)
#if defined(__NR_compat_syscalls)
#define MIDORI_NR_COMPAT_SYSCALLS __NR_compat_syscalls
#else
/* 6.11+ generates the compat numbers with a "compat32_" prefix */
#include <asm/unistd_compat_32.h>
#define MIDORI_NR_COMPAT_SYSCALLS __NR_compat32_syscalls
#endif
#endif

static DEFINE_MUTEX(ksu_sct_sync_mutex);

static void ksu_sct_sync_table(uintptr_t *mirror, const uintptr_t *real, unsigned int nr_slots)
{
	unsigned int nr;

	if ((uintptr_t)mirror == (uintptr_t)real)
		return;

	for (nr = 0; nr < nr_slots; nr++) {
		uintptr_t cur = READ_ONCE(mirror[nr]);
		uintptr_t want = READ_ONCE(real[nr]);

		if (cur == want || !want || within_module(cur, THIS_MODULE))
			continue;

		pr_info("%s: syscall #%u: 0x%lx -> 0x%lx\n", __func__, nr, cur, want);
		patch_ptr_slot_kick_cpu((void **)&mirror[nr], (void *)want);
	}
}

static void ksu_sct_sync(void)
{
	mutex_lock(&ksu_sct_sync_mutex);
	ksu_sct_sync_table((uintptr_t *)native_sct, (const uintptr_t *)sys_call_table, __NR_syscalls);
#if defined(CONFIG_COMPAT)
	ksu_sct_sync_table((uintptr_t *)compat_sct, (const uintptr_t *)compat_sys_call_table,
			   MIDORI_NR_COMPAT_SYSCALLS);
#endif
	mutex_unlock(&ksu_sct_sync_mutex);
}

static int ksu_sct_module_notify(struct notifier_block *nb, unsigned long action, void *data)
{
	if (action == MODULE_STATE_LIVE || action == MODULE_STATE_GOING)
		ksu_sct_sync();
	return NOTIFY_DONE;
}

static struct notifier_block ksu_sct_module_nb = {
	.notifier_call = ksu_sct_module_notify,
};

static void __maybe_unused ksu_sct_sync_init(void)
{
	bool mirrored = native_sct != (void *)sys_call_table;

#if defined(CONFIG_COMPAT)
	mirrored |= compat_sct != (void *)compat_sys_call_table;
#endif
	if (!mirrored)
		return;

	register_module_notifier(&ksu_sct_module_nb);
	pr_info("%s: following sys_call_table changes\n", __func__);
}

static syscall_fn_t ksu_sct_next_reboot(void)
{
	const void *sct = native_sct;
	const void *real = (const void *)sys_call_table;
	unsigned int nr = __NR_reboot;
	syscall_fn_t next;

#if defined(CONFIG_COMPAT)
	if (in_compat_syscall()) {
		sct = compat_sct;
		real = (const void *)compat_sys_call_table;
		nr = MIDORI_COMPAT_NR_REBOOT;
	}
#endif
	if (sct == real)
		return NULL;

	next = READ_ONCE(((const syscall_fn_t *)real)[nr]);
	if (!next || within_module((unsigned long)next, THIS_MODULE))
		return NULL;
	return next;
}

#define KSU_SCT_FORWARD_REBOOT(regs)					\
	do {								\
		syscall_fn_t __ksu_sct_next = ksu_sct_next_reboot();	\
		if (__ksu_sct_next)					\
			return __ksu_sct_next(regs);			\
	} while (0)

#else
#define ksu_sct_sync_init()		do { } while (0)
#define KSU_SCT_FORWARD_REBOOT(regs)	do { } while (0)
#endif

#endif /* __MIDORI_SCT_SYNC_H */
