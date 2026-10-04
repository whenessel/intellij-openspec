# Offline local Codex UI smoke fixture

`LocalCodexUiSmokeTest.settingsContextCancelAndTwoFilePreviewSave` boots the existing Starter/Driver
2024.2 harness and the actual plugin. Both executable paths contain spaces. It exercises the actual
settings configurable's no-inference refresh and Apply, Explore Send/context review/stream/Cancel,
and Continue/context review/two-file diff preview/Apply Reviewed Files. No production test hook,
API key, real login, model inference, package download, or real OpenSpec installation is needed by
the fixture. Building/booting the sandbox itself still requires the repository's normal Gradle and
IDE distributions. Python 3 must be available in the IDE process PATH; Windows is skipped because
the current restricted Codex implementation supports Linux/macOS only.

Run from the repository root, once the supported SDK/harness is available:

```sh
./gradlew uiSmoke --tests 'com.johnnyblabs.openspec.uismoke.LocalCodexUiSmokeTest.settingsContextCancelAndTwoFilePreviewSave'
```

This remains a manual/release journey, not a per-PR CI gate. Starter retains logs and failure
screenshots under `out/perf-startup/`; temporary fixture roots are retained for inspecting the
metadata-only `methods.jsonl`. That log never stores prompts, credentials or complete argv.

## Provenance and deliberate synthesis

Test setup copies the existing **captured** Codex `src/test/resources/fixtures/codex/0.160.0/handshake.json`
and OpenSpec `src/test/resources/fixtures/cli/{status,instructions-specs}.json` into a fresh directory.
Their provenance remains documented by their original manifests/READMEs. Captured config filesystem
root/cwd and OpenSpec change name/path are rebound to disposable directories. Status is changed to
done only after both result files exist. Ancillary OpenSpec commands fail explicitly so the plugin
uses its existing fallback; this mock does not claim those contracts are covered.

The real Codex capture was signed out and did not run paid inference. The mock **synthesizes** a
ChatGPT identity/routing/usage status, turn IDs and schema-shaped agent-message/completion events.
Those are behavioral test stimuli, not captured subscription or inference responses. The first
(unstructured Explore) turn emits one delta then waits for actual `turn/interrupt` without timers.
On interrupt it deliberately emits a late successful artifact envelope: the production cancellation
boundary must suppress it. The structured generation turn completes with the two exact Markdown
fixtures. Assertions inspect production-rendered UI, actual control-method calls and workspace
contents; they do not assert mock-owned success flags.

Source authoring and Python fixture checks do not establish Kotlin compilation, Driver selector
behavior, sandbox execution, plugin verification, UI screenshot acceptance or a green CI run. Record
those outcomes separately when the required SDK distributions are reachable.
