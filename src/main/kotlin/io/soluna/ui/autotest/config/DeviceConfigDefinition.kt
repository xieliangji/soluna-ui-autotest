package io.soluna.ui.autotest.config

import com.fasterxml.jackson.databind.JsonNode
import io.soluna.ui.autotest.appium.server.AppiumServerConfig
import io.soluna.ui.autotest.appium.wda.WdaConfig

data class DeviceConfigDefinition(
    val schemaVersion: String,
    val id: String,
    val device: DeviceDefinition,
    val appium: DeviceAppiumDefinition,
    val ios: DeviceIosDefinition = DeviceIosDefinition(),
)

data class DeviceDefinition(
    val platform: String? = null,
    val udid: String,
    val name: String? = null,
    val osVersion: String? = null,
)

data class DeviceAppiumDefinition(
    val server: DeviceAppiumServerDefinition,
    val capabilities: Map<String, JsonNode> = emptyMap(),
)

data class DeviceIosDefinition(
    val wda: DeviceWdaDefinition = DeviceWdaDefinition(),
)

data class DeviceWdaDefinition(
    val enabled: Boolean = true,
    val managed: Boolean = true,
    val url: String? = null,
    val host: String = "127.0.0.1",
    val hostPort: Int? = null,
    val devicePort: Int = 8100,
    val executable: String = "ios",
    val tunnelMode: String = "userspace",
    val tunnelInfoHost: String = "127.0.0.1",
    val tunnelInfoPort: Int? = null,
    val userspaceTunnelPort: Int? = null,
    val bundleId: String? = null,
    val testRunnerBundleId: String? = null,
    val xctestConfig: String? = null,
    val extraArgs: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val startupTimeoutMs: Long = 60_000,
    val tunnelStartupDelayMs: Long = 2_000,
    val runwdaStartupDelayMs: Long = 2_000,
)

data class DeviceAppiumServerDefinition(
    val managed: Boolean,
    val url: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val usePlugins: List<String> = listOf("soluna-ext"),
    val ensureDrivers: List<String> = listOf("uiautomator2", "xcuitest"),
    val executable: String = "appium",
    val extraArgs: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val startupTimeoutMs: Long = 30_000,
)

fun DeviceAppiumServerDefinition.toAppiumServerConfig(): AppiumServerConfig {
    return AppiumServerConfig(
        managed = managed,
        baseUrl = url,
        host = host,
        port = port,
        usePlugins = usePlugins,
        ensureDrivers = ensureDrivers,
        executable = executable,
        extraArgs = extraArgs,
        environment = environment,
        startupTimeoutMs = startupTimeoutMs,
    )
}

fun DeviceConfigDefinition.toWdaConfig(): WdaConfig {
    val wda = ios.wda
    return WdaConfig(
        udid = device.udid,
        osVersion = device.osVersion,
        enabled = wda.enabled,
        managed = wda.managed,
        url = wda.url,
        host = wda.host,
        hostPort = wda.hostPort,
        devicePort = wda.devicePort,
        executable = wda.executable,
        tunnelMode = wda.tunnelMode,
        tunnelInfoHost = wda.tunnelInfoHost,
        tunnelInfoPort = wda.tunnelInfoPort,
        userspaceTunnelPort = wda.userspaceTunnelPort,
        bundleId = wda.bundleId,
        testRunnerBundleId = wda.testRunnerBundleId,
        xctestConfig = wda.xctestConfig,
        extraArgs = wda.extraArgs,
        environment = wda.environment,
        startupTimeoutMs = wda.startupTimeoutMs,
        tunnelStartupDelayMs = wda.tunnelStartupDelayMs,
        runwdaStartupDelayMs = wda.runwdaStartupDelayMs,
    )
}
