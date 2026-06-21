package com.soluna.ui.autotest.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.soluna.ui.autotest.core.execution.ActionExecutionResult
import com.soluna.ui.autotest.core.execution.PlanExecutionResult
import com.soluna.ui.autotest.runner.PlanRunResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    private val zoneId: ZoneId = ZoneId.systemDefault(),
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
        val executionActions = flattenActions(result.executionResult)
        val data = LocalReportData(
            generatedAt = clock().toString(),
            startedAt = executionActions.mapNotNull { it.startedAt.toInstantOrNull() }.minOrNull()?.toString(),
            finishedAt = executionActions.mapNotNull { it.finishedAt.toInstantOrNull() }.maxOrNull()?.toString(),
            runId = result.executionResult.runId,
            planId = result.executionResult.planId,
            planName = result.plan.name,
            productModel = result.plan.productModel,
            appId = result.plan.app?.id,
            appName = result.plan.app?.name,
            status = result.executionResult.status.name.lowercase(),
            deviceId = result.deviceConfig.id,
            deviceName = result.deviceConfig.device.name
                ?.takeIf { it.isNotBlank() }
                ?: result.deviceConfig.device.udid,
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
                val stageDefinition = result.plan.stages.firstOrNull { it.id == stage.stageId }
                LocalStageReportData(
                    stageId = stage.stageId,
                    stageName = stageDefinition?.name ?: stage.stageId,
                    status = stage.status.name.lowercase(),
                    setupActions = stage.setupActions.mapIndexed { index, action ->
                        action.toReportData(index, "stage.setup")
                    },
                    teardownActions = stage.teardownActions.mapIndexed { index, action ->
                        action.toReportData(index, "stage.teardown")
                    },
                    cases = stage.cases.map { case ->
                        val caseDefinition = stageDefinition?.cases?.firstOrNull { it.id == case.caseId }
                        LocalCaseReportData(
                            caseId = case.caseId,
                            caseName = caseDefinition?.name ?: case.caseId,
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
        val caseRows = renderCaseOverviewRows(data)
        val caseDialogs = renderCaseActionDialogs(data)
        val failureSection = renderFailureSection(data.failures)
        val traceSection = renderTraceSection(data.traceArtifacts)
        val casePassRate = percentage(data.summary.casePassed, data.summary.caseTotal)
        val actionPassRate = percentage(data.summary.actionPassed, data.summary.actionTotal)
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <title>App UI自动化测试 - ${escape(data.runId)}</title>
              <style>
                :root {
                  color-scheme: light;
                  --bg: #f4f7f6;
                  --surface: #ffffff;
                  --line: #dbe5df;
                  --text: #17201c;
                  --muted: #65736d;
                  --accent: #0f7a55;
                  --accent-soft: #dff3eb;
                  --danger: #b3261e;
                  --danger-soft: #fde8e7;
                  --warn: #8a5a00;
                  --warn-soft: #fff3cf;
                }
                * { box-sizing: border-box; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                  margin: 0;
                  color: var(--text);
                  background: var(--bg);
                }
                body.modal-open { overflow: hidden; }
                main { max-width: 1180px; margin: 0 auto; padding: 28px 28px 44px; }
                h1, h2, h3, p { margin-top: 0; }
                h1 { font-size: 34px; line-height: 1.18; margin-bottom: 10px; letter-spacing: 0; }
                h2 { font-size: 18px; margin: 0 0 14px; letter-spacing: 0; }
                h3 { font-size: 15px; margin-bottom: 10px; }
                a { color: #075f8f; text-decoration: none; }
                a:hover { text-decoration: underline; }
                code { background: #eef3f0; border-radius: 4px; padding: 1px 5px; }
                .hero {
                  background: var(--surface);
                  border: 1px solid var(--line);
                  border-radius: 8px;
                  padding: 28px 30px;
                  box-shadow: 0 12px 32px rgba(31, 47, 42, 0.08);
                }
                .eyebrow { color: var(--accent); font-size: 15px; font-weight: 750; margin-bottom: 8px; }
                .hero-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px 20px; margin-top: 22px; }
                .fact { min-width: 0; }
                .fact-label { display: block; color: var(--muted); font-size: 12px; margin-bottom: 4px; }
                .fact-value { font-size: 14px; font-weight: 650; overflow-wrap: anywhere; }
                .status { display: inline-flex; align-items: center; min-height: 24px; padding: 3px 10px; border-radius: 999px; font-size: 12px; font-weight: 750; }
                .status-passed { background: var(--accent-soft); color: #096941; }
                .status-failed { background: var(--danger-soft); color: var(--danger); }
                .status-skipped { background: #eceff1; color: #52605a; }
                .status-running { background: #e2eefb; color: #075f8f; }
                .summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 20px 0; }
                .metric { background: var(--surface); border: 1px solid var(--line); padding: 16px; border-radius: 8px; min-height: 112px; }
                .metric .label { color: var(--muted); font-size: 13px; }
                .metric .value { display: block; font-size: 26px; font-weight: 800; margin: 8px 0 4px; letter-spacing: 0; }
                .metric .note { color: var(--muted); font-size: 12px; }
                .panel { background: var(--surface); border: 1px solid var(--line); border-radius: 8px; padding: 18px; margin-top: 16px; }
                .resource-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
                .resource-item {
                  border: 1px solid var(--line);
                  border-radius: 8px;
                  padding: 14px 16px;
                  background: #fbfdfc;
                  min-width: 0;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 14px;
                }
                .resource-label { color: var(--muted); font-size: 13px; white-space: nowrap; }
                .resource-item a { display: block; font-size: 16px; font-weight: 750; overflow-wrap: anywhere; text-align: right; }
                .bar { height: 10px; background: #e8efeb; border-radius: 999px; overflow: hidden; margin: 10px 0 12px; }
                .bar span { display: block; height: 100%; background: var(--accent); border-radius: inherit; }
                .progress-line { color: var(--muted); font-size: 13px; margin-bottom: 14px; }
                .overview-details { padding: 0; }
                .overview-details > summary {
                  list-style: none;
                  cursor: pointer;
                  padding: 18px;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 12px;
                }
                .overview-details > summary::-webkit-details-marker { display: none; }
                .overview-details > summary h2 { margin: 0; }
                .overview-details > summary::after {
                  content: "收起";
                  color: var(--muted);
                  font-size: 13px;
                }
                .overview-details:not([open]) > summary::after { content: "展开"; }
                .overview-body { padding: 0 18px 18px; }
                table { border-collapse: separate; border-spacing: 0; width: 100%; overflow: hidden; border: 1px solid var(--line); border-radius: 8px; }
                th, td { border-bottom: 1px solid var(--line); padding: 9px 11px; text-align: left; font-size: 13px; vertical-align: top; }
                th { background: #edf4f0; color: #44524c; font-weight: 750; }
                tr:last-child td { border-bottom: 0; }
                tbody tr:nth-child(even) { background: #fbfdfc; }
                .case-row { cursor: pointer; }
                .case-row:hover { background: #f1f8f4; }
                .case-row:focus { outline: 2px solid #7bb99d; outline-offset: -2px; }
                .numeric-cell { text-align: center; vertical-align: middle; white-space: nowrap; }
                .detail-cell { text-align: center; vertical-align: middle; }
                .detail-link {
                  border: 0;
                  background: transparent;
                  color: #096941;
                  padding: 0;
                  font-weight: 750;
                  cursor: pointer;
                  white-space: nowrap;
                  text-decoration: underline;
                  text-underline-offset: 3px;
                }
                .detail-link:hover { color: var(--accent); }
                .empty { color: var(--muted); background: #f6f9f7; border: 1px dashed var(--line); border-radius: 8px; padding: 14px; }
                .error { color: var(--danger); white-space: pre-wrap; }
                .message { color: #3c4944; white-space: pre-wrap; }
                dialog {
                  width: min(1040px, calc(100vw - 32px));
                  height: min(760px, calc(100vh - 48px));
                  max-height: calc(100vh - 48px);
                  border: 1px solid var(--line);
                  border-radius: 8px;
                  padding: 0;
                  box-shadow: 0 24px 60px rgba(31, 47, 42, 0.24);
                  overscroll-behavior: contain;
                }
                dialog::backdrop { background: rgba(20, 32, 28, 0.34); }
                .modal-frame { display: flex; flex-direction: column; height: 100%; max-height: inherit; overflow: hidden; }
                .modal-header {
                  flex: 0 0 auto;
                  display: flex;
                  align-items: flex-start;
                  justify-content: space-between;
                  gap: 16px;
                  padding: 18px 20px;
                  border-bottom: 1px solid var(--line);
                  background: var(--surface);
                }
                .modal-title { margin: 0; font-size: 18px; }
                .modal-subtitle { margin-top: 6px; color: var(--muted); font-size: 13px; }
                .modal-body { flex: 1 1 auto; min-height: 0; padding: 18px 20px 20px; overflow: auto; overscroll-behavior: contain; }
                .modal-action-table th,
                .modal-action-table td {
                  vertical-align: middle;
                }
                .modal-action-table th:nth-child(4),
                .modal-action-table td:nth-child(4) {
                  width: 1%;
                  min-width: 68px;
                  text-align: center;
                  white-space: nowrap;
                }
                .modal-action-table .status {
                  width: max-content;
                  min-width: max-content;
                  white-space: nowrap;
                }
                .close-button {
                  flex: 0 0 auto;
                  width: 34px;
                  height: 34px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  border: 1px solid transparent;
                  background: transparent;
                  border-radius: 6px;
                  padding: 0;
                  color: var(--muted);
                  cursor: pointer;
                }
                .close-button:hover { color: var(--text); background: #eef3f0; }
                .close-button:focus-visible { outline: 2px solid #0b63ce; outline-offset: 2px; }
                .close-button svg { width: 20px; height: 20px; stroke-width: 2.4; }
                @media (max-width: 860px) {
                  main { padding: 18px 14px 32px; }
                  h1 { font-size: 28px; }
                  .hero-grid, .summary, .resource-grid { grid-template-columns: 1fr; }
                  table { display: block; overflow-x: auto; }
                  .modal-action-table { display: table; min-width: 840px; }
                }
              </style>
            </head>
            <body>
              <main>
                <section class="hero">
                  <div class="eyebrow">App UI自动化测试</div>
                  <h1>${escape(data.productModel)} UI 自动化测试</h1>
                  <div class="hero-grid">
                    <div class="fact"><span class="fact-label">执行状态</span><span class="${statusClass(data.status)}">${escape(statusText(data.status))}</span></div>
                    <div class="fact"><span class="fact-label">应用名称</span><span class="fact-value">${escape(data.appName ?: data.productModel)}</span></div>
                    <div class="fact"><span class="fact-label">应用标识</span><span class="fact-value">${escape(data.appId ?: "-")}</span></div>
                    <div class="fact"><span class="fact-label">运行编号</span><span class="fact-value">${escape(data.runId)}</span></div>
                    <div class="fact"><span class="fact-label">计划编号</span><span class="fact-value">${escape(data.planId)}</span></div>
                    <div class="fact"><span class="fact-label">设备编号</span><span class="fact-value">${escape(data.deviceId)}</span></div>
                    <div class="fact"><span class="fact-label">设备名称</span><span class="fact-value">${escape(data.deviceName)}</span></div>
                    <div class="fact"><span class="fact-label">平台类型</span><span class="fact-value">${escape(data.platform)}</span></div>
                    <div class="fact"><span class="fact-label">开始时间</span><span class="fact-value">${escape(formatDateTime(data.startedAt))}</span></div>
                    <div class="fact"><span class="fact-label">结束时间</span><span class="fact-value">${escape(formatDateTime(data.finishedAt))}</span></div>
                  </div>
                </section>

                <section class="panel">
                  <h2>报告资源</h2>
                  <div class="resource-grid">
                    <div class="resource-item">
                      <div class="resource-label">结果数据</div>
                      <a href="execution-result.json">execution-result.json</a>
                    </div>
                    <div class="resource-item">
                      <div class="resource-label">资源清单</div>
                      <a href="plan-resource-manifest.json">plan-resource-manifest.json</a>
                    </div>
                  </div>
                </section>

                <section class="summary">
                  <div class="metric"><span class="label">阶段通过</span><span class="value">${data.summary.stagePassed}/${data.summary.stageTotal}</span><span class="note">${data.summary.stageFailed} 失败，${data.summary.stageSkipped} 跳过</span></div>
                  <div class="metric"><span class="label">用例通过</span><span class="value">${data.summary.casePassed}/${data.summary.caseTotal}</span><span class="note">通过率 ${casePassRate}%</span></div>
                  <div class="metric"><span class="label">动作通过</span><span class="value">${data.summary.actionPassed}/${data.summary.actionTotal}</span><span class="note">通过率 ${actionPassRate}%</span></div>
                  <div class="metric"><span class="label">追踪资源</span><span class="value">${data.traceArtifacts.size}</span><span class="note">失败诊断截图和源码</span></div>
                </section>

                <details class="panel overview-details" open>
                  <summary><h2>执行概览</h2></summary>
                  <div class="overview-body">
                    <div class="bar"><span style="width: ${casePassRate}%"></span></div>
                    <div class="progress-line">用例 ${data.summary.casePassed}/${data.summary.caseTotal} 通过，${data.summary.caseFailed} 失败，${data.summary.caseSkipped} 跳过</div>
                    <table>
                      <thead>
                        <tr><th>阶段</th><th>用例</th><th>状态</th><th>动作</th><th>耗时</th><th>失败原因</th><th>操作</th></tr>
                      </thead>
                      <tbody>
                        $caseRows
                      </tbody>
                    </table>
                  </div>
                </details>

                $failureSection
                $traceSection

                $caseDialogs
              </main>
              <script>
                document.querySelectorAll('[data-dialog-target]').forEach((trigger) => {
                  const openDialog = () => {
                    const target = document.getElementById(trigger.dataset.dialogTarget);
                    if (target) {
                      target.showModal();
                      document.body.classList.add('modal-open');
                    }
                  };
                  trigger.addEventListener('click', (event) => {
                    event.stopPropagation();
                    openDialog();
                  });
                  trigger.addEventListener('keydown', (event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      openDialog();
                    }
                  });
                });
                document.querySelectorAll('dialog[data-case-dialog]').forEach((dialog) => {
                  dialog.addEventListener('close', () => {
                    if (!document.querySelector('dialog[data-case-dialog][open]')) {
                      document.body.classList.remove('modal-open');
                    }
                  });
                  dialog.addEventListener('click', (event) => {
                    if (event.target === dialog) dialog.close();
                  });
                  dialog.querySelectorAll('[data-close-dialog]').forEach((button) => {
                    button.addEventListener('click', () => dialog.close());
                  });
                });
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderCaseOverviewRows(data: LocalReportData): String {
        val rows = data.stages.flatMap { stage ->
            stage.cases.map { case ->
                val actions = case.allActions()
                val failedAction = actions.firstOrNull { it.status == "failed" }
                val failure = data.failures.firstOrNull {
                    it.stageId == stage.stageId && it.caseId == case.caseId
                }
                val duration = actions.mapNotNull { it.durationMs }.sum().takeIf { actions.any { action -> action.durationMs != null } }
                val dialogId = dialogId(stage.stageId, case.caseId)
                """
                    <tr class="case-row" data-dialog-target="${escape(dialogId)}" tabindex="0" role="button" aria-label="查看 ${escape(case.caseName)} 执行明细">
                      <td><div>${escape(stage.stageName)}</div><code>${escape(stage.stageId)}</code></td>
                      <td><div>${escape(case.caseName)}</div><code>${escape(case.caseId)}</code></td>
                      <td><span class="${statusClass(case.status)}">${escape(statusText(case.status))}</span></td>
                      <td class="numeric-cell">${actions.count { it.status == "passed" }}/${actions.size}</td>
                      <td class="numeric-cell">${escape(formatDuration(duration))}</td>
                      <td class="error">${escape(failure?.error ?: failedAction?.error ?: "")}</td>
                      <td class="detail-cell">
                        <button class="detail-link" type="button" data-dialog-target="${escape(dialogId)}">动作明细</button>
                      </td>
                    </tr>
                """.trimIndent()
            }
        }
        if (rows.isEmpty()) {
            return """<tr><td colspan="7" class="message">无用例</td></tr>"""
        }
        return rows.joinToString(separator = "\n")
    }

    private fun renderCaseActionDialogs(data: LocalReportData): String {
        return data.stages.flatMap { stage ->
            stage.cases.map { case ->
                val actions = case.allActions()
                val rows = if (actions.isEmpty()) {
                    """<tr><td colspan="8" class="message">无动作</td></tr>"""
                } else {
                    actions.joinToString(separator = "\n") { action ->
                        renderActionRow(action)
                    }
                }
                """
                    <dialog id="${escape(dialogId(stage.stageId, case.caseId))}" data-case-dialog>
                      <div class="modal-frame">
                        <div class="modal-header">
                          <div>
                            <h2 class="modal-title">${escape(case.caseName)}</h2>
                            <div class="modal-subtitle">${escape(stage.stageName)} / ${escape(case.caseId)}</div>
                          </div>
                          <button class="close-button" type="button" data-close-dialog aria-label="关闭">
                            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M18 6 6 18"></path>
                              <path d="m6 6 12 12"></path>
                            </svg>
                          </button>
                        </div>
                        <div class="modal-body">
                          <table class="modal-action-table">
                            <thead>
                              <tr><th>环节</th><th>#</th><th>动作</th><th>状态</th><th>重试</th><th>耗时</th><th>消息</th><th>错误</th></tr>
                            </thead>
                            <tbody>
                              $rows
                            </tbody>
                          </table>
                        </div>
                      </div>
                    </dialog>
                """.trimIndent()
            }
        }.joinToString(separator = "\n")
    }

    private fun renderFailureSection(failures: List<ExecutionFailureSummary>): String {
        if (failures.isEmpty()) {
            return """<section class="panel"><h2>失败摘要</h2><div class="empty">当前执行没有失败动作。</div></section>"""
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
            <section class="panel">
              <h2>失败摘要</h2>
              <table>
                <thead>
                  <tr><th>阶段</th><th>用例</th><th>环节</th><th>#</th><th>动作</th><th>消息</th><th>错误</th></tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
            </section>
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
            <section class="panel">
              <h2>追踪资源</h2>
              <table>
                <thead>
                  <tr><th>阶段</th><th>用例</th><th>环节</th><th>#</th><th>动作</th><th>时机</th><th>资源</th></tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
            </section>
        """.trimIndent()
    }

    private fun renderActionRow(action: LocalActionReportData): String {
        return """
            <tr>
              <td>${escape(action.phase)}</td>
              <td>${action.index}</td>
              <td>${escape(actionLabel(action.actionKeyword, action.actionId))}</td>
              <td><span class="${statusClass(action.status)}">${escape(statusText(action.status))}</span></td>
              <td>${retryCount(action.attempt)}</td>
              <td>${escape(formatDuration(action.durationMs))}</td>
              <td class="message">${escape(action.message.orEmpty())}</td>
              <td class="error">${escape(action.error.orEmpty())}</td>
            </tr>
        """.trimIndent()
    }

    private fun LocalCaseReportData.allActions(): List<LocalActionReportData> {
        return setupActions + actions + teardownActions
    }

    private fun flattenActions(result: PlanExecutionResult): List<ActionExecutionResult> {
        return buildList {
            addAll(result.setupActions)
            result.stages.forEach { stage ->
                addAll(stage.setupActions)
                stage.cases.forEach { case ->
                    addAll(case.setupActions)
                    addAll(case.actions)
                    addAll(case.teardownActions)
                }
                addAll(stage.teardownActions)
            }
            addAll(result.teardownActions)
        }
    }

    private fun String?.toInstantOrNull(): Instant? {
        val value = this?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun formatDateTime(value: String?): String {
        val instant = value.toInstantOrNull() ?: return value?.takeIf { it.isNotBlank() } ?: "-"
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(zoneId)
            .format(instant)
    }

    private fun dialogId(
        stageId: String,
        caseId: String,
    ): String {
        return "case-detail-${stageId.toHtmlIdPart()}-${caseId.toHtmlIdPart()}"
    }

    private fun String.toHtmlIdPart(): String {
        return replace(Regex("[^A-Za-z0-9_-]"), "-").ifBlank { "item" }
    }

    private fun percentage(
        passed: Int,
        total: Int,
    ): Int {
        return if (total <= 0) {
            0
        } else {
            ((passed.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        }
    }

    private fun formatDuration(durationMs: Long?): String {
        val value = durationMs ?: return "-"
        return when {
            value < 1_000 -> "${value}ms"
            value < 60_000 -> String.format(Locale.ROOT, "%.1fs", value / 1_000.0)
            else -> {
                val minutes = value / 60_000
                val seconds = (value % 60_000) / 1_000
                "${minutes}m ${seconds}s"
            }
        }
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

    private fun statusText(status: String): String {
        return when (status) {
            "passed" -> "通过"
            "failed" -> "失败"
            "skipped" -> "跳过"
            "running" -> "运行中"
            else -> status
        }
    }

    private fun retryCount(attempt: Int): Int {
        return (attempt - 1).coerceAtLeast(0)
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
    val startedAt: String?,
    val finishedAt: String?,
    val runId: String,
    val planId: String,
    val planName: String,
    val productModel: String,
    val appId: String?,
    val appName: String?,
    val status: String,
    val deviceId: String,
    val deviceName: String,
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
    val stageName: String,
    val status: String,
    val setupActions: List<LocalActionReportData>,
    val teardownActions: List<LocalActionReportData>,
    val cases: List<LocalCaseReportData>,
)

data class LocalCaseReportData(
    val caseId: String,
    val caseName: String,
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
