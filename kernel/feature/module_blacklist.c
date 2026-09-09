#include <asm/barrier.h>
#include <linux/capability.h>
#include <linux/compiler.h>
#include <linux/errno.h>
#include <linux/init.h>
#include <linux/moduleparam.h>
#include <linux/slab.h>
#include <linux/smp.h>
#include <linux/string.h>

#include "feature/module_blacklist.h"
#include "infra/symbol_resolver.h"
#include "klog.h" // IWYU pragma: keep
#include "ksu.h"

#if defined(CONFIG_MODULES) && (defined(CONFIG_ARM64) || defined(CONFIG_X86_64) || defined(CONFIG_RISCV))
#define KSU_MODULE_BLACKLIST_GUARD

#include <asm/unistd.h>

#include "hook/syscall_hook.h"
#include "runtime/ksud_boot.h"
#endif

// Block kernel modules from loading by reusing the kernel's native
// `module_blacklist` (consulted by blacklisted() before a module is laid out),
// instead of hooking init_module/finit_module. It is the standard mechanism and
// needs nothing maintained on our side.
//
// The list is supplied via the `block_modules=name1,name2,...` module param,
// which flows in through ksu_config (boot-patch / boot-patch-v2) or the kernel
// cmdline (built-in). Names use the kernel form (underscores, e.g.
// `oplus_secure_guard`).
//
// Developer only: a blocked module fails to load with EPERM, which first stage
// init treats as fatal for anything in modules.load / modules.load.recovery
// that is not in modules.blocklist. Picking safe names is up to the user.
//
// With KSU_MODULE_BLACKLIST_GUARD, that EPERM is masked until boot completes,
// so first stage init does not abort on the blocked module itself.

static char block_modules[256];

#ifdef KSU_MODULE_BLACKLIST_GUARD
static syscall_fn_t orig_sys_init_module;
static syscall_fn_t orig_sys_finit_module;
static bool guard_armed;
static bool init_module_hooked;
static bool finit_module_hooked;

static long ksu_sys_init_module(const struct pt_regs *regs)
{
    long ret = orig_sys_init_module(regs);

    if (ret == -EPERM && !READ_ONCE(ksu_boot_completed) && capable(CAP_SYS_MODULE))
        return 0;

    return ret;
}

static long ksu_sys_finit_module(const struct pt_regs *regs)
{
    long ret = orig_sys_finit_module(regs);

    if (ret == -EPERM && !READ_ONCE(ksu_boot_completed) && capable(CAP_SYS_MODULE))
        return 0;

    return ret;
}

static void arm_module_blacklist_guard(void)
{
    syscall_fn_t *table;

    ksu_syscall_table_hook(__NR_init_module, ksu_sys_init_module, &orig_sys_init_module);
    ksu_syscall_table_hook(__NR_finit_module, ksu_sys_finit_module, &orig_sys_finit_module);

    table = READ_ONCE(ksu_syscall_table);
    WRITE_ONCE(init_module_hooked, table && READ_ONCE(table[__NR_init_module]) == ksu_sys_init_module);
    WRITE_ONCE(finit_module_hooked, table && READ_ONCE(table[__NR_finit_module]) == ksu_sys_finit_module);
    WRITE_ONCE(guard_armed, READ_ONCE(init_module_hooked) && READ_ONCE(finit_module_hooked) &&
                                orig_sys_init_module && orig_sys_finit_module);
    if (READ_ONCE(guard_armed))
        pr_info("module_blacklist: guard armed\n");
}
#endif

// The param may be parsed before the slab allocator is up (built-in), so copy
// into a static buffer instead of allocating. Truncate rather than fail: a
// param error would abort the whole KernelSU load.
static int block_modules_param_set(const char *val, const struct kernel_param *kp)
{
    if (!val || !val[0])
        return 0;

    if (strscpy(block_modules, val, sizeof(block_modules)) < 0)
        pr_err("module_blacklist: block_modules too long, truncated — some modules will not be blocked\n");

    return 0;
}

static const struct kernel_param_ops block_modules_param_ops = {
    // A bare `block_modules` would otherwise fail parse_args() and the load
    .flags = KERNEL_PARAM_OPS_FL_NOARG,
    .set = block_modules_param_set,
};
module_param_cb(block_modules, &block_modules_param_ops, NULL, 0);

// Merge the configured names into the kernel's native module_blacklist.
void __init ksu_module_blacklist_init(void)
{
    char **native_blacklist;
    const char *existing;
    char *merged;
    size_t existing_len;

#ifdef KSU_MODULE_BLACKLIST_GUARD
    pr_info("module_blacklist: guard available\n");
#endif

    if (!block_modules[0])
        return;

    native_blacklist = (char **)find_kernel_symbol_exact("module_blacklist");
    if (!native_blacklist) {
        pr_warn("module_blacklist: symbol not found, cannot block modules\n");
        return;
    }

    existing = *native_blacklist;
    existing_len = existing ? strlen(existing) : 0;

    // Merge with any existing blacklist (e.g. one set on the kernel cmdline).
    merged = kasprintf(GFP_KERNEL, "%s%s%s", existing_len ? existing : "", existing_len ? "," : "", block_modules);
    if (!merged) {
        pr_err("module_blacklist: cannot allocate blacklist\n");
        return;
    }

#ifdef KSU_MODULE_BLACKLIST_GUARD
    if (!ksu_late_loaded)
        arm_module_blacklist_guard();
#endif

    // Release-publish: pairs with the address dependency in the lockless
    // blacklisted() reader so it can never observe the new pointer before the
    // string contents are visible.
    smp_store_release(native_blacklist, merged);
    // Force every other CPU through a memory barrier so any module load already
    // in flight observes the new list right away (suggested by @backslashxx).
    kick_all_cpus_sync();

    pr_info("module_blacklist: blocking modules: %s\n", merged);
}

void ksu_module_blacklist_drop_guard(void)
{
#ifdef KSU_MODULE_BLACKLIST_GUARD
    if (READ_ONCE(init_module_hooked)) {
        ksu_syscall_table_unhook(__NR_init_module);
        WRITE_ONCE(init_module_hooked, false);
    }
    if (READ_ONCE(finit_module_hooked)) {
        ksu_syscall_table_unhook(__NR_finit_module);
        WRITE_ONCE(finit_module_hooked, false);
    }
    WRITE_ONCE(guard_armed, false);
#endif
}

void __exit ksu_module_blacklist_exit(void)
{
    // Intentionally does NOT restore the original module_blacklist pointer.
    // The module loader reads module_blacklist in blacklisted() without any
    // lock or READ_ONCE, testing it for NULL and then dereferencing it; swapping
    // the pointer back (especially to the common NULL) could race that reader
    // into a NULL dereference. The merged string already contains any
    // pre-existing entries and lives on the kernel heap, so leaving it installed
    // stays valid even after KernelSU is unloaded. The one-time, bounded
    // allocation is deliberately leaked.
}
