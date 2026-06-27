package com.soluna.ui.autotest.appium.driver

import com.soluna.ui.autotest.appium.server.AppiumServerConfig
import com.soluna.ui.autotest.appium.server.AppiumServerHandle
import com.soluna.ui.autotest.appium.server.AppiumServerManager
import com.soluna.ui.autotest.appium.wda.WdaConfig
import com.soluna.ui.autotest.appium.wda.WdaHandle
import com.soluna.ui.autotest.appium.wda.WdaManager
import com.soluna.ui.autotest.core.model.LocatorDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecoveringWebDriverAdapterTest {
    @Test
    fun `rebuilds physical session when Appium server is not running before operation`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1),
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 2),
            ),
            running = false,
        )
        val delegate = RecordingWebDriverAdapter()
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())

        val element = adapter.findElement(
            sessionId = session.sessionId,
            locator = LocatorDefinition("id", "login_button"),
            wait = null,
        )

        assertTrue(session.sessionId.startsWith("logical-"))
        assertEquals(listOf("physical-1", "physical-2"), delegate.startedSessions)
        assertEquals(listOf("physical-1"), delegate.stoppedSessions)
        assertEquals(listOf<Long?>(1L), serverManager.stoppedProcessIds)
        assertEquals("physical-2", delegate.findElementCalls.single().sessionId)
        assertTrue(element.elementId.startsWith("${session.sessionId}:"))
    }

    @Test
    fun `re-finds logical element after session recovery before tap`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1),
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 2),
            ),
            running = true,
        )
        val delegate = RecordingWebDriverAdapter()
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())
        val element = adapter.findElement(
            sessionId = session.sessionId,
            locator = LocatorDefinition("id", "login_button"),
            wait = null,
        )

        serverManager.running = false
        adapter.tap(session.sessionId, element)

        assertEquals(listOf("physical-1", "physical-2"), delegate.startedSessions)
        assertEquals(
            listOf(
                FindElementCall("physical-1", LocatorDefinition("id", "login_button")),
                FindElementCall("physical-2", LocatorDefinition("id", "login_button")),
            ),
            delegate.findElementCalls,
        )
        assertEquals(listOf(TapCall("physical-2", "physical-2:element-2")), delegate.tapCalls)
    }

    @Test
    fun `viewport tap uses recovered physical session`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1),
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 2),
            ),
            running = true,
        )
        val delegate = RecordingWebDriverAdapter()
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())

        serverManager.running = false
        adapter.tapViewport(session.sessionId, xRatio = 0.5, yRatio = 0.3)

        assertEquals(listOf("physical-1", "physical-2"), delegate.startedSessions)
        assertEquals(listOf(ViewportTapCall("physical-2", 0.5, 0.3)), delegate.viewportTapCalls)
    }

    @Test
    fun `viewport swipe uses recovered physical session`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1),
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 2),
            ),
            running = true,
        )
        val delegate = RecordingWebDriverAdapter()
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())

        serverManager.running = false
        adapter.swipeViewport(
            sessionId = session.sessionId,
            durationMs = 600,
            startXRatio = 0.5,
            startYRatio = 0.8,
            endXRatio = 0.5,
            endYRatio = 0.2,
        )

        assertEquals(listOf("physical-1", "physical-2"), delegate.startedSessions)
        assertEquals(
            listOf(ViewportSwipeCall("physical-2", 600, 0.5, 0.8, 0.5, 0.2)),
            delegate.viewportSwipeCalls,
        )
    }

    @Test
    fun `element swipe re-finds logical element after recovery`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1),
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 2),
            ),
            running = true,
        )
        val delegate = RecordingWebDriverAdapter()
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())
        val element = adapter.findElement(
            sessionId = session.sessionId,
            locator = LocatorDefinition("id", "list"),
            wait = null,
        )

        serverManager.running = false
        adapter.swipe(
            sessionId = session.sessionId,
            element = element,
            durationMs = 700,
            startXRatio = 0.5,
            startYRatio = 0.9,
            endXRatio = 0.5,
            endYRatio = 0.1,
        )

        assertEquals(listOf("physical-1", "physical-2"), delegate.startedSessions)
        assertEquals(
            listOf(
                FindElementCall("physical-1", LocatorDefinition("id", "list")),
                FindElementCall("physical-2", LocatorDefinition("id", "list")),
            ),
            delegate.findElementCalls,
        )
        assertEquals(
            listOf(SwipeCall("physical-2", "physical-2:element-2", 700, 0.5, 0.9, 0.5, 0.1)),
            delegate.swipeCalls,
        )
    }

    @Test
    fun `restarts WDA and rebuilds physical session when WDA is not running before operation`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(
                AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1),
                AppiumServerHandle("http://127.0.0.1:4726", managed = true, processId = 2),
            ),
            running = true,
        )
        val initialWdaHandle = wdaHandle(url = "http://127.0.0.1:18100", pidOffset = 10)
        val restartedWdaHandle = wdaHandle(url = "http://127.0.0.1:18101", pidOffset = 20)
        val wdaManager = RecordingWdaManager(
            handles = mutableListOf(restartedWdaHandle),
            running = false,
        )
        val delegate = RecordingWebDriverAdapter()
        val adapter = recoveringAdapter(
            serverManager = serverManager,
            delegate = delegate,
            wdaManager = wdaManager,
            wdaConfig = wdaConfig(),
            initialWdaHandle = initialWdaHandle,
        )
        val session = adapter.startSession(iosStartRequest(initialWdaHandle.url))

        adapter.takeScreenshot(session.sessionId)

        assertEquals(listOf("physical-1", "physical-2"), delegate.startedSessions)
        assertEquals(
            listOf(initialWdaHandle.url, restartedWdaHandle.url),
            delegate.startRequests.map { it.capabilities["appium:webDriverAgentUrl"] },
        )
        assertEquals(listOf("physical-1"), delegate.stoppedSessions)
        assertEquals(listOf(initialWdaHandle.url), wdaManager.stoppedUrls)
        assertEquals(listOf("ios-001"), wdaManager.startedConfigs.map { it.udid })
        assertEquals(restartedWdaHandle, adapter.currentWdaHandle())
        assertEquals(listOf<Long?>(1L), serverManager.stoppedProcessIds)
    }

    @Test
    fun `does not recover healthy session action failures`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1)),
            running = true,
        )
        val delegate = RecordingWebDriverAdapter(tapFailure = IllegalStateException("element is covered"))
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())
        val element = adapter.findElement(
            sessionId = session.sessionId,
            locator = LocatorDefinition("id", "login_button"),
            wait = null,
        )

        val error = assertFailsWith<IllegalStateException> {
            adapter.tap(session.sessionId, element)
        }

        assertEquals("element is covered", error.message)
        assertEquals(listOf("physical-1"), delegate.startedSessions)
    }

    @Test
    fun `re-finds stale logical element without rebuilding session`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1)),
            running = true,
        )
        val delegate = RecordingWebDriverAdapter(
            tapFailures = mutableListOf(IllegalStateException("stale element reference: not linked to the same object")),
        )
        val adapter = recoveringAdapter(serverManager, delegate)
        val session = adapter.startSession(startRequest())
        val element = adapter.findElement(
            sessionId = session.sessionId,
            locator = LocatorDefinition("id", "login_button"),
            wait = null,
        )

        adapter.tap(session.sessionId, element)

        assertEquals(listOf("physical-1"), delegate.startedSessions)
        assertEquals(
            listOf(
                FindElementCall("physical-1", LocatorDefinition("id", "login_button")),
                FindElementCall("physical-1", LocatorDefinition("id", "login_button")),
                FindElementCall("physical-1", LocatorDefinition("id", "login_button")),
            ),
            delegate.findElementCalls,
        )
        assertEquals(listOf(TapCall("physical-1", "physical-1:element-3")), delegate.tapCalls)
    }

    @Test
    fun `fails clearly when recovery limit is exceeded`() {
        val serverManager = RecordingServerManager(
            handles = mutableListOf(AppiumServerHandle("http://127.0.0.1:4725", managed = true, processId = 1)),
            running = false,
        )
        val adapter = recoveringAdapter(
            serverManager = serverManager,
            delegate = RecordingWebDriverAdapter(),
            recoveryPolicy = AppiumSessionRecoveryPolicy(maxRecoveriesPerSession = 0),
        )
        val session = adapter.startSession(startRequest())

        assertFailsWith<AppiumSessionRecoveryException> {
            adapter.takeScreenshot(session.sessionId)
        }
    }

    private fun recoveringAdapter(
        serverManager: RecordingServerManager,
        delegate: RecordingWebDriverAdapter,
        wdaManager: RecordingWdaManager? = null,
        wdaConfig: WdaConfig? = null,
        initialWdaHandle: WdaHandle? = null,
        recoveryPolicy: AppiumSessionRecoveryPolicy = AppiumSessionRecoveryPolicy(),
    ): RecoveringWebDriverAdapter {
        return RecoveringWebDriverAdapter(
            delegate = delegate,
            appiumServerManager = serverManager,
            serverConfig = AppiumServerConfig(managed = true, port = 4725),
            initialServerHandle = serverManager.handles.removeFirst(),
            wdaManager = wdaManager,
            wdaConfig = wdaConfig,
            initialWdaHandle = initialWdaHandle,
            recoveryPolicy = recoveryPolicy,
        )
    }

    private fun startRequest(): StartSessionRequest {
        return StartSessionRequest(
            serverUrl = "http://127.0.0.1:4725",
            capabilities = mapOf(
                "platformName" to "Android",
                "appium:automationName" to "UiAutomator2",
            ),
        )
    }

    private fun iosStartRequest(webDriverAgentUrl: String): StartSessionRequest {
        return StartSessionRequest(
            serverUrl = "http://127.0.0.1:4725",
            capabilities = mapOf(
                "platformName" to "iOS",
                "appium:automationName" to "XCUITest",
                "appium:webDriverAgentUrl" to webDriverAgentUrl,
            ),
        )
    }

    private fun wdaConfig(): WdaConfig {
        return WdaConfig(
            udid = "ios-001",
            osVersion = "16.4",
            bundleId = "com.facebook.WebDriverAgentRunner.xctrunner",
            testRunnerBundleId = "com.facebook.WebDriverAgentRunner.xctrunner",
            xctestConfig = "WebDriverAgentRunner.xctest",
        )
    }

    private fun wdaHandle(
        url: String,
        pidOffset: Long,
    ): WdaHandle {
        return WdaHandle(
            url = url,
            udid = "ios-001",
            managed = true,
            usesTunnel = false,
            hostPort = url.substringAfterLast(":").toInt(),
            devicePort = 8100,
            runwdaProcessId = pidOffset,
            forwardProcessId = pidOffset + 1,
        )
    }

    private class RecordingServerManager(
        val handles: MutableList<AppiumServerHandle>,
        var running: Boolean,
    ) : AppiumServerManager {
        val stoppedProcessIds = mutableListOf<Long?>()

        override fun ensureRunning(config: AppiumServerConfig): AppiumServerHandle {
            running = true
            return handles.removeFirst()
        }

        override fun stop(handle: AppiumServerHandle) {
            stoppedProcessIds += handle.processId
            running = false
        }

        override fun isRunning(handle: AppiumServerHandle): Boolean {
            return running
        }
    }

    private class RecordingWdaManager(
        val handles: MutableList<WdaHandle>,
        var running: Boolean,
    ) : WdaManager {
        val startedConfigs = mutableListOf<WdaConfig>()
        val stoppedUrls = mutableListOf<String>()

        override fun ensureRunning(config: WdaConfig): WdaHandle {
            startedConfigs += config
            running = true
            return handles.removeFirst()
        }

        override fun stop(handle: WdaHandle) {
            stoppedUrls += handle.url
            running = false
        }

        override fun isRunning(handle: WdaHandle): Boolean {
            return running
        }
    }

    private class RecordingWebDriverAdapter(
        private val tapFailure: RuntimeException? = null,
        tapFailures: MutableList<RuntimeException> = mutableListOf(),
    ) : WebDriverAdapter {
        val startedSessions = mutableListOf<String>()
        val startRequests = mutableListOf<StartSessionRequest>()
        val stoppedSessions = mutableListOf<String>()
        val findElementCalls = mutableListOf<FindElementCall>()
        val tapCalls = mutableListOf<TapCall>()
        val viewportTapCalls = mutableListOf<ViewportTapCall>()
        val swipeCalls = mutableListOf<SwipeCall>()
        val viewportSwipeCalls = mutableListOf<ViewportSwipeCall>()
        private var sessionCounter = 0
        private var elementCounter = 0
        private val activeSessions = mutableSetOf<String>()
        private val tapFailures = ArrayDeque<RuntimeException>().also { failures ->
            tapFailure?.let { failures.add(it) }
            tapFailures.forEach { failures.add(it) }
        }

        override fun startSession(request: StartSessionRequest): DriverSession {
            sessionCounter += 1
            val sessionId = "physical-$sessionCounter"
            activeSessions += sessionId
            startedSessions += sessionId
            startRequests += request
            return DriverSession(
                sessionId = sessionId,
                serverUrl = request.serverUrl,
                capabilities = request.capabilities,
            )
        }

        override fun getSession(sessionId: String): DriverSession? {
            return null
        }

        override fun stopSession(sessionId: String) {
            activeSessions -= sessionId
            stoppedSessions += sessionId
        }

        override fun findElement(
            sessionId: String,
            locator: LocatorDefinition,
            wait: DriverWaitOptions?,
        ): DriverElement {
            findElementCalls += FindElementCall(sessionId, locator)
            elementCounter += 1
            return DriverElement("$sessionId:element-$elementCounter")
        }

        override fun tap(
            sessionId: String,
            element: DriverElement,
            xRatio: Double,
            yRatio: Double,
        ) {
            if (tapFailures.isNotEmpty()) {
                throw tapFailures.removeFirst()
            }
            tapCalls += TapCall(sessionId, element.elementId, xRatio, yRatio)
        }

        override fun tapViewport(
            sessionId: String,
            xRatio: Double,
            yRatio: Double,
        ) {
            viewportTapCalls += ViewportTapCall(sessionId, xRatio, yRatio)
        }

        override fun swipe(
            sessionId: String,
            element: DriverElement,
            durationMs: Long,
            startXRatio: Double,
            startYRatio: Double,
            endXRatio: Double,
            endYRatio: Double,
        ) {
            swipeCalls += SwipeCall(
                sessionId = sessionId,
                elementId = element.elementId,
                durationMs = durationMs,
                startXRatio = startXRatio,
                startYRatio = startYRatio,
                endXRatio = endXRatio,
                endYRatio = endYRatio,
            )
        }

        override fun swipeViewport(
            sessionId: String,
            durationMs: Long,
            startXRatio: Double,
            startYRatio: Double,
            endXRatio: Double,
            endYRatio: Double,
        ) {
            viewportSwipeCalls += ViewportSwipeCall(
                sessionId = sessionId,
                durationMs = durationMs,
                startXRatio = startXRatio,
                startYRatio = startYRatio,
                endXRatio = endXRatio,
                endYRatio = endYRatio,
            )
        }

        override fun inputText(
            sessionId: String,
            element: DriverElement,
            text: String,
            clearFirst: Boolean,
        ) {
        }

        override fun takeScreenshot(sessionId: String): ScreenshotData {
            return ScreenshotData(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        }

        override fun isSessionHealthy(sessionId: String): Boolean {
            return sessionId in activeSessions
        }
    }
}

private data class FindElementCall(
    val sessionId: String,
    val locator: LocatorDefinition,
)

private data class TapCall(
    val sessionId: String,
    val elementId: String,
    val xRatio: Double = 0.5,
    val yRatio: Double = 0.5,
)

private data class ViewportTapCall(
    val sessionId: String,
    val xRatio: Double,
    val yRatio: Double,
)

private data class SwipeCall(
    val sessionId: String,
    val elementId: String,
    val durationMs: Long,
    val startXRatio: Double,
    val startYRatio: Double,
    val endXRatio: Double,
    val endYRatio: Double,
)

private data class ViewportSwipeCall(
    val sessionId: String,
    val durationMs: Long,
    val startXRatio: Double,
    val startYRatio: Double,
    val endXRatio: Double,
    val endYRatio: Double,
)
