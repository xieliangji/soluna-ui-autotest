package com.soluna.ui.autotest.core.execution

import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.CaseDefinition
import com.soluna.ui.autotest.core.model.StageDefinition

data class RetryDecision(
    val shouldRetry: Boolean,
    val delayMs: Long = 0,
    val reason: String? = null,
) {
    init {
        require(delayMs >= 0) { "delayMs must be >= 0" }
    }

    companion object {
        fun retry(
            delayMs: Long = 0,
            reason: String? = null,
        ): RetryDecision {
            return RetryDecision(
                shouldRetry = true,
                delayMs = delayMs,
                reason = reason,
            )
        }

        fun stop(reason: String? = null): RetryDecision {
            return RetryDecision(
                shouldRetry = false,
                reason = reason,
            )
        }
    }
}

data class ActionRetryContext(
    val executionContext: ExecutionContext,
    val stage: StageDefinition,
    val case: CaseDefinition,
    val action: ActionDefinition,
    val attempt: Int,
    val previousResult: ActionExecutionResult,
)

interface RetryStrategy {
    fun nextActionRetry(context: ActionRetryContext): RetryDecision
}

object NoRetryStrategy : RetryStrategy {
    override fun nextActionRetry(context: ActionRetryContext): RetryDecision {
        return RetryDecision.stop("no-retry")
    }
}

class MaxAttemptsRetryStrategy(
    private val maxAttempts: Int,
    private val delayMs: Long = 0,
) : RetryStrategy {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(delayMs >= 0) { "delayMs must be >= 0" }
    }

    override fun nextActionRetry(context: ActionRetryContext): RetryDecision {
        return if (context.previousResult.status == ExecutionStatus.FAILED && context.attempt < maxAttempts) {
            RetryDecision.retry(
                delayMs = delayMs,
                reason = "attempt ${context.attempt} of $maxAttempts failed",
            )
        } else {
            RetryDecision.stop("max-attempts-reached")
        }
    }
}
