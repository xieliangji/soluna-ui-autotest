package com.ugreen.iot.soluna.autotest.runner

import com.ugreen.iot.soluna.autotest.appium.driver.DriverElement
import com.ugreen.iot.soluna.autotest.appium.driver.DriverSession
import com.ugreen.iot.soluna.autotest.appium.driver.DriverWaitOptions
import com.ugreen.iot.soluna.autotest.appium.driver.ScreenshotData
import com.ugreen.iot.soluna.autotest.appium.driver.StartSessionRequest
import com.ugreen.iot.soluna.autotest.appium.driver.WebDriverAdapter
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerConfig
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerHandle
import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerManager
import com.ugreen.iot.soluna.autotest.appium.wda.WdaBundleResolveRequest
import com.ugreen.iot.soluna.autotest.appium.wda.WdaBundleResolution
import com.ugreen.iot.soluna.autotest.appium.wda.WdaBundleResolutionSource
import com.ugreen.iot.soluna.autotest.appium.wda.WdaBundleResolver
import com.ugreen.iot.soluna.autotest.appium.wda.WdaConfig
import com.ugreen.iot.soluna.autotest.appium.wda.WdaHandle
import com.ugreen.iot.soluna.autotest.appium.wda.WdaManager
import com.ugreen.iot.soluna.autotest.artifact.ArtifactKind
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadDrainResult
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadRequest
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadStatus
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploadTaskState
import com.ugreen.iot.soluna.autotest.artifact.ArtifactUploader
import com.ugreen.iot.soluna.autotest.core.execution.ActionExecutionResult
import com.ugreen.iot.soluna.autotest.core.execution.ActionExecutor
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionContext
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import com.ugreen.iot.soluna.autotest.notification.NotificationMessage
import com.ugreen.iot.soluna.autotest.notification.NotificationSendResult
import com.ugreen.iot.soluna.autotest.notification.NotificationSender
import com.ugreen.iot.soluna.autotest.report.LocalReportWriter
import java.time.Instant
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanRunnerTest {
    @Test
    fun `runs plan with plan-scoped Appium session and stops owned resources`() {
        val root = Files.createTempDirectory("soluna-runner-test")
        val planPath = writePlan(root, "devices/android.yaml")
        writeAndroidDeviceConfig(root.resolve("devices"))
        val serverManager = RecordingAppiumServerManager()
        val driver = RecordingWebDriverAdapter()
        val seenSessionIds = mutableListOf<String?>()
        val runner = PlanRunner(
            appiumServerManager = serverManager,
            webDriverAdapter = driver,
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(seenSessionIds)) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-001",
            ),
        )

        assertEquals(ExecutionStatus.PASSED, result.executionResult.status)
        assertEquals("run-001", result.executionResult.runId)
        assertEquals(1, seenSessionIds.size)
        assertTrue(seenSessionIds.single()?.startsWith("logical-") == true)
        assertEquals(true, serverManager.startedConfig?.managed)
        assertNull(serverManager.startedConfig?.host)
        assertNull(serverManager.startedConfig?.port)
        assertEquals(listOf("soluna-ext"), serverManager.startedConfig?.usePlugins)
        assertEquals("android-001", driver.startedRequest?.capabilities?.get("appium:udid"))
        assertFalse(driver.startedRequest?.capabilities?.containsKey("appium:appPackage") == true)
        assertFalse(driver.startedRequest?.capabilities?.containsKey("appium:appActivity") == true)
        assertEquals("session-001", driver.stoppedSessionId)
        assertTrue(serverManager.stopped)
    }

    @Test
    fun `uses provided session id without starting or stopping driver session`() {
        val root = Files.createTempDirectory("soluna-runner-existing-session-test")
        val planPath = writePlan(root, "devices/android.yaml")
        writeAndroidDeviceConfig(root.resolve("devices"), managed = false)
        val serverManager = RecordingAppiumServerManager()
        val driver = RecordingWebDriverAdapter()
        val seenSessionIds = mutableListOf<String?>()
        val runner = PlanRunner(
            appiumServerManager = serverManager,
            webDriverAdapter = driver,
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(seenSessionIds)) },
        )

        runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-existing",
                driverSessionId = "external-session",
            ),
        )

        assertEquals(listOf<String?>("external-session"), seenSessionIds)
        assertNull(driver.startedRequest)
        assertNull(driver.stoppedSessionId)
        assertFalse(serverManager.stopped)
    }

    @Test
    fun `starts managed WDA for iOS sessions and injects WDA URL`() {
        val root = Files.createTempDirectory("soluna-runner-ios-wda-test")
        val planPath = writePlan(root, "devices/ios.yaml", platform = "ios")
        writeIosDeviceConfig(root.resolve("devices"))
        val driver = RecordingWebDriverAdapter()
        val wdaManager = RecordingWdaManager()
        val runner = PlanRunner(
            appiumServerManager = RecordingAppiumServerManager(),
            wdaManager = wdaManager,
            wdaBundleResolver = StaticWdaBundleResolver("com.facebook.WebDriverAgentRunner.xctrunner"),
            webDriverAdapter = driver,
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(mutableListOf())) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-ios-wda",
            ),
        )

        assertEquals("ios-001", wdaManager.startedConfig?.udid)
        assertEquals("17.2", wdaManager.startedConfig?.osVersion)
        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", wdaManager.startedConfig?.bundleId)
        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", wdaManager.startedConfig?.testRunnerBundleId)
        assertEquals("WebDriverAgentRunner.xctest", wdaManager.startedConfig?.xctestConfig)
        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", result.deviceConfig.ios.wda.bundleId)
        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", result.deviceConfig.ios.wda.testRunnerBundleId)
        assertEquals("WebDriverAgentRunner.xctest", result.deviceConfig.ios.wda.xctestConfig)
        assertEquals("http://127.0.0.1:18100", driver.startedRequest?.capabilities?.get("appium:webDriverAgentUrl"))
        assertEquals("http://127.0.0.1:18100", result.wdaHandle?.url)
        assertTrue(wdaManager.stopped)
    }

    @Test
    fun `uploads report files and manifest through injected artifact uploader`() {
        val root = Files.createTempDirectory("soluna-runner-upload-test")
        val reportRoot = root.resolve("reports")
        val planPath = writePlan(root, "devices/android.yaml")
        writeAndroidDeviceConfig(root.resolve("devices"))
        val serverManager = RecordingAppiumServerManager()
        val driver = RecordingWebDriverAdapter()
        val uploader = RecordingArtifactUploader()
        val runner = PlanRunner(
            appiumServerManager = serverManager,
            webDriverAdapter = driver,
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(mutableListOf())) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-upload",
                reportWriter = LocalReportWriter(outputRoot = reportRoot),
                artifactUploader = uploader,
                artifactDrainTimeoutMs = 1_000,
            ),
        )

        val report = assertNotNull(result.report)
        assertNotNull(report.resourceManifestFile)
        assertNotNull(result.artifactUploads)
        assertTrue(Files.readString(report.htmlFile).contains("https://artifact.local/soluna/runs/run-upload/report/execution-result.json"))
        assertTrue(Files.readString(report.htmlFile).contains("https://artifact.local/soluna/runs/run-upload/report/plan-resource-manifest.json"))
        assertEquals(
            listOf(
                "soluna/runs/run-upload/report/execution-result.json",
                "soluna/runs/run-upload/report/plan-resource-manifest.json",
                "soluna/runs/run-upload/report/index.html",
            ),
            uploader.requests.map { it.objectKey },
        )
    }

    @Test
    fun `uploads retained trace screenshots only when action fails`() {
        val root = Files.createTempDirectory("soluna-runner-trace-test")
        val reportRoot = root.resolve("reports")
        val planPath = writeTracePlan(root, cleanupMode = "never")
        writeAndroidDeviceConfig(root.resolve("devices"))
        val serverManager = RecordingAppiumServerManager()
        val driver = RecordingWebDriverAdapter()
        val uploader = RecordingArtifactUploader()
        val runner = PlanRunner(
            appiumServerManager = serverManager,
            webDriverAdapter = driver,
            actionExecutorFactory = { _, _ -> listOf(FailingActionExecutor()) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-trace",
                reportWriter = LocalReportWriter(outputRoot = reportRoot),
                artifactUploader = uploader,
                localArtifactRoot = reportRoot,
                artifactDrainTimeoutMs = 1_000,
            ),
        )

        val report = assertNotNull(result.report)
        assertEquals(ExecutionStatus.FAILED, result.executionResult.status)
        assertEquals(1, driver.screenshotCalls)
        assertEquals(1, result.traceArtifacts.size)
        assertTrue(uploader.requests.any { it.objectKey.startsWith("soluna/runs/run-trace/diagnostics/trace-") })
        assertTrue(Files.readString(report.dataFile).contains("\"traceArtifacts\""))
        assertTrue(Files.readString(report.dataFile).contains("https://artifact.local/soluna/runs/run-trace/diagnostics/trace-"))
    }

    @Test
    fun `cleans local run directory after all artifacts upload successfully`() {
        val root = Files.createTempDirectory("soluna-runner-cleanup-test")
        val reportRoot = root.resolve("reports")
        val planPath = writeTracePlan(root, cleanupMode = "after-upload-success")
        writeAndroidDeviceConfig(root.resolve("devices"))
        val uploader = RecordingArtifactUploader()
        val runner = PlanRunner(
            appiumServerManager = RecordingAppiumServerManager(),
            webDriverAdapter = RecordingWebDriverAdapter(),
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(mutableListOf())) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-cleanup",
                reportWriter = LocalReportWriter(outputRoot = reportRoot),
                artifactUploader = uploader,
                localArtifactRoot = reportRoot,
                artifactDrainTimeoutMs = 1_000,
            ),
        )

        val cleanup = assertNotNull(result.localArtifactCleanup)
        assertTrue(cleanup.deleted)
        assertFalse(Files.exists(reportRoot.resolve("run-cleanup")))
    }

    @Test
    fun `keeps local run directory when artifact uploads are not fully complete`() {
        val root = Files.createTempDirectory("soluna-runner-keep-local-test")
        val reportRoot = root.resolve("reports")
        val planPath = writeTracePlan(root, cleanupMode = "after-upload-success")
        writeAndroidDeviceConfig(root.resolve("devices"))
        val uploader = RecordingArtifactUploader(allDrainCompleted = false)
        val runner = PlanRunner(
            appiumServerManager = RecordingAppiumServerManager(),
            webDriverAdapter = RecordingWebDriverAdapter(),
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(mutableListOf())) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-keep-local",
                reportWriter = LocalReportWriter(outputRoot = reportRoot),
                artifactUploader = uploader,
                localArtifactRoot = reportRoot,
                artifactDrainTimeoutMs = 1_000,
            ),
        )

        val cleanup = assertNotNull(result.localArtifactCleanup)
        assertFalse(cleanup.deleted)
        assertTrue(Files.exists(reportRoot.resolve("run-keep-local")))
    }

    @Test
    fun `sends plan lifecycle notifications separately`() {
        val root = Files.createTempDirectory("soluna-runner-notification-test")
        val reportRoot = root.resolve("reports")
        val planPath = writePlan(
            root = root,
            deviceConfig = "devices/android.yaml",
            artifactStore = "../artifacts/minio.yaml",
        )
        writeAndroidDeviceConfig(root.resolve("devices"))
        writeArtifactStoreConfig(root.resolve("artifacts"))
        val uploader = RecordingArtifactUploader()
        val notificationSender = RecordingNotificationSender()
        val runner = PlanRunner(
            appiumServerManager = RecordingAppiumServerManager(),
            webDriverAdapter = RecordingWebDriverAdapter(),
            notificationSenderFactory = { notificationSender },
            actionExecutorFactory = { _, _ -> listOf(RecordingActionExecutor(mutableListOf())) },
        )

        val result = runner.run(
            PlanRunRequest(
                planPath = planPath,
                runId = "run-notify",
                reportWriter = LocalReportWriter(outputRoot = reportRoot),
                artifactUploader = uploader,
                artifactDrainTimeoutMs = 1_000,
            ),
        )

        assertEquals(3, result.notifications.size)
        assertEquals(
            listOf(
                "Soluna plan started",
                "Soluna test finished: passed",
                "Soluna report published: passed",
            ),
            notificationSender.messages.map { it.title },
        )
        assertTrue(notificationSender.messages[1].markdown.contains("- Status: `passed`"))
        assertTrue(
            notificationSender.messages[2].markdown.contains(
                "https://artifact.local/soluna/runs/run-notify/report/index.html",
            ),
        )
    }

    private fun writePlan(
        root: Path,
        deviceConfig: String,
        platform: String = "android",
        artifactStore: String? = null,
    ): Path {
        val planPath = root.resolve("plans/daily.yaml")
        Files.createDirectories(planPath.parent)
        val artifactStoreBlock = artifactStore?.let { "\n            artifactStore: $it" } ?: ""
        Files.writeString(
            planPath,
            """
            schemaVersion: "1.0"
            id: runner-plan
            name: Runner Plan
            deviceConfig: ../$deviceConfig$artifactStoreBlock
            app:
              platform: $platform
              reset: false
            stages:
              - id: main
                name: Main
                cases:
                  - id: smoke
                    name: Smoke
                    actions:
                      - tap: tap-home
                        xRatio: 0.5
                        yRatio: 0.5
            """.trimIndent(),
        )
        return planPath
    }

    private fun writeTracePlan(
        root: Path,
        cleanupMode: String,
    ): Path {
        val planPath = root.resolve("plans/trace.yaml")
        Files.createDirectories(planPath.parent)
        Files.writeString(
            planPath,
            """
            schemaVersion: "1.0"
            id: trace-plan
            name: Trace Plan
            deviceConfig: ../devices/android.yaml
            app:
              platform: android
              reset: false
            trace:
              screenshots:
                enabled: true
                beforeAction: onFailure
                retainBeforeActionCount: 3
                upload: onFailure
            localArtifacts:
              cleanup:
                mode: $cleanupMode
            stages:
              - id: main
                name: Main
                cases:
                  - id: smoke
                    name: Smoke
                    actions:
                      - tap: tap-home
                        xRatio: 0.5
                        yRatio: 0.5
            """.trimIndent(),
        )
        return planPath
    }

    private fun writeAndroidDeviceConfig(
        directory: Path,
        managed: Boolean = true,
    ): Path {
        Files.createDirectories(directory)
        val devicePath = directory.resolve("android.yaml")
        val serverConfig = if (managed) {
            """
                managed: true
                usePlugins:
                  - soluna-ext
            """.trimIndent()
        } else {
            """
                managed: false
                url: http://127.0.0.1:4725
                usePlugins:
                  - soluna-ext
            """.trimIndent()
        }
        Files.writeString(
            devicePath,
            """
            schemaVersion: "1.0"
            id: android-device
            device:
              platform: android
              udid: android-001
              name: Demo Android
            appium:
              server:
${serverConfig.prependIndent("                ")}
              capabilities:
                automationName: UiAutomator2
            """.trimIndent(),
        )
        return devicePath
    }

    private fun writeArtifactStoreConfig(directory: Path): Path {
        Files.createDirectories(directory)
        val notificationPath = directory.resolve("dingtalk.yaml")
        Files.writeString(
            notificationPath,
            """
            schemaVersion: "1.0"
            id: dingtalk-test
            type: dingtalk
            robot:
              webhook: https://example.invalid/robot/send
              secret: test-secret
            """.trimIndent(),
        )
        val artifactPath = directory.resolve("minio.yaml")
        Files.writeString(
            artifactPath,
            """
            schemaVersion: "1.0"
            id: minio-test
            type: minio
            endpoint: http://minio.example:9000
            secure: false
            bucket: autotest
            credentials:
              accessKey: access
              secretKey: secret
            notifications:
              uploadFailures: ./dingtalk.yaml
              planStarted: ./dingtalk.yaml
              testFinished: ./dingtalk.yaml
              reportPublished: ./dingtalk.yaml
            """.trimIndent(),
        )
        return artifactPath
    }

    private fun writeIosDeviceConfig(directory: Path): Path {
        Files.createDirectories(directory)
        val devicePath = directory.resolve("ios.yaml")
        Files.writeString(
            devicePath,
            """
            schemaVersion: "1.0"
            id: ios-device
            device:
              platform: ios
              udid: ios-001
              name: Demo iPhone
              osVersion: "17.2"
            appium:
              server:
                managed: true
                usePlugins:
                  - soluna-ext
              capabilities:
                automationName: XCUITest
            ios:
              wda:
                enabled: true
                managed: true
                hostPort: 18100
            """.trimIndent(),
        )
        return devicePath
    }

    private class RecordingActionExecutor(
        private val seenSessionIds: MutableList<String?>,
    ) : ActionExecutor {
        override val keyword: String = "tap"

        override fun execute(
            action: ActionDefinition,
            context: ExecutionContext,
        ): ActionExecutionResult {
            seenSessionIds += context.driverSessionId
            return ActionExecutionResult.passed("ok")
        }
    }

    private class RecordingAppiumServerManager : AppiumServerManager {
        var startedConfig: AppiumServerConfig? = null
        var stopped: Boolean = false

        override fun ensureRunning(config: AppiumServerConfig): AppiumServerHandle {
            startedConfig = config
            return AppiumServerHandle(
                url = config.url,
                managed = config.managed,
                processId = 123,
            )
        }

        override fun stop(handle: AppiumServerHandle) {
            stopped = true
        }

        override fun isRunning(handle: AppiumServerHandle): Boolean {
            return true
        }
    }

    private class RecordingWdaManager : WdaManager {
        var startedConfig: WdaConfig? = null
        var stopped: Boolean = false

        override fun ensureRunning(config: WdaConfig): WdaHandle {
            startedConfig = config
            return WdaHandle(
                url = "http://127.0.0.1:18100",
                udid = config.udid,
                managed = config.managed,
                usesTunnel = config.requiresTunnel(),
                hostPort = 18100,
                devicePort = config.devicePort,
                tunnelProcessId = 1,
                runwdaProcessId = 2,
                forwardProcessId = 3,
            )
        }

        override fun stop(handle: WdaHandle) {
            stopped = true
        }

        override fun isRunning(handle: WdaHandle): Boolean {
            return true
        }
    }

    private class StaticWdaBundleResolver(
        private val testRunnerBundleId: String,
    ) : WdaBundleResolver {
        override fun resolve(request: WdaBundleResolveRequest): WdaBundleResolution {
            return WdaBundleResolution(
                bundleId = request.bundleId ?: testRunnerBundleId,
                testRunnerBundleId = request.testRunnerBundleId ?: testRunnerBundleId,
                xctestConfig = request.xctestConfig ?: "WebDriverAgentRunner.xctest",
                source = if (request.testRunnerBundleId == null) {
                    WdaBundleResolutionSource.SOLUNA_EXT
                } else {
                    WdaBundleResolutionSource.CONFIG
                },
            )
        }
    }

    private class RecordingWebDriverAdapter : WebDriverAdapter {
        var startedRequest: StartSessionRequest? = null
        var stoppedSessionId: String? = null
        var screenshotCalls: Int = 0

        override fun startSession(request: StartSessionRequest): DriverSession {
            startedRequest = request
            return DriverSession(
                sessionId = "session-001",
                serverUrl = request.serverUrl,
                capabilities = request.capabilities,
            )
        }

        override fun getSession(sessionId: String): DriverSession? {
            return null
        }

        override fun stopSession(sessionId: String) {
            stoppedSessionId = sessionId
        }

        override fun findElement(
            sessionId: String,
            locator: LocatorDefinition,
            wait: DriverWaitOptions?,
        ): DriverElement {
            error("not used")
        }

        override fun tap(
            sessionId: String,
            element: DriverElement,
        ) {
            error("not used")
        }

        override fun inputText(
            sessionId: String,
            element: DriverElement,
            text: String,
            clearFirst: Boolean,
        ) {
            error("not used")
        }

        override fun takeScreenshot(sessionId: String): ScreenshotData {
            screenshotCalls += 1
            return ScreenshotData(bytes = "png".toByteArray())
        }

        override fun isSessionHealthy(sessionId: String): Boolean {
            return true
        }
    }

    private class FailingActionExecutor : ActionExecutor {
        override val keyword: String = "tap"

        override fun execute(
            action: ActionDefinition,
            context: ExecutionContext,
        ): ActionExecutionResult {
            return ActionExecutionResult.failed("failed by test")
        }
    }

    private class RecordingArtifactUploader(
        private val allDrainCompleted: Boolean = true,
    ) : ArtifactUploader {
        val requests = mutableListOf<ArtifactUploadRequest>()

        override fun objectKeyPrefix(
            runId: String,
            kind: ArtifactKind,
        ): String {
            return "soluna/runs/$runId/${kind.directory}"
        }

        override fun objectKey(
            runId: String,
            kind: ArtifactKind,
            fileName: String,
        ): String {
            return "${objectKeyPrefix(runId, kind)}/$fileName"
        }

        override fun urlFor(objectKey: String): String {
            return "https://artifact.local/$objectKey"
        }

        override fun enqueue(request: ArtifactUploadRequest): ArtifactUploadTaskState {
            requests += request
            return request.uploadedState()
        }

        override fun drainRequired(timeoutMs: Long): ArtifactUploadDrainResult {
            return ArtifactUploadDrainResult(
                completed = true,
                states = requests.map { it.uploadedState() },
            )
        }

        override fun drainAll(timeoutMs: Long): ArtifactUploadDrainResult {
            return ArtifactUploadDrainResult(
                completed = allDrainCompleted,
                states = requests.map { it.uploadedState() },
            )
        }

        override fun snapshot(): List<ArtifactUploadTaskState> {
            return requests.map { it.uploadedState() }
        }

        override fun close() {
            // Injected uploader is owned by the caller.
        }

        private fun ArtifactUploadRequest.uploadedState(): ArtifactUploadTaskState {
            return ArtifactUploadTaskState(
                taskId = taskId,
                request = this,
                status = ArtifactUploadStatus.UPLOADED,
                attempts = 1,
                url = urlFor(objectKey),
                updatedAt = Instant.parse("2026-06-13T00:00:00Z"),
            )
        }
    }

    private class RecordingNotificationSender : NotificationSender {
        val messages = mutableListOf<NotificationMessage>()

        override fun send(message: NotificationMessage): NotificationSendResult {
            messages += message
            return NotificationSendResult(delivered = true, statusCode = 200)
        }
    }
}
