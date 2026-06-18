package com.ugreen.iot.soluna.autotest.core.execution

import com.ugreen.iot.soluna.autotest.core.hook.HookBus
import com.ugreen.iot.soluna.autotest.core.hook.HookEvent
import com.ugreen.iot.soluna.autotest.core.hook.HookEventType
import com.ugreen.iot.soluna.autotest.core.hook.SimpleHookBus
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.CaseDefinition
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.StageDefinition
import java.time.Clock

class LinearExecutionEngine(
    private val actionExecutorRegistry: ActionExecutorRegistry,
    private val hookBus: HookBus = SimpleHookBus(),
    private val failureStrategy: FailureStrategy = FailFastFailureStrategy,
    private val retryStrategy: RetryStrategy = NoRetryStrategy,
    private val actionTraceCollector: ActionTraceCollector = NoOpActionTraceCollector,
    private val sleeper: Sleeper = NoOpSleeper,
    private val clock: Clock = Clock.systemUTC(),
) : ExecutionEngine {
    override fun execute(
        plan: PlanDefinition,
        request: ExecutionRequest,
    ): PlanExecutionResult {
        val context = ExecutionContext(
            runId = request.runId,
            plan = plan,
            driverSessionId = request.driverSessionId,
        )

        publishPlanEvent(HookEventType.PLAN_BEFORE, context, ExecutionStatus.RUNNING)

        val planSetupResults = executeSetupActions(context, null, null, plan.setupActions, "plan.setup")
        val stageResults = mutableListOf<StageExecutionResult>()
        var planStatus = if (planSetupResults.hasFailure()) {
            ExecutionStatus.FAILED
        } else {
            ExecutionStatus.PASSED
        }

        if (planStatus != ExecutionStatus.FAILED) {
            for (stage in plan.stages) {
                val stageResult = executeStage(context, stage)
                stageResults += stageResult

                if (stageResult.status == ExecutionStatus.FAILED) {
                    planStatus = ExecutionStatus.FAILED
                    if (!failureStrategy.continuePlanAfterStageFailure(context, stage, stageResult)) {
                        break
                    }
                }
            }
        }
        val planTeardownResults = executeTeardownActions(context, null, null, plan.teardownActions, "plan.teardown")
        if (planTeardownResults.hasFailure()) {
            planStatus = ExecutionStatus.FAILED
        }

        val result = PlanExecutionResult(
            runId = context.runId,
            planId = plan.id,
            status = planStatus,
            setupActions = planSetupResults,
            teardownActions = planTeardownResults,
            stages = stageResults,
        )

        publishPlanEvent(HookEventType.PLAN_AFTER, context, result.status)
        return result
    }

    private fun executeStage(
        context: ExecutionContext,
        stage: StageDefinition,
    ): StageExecutionResult {
        val stageContext = context.copy(
            currentStageId = stage.id,
            currentCaseId = null,
        )
        publishStageEvent(HookEventType.STAGE_BEFORE, stageContext, stage, ExecutionStatus.RUNNING)

        val stageSetupResults = executeSetupActions(stageContext, stage, null, stage.setupActions, "stage.setup")
        val caseResults = mutableListOf<CaseExecutionResult>()
        var stageStatus = if (stageSetupResults.hasFailure()) {
            ExecutionStatus.FAILED
        } else {
            ExecutionStatus.PASSED
        }

        if (stageStatus != ExecutionStatus.FAILED) {
            for (case in stage.cases) {
                val caseResult = executeCase(stageContext, stage, case)
                caseResults += caseResult

                if (caseResult.status == ExecutionStatus.FAILED) {
                    stageStatus = ExecutionStatus.FAILED
                    if (!failureStrategy.continueStageAfterCaseFailure(context, stage, case, caseResult)) {
                        break
                    }
                }
            }
        }
        val stageTeardownResults = executeTeardownActions(stageContext, stage, null, stage.teardownActions, "stage.teardown")
        if (stageTeardownResults.hasFailure()) {
            stageStatus = ExecutionStatus.FAILED
        }

        val result = StageExecutionResult(
            stageId = stage.id,
            status = stageStatus,
            setupActions = stageSetupResults,
            teardownActions = stageTeardownResults,
            cases = caseResults,
        )
        publishStageEvent(HookEventType.STAGE_AFTER, stageContext, stage, result.status)
        return result
    }

    private fun executeCase(
        context: ExecutionContext,
        stage: StageDefinition,
        case: CaseDefinition,
    ): CaseExecutionResult {
        val caseContext = context.copy(
            currentStageId = stage.id,
            currentCaseId = case.id,
        )
        publishCaseEvent(HookEventType.CASE_BEFORE, caseContext, stage, case, ExecutionStatus.RUNNING)

        val setupResults = executeSetupActions(caseContext, stage, case, case.setupActions, "case.setup")
        val actionResults = mutableListOf<ActionExecutionResult>()
        var caseStatus = if (setupResults.hasFailure()) {
            ExecutionStatus.FAILED
        } else {
            ExecutionStatus.PASSED
        }

        if (caseStatus != ExecutionStatus.FAILED) {
            for ((index, action) in case.actions.withIndex()) {
                val actionResult = executeAction(caseContext, stage, case, action, "case.action", index + 1)
                actionResults += actionResult

                if (actionResult.status == ExecutionStatus.FAILED) {
                    caseStatus = ExecutionStatus.FAILED
                    if (!failureStrategy.continueCaseAfterActionFailure(context, stage, case, action, actionResult)) {
                        break
                    }
                }
            }
        }
        val teardownResults = executeTeardownActions(caseContext, stage, case, case.teardownActions, "case.teardown")
        if (teardownResults.hasFailure()) {
            caseStatus = ExecutionStatus.FAILED
        }

        val result = CaseExecutionResult(
            caseId = case.id,
            status = caseStatus,
            setupActions = setupResults,
            teardownActions = teardownResults,
            actions = actionResults,
        )
        publishCaseEvent(HookEventType.CASE_AFTER, caseContext, stage, case, result.status)
        return result
    }

    private fun executeSetupActions(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        actions: List<ActionDefinition>,
        phase: String,
    ): List<ActionExecutionResult> {
        val results = mutableListOf<ActionExecutionResult>()
        actions.forEachIndexed { index, action ->
            val result = executeAction(context, stage, case, action, phase, index + 1)
            results += result
            if (result.status == ExecutionStatus.FAILED) {
                return results
            }
        }
        return results
    }

    private fun executeTeardownActions(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        actions: List<ActionDefinition>,
        phase: String,
    ): List<ActionExecutionResult> {
        val results = mutableListOf<ActionExecutionResult>()
        actions.forEachIndexed { index, action ->
            val result = executeAction(context, stage, case, action, phase, index + 1)
            results += result
            if (result.status == ExecutionStatus.FAILED) {
                return results
            }
        }
        return results
    }

    private fun executeAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
    ): ActionExecutionResult {
        if (action.keyword == "if") {
            return executeIfAction(context, stage, case, action, phase, index)
        }

        var attempt = 1
        var result: ActionExecutionResult

        while (true) {
            publishActionEvent(HookEventType.ACTION_BEFORE, context, stage, case, action, ExecutionStatus.RUNNING)
            actionTraceCollector.beforeAction(context, stage, case, action, phase, index, attempt)

            result = runCatching {
                actionExecutorRegistry.execute(action, context)
            }.getOrElse { err ->
                ActionExecutionResult.failed(err.message ?: err::class.simpleName ?: "Action execution failed")
            }

            if (result.status != ExecutionStatus.FAILED) {
                break
            }

            val decision = if (stage != null && case != null) {
                retryStrategy.nextActionRetry(
                    ActionRetryContext(
                        executionContext = context,
                        stage = stage,
                        case = case,
                        action = action,
                        attempt = attempt,
                        previousResult = result,
                    ),
                )
            } else {
                RetryDecision(shouldRetry = false)
            }

            if (!decision.shouldRetry) {
                break
            }

            if (decision.delayMs > 0) {
                sleeper.sleep(decision.delayMs)
            }
            attempt += 1
        }

        publishActionEvent(
            type = HookEventType.ACTION_AFTER,
            context = context,
            stage = stage,
            case = case,
            action = action,
            status = result.status,
            error = result.error,
        )
        actionTraceCollector.afterAction(context, stage, case, action, phase, index, result)
        return result
    }

    private fun executeIfAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
    ): ActionExecutionResult {
        publishActionEvent(HookEventType.ACTION_BEFORE, context, stage, case, action, ExecutionStatus.RUNNING)
        actionTraceCollector.beforeAction(context, stage, case, action, phase, index, attempt = 1)

        val condition = action.conditionAction
        val result = if (condition == null) {
            ActionExecutionResult.failed("If action '${action.id ?: action.keyword}' requires a condition action")
        } else {
            val conditionResult = runCatching {
                actionExecutorRegistry.execute(condition, context)
            }.getOrElse { err ->
                ActionExecutionResult.failed(err.message ?: err::class.simpleName ?: "Condition action failed")
            }
            val selectedBranch = if (conditionResult.status == ExecutionStatus.PASSED) {
                action.thenActions
            } else {
                action.elseActions
            }
            val branchResults = executeNestedActions(context, stage, case, selectedBranch, phase)
            if (branchResults.hasFailure()) {
                val branchName = if (conditionResult.status == ExecutionStatus.PASSED) "then" else "else"
                val failedAction = selectedBranch.zip(branchResults)
                    .firstOrNull { (_, branchResult) -> branchResult.status == ExecutionStatus.FAILED }
                val failedActionId = failedAction?.first?.id ?: failedAction?.first?.keyword ?: "unknown"
                val failedReason = failedAction?.second?.error ?: failedAction?.second?.message ?: "branch action failed"
                ActionExecutionResult.failed("if $branchName branch failed at $failedActionId: $failedReason")
            } else {
                val branchName = if (conditionResult.status == ExecutionStatus.PASSED) "then" else "else"
                ActionExecutionResult.passed("if $branchName branch completed")
            }
        }

        publishActionEvent(
            type = HookEventType.ACTION_AFTER,
            context = context,
            stage = stage,
            case = case,
            action = action,
            status = result.status,
            error = result.error,
        )
        actionTraceCollector.afterAction(context, stage, case, action, phase, index, result)
        return result
    }

    private fun executeNestedActions(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        actions: List<ActionDefinition>,
        phase: String,
    ): List<ActionExecutionResult> {
        val results = mutableListOf<ActionExecutionResult>()
        actions.forEachIndexed { index, branchAction ->
            val result = executeAction(context, stage, case, branchAction, phase, index + 1)
            results += result
            if (result.status == ExecutionStatus.FAILED) {
                return results
            }
        }
        return results
    }

    private fun publishPlanEvent(
        type: HookEventType,
        context: ExecutionContext,
        status: ExecutionStatus,
    ) {
        hookBus.publish(
            HookEvent(
                type = type,
                runId = context.runId,
                planId = context.plan.id,
                status = status,
                timestamp = clock.instant(),
            ),
        )
    }

    private fun publishStageEvent(
        type: HookEventType,
        context: ExecutionContext,
        stage: StageDefinition,
        status: ExecutionStatus,
    ) {
        hookBus.publish(
            HookEvent(
                type = type,
                runId = context.runId,
                planId = context.plan.id,
                stageId = stage.id,
                status = status,
                timestamp = clock.instant(),
            ),
        )
    }

    private fun publishCaseEvent(
        type: HookEventType,
        context: ExecutionContext,
        stage: StageDefinition,
        case: CaseDefinition,
        status: ExecutionStatus,
    ) {
        hookBus.publish(
            HookEvent(
                type = type,
                runId = context.runId,
                planId = context.plan.id,
                stageId = stage.id,
                caseId = case.id,
                status = status,
                timestamp = clock.instant(),
            ),
        )
    }

    private fun publishActionEvent(
        type: HookEventType,
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        status: ExecutionStatus,
        error: String? = null,
    ) {
        hookBus.publish(
            HookEvent(
                type = type,
                runId = context.runId,
                planId = context.plan.id,
                stageId = stage?.id,
                caseId = case?.id,
                actionId = action.id,
                actionKeyword = action.keyword,
                status = status,
                timestamp = clock.instant(),
                error = error,
            ),
        )
    }

    private fun List<ActionExecutionResult>.hasFailure(): Boolean {
        return any { it.status == ExecutionStatus.FAILED }
    }
}

interface Sleeper {
    fun sleep(delayMs: Long)
}

object NoOpSleeper : Sleeper {
    override fun sleep(delayMs: Long) {
        // Intentionally no-op for the v0 synchronous skeleton.
    }
}

object ThreadSleeper : Sleeper {
    override fun sleep(delayMs: Long) {
        Thread.sleep(delayMs)
    }
}
