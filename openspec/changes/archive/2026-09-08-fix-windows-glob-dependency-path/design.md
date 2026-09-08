## Context

See proposal.md — Why. The generation prompt inlines each completed dependency's content by resolving `changeDir` + the dependency's declared path to a filesystem path and reading it. The `specs` dependency's declared path is the glob `specs/**/*.md`. On Windows, resolving that to a `Path` throws (`*` is an illegal path character); on POSIX it resolves to a nonexistent literal path and the read silently yields nothing. The failure surfaces only when a glob-valued dependency is *completed* (the crash trigger), which is exactly the state reached before generating `tasks`.

Constraint that shapes the fix: **the crash only throws on Windows, but CI runs on POSIX.** A fix that relies on the OS throwing cannot be regression-tested on CI.

## Goals / Non-Goals

**Goals:**
- Generation succeeds on every OS when a completed dependency has a glob-valued path.
- The fix is verifiable on POSIX CI — the test must fail against today's code and pass after the fix, without depending on the OS throwing.

**Non-Goals:**
- Glob **expansion / inlining** of the matched spec files. Out of scope by decision (a separate prompt-quality enhancement; it would need an upstream-model check on whether spec bodies are meant to be inlined). This change only restores cross-OS parity.
- The sibling paths that also combine `changeDir` with the glob `outputPath` (the post-generation file watcher; the Direct-API write path when generating `specs` itself). Those degrade quietly rather than crashing; noted as a follow-up, not fixed here.

## Decisions

**1. Detect glob-valued paths explicitly; do not rely on `Path.of` throwing.**
Before resolving a dependency path to a filesystem path, test it for glob metacharacters (`*`, `?`, `[`). If present, treat it as a reference (return the path-only fallback) and never call `Path.of` on it.
- *Why over catching the exception:* (a) testable on any OS — POSIX CI can assert a glob path is never read, whereas the `InvalidPathException` it would need to assert never fires on POSIX; (b) semantically correct everywhere — a glob is not a single file on any platform, so resolving-and-stat-ing it is meaningless even on POSIX (it already returns nothing there today); (c) no dependence on OS-specific exception behavior.
- *Alternative considered — only catch `InvalidPathException`:* rejected as the primary mechanism. It stops the crash but is unverifiable on POSIX CI and leaves the code still attempting to stat a glob. Kept as a secondary net (below).

**2. Keep a path-resolution safety net (defense-in-depth).**
Broaden the existing `catch` so a path-resolution failure (in addition to the current I/O failure) also degrades to the path-only reference rather than propagating. This guarantees no dependency path can abort prompt assembly even if some future path escapes the glob check.

**3. Scope the change to the read-a-dependency step.**
The fix lives entirely in how a single dependency's content is resolved; `buildPrompt`'s structure, ordering, and output are unchanged. This keeps the blast radius to one method and preserves the existing behavior for concrete dependency files.

## Risks / Trade-offs

- **A real filename legitimately containing `[` or `?`** is misread as a glob → it degrades to a path-only reference (the same graceful fallback), never a crash or lost content. In practice dependency paths come from the CLI (`proposal.md`, `design.md`, `tasks.md`, `specs/**/*.md`) — only the specs glob carries such characters. Acceptable.
- **POSIX behavior change** → none. `readDependencyContent` already returns null for the glob on POSIX (no literal match), so the observable prompt is unchanged there; only Windows changes (stops crashing).
- **Under-fix (siblings still mishandle the glob)** → out of scope by decision; those are non-crashing and tracked as follow-up, so shipping this does not regress anything.

## Migration Plan

Pure fix — no migration. Rollback is a straight revert of the one method.
