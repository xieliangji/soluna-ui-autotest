package com.soluna.ui.autotest.config

import com.soluna.ui.autotest.dsl.DslValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YamlDeviceConfigParserTest {
    private val parser = YamlDeviceConfigParser()

    @Test
    fun `parses device config yaml`() {
        val config = parser.parse(validConfigYaml())

        assertEquals("android-device", config.id)
        assertEquals("android", config.device.platform)
        assertEquals("android-001", config.device.udid)
        assertNull(config.appium.server.host)
        assertNull(config.appium.server.port)
        assertEquals("appium", config.appium.server.executable)
        assertEquals(emptyMap(), config.appium.server.environment)
        assertEquals("UiAutomator2", config.appium.capabilities["automationName"]?.asText())
    }

    @Test
    fun `parses iOS WDA config`() {
        val config = parser.parse(
            """
            schemaVersion: "1.0"
            id: ios-device
            device:
              platform: ios
              udid: ios-001
              osVersion: "17.2"
            appium:
              server:
                managed: true
            ios:
              wda:
                enabled: true
                managed: true
                hostPort: 18100
                devicePort: 8100
                executable: /opt/homebrew/bin/ios
                tunnelMode: userspace
                tunnelInfoHost: 127.0.0.1
                tunnelInfoPort: 28100
                userspaceTunnelPort: 28101
                bundleId: com.example.WebDriverAgentRunner.xctrunner
                tunnelStartupDelayMs: 1500
                runwdaStartupDelayMs: 500
            """.trimIndent(),
        )

        assertEquals("17.2", config.device.osVersion)
        assertEquals(18100, config.ios.wda.hostPort)
        assertEquals(8100, config.ios.wda.devicePort)
        assertEquals("/opt/homebrew/bin/ios", config.ios.wda.executable)
        assertEquals("userspace", config.ios.wda.tunnelMode)
        assertEquals("127.0.0.1", config.ios.wda.tunnelInfoHost)
        assertEquals(28100, config.ios.wda.tunnelInfoPort)
        assertEquals(28101, config.ios.wda.userspaceTunnelPort)
        assertEquals("com.example.WebDriverAgentRunner.xctrunner", config.ios.wda.bundleId)
        assertEquals(1500, config.ios.wda.tunnelStartupDelayMs)
        assertEquals(500, config.ios.wda.runwdaStartupDelayMs)
    }

    @Test
    fun `parses optional Appium environment overrides`() {
        val config = parser.parse(
            """
            schemaVersion: "1.0"
            id: android-device
            device:
              udid: android-001
            appium:
              server:
                managed: true
                environment:
                  SOLUNA_APPIUM_LOG_LEVEL: debug
            """.trimIndent(),
        )

        assertEquals("debug", config.appium.server.environment["SOLUNA_APPIUM_LOG_LEVEL"])
    }

    @Test
    fun `rejects invalid device config yaml`() {
        val error = assertFailsWith<DslValidationException> {
            parser.parse(
                """
                schemaVersion: "1.0"
                id: bad-device
                device:
                  platform: web
                  udid: web-001
                appium:
                  server:
                    managed: false
                """.trimIndent(),
            )
        }

        assertTrue(error.violations.any { it.path.contains("platform") })
    }

    @Test
    fun `rejects app config inside device config yaml`() {
        val error = assertFailsWith<DslValidationException> {
            parser.parse(
                """
                schemaVersion: "1.0"
                id: bad-device
                device:
                  platform: android
                  udid: android-001
                app:
                  packageName: com.example.demo
                appium:
                  server:
                    managed: false
                """.trimIndent(),
            )
        }

        assertTrue(error.violations.any { it.message.contains("app") || it.path.contains("app") })
    }

    private fun validConfigYaml(): String {
        return """
            schemaVersion: "1.0"
            id: android-device
            device:
              platform: android
              udid: android-001
              name: Demo Android
            appium:
              server:
                managed: true
                executable: appium
                usePlugins:
                  - soluna-ext
                startupTimeoutMs: 30000
              capabilities:
                automationName: UiAutomator2
        """.trimIndent()
    }
}
