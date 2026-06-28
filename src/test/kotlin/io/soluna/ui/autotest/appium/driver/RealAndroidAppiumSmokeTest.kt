package io.soluna.ui.autotest.appium.driver

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealAndroidAppiumSmokeTest {
    @Test
    fun `starts real Android Appium session and captures screenshot`() {
        val udid = System.getenv("SOLUNA_ANDROID_UDID") ?: return
        val serverUrl = System.getenv("SOLUNA_APPIUM_SERVER_URL") ?: "http://127.0.0.1:4725"
        val adapter = AppiumJavaClientWebDriverAdapter()

        val session = adapter.startSession(
            StartSessionRequest(
                serverUrl = serverUrl,
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
            assertNotNull(session.sessionId)
            assertTrue(adapter.isSessionHealthy(session.sessionId))

            val screenshot = adapter.takeScreenshot(session.sessionId)
            assertContentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47),
                screenshot.bytes.take(4).toByteArray(),
            )
        } finally {
            adapter.stopSession(session.sessionId)
        }
    }
}
