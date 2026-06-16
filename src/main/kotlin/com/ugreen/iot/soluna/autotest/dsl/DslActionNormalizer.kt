package com.ugreen.iot.soluna.autotest.dsl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

class DslActionNormalizer(
    private val objectMapper: ObjectMapper,
    private val keywordRegistry: KeywordRegistry = DefaultKeywordRegistry,
) {
    private val actionArgumentFields = setOf(
        "appId",
        "attr",
        "clearFirst",
        "durationMs",
        "pattern",
        "saveAs",
        "scope",
        "timeoutMs",
        "xRatio",
        "yRatio",
    )

    fun normalizePlan(planNode: JsonNode): JsonNode {
        val normalized = planNode.deepCopy<ObjectNode>()
        normalizeActionList(normalized, "setupActions")
        normalizeActionList(normalized, "teardownActions")
        normalized.path("stages")
            .takeIf { it.isArray }
            ?.forEach { stage ->
                if (stage is ObjectNode) {
                    normalizeActionList(stage, "setupActions")
                    normalizeActionList(stage, "teardownActions")
                    stage.path("cases")
                        .takeIf { it.isArray }
                        ?.forEach { case ->
                            if (case is ObjectNode) {
                                normalizeCaseNode(case)
                            }
                        }
                }
            }
        return normalized
    }

    fun normalizeCase(caseNode: JsonNode): JsonNode {
        return caseNode.deepCopy<ObjectNode>().also { normalizeCaseNode(it) }
    }

    fun normalizeFragmentCatalog(fragmentCatalogNode: JsonNode): JsonNode {
        val normalized = fragmentCatalogNode.deepCopy<ObjectNode>()
        normalized.path("fragments")
            .takeIf { it.isObject }
            ?.fields()
            ?.forEachRemaining { (_, fragment) ->
                if (fragment is ObjectNode) {
                    normalizeActionList(fragment, "actions")
                }
            }
        return normalized
    }

    private fun normalizeCaseNode(caseNode: ObjectNode) {
        normalizeActionList(caseNode, "setupActions")
        normalizeActionList(caseNode, "actions")
        normalizeActionList(caseNode, "teardownActions")
    }

    private fun normalizeActionList(
        parent: ObjectNode,
        fieldName: String,
    ) {
        val actions = parent.get(fieldName)
        if (actions !is ArrayNode) {
            return
        }

        val normalizedActions = objectMapper.createArrayNode()
        actions.forEach { action ->
            normalizedActions.add(normalizeAction(action))
        }
        parent.set<ArrayNode>(fieldName, normalizedActions)
    }

    private fun normalizeAction(action: JsonNode): ObjectNode {
        require(action.isObject) { "Action must be an object" }

        if (action.has("if")) {
            return normalizeIfAction(action)
        }

        val keywordFields = action.fields().asSequence()
            .filter { (fieldName, value) ->
                value.isTextual && keywordRegistry.normalize(fieldName) != null
            }
            .toList()

        require(keywordFields.size == 1) {
            "Action must declare exactly one supported keyword field"
        }

        val (keywordField, idNode) = keywordFields.single()
        val canonicalKeyword = keywordRegistry.normalize(keywordField)
            ?: error("Unsupported action keyword '$keywordField'")

        return objectMapper.createObjectNode().also { normalized ->
            normalized.put("id", idNode.asText())
            normalized.put("keyword", canonicalKeyword)
            copyTextField(action, normalized, from = "desc", to = "name")
            copyField(action, normalized, "element")
            copyField(action, normalized, "target")
            copyField(action, normalized, "resourceId")

            if (action.has("expected")) {
                normalized.set<JsonNode>("value", action.get("expected"))
            } else if (action.has("pattern")) {
                normalized.set<JsonNode>("value", action.get("pattern"))
            } else {
                copyField(action, normalized, "value")
            }

            val wait = action.get("wait")
            if (keywordField != "wait" && wait != null && wait.isObject) {
                normalized.set<JsonNode>("wait", wait)
            }

            val args = objectMapper.createObjectNode()
            actionArgumentFields.forEach { field ->
                copyField(action, args, field)
            }
            if (!args.isEmpty) {
                normalized.set<JsonNode>("args", args)
            }

            copyNormalizedBranch(action, normalized, from = "then", to = "thenActions")
            copyNormalizedBranch(action, normalized, from = "else", to = "elseActions")
        }
    }

    private fun normalizeIfAction(action: JsonNode): ObjectNode {
        val condition = normalizeAction(action.get("if"))
        return objectMapper.createObjectNode().also { normalized ->
            val explicitId = action.get("id")?.takeIf { it.isTextual }?.asText()
            val conditionId = condition.get("id")?.takeIf { it.isTextual }?.asText()
            normalized.put("id", explicitId ?: "if-${conditionId ?: "condition"}")
            normalized.put("keyword", "if")
            copyTextField(action, normalized, from = "desc", to = "name")
            normalized.set<JsonNode>("conditionAction", condition)
            copyNormalizedBranch(action, normalized, from = "then", to = "thenActions")
            copyNormalizedBranch(action, normalized, from = "else", to = "elseActions")
        }
    }

    private fun copyNormalizedBranch(
        source: JsonNode,
        target: ObjectNode,
        from: String,
        to: String,
    ) {
        val branch = source.get(from)
        if (branch !is ArrayNode) {
            return
        }

        val normalizedBranch = objectMapper.createArrayNode()
        branch.forEach { action ->
            normalizedBranch.add(normalizeAction(action))
        }
        target.set<ArrayNode>(to, normalizedBranch)
    }

    private fun copyTextField(
        source: JsonNode,
        target: ObjectNode,
        from: String,
        to: String,
    ) {
        val value = source.get(from) ?: return
        if (value.isTextual) {
            target.put(to, value.asText())
        } else {
            target.set<JsonNode>(to, value)
        }
    }

    private fun copyField(
        source: JsonNode,
        target: ObjectNode,
        fieldName: String,
    ) {
        if (source.has(fieldName)) {
            target.set<JsonNode>(fieldName, source.get(fieldName))
        }
    }
}
