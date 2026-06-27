package com.soluna.ui.autotest.dsl

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
        "args",
        "assertion",
        "attr",
        "asRoi",
        "clearFirst",
        "color",
        "durationMs",
        "endElementXRatio",
        "endElementYRatio",
        "endXRatio",
        "endYRatio",
        "elementXRatio",
        "elementYRatio",
        "expandBottomRatio",
        "expandLeftRatio",
        "expandRightRatio",
        "expandTopRatio",
        "pattern",
        "recognizer",
        "roi",
        "saveAs",
        "scope",
        "scales",
        "settleMs",
        "source",
        "startElementXRatio",
        "startElementYRatio",
        "startXRatio",
        "startYRatio",
        "targetXRatio",
        "targetYRatio",
        "template",
        "threshold",
        "fullHeight",
        "fullWidth",
        "framesPerSecond",
        "filter",
        "ignoreMissingElement",
        "ignoreMissingElementReason",
        "maxBufferEntries",
        "maxEntries",
        "maxFrames",
        "maxReadBatches",
        "maxSessionBytes",
        "minPixels",
        "minRatio",
        "candidateMaxFrames",
        "candidateStrategy",
        "plugin",
        "readLimit",
        "visualDifferenceThreshold",
        "timeLimitMs",
        "timeoutMs",
        "ttlMs",
        "udid",
        "xRatio",
        "yRatio",
    )

    fun normalizePlan(planNode: JsonNode): JsonNode {
        val normalized = planNode.deepCopy<ObjectNode>()
        normalizeActionList(normalized, "setupActions")
        normalizeActionList(normalized, "caseSetupActions")
        normalizeActionList(normalized, "caseTeardownActions")
        normalizeActionList(normalized, "teardownActions")
        normalized.path("stages")
            .takeIf { it.isArray }
            ?.forEach { stage ->
                if (stage is ObjectNode) {
                    normalizeActionList(stage, "setupActions")
                    normalizeActionList(stage, "caseSetupActions")
                    normalizeActionList(stage, "caseTeardownActions")
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
        normalizeActionList(caseNode, "caseSetupActions")
        normalizeActionList(caseNode, "caseTeardownActions")
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
                isKeywordValue(value) && keywordRegistry.normalize(fieldName) != null
            }
            .toList()

        require(keywordFields.size == 1) {
            "Action must declare exactly one supported keyword field"
        }

        val (keywordField, keywordValue) = keywordFields.single()
        val canonicalKeyword = keywordRegistry.normalize(keywordField)
            ?: error("Unsupported action keyword '$keywordField'")
        val payload = keywordPayload(keywordField, keywordValue, action)
        val normalizedKeyword = if (canonicalKeyword == "tapPosition") "tap" else canonicalKeyword

        return objectMapper.createObjectNode().also { normalized ->
            normalized.put("id", payload.id)
            normalized.put("keyword", normalizedKeyword)
            copyTextField(payload.node, normalized, from = "desc", to = "name")
            copyField(payload.node, normalized, "element")
            copyField(payload.node, normalized, "target")
            copyField(payload.node, normalized, "resourceId")

            if (payload.node.has("expected")) {
                normalized.set<JsonNode>("value", payload.node.get("expected"))
            } else if (payload.node.has("pattern")) {
                normalized.set<JsonNode>("value", payload.node.get("pattern"))
            } else {
                copyField(payload.node, normalized, "value")
            }

            val wait = payload.node.get("wait")
            if (normalizedKeyword != "wait" && wait != null && wait.isObject) {
                normalized.set<JsonNode>("wait", wait)
            }

            val args = objectMapper.createObjectNode()
            if (canonicalKeyword == "tapPosition" && payload.node.has("element")) {
                copyField(payload.node, args, from = "xRatio", to = "elementXRatio")
                copyField(payload.node, args, from = "yRatio", to = "elementYRatio")
                actionArgumentFields
                    .filterNot { it == "xRatio" || it == "yRatio" || it == "elementXRatio" || it == "elementYRatio" }
                    .forEach { field ->
                        copyField(payload.node, args, field)
                    }
            } else {
                actionArgumentFields.forEach { field ->
                    copyField(payload.node, args, field)
                }
            }
            if (!args.isEmpty) {
                normalized.set<JsonNode>("args", args)
            }

            copyNormalizedBranch(action, normalized, from = "then", to = "thenActions")
            copyNormalizedBranch(action, normalized, from = "else", to = "elseActions")
        }
    }

    private fun isKeywordValue(value: JsonNode): Boolean {
        return value.isTextual || value.isObject
    }

    private fun keywordPayload(
        keywordField: String,
        keywordValue: JsonNode,
        action: JsonNode,
    ): KeywordPayload {
        if (keywordValue.isTextual) {
            return KeywordPayload(
                id = keywordValue.asText(),
                node = action,
            )
        }

        require(keywordValue.isObject) {
            "Action keyword '$keywordField' must be a string id or an object payload"
        }

        val id = keywordValue.get("id")
        require(id != null && id.isTextual && id.asText().isNotBlank()) {
            "Nested action keyword '$keywordField' must declare a non-empty id"
        }
        return KeywordPayload(
            id = id.asText(),
            node = keywordValue,
        )
    }

    private data class KeywordPayload(
        val id: String,
        val node: JsonNode,
    )

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

    private fun copyField(
        source: JsonNode,
        target: ObjectNode,
        from: String,
        to: String,
    ) {
        if (source.has(from)) {
            target.set<JsonNode>(to, source.get(from))
        }
    }
}
