package io.soluna.ui.autotest.config

import io.soluna.ui.autotest.appium.ext.CommandExecuteRequest
import io.soluna.ui.autotest.appium.ext.CommandExecuteResult
import io.soluna.ui.autotest.appium.ext.AppLookupResult
import io.soluna.ui.autotest.appium.ext.CreateLogSessionRequest
import io.soluna.ui.autotest.appium.ext.CreateLogSessionResult
import io.soluna.ui.autotest.appium.ext.DeleteLogSessionRequest
import io.soluna.ui.autotest.appium.ext.DeleteLogSessionResult
import io.soluna.ui.autotest.appium.ext.DeviceLookupResult
import io.soluna.ui.autotest.appium.ext.ListDevicesResult
import io.soluna.ui.autotest.appium.ext.Platform
import io.soluna.ui.autotest.appium.ext.ReadLogSessionRequest
import io.soluna.ui.autotest.appium.ext.ReadLogSessionResult
import io.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import io.soluna.ui.autotest.appium.ext.UnifiedDeviceInfo
import io.soluna.ui.autotest.appium.ext.WdaBundleLookupResult
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceConfigResolverTest {
    @Test
    fun `fills missing platform and name from soluna ext`() {
        val resolver = DeviceConfigResolver(
            extClientFactory = { uri ->
                assertEquals(URI.create("http://127.0.0.1:4728"), uri)
                FakeExtClient(
                    DeviceLookupResult(
                        exists = true,
                        device = UnifiedDeviceInfo(
                            platform = Platform.ANDROID,
                            udid = "android-001",
                            name = "Demo Android",
                            model = "DemoModel",
                            osVersion = "16",
                        ),
                    ),
                )
            },
        )

        val resolved = resolver.resolve(
            config = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "android-001",
                device = DeviceDefinition(udid = "android-001"),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = true),
                ),
            ),
            appiumServerUrl = "http://127.0.0.1:4728",
        )

        assertEquals("android", resolved.device.platform)
        assertEquals("Demo Android", resolved.device.name)
        assertEquals("16", resolved.device.osVersion)
    }

    @Test
    fun `prefers soluna ext device name even when display name is configured`() {
        val resolver = DeviceConfigResolver(
            extClientFactory = {
                FakeExtClient(
                    DeviceLookupResult(
                        exists = true,
                        device = UnifiedDeviceInfo(
                            platform = Platform.IOS,
                            udid = "ios-001",
                            name = "Demo iPhone",
                            model = "iPhone",
                            osVersion = "17.2",
                        ),
                    ),
                )
            },
        )

        val resolved = resolver.resolve(
            config = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "ios-001",
                device = DeviceDefinition(
                    platform = "ios",
                    udid = "ios-001",
                    name = "Configured iPhone",
                ),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = true),
                ),
            ),
            appiumServerUrl = "http://127.0.0.1:4728",
        )

        assertEquals("ios", resolved.device.platform)
        assertEquals("Demo iPhone", resolved.device.name)
        assertEquals("17.2", resolved.device.osVersion)
    }

    @Test
    fun `keeps complete configured device fields when soluna ext lookup fails`() {
        val resolver = DeviceConfigResolver(
            extClientFactory = {
                object : FakeExtClient(
                    DeviceLookupResult(exists = false),
                ) {
                    override fun getDevice(udid: String): DeviceLookupResult {
                        error("soluna-ext unavailable")
                    }
                }
            },
        )

        val resolved = resolver.resolve(
            config = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "android-001",
                device = DeviceDefinition(
                    platform = "android",
                    udid = "android-001",
                    name = "Configured Android",
                ),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = true),
                ),
            ),
            appiumServerUrl = "http://127.0.0.1:4728",
        )

        assertEquals("android", resolved.device.platform)
        assertEquals("Configured Android", resolved.device.name)
    }

    private open class FakeExtClient(
        private val lookup: DeviceLookupResult,
    ) : SolunaAppiumExtClient {
        open override fun getDevice(udid: String): DeviceLookupResult {
            return lookup
        }

        override fun listDevices(): ListDevicesResult {
            error("not used")
        }

        override fun getApp(
            udid: String,
            appId: String,
        ): AppLookupResult {
            error("not used")
        }

        override fun getWdaBundle(udid: String): WdaBundleLookupResult {
            error("not used")
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
