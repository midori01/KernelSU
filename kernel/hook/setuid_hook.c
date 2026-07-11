#ifdef CONFIG_KSU_SUSFS
#include <linux/susfs_def.h>

static inline bool is_zygote_isolated_service_uid(uid_t uid)
{
	uid %= 100000;
	return (uid >= 99000 && uid < 100000);
}

static inline bool is_zygote_normal_app_uid(uid_t uid)
{
	uid %= 100000;
	return (uid >= 10000 && uid < 19999);
}

extern u32 susfs_zygote_sid;
extern struct cred *ksu_cred;

static void ksu_handle_extra_susfs_work(void) {}

#else
#define ksu_handle_extra_susfs_work() do {} while (0)
#define is_zygote_isolated_service_uid(uid) false
#endif

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

#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
	// Check if spawned process is isolated service first, and force to do umount if so
	if (is_zygote_isolated_service_uid(new_uid)) {
		goto do_umount;
	}
#endif

	// we dont have those new fancy things upstream has
	// lets just do the original thing where we disable seccomp
	if (unlikely(is_uid_manager(new_uid)))
		goto install_ksu_fd;

	if (ksu_is_allow_uid_for_current(new_uid))
		goto kill_seccomp;

	// Handle kernel umount
	goto do_umount;
	return;

install_ksu_fd:
	pr_info("install fd for manager: %d\n", new_uid);
	ksu_install_fd();

kill_seccomp:
	disable_seccomp();
	return;
do_umount:
	// Handle kernel umount
	ksu_handle_umount(new, old);

#ifdef CONFIG_KSU_SUSFS
	if (is_zygote_normal_app_uid(new_uid)) {
		ksu_handle_extra_susfs_work();
	}

	susfs_set_current_proc_umounted();

	return;
#endif
}
