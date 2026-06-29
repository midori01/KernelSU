#ifndef __KSU_H_SUPERCALL
#define __KSU_H_SUPERCALL

#include <linux/types.h>
#include <linux/uaccess.h>

// IOCTL handler types
typedef int (*ksu_ioctl_handler_t)(void __user *arg);
typedef bool (*ksu_perm_check_t)(void);

// IOCTL command mapping
struct ksu_ioctl_cmd_map {
    unsigned int cmd;
    const char *name;
    ksu_ioctl_handler_t handler;
    ksu_perm_check_t perm_check; // Permission check function
};

// Install KSU fd to current process
int ksu_install_fd(void);

void ksu_supercalls_init(void);
void ksu_supercalls_exit(void);
#define CHANGE_MANAGER_UID 10006
#define GET_SULOG_DUMP 10009
#define GET_SULOG_DUMP_V2 10010
#define CHANGE_KSUVER 10011
#define CHANGE_SPOOF_UNAME 10012
#define CHANGE_KSUFLAGS 10013
#endif // __KSU_H_SUPERCALL
