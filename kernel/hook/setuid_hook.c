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

static inline void handle_susfs_setresuid(struct cred *new, const struct cred *old, uid_t new_uid, bool is_zygote_next)
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
			ksu_handle_umount(new, old);
		ksu_handle_extra_susfs_work();
	}
}
#endif // #ifdef CONFIG_KSU_SUSFS

static __always_inline void ksu_handle_setresuid_cred(struct cred *new, const struct cred *old)
{
	if (!new || !old)
		return;

	uid_t new_uid = ksu_get_uid_t(new->uid);
	uid_t old_uid = ksu_get_uid_t(old->uid);

	// old process is not root, ignore it.
	if (unlikely(!!old_uid))
		return;

	if (IS_ENABLED(CONFIG_KSU_DEBUG))
		pr_info("handle_setresuid from %d to %d\n", old_uid, new_uid);

	// we dont have those new fancy things upstream has
	// lets just do the original thing where we disable seccomp
	if (unlikely(is_uid_manager(new_uid)))
		goto install_ksu_fd;

	if (ksu_is_allow_uid_for_current(new_uid))
		goto kill_seccomp;

	// Handle kernel umount
#ifdef CONFIG_KSU_SUSFS
	if (susfs_is_current_zygote_domain() || new_uid == WEBVIEW_ZYGOTE_UID) {
		handle_susfs_setresuid(new, old, new_uid, false);
	} else if (susfs_is_current_zygote_next_domain()) {
		handle_susfs_setresuid(new, old, new_uid, true);
	}
#else
	ksu_handle_umount(new, old);
#endif // #ifdef CONFIG_KSU_SUSFS
	return;

install_ksu_fd:
	pr_info("install fd for manager: %d\n", new_uid);
	ksu_install_fd();

kill_seccomp:
	disable_seccomp();
	set_thread_flag(TIF_KSU_MANAGED); // sucompat fast-path
	return;
}
