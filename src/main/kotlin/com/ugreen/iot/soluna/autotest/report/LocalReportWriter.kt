package com.ugreen.iot.soluna.autotest.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ugreen.iot.soluna.autotest.runner.PlanRunResult
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
        val traceSection = renderTraceSection(data.traceArtifacts)
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Soluna Report - ${escape(data.runId)}</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 32px; color: #202124; }
                h1 { font-size: 24px; margin: 0 0 16px; }
                .summary { display: grid; grid-template-columns: repeat(2, minmax(180px, 1fr)); gap: 8px 24px; margin-bottom: 24px; }
                .label { color: #5f6368; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #dadce0; padding: 8px 10px; text-align: left; font-size: 14px; }
                th { background: #f8f9fa; }
              </style>
            </head>
            <body>
              <h1>${escape(data.planName)}</h1>
              <div class="summary">
                <div><span class="label">Run</span> ${escape(data.runId)}</div>
                <div><span class="label">Status</span> ${escape(data.status)}</div>
                <div><span class="label">Plan</span> ${escape(data.planId)}</div>
                <div><span class="label">Device</span> ${escape(data.deviceId)} / ${escape(data.platform)}</div>
                <div><span class="label">Generated</span> ${escape(data.generatedAt)}</div>
                <div><span class="label">Data</span> <a href="execution-result.json">execution-result.json</a></div>
                <div><span class="label">Resources</span> <a href="plan-resource-manifest.json">plan-resource-manifest.json</a></div>
              </div>
              <table>
                <thead>
                  <tr><th>Stage</th><th>Case</th><th>Phase</th><th>#</th><th>Status</th><th>Message</th><th>Error</th></tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
              $traceSection
            </body>
            </html>
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
                <tr><th>Stage</th><th>Case</th><th>Phase</th><th>#</th><th>Action</th><th>Timing</th><th>Screenshot</th></tr>
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
              <td>${escape(action.status)}</td>
              <td>${escape(action.message.orEmpty())}</td>
              <td>${escape(action.error.orEmpty())}</td>
            </tr>
        """.trimIndent()
    }

    private fun com.ugreen.iot.soluna.autotest.core.execution.ActionExecutionResult.toReportData(
        index: Int,
        phase: String,
    ): LocalActionReportData {
        return LocalActionReportData(
            index = index + 1,
            phase = phase,
            status = status.name.lowercase(),
            message = message,
            error = error,
        )
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
