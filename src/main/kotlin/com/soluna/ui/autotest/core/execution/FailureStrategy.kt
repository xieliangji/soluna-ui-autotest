package com.soluna.ui.autotest.core.execution

import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.CaseDefinition
import com.soluna.ui.autotest.core.model.StageDefinition

interface FailureStrategy {
    fun continueCaseAfterActionFailure(
        context: ExecutionContext,
        stage: StageDefinition,
        case: CaseDefinition,
        action: ActionDefinition,
        result: ActionExecutionResult,
    ): Boolean

    fun continueStageAfterCaseFailure(
        context: ExecutionContext,
        stage: StageDefinition,
        case: CaseDefinition,
        result: CaseExecutionResult,
    ): Boolean

    fun continuePlanAfterStageFailure(
        context: ExecutionContext,
        stage: StageDefinition,
        result: StageExecutionResult,
    ): Boolean
}

object FailFastFailureStrategy : FailureStrategy {
    override fun continueCaseAfterActionFailure(
        context: ExecutionContext,
        stage: StageDefinition,
        case: CaseDefinition,
        action: ActionDefinition,
        result: ActionExecutionResult,
    ): Boolean {
        return false
    }

    override fun continueStageAfterCaseFailure(
        context: ExecutionContext,
        stage: StageDefinition,
        case: CaseDefinition,
        result: CaseExecutionResult,
    ): Boolean {
        return false
    }

    override fun continuePlanAfterStageFailure(
        context: ExecutionContext,
        stage: StageDefinition,
        result: StageExecutionResult,
    ): Boolean {
        return false
    }
}
