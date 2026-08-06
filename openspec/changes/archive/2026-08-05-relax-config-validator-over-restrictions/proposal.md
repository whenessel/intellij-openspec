# Relax config-validator over-restrictions to match the OpenSpec CLI

## Why

The plugin's built-in validator and config inspection warn on several `config.yaml` states the OpenSpec CLI accepts clean — a breach of the invariant that **the plugin must never be more restrictive than the CLI, for any version.** Verified against the real 1.7.0 CLI:

- A `version:` value that isn't the single `1.2.0` config-format baseline raises `config-version-unknown`, but `version:` is a plugin-internal field the CLI strips and never reads — `openspec validate` is clean for *any* `version:`.
- A missing `schema:` raises a WARNING squiggle, but the CLI defaults to `spec-driven` and validates clean — a warning on a CLI-clean file is still "stricter."
- A non-built-in `schema:` (a legitimate `openspec schema fork`) falsely reds when the CLI is unavailable, because the known-set collapses to the built-in floor — yet the real CLI never rejects a schema name.
- The config inspection nags for a `profile:` field, but `profile` is the *global* workflow profile (`openspec config profile`), never a project `config.yaml` field, and is absent from a clean `openspec init` config and from upstream's Zod schema.
- The Explore AI-prompt surfaces the plugin-internal `version:` as if it were real project config.

## What Changes

- **Delete** the `config-version-unknown` warning (keep the `getVersion()` reader — it's the config-format-axis fallback).
- **Demote** the missing-`schema:` nudge from WARNING to **INFO** (advisory-only) in both the built-in validator and the inspection — a genuine hygiene hint that never reds a CLI-clean file.
- **Guard** `config-schema-invalid` and `change-schema-incompatible` so they fire only when the schema known-set is authoritative (CLI available + schema-supported); offline, a custom fork no longer falsely warns, while a genuine typo still warns when the CLI supplies the real set.
- **Remove** the `profile:` inspection nag (aligns the inspection with the validation spec, which already accepts an absent `profile`).
- **Stop surfacing** `version:` in the Explore AI-prompt context; fix stale comments that claimed the tree reads `version`/`profile`.

## Capabilities

- **validation** — MODIFIED: the *Config validation* requirement drops the `version:`-unknown warning, makes the missing-`schema:` nudge advisory (INFO), and gates schema-name recognition on an authoritative known-set (no false warning when the CLI is down).

## Impact

- No plugin surface reds or warns on a `config.yaml` state the CLI validates clean; a custom-forked schema no longer falsely warns offline.
- The load-bearing readers (`getVersion`/`getEffectiveVersion`, `getProfile`) and the `VersionSupport` config-format axis pin are **unchanged**.
- Each demotion is backed by captured real 1.7.0 CLI output under `fixtures/cli/1.7.0/config-validation/` (a re-capture tripwire), and the schema-recognition guard is covered in both directions (CLI-down → clean, CLI-authoritative → still warns).
