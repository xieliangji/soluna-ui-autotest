package com.ugreen.iot.soluna.autotest.appium.driver

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.ugreen.iot.soluna.autotest.config.DeviceAppiumDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceAppiumServerDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceConfigDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceDefinition
import com.ugreen.iot.soluna.autotest.core.model.AppDefinition
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AppiumSessionRequestFactoryTest {
    private val factory = AppiumSessionRequestFactory()

    @Test
    fun `builds Android Appium capabilities from device config and plan`() {
        val request = factory.create(
            serverUrl = "http://127.0.0.1:4725",
            deviceConfig = androidDeviceConfig(
                capabilities = mapOf(
                    "automationName" to JsonNodeFactory.instance.textNode("UiAutomator2"),
                    "newCommandTimeout" to JsonNodeFactory.instance.numberNode(90),
                ),
            ),
            plan = plan(platform = "android"),
        )

        assertEquals("http://127.0.0.1:4725", request.serverUrl)
        assertEquals("Android", request.capabilities["platformName"])
        assertEquals("UiAutomator2", request.capabilities["appium:automationName"])
        assertEquals("android-001", request.capabilities["appium:udid"])
        assertEquals("Demo Android", request.capabilities["appium:deviceName"])
        assertEquals(true, request.capabilities["appium:unicodeKeyboard"])
        assertEquals(true, request.capabilities["appium:resetKeyboard"])
        assertEquals(90, request.capabilities["appium:newCommandTimeout"])
        assertFalse(request.capabilities.containsKey("appium:appPackage"))
        assertFalse(request.capabilities.containsKey("appium:appActivity"))
        assertFalse(request.capabilities.containsKey("appium:noReset"))
    }

    @Test
    fun `device config can override Android keyboard capabilities`() {
        val request = factory.create(
            serverUrl = "http://127.0.0.1:4725",
            deviceConfig = androidDeviceConfig(
                capabilities = mapOf(
                    "unicodeKeyboard" to JsonNodeFactory.instance.booleanNode(false),
                    "resetKeyboard" to JsonNodeFactory.instance.booleanNode(false),
                ),
            ),
            plan = plan(platform = "android"),
        )

        assertEquals(false, request.capabilities["appium:unicodeKeyboard"])
        assertEquals(false, request.capabilities["appium:resetKeyboard"])
    }

    @Test
    fun `plan app settings do not launch or reset app during session creation`() {
        val request = factory.create(
            serverUrl = "http://127.0.0.1:4725",
            deviceConfig = androidDeviceConfig(),
            plan = plan(platform = "android", reset = true),
        )

        assertFalse(request.capabilities.containsKey("appium:appPackage"))
        assertFalse(request.capabilities.containsKey("appium:appActivity"))
        assertFalse(request.capabilities.containsKey("appium:noReset"))
    }

    @Test
    fun `builds iOS Appium capabilities`() {
        val request = factory.create(
            serverUrl = "http://127.0.0.1:4723",
            deviceConfig = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "ios-device",
                device = DeviceDefinition(
                    platform = "ios",
                    udid = "ios-001",
                ),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = false),
                ),
            ),
            plan = plan(platform = "ios"),
        )

        assertEquals("iOS", request.capabilities["platformName"])
        assertEquals("XCUITest", request.capabilities["appium:automationName"])
        assertEquals("ios-001", request.capabilities["appium:udid"])
        assertEquals("ios-001", request.capabilities["appium:deviceName"])
        assertFalse(request.capabilities.containsKey("appium:bundleId"))
    }

    @Test
    fun `injects managed WDA URL for iOS sessions`() {
        val request = factory.create(
            serverUrl = "http://127.0.0.1:4723",
            deviceConfig = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "ios-device",
                device = DeviceDefinition(
                    platform = "ios",
                    udid = "ios-001",
                ),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = false),
                ),
            ),
            plan = plan(platform = "ios"),
            webDriverAgentUrl = "http://127.0.0.1:18100",
        )

        assertEquals("http://127.0.0.1:18100", request.capabilities["appium:webDriverAgentUrl"])
    }

    @Test
    fun `rejects mismatched plan and device platforms`() {
        assertFailsWith<IllegalArgumentException> {
            factory.create(
                serverUrl = "http://127.0.0.1:4725",
                deviceConfig = androidDeviceConfig(),
                plan = plan(platform = "ios"),
            )
        }
    }

    private fun androidDeviceConfig(
        capabilities: Map<String, com.fasterxml.jackson.databind.JsonNode> = emptyMap(),
    ): DeviceConfigDefinition {
        return DeviceConfigDefinition(
            schemaVersion = "1.0",
            id = "android-device",
            device = DeviceDefinition(
                platform = "android",
                udid = "android-001",
                name = "Demo Android",
            ),
            appium = DeviceAppiumDefinition(
                server = DeviceAppiumServerDefinition(managed = false),
                capabilities = capabilities,
            ),
        )
    }

    private fun plan(
        platform: String,
        reset: Boolean? = null,
    ): PlanDefinition {
        return PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-001",
            name = "Plan 001",
            app = AppDefinition(
                platform = platform,
                reset = reset,
            ),
        )
    }
}
