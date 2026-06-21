package com.soluna.ui.autotest.config

import com.soluna.ui.autotest.appium.ext.AppLookupResult
import com.soluna.ui.autotest.appium.ext.CommandExecuteRequest
import com.soluna.ui.autotest.appium.ext.CommandExecuteResult
import com.soluna.ui.autotest.appium.ext.CreateLogSessionRequest
import com.soluna.ui.autotest.appium.ext.CreateLogSessionResult
import com.soluna.ui.autotest.appium.ext.DeleteLogSessionRequest
import com.soluna.ui.autotest.appium.ext.DeleteLogSessionResult
import com.soluna.ui.autotest.appium.ext.DeviceLookupResult
import com.soluna.ui.autotest.appium.ext.InstalledAppInfo
import com.soluna.ui.autotest.appium.ext.ListDevicesResult
import com.soluna.ui.autotest.appium.ext.Platform
import com.soluna.ui.autotest.appium.ext.ReadLogSessionRequest
import com.soluna.ui.autotest.appium.ext.ReadLogSessionResult
import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import com.soluna.ui.autotest.appium.ext.WdaBundleLookupResult
import com.soluna.ui.autotest.core.model.AppDefinition
import com.soluna.ui.autotest.core.model.PlanDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class AppMetadataResolverTest {
    @Test
    fun `resolves actual installed app name from soluna ext`() {
        val resolver = AppMetadataResolver {
            FakeExtClient(
                AppLookupResult(
                    exists = true,
                    app = InstalledAppInfo(
                        platform = Platform.ANDROID,
                        udid = "android-001",
                        appId = "com.example.app",
                        name = "Actual App",
                    ),
                ),
            )
        }

        val resolved = resolver.resolve(
            plan = plan(appName = "Configured App"),
            deviceConfig = deviceConfig(),
            appiumServerUrl = "http://127.0.0.1:4723",
        )

        assertEquals("Actual App", resolved.app?.name)
        assertEquals("android", resolved.app?.platform)
    }

    @Test
    fun `keeps configured app name when ext lookup is unavailable`() {
        val resolver = AppMetadataResolver {
            object : FakeExtClient(AppLookupResult(exists = false)) {
                override fun getApp(
                    udid: String,
                    appId: String,
                ): AppLookupResult {
                    error("ext unavailable")
                }
            }
        }

        val resolved = resolver.resolve(
            plan = plan(appName = "Configured App"),
            deviceConfig = deviceConfig(),
            appiumServerUrl = "http://127.0.0.1:4723",
        )

        assertEquals("Configured App", resolved.app?.name)
        assertEquals("android", resolved.app?.platform)
    }

    private fun plan(appName: String): PlanDefinition {
        return PlanDefinition(
            schemaVersion = "1.0",
            id = "plan-001",
            name = "Plan 001",
            productModel = "Product 001",
            app = AppDefinition(
                id = "com.example.app",
                name = appName,
                platform = "android",
            ),
        )
    }

    private fun deviceConfig(): DeviceConfigDefinition {
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
            ),
        )
    }

    private open class FakeExtClient(
        private val appLookupResult: AppLookupResult,
    ) : SolunaAppiumExtClient {
        open override fun getApp(
            udid: String,
            appId: String,
        ): AppLookupResult {
            return appLookupResult
        }

        override fun getDevice(udid: String): DeviceLookupResult {
            error("not used")
        }

        override fun listDevices(): ListDevicesResult {
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
