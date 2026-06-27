package com.soluna.ui.autotest.dsl

import com.fasterxml.jackson.databind.JsonNode

class DslPolicyValidator(
    private val keywordRegistry: KeywordRegistry = DefaultKeywordRegistry,
    private val policyConfig: DslPolicyConfig = DslPolicyConfig(),
) {
    private val logicControlKeys = setOf(
        "if",
        "else",
        "for",
        "while",
        "loop",
        "repeat",
        "switch",
        "branch",
        "condition",
    )

    private val parameterReference = Regex("""\$\{[^}]+}""")
    private val coordinateAttributeExpression = Regex("""(?i)@(x|y|width|height)\b""")
    private val actionFields = setOf(
        "appId",
        "args",
        "assertion",
        "asRoi",
        "candidateMaxFrames",
        "candidateStrategy",
        "clearFirst",
        "color",
        "desc",
        "durationMs",
        "endElementXRatio",
        "endElementYRatio",
        "endXRatio",
        "endYRatio",
        "element",
        "elementXRatio",
        "elementYRatio",
        "expandBottomRatio",
        "expandLeftRatio",
        "expandRightRatio",
        "expandTopRatio",
        "expected",
        "framesPerSecond",
        "fullHeight",
        "fullWidth",
        "filter",
        "attr",
        "id",
        "ignoreMissingElement",
        "ignoreMissingElementReason",
        "maxBufferEntries",
        "maxEntries",
        "maxFrames",
        "maxReadBatches",
        "maxSessionBytes",
        "minPixels",
        "minRatio",
        "pattern",
        "plugin",
        "readLimit",
        "recognizer",
        "resourceId",
        "roi",
        "saveAs",
        "scope",
        "settleMs",
        "source",
        "scales",
        "startElementXRatio",
        "startElementYRatio",
        "startXRatio",
        "startYRatio",
        "targetXRatio",
        "targetYRatio",
        "template",
        "threshold",
        "target",
        "timeLimitMs",
        "timeoutMs",
        "ttlMs",
        "udid",
        "value",
        "visualDifferenceThreshold",
        "wait",
        "xRatio",
        "yRatio",
    )
    private val predefinedTapMissingElementReasons = setOf(
        "optionalFirmwareUpgradePrompt",
    )
    private val textStrategies = setOf("text", "文本", "label", "name")
    private val textAttributeSelector = """(?:@?(?:text|label|name|value)|string\(\s*@(?:text|label|name|value)\s*\))"""
    private val expressionStrategies = setOf(
        "xpath",
        "predicate",
        "iospredicate",
        "ios predicate",
        "classchain",
        "class chain",
        "androiduiautomator",
        "android uiautomator",
    )

    private val exactTextExpression = Regex(
        pattern = """(?i)$textAttributeSelector\s*(==|=)\s*['"][^'$"][^'"]*['"]""",
    )

    private val textAttributeComparisonExpression = Regex(
        pattern = """(?i)$textAttributeSelector\s*(==|=)\s*['"]""",
    )

    private val containsTextExpression = Regex(
        pattern = """(?i)contains\s*\(\s*$textAttributeSelector\s*,\s*['"][^'$"][^'"]*['"]\s*\)""",
    )

    private val containsTextAttributeExpression = Regex(
        pattern = """(?i)contains\s*\(\s*$textAttributeSelector\s*,\s*['"]""",
    )

    private val startsWithTextExpression = Regex(
        pattern = """(?i)starts-with\s*\(\s*$textAttributeSelector\s*,\s*['"][^'$"][^'"]*['"]\s*\)""",
    )

    private val startsWithTextAttributeExpression = Regex(
        pattern = """(?i)starts-with\s*\(\s*$textAttributeSelector\s*,\s*['"]""",
    )

    private val uiAutomatorTextExpression = Regex(
        pattern = """(?i)\.text(?:contains|matches)?\s*\(\s*['"][^'$"][^'"]*['"]\s*\)""",
    )

    private val uiAutomatorTextAttributeExpression = Regex(
        pattern = """(?i)\.text(?:contains|matches)?\s*\(\s*['"]""",
    )

    private val predicateTextExpression = Regex(
        pattern = """(?i)(label|name|value|text)\s+(CONTAINS|BEGINSWITH|ENDSWITH|MATCHES)(?:\[[cd]]+)?\s*['"][^'$"][^'"]*['"]""",
    )

    private val predicateTextAttributeExpression = Regex(
        pattern = """(?i)(label|name|value|text)\s+(CONTAINS|BEGINSWITH|ENDSWITH|MATCHES)(?:\[[cd]]+)?\s*['"]""",
    )

    private val stateValueComparisonExpression = Regex(
        pattern = """(?i)^\s*@?value\s*(==|=)\s*['"](?:0|1|true|false)['"]\s*$""",
    )

    private val exactTextLiteralExpression = Regex(
        pattern = """(?i)$textAttributeSelector\s*(?:==|=)\s*['"]([^'$"][^'"]*)['"]""",
    )

    private val containsTextLiteralExpression = Regex(
        pattern = """(?i)contains\s*\(\s*$textAttributeSelector\s*,\s*['"]([^'$"][^'"]*)['"]\s*\)""",
    )

    private val startsWithTextLiteralExpression = Regex(
        pattern = """(?i)starts-with\s*\(\s*$textAttributeSelector\s*,\s*['"]([^'$"][^'"]*)['"]\s*\)""",
    )

    private val uiAutomatorTextLiteralExpression = Regex(
        pattern = """(?i)\.text(?:contains|matches)?\s*\(\s*['"]([^'$"][^'"]*)['"]\s*\)""",
    )

    private val predicateTextLiteralExpression = Regex(
        pattern = """(?i)(?:label|name|value|text)\s+(?:CONTAINS|BEGINSWITH|ENDSWITH|MATCHES)(?:\[[cd]]+)?\s*['"]([^'$"][^'"]*)['"]""",
    )

    fun validatePlan(planNode: JsonNode): List<DslViolation> {
        val violations = mutableListOf<DslViolation>()
        validateActionList(planNode.path("setupActions"), "$.setupActions", violations)
        validateActionList(planNode.path("caseSetupActions"), "$.caseSetupActions", violations)
        validateActionList(planNode.path("caseTeardownActions"), "$.caseTeardownActions", violations)
        validateActionList(planNode.path("teardownActions"), "$.teardownActions", violations)
        validateStages(planNode, violations)
        validateLocators(planNode, "$", violations)
        return violations
    }

    fun validateCase(caseNode: JsonNode): List<DslViolation> {
        val violations = mutableListOf<DslViolation>()
        validateCaseNode(caseNode, "$", violations)
        validateLocators(caseNode, "$", violations)
        return violations
    }

    fun validateElementCatalog(elementCatalogNode: JsonNode): List<DslViolation> {
        val violations = mutableListOf<DslViolation>()
        val elements = elementCatalogNode.path("elements")
        if (elements.isObject) {
            elements.fields().forEachRemaining { (name, element) ->
                if (element.has("strategy") || element.has("value")) {
                    validateLocator(
                        locator = element,
                        path = "$.elements.$name",
                        violations = violations,
                        inheritedParameterizedTextReason = null,
                        inheritedHardcodedTextReason = null,
                    )
                }
                listOf("android", "ios").forEach { platform ->
                    if (element.has(platform)) {
                        validateLocator(
                            locator = element.get(platform),
                            path = "$.elements.$name.$platform",
                            violations = violations,
                            inheritedParameterizedTextReason = element.parameterizedTextReason(),
                            inheritedHardcodedTextReason = element.hardcodedTextReason(),
                        )
                    }
                }
            }
        }
        return violations
    }

    fun validateFragmentCatalog(fragmentCatalogNode: JsonNode): List<DslViolation> {
        val violations = mutableListOf<DslViolation>()
        val fragments = fragmentCatalogNode.path("fragments")
        if (!fragments.isObject) {
            return violations
        }

        fragments.fields().forEachRemaining { (fragmentId, fragment) ->
            val actions = fragment.path("actions")
            if (!actions.isArray) {
                return@forEachRemaining
            }
            validateFragmentActionList(actions, "$.fragments.$fragmentId.actions", violations)
        }
        return violations
    }

    private fun validateFragmentActionList(
        actions: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        actions.forEachIndexed { actionIndex, action ->
            val actionPath = "$path[$actionIndex]"
            if (action.has("if")) {
                validateKeyword(action.get("if"), "$actionPath.if", violations)
                validateLocators(action.get("if"), "$actionPath.if", violations)
            } else {
                validateKeyword(action, actionPath, violations)
                validateLocators(action, actionPath, violations)
            }
            listOf("then", "else").forEach { branch ->
                val branchActions = action.path(branch)
                if (branchActions.isArray) {
                    validateFragmentActionList(branchActions, "$actionPath.$branch", violations)
                }
            }
        }
    }

    private fun validateStages(
        planNode: JsonNode,
        violations: MutableList<DslViolation>,
    ) {
        val stages = planNode.path("stages")
        if (!stages.isArray) {
            return
        }

        stages.forEachIndexed { stageIndex, stage ->
            validateActionList(stage.path("setupActions"), "$.stages[$stageIndex].setupActions", violations)
            validateActionList(stage.path("caseSetupActions"), "$.stages[$stageIndex].caseSetupActions", violations)
            validateActionList(stage.path("caseTeardownActions"), "$.stages[$stageIndex].caseTeardownActions", violations)
            validateActionList(stage.path("teardownActions"), "$.stages[$stageIndex].teardownActions", violations)

            val cases = stage.path("cases")
            if (!cases.isArray) {
                return@forEachIndexed
            }

            cases.forEachIndexed { caseIndex, case ->
                validateCaseNode(case, "$.stages[$stageIndex].cases[$caseIndex]", violations)
            }
        }
    }

    private fun validateCaseNode(
        case: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        validateNoLogicKeys(case, path, violations)

        validateActionList(case.path("setupActions"), "$path.setupActions", violations)
        validateActionList(case.path("caseSetupActions"), "$path.caseSetupActions", violations)
        validateActionList(case.path("caseTeardownActions"), "$path.caseTeardownActions", violations)
        validateActionList(case.path("actions"), "$path.actions", violations)
        validateActionList(case.path("teardownActions"), "$path.teardownActions", violations)
    }

    private fun validateActionList(
        actions: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        if (!actions.isArray) {
            return
        }

        actions.forEachIndexed { actionIndex, action ->
            val actionPath = "$path[$actionIndex]"
            validateNoLogicKeys(action, actionPath, violations)
            validateKeyword(action, actionPath, violations)
        }
    }

    private fun validateNoLogicKeys(
        node: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        if (!node.isObject) {
            return
        }

        val illegalKeys = node.fieldNames().asSequence()
            .filter { it in logicControlKeys }
            .toList()

        illegalKeys.forEach { key ->
            violations += DslViolation(
                path = "$path.$key",
                message = "Case DSL is linear; logic control key '$key' is not allowed here",
            )
        }
    }

    private fun validateKeyword(
        action: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        if (!action.isObject) {
            return
        }

        val keywordFields = action.fields().asSequence()
            .filter { (fieldName, value) ->
                isKeywordValue(value) && keywordRegistry.normalize(fieldName) != null
            }
            .toList()

        if (keywordFields.size > 1) {
            violations += DslViolation(
                path = path,
                message = "Action must declare exactly one keyword field",
            )
            return
        }

        if (keywordFields.size == 1) {
            val (keywordField, keywordValue) = keywordFields.single()
            if (keywordValue.isObject) {
                validateNestedActionPayload(keywordField, keywordValue, "$path.$keywordField", violations)
            }
            return
        }

        val unknownKeyword = action.fields().asSequence()
            .map { it.key }
            .firstOrNull { fieldName ->
                fieldName !in actionFields && keywordRegistry.normalize(fieldName) == null
            }
        if (unknownKeyword != null) {
            violations += DslViolation(
                path = "$path.$unknownKeyword",
                message = "Unknown action keyword '$unknownKeyword'",
            )
        }
    }

    private fun validateNestedActionPayload(
        keywordField: String,
        payload: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        val canonicalKeyword = keywordRegistry.normalize(keywordField) ?: return
        val unknownField = payload.fieldNames().asSequence()
            .firstOrNull { fieldName ->
                fieldName !in actionFields && keywordRegistry.normalize(fieldName) == null
            }
        if (unknownField != null) {
            violations += DslViolation(
                path = "$path.$unknownField",
                message = "Unknown action field '$unknownField' for keyword '$keywordField'",
            )
        }

        when (canonicalKeyword) {
            "tap" -> {
                validatePointerTargetPayload(payload, path, violations, actionName = "Tap")
                validateTapMissingElementPolicy(payload, path, violations)
            }

            "tapPosition" -> {
                validateTapPositionPayload(payload, path, violations)
            }

            "longPress" -> {
                validatePointerTargetPayload(payload, path, violations, actionName = "Long press")
            }

            "swipe" -> {
                validateSwipePayload(payload, path, violations)
            }

            "input" -> {
                requireNestedFields(payload, path, violations, "element", "value")
            }

            "restartApp" -> {
                requireNestedFields(payload, path, violations, "appId")
            }

            "getText" -> {
                requireNestedFields(payload, path, violations, "element", "saveAs")
            }

            "saveElementRect" -> {
                requireNestedFields(payload, path, violations, "element", "saveAs")
            }

            "wait" -> {
                if (!payload.hasNonNullField("durationMs") &&
                    !payload.hasNonNullField("timeoutMs") &&
                    !payload.hasNonNullField("value")
                ) {
                    violations += DslViolation(
                        path = path,
                        message = "Wait action requires durationMs, timeoutMs, or value",
                    )
                }
            }

            "assertElementAttrEquals" -> {
                requireNestedFields(payload, path, violations, "element", "attr", "expected")
            }

            "assertElementExists" -> {
                requireNestedFields(payload, path, violations, "element")
            }

            "assertElementAttrRegexMatch" -> {
                requireNestedFields(payload, path, violations, "element", "attr", "pattern")
            }

            "assertSourceRegexMatch" -> {
                requireNestedFields(payload, path, violations, "pattern")
            }

            "startScreenRecording" -> {
                // Optional timeLimitMs/timeoutMs only.
            }

            "stopScreenRecording" -> {
                // Optional resourceId/saveAs only.
            }

            "assertScreenRecordingTextRegexMatch" -> {
                requireNestedFields(payload, path, violations, "pattern")
            }

            "assertImageTextRegexMatch" -> {
                requireNestedFields(payload, path, violations, "pattern")
            }

            "tapVisualTemplate" -> {
                requireNestedFields(payload, path, violations, "template")
            }

            "assertImageColorRatio" -> {
                requireNestedFields(payload, path, violations, "source", "color", "minRatio")
            }

            "captureAppLogStart" -> {
                requireNestedFields(payload, path, violations, "saveAs")
            }

            "captureAppLogEnd" -> {
                // source/saveAs are optional; the executor falls back to the latest case log capture.
            }

            "customAssertAppLog" -> {
                requireNestedFields(payload, path, violations, "plugin", "assertion")
            }
        }
    }

    private fun validatePointerTargetPayload(
        payload: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
        actionName: String,
    ) {
        val hasElement = payload.hasNonNullField("element")
        val hasXRatio = payload.hasNonNullField("xRatio")
        val hasYRatio = payload.hasNonNullField("yRatio")
        val hasElementXRatio = payload.hasNonNullField("elementXRatio")
        val hasElementYRatio = payload.hasNonNullField("elementYRatio")
        if (hasElement && (hasXRatio || hasYRatio)) {
            violations += DslViolation(
                path = path,
                message = "$actionName action must use either element or xRatio/yRatio, not both",
            )
        } else if (!hasElement && !(hasXRatio && hasYRatio)) {
            violations += DslViolation(
                path = path,
                message = "$actionName action requires element or both xRatio and yRatio",
            )
        } else if (!hasElement && (hasElementXRatio || hasElementYRatio)) {
            violations += DslViolation(
                path = path,
                message = "$actionName action can only use elementXRatio/elementYRatio with element",
            )
        } else if (hasElementXRatio != hasElementYRatio) {
            violations += DslViolation(
                path = path,
                message = "$actionName action requires both elementXRatio and elementYRatio when overriding element-relative position",
            )
        }
    }

    private fun validateTapPositionPayload(
        payload: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        val hasXRatio = payload.hasNonNullField("xRatio")
        val hasYRatio = payload.hasNonNullField("yRatio")
        val hasElementXRatio = payload.hasNonNullField("elementXRatio")
        val hasElementYRatio = payload.hasNonNullField("elementYRatio")
        if (!(hasXRatio && hasYRatio)) {
            violations += DslViolation(
                path = path,
                message = "Tap position action requires both xRatio and yRatio",
            )
        }
        if (hasElementXRatio || hasElementYRatio) {
            violations += DslViolation(
                path = path,
                message = "Tap position action uses xRatio/yRatio within the selected region and must not use elementXRatio/elementYRatio",
            )
        }
        if (payload.hasNonNullField("ignoreMissingElement") || payload.hasNonNullField("ignoreMissingElementReason")) {
            violations += DslViolation(
                path = path,
                message = "Tap position action does not support ignoreMissingElement; use tap for optional conditional elements",
            )
        }
    }

    private fun validateTapMissingElementPolicy(
        payload: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        val ignoreMissingElement = payload.get("ignoreMissingElement")?.asBoolean(false) == true
        val hasReason = payload.hasNonNullField("ignoreMissingElementReason")
        if (ignoreMissingElement && !hasReason) {
            violations += DslViolation(
                path = path,
                message = "Tap action with ignoreMissingElement=true requires ignoreMissingElementReason",
            )
        }
        if (!ignoreMissingElement && hasReason) {
            violations += DslViolation(
                path = path,
                message = "Tap action can only use ignoreMissingElementReason with ignoreMissingElement=true",
            )
        }
        if (ignoreMissingElement && !payload.hasNonNullField("element")) {
            violations += DslViolation(
                path = path,
                message = "Tap action can only use ignoreMissingElement with element taps",
            )
        }
        if (hasReason) {
            val reason = payload.get("ignoreMissingElementReason")?.asText()
            if (reason !in predefinedTapMissingElementReasons) {
                violations += DslViolation(
                    path = "$path.ignoreMissingElementReason",
                    message = "Tap action ignoreMissingElementReason must be one of " +
                        predefinedTapMissingElementReasons.joinToString(", "),
                )
            }
        }
    }

    private fun validateSwipePayload(
        payload: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        val hasElement = payload.hasNonNullField("element")
        val viewportFields = listOf("startXRatio", "startYRatio", "endXRatio", "endYRatio")
        val elementFields = listOf("startElementXRatio", "startElementYRatio", "endElementXRatio", "endElementYRatio")
        val hasAnyViewportField = viewportFields.any { payload.hasNonNullField(it) }
        val hasAllViewportFields = viewportFields.all { payload.hasNonNullField(it) }
        val hasAnyElementField = elementFields.any { payload.hasNonNullField(it) }
        val hasAllElementFields = elementFields.all { payload.hasNonNullField(it) }

        when {
            hasElement && hasAnyViewportField -> {
                violations += DslViolation(
                    path = path,
                    message = "Swipe action must use either element-relative ratios or viewport ratios, not both",
                )
            }
            hasElement && !hasAllElementFields -> {
                violations += DslViolation(
                    path = path,
                    message = "Swipe action with element requires startElementXRatio, startElementYRatio, endElementXRatio, and endElementYRatio",
                )
            }
            !hasElement && !hasAllViewportFields -> {
                violations += DslViolation(
                    path = path,
                    message = "Swipe action requires element-relative ratios with element or viewport startXRatio, startYRatio, endXRatio, and endYRatio",
                )
            }
            !hasElement && hasAnyElementField -> {
                violations += DslViolation(
                    path = path,
                    message = "Swipe action can only use element-relative ratios with element",
                )
            }
            hasAnyViewportField && !hasAllViewportFields -> {
                violations += DslViolation(
                    path = path,
                    message = "Swipe action viewport ratios must include startXRatio, startYRatio, endXRatio, and endYRatio",
                )
            }
            hasAnyElementField && !hasAllElementFields -> {
                violations += DslViolation(
                    path = path,
                    message = "Swipe action element-relative ratios must include startElementXRatio, startElementYRatio, endElementXRatio, and endElementYRatio",
                )
            }
        }
    }

    private fun isKeywordValue(value: JsonNode): Boolean {
        return value.isTextual || value.isObject
    }

    private fun requireNestedFields(
        payload: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
        vararg fields: String,
    ) {
        fields
            .filterNot { payload.hasNonNullField(it) }
            .forEach { field ->
                violations += DslViolation(
                    path = "$path.$field",
                    message = "Nested action requires field '$field'",
                )
            }
    }

    private fun JsonNode.hasNonNullField(fieldName: String): Boolean {
        val value = get(fieldName) ?: return false
        if (value.isNull) {
            return false
        }
        if (value.isTextual && value.asText().isBlank()) {
            return false
        }
        return true
    }

    private fun validateLocators(
        node: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
    ) {
        if (node.isObject) {
            if (node.has("locator")) {
                validateLocator(node.get("locator"), "$path.locator", violations)
            }

            node.fields().forEachRemaining { (fieldName, child) ->
                validateLocators(child, "$path.$fieldName", violations)
            }
            return
        }

        if (node.isArray) {
            node.forEachIndexed { index, child ->
                validateLocators(child, "$path[$index]", violations)
            }
        }
    }

    private fun validateLocator(
        locator: JsonNode,
        path: String,
        violations: MutableList<DslViolation>,
        inheritedParameterizedTextReason: String? = null,
        inheritedHardcodedTextReason: String? = null,
    ) {
        val strategy = locator.path("strategy").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
        val value = locator.path("value").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
        val parameterizedTextReason = locator.parameterizedTextReason() ?: inheritedParameterizedTextReason
        val hardcodedTextReason = locator.hardcodedTextReason() ?: inheritedHardcodedTextReason
        if (strategy.isEmpty() || value.isEmpty()) {
            return
        }

        if (coordinateAttributeExpression.containsMatchIn(value)) {
            violations += DslViolation(
                path = "$path.value",
                message = "Locator expressions must not use coordinate or size attributes such as @x, @y, @width, or @height",
            )
        }

        val normalizedStrategy = strategy.lowercase()
        val usesTextLocator = normalizedStrategy in textStrategies ||
            (normalizedStrategy in expressionStrategies && hasTextAttributeExpression(value))

        if (!usesTextLocator) {
            return
        }

        if (parameterReference.containsMatchIn(value)) {
            if (parameterizedTextReason.isNullOrBlank()) {
                violations += DslViolation(
                    path = "$path.value",
                    message = "Parameterized text locators require parameterizedTextReason",
                )
                return
            }
            if (parameterizedTextReason !in policyConfig.parameterizedTextReasons) {
                violations += DslViolation(
                    path = "$path.parameterizedTextReason",
                    message = "Parameterized text locator reason '$parameterizedTextReason' is not allowed. Allowed reasons: " +
                        policyConfig.parameterizedTextReasons.sorted().joinToString(", "),
                )
                return
            }
        }

        val hasHardcodedText = normalizedStrategy in textStrategies || hasHardcodedTextExpression(value)

        if (!hardcodedTextReason.isNullOrBlank() && hardcodedTextReason !in policyConfig.hardcodedTextReasons) {
            violations += DslViolation(
                path = "$path.hardcodedTextReason",
                message = "Hardcoded text locator reason '$hardcodedTextReason' is not allowed. Allowed reasons: " +
                    policyConfig.hardcodedTextReasons.sorted().joinToString(", "),
            )
            return
        }

        if (hasHardcodedText && hardcodedTextReason in policyConfig.hardcodedTextReasons) {
            return
        }

        if (hasHardcodedText) {
            violations += DslViolation(
                path = "$path.value",
                message = "Locator expressions must not hardcode fixed UI copy unless hardcodedTextReason explains why the text is language-insensitive",
            )
        }
    }

    private fun hasHardcodedTextExpression(value: String): Boolean {
        return exactTextExpression.findAll(value).any { !stateValueComparisonExpression.matches(it.value) } ||
            containsTextExpression.containsMatchIn(value) ||
            startsWithTextExpression.containsMatchIn(value) ||
            uiAutomatorTextExpression.containsMatchIn(value) ||
            predicateTextExpression.containsMatchIn(value)
    }

    private fun hardcodedTextLiterals(value: String): List<String> {
        return (
            exactTextLiteralExpression.findAll(value)
                .filter { !stateValueComparisonExpression.matches(it.value) } +
                containsTextLiteralExpression.findAll(value) +
                startsWithTextLiteralExpression.findAll(value) +
                uiAutomatorTextLiteralExpression.findAll(value) +
                predicateTextLiteralExpression.findAll(value)
            )
            .mapNotNull { it.groups[1]?.value }
            .toList()
    }

    private fun hasTextAttributeExpression(value: String): Boolean {
        return textAttributeComparisonExpression.containsMatchIn(value) ||
            containsTextAttributeExpression.containsMatchIn(value) ||
            startsWithTextAttributeExpression.containsMatchIn(value) ||
            uiAutomatorTextAttributeExpression.containsMatchIn(value) ||
            predicateTextAttributeExpression.containsMatchIn(value)
    }

    private fun JsonNode.parameterizedTextReason(): String? {
        return path("parameterizedTextReason")
            .takeIf { it.isTextual }
            ?.asText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun JsonNode.hardcodedTextReason(): String? {
        return path("hardcodedTextReason")
            .takeIf { it.isTextual }
            ?.asText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
