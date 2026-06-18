package com.soluna.ui.autotest.appium.wda

import com.soluna.ui.autotest.appium.ext.CommandExecuteRequest
import com.soluna.ui.autotest.appium.ext.CommandExecuteResult
import com.soluna.ui.autotest.appium.ext.CreateLogSessionRequest
import com.soluna.ui.autotest.appium.ext.CreateLogSessionResult
import com.soluna.ui.autotest.appium.ext.DeleteLogSessionRequest
import com.soluna.ui.autotest.appium.ext.DeleteLogSessionResult
import com.soluna.ui.autotest.appium.ext.DeviceLookupResult
import com.soluna.ui.autotest.appium.ext.ListDevicesResult
import com.soluna.ui.autotest.appium.ext.ReadLogSessionRequest
import com.soluna.ui.autotest.appium.ext.ReadLogSessionResult
import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import com.soluna.ui.autotest.appium.ext.WdaBundleLookupResult
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WdaBundleResolverTest {
    @Test
    fun `keeps configured test runner bundle id without soluna ext lookup`() {
        var lookupCount = 0
        val resolver = SolunaExtWdaBundleResolver {
            lookupCount += 1
            FakeExtClient(WdaBundleLookupResult(exists = true, udid = "ios-001", bundleId = "detected"))
        }

        val resolution = resolver.resolve(
            WdaBundleResolveRequest(
                appiumServerUrl = "http://127.0.0.1:4723",
                udid = "ios-001",
                bundleId = "configured.bundle",
                testRunnerBundleId = "configured.runner",
                xctestConfig = "ConfiguredRunner.xctest",
            ),
        )

        assertEquals(0, lookupCount)
        assertEquals("configured.bundle", resolution.bundleId)
        assertEquals("configured.runner", resolution.testRunnerBundleId)
        assertEquals("ConfiguredRunner.xctest", resolution.xctestConfig)
        assertEquals(WdaBundleResolutionSource.CONFIG, resolution.source)
    }

    @Test
    fun `resolves missing test runner bundle id through soluna ext`() {
        val resolver = SolunaExtWdaBundleResolver { uri ->
            assertEquals(URI.create("http://127.0.0.1:4723"), uri)
            FakeExtClient(
                WdaBundleLookupResult(
                    exists = true,
                    udid = "ios-001",
                    bundleId = "com.facebook.WebDriverAgentRunner.xctrunner",
                ),
            )
        }

        val resolution = resolver.resolve(
            WdaBundleResolveRequest(
                appiumServerUrl = "http://127.0.0.1:4723",
                udid = "ios-001",
                bundleId = null,
                testRunnerBundleId = null,
                xctestConfig = null,
            ),
        )

        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", resolution.bundleId)
        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", resolution.testRunnerBundleId)
        assertEquals("WebDriverAgentRunner.xctest", resolution.xctestConfig)
        assertEquals(WdaBundleResolutionSource.SOLUNA_EXT, resolution.source)
    }

    @Test
    fun `fails when soluna ext cannot find WDA runner bundle`() {
        val resolver = SolunaExtWdaBundleResolver {
            FakeExtClient(
                WdaBundleLookupResult(
                    exists = false,
                    udid = "ios-001",
                    message = "not installed",
                ),
            )
        }

        val error = assertFailsWith<IllegalStateException> {
            resolver.resolve(
                WdaBundleResolveRequest(
                    appiumServerUrl = "http://127.0.0.1:4723",
                    udid = "ios-001",
                    bundleId = null,
                    testRunnerBundleId = null,
                    xctestConfig = null,
                ),
            )
        }

        assertEquals(
            "WDA runner bundle id was not resolved from soluna-ext for device 'ios-001': not installed",
            error.message,
        )
    }

    private class FakeExtClient(
        private val wdaBundle: WdaBundleLookupResult,
    ) : SolunaAppiumExtClient {
        override fun getDevice(udid: String): DeviceLookupResult {
            error("not used")
        }

        override fun listDevices(): ListDevicesResult {
            error("not used")
        }

        override fun getWdaBundle(udid: String): WdaBundleLookupResult {
            return wdaBundle
        }

        override fun executeCommand(request: CommandExecuteRequest): CommandExecuteResult {
            error("not used")
        }

        override fun createLogSession(request: CreateLogSessionRequest): CreateLogSessionResult {
            error("not used")
        }

        override fun readLogSession(request: ReadLogSessionRequest): ReadLogSessionResult {
            error("not used")
        }

        override fun deleteLogSession(request: DeleteLogSessionRequest): DeleteLogSessionResult {
            error("not used")
        }
    }
}
