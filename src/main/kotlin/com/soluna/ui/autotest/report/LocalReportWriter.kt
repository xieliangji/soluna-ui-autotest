package com.soluna.ui.autotest.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.soluna.ui.autotest.runner.PlanRunResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

interface ReportWriter {
    fun write(result: PlanRunResult): ReportWriteResult
}

data class ReportWriteResult(
    val directory: Path,
    val dataFile: Path,
    val htmlFile: Path,
    val resourceManifestFile: Path? = null,
)

class LocalReportWriter(
    private val outputRoot: Path = Path.of("build/soluna-runs"),
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
    private val clock: () -> Instant = { Instant.now() },
) : ReportWriter {
    override fun write(result: PlanRunResult): ReportWriteResult {
        val directory = outputRoot
            .resolve(result.executionResult.runId)
            .resolve("report")
            .toAbsolutePath()
            .normalize()
        Files.createDirectories(directory)

        val dataFile = directory.resolve("execution-result.json")
        val htmlFile = directory.resolve("index.html")
        val data = LocalReportData(
            generatedAt = clock().toString(),
            runId = result.executionResult.runId,
            planId = result.executionResult.planId,
            planName = result.plan.name,
            status = result.executionResult.status.name.lowercase(),
            deviceId = result.deviceConfig.id,
            platform = result.deviceConfig.device.platform ?: "unknown",
            summary = ExecutionReportSummaries.summary(result.executionResult),
            failures = ExecutionReportSummaries.failures(result.executionResult),
            traceArtifacts = result.traceArtifacts.map { artifact ->
                LocalTraceArtifactData(
                    captureId = artifact.captureId,
                    stageId = artifact.stageId,
                    caseId = artifact.caseId,
                    actionId = artifact.actionId,
                    actionKeyword = artifact.actionKeyword,
                    phase = artifact.phase,
                    index = artifact.index,
                    attempt = artifact.attempt,
                    timing = artifact.timing,
                    href = artifact.url ?: artifact.localPath.toAbsolutePath().normalize().toString(),
                    contentType = artifact.contentType,
                    sizeBytes = artifact.sizeBytes,
                    capturedAt = artifact.capturedAt,
                )
            },
            setupActions = result.executionResult.setupActions.mapIndexed { index, action ->
                action.toReportData(index, "plan.setup")
            },
            teardownActions = result.executionResult.teardownActions.mapIndexed { index, action ->
                action.toReportData(index, "plan.teardown")
            },
            stages = result.executionResult.stages.map { stage ->
                LocalStageReportData(
                    stageId = stage.stageId,
                    status = stage.status.name.lowercase(),
                    setupActions = stage.setupActions.mapIndexed { index, action ->
                        action.toReportData(index, "stage.setup")
                    },
                    teardownActions = stage.teardownActions.mapIndexed { index, action ->
                        action.toReportData(index, "stage.teardown")
                    },
                    cases = stage.cases.map { case ->
                        LocalCaseReportData(
                            caseId = case.caseId,
                            status = case.status.name.lowercase(),
                            setupActions = case.setupActions.mapIndexed { index, action ->
                                action.toReportData(index, "case.setup")
                            },
                            teardownActions = case.teardownActions.mapIndexed { index, action ->
                                action.toReportData(index, "case.teardown")
                            },
                            actions = case.actions.mapIndexed { index, action ->
                                action.toReportData(index, "case.action")
                            },
                        )
                    },
                )
            },
        )

        Files.writeString(dataFile, objectMapper.writeValueAsString(data))
        Files.writeString(htmlFile, renderHtml(data))
        return ReportWriteResult(directory, dataFile, htmlFile)
    }

    private fun renderHtml(data: LocalReportData): String {
        val rows = buildList {
            data.setupActions.forEach { action ->
                add(renderActionRow("", "", action))
            }
            data.stages.forEach { stage ->
                stage.setupActions.forEach { action ->
                    add(renderActionRow(stage.stageId, "", action))
                }
                stage.cases.forEach { case ->
                    case.setupActions.forEach { action ->
                        add(renderActionRow(stage.stageId, case.caseId, action))
                    }
                    case.actions.forEach { action ->
                        add(renderActionRow(stage.stageId, case.caseId, action))
                    }
                    case.teardownActions.forEach { action ->
                        add(renderActionRow(stage.stageId, case.caseId, action))
                    }
                }
                stage.teardownActions.forEach { action ->
                    add(renderActionRow(stage.stageId, "", action))
                }
            }
            data.teardownActions.forEach { action ->
                add(renderActionRow("", "", action))
            }
        }.joinToString(separator = "\n")
        val failureSection = renderFailureSection(data.failures)
        val traceSection = renderTraceSection(data.traceArtifacts)
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Soluna Report - ${escape(data.runId)}</title>
              <style>
                :root { color-scheme: light; }
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; color: #202124; background: #f6f8fb; }
                main { max-width: 1280px; margin: 0 auto; padding: 28px 32px 40px; }
                h1 { font-size: 24px; margin: 0 0 8px; }
                h2 { font-size: 18px; margin: 28px 0 12px; }
                .meta { display: grid; grid-template-columns: repeat(2, minmax(220px, 1fr)); gap: 8px 24px; margin: 16px 0 22px; }
                .summary { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 12px; margin-bottom: 24px; }
                .metric { background: #fff; border: 1px solid #d9e2ec; padding: 12px 14px; border-radius: 8px; }
                .metric .value { display: block; font-size: 22px; font-weight: 650; margin-top: 4px; }
                .label { color: #5f6368; font-size: 13px; }
                .status { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 650; }
                .status-passed { background: #e6f4ea; color: #137333; }
                .status-failed { background: #fce8e6; color: #a50e0e; }
                .status-skipped { background: #f1f3f4; color: #5f6368; }
                .status-running { background: #e8f0fe; color: #174ea6; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #dadce0; padding: 8px 10px; text-align: left; font-size: 13px; vertical-align: top; }
                th { background: #eef3f8; }
                tbody tr:nth-child(even) { background: #fbfdff; }
                tbody tr:nth-child(odd) { background: #fff; }
                code { background: #f1f3f4; border-radius: 4px; padding: 1px 4px; }
                .error { color: #a50e0e; white-space: pre-wrap; }
                .message { color: #3c4043; white-space: pre-wrap; }
                @media (max-width: 860px) {
                  main { padding: 20px 16px 32px; }
                  .summary, .meta { grid-template-columns: 1fr; }
                  table { display: block; overflow-x: auto; }
                }
              </style>
            </head>
            <body>
              <main>
              <h1>${escape(data.planName)}</h1>
              <div><span class="${statusClass(data.status)}">${escape(data.status)}</span></div>
              <div class="meta">
                <div><span class="label">Run</span> <code>${escape(data.runId)}</code></div>
                <div><span class="label">Plan</span> <code>${escape(data.planId)}</code></div>
                <div><span class="label">Device</span> <code>${escape(data.deviceId)}</code> / ${escape(data.platform)}</div>
                <div><span class="label">Generated</span> ${escape(data.generatedAt)}</div>
                <div><span class="label">Data</span> <a href="execution-result.json">execution-result.json</a></div>
                <div><span class="label">Resources</span> <a href="plan-resource-manifest.json">plan-resource-manifest.json</a></div>
              </div>
              <section class="summary">
                <div class="metric"><span class="label">Stages</span><span class="value">${data.summary.stagePassed}/${data.summary.stageTotal}</span><span class="label">${data.summary.stageFailed} failed</span></div>
                <div class="metric"><span class="label">Cases</span><span class="value">${data.summary.casePassed}/${data.summary.caseTotal}</span><span class="label">${data.summary.caseFailed} failed</span></div>
                <div class="metric"><span class="label">Actions</span><span class="value">${data.summary.actionPassed}/${data.summary.actionTotal}</span><span class="label">${data.summary.actionFailed} failed</span></div>
                <div class="metric"><span class="label">Trace Artifacts</span><span class="value">${data.traceArtifacts.size}</span><span class="label">diagnostic links</span></div>
              </section>
              $failureSection
              <h2>Action Timeline</h2>
              <table>
                <thead>
                  <tr><th>Stage</th><th>Case</th><th>Phase</th><th>#</th><th>Action</th><th>Status</th><th>Attempt</th><th>Duration</th><th>Message</th><th>Error</th></tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
              $traceSection
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderFailureSection(failures: List<ExecutionFailureSummary>): String {
        if (failures.isEmpty()) {
            return ""
        }
        val rows = failures.joinToString(separator = "\n") { failure ->
            """
                <tr>
                  <td>${escape(failure.stageId.orEmpty())}</td>
                  <td>${escape(failure.caseId.orEmpty())}</td>
                  <td>${escape(failure.phase)}</td>
                  <td>${failure.index}</td>
                  <td>${escape(actionLabel(failure.actionKeyword, failure.actionId))}</td>
                  <td class="message">${escape(failure.message.orEmpty())}</td>
                  <td class="error">${escape(failure.error.orEmpty())}</td>
                </tr>
            """.trimIndent()
        }
        return """
            <h2>Failure Summary</h2>
            <table>
              <thead>
                <tr><th>Stage</th><th>Case</th><th>Phase</th><th>#</th><th>Action</th><th>Message</th><th>Error</th></tr>
              </thead>
              <tbody>
                $rows
              </tbody>
            </table>
        """.trimIndent()
    }

    private fun renderTraceSection(traceArtifacts: List<LocalTraceArtifactData>): String {
        if (traceArtifacts.isEmpty()) {
            return ""
        }
        val rows = traceArtifacts.joinToString(separator = "\n") { artifact ->
            """
                <tr>
                  <td>${escape(artifact.stageId.orEmpty())}</td>
                  <td>${escape(artifact.caseId.orEmpty())}</td>
                  <td>${escape(artifact.phase)}</td>
                  <td>${artifact.index}</td>
                  <td>${escape(artifact.actionId ?: artifact.actionKeyword)}</td>
                  <td>${escape(artifact.timing)}</td>
                  <td><a href="${escape(artifact.href)}">${escape(artifact.captureId)}</a></td>
                </tr>
            """.trimIndent()
        }
        return """
            <h2>Trace Artifacts</h2>
            <table>
              <thead>
                <tr><th>Stage</th><th>Case</th><th>Phase</th><th>#</th><th>Action</th><th>Timing</th><th>Artifact</th></tr>
              </thead>
              <tbody>
                $rows
              </tbody>
            </table>
        """.trimIndent()
    }

    private fun renderActionRow(
        stageId: String,
        caseId: String,
        action: LocalActionReportData,
    ): String {
        return """
            <tr>
              <td>${escape(stageId)}</td>
              <td>${escape(caseId)}</td>
              <td>${escape(action.phase)}</td>
              <td>${action.index}</td>
              <td>${escape(actionLabel(action.actionKeyword, action.actionId))}</td>
              <td><span class="${statusClass(action.status)}">${escape(action.status)}</span></td>
              <td>${action.attempt}</td>
              <td>${action.durationMs?.let { "${it}ms" } ?: ""}</td>
              <td class="message">${escape(action.message.orEmpty())}</td>
              <td class="error">${escape(action.error.orEmpty())}</td>
            </tr>
        """.trimIndent()
    }

    private fun com.soluna.ui.autotest.core.execution.ActionExecutionResult.toReportData(
        index: Int,
        phase: String,
    ): LocalActionReportData {
        return LocalActionReportData(
            index = index + 1,
            phase = phase,
            status = status.name.lowercase(),
            actionId = actionId,
            actionKeyword = actionKeyword,
            actionName = actionName,
            attempt = attempt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = durationMs,
            message = message,
            error = error,
        )
    }

    private fun actionLabel(
        keyword: String?,
        id: String?,
    ): String {
        return listOfNotNull(keyword, id?.let { "#$it" }).joinToString(" ").ifBlank { "-" }
    }

    private fun statusClass(status: String): String {
        return "status status-${escape(status)}"
    }

    private fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}

data class LocalReportData(
    val schemaVersion: String = "1.0",
    val generatedAt: String,
    val runId: String,
    val planId: String,
    val planName: String,
    val status: String,
    val deviceId: String,
    val platform: String,
    val summary: ExecutionReportSummary,
    val failures: List<ExecutionFailureSummary>,
    val traceArtifacts: List<LocalTraceArtifactData>,
    val setupActions: List<LocalActionReportData>,
    val teardownActions: List<LocalActionReportData>,
    val stages: List<LocalStageReportData>,
)

data class LocalStageReportData(
    val stageId: String,
    val status: String,
    val setupActions: List<LocalActionReportData>,
    val teardownActions: List<LocalActionReportData>,
    val cases: List<LocalCaseReportData>,
)

data class LocalCaseReportData(
    val caseId: String,
    val status: String,
    val setupActions: List<LocalActionReportData>,
    val teardownActions: List<LocalActionReportData>,
    val actions: List<LocalActionReportData>,
)

data class LocalActionReportData(
    val index: Int,
    val phase: String,
    val status: String,
    val actionId: String?,
    val actionKeyword: String?,
    val actionName: String?,
    val attempt: Int,
    val startedAt: String?,
    val finishedAt: String?,
    val durationMs: Long?,
    val message: String?,
    val error: String?,
)

data class LocalTraceArtifactData(
    val captureId: String,
    val stageId: String?,
    val caseId: String?,
    val actionId: String?,
    val actionKeyword: String,
    val phase: String,
    val index: Int,
    val attempt: Int,
    val timing: String,
    val href: String,
    val contentType: String,
    val sizeBytes: Long,
    val capturedAt: String,
)
