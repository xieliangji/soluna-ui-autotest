package com.soluna.ui.autotest.appium.driver

import com.soluna.ui.autotest.appium.server.AppiumServerConfig
import com.soluna.ui.autotest.appium.server.LocalProcessAppiumServerManager
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealAndroidAppiumRecoverySmokeTest {
    @Test
    fun `recovers Android session after managed Appium process exits`() {
        if (System.getenv("SOLUNA_APPIUM_RECOVERY_SMOKE") != "true") {
            return
        }

        val udid = System.getenv("SOLUNA_ANDROID_UDID") ?: return
        val executable = System.getenv("SOLUNA_APPIUM_EXECUTABLE") ?: "appium"
        val serverConfig = AppiumServerConfig(
            managed = true,
            usePlugins = listOf("soluna-ext"),
            executable = executable,
            startupTimeoutMs = 20_000,
        )
        val manager = LocalProcessAppiumServerManager()
        val initialHandle = manager.ensureRunning(serverConfig)
        val adapter = RecoveringWebDriverAdapter(
            delegate = AppiumJavaClientWebDriverAdapter(),
            appiumServerManager = manager,
            serverConfig = serverConfig,
            initialServerHandle = initialHandle,
            recoveryPolicy = AppiumSessionRecoveryPolicy(maxRecoveriesPerSession = 1),
        )
        val session = adapter.startSession(
            StartSessionRequest(
                serverUrl = initialHandle.url,
                capabilities = mapOf(
                    "platformName" to "Android",
                    "appium:automationName" to "UiAutomator2",
                    "appium:udid" to udid,
                    "appium:deviceName" to udid,
                    "appium:newCommandTimeout" to 60,
                ),
            ),
        )

        try {
            assertPng(adapter.takeScreenshot(session.sessionId).bytes)
            val firstProcessId = assertNotNull(adapter.currentServerHandle().processId)

            killProcess(firstProcessId)

            assertPng(adapter.takeScreenshot(session.sessionId).bytes)
            val recoveredHandle = adapter.currentServerHandle()
            val recoveredProcessId = assertNotNull(recoveredHandle.processId)

            assertNotEquals(firstProcessId, recoveredProcessId)
            assertTrue(manager.isRunning(recoveredHandle))
            assertTrue(adapter.isSessionHealthy(session.sessionId))
        } finally {
            adapter.stopSession(session.sessionId)
            manager.stop(adapter.currentServerHandle())
        }
    }

    private fun assertPng(bytes: ByteArray) {
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47),
            bytes.take(4).toByteArray(),
        )
    }

    private fun killProcess(processId: Long) {
        val processHandle = ProcessHandle.of(processId).getOrNull()
            ?: error("Process $processId does not exist")
        processHandle.destroyForcibly()
        processHandle.onExit().get(5, TimeUnit.SECONDS)
    }
}
