#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs_def.h>
#include "selinux/selinux.h"
#endif

#ifdef CONFIG_KSU_SUSFS
extern u32 susfs_zygote_sid;
extern u32 susfs_zygote_next_sid;
extern void disable_seccomp(void);
extern struct work_struct susfs_extra_works;

static inline void ksu_handle_extra_susfs_work(void)
{
	if (work_pending(&susfs_extra_works))
		return;

	schedule_work(&susfs_extra_works);
}

static int handle_zygote_setresuid(uid_t ruid) {
	// Check if spawned process is isolated service first, and force to do umount if so
	if (is_isolated_process(ruid)) {
		susfs_set_current_proc_no_su();
		susfs_set_current_proc_umounted();
		goto do_umount;
	}

	// KSU manager: disable seccomp and install fd
	if (likely(ksu_is_manager_appid_valid()) && unlikely(is_uid_manager(ruid))) {
		disable_seccomp();
		pr_info("install fd for manager: %d\n", ruid);
		ksu_install_fd();
		return 0;
	}

	// Don't umount for webview zygote
	if (unlikely(ruid == WEBVIEW_ZYGOTE_UID)) {
		susfs_set_current_proc_no_su();
		return 0;
	}

	// Normal app that needs umount
	if (likely(is_appuid(ruid) && ksu_uid_should_umount(ruid))) {
		susfs_set_current_proc_no_su();
		susfs_set_current_proc_umounted();
		goto do_umount;
	}

	// Root allowed apps
	if (ksu_is_allow_uid_for_current(ruid)) {
		disable_seccomp();
		return 0;
	}

	susfs_set_current_proc_no_su();
	return 0;

do_umount:
	{
		ksu_handle_umount(current_uid().val, ruid);
		ksu_handle_extra_susfs_work();
	}

	return 0;
}

static int handle_zygote_next_setresuid(uid_t ruid) {
	// zygote_next: do NOT umount, just set flags
	if (is_isolated_process(ruid)) {
		susfs_set_current_proc_no_su();
		susfs_set_current_proc_umounted_for_zygote_next();
		goto do_susfs_work;
	}

	if (likely(ksu_is_manager_appid_valid()) && unlikely(is_uid_manager(ruid))) {
		disable_seccomp();
		pr_info("install fd for manager: %d\n", ruid);
		ksu_install_fd();
		return 0;
	}

	if (unlikely(ruid == WEBVIEW_ZYGOTE_UID)) {
		susfs_set_current_proc_no_su();
		return 0;
	}

	if (likely(is_appuid(ruid) && ksu_uid_should_umount(ruid))) {
		susfs_set_current_proc_no_su();
		susfs_set_current_proc_umounted_for_zygote_next();
		goto do_susfs_work;
	}

	if (ksu_is_allow_uid_for_current(ruid)) {
		disable_seccomp();
		return 0;
	}

	susfs_set_current_proc_no_su();
	return 0;

do_susfs_work:
	{
		// Do not umount here as we are in init namespace now
		ksu_handle_extra_susfs_work();
	}

	return 0;
}

int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
	if (current_uid().val != 0)
		return 0;

	if (susfs_is_sid_equal(current_cred(), susfs_zygote_sid))
		return handle_zygote_setresuid(ruid);

	if (susfs_is_sid_equal(current_cred(), susfs_zygote_next_sid))
		return handle_zygote_next_setresuid(ruid);

	return 0;
}
#endif // #ifdef CONFIG_KSU_SUSFS
