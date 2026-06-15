package com.ugreen.iot.soluna.autotest.core.execution

import java.util.UUID

interface ExecutionEngine {
    fun execute(
        plan: com.ugreen.iot.soluna.autotest.core.model.PlanDefinition,
        request: ExecutionRequest = ExecutionRequest(),
    ): PlanExecutionResult
}

data class ExecutionRequest(
    val runId: String = UUID.randomUUID().toString(),
    val driverSessionId: String? = null,
)
