package com.ugreen.iot.soluna.autotest.appium.wda

import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerConfig
import com.ugreen.iot.soluna.autotest.appium.server.LocalProcessAppiumServerManager
import com.ugreen.iot.soluna.autotest.config.DeviceAppiumDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceAppiumServerDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceConfigDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceConfigResolver
import com.ugreen.iot.soluna.autotest.config.DeviceDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceIosDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceWdaDefinition
import com.ugreen.iot.soluna.autotest.config.toWdaConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealIosWdaSmokeTest {
    @Test
    fun `starts installed WDA on real iOS device through go-ios`() {
        if (System.getenv("SOLUNA_IOS_WDA_SMOKE") != "true") {
            return
        }
        val udid = requireNotNull(System.getenv("SOLUNA_IOS_UDID")) {
            "SOLUNA_IOS_UDID is required when SOLUNA_IOS_WDA_SMOKE=true"
        }
        val appiumExecutable = System.getenv("SOLUNA_APPIUM_EXECUTABLE") ?: "appium"
        val iosExecutable = System.getenv("SOLUNA_GO_IOS_EXECUTABLE") ?: "ios"
        val runwdaStartupDelayMs = System.getenv("SOLUNA_IOS_WDA_STARTUP_DELAY_MS")?.toLongOrNull() ?: 10_000

        val appiumServerManager = LocalProcessAppiumServerManager()
        val wdaManager = LocalGoIosWdaManager()
        val wdaBundleResolver = SolunaExtWdaBundleResolver()
        val logDirectory = Path.of("build", "soluna-runs", "ios-wda-smoke", "wda-logs")
            .toAbsolutePath()
            .normalize()
        Files.createDirectories(logDirectory)
        val appiumHandle = appiumServerManager.ensureRunning(
            AppiumServerConfig(
                managed = true,
                executable = appiumExecutable,
                usePlugins = listOf("soluna-ext"),
                startupTimeoutMs = 30_000,
            ),
        )
        var wdaHandle: WdaHandle? = null
        try {
            val resolvedDeviceConfig = DeviceConfigResolver().resolve(
                config = DeviceConfigDefinition(
                    schemaVersion = "1.0",
                    id = udid,
                    device = DeviceDefinition(
                        platform = "ios",
                        udid = udid,
                    ),
                    appium = DeviceAppiumDefinition(
                        server = DeviceAppiumServerDefinition(managed = true),
                    ),
                    ios = DeviceIosDefinition(
                        wda = DeviceWdaDefinition(
                            enabled = true,
                            managed = true,
                            executable = iosExecutable,
                            startupTimeoutMs = 90_000,
                            runwdaStartupDelayMs = runwdaStartupDelayMs,
                        ),
                    ),
                ),
                appiumServerUrl = appiumHandle.url,
            )
            assertNotNull(resolvedDeviceConfig.device.osVersion)
            val wdaBundle = wdaBundleResolver.resolve(
                WdaBundleResolveRequest(
                    appiumServerUrl = appiumHandle.url,
                    udid = resolvedDeviceConfig.device.udid,
                    bundleId = resolvedDeviceConfig.ios.wda.bundleId,
                    testRunnerBundleId = resolvedDeviceConfig.ios.wda.testRunnerBundleId,
                    xctestConfig = resolvedDeviceConfig.ios.wda.xctestConfig,
                ),
            )
            val resolvedWdaDeviceConfig = resolvedDeviceConfig.copy(
                ios = resolvedDeviceConfig.ios.copy(
                    wda = resolvedDeviceConfig.ios.wda.copy(
                        bundleId = wdaBundle.bundleId,
                        testRunnerBundleId = wdaBundle.testRunnerBundleId,
                        xctestConfig = wdaBundle.xctestConfig,
                    ),
                ),
            )

            wdaHandle = wdaManager.ensureRunning(resolvedWdaDeviceConfig.toWdaConfig().copy(logDirectory = logDirectory))

            assertTrue(wdaManager.isRunning(wdaHandle))
        } finally {
            wdaHandle?.let { wdaManager.stop(it) }
            appiumServerManager.stop(appiumHandle)
        }
    }
}
