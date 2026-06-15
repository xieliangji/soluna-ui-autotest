package com.ugreen.iot.soluna.autotest.appium.driver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ugreen.iot.soluna.autotest.config.DeviceConfigDefinition
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition

class AppiumSessionRequestFactory(
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
) {
    fun create(
        serverUrl: String,
        deviceConfig: DeviceConfigDefinition,
        plan: PlanDefinition,
        webDriverAgentUrl: String? = null,
    ): StartSessionRequest {
        validatePlanPlatform(deviceConfig, plan)

        val capabilities = linkedMapOf<String, Any?>()
        deviceConfig.appium.capabilities.forEach { (key, value) ->
            capabilities[normalizeCapabilityKey(key)] = value.toPlainValue()
        }

        val platform = deviceConfig.device.platform?.lowercase()
            ?: error("Device '${deviceConfig.device.udid}' does not define platform and was not resolved from soluna-ext")
        capabilities.putIfAbsent("platformName", platform.toPlatformName())
        capabilities.putIfAbsent("appium:automationName", platform.toDefaultAutomationName())
        capabilities["appium:udid"] = deviceConfig.device.udid
        capabilities.putIfAbsent("appium:deviceName", deviceConfig.device.name ?: deviceConfig.device.udid)

        when (platform) {
            "android" -> {
                capabilities.putIfAbsent("appium:unicodeKeyboard", true)
                capabilities.putIfAbsent("appium:resetKeyboard", true)
            }
            "ios" -> {
                webDriverAgentUrl?.let { capabilities["appium:webDriverAgentUrl"] = it }
            }
        }

        return StartSessionRequest(
            serverUrl = serverUrl,
            capabilities = capabilities,
        )
    }

    private fun validatePlanPlatform(
        deviceConfig: DeviceConfigDefinition,
        plan: PlanDefinition,
    ) {
        val planPlatform = plan.app?.platform ?: return
        val devicePlatform = deviceConfig.device.platform
            ?: error("Device '${deviceConfig.device.udid}' does not define platform and was not resolved from soluna-ext")
        require(planPlatform.equals(devicePlatform, ignoreCase = true)) {
            "Plan app platform '$planPlatform' does not match device platform '$devicePlatform'"
        }
    }

    private fun JsonNode.toPlainValue(): Any? {
        return objectMapper.convertValue(this, Any::class.java)
    }

    companion object {
        private val standardCapabilityNames = setOf(
            "browserName",
            "browserVersion",
            "platformName",
            "acceptInsecureCerts",
            "pageLoadStrategy",
            "proxy",
            "setWindowRect",
            "timeouts",
            "unhandledPromptBehavior",
        )

        fun normalizeCapabilityKey(key: String): String {
            return if (key.contains(":") || key in standardCapabilityNames) {
                key
            } else {
                "appium:$key"
            }
        }

        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
        }
    }
}

private fun String.toPlatformName(): String {
    return when (this) {
        "android" -> "Android"
        "ios" -> "iOS"
        else -> error("Unsupported device platform '$this'")
    }
}

private fun String.toDefaultAutomationName(): String {
    return when (this) {
        "android" -> "UiAutomator2"
        "ios" -> "XCUITest"
        else -> error("Unsupported device platform '$this'")
    }
}
