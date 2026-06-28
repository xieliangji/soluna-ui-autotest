package io.soluna.ui.autotest.report

import com.fasterxml.jackson.databind.ObjectMapper
import io.soluna.ui.autotest.appium.server.AppiumServerHandle
import io.soluna.ui.autotest.config.DeviceAppiumDefinition
import io.soluna.ui.autotest.config.DeviceAppiumServerDefinition
import io.soluna.ui.autotest.config.DeviceConfigDefinition
import io.soluna.ui.autotest.config.DeviceDefinition
import io.soluna.ui.autotest.core.execution.ActionExecutionResult
import io.soluna.ui.autotest.core.execution.CaseExecutionResult
import io.soluna.ui.autotest.core.execution.ExecutionStatus
import io.soluna.ui.autotest.core.execution.PlanExecutionResult
import io.soluna.ui.autotest.core.execution.StageExecutionResult
import io.soluna.ui.autotest.core.model.PlanDefinition
import io.soluna.ui.autotest.runner.PlanRunResult
import io.soluna.ui.autotest.schema.JsonSchemaDslValidator
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(Files.readString(result.dataFile).contains("\"productModel\" : \"Plan 001\""))
        assertTrue(Files.readString(result.dataFile).contains("\"startedAt\" : \"2026-06-13T00:00:01Z\""))
        assertTrue(Files.readString(result.dataFile).contains("\"finishedAt\" : \"2026-06-13T00:00:01.025Z\""))
        assertTrue(Files.readString(result.dataFile).contains("\"deviceName\" : \"Demo Android\""))
        assertTrue(Files.readString(result.dataFile).contains("\"summary\""))
        assertTrue(Files.readString(result.dataFile).contains("\"actionKeyword\" : \"tap\""))
        assertTrue(Files.readString(result.dataFile).contains("\"durationMs\" : 25"))
        assertTrue(Files.readString(result.dataFile).contains("\"phase\" : \"case.teardown\""))
        val html = Files.readString(result.htmlFile)
        assertTrue(html.contains("execution-result.json"))
        assertTrue(html.contains("plan-resource-manifest.json"))
        assertTrue(html.contains("App UI自动化测试"))
        assertTrue(html.contains("报告资源"))
        assertTrue(html.contains("执行概览"))
        assertTrue(html.contains("设备名称"))
        assertTrue(html.contains("开始时间"))
        assertTrue(html.contains("结束时间"))
        assertFalse(html.contains("生成时间"))
        assertTrue(html.indexOf("<h2>报告资源</h2>") < html.indexOf("<span class=\"label\">阶段通过</span>"))
        assertFalse(html.contains("<th>明细</th>"))
        assertFalse(html.contains("detail-button"))
        assertTrue(html.contains("<details class=\"panel overview-details\" open>"))
        assertTrue(html.contains("<th>操作</th>"))
        assertTrue(html.contains("class=\"numeric-cell\""))
        assertTrue(html.contains("class=\"detail-link\""))
        assertTrue(html.contains(">动作明细</button>"))
        assertTrue(html.contains("body.modal-open { overflow: hidden; }"))
        assertTrue(html.contains("document.body.classList.add('modal-open')"))
        assertTrue(html.contains("document.body.classList.remove('modal-open')"))
        assertTrue(html.contains("class=\"modal-frame\""))
        assertTrue(html.contains("flex-direction: column"))
        assertTrue(html.contains("<table class=\"modal-action-table\">"))
        assertTrue(html.contains(".modal-action-table td"))
        assertTrue(html.contains("vertical-align: middle"))
        assertTrue(html.contains(".modal-action-table td:nth-child(4)"))
        assertTrue(html.contains("white-space: nowrap"))
        assertTrue(html.contains("class=\"close-button\" type=\"button\" data-close-dialog aria-label=\"关闭\""))
        assertTrue(html.contains("<svg viewBox=\"0 0 24 24\""))
        assertFalse(html.contains("data-close-dialog>关闭</button>"))
        assertTrue(html.contains("<tr><th>环节</th><th>#</th><th>动作</th><th>状态</th><th>重试</th><th>耗时</th><th>消息</th><th>错误</th></tr>"))
        assertTrue(Regex("""<td><span class="status status-passed">通过</span></td>\s*<td>0</td>""").containsMatchIn(html))
        assertFalse(html.contains("<tr><th>阶段</th><th>用例</th><th>环节</th>"))
        assertTrue(html.contains("data-case-dialog"))
        assertTrue(html.contains("data-dialog-target=\"case-detail-stage-001-case-001\""))
        assertTrue(html.contains("role=\"button\""))
        assertTrue(html.contains("tap #open-mine"))
        assertTrue(html.contains("case.teardown"))
        assertEquals(
            emptyList(),
            JsonSchemaDslValidator().validate(
                "/schemas/v1/report-data.schema.json",
                ObjectMapper().readTree(Files.readString(result.dataFile)),
            ),
        )
    }

    @Test
    fun `writes failure summary for failed actions`() {
        val root = Files.createTempDirectory("soluna-report-failure-test")
        val writer = LocalReportWriter(
            outputRoot = root,
            clock = { Instant.parse("2026-06-13T00:00:00Z") },
        )

        val result = writer.write(planRunResultWithFailure())
        val html = Files.readString(result.htmlFile)
        val json = Files.readString(result.dataFile)

        assertTrue(html.contains("失败摘要"))
        assertTrue(html.contains("<table class=\"overview-table\">"))
        assertTrue(html.contains("<table class=\"failure-table\">"))
        assertTrue(html.contains("class=\"compact-cell error\""))
        assertTrue(Regex("""class="compact-cell error"""").findAll(html).count() >= 2)
        assertTrue(html.contains("class=\"compact-text\" title=\"home marker missing\">home marker missing</span>"))
        assertTrue(html.contains(".compact-text"))
        assertTrue(html.contains(".compact-cell { max-width: 0; min-width: 0; overflow: hidden; vertical-align: middle; }"))
        assertTrue(html.contains("text-overflow: ellipsis"))
        assertTrue(html.contains("assertElementExists #assert-home"))
        assertTrue(json.contains("\"failures\""))
        assertTrue(json.contains("\"caseFailed\" : 1"))
        assertEquals(
            emptyList(),
            JsonSchemaDslValidator().validate(
                "/schemas/v1/report-data.schema.json",
                ObjectMapper().readTree(json),
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
                    name = "Demo Android",
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
                                actions = listOf(
                                    ActionExecutionResult.passed("ok").copy(
                                        actionId = "open-mine",
                                        actionKeyword = "tap",
                                        startedAt = "2026-06-13T00:00:01Z",
                                        finishedAt = "2026-06-13T00:00:01.025Z",
                                        durationMs = 25,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun planRunResultWithFailure(): PlanRunResult {
        val base = planRunResult()
        return base.copy(
            executionResult = PlanExecutionResult(
                runId = "run-002",
                planId = "plan-001",
                status = ExecutionStatus.FAILED,
                stages = listOf(
                    StageExecutionResult(
                        stageId = "stage-001",
                        status = ExecutionStatus.FAILED,
                        cases = listOf(
                            CaseExecutionResult(
                                caseId = "case-001",
                                status = ExecutionStatus.FAILED,
                                actions = listOf(
                                    ActionExecutionResult.failed("home marker missing").copy(
                                        actionId = "assert-home",
                                        actionKeyword = "assertElementExists",
                                        durationMs = 3000,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
