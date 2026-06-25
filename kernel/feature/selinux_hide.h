#ifndef __KSU_H_SELINUX_HIDE
#define __KSU_H_SELINUX_HIDE

#include <linux/types.h>

void ksu_selinux_hide_init();
void ksu_selinux_hide_exit();
void ksu_selinux_hide_drop_backup_if_unused();
void ksu_selinux_hide_handle_second_stage();
void ksu_selinux_hide_handle_post_fs_data();

extern bool ksu_selinux_hide_running;
extern bool ksu_selinux_hide_enabled;
extern void initialize_fake_status(void);

struct page;
extern struct page *fake_status;

struct static_key_false;
extern struct static_key_false fake_status_initialize_key;

struct selinux_state;
extern struct selinux_state fake_state;

struct selinux_policy;
struct av_decision;
extern int security_context_to_sid_with_policy(struct selinux_policy *policy, const char *scontext, u32 scontext_len, u32 *sid, u32 def_sid, gfp_t gfp_flags);
extern int security_sid_to_context_with_policy(struct selinux_policy *policy, u32 sid, char **scontext, u32 *scontext_len);
extern void security_compute_av_user_with_policy(struct selinux_policy *policy, u32 ssid, u32 tsid, u16 tclass, struct av_decision *avd);

#endif
