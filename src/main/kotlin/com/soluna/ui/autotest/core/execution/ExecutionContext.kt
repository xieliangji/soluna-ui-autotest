package com.soluna.ui.autotest.core.execution

import com.fasterxml.jackson.databind.JsonNode
import com.soluna.ui.autotest.core.model.PlanDefinition

data class ExecutionContext(
    val runId: String,
    val plan: PlanDefinition,
    val driverSessionId: String? = null,
    val currentStageId: String? = null,
    val currentCaseId: String? = null,
    val variables: ExecutionVariables = ExecutionVariables(),
) {
    fun caseVariableScopeId(): String? {
        val caseId = currentCaseId ?: return null
        return "${currentStageId ?: "_"}:$caseId"
    }
}

class ExecutionVariables {
    private val planVariables: MutableMap<String, JsonNode> = linkedMapOf()
    private val caseVariables: MutableMap<String, MutableMap<String, JsonNode>> = linkedMapOf()

    fun set(
        scope: String,
        name: String,
        value: JsonNode,
        caseId: String? = null,
    ) {
        require(name.isNotBlank()) { "Variable name must not be blank" }
        when (scope.lowercase()) {
            "plan" -> planVariables[name] = value.deepCopy()
            "case" -> {
                val resolvedCaseId = caseId ?: error("Case scoped variable '$name' requires current case id")
                caseVariables.getOrPut(resolvedCaseId) { linkedMapOf() }[name] = value.deepCopy()
            }
            else -> error("Unsupported variable scope '$scope'")
        }
    }

    fun get(
        scope: String,
        name: String,
        caseId: String? = null,
    ): JsonNode? {
        return when (scope.lowercase()) {
            "plan" -> planVariables[name]?.deepCopy()
            "case" -> caseId?.let { caseVariables[it]?.get(name)?.deepCopy() }
            else -> error("Unsupported variable scope '$scope'")
        }
    }
}
