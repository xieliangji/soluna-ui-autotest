package com.soluna.ui.autotest.cli

import com.soluna.ui.autotest.appium.server.AppiumServerHandle
import com.soluna.ui.autotest.config.DeviceAppiumDefinition
import com.soluna.ui.autotest.config.DeviceAppiumServerDefinition
import com.soluna.ui.autotest.config.DeviceConfigDefinition
import com.soluna.ui.autotest.config.DeviceDefinition
import com.soluna.ui.autotest.core.execution.CaseExecutionResult
import com.soluna.ui.autotest.core.execution.ExecutionStatus
import com.soluna.ui.autotest.core.execution.PlanExecutionResult
import com.soluna.ui.autotest.core.execution.StageExecutionResult
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.report.LocalReportWriter
import com.soluna.ui.autotest.runner.PlanRunRequest
import com.soluna.ui.autotest.runner.PlanRunResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolunaCliApplicationTest {
    @Test
    fun `prints help without running plan`() {
        var called = false
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runPlan = {
                called = true
                planRunResult(it, ExecutionStatus.PASSED)
            },
        )

        val exitCode = cli.run(arrayOf("--help"), stdout, stderr)

        assertEquals(0, exitCode)
        assertTrue(stdout.toString().contains("soluna run <plan.yaml>"))
        assertEquals("", stderr.toString())
        assertEquals(false, called)
    }

    @Test
    fun `run command only requires plan path`() {
        var capturedRequest: PlanRunRequest? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runPlan = { request ->
                capturedRequest = request
                planRunResult(request, ExecutionStatus.PASSED)
            },
        )

        val exitCode = cli.run(arrayOf("run", "plans/main.yaml"), stdout, stderr)

        val request = assertNotNull(capturedRequest)
        assertEquals(0, exitCode)
        assertEquals(Path.of("plans/main.yaml"), request.planPath)
        assertTrue(request.runId.startsWith("run-"))
        assertEquals(emptyMap(), request.parameterOverrides)
        assertIs<LocalReportWriter>(request.reportWriter)
        assertEquals(Path.of("build/soluna-runs"), request.localArtifactRoot)
        assertTrue(stdout.toString().contains("status: passed"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `run command accepts narrow optional overrides`() {
        var capturedRequest: PlanRunRequest? = null
        val reportRoot = Files.createTempDirectory("soluna-cli-report-root")
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runPlan = { request ->
                capturedRequest = request
                planRunResult(request, ExecutionStatus.FAILED)
            },
        )

        val exitCode = cli.run(
            arrayOf(
                "run",
                "plans/main.yaml",
                "--run-id",
                "cli-run",
                "--param",
                "nickname=SolunaCLI",
                "--param=retries=2",
                "--param=enabled=true",
                "--report-root",
                reportRoot.toString(),
                "--expect",
                "failed",
            ),
            stdout,
            stderr,
        )

        val request = assertNotNull(capturedRequest)
        assertEquals(0, exitCode)
        assertEquals("cli-run", request.runId)
        assertEquals("SolunaCLI", request.parameterOverrides.getValue("nickname").asText())
        assertEquals(2, request.parameterOverrides.getValue("retries").asInt())
        assertEquals(true, request.parameterOverrides.getValue("enabled").asBoolean())
        assertEquals(reportRoot, request.localArtifactRoot)
        assertIs<LocalReportWriter>(request.reportWriter)
        assertTrue(stdout.toString().contains("status: failed"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `rejects non-plan configuration options`() {
        var capturedRequest: PlanRunRequest? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runPlan = { request ->
                capturedRequest = request
                planRunResult(request, ExecutionStatus.PASSED)
            },
        )

        val exitCode = cli.run(
            arrayOf("run", "plans/main.yaml", "--device-config", "devices/android.yaml"),
            stdout,
            stderr,
        )

        assertEquals(2, exitCode)
        assertNull(capturedRequest)
        assertEquals("", stdout.toString())
        assertTrue(stderr.toString().contains("Unknown option '--device-config'"))
    }

    @Test
    fun `returns failure exit code when expected status does not match result`() {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runPlan = { request -> planRunResult(request, ExecutionStatus.FAILED) },
        )

        val exitCode = cli.run(arrayOf("run", "plans/main.yaml"), stdout, stderr)

        assertEquals(1, exitCode)
        assertTrue(stdout.toString().contains("status: failed"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `debug input command passes locator and text to debug manager`() {
        var capturedRequest: AppiumDebugRequest? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runDebug = { request ->
                capturedRequest = request
                AppiumDebugResult(
                    action = request.action.name,
                    serverUrl = "http://127.0.0.1:4723",
                    wdaUrl = null,
                    sessionId = "session-debug",
                    output = "ok",
                )
            },
        )

        val exitCode = cli.run(
            arrayOf(
                "debug",
                "plans/ios.yaml",
                "input",
                "--strategy",
                "class chain",
                "--locator",
                "**/XCUIElementTypeTextView",
                "--text",
                "少于十字",
                "--clear-first",
                "false",
            ),
            stdout,
            stderr,
        )

        val request = assertNotNull(capturedRequest)
        val action = assertIs<AppiumDebugAction.Input>(request.action)
        assertEquals(0, exitCode)
        assertEquals(Path.of("plans/ios.yaml"), request.planPath)
        assertEquals("class chain", action.locator.strategy)
        assertEquals("**/XCUIElementTypeTextView", action.locator.value)
        assertEquals("少于十字", action.text)
        assertEquals(false, action.clearFirst)
        assertTrue(stdout.toString().contains("action: input"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `debug restart app command passes app id to debug manager`() {
        var capturedRequest: AppiumDebugRequest? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runDebug = { request ->
                capturedRequest = request
                AppiumDebugResult(
                    action = request.action.name,
                    serverUrl = "http://127.0.0.1:4723",
                    wdaUrl = null,
                    sessionId = "session-debug",
                    output = "restart-app: com.ugreen.iot",
                )
            },
        )

        val exitCode = cli.run(
            arrayOf(
                "debug",
                "plans/android.yaml",
                "restart-app",
                "--app-id",
                "com.ugreen.iot",
                "--keep-infra",
            ),
            stdout,
            stderr,
        )

        val request = assertNotNull(capturedRequest)
        val action = assertIs<AppiumDebugAction.RestartApp>(request.action)
        assertEquals(0, exitCode)
        assertEquals(Path.of("plans/android.yaml"), request.planPath)
        assertEquals("com.ugreen.iot", action.appId)
        assertEquals(true, request.keepInfrastructure)
        assertTrue(stdout.toString().contains("action: restart-app"))
        assertTrue(stdout.toString().contains("restart-app: com.ugreen.iot"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `debug swipe command passes viewport ratios to debug manager`() {
        var capturedRequest: AppiumDebugRequest? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runDebug = { request ->
                capturedRequest = request
                AppiumDebugResult(
                    action = request.action.name,
                    serverUrl = "http://127.0.0.1:4723",
                    wdaUrl = null,
                    sessionId = "session-debug",
                    output = "swipe: start=0.5,0.8, end=0.5,0.2, durationMs=650",
                )
            },
        )

        val exitCode = cli.run(
            arrayOf(
                "debug",
                "plans/ios.yaml",
                "swipe",
                "--start-x-ratio",
                "0.5",
                "--start-y-ratio",
                "0.8",
                "--end-x-ratio",
                "0.5",
                "--end-y-ratio",
                "0.2",
                "--duration-ms",
                "650",
            ),
            stdout,
            stderr,
        )

        val request = assertNotNull(capturedRequest)
        val action = assertIs<AppiumDebugAction.Swipe>(request.action)
        assertEquals(0, exitCode)
        assertEquals(Path.of("plans/ios.yaml"), request.planPath)
        assertEquals(0.5, action.startXRatio)
        assertEquals(0.8, action.startYRatio)
        assertEquals(0.5, action.endXRatio)
        assertEquals(0.2, action.endYRatio)
        assertEquals(650L, action.durationMs)
        assertTrue(stdout.toString().contains("action: swipe"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `debug shell command delegates to shell runner`() {
        var capturedRequest: AppiumDebugShellRequest? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication(
            runDebugShell = { request, out ->
                capturedRequest = request
                out.appendLine("shell body")
                AppiumDebugShellResult(
                    serverUrl = "http://127.0.0.1:4723",
                    wdaUrl = "http://127.0.0.1:8100",
                    sessionId = "session-shell",
                    commandCount = 2,
                )
            },
        )

        val exitCode = cli.run(arrayOf("debug", "plans/ios.yaml", "shell", "--keep-infra"), stdout, stderr)

        val request = assertNotNull(capturedRequest)
        assertEquals(0, exitCode)
        assertEquals(Path.of("plans/ios.yaml"), request.planPath)
        assertEquals(true, request.keepInfrastructure)
        assertTrue(stdout.toString().contains("shell body"))
        assertTrue(stdout.toString().contains("commands: 2"))
        assertEquals("", stderr.toString())
    }

    @Test
    fun `scaffold app log plugin command creates plugin project files`() {
        val root = Files.createTempDirectory("soluna-cli-plugin-scaffold")
        val output = root.resolve("ugreen-audio-plugin")
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication()

        val exitCode = cli.run(
            arrayOf(
                "scaffold",
                "app-log-plugin",
                output.toString(),
                "--plugin-id",
                "ugreen-audio",
                "--package",
                "com.ugreen.soluna.applog",
                "--assertion",
                "ble-command-ack",
            ),
            stdout,
            stderr,
        )

        assertEquals(0, exitCode)
        assertEquals("", stderr.toString())
        assertTrue(stdout.toString().contains("Soluna app-log plugin scaffold created"))
        assertTrue(Files.exists(output.resolve("settings.gradle.kts")))
        assertTrue(Files.readString(output.resolve("build.gradle.kts")).contains("SOLUNA_HOME"))
        assertEquals(
            "com.ugreen.soluna.applog.UgreenAudioAppLogPlugin\n",
            Files.readString(
                output.resolve(
                    "src/main/resources/META-INF/services/com.soluna.ui.autotest.extension.applog.AppLogAssertionPlugin",
                ),
            ),
        )
        val source = Files.readString(output.resolve("src/main/kotlin/com/ugreen/soluna/applog/UgreenAudioAppLogPlugin.kt"))
        assertTrue(source.contains("override val id: String = \"ugreen-audio\""))
        assertTrue(source.contains("override val name: String = \"ble-command-ack\""))
    }

    @Test
    fun `scaffold app log plugin command refuses non-empty output without force`() {
        val output = Files.createTempDirectory("soluna-cli-plugin-scaffold-existing")
        Files.writeString(output.resolve("existing.txt"), "existing")
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val cli = SolunaCliApplication()

        val exitCode = cli.run(
            arrayOf(
                "scaffold",
                "app-log-plugin",
                output.toString(),
                "--plugin-id",
                "ugreen-audio",
                "--package",
                "com.ugreen.soluna.applog",
            ),
            stdout,
            stderr,
        )

        assertEquals(1, exitCode)
        assertEquals("", stdout.toString())
        assertTrue(stderr.toString().contains("Output directory is not empty"))
    }

    private fun planRunResult(
        request: PlanRunRequest,
        status: ExecutionStatus,
    ): PlanRunResult {
        return PlanRunResult(
            plan = PlanDefinition(
                schemaVersion = "1.0",
                id = "plan-cli",
                name = "CLI Plan",
            ),
            deviceConfig = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "android-device",
                device = DeviceDefinition(
                    platform = "android",
                    udid = "android-001",
                ),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = false),
                ),
            ),
            serverHandle = AppiumServerHandle(
                url = "http://127.0.0.1:4723",
                managed = false,
            ),
            driverSession = null,
            executionResult = PlanExecutionResult(
                runId = request.runId,
                planId = "plan-cli",
                status = status,
                stages = listOf(
                    StageExecutionResult(
                        stageId = "stage-cli",
                        status = status,
                        cases = listOf(
                            CaseExecutionResult(
                                caseId = "case-cli",
                                status = status,
                                actions = emptyList(),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
