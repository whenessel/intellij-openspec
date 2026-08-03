# Compliance (delta)

The plugin-invented "Compliance / Pre-Flight" concept is removed. Upstream OpenSpec has no
"compliance"; its word for the pre-archive check is **verify**. Every requirement below is absorbed
— restated in verify vocabulary — by the `verify-workflow` capability's ADDED "Single Verify surface
with three archive-readiness states" requirement (and its MODIFIED "Verification report"). Nothing is
dropped: the three-dimension check, the gated dialog, the notification group, the status chip, and
the result dialog all continue to exist under Verify.

## REMOVED Requirements

### Requirement: Three-category compliance check

**Reason:** Folded into `verify-workflow` as the "Three-dimension check" scenario (completeness,
validation, spec-sync) of the single Verify surface.

### Requirement: Compliance pre-flight dialog

**Reason:** Replaced by the three-state Verify dialog (`verify-workflow`), which reserves red/blocked
for a hard validation failure and renders unfinished tasks as a neutral, bypassable "In Progress".

### Requirement: Compliance notification group

**Reason:** Replaced by the `OpenSpec.Verify` notification group (`verify-workflow` → "Verify
notification group" scenario). `OpenSpec.Compliance` is no longer registered.

### Requirement: Compliance status chip in workflow panel

**Reason:** Replaced by the Verify status chip (`verify-workflow` → "Verify status chip" scenario),
whose three states use verify vocabulary and a neutral in-progress treatment.

### Requirement: Compliance result dialog

**Reason:** Same surface as the pre-flight dialog above — the single Verify dialog with per-category
sections. No separate result dialog remains.
