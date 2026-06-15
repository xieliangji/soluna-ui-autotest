package com.ugreen.iot.soluna.autotest.artifact

import com.ugreen.iot.soluna.autotest.appium.server.AppiumServerHandle
import com.ugreen.iot.soluna.autotest.config.DeviceAppiumDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceAppiumServerDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceConfigDefinition
import com.ugreen.iot.soluna.autotest.config.DeviceDefinition
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.core.execution.PlanExecutionResult
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.runner.PlanRunResult
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

class PlanResourceManifestWriterTest {
    @Test
    fun `writes empty plan resource manifest beside report`() {
        val root = Files.createTempDirectory("soluna-manifest-test")
        val writer = PlanResourceManifestWriter(
            clock = Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC),
        )

        val result = writer.write(
            result = planRunResult(),
            directory = root,
            screenshots = emptyList(),
            artifactUploader = null,
        )

        assertTrue(Files.exists(result.file))
        val json = Files.readString(result.file)
        assertTrue(json.contains("\"schemaVersion\" : \"1.0\""))
        assertTrue(json.contains("\"resources\" : [ ]"))
    }

    private fun planRunResult(): PlanRunResult {
        return PlanRunResult(
            plan = PlanDefinition(
                schemaVersion = "1.0",
                id = "plan-001",
                name = "Plan 001",
            ),
            deviceConfig = DeviceConfigDefinition(
                schemaVersion = "1.0",
                id = "android-device",
                device = DeviceDefinition(
                    platform = "android",
                    udid = "android-001",
                ),
                appium = DeviceAppiumDefinition(
                    server = DeviceAppiumServerDefinition(managed = false),
                ),
            ),
            serverHandle = AppiumServerHandle(
                url = "http://127.0.0.1:4725",
                managed = false,
            ),
            driverSession = null,
            executionResult = PlanExecutionResult(
                runId = "run-001",
                planId = "plan-001",
                status = ExecutionStatus.PASSED,
                stages = emptyList(),
            ),
        )
    }
}
