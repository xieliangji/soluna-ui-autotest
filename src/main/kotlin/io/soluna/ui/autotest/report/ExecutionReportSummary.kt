package io.soluna.ui.autotest.report

import io.soluna.ui.autotest.core.execution.ActionExecutionResult
import io.soluna.ui.autotest.core.execution.ExecutionStatus
import io.soluna.ui.autotest.core.execution.PlanExecutionResult

data class ExecutionReportSummary(
    val status: String,
    val stageTotal: Int,
    val stagePassed: Int,
    val stageFailed: Int,
    val stageSkipped: Int,
    val caseTotal: Int,
    val casePassed: Int,
    val caseFailed: Int,
    val caseSkipped: Int,
    val actionTotal: Int,
    val actionPassed: Int,
    val actionFailed: Int,
    val actionSkipped: Int,
)

data class ExecutionFailureSummary(
    val stageId: String?,
    val caseId: String?,
    val phase: String,
    val index: Int,
    val actionId: String?,
    val actionKeyword: String?,
    val message: String?,
    val error: String?,
)

object ExecutionReportSummaries {
    fun summary(result: PlanExecutionResult): ExecutionReportSummary {
        val stages = result.stages
        val cases = result.stages.flatMap { it.cases }
        val actions = flattenActions(result).map { it.action }
        return ExecutionReportSummary(
            status = result.status.reportValue(),
            stageTotal = stages.size,
            stagePassed = stages.count { it.status == ExecutionStatus.PASSED },
            stageFailed = stages.count { it.status == ExecutionStatus.FAILED },
            stageSkipped = stages.count { it.status == ExecutionStatus.SKIPPED },
            caseTotal = cases.size,
            casePassed = cases.count { it.status == ExecutionStatus.PASSED },
            caseFailed = cases.count { it.status == ExecutionStatus.FAILED },
            caseSkipped = cases.count { it.status == ExecutionStatus.SKIPPED },
            actionTotal = actions.size,
            actionPassed = actions.count { it.status == ExecutionStatus.PASSED },
            actionFailed = actions.count { it.status == ExecutionStatus.FAILED },
            actionSkipped = actions.count { it.status == ExecutionStatus.SKIPPED },
        )
    }

    fun failures(
        result: PlanExecutionResult,
        limit: Int = Int.MAX_VALUE,
    ): List<ExecutionFailureSummary> {
        return flattenActions(result)
            .filter { it.action.status == ExecutionStatus.FAILED }
            .take(limit.coerceAtLeast(0))
            .map { item ->
                ExecutionFailureSummary(
                    stageId = item.stageId,
                    caseId = item.caseId,
                    phase = item.phase,
                    index = item.index,
                    actionId = item.action.actionId,
                    actionKeyword = item.action.actionKeyword,
                    message = item.action.message,
                    error = item.action.error,
                )
            }
    }

    private fun flattenActions(result: PlanExecutionResult): List<ActionReportItem> {
        return buildList {
            result.setupActions.forEachIndexed { index, action ->
                add(ActionReportItem(null, null, "plan.setup", index + 1, action))
            }
            result.stages.forEach { stage ->
                stage.setupActions.forEachIndexed { index, action ->
                    add(ActionReportItem(stage.stageId, null, "stage.setup", index + 1, action))
                }
                stage.cases.forEach { case ->
                    case.setupActions.forEachIndexed { index, action ->
                        add(ActionReportItem(stage.stageId, case.caseId, "case.setup", index + 1, action))
                    }
                    case.actions.forEachIndexed { index, action ->
                        add(ActionReportItem(stage.stageId, case.caseId, "case.action", index + 1, action))
                    }
                    case.teardownActions.forEachIndexed { index, action ->
                        add(ActionReportItem(stage.stageId, case.caseId, "case.teardown", index + 1, action))
                    }
                }
                stage.teardownActions.forEachIndexed { index, action ->
                    add(ActionReportItem(stage.stageId, null, "stage.teardown", index + 1, action))
                }
            }
            result.teardownActions.forEachIndexed { index, action ->
                add(ActionReportItem(null, null, "plan.teardown", index + 1, action))
            }
        }
    }

    private data class ActionReportItem(
        val stageId: String?,
        val caseId: String?,
        val phase: String,
        val index: Int,
        val action: ActionExecutionResult,
    )
}

private fun ExecutionStatus.reportValue(): String {
    return name.lowercase()
}
