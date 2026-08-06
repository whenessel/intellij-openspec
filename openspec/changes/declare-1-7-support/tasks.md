# Tasks — declare 1.7.x supported

## 1. Spec-level declaration

- [x] 1.1 `plugin-core` MODIFIED delta: add `1.7.x` to the supported-version list; add "The 1.7.x line is a supported generation" scenario (preserve the 1.3.0 floor + both existing scenarios verbatim)
- [x] 1.2 `openspec validate --strict` on the change is clean

## 2. Docs (vendor-neutral, public mirror)

- [x] 2.1 `openspec-support.md`: revise the spec-validator sentence to record the 1.6.0 parity test + the 1.6→1.7 verdict-stability check (the 1.7.x supported-line + per-line bullet already exist)
- [x] 2.2 README: concise `1.7.x` per-version note (do not reword the 1.3.0 floor)
- [x] 2.3 `docs/cli-versions/1.7.md` + index: already present (verify)

## 3. In-product strings + CHANGELOG

- [x] 3.1 Audit in-product range strings — all floor/window-based, none cap at 1.6 → no change (documented)
- [x] 3.2 CHANGELOG `Added`: OpenSpec CLI 1.7.x support (additive, user-outcome-worded)

## 4. Test + verification

- [x] 4.1 `CliVersionAtLeastTest`: assert `1.6.0` and `1.7.0` clear the 1.3.0 floor
- [x] 4.2 `./gradlew build` green; re-measure coverage (unchanged — floors already ratcheted)
- [x] 4.3 Leak grep over changed public files; doc-fidelity pass across the surfaces
