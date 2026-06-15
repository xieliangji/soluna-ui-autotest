package com.ugreen.iot.soluna.autotest.appium.ext

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealAndroidSolunaExtSmokeTest {
    @Test
    fun `discovers connected Android device through soluna ext plugin`() {
        val udid = System.getenv("SOLUNA_ANDROID_UDID") ?: return
        val serverUrl = System.getenv("SOLUNA_APPIUM_SERVER_URL") ?: "http://127.0.0.1:4725"
        val client = SolunaAppiumExtHttpClient(URI.create(serverUrl))

        val devices = client.listDevices()
        val lookup = client.getDevice(udid)
        val adbResult = client.executeCommand(
            CommandExecuteRequest(
                tool = SupportedCommandTool.ADB,
                args = listOf("devices", "-l"),
                timeoutMs = 5_000,
                maxOutputBytes = 65_536,
            ),
        )

        val device = assertNotNull(lookup.device)
        assertTrue(devices.devices.any { it.udid == udid && it.platform == Platform.ANDROID })
        assertEquals(udid, device.udid)
        assertEquals(Platform.ANDROID, device.platform)
        assertTrue(adbResult.ok)
        assertTrue(adbResult.stdout.contains(udid))
    }
}
