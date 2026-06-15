package com.ugreen.iot.soluna.autotest.appium.ext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolunaAppiumExtModelsTest {
    @Test
    fun `command result ok follows exit code`() {
        val ok = CommandExecuteResult(
            command = "adb",
            args = listOf("devices"),
            exitCode = 0,
            timedOut = false,
            truncated = false,
            durationMs = 10,
            stdout = "",
            stderr = "",
        )
        val failed = ok.copy(exitCode = 1)

        assertTrue(ok.ok)
        assertFalse(failed.ok)
    }

    @Test
    fun `supported command tool keeps plugin wire values`() {
        assertEquals("adb", SupportedCommandTool.ADB.toWireValue())
        assertEquals("go-ios", SupportedCommandTool.GO_IOS.toWireValue())
        assertEquals("ios", SupportedCommandTool.IOS.toWireValue())
    }
}
