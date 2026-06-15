package com.ugreen.iot.soluna.autotest.appium.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.ugreen.iot.soluna.autotest.appium.driver.DriverElement
import com.ugreen.iot.soluna.autotest.appium.driver.DriverSession
import com.ugreen.iot.soluna.autotest.appium.driver.DriverWaitOptions
import com.ugreen.iot.soluna.autotest.appium.driver.ScreenshotData
import com.ugreen.iot.soluna.autotest.appium.driver.StartSessionRequest
import com.ugreen.iot.soluna.autotest.appium.driver.WebDriverAdapter
import com.ugreen.iot.soluna.autotest.core.execution.DefaultActionExecutorRegistry
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionContext
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.core.execution.Sleeper
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import com.ugreen.iot.soluna.autotest.core.model.PlanDefinition
import com.ugreen.iot.soluna.autotest.core.model.WaitDefinition
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WebDriverActionExecutorsTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `tap action finds element and taps it`() {
        val driver = RecordingWebDriverAdapter()
        val executor = TapActionExecutor(driver)
        val action = ActionDefinition(
            id = "tap-login",
            keyword = "tap",
            locator = LocatorDefinition(
                strategy = "id",
                value = "login_button",
            ),
            wait = WaitDefinition(timeoutMs = 3000, intervalMs = 100),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=login_button:3000", "tap:element-1"), driver.calls)
    }

    @Test
    fun `tap action can tap viewport coordinates by ratio`() {
        val driver = RecordingWebDriverAdapter()
        val executor = TapActionExecutor(driver)
        val action = ActionDefinition(
            id = "dismiss-modal-backdrop",
            keyword = "tap",
            args = mapOf(
                "xRatio" to objectMapper.valueToTree(0.5),
                "yRatio" to objectMapper.valueToTree(0.3),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("tapViewport:session-1:0.5:0.3"), driver.calls)
    }

    @Test
    fun `input action sends text with clear first default`() {
        val driver = RecordingWebDriverAdapter()
        val executor = InputActionExecutor(driver)
        val action = ActionDefinition(
            id = "input-username",
            keyword = "input",
            locator = LocatorDefinition(
                strategy = "id",
                value = "username_input",
            ),
            value = objectMapper.valueToTree("demo-user"),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=username_input:null", "input:element-1:demo-user:true"), driver.calls)
    }

    @Test
    fun `input action honors clearFirst false arg`() {
        val driver = RecordingWebDriverAdapter()
        val executor = InputActionExecutor(driver)
        val action = ActionDefinition(
            id = "input-username",
            keyword = "input",
            locator = LocatorDefinition(
                strategy = "id",
                value = "username_input",
            ),
            value = objectMapper.valueToTree("demo-user"),
            args = mapOf("clearFirst" to objectMapper.valueToTree(false)),
        )

        executor.execute(action, context())

        assertEquals(listOf("find:session-1:id=username_input:null", "input:element-1:demo-user:false"), driver.calls)
    }

    @Test
    fun `get text action stores case variable and input action can consume it`() {
        val driver = RecordingWebDriverAdapter(elementText = "OldName")
        val context = context(currentCaseId = "case-1")
        val getText = GetTextActionExecutor(driver)
        val input = InputActionExecutor(driver)

        val getTextResult = getText.execute(
            ActionDefinition(
                id = "capture-original",
                keyword = "getText",
                locator = LocatorDefinition("id", "nickname"),
                args = mapOf(
                    "scope" to objectMapper.valueToTree("case"),
                    "saveAs" to objectMapper.valueToTree("originalNickname"),
                ),
            ),
            context,
        )
        val inputResult = input.execute(
            ActionDefinition(
                id = "restore-original",
                keyword = "input",
                locator = LocatorDefinition("class", "android.widget.EditText"),
                value = objectMapper.valueToTree("@{case.originalNickname}"),
            ),
            context,
        )

        assertEquals(ExecutionStatus.PASSED, getTextResult.status)
        assertEquals(ExecutionStatus.PASSED, inputResult.status)
        assertEquals(
            listOf(
                "find:session-1:id=nickname:null",
                "text:element-1",
                "find:session-1:class=android.widget.EditText:null",
                "input:element-1:OldName:true",
            ),
            driver.calls,
        )
    }

    @Test
    fun `screenshot action sends explicit screenshot to sink`() {
        val driver = RecordingWebDriverAdapter()
        val sink = RecordingScreenshotSink()
        val executor = ScreenshotActionExecutor(driver, sink)
        val action = ActionDefinition(
            id = "capture-home",
            keyword = "screenshot",
            name = "Home",
            resourceId = "home-after-login",
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("screenshot:session-1"), driver.calls)
        val screenshot = sink.screenshots.single()
        assertEquals("run-1", screenshot.runId)
        assertEquals("plan-1", screenshot.planId)
        assertEquals("capture-home", screenshot.actionId)
        assertEquals("home-after-login", screenshot.resourceId)
        assertContentEquals(byteArrayOf(1, 2, 3), screenshot.data.bytes)
    }

    @Test
    fun `restart app action delegates app lifecycle command`() {
        val driver = RecordingWebDriverAdapter()
        val executor = RestartAppActionExecutor(driver)
        val action = ActionDefinition(
            id = "restart-app",
            keyword = "restartApp",
            args = mapOf("appId" to objectMapper.valueToTree("com.example.demo")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("restart:session-1:com.example.demo"), driver.calls)
    }

    @Test
    fun `assert element attr equals action compares configured attribute`() {
        val driver = RecordingWebDriverAdapter(attributes = mapOf("name" to "SolunaTester"))
        val executor = AssertElementAttrEqualsActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-nickname",
            keyword = "assertElementAttrEquals",
            locator = LocatorDefinition(
                strategy = "id",
                value = "nickname",
            ),
            value = objectMapper.valueToTree("SolunaTester"),
            args = mapOf("attr" to objectMapper.valueToTree("name/label")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=nickname:null", "attr:element-1:name"), driver.calls)
    }

    @Test
    fun `assert element attr equals action fails on mismatched attribute`() {
        val driver = RecordingWebDriverAdapter(attributes = mapOf("text" to "OldName"))
        val executor = AssertElementAttrEqualsActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-nickname",
            keyword = "assertElementAttrEquals",
            locator = LocatorDefinition(
                strategy = "id",
                value = "nickname",
            ),
            value = objectMapper.valueToTree("SolunaTester"),
            args = mapOf("attr" to objectMapper.valueToTree("text")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals("Expected text to equal 'SolunaTester' but was 'OldName'", result.error)
    }

    @Test
    fun `assert element attr equals action polls until attribute matches`() {
        val driver = RecordingWebDriverAdapter(
            attributeSequences = mapOf("text" to listOf("OldName", "SolunaTester")),
        )
        val sleeper = RecordingSleeper()
        val executor = AssertElementAttrEqualsActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "assert-nickname",
            keyword = "assertElementAttrEquals",
            locator = LocatorDefinition(
                strategy = "id",
                value = "nickname",
            ),
            value = objectMapper.valueToTree("SolunaTester"),
            wait = WaitDefinition(timeoutMs = 1_000, intervalMs = 100),
            args = mapOf("attr" to objectMapper.valueToTree("text")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(100L), sleeper.delays)
        assertEquals(
            listOf(
                "find:session-1:id=nickname:1000",
                "attr:element-1:text",
                "find:session-1:id=nickname:null",
                "attr:element-1:text",
            ),
            driver.calls,
        )
    }

    @Test
    fun `assert element attr regex match action checks configured attribute pattern`() {
        val driver = RecordingWebDriverAdapter(attributes = mapOf("label" to "SolunaTester"))
        val executor = AssertElementAttrRegexMatchActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-nickname-pattern",
            keyword = "assertElementAttrRegexMatch",
            locator = LocatorDefinition(
                strategy = "id",
                value = "nickname",
            ),
            value = objectMapper.valueToTree("Soluna.*"),
            args = mapOf("attr" to objectMapper.valueToTree("name/label")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=nickname:null", "attr:element-1:name", "attr:element-1:label"), driver.calls)
    }

    @Test
    fun `assert element attr regex match action polls until attribute matches pattern`() {
        val driver = RecordingWebDriverAdapter(
            attributeSequences = mapOf("label" to listOf("OldName", "SolunaTester")),
        )
        val sleeper = RecordingSleeper()
        val executor = AssertElementAttrRegexMatchActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "assert-nickname-pattern",
            keyword = "assertElementAttrRegexMatch",
            locator = LocatorDefinition(
                strategy = "id",
                value = "nickname",
            ),
            value = objectMapper.valueToTree("Soluna.*"),
            wait = WaitDefinition(timeoutMs = 1_000, intervalMs = 100),
            args = mapOf("attr" to objectMapper.valueToTree("label")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(100L), sleeper.delays)
        assertEquals(
            listOf(
                "find:session-1:id=nickname:1000",
                "attr:element-1:label",
                "find:session-1:id=nickname:null",
                "attr:element-1:label",
            ),
            driver.calls,
        )
    }

    @Test
    fun `assert source regex match action checks page source pattern`() {
        val driver = RecordingWebDriverAdapter(pageSource = "<App><Label name=\"SolunaTester\" /></App>")
        val executor = AssertSourceRegexMatchActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-source",
            keyword = "assertSourceRegexMatch",
            value = objectMapper.valueToTree("Label name=\"Soluna.*\""),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("source:session-1"), driver.calls)
    }

    @Test
    fun `assert source regex match action polls until source matches`() {
        val driver = RecordingWebDriverAdapter(
            pageSources = listOf("<App><Label name=\"OldName\" /></App>", "<App><Label name=\"SolunaTester\" /></App>"),
        )
        val sleeper = RecordingSleeper()
        val executor = AssertSourceRegexMatchActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "assert-source",
            keyword = "assertSourceRegexMatch",
            value = objectMapper.valueToTree("SolunaTester"),
            wait = WaitDefinition(timeoutMs = 1_000, intervalMs = 100),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(100L), sleeper.delays)
        assertEquals(listOf("source:session-1", "source:session-1"), driver.calls)
    }

    @Test
    fun `wait action sleeps for configured duration`() {
        val sleeper = RecordingSleeper()
        val executor = WaitActionExecutor(sleeper)

        val result = executor.execute(
            ActionDefinition(
                id = "settle-after-update",
                keyword = "wait",
                args = mapOf("durationMs" to objectMapper.valueToTree(1200)),
            ),
            context(),
        )

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(1200L), sleeper.delays)
    }

    @Test
    fun `default web driver action executors register through action registry`() {
        val driver = RecordingWebDriverAdapter()
        val sink = RecordingScreenshotSink()
        val registry = DefaultActionExecutorRegistry(
            defaultWebDriverActionExecutors(driver, sink),
        )

        val result = registry.execute(
            ActionDefinition(
                id = "capture-home",
                keyword = "截图",
                resourceId = "home-after-login",
            ),
            context(),
        )

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals("home-after-login", sink.screenshots.single().resourceId)
    }

    private fun context(
        currentCaseId: String? = null,
    ): ExecutionContext {
        return ExecutionContext(
            runId = "run-1",
            plan = PlanDefinition(
                schemaVersion = "1.0",
                id = "plan-1",
                name = "Plan 1",
            ),
            driverSessionId = "session-1",
            currentCaseId = currentCaseId,
        )
    }

    private class RecordingScreenshotSink : ScreenshotSink {
        val screenshots = mutableListOf<ExplicitScreenshot>()

        override fun accept(screenshot: ExplicitScreenshot) {
            screenshots += screenshot
        }
    }

    private class RecordingSleeper : Sleeper {
        val delays = mutableListOf<Long>()

        override fun sleep(delayMs: Long) {
            delays += delayMs
        }
    }

    private class RecordingWebDriverAdapter(
        private val elementText: String = "",
        private val attributes: Map<String, String> = emptyMap(),
        private val attributeSequences: Map<String, List<String>> = emptyMap(),
        private val pageSource: String = "",
        private val pageSources: List<String> = emptyList(),
    ) : WebDriverAdapter {
        val calls = mutableListOf<String>()
        private val attributeReadCounts = mutableMapOf<String, Int>()
        private var pageSourceReadCount = 0

        override fun startSession(request: StartSessionRequest): DriverSession {
            error("not used")
        }

        override fun getSession(sessionId: String): DriverSession? {
            return null
        }

        override fun stopSession(sessionId: String) {
            calls += "stop:$sessionId"
        }

        override fun findElement(
            sessionId: String,
            locator: LocatorDefinition,
            wait: DriverWaitOptions?,
        ): DriverElement {
            calls += "find:$sessionId:${locator.strategy}=${locator.value}:${wait?.timeoutMs}"
            return DriverElement("element-1")
        }

        override fun tap(
            sessionId: String,
            element: DriverElement,
        ) {
            calls += "tap:${element.elementId}"
        }

        override fun tapViewport(
            sessionId: String,
            xRatio: Double,
            yRatio: Double,
        ) {
            calls += "tapViewport:$sessionId:$xRatio:$yRatio"
        }

        override fun inputText(
            sessionId: String,
            element: DriverElement,
            text: String,
            clearFirst: Boolean,
        ) {
            calls += "input:${element.elementId}:$text:$clearFirst"
        }

        override fun getElementText(
            sessionId: String,
            element: DriverElement,
        ): String {
            calls += "text:${element.elementId}"
            return elementText
        }

        override fun getElementAttribute(
            sessionId: String,
            element: DriverElement,
            name: String,
        ): String? {
            calls += "attr:${element.elementId}:$name"
            attributeSequences[name]?.let { values ->
                val index = attributeReadCounts.getOrDefault(name, 0)
                attributeReadCounts[name] = index + 1
                return values[index.coerceAtMost(values.lastIndex)]
            }
            return attributes[name]
        }

        override fun getPageSource(sessionId: String): String {
            calls += "source:$sessionId"
            if (pageSources.isNotEmpty()) {
                val index = pageSourceReadCount
                pageSourceReadCount += 1
                return pageSources[index.coerceAtMost(pageSources.lastIndex)]
            }
            return pageSource
        }

        override fun restartApp(
            sessionId: String,
            appId: String,
        ) {
            calls += "restart:$sessionId:$appId"
        }

        override fun takeScreenshot(sessionId: String): ScreenshotData {
            calls += "screenshot:$sessionId"
            return ScreenshotData(byteArrayOf(1, 2, 3))
        }

        override fun isSessionHealthy(sessionId: String): Boolean {
            return true
        }
    }
}
