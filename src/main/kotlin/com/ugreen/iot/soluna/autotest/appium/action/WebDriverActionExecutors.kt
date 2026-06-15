package com.ugreen.iot.soluna.autotest.appium.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import com.ugreen.iot.soluna.autotest.appium.driver.DriverElement
import com.ugreen.iot.soluna.autotest.appium.driver.DriverWaitOptions
import com.ugreen.iot.soluna.autotest.appium.driver.ScreenshotData
import com.ugreen.iot.soluna.autotest.appium.driver.WebDriverAdapter
import com.ugreen.iot.soluna.autotest.core.execution.ActionExecutionResult
import com.ugreen.iot.soluna.autotest.core.execution.ActionExecutor
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionContext
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.core.execution.Sleeper
import com.ugreen.iot.soluna.autotest.core.execution.ThreadSleeper
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import com.ugreen.iot.soluna.autotest.core.model.WaitDefinition

class TapActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "tap"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val xRatio = action.args["xRatio"]?.asDoubleOrNull()
        val yRatio = action.args["yRatio"]?.asDoubleOrNull()
        if (xRatio != null || yRatio != null) {
            require(xRatio != null && yRatio != null) {
                "Tap action '${action.id ?: action.keyword}' requires both args.xRatio and args.yRatio"
            }
            driver.tapViewport(
                sessionId = sessionId,
                xRatio = xRatio,
                yRatio = yRatio,
            )
            return ActionExecutionResult.passed("tap viewport executed")
        }

        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        driver.tap(sessionId, element)
        return ActionExecutionResult.passed("tap executed")
    }
}

class InputActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "input"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        driver.inputText(
            sessionId = sessionId,
            element = element,
            text = action.requireTextValue().resolveRuntimeText(context),
            clearFirst = action.args["clearFirst"]?.asBooleanOrNull() ?: true,
        )
        return ActionExecutionResult.passed("input executed")
    }
}

class ScreenshotActionExecutor(
    private val driver: WebDriverAdapter,
    private val sink: ScreenshotSink = NoOpScreenshotSink,
) : ActionExecutor {
    override val keyword: String = "screenshot"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val screenshot = driver.takeScreenshot(sessionId)
        sink.accept(
            ExplicitScreenshot(
                runId = context.runId,
                planId = context.plan.id,
                actionId = action.id,
                resourceId = action.resourceId,
                name = action.name,
                data = screenshot,
            ),
        )
        return ActionExecutionResult.passed("screenshot captured")
    }
}

class RestartAppActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "restartApp"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val appId = action.args["appId"]?.asTextOrNull()
            ?: action.value?.takeIf { it.isTextual }?.asText()
            ?: error("Restart app action '${action.id ?: action.keyword}' requires args.appId or string value")
        driver.restartApp(sessionId, appId)
        return ActionExecutionResult.passed("app restarted")
    }
}

class GetTextActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "getText"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val saveAs = action.args["saveAs"]?.asTextOrNull()
            ?: error("Get text action '${action.id ?: action.keyword}' requires args.saveAs")
        val scope = action.args["scope"]?.asTextOrNull() ?: "case"
        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        val text = driver.getElementText(sessionId, element)
        context.variables.set(
            scope = scope,
            name = saveAs,
            value = TextNode(text),
            caseId = context.caseVariableScopeId(),
        )
        return ActionExecutionResult.passed("saved text to $scope.$saveAs")
    }
}

class AssertElementAttrEqualsActionExecutor(
    private val driver: WebDriverAdapter,
    private val sleeper: Sleeper = ThreadSleeper,
) : ActionExecutor {
    override val keyword: String = "assertElementAttrEquals"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val attrCandidates = action.requireAttrCandidates()
        val resolvedExpected = action.requireExpectedText("Assert element attr equals", context)
        return action.pollAssertion(sleeper) { attempt ->
            val element = driver.findElement(
                sessionId = sessionId,
                locator = action.requireLocator(),
                wait = action.assertionFindWait(attempt),
            )
            val actual = driver.firstNonBlankAttribute(sessionId, element, attrCandidates)
            if (actual == resolvedExpected) {
                ActionExecutionResult.passed("assert element attr equals passed")
            } else {
                ActionExecutionResult.failed("Expected ${attrCandidates.joinToString("/")} to equal '$resolvedExpected' but was '$actual'")
            }
        }
    }
}

class AssertElementAttrRegexMatchActionExecutor(
    private val driver: WebDriverAdapter,
    private val sleeper: Sleeper = ThreadSleeper,
) : ActionExecutor {
    override val keyword: String = "assertElementAttrRegexMatch"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val attrCandidates = action.requireAttrCandidates()
        val pattern = action.requirePatternText(context)
        return action.pollAssertion(sleeper) { attempt ->
            val element = driver.findElement(
                sessionId = sessionId,
                locator = action.requireLocator(),
                wait = action.assertionFindWait(attempt),
            )
            val actual = driver.firstNonBlankAttribute(sessionId, element, attrCandidates)
            if (pattern.containsMatchIn(actual)) {
                ActionExecutionResult.passed("assert element attr regex match passed")
            } else {
                ActionExecutionResult.failed("Expected ${attrCandidates.joinToString("/")} to match '${pattern.pattern}' but was '$actual'")
            }
        }
    }
}

class AssertSourceRegexMatchActionExecutor(
    private val driver: WebDriverAdapter,
    private val sleeper: Sleeper = ThreadSleeper,
) : ActionExecutor {
    override val keyword: String = "assertSourceRegexMatch"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val pattern = action.requirePatternText(context)
        return action.pollAssertion(sleeper) {
            val source = driver.getPageSource(sessionId)
            if (pattern.containsMatchIn(source)) {
                ActionExecutionResult.passed("assert source regex match passed")
            } else {
                ActionExecutionResult.failed("Expected page source to match '${pattern.pattern}'")
            }
        }
    }
}

class WaitActionExecutor(
    private val sleeper: Sleeper = ThreadSleeper,
) : ActionExecutor {
    override val keyword: String = "wait"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val durationMs = action.args["durationMs"]?.asLongOrNull()
            ?: action.args["timeoutMs"]?.asLongOrNull()
            ?: action.value?.asLongOrNull()
            ?: action.wait?.timeoutMs
            ?: error("Wait action '${action.id ?: action.keyword}' requires value, args.durationMs, args.timeoutMs, or wait.timeoutMs")
        require(durationMs >= 0) { "Wait action durationMs must be >= 0" }
        sleeper.sleep(durationMs)
        return ActionExecutionResult.passed("waited ${durationMs}ms")
    }
}

data class ExplicitScreenshot(
    val runId: String,
    val planId: String,
    val actionId: String?,
    val resourceId: String?,
    val name: String?,
    val data: ScreenshotData,
)

interface ScreenshotSink {
    fun accept(screenshot: ExplicitScreenshot)
}

object NoOpScreenshotSink : ScreenshotSink {
    override fun accept(screenshot: ExplicitScreenshot) {
        // Screenshot manifest/upload handling is implemented by later artifact iterations.
    }
}

fun defaultWebDriverActionExecutors(
    driver: WebDriverAdapter,
    screenshotSink: ScreenshotSink = NoOpScreenshotSink,
): List<ActionExecutor> {
    return listOf(
        RestartAppActionExecutor(driver),
        GetTextActionExecutor(driver),
        TapActionExecutor(driver),
        InputActionExecutor(driver),
        AssertElementAttrEqualsActionExecutor(driver),
        AssertElementAttrRegexMatchActionExecutor(driver),
        AssertSourceRegexMatchActionExecutor(driver),
        ScreenshotActionExecutor(driver, screenshotSink),
        WaitActionExecutor(),
    )
}

private fun ExecutionContext.requireDriverSessionId(): String {
    return driverSessionId ?: error("Action requires an Appium/WebDriver session id")
}

private fun ActionDefinition.requireLocator(): LocatorDefinition {
    return locator ?: error("Action '${id ?: keyword}' requires a locator")
}

private fun ActionDefinition.requireTextValue(): String {
    val node = value ?: error("Input action '${id ?: keyword}' requires value")
    if (!node.isTextual) {
        error("Input action '${id ?: keyword}' value must be a string")
    }
    return node.asText()
}

private fun ActionDefinition.requireExpectedText(
    actionLabel: String,
    context: ExecutionContext,
): String {
    val expected = assertion?.expected?.takeIf { it.isTextual }?.asText()
        ?: value?.takeIf { it.isTextual }?.asText()
        ?: error("$actionLabel action '${id ?: keyword}' requires a textual expected value")
    return expected.resolveRuntimeText(context)
}

private fun ActionDefinition.requirePatternText(context: ExecutionContext): Regex {
    val pattern = value?.takeIf { it.isTextual }?.asText()
        ?: args["pattern"]?.asTextOrNull()
        ?: error("Regex assertion action '${id ?: keyword}' requires pattern")
    val resolvedPattern = pattern.resolveRuntimeText(context)
    return runCatching {
        Regex(resolvedPattern, setOf(RegexOption.DOT_MATCHES_ALL))
    }.getOrElse { err ->
        error("Regex assertion action '${id ?: keyword}' has invalid pattern '$resolvedPattern': ${err.message}")
    }
}

private fun ActionDefinition.requireAttrCandidates(): List<String> {
    return args["attr"]?.asTextOrNull()
        ?.split("/")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?: error("Element attribute assertion action '${id ?: keyword}' requires args.attr")
}

private fun WebDriverAdapter.firstNonBlankAttribute(
    sessionId: String,
    element: DriverElement,
    attrCandidates: List<String>,
): String {
    return attrCandidates.firstNotNullOfOrNull { attr ->
        getElementAttribute(sessionId, element, attr)
            ?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun ActionDefinition.pollAssertion(
    sleeper: Sleeper,
    evaluate: (attempt: Int) -> ActionExecutionResult,
): ActionExecutionResult {
    val timeoutMs = wait?.timeoutMs ?: 0
    val intervalMs = wait?.intervalMs?.takeIf { it > 0 } ?: 500
    val attempts = if (timeoutMs <= 0) {
        1
    } else {
        ((timeoutMs + intervalMs - 1) / intervalMs + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }.coerceAtLeast(1)

    var lastResult: ActionExecutionResult? = null
    repeat(attempts) { attempt ->
        val result = runCatching {
            evaluate(attempt)
        }.getOrElse { err ->
            ActionExecutionResult.failed(err.message ?: err::class.simpleName ?: "assertion failed")
        }
        if (result.status == ExecutionStatus.PASSED) {
            return result
        }
        lastResult = result
        if (attempt < attempts - 1) {
            sleeper.sleep(intervalMs)
        }
    }
    return lastResult ?: ActionExecutionResult.failed("assertion failed")
}

private fun ActionDefinition.assertionFindWait(attempt: Int): DriverWaitOptions? {
    return if (attempt == 0) {
        wait.toDriverWaitOptions()
    } else {
        null
    }
}

private fun WaitDefinition?.toDriverWaitOptions(): DriverWaitOptions? {
    val timeout = this?.timeoutMs ?: return null
    return DriverWaitOptions(
        timeoutMs = timeout,
        intervalMs = intervalMs,
    )
}

private fun JsonNode.asBooleanOrNull(): Boolean? {
    return when {
        isBoolean -> asBoolean()
        isTextual && asText().equals("true", ignoreCase = true) -> true
        isTextual && asText().equals("false", ignoreCase = true) -> false
        else -> null
    }
}

private fun JsonNode.asTextOrNull(): String? {
    return takeIf { isTextual }?.asText()
}

private fun JsonNode.asLongOrNull(): Long? {
    return when {
        isIntegralNumber -> asLong()
        isTextual -> asText().toLongOrNull()
        else -> null
    }
}

private fun JsonNode.asDoubleOrNull(): Double? {
    return when {
        isNumber -> asDouble()
        isTextual -> asText().toDoubleOrNull()
        else -> null
    }
}

private fun String.resolveRuntimeText(context: ExecutionContext): String {
    val exact = runtimeVariableReference.matchEntire(this)
    if (exact != null) {
        return context.lookupRuntimeVariable(exact.groupValues[1], exact.groupValues[2]).asText()
    }
    return runtimeVariableReference.replace(this) { match ->
        context.lookupRuntimeVariable(match.groupValues[1], match.groupValues[2]).asText()
    }
}

private fun ExecutionContext.lookupRuntimeVariable(
    scope: String,
    name: String,
): JsonNode {
    return variables.get(
        scope = scope,
        name = name,
        caseId = caseVariableScopeId(),
    ) ?: error("Runtime variable '@{$scope.$name}' is not defined")
}

private val runtimeVariableReference = Regex("""@\{(plan|case)\.([^}]+)}""")
