> **Current status — 2026-10-05:** SDK, full build/check/verifier and integration Kotlin compilation are available and passed through the normal environment proxy; see [P6 validation](p6-validation.md). Any SDK access failure below records the earlier investigation, not a current blocker. No network bypass was used.

# IntelliJ Platform SDK connection review — 2026-10-04

This is documentation research against the existing build, not an SDK/dependency change or successful build claim.

## Existing Gradle connection

The repository already follows the recommended Gradle approach. `settings.gradle.kts` applies `org.jetbrains.intellij.platform.settings` **2.18.1**, centralizes repositories with `FAIL_ON_PROJECT_REPOS`, and includes Maven Central and `intellijPlatform.defaultRepositories()`. `build.gradle.kts` applies `org.jetbrains.intellij.platform` without repeating the version. JetBrains explicitly documents this settings-plugin arrangement and version omission in its [Gradle configuration](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) and [FAQ](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html).

The SDK target is a dependency:

```kotlin
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }
}
```

The official [dependencies extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html) resolves the IDE distribution, platform classes, bundled plugin dependencies and test framework. Its default installer distribution includes JBR. Community/Ultimate helpers remain valid for targets before 2025.3, including this target. Multi-OS archives use `useInstaller = false` and require a separately available JBR for execution. This is an alternative configuration, not an implemented workaround.

The project compiles with Java 21 and Gradle wrapper 9.0.0. JetBrains maps IDEA **2024.2 → branch 242 → Java 21** and recommends building against the lowest supported platform; see [build ranges and Java requirements](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html). Current documentation examples use newer plugin/IDE versions; this research does not authorize upgrading the repository's pinned 2.18.1/2024.2 versions.

## Local IDE versus manually configured Plugin SDK

Gradle also accepts `dependencies { intellijPlatform { local("/absolute/path/to/IDE") } }`. That replaces the downloaded target with an actual installed/extracted IDE; it does not create an SDK from arbitrary JARs. `localPlatformArtifacts()` is needed and is already included by `defaultRepositories()`. See [local platform setup](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#local-intellij-platform-ide-instance). A matching usable IDE distribution must first exist; extra test framework/verifier dependencies can still need resolution.

The manual DevKit workflow adds JDK first, then File → Project Structure → SDKs → Add IntelliJ Platform Plugin SDK from disk, selects an IDE installation and internal JDK, and sets Sandbox Home. JetBrains describes this in [theme/DevKit environment setup](https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html). It is a separate older project model, not a missing step in this Gradle project. Plugin DevKit provides IDE development tooling; it is not itself the platform dependency. JetBrains recommends Gradle for behavior plugins in [Introduction to Plugin Development](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html).

## Import and source navigation

For this Gradle project, open its root build/settings files as a Gradle project, choose JDK 21 for the Gradle JVM and reload Gradle. Enable dependency source downloads to navigate platform source. JetBrains documents that source attachment is handled by Plugin DevKit and Gradle; a separate manually created Plugin SDK is not required by this build model. See [attaching platform sources](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#attaching-sources-in-the-ide).

| Component | Role in this repository |
| --- | --- |
| JDK 21 | Gradle toolchain and Java compilation for target platform 2024.2 |
| IntelliJ Platform dependency | Actual target IDE distribution and APIs resolved by `intellijIdeaCommunity("2024.2")` |
| Plugin DevKit | IDE-side plugin development support and source navigation; does not supply the target distribution |
| Platform test framework | Explicit `testFramework(TestFrameworkType.Platform)` dependency for IDE-backed tests |
| Starter/Driver | Separate existing `integrationTest` stack pinned to branch 242 for real UI smoke tests |

## Verification and current blocker

For this repository, import/reload the Gradle project with JDK 21. The [documented tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html) provide `runIde` for a development IDE, `buildPlugin` for the plugin archive and `verifyPlugin` for compatibility checks. Existing project `build` and `uiSmoke` gates must also pass. Source parsing and pure JUnit tests cannot establish platform API compatibility or UI behavior.

Earlier `build`, `buildPlugin`, `verifyPlugin` and `uiSmoke` attempts failed before SDK compilation because the environment proxy denied CONNECT with HTTP 403 before TLS. No cached usable target SDK was found in that investigation. This is an observed environment access failure, not evidence that Gradle declarations or JetBrains licensing are wrong. Documentation browsing uses a different tool path and does not prove shell/Gradle access.

JetBrains [repositories documentation](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html) lists installer downloads, platform releases/snapshots, runtime, Marketplace and additional dependency repositories. Isolated networks can use explicitly approved internal mirrors while retaining the local artifacts repository. A permitted matching local IDE plus the other required artifacts is another route. `--offline` cannot supply absent artifacts. Mirror setup, endpoint permissions and local SDK provisioning remain environment decisions; no such change or repeated blocked download was performed in this pass.
