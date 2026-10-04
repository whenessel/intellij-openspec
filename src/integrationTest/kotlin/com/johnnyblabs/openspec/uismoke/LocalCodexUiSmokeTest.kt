package com.johnnyblabs.openspec.uismoke

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Real 242 UI and production routing/writer; only the two CLI executables are offline mocks. */
class LocalCodexUiSmokeTest {
    @Remote("javax.swing.AbstractButton")
    interface ButtonRef {
        fun doClick()
        fun isEnabled(): Boolean
    }

    @Remote("com.johnnyblabs.openspec.ai.AiExecutionService", plugin = "com.johnnyblabs.openspec")
    interface ExecutionRef {
        fun hasActiveRequest(): Boolean
    }

    // Uses the same Remote + EDT pattern as existing OpenSpecUiSmokeTest. No production test hook.
    private fun Driver.press(name: String, title: String? = null) {
        val button = if (title == null) ui.x { byAccessibleName(name) }
            else ui.x { byTitle(title) }.x { byAccessibleName(name) }
        await("enabled $name button") {
            button.present() && withContext(OnDispatcher.EDT) { cast(button.component, ButtonRef::class).isEnabled() }
        }
        // These controls close an existing dialog or schedule background work; none opens a nested modal.
        // Physical robot clicks are unreliable in this harness; reuse its Remote/EDT pattern.
        withContext(OnDispatcher.EDT) { cast(button.component, ButtonRef::class).doClick() }
    }

    private fun await(what: String, timeout: Duration = 90.seconds, probe: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            if (runCatching(probe).getOrDefault(false)) return
            Thread.sleep(100) // Bounded polling of observable UI/transport state, never stage timing.
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun copyTree(source: Path, target: Path) {
        Files.walk(source).use { paths -> paths.forEach { path ->
            val dest = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) Files.createDirectories(dest)
            else Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
        } }
    }

    private fun xml(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private fun planningSnapshot(root: Path): Map<String, String> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.toList().associate {
            root.relativize(it).toString() to Files.readString(it)
        }
    }

    @Test
    fun settingsContextCancelAndTwoFilePreviewSave() {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"),
            "Restricted Codex MVP and executable fixtures target Linux/macOS")
        val python = ProcessBuilder("python3", "--version").start()
        check(python.waitFor() == 0) { "Offline mock fixture requires Python 3" }
        val demo = Path.of(System.getProperty("demo.project.path")).toAbsolutePath()
        val repository = demo.parent.parent.parent.parent
        val root = Files.createTempDirectory("openspec-codex-ui-")
        val project = root.resolve("project")
        copyTree(demo, project)
        val mock = root.resolve("mock executables with spaces")
        copyTree(demo.parent.resolve("local-codex-mock"), mock)
        val captures = repository.resolve("src/test/resources/fixtures")
        Files.copy(captures.resolve("codex/0.160.0/handshake.json"), mock.resolve("handshake.json"))
        for (name in listOf("status.json", "instructions-specs.json")) {
            Files.copy(captures.resolve("cli/$name"), mock.resolve(name))
        }
        val codex = mock.resolve("mock-codex.py")
        val openspec = mock.resolve("mock-openspec.py")
        check(codex.toFile().setExecutable(true) && openspec.toFile().setExecutable(true))
        val change = project.resolve("openspec/changes/demo-add-farewell")
        Files.writeString(change.resolve("design.md"), "## Design\nKeep the existing greeting and add a reusable farewell message.\n")
        Files.writeString(change.resolve("tasks.md"), "## Implementation\n- [x] Define the disposable smoke fixture without modifying application sources.\n")
        val before = planningSnapshot(project.resolve("openspec"))
        Files.createDirectories(project.resolve(".idea"))
        // Persisted settings are input fixtures, not a bypass of runtime routing. The real Settings
        // configurable must render, probe, apply and close before any request is allowed.
        Files.writeString(project.resolve(".idea/openspec.xml"), """
            <project version="4"><component name="OpenSpecSettings">
              <option name="aiSettingsVersion" value="1" />
              <option name="aiBackend" value="LOCAL_CODEX" />
              <option name="codexExecutable" value="${xml(codex.toString())}" />
              <option name="cliPath" value="${xml(openspec.toString())}" />
              <option name="preferredDeliveryMethod" value="DIRECT_API" />
              <option name="setupCompleted" value="true" />
            </component></project>
        """.trimIndent())
        val log = mock.resolve("methods.jsonl")
        fun calls(method: String): Int = if (!Files.exists(log)) 0 else
            Files.readAllLines(log).count { it.contains("\"method\": \"$method\"") }

        val context = Starter.newContext(CurrentTestMethod.hyphenateWithClass(),
            TestCase(IdeProductProvider.IC, LocalProjectInfo(project)).withVersion("2024.2")).apply {
            PluginConfigurator(this).installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin")))
            applyVMOptionsPatch { withEnv("XDG_DATA_HOME", root.resolve("xdg").toString()) }
        }
        context.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            invokeAction("ShowSettings", now = false)
            ideFrame {
                await("settings opens") {
                    x { byType("com.intellij.openapi.ui.impl.DialogPanelWrapper") }.present() ||
                        x { byAccessibleName("Settings") }.present()
                }
                keyboard { enterText("OpenSpec", 50); enter() }
                await("Codex settings render") { hasText("Codex executable:") }
            }
            press("Refresh status and models (no inference)")
            ideFrame { await("reported ChatGPT auth and model catalog") {
                hasSubtext("Auth: chatgpt") && hasSubtext("Model catalog:") && !hasSubtext("Model catalog: not refreshed")
            } }
            assertEquals(0, calls("turn/start"), "Settings refresh must never perform inference")
            press("OK")
            await("Settings closes") { ui.x { byAccessibleName("Settings") }.notPresent() }
            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            invokeAction("OpenSpec.Explore", now = false)
            press("Send") // Empty topic is supported; uses the seeded selected-change context.
            val review = "Review AI Context and Destination"
            await("context review") { ui.x { byTitle(review) }.present() }
            assertEquals(0, calls("turn/start"), "Context has not been accepted yet")
            assertEquals(before, planningSnapshot(project.resolve("openspec")))
            press("Send Reviewed Context", review)
            ideFrame { await("streamed delta rendered") { hasSubtext("UI smoke streamed response awaiting cancellation") } }
            press("Cancel")
            await("transport interruption") { calls("turn/interrupt") == 1 }
            ideFrame { await("cancellation recovery") { hasSubtext("Exploration cancelled") } }
            assertEquals(before, planningSnapshot(project.resolve("openspec")), "Canceled/late output cannot write files")
            assertFalse(ui.x { byTitle("Review Generated specs") }.present(), "Canceled Explore cannot open file preview")
            val execution = service(ExecutionRef::class, singleProject())
            await("canceled execution releases its active run") {
                withContext(OnDispatcher.EDT) { !execution.hasActiveRequest() }
            }
            press("New conversation")

            invokeAction("OpenSpec.Continue", now = false)
            await("generation context review", 2.minutes) { ui.x { byTitle(review) }.present() }
            assertEquals(1, calls("turn/start"), "Continue must wait for its own context acceptance")
            press("Send Reviewed Context", review)
            val preview = "Review Generated specs"
            await("two-file result preview", 2.minutes) { ui.x { byTitle(preview) }.present() }
            val previewUi = ui.x { byTitle(preview) }
            assertTrue(previewUi.hasSubtext("2 files"), "Preview must expose the complete batch")
            assertTrue(previewUi.hasSubtext("specs/mock-alpha/spec.md"))
            assertTrue(previewUi.hasSubtext("specs/mock-beta/spec.md"))
            assertEquals(before, planningSnapshot(project.resolve("openspec")), "Preview must not apply files")
            press("Apply Reviewed Files", preview)
            for (name in listOf("mock-alpha", "mock-beta")) {
                val output = change.resolve("specs/$name/spec.md")
                await("accepted $name written") { Files.exists(output) }
                assertEquals(Files.readString(mock.resolve("$name.md")), Files.readString(output))
            }
            assertEquals(2, calls("turn/start"), "One canceled Explore and one accepted generation; no hidden retry")
            assertFalse(Files.exists(change.resolve("specs/**/*.md")), "Glob is never a concrete output filename")
            val after = planningSnapshot(project.resolve("openspec"))
            assertEquals(2, after.size - before.size)
            assertEquals(before, after.filterKeys { it in before }, "Existing planning files must remain untouched")
        }
        // Project-close disposal must not create additional late output.
        val closed = planningSnapshot(project.resolve("openspec"))
        assertEquals(2, closed.size - before.size)
        assertEquals(before, closed.filterKeys { it in before })
    }
}
