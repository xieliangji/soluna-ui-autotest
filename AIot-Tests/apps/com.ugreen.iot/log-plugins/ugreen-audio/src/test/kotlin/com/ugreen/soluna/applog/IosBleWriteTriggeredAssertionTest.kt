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
    fun `plugin exposes legacy and platform-neutral BLE write assertions`() {
        val plugin = UgreenAudioAppLogPlugin()

        assertTrue(plugin.assertion("ios-ble-write-triggered") === BleWriteTriggeredAssertion)
        assertTrue(plugin.assertion("ble-write-triggered") === BleWriteTriggeredAssertion)
        assertTrue(plugin.assertion("android-ble-write-triggered") === BleWriteTriggeredAssertion)
        assertTrue(plugin.assertion("ble-command-ack") == null)
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

        val result = BleWriteTriggeredAssertion.evaluate(input(logFile = logFile, args = null))

        assertTrue(result.passed)
    }

    @Test
    fun `passes with configured required markers`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(logFile, "{\"message\":\"CBMsgIdCharacteristicWriteValue from com.ugreen.iot-central\"}\n")

        val result = BleWriteTriggeredAssertion.evaluate(
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
    fun `passes Android captures with default payload exchange markers`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(
            logFile,
            """
            {"tag":"ReactNativeJS","message":"'[Headphones]', '[蓝牙下发] ⬇️ 10:49:30.521'"}
            {"tag":"ReactNativeJS","message":"'[Headphones]', '  命令: PROMPT_LANG'"}
            {"tag":"UGRNBluetoothBridge","message":"发送 ble 数据 data: AAC002090010D0E87F4D97D364A56BF8BDBBD5F4A78E4068"}
            {"tag":"ReactNativeJS","message":"'[Headphones]', '[UG3] Payload 已解密:0,1,1,2 -> 16 -> 4 bytes'"}
            {"tag":"UGRNLogController","message":"[Headphones] [蓝牙上报] ⬆️ 10:49:31.080"}
            """.trimIndent() + "\n",
        )

        val result = BleWriteTriggeredAssertion.evaluate(
            input(
                logFile = logFile,
                platform = "android",
                args = null,
            ),
        )

        assertTrue(result.passed)
    }

    @Test
    fun `passes Android captures with configured Android command markers`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(logFile, "{\"message\":\"[Headphones] 命令: PROMPT_LANG; [蓝牙下发] payload queued\"}\n")

        val result = BleWriteTriggeredAssertion.evaluate(
            input(
                logFile = logFile,
                platform = "android",
                args = mapper.createObjectNode().also {
                    it.putArray("androidContainsAll")
                        .add("命令: PROMPT_LANG")
                        .add("[蓝牙下发]")
                },
            ),
        )

        assertTrue(result.passed)
    }

    @Test
    fun `Android ignores legacy iOS default markers and uses Android payload defaults`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(
            logFile,
            """
            {"message":"[蓝牙下发]"}
            {"message":"发送 ble 数据 data: AAC002090010"}
            {"message":"[蓝牙上报]"}
            {"message":"Payload 已解密:0,1,1,2 -> 16 -> 4 bytes"}
            """.trimIndent() + "\n",
        )

        val result = BleWriteTriggeredAssertion.evaluate(
            input(
                logFile = logFile,
                platform = "android",
                args = mapper.createObjectNode().also {
                    it.putArray("containsAll")
                        .add("CBMsgIdCharacteristicWriteValue")
                        .add("Writing value without response")
                        .add("com.ugreen.iot-central")
                },
            ),
        )

        assertTrue(result.passed)
    }

    @Test
    fun `fails when required marker is missing`() {
        val logFile = Files.createTempFile("app-log-", ".jsonl")
        Files.writeString(logFile, "{\"message\":\"CBMsgIdCharacteristicWriteValue\"}\n")

        val result = BleWriteTriggeredAssertion.evaluate(
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
