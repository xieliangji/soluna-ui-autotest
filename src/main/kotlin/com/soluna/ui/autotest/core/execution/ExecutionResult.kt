package com.soluna.ui.autotest.core.execution

enum class ExecutionStatus {
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED,
}

data class PlanExecutionResult(
    val runId: String,
    val planId: String,
    val status: ExecutionStatus,
    val setupActions: List<ActionExecutionResult> = emptyList(),
    val teardownActions: List<ActionExecutionResult> = emptyList(),
    val stages: List<StageExecutionResult>,
)

data class StageExecutionResult(
    val stageId: String,
    val status: ExecutionStatus,
    val setupActions: List<ActionExecutionResult> = emptyList(),
    val teardownActions: List<ActionExecutionResult> = emptyList(),
    val cases: List<CaseExecutionResult>,
)

data class CaseExecutionResult(
    val caseId: String,
    val status: ExecutionStatus,
    val setupActions: List<ActionExecutionResult> = emptyList(),
    val teardownActions: List<ActionExecutionResult> = emptyList(),
    val actions: List<ActionExecutionResult>,
)

data class ActionExecutionResult(
    val status: ExecutionStatus,
    val message: String? = null,
    val error: String? = null,
) {
    companion object {
        fun passed(message: String? = null): ActionExecutionResult {
            return ActionExecutionResult(
                status = ExecutionStatus.PASSED,
                message = message,
            )
        }

        fun failed(error: String): ActionExecutionResult {
            return ActionExecutionResult(
                status = ExecutionStatus.FAILED,
                error = error,
            )
        }
    }
}
