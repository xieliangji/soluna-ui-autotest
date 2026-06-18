package com.soluna.ui.autotest.core.execution

import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.CaseDefinition
import com.soluna.ui.autotest.core.model.StageDefinition

interface ActionTraceCollector {
    fun beforeAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
        attempt: Int,
    )

    fun afterAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
        result: ActionExecutionResult,
    )
}

object NoOpActionTraceCollector : ActionTraceCollector {
    override fun beforeAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
        attempt: Int,
    ) {
        // Trace collection is optional and pluggable.
    }

    override fun afterAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
        result: ActionExecutionResult,
    ) {
        // Trace collection is optional and pluggable.
    }
}
