#!/usr/bin/env bash
# Usage: apply-sct-sync.sh <header> (run from the xxKSU source root)
set -u

header=$1
ksu=kernel/ksu.c
bl=kernel/hook/branch_link_hook_arm64.c
sct=kernel/hook/syscall_table_hook_arm64.c

skip() {
  git checkout -- "$ksu" "$bl" "$sct" 2>/dev/null
  rm -f kernel/downstream/midori_sct_sync.h
  echo "::warning::xxKSU sys_call_table sync not applied: $1"
  exit 0
}

for f in "$ksu" "$bl" "$sct"; do
  [ -f "$f" ] || skip "missing $f"
done

cp "$header" kernel/downstream/midori_sct_sync.h
sed -i '/^#include "downstream\/arm64_syscall_table_mirror.h"/a #include "downstream/midori_sct_sync.h"' "$ksu"
sed -i '/^[[:space:]]*try_mirror_syscall_table();/a \	ksu_sct_sync_init();' "$bl"
sed -i '/^[[:space:]]*return __arm64_sys_reboot(regs);/i \	KSU_SCT_FORWARD_REBOOT(regs);' "$sct"

[ "$(grep -c '^#include "downstream/midori_sct_sync.h"' "$ksu")" = 1 ] || skip "include anchor"
[ "$(grep -c 'ksu_sct_sync_init();' "$bl")" = 1 ] || skip "mirror init anchor"
[ "$(grep -c 'KSU_SCT_FORWARD_REBOOT(regs);' "$sct")" -ge 1 ] || skip "reboot hook anchor"

echo "xxKSU sys_call_table sync applied"
