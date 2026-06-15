package com.ugreen.iot.soluna.autotest.core.execution

import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.dsl.DefaultKeywordRegistry
import com.ugreen.iot.soluna.autotest.dsl.KeywordRegistry

interface ActionExecutor {
    val keyword: String

    fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult
}

interface ActionExecutorRegistry {
    fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult
}

class DefaultActionExecutorRegistry(
    executors: List<ActionExecutor>,
    private val keywordRegistry: KeywordRegistry = DefaultKeywordRegistry,
) : ActionExecutorRegistry {
    private val executorsByKeyword = executors.associateBy { it.keyword }

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val canonicalKeyword = keywordRegistry.normalize(action.keyword)
            ?: return ActionExecutionResult.failed("Unknown action keyword '${action.keyword}'")

        val executor = executorsByKeyword[canonicalKeyword]
            ?: return ActionExecutionResult.failed("No action executor registered for keyword '$canonicalKeyword'")

        return executor.execute(action, context)
    }
}
