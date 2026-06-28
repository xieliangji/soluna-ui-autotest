package io.soluna.ui.autotest.core.execution

import java.util.UUID

interface ExecutionEngine {
    fun execute(
        plan: io.soluna.ui.autotest.core.model.PlanDefinition,
        request: ExecutionRequest = ExecutionRequest(),
    ): PlanExecutionResult
}

data class ExecutionRequest(
    val runId: String = UUID.randomUUID().toString(),
    val driverSessionId: String? = null,
)
