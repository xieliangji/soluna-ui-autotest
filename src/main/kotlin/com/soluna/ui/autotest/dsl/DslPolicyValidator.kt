package com.soluna.ui.autotest.dsl

import com.fasterxml.jackson.databind.JsonNode

class DslPolicyValidator(
    private val keywordRegistry: KeywordRegistry = DefaultKeywordRegistry,
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
    private val actionFields = setOf(
        "appId",
        "asRoi",
        "candidateMaxFrames",
        "candidateStrategy",
        "clearFirst",
        "desc",
        "durationMs",
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
        "attr",
        "id",
        "maxFrames",
        "pattern",
        "recognizer",
        "resourceId",
        "roi",
        "saveAs",
        "scope",
        "settleMs",
        "source",
        "scales",
        "targetXRatio",
        "targetYRatio",
        "template",
        "threshold",
        "target",
        "timeLimitMs",
        "timeoutMs",
        "value",
        "visualDifferenceThreshold",
        "wait",
        "xRatio",
        "yRatio",
    )
    private val textStrategies = setOf("text", "文本", "label", "name")
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
        pattern = """(?i)(@text|text|label|name|value)\s*(==|=)\s*['"][^'$"][^'"]*['"]""",
    )

    private val textAttributeComparisonExpression = Regex(
        pattern = """(?i)(@text|text|label|name|value)\s*(==|=)\s*['"]""",
    )

    private val containsTextExpression = Regex(
        pattern = """(?i)contains\s*\(\s*(@text|text|label|name|value)\s*,\s*['"][^'$"][^'"]*['"]\s*\)""",
    )

    private val containsTextAttributeExpression = Regex(
        pattern = """(?i)contains\s*\(\s*(@text|text|label|name|value)\s*,\s*['"]""",
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
        pattern = """(?i)(?:@text|text|label|name|value)\s*(?:==|=)\s*['"]([^'$"][^'"]*)['"]""",
    )

    private val containsTextLiteralExpression = Regex(
        pattern = """(?i)contains\s*\(\s*(?:@text|text|label|name|value)\s*,\s*['"]([^'$"][^'"]*)['"]\s*\)""",
    )

    private val uiAutomatorTextLiteralExpression = Regex(
        pattern = """(?i)\.text(?:contains|matches)?\s*\(\s*['"]([^'$"][^'"]*)['"]\s*\)""",
    )

    private val predicateTextLiteralExpression = Regex(
        pattern = """(?i)(?:label|name|value|text)\s+(?:CONTAINS|BEGINSWITH|ENDSWITH|MATCHES)(?:\[[cd]]+)?\s*['"]([^'$"][^'"]*)['"]""",
    )

    private val iconNameTextLocatorPurpose = "iconName"

    private val approvedTextLocatorPurposes = setOf(
        "brandLogo",
        "languageTitle",
    )

    private val parameterizedTextLocatorPurposes = setOf(
        "languageTitle",
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
                        inheritedTextLocatorPurpose = null,
                    )
                }
                listOf("android", "ios").forEach { platform ->
                    if (element.has(platform)) {
                        validateLocator(
                            locator = element.get(platform),
                            path = "$.elements.$name.$platform",
                            violations = violations,
                            inheritedTextLocatorPurpose = element.textLocatorPurpose(),
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
                val hasElement = payload.hasNonNullField("element")
                val hasXRatio = payload.hasNonNullField("xRatio")
                val hasYRatio = payload.hasNonNullField("yRatio")
                val hasElementXRatio = payload.hasNonNullField("elementXRatio")
                val hasElementYRatio = payload.hasNonNullField("elementYRatio")
                if (hasElement && (hasXRatio || hasYRatio)) {
                    violations += DslViolation(
                        path = path,
                        message = "Tap action must use either element or xRatio/yRatio, not both",
                    )
                } else if (!hasElement && !(hasXRatio && hasYRatio)) {
                    violations += DslViolation(
                        path = path,
                        message = "Tap action requires element or both xRatio and yRatio",
                    )
                } else if (!hasElement && (hasElementXRatio || hasElementYRatio)) {
                    violations += DslViolation(
                        path = path,
                        message = "Tap action can only use elementXRatio/elementYRatio with element",
                    )
                } else if (hasElementXRatio != hasElementYRatio) {
                    violations += DslViolation(
                        path = path,
                        message = "Tap action requires both elementXRatio and elementYRatio when overriding element-relative click position",
                    )
                }
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

            "tapVisualTemplate" -> {
                requireNestedFields(payload, path, violations, "template")
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
        inheritedTextLocatorPurpose: String? = null,
    ) {
        val strategy = locator.path("strategy").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
        val value = locator.path("value").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
        val textLocatorPurpose = locator.textLocatorPurpose() ?: inheritedTextLocatorPurpose
        if (strategy.isEmpty() || value.isEmpty()) {
            return
        }

        val normalizedStrategy = strategy.lowercase()
        val usesTextLocator = normalizedStrategy in textStrategies ||
            (normalizedStrategy in expressionStrategies && hasTextAttributeExpression(value))

        if (!usesTextLocator) {
            return
        }

        if (parameterReference.containsMatchIn(value)) {
            if (textLocatorPurpose in parameterizedTextLocatorPurposes) {
                return
            }
            violations += DslViolation(
                path = "$path.value",
                message = "Parameterized text locators are only allowed for approved stable text purposes such as languageTitle",
            )
            return
        }

        val hasHardcodedText = normalizedStrategy in textStrategies || hasHardcodedTextExpression(value)

        if (hasHardcodedText &&
            textLocatorPurpose == iconNameTextLocatorPurpose &&
            normalizedStrategy in expressionStrategies &&
            hasOnlyIconNameHardcodedText(value)
        ) {
            return
        }

        if (hasHardcodedText && textLocatorPurpose in approvedTextLocatorPurposes) {
            return
        }

        if (hasHardcodedText) {
            violations += DslViolation(
                path = "$path.value",
                message = "Locator expressions must not hardcode fixed UI copy. Avoid text-based locators outside approved stable text purposes such as iconName, brandLogo, or languageTitle",
            )
        }
    }

    private fun hasHardcodedTextExpression(value: String): Boolean {
        return exactTextExpression.findAll(value).any { !stateValueComparisonExpression.matches(it.value) } ||
            containsTextExpression.containsMatchIn(value) ||
            uiAutomatorTextExpression.containsMatchIn(value) ||
            predicateTextExpression.containsMatchIn(value)
    }

    private fun hasOnlyIconNameHardcodedText(value: String): Boolean {
        val literals = hardcodedTextLiterals(value)
        return literals.isNotEmpty() && literals.all { it.startsWith("icon ") }
    }

    private fun hardcodedTextLiterals(value: String): List<String> {
        return (
            exactTextLiteralExpression.findAll(value)
                .filter { !stateValueComparisonExpression.matches(it.value) } +
                containsTextLiteralExpression.findAll(value) +
                uiAutomatorTextLiteralExpression.findAll(value) +
                predicateTextLiteralExpression.findAll(value)
            )
            .mapNotNull { it.groups[1]?.value }
            .toList()
    }

    private fun hasTextAttributeExpression(value: String): Boolean {
        return textAttributeComparisonExpression.containsMatchIn(value) ||
            containsTextAttributeExpression.containsMatchIn(value) ||
            uiAutomatorTextAttributeExpression.containsMatchIn(value) ||
            predicateTextAttributeExpression.containsMatchIn(value)
    }

    private fun JsonNode.textLocatorPurpose(): String? {
        return path("textLocatorPurpose")
            .takeIf { it.isTextual }
            ?.asText()
    }
}
