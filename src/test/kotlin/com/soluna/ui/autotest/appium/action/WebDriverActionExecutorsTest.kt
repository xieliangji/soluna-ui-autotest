package com.soluna.ui.autotest.appium.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.appium.driver.DriverElement
import com.soluna.ui.autotest.appium.driver.DriverSession
import com.soluna.ui.autotest.appium.driver.DriverWaitOptions
import com.soluna.ui.autotest.appium.driver.ScreenRecordingData
import com.soluna.ui.autotest.appium.driver.ScreenRecordingOptions
import com.soluna.ui.autotest.appium.driver.ScreenshotData
import com.soluna.ui.autotest.appium.driver.StartSessionRequest
import com.soluna.ui.autotest.appium.driver.WebDriverAdapter
import com.soluna.ui.autotest.artifact.CapturedPlanResource
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import com.soluna.ui.autotest.core.execution.DefaultActionExecutorRegistry
import com.soluna.ui.autotest.core.execution.ExecutionContext
import com.soluna.ui.autotest.core.execution.ExecutionStatus
import com.soluna.ui.autotest.core.execution.Sleeper
import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.LocatorDefinition
import com.soluna.ui.autotest.core.model.PlanDefinition
import com.soluna.ui.autotest.core.model.WaitDefinition
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WebDriverActionExecutorsTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `tap action finds element and taps it`() {
        val driver = RecordingWebDriverAdapter()
        val sleeper = RecordingSleeper()
        val executor = TapActionExecutor(driver, sleeper)
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
        assertEquals(listOf("find:session-1:id=login_button:3000", "tap:element-1:0.5:0.5"), driver.calls)
        assertEquals(listOf(800L), sleeper.delays)
    }

    @Test
    fun `tap action can tap an element-relative ratio`() {
        val driver = RecordingWebDriverAdapter()
        val executor = TapActionExecutor(driver)
        val action = ActionDefinition(
            id = "tap-eye-icon",
            keyword = "tap",
            locator = LocatorDefinition(
                strategy = "id",
                value = "password_field",
            ),
            args = mapOf(
                "elementXRatio" to objectMapper.valueToTree(0.9),
                "elementYRatio" to objectMapper.valueToTree(0.5),
                "settleMs" to objectMapper.valueToTree(0),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=password_field:null", "tap:element-1:0.9:0.5"), driver.calls)
    }

    @Test
    fun `tap action can tap viewport coordinates by ratio`() {
        val driver = RecordingWebDriverAdapter()
        val sleeper = RecordingSleeper()
        val executor = TapActionExecutor(driver, sleeper)
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
        assertEquals(listOf(800L), sleeper.delays)
    }

    @Test
    fun `tap visual template action taps matched template point by viewport ratio`() {
        val template = kotlin.io.path.createTempFile(suffix = ".png")
        Files.write(template, byteArrayOf(1, 2, 3))
        val driver = RecordingWebDriverAdapter(
            screenshotData = pngBytes(width = 100, height = 200),
        )
        val matcher = object : VisualTemplateMatcher {
            override fun find(
                screenshot: ByteArray,
                template: ByteArray,
                targetName: String,
                threshold: Double,
                scales: List<Double>,
                roi: Region?,
            ): MatchResult {
                assertEquals("tap-template", targetName)
                assertEquals(0.9, threshold)
                assertEquals(listOf(1.0), scales)
                return MatchResult("tap-template", Region(10, 20, 30, 40), 0.95, 1.0, 0)
            }
        }
        val sleeper = RecordingSleeper()
        val executor = TapVisualTemplateActionExecutor(driver, matcher, sleeper)
        val action = ActionDefinition(
            id = "tap-template",
            keyword = "tapVisualTemplate",
            args = mapOf(
                "template" to objectMapper.valueToTree(template.toString()),
                "threshold" to objectMapper.valueToTree(0.9),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("screenshot:session-1", "tapViewport:session-1:0.25:0.2"), driver.calls)
        assertEquals(listOf(800L), sleeper.delays)
        Files.deleteIfExists(template)
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
    fun `input action converts numeric scalar values to text`() {
        val driver = RecordingWebDriverAdapter()
        val executor = InputActionExecutor(driver)
        val action = ActionDefinition(
            id = "input-phone",
            keyword = "input",
            locator = LocatorDefinition(
                strategy = "id",
                value = "phone_input",
            ),
            value = objectMapper.valueToTree(13000000000),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=phone_input:null", "input:element-1:13000000000:true"), driver.calls)
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
    fun `screen recording actions start stop and save recording path`() {
        val driver = RecordingWebDriverAdapter()
        val sink = RecordingPlanResourceSink()
        val context = context(currentCaseId = "case-1")
        val start = StartScreenRecordingActionExecutor(driver)
        val stop = StopScreenRecordingActionExecutor(driver, sink)

        val startResult = start.execute(
            ActionDefinition(
                id = "start-toast-recording",
                keyword = "startScreenRecording",
                args = mapOf("timeLimitMs" to objectMapper.valueToTree(6000)),
            ),
            context,
        )
        val stopResult = stop.execute(
            ActionDefinition(
                id = "stop-toast-recording",
                keyword = "stopScreenRecording",
                resourceId = "toast-recording",
                args = mapOf("saveAs" to objectMapper.valueToTree("toastVideo")),
            ),
            context,
        )

        assertEquals(ExecutionStatus.PASSED, startResult.status)
        assertEquals(ExecutionStatus.PASSED, stopResult.status)
        assertEquals(listOf("startRecording:session-1:6000", "stopRecording:session-1"), driver.calls)
        assertEquals("video", sink.resources.single().type)
        assertEquals("explicit_screen_recording", sink.resources.single().purpose)
        assertEquals("/tmp/toast-recording.mp4", context.variables.get("case", "toastVideo", "_:case-1")?.asText())
    }

    @Test
    fun `screen recording text assertion extracts frames and stores matched frame`() {
        val frame = kotlin.io.path.createTempFile(suffix = ".png")
        java.nio.file.Files.write(frame, byteArrayOf(9, 8, 7))
        val extractor = RecordingFrameExtractor(listOf(frame))
        val selector = RecordingCandidateSelector(listOf(frame))
        val recognizer = RecordingTextRecognizer(mapOf(frame to listOf("提交成功")))
        val sink = RecordingPlanResourceSink()
        val action = ActionDefinition(
            id = "assert-toast",
            keyword = "assertScreenRecordingTextRegexMatch",
            resourceId = "toast-match",
            value = objectMapper.valueToTree("提交成功"),
            args = mapOf(
                "source" to objectMapper.valueToTree(frame.toString()),
            ),
        )

        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizer = recognizer,
            sink = sink,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("extract:${frame.fileName}:5.0:40"), extractor.calls)
        assertEquals(listOf("candidates:1:5:0.01:visual-diff"), selector.calls)
        assertEquals("screen_recording_text_match_frame", sink.resources.single().purpose)
        java.nio.file.Files.deleteIfExists(frame)
    }

    @Test
    fun `screen recording text assertion crops roi before recognizing text`() {
        val frame = kotlin.io.path.createTempFile(suffix = ".png")
        ImageIO.write(BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB), "png", frame.toFile())
        val extractor = RecordingFrameExtractor(listOf(frame))
        val selector = PassThroughCandidateSelector
        val recognizedSizes = mutableListOf<Pair<Int, Int>>()
        val recognizer = object : VisualTextRecognizer {
            override fun recognize(imagePath: Path): List<String> {
                val image = ImageIO.read(imagePath.toFile())
                recognizedSizes += image.width to image.height
                return listOf("提交成功")
            }
        }
        val sink = RecordingPlanResourceSink()
        val action = ActionDefinition(
            id = "assert-toast",
            keyword = "assertScreenRecordingTextRegexMatch",
            resourceId = "toast-match",
            value = objectMapper.valueToTree("提交成功"),
            args = mapOf(
                "source" to objectMapper.valueToTree(frame.toString()),
                "roi" to objectMapper.valueToTree(
                    mapOf(
                        "x" to 0.25,
                        "y" to 0.50,
                        "width" to 0.50,
                        "height" to 0.25,
                    ),
                ),
            ),
        )

        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizer = recognizer,
            sink = sink,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(50 to 50), recognizedSizes)
        assertEquals("screen_recording_text_match_frame", sink.resources.single().purpose)
        java.nio.file.Files.deleteIfExists(frame)
    }

    @Test
    fun `screen recording text assertion only ocrs selected candidate frames`() {
        val frames = List(4) { kotlin.io.path.createTempFile(suffix = ".png") }
        frames.forEachIndexed { index, frame ->
            java.nio.file.Files.write(frame, byteArrayOf(index.toByte()))
        }
        val extractor = RecordingFrameExtractor(frames)
        val selector = RecordingCandidateSelector(listOf(frames[2]))
        val recognizer = RecordingTextRecognizer(mapOf(frames[2] to listOf("提交成功")))
        val sink = RecordingPlanResourceSink()
        val action = ActionDefinition(
            id = "assert-toast",
            keyword = "assertScreenRecordingTextRegexMatch",
            resourceId = "toast-match",
            value = objectMapper.valueToTree("提交成功"),
            args = mapOf(
                "source" to objectMapper.valueToTree(frames.first().toString()),
                "candidateMaxFrames" to objectMapper.valueToTree(2),
                "candidateStrategy" to objectMapper.valueToTree("visual-diff"),
                "visualDifferenceThreshold" to objectMapper.valueToTree(0.05),
            ),
        )

        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizer = recognizer,
            sink = sink,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("candidates:4:2:0.05:visual-diff"), selector.calls)
        assertEquals(listOf(frames[2]), recognizer.seen)
        frames.forEach { java.nio.file.Files.deleteIfExists(it) }
    }

    @Test
    fun `screen recording text assertion passes candidate strategy to selector`() {
        val frames = List(3) { kotlin.io.path.createTempFile(suffix = ".png") }
        frames.forEachIndexed { index, frame ->
            java.nio.file.Files.write(frame, byteArrayOf(index.toByte()))
        }
        val extractor = RecordingFrameExtractor(frames)
        val selector = RecordingCandidateSelector(listOf(frames[1]))
        val recognizer = RecordingTextRecognizer(mapOf(frames[1] to listOf("提交成功")))
        val sink = RecordingPlanResourceSink()
        val action = ActionDefinition(
            id = "assert-toast",
            keyword = "assertScreenRecordingTextRegexMatch",
            resourceId = "toast-match",
            value = objectMapper.valueToTree("提交成功"),
            args = mapOf(
                "source" to objectMapper.valueToTree(frames.first().toString()),
                "candidateMaxFrames" to objectMapper.valueToTree(2),
                "candidateStrategy" to objectMapper.valueToTree("uniform"),
            ),
        )

        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizer = recognizer,
            sink = sink,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("candidates:3:2:0.01:uniform"), selector.calls)
        frames.forEach { java.nio.file.Files.deleteIfExists(it) }
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
        assertEquals(listOf("restart:session-1:com.example.demo:null"), driver.calls)
    }

    @Test
    fun `restart app action passes explicit wait to driver`() {
        val driver = RecordingWebDriverAdapter()
        val executor = RestartAppActionExecutor(driver)
        val action = ActionDefinition(
            id = "restart-app",
            keyword = "restartApp",
            args = mapOf("appId" to objectMapper.valueToTree("com.example.demo")),
            wait = WaitDefinition(timeoutMs = 12_000, intervalMs = 250),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("restart:session-1:com.example.demo:DriverWaitOptions(timeoutMs=12000, intervalMs=250)"), driver.calls)
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
                "find:session-1:id=nickname:1",
                "attr:element-1:text",
                "find:session-1:id=nickname:1",
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
                "find:session-1:id=nickname:1",
                "attr:element-1:label",
                "find:session-1:id=nickname:1",
                "attr:element-1:label",
            ),
            driver.calls,
        )
    }

    @Test
    fun `assert element exists action passes when element can be found`() {
        val driver = RecordingWebDriverAdapter()
        val executor = AssertElementExistsActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-add-button",
            keyword = "assertElementExists",
            locator = LocatorDefinition(
                strategy = "id",
                value = "add_button",
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=add_button:null"), driver.calls)
    }

    @Test
    fun `assert element exists action polls until element is found`() {
        val driver = RecordingWebDriverAdapter(findFailuresBeforeSuccess = 1)
        val sleeper = RecordingSleeper()
        val executor = AssertElementExistsActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "assert-add-button",
            keyword = "assertElementExists",
            locator = LocatorDefinition(
                strategy = "id",
                value = "add_button",
            ),
            wait = WaitDefinition(timeoutMs = 1_000, intervalMs = 100),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(100L), sleeper.delays)
        assertEquals(
            listOf(
                "find:session-1:id=add_button:1",
                "find:session-1:id=add_button:1",
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

        override fun accept(screenshot: ExplicitScreenshot): CapturedPlanResource? {
            screenshots += screenshot
            return null
        }
    }

    private class RecordingPlanResourceSink : PlanResourceSink {
        val resources = mutableListOf<ExplicitPlanResource>()

        override fun accept(resource: ExplicitPlanResource): CapturedPlanResource {
            resources += resource
            return CapturedPlanResource(
                resourceId = resource.resourceId ?: "recording",
                type = resource.type,
                purpose = resource.purpose,
                actionId = resource.actionId,
                name = resource.name,
                localPath = Path.of("/tmp/${resource.resourceId ?: "recording"}.mp4"),
                fileName = "${resource.resourceId ?: "recording"}.mp4",
                contentType = resource.contentType,
                sizeBytes = resource.bytes.size.toLong(),
                capturedAt = "2026-06-17T00:00:00Z",
            )
        }
    }

    private class RecordingFrameExtractor(
        private val frames: List<Path>,
    ) : VideoFrameExtractor {
        val calls = mutableListOf<String>()

        override fun extract(
            videoPath: Path,
            outputDirectory: Path,
            framesPerSecond: Double,
            maxFrames: Int,
        ): List<Path> {
            calls += "extract:${videoPath.fileName}:$framesPerSecond:$maxFrames"
            return frames
        }
    }

    private class RecordingTextRecognizer(
        private val textsByFrame: Map<Path, List<String>>,
    ) : VisualTextRecognizer {
        val seen = mutableListOf<Path>()

        override fun recognize(imagePath: Path): List<String> {
            seen.add(imagePath)
            return textsByFrame[imagePath].orEmpty()
        }
    }

    private object PassThroughCandidateSelector : VisualFrameCandidateSelector {
        override fun selectCandidates(
            frames: List<Path>,
            maxCandidates: Int,
            differenceThreshold: Double,
            strategy: FrameCandidateStrategy,
        ): List<Path> = frames
    }

    private class RecordingCandidateSelector(
        private val candidates: List<Path>,
    ) : VisualFrameCandidateSelector {
        val calls = mutableListOf<String>()

        override fun selectCandidates(
            frames: List<Path>,
            maxCandidates: Int,
            differenceThreshold: Double,
            strategy: FrameCandidateStrategy,
        ): List<Path> {
            calls += "candidates:${frames.size}:$maxCandidates:$differenceThreshold:${strategy.externalName}"
            return candidates
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
        private val screenshotData: ByteArray = byteArrayOf(1, 2, 3),
        private val findFailuresBeforeSuccess: Int = 0,
    ) : WebDriverAdapter {
        val calls = mutableListOf<String>()
        private val attributeReadCounts = mutableMapOf<String, Int>()
        private var findCount = 0
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
            findCount += 1
            if (findCount <= findFailuresBeforeSuccess) {
                error("element not found")
            }
            return DriverElement("element-1")
        }

        override fun tap(
            sessionId: String,
            element: DriverElement,
            xRatio: Double,
            yRatio: Double,
        ) {
            calls += "tap:${element.elementId}:$xRatio:$yRatio"
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
            wait: DriverWaitOptions?,
        ) {
            calls += "restart:$sessionId:$appId:$wait"
        }

        override fun takeScreenshot(sessionId: String): ScreenshotData {
            calls += "screenshot:$sessionId"
            return ScreenshotData(screenshotData)
        }

        override fun startScreenRecording(
            sessionId: String,
            options: ScreenRecordingOptions,
        ) {
            calls += "startRecording:$sessionId:${options.timeLimitMs}"
        }

        override fun stopScreenRecording(sessionId: String): ScreenRecordingData {
            calls += "stopRecording:$sessionId"
            return ScreenRecordingData(byteArrayOf(4, 5, 6))
        }

        override fun isSessionHealthy(sessionId: String): Boolean {
            return true
        }
    }

    private fun pngBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
