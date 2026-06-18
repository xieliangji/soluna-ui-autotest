package com.soluna.ui.autotest.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.appium.server.AppiumServerHandle
import com.soluna.ui.autotest.config.DeviceAppiumDefinition
import com.soluna.ui.autotest.config.DeviceAppiumServerDefinition
import com.soluna.ui.autotest.config.DeviceConfigDefinition
import com.soluna.ui.autotest.config.DeviceDefinition
import com.soluna.ui.autotest.core.execution.ActionExecutionResult
import com.soluna.ui.autotest.core.execution.CaseExecutionResult
import com.soluna.ui.autotest.core.execution.ExecutionStatus
import com.soluna.ui.autotest.core.execution.PlanExecutionResult
import com.soluna.ui.autotest.core.execution.StageExecutionResult
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.runner.PlanRunResult
import com.soluna.ui.autotest.schema.JsonSchemaDslValidator
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalReportWriterTest {
    @Test
    fun `writes local html and json report files`() {
        val root = Files.createTempDirectory("soluna-report-test")
        val writer = LocalReportWriter(
            outputRoot = root,
            clock = { Instant.parse("2026-06-13T00:00:00Z") },
        )

        val result = writer.write(planRunResult())

        assertTrue(Files.exists(result.dataFile))
        assertTrue(Files.exists(result.htmlFile))
        assertTrue(Files.readString(result.dataFile).contains("\"schemaVersion\" : \"1.0\""))
        assertTrue(Files.readString(result.dataFile).contains("\"runId\" : \"run-001\""))
        assertTrue(Files.readString(result.dataFile).contains("\"phase\" : \"case.teardown\""))
        assertTrue(Files.readString(result.htmlFile).contains("execution-result.json"))
        assertTrue(Files.readString(result.htmlFile).contains("plan-resource-manifest.json"))
        assertTrue(Files.readString(result.htmlFile).contains("case.teardown"))
        assertEquals(
            emptyList(),
            JsonSchemaDslValidator().validate(
                "/schemas/v1/report-data.schema.json",
                ObjectMapper().readTree(Files.readString(result.dataFile)),
            ),
        )
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
                stages = listOf(
                    StageExecutionResult(
                        stageId = "stage-001",
                        status = ExecutionStatus.PASSED,
                        cases = listOf(
                            CaseExecutionResult(
                                caseId = "case-001",
                                status = ExecutionStatus.PASSED,
                                teardownActions = listOf(ActionExecutionResult.passed("cleaned")),
                                actions = listOf(ActionExecutionResult.passed("ok")),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
