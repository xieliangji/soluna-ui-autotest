package com.soluna.ui.autotest.appium.wda

import com.soluna.ui.autotest.appium.server.AppiumServerConfig
import com.soluna.ui.autotest.appium.server.LocalProcessAppiumServerManager
import com.soluna.ui.autotest.config.DeviceAppiumDefinition
import com.soluna.ui.autotest.config.DeviceAppiumServerDefinition
import com.soluna.ui.autotest.config.DeviceConfigDefinition
import com.soluna.ui.autotest.config.DeviceConfigResolver
import com.soluna.ui.autotest.config.DeviceDefinition
import com.soluna.ui.autotest.config.DeviceIosDefinition
import com.soluna.ui.autotest.config.DeviceWdaDefinition
import com.soluna.ui.autotest.config.toWdaConfig
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
