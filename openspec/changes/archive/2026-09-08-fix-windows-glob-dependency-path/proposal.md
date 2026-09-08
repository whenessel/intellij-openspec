## Why

On Windows, generating an artifact whose completed dependency has a **glob-valued path** crashes with `Generation failed: Illegal char <*> at index …`. The OpenSpec CLI declares the `specs` artifact's dependency path as a glob (`specs/**/*.md`); when `specs` is complete and the user generates `tasks` (or any artifact that depends on `specs`), the plugin resolves that glob to a filesystem `Path` and Windows rejects the `*` as an illegal path character. POSIX platforms accept `*` in paths, so the same code silently degrades to a path-only reference there — which is why the defect is Windows-only and went unnoticed until reported (GitHub issue #20: PyCharm 2026.2.1 / Windows 11). Tasks generation is unusable for Windows users.

## What Changes

- Make the generation prompt's dependency-content inlining robust to **glob-valued dependency paths**: detect a glob path and fall back to a path-only reference instead of resolving it to a filesystem `Path`. This makes behavior identical on every OS and no longer depends on the OS tolerating glob metacharacters in a path.
- Add a defensive net so an unresolvable dependency path can never abort prompt assembly (catch the path-resolution failure in addition to the existing I/O failure), degrading to the path-only reference rather than surfacing "Generation failed".
- No change to what is inlined for real (non-glob) dependency files, and no change to POSIX behavior — this restores cross-OS parity, it does not add glob expansion.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `workflow`: add a requirement that the artifact-generation prompt tolerates a glob-valued dependency path on every OS — degrading to a path-only reference rather than crashing — so generation succeeds regardless of platform.

## Impact

- **Code:** `ArtifactInstruction.readDependencyContent` (and, by extension, `buildPrompt`) under `com.johnnyblabs.openspec.model`. Pure robustness fix; no API surface change.
- **Platforms:** fixes Windows; POSIX behavior unchanged. No change to the plugin's IntelliJ 2024.2+ compatibility.
- **CLI:** independent of OpenSpec CLI version — the glob dependency path is emitted by every supported CLI; the fix concerns how the plugin resolves it, not which CLI produced it.
- **Tests:** the crash only throws on Windows while CI runs on POSIX, so the fix is made testable by detecting glob paths explicitly (assertable on any OS) and is contract-tested against captured CLI output that carries the `specs/**/*.md` dependency.
