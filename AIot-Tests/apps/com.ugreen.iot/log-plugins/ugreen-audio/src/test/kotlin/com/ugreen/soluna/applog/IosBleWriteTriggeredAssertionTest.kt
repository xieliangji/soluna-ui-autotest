package com.ugreen.soluna.applog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.extension.applog.AppLogAssertionInput
import com.soluna.ui.autotest.extension.applog.AppLogAssertionRunContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosBleWriteTriggeredAssertionTest {
    private val mapper = ObjectMapper()

    @Test
    fun `plugin exposes only the iOS BLE write assertion`() {
        val plugin = UgreenAudioAppLogPlugin()

        assertTrue(plugin.assertion("ios-ble-write-triggered") === IosBleWriteTriggeredAssertion)
        assertTrue(plugin.assertion("ble-command-ack") == null)
        assertTrue(plugin.assertion("android-spp-command-report") == null)
    }

    @Test
    fun `passes with default CoreBluetooth write markers`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(
            logFile,
            """
            {"process":"bluetoothd","message":"Received XPC message \"CBMsgIdCharacteristicWriteValue\" from session \"com.ugreen.iot-central-36137-2468\""}
            {"process":"bluetoothd","message":"Writing value without response to characteristic handle 0x0006 on device \"12D24759-ED59-164F-8C5E-9D17E68821EC\""}
            """.trimIndent() + "\n",
        )

        val result = IosBleWriteTriggeredAssertion.evaluate(input(logFile = logFile, args = null))

        assertTrue(result.passed)
    }

    @Test
    fun `passes with configured required markers`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(logFile, "{\"message\":\"CBMsgIdCharacteristicWriteValue from com.ugreen.iot-central\"}\n")

        val result = IosBleWriteTriggeredAssertion.evaluate(
            input(
                logFile = logFile,
                args = mapper.createObjectNode().also {
                    it.putArray("containsAll")
                        .add("CBMsgIdCharacteristicWriteValue")
                        .add("com.ugreen.iot-central")
                },
            ),
        )

        assertTrue(result.passed)
    }

    @Test
    fun `rejects Android captures`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(logFile, "{\"message\":\"CBMsgIdCharacteristicWriteValue\"}\n")

        val result = IosBleWriteTriggeredAssertion.evaluate(
            input(
                logFile = logFile,
                platform = "android",
                args = null,
            ),
        )

        assertFalse(result.passed)
    }

    @Test
    fun `fails when required marker is missing`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(logFile, "{\"message\":\"CBMsgIdCharacteristicWriteValue\"}\n")

        val result = IosBleWriteTriggeredAssertion.evaluate(
            input(
                logFile = logFile,
                args = mapper.createObjectNode().also {
                    it.putArray("containsAll")
                        .add("CBMsgIdCharacteristicWriteValue")
                        .add("Writing value without response")
                },
            ),
        )

        assertFalse(result.passed)
    }

    private fun input(
        logFile: Path,
        platform: String = "ios",
        args: JsonNode?,
    ): AppLogAssertionInput {
        return AppLogAssertionInput(
            pluginId = "ugreen-audio",
            assertionName = "ios-ble-write-triggered",
            logFile = logFile,
            source = null,
            args = args,
            context = AppLogAssertionRunContext(
                runId = "run-test",
                planId = "plan-test",
                stageId = "stage-test",
                caseId = "case-test",
                platform = platform,
                udid = "TEST_UDID",
            ),
        )
    }
}
