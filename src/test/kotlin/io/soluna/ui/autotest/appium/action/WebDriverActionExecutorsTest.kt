package io.soluna.ui.autotest.appium.action

import com.fasterxml.jackson.databind.ObjectMapper
import io.soluna.ui.autotest.appium.driver.DriverElement
import io.soluna.ui.autotest.appium.driver.DriverSession
import io.soluna.ui.autotest.appium.driver.DriverWaitOptions
import io.soluna.ui.autotest.appium.driver.ElementRect
import io.soluna.ui.autotest.appium.driver.ScreenRecordingData
import io.soluna.ui.autotest.appium.driver.ScreenRecordingOptions
import io.soluna.ui.autotest.appium.driver.ScreenshotData
import io.soluna.ui.autotest.appium.driver.StartSessionRequest
import io.soluna.ui.autotest.appium.driver.WebDriverAdapter
import io.soluna.ui.autotest.appium.ext.AppLookupResult
import io.soluna.ui.autotest.appium.ext.CommandExecuteRequest
import io.soluna.ui.autotest.appium.ext.CommandExecuteResult
import io.soluna.ui.autotest.appium.ext.CreateLogSessionRequest
import io.soluna.ui.autotest.appium.ext.CreateLogSessionResult
import io.soluna.ui.autotest.appium.ext.DeleteLogSessionRequest
import io.soluna.ui.autotest.appium.ext.DeleteLogSessionResult
import io.soluna.ui.autotest.appium.ext.DeviceLookupResult
import io.soluna.ui.autotest.appium.ext.ListDevicesResult
import io.soluna.ui.autotest.appium.ext.LogLineSource
import io.soluna.ui.autotest.appium.ext.LogSessionSnapshot
import io.soluna.ui.autotest.appium.ext.LogSessionStatus
import io.soluna.ui.autotest.appium.ext.Platform
import io.soluna.ui.autotest.appium.ext.ReadLogSessionRequest
import io.soluna.ui.autotest.appium.ext.ReadLogSessionResult
import io.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import io.soluna.ui.autotest.appium.ext.UnifiedLogEntry
import io.soluna.ui.autotest.appium.ext.WdaBundleLookupResult
import io.soluna.ui.autotest.artifact.CapturedPlanResource
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import io.soluna.ui.autotest.core.execution.DefaultActionExecutorRegistry
import io.soluna.ui.autotest.core.execution.ExecutionContext
import io.soluna.ui.autotest.core.execution.ExecutionStatus
import io.soluna.ui.autotest.core.execution.Sleeper
import io.soluna.ui.autotest.core.model.ActionDefinition
import io.soluna.ui.autotest.core.model.LocatorDefinition
import io.soluna.ui.autotest.core.model.PlanDefinition
import io.soluna.ui.autotest.core.model.WaitDefinition
import io.soluna.ui.autotest.extension.applog.AppLogAssertion
import io.soluna.ui.autotest.extension.applog.AppLogAssertionInput
import io.soluna.ui.autotest.extension.applog.AppLogAssertionRegistry
import io.soluna.ui.autotest.extension.applog.AppLogAssertionResult
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.TimeoutException

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
    fun `tap action can ignore missing optional firmware prompt element`() {
        val driver = RecordingWebDriverAdapter(throwNoSuchElement = true)
        val sleeper = RecordingSleeper()
        val executor = TapActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "dismiss-firmware-prompt-if-present",
            keyword = "tap",
            locator = LocatorDefinition(
                strategy = "id",
                value = "ignore_button",
            ),
            args = mapOf(
                "ignoreMissingElement" to objectMapper.valueToTree(true),
                "ignoreMissingElementReason" to objectMapper.valueToTree("optionalFirmwareUpgradePrompt"),
            ),
            wait = WaitDefinition(timeoutMs = 5000, intervalMs = 500),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("find:session-1:id=ignore_button:5000"),
            driver.calls,
        )
        assertEquals(emptyList(), sleeper.delays)
    }

    @Test
    fun `tap action can ignore timed out optional firmware prompt element`() {
        val driver = RecordingWebDriverAdapter(throwTimeoutException = true)
        val sleeper = RecordingSleeper()
        val executor = TapActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "dismiss-firmware-prompt-if-present",
            keyword = "tap",
            locator = LocatorDefinition(
                strategy = "id",
                value = "ignore_button",
            ),
            args = mapOf(
                "ignoreMissingElement" to objectMapper.valueToTree(true),
                "ignoreMissingElementReason" to objectMapper.valueToTree("optionalFirmwareUpgradePrompt"),
            ),
            wait = WaitDefinition(timeoutMs = 5000, intervalMs = 500),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("find:session-1:id=ignore_button:5000"),
            driver.calls,
        )
        assertEquals(emptyList(), sleeper.delays)
    }

    @Test
    fun `long press action finds element and presses it`() {
        val driver = RecordingWebDriverAdapter()
        val sleeper = RecordingSleeper()
        val executor = LongPressActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "press-device",
            keyword = "longPress",
            locator = LocatorDefinition(
                strategy = "id",
                value = "device_card",
            ),
            args = mapOf(
                "durationMs" to objectMapper.valueToTree(1200),
                "elementXRatio" to objectMapper.valueToTree(0.4),
                "elementYRatio" to objectMapper.valueToTree(0.6),
            ),
            wait = WaitDefinition(timeoutMs = 3000, intervalMs = 100),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("find:session-1:id=device_card:3000", "longPress:element-1:1200:0.4:0.6"),
            driver.calls,
        )
        assertEquals(listOf(800L), sleeper.delays)
    }

    @Test
    fun `long press action can press viewport coordinates by ratio`() {
        val driver = RecordingWebDriverAdapter()
        val sleeper = RecordingSleeper()
        val executor = LongPressActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "press-backdrop",
            keyword = "longPress",
            args = mapOf(
                "durationMs" to objectMapper.valueToTree(900),
                "xRatio" to objectMapper.valueToTree(0.5),
                "yRatio" to objectMapper.valueToTree(0.3),
                "settleMs" to objectMapper.valueToTree(0),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("longPressViewport:session-1:900:0.5:0.3"), driver.calls)
        assertEquals(emptyList(), sleeper.delays)
    }

    @Test
    fun `swipe action can swipe viewport coordinates by ratio`() {
        val driver = RecordingWebDriverAdapter()
        val sleeper = RecordingSleeper()
        val executor = SwipeActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "scroll-down",
            keyword = "swipe",
            args = mapOf(
                "startXRatio" to objectMapper.valueToTree(0.5),
                "startYRatio" to objectMapper.valueToTree(0.8),
                "endXRatio" to objectMapper.valueToTree(0.5),
                "endYRatio" to objectMapper.valueToTree(0.2),
                "durationMs" to objectMapper.valueToTree(600),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("swipeViewport:session-1:600:0.5:0.8:0.5:0.2"), driver.calls)
        assertEquals(listOf(800L), sleeper.delays)
    }

    @Test
    fun `swipe action can swipe inside an element`() {
        val driver = RecordingWebDriverAdapter()
        val sleeper = RecordingSleeper()
        val executor = SwipeActionExecutor(driver, sleeper)
        val action = ActionDefinition(
            id = "scroll-list",
            keyword = "swipe",
            locator = LocatorDefinition(
                strategy = "id",
                value = "list",
            ),
            args = mapOf(
                "startElementXRatio" to objectMapper.valueToTree(0.5),
                "startElementYRatio" to objectMapper.valueToTree(0.9),
                "endElementXRatio" to objectMapper.valueToTree(0.5),
                "endElementYRatio" to objectMapper.valueToTree(0.1),
                "durationMs" to objectMapper.valueToTree(500),
                "settleMs" to objectMapper.valueToTree(0),
            ),
            wait = WaitDefinition(timeoutMs = 3000, intervalMs = 100),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("find:session-1:id=list:3000", "swipe:element-1:500:0.5:0.9:0.5:0.1"),
            driver.calls,
        )
        assertEquals(emptyList(), sleeper.delays)
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
    fun `save element rect action can store full-width roi from element y and height`() {
        val driver = RecordingWebDriverAdapter(
            elementRect = ElementRect(
                x = 40,
                y = 20,
                width = 30,
                height = 40,
                viewportWidth = 100,
                viewportHeight = 200,
            ),
        )
        val executor = SaveElementRectActionExecutor(driver)
        val context = context(currentCaseId = "case-1")
        val action = ActionDefinition(
            id = "save-title-bar-roi",
            keyword = "saveElementRect",
            locator = LocatorDefinition(
                strategy = "id",
                value = "title",
            ),
            args = mapOf(
                "saveAs" to objectMapper.valueToTree("titleBarRoi"),
                "asRoi" to objectMapper.valueToTree(true),
                "fullWidth" to objectMapper.valueToTree(true),
            ),
        )

        val result = executor.execute(action, context)
        val roi = context.variables.get("case", "titleBarRoi", "_:case-1")

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("find:session-1:id=title:null", "rect:element-1"), driver.calls)
        assertEquals(0.0, roi?.get("x")?.asDouble())
        assertEquals(0.1, roi?.get("y")?.asDouble())
        assertEquals(1.0, roi?.get("width")?.asDouble())
        assertEquals(0.2, roi?.get("height")?.asDouble())
    }

    @Test
    fun `tap visual template action can retry and use runtime roi variable`() {
        val template = kotlin.io.path.createTempFile(suffix = ".png")
        Files.write(template, byteArrayOf(1, 2, 3))
        val driver = RecordingWebDriverAdapter(
            screenshotData = pngBytes(width = 100, height = 200),
        )
        val context = context(currentCaseId = "case-1")
        context.variables.set(
            scope = "case",
            name = "titleBarRoi",
            value = objectMapper.createObjectNode().also { roi ->
                roi.put("x", 0.0)
                roi.put("y", 0.1)
                roi.put("width", 1.0)
                roi.put("height", 0.2)
            },
            caseId = "_:case-1",
        )
        var matchAttempts = 0
        val matcher = object : VisualTemplateMatcher {
            override fun find(
                screenshot: ByteArray,
                template: ByteArray,
                targetName: String,
                threshold: Double,
                scales: List<Double>,
                roi: Region?,
            ): MatchResult? {
                assertEquals(Region(0, 20, 100, 40), roi)
                matchAttempts += 1
                return if (matchAttempts == 1) {
                    null
                } else {
                    MatchResult("tap-template", Region(80, 25, 10, 10), 0.95, 1.0, 0)
                }
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
                "roi" to objectMapper.valueToTree("@{case.titleBarRoi}"),
            ),
            wait = WaitDefinition(timeoutMs = 1000, intervalMs = 250),
        )

        val result = executor.execute(action, context)

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(2, matchAttempts)
        assertEquals(
            listOf(
                "screenshot:session-1",
                "screenshot:session-1",
                "tapViewport:session-1:0.85:0.15",
            ),
            driver.calls,
        )
        assertEquals(listOf(250L, 800L), sleeper.delays)
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
    fun `screenshot action can save captured screenshot path`() {
        val driver = RecordingWebDriverAdapter()
        val sink = SavingScreenshotSink(Path.of("/tmp/home.png"))
        val executor = ScreenshotActionExecutor(driver, sink)
        val context = context(currentCaseId = "case-1")
        val action = ActionDefinition(
            id = "capture-home",
            keyword = "screenshot",
            resourceId = "home-after-login",
            args = mapOf(
                "saveAs" to objectMapper.valueToTree("homeScreenshot"),
            ),
        )

        val result = executor.execute(action, context)

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals("/tmp/home.png", context.variables.get("case", "homeScreenshot", "_:case-1")?.asText())
        assertEquals("/tmp/home.png", context.variables.get("case", "lastScreenshot", "_:case-1")?.asText())
    }

    @Test
    fun `screenshot action can capture an element screenshot`() {
        val driver = RecordingWebDriverAdapter(elementScreenshotData = byteArrayOf(7, 8, 9))
        val sink = RecordingScreenshotSink()
        val executor = ScreenshotActionExecutor(driver, sink)
        val action = ActionDefinition(
            id = "capture-selected-mode",
            keyword = "screenshot",
            resourceId = "selected-mode",
            locator = LocatorDefinition(
                strategy = "xpath",
                value = "//*[@name='mode']",
            ),
            wait = WaitDefinition(timeoutMs = 3000, intervalMs = 200),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf(
                "find:session-1:xpath=//*[@name='mode']:3000",
                "elementScreenshot:element-1",
            ),
            driver.calls,
        )
        assertContentEquals(byteArrayOf(7, 8, 9), sink.screenshots.single().data.bytes)
    }

    @Test
    fun `image color ratio assertion passes when named color ratio is high enough`() {
        val image = kotlin.io.path.createTempFile(suffix = ".png")
        Files.write(
            image,
            pngBytes(width = 20, height = 20) { x, y ->
                if (x in 8..11 && y in 8..11) Color.BLUE.rgb else Color.BLACK.rgb
            },
        )
        val executor = AssertImageColorRatioActionExecutor()
        val action = ActionDefinition(
            id = "assert-blue-dot",
            keyword = "assertImageColorRatio",
            args = mapOf(
                "source" to objectMapper.valueToTree(image.toString()),
                "color" to objectMapper.valueToTree("blue"),
                "minRatio" to objectMapper.valueToTree(0.03),
                "minPixels" to objectMapper.valueToTree(10),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        Files.deleteIfExists(image)
    }

    @Test
    fun `image color ratio assertion fails when named color ratio is too low`() {
        val image = kotlin.io.path.createTempFile(suffix = ".png")
        Files.write(image, pngBytes(width = 10, height = 10) { _, _ -> Color.BLACK.rgb })
        val executor = AssertImageColorRatioActionExecutor()
        val action = ActionDefinition(
            id = "assert-blue-dot",
            keyword = "assertImageColorRatio",
            args = mapOf(
                "source" to objectMapper.valueToTree(image.toString()),
                "color" to objectMapper.valueToTree("blue"),
                "minRatio" to objectMapper.valueToTree(0.01),
            ),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.FAILED, result.status)
        Files.deleteIfExists(image)
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
    fun `image text assertion recognizes screenshot text`() {
        val image = kotlin.io.path.createTempFile(suffix = ".png")
        java.nio.file.Files.write(image, byteArrayOf(9, 8, 7))
        val recognizer = RecordingTextRecognizer(mapOf(image to listOf("产品说明书", "UGREEN HiTune T8")))
        val action = ActionDefinition(
            id = "assert-manual-title",
            keyword = "assertImageTextRegexMatch",
            args = mapOf(
                "source" to objectMapper.valueToTree(image.toString()),
                "pattern" to objectMapper.valueToTree("产品说明书(?s).*UGREEN HiTune T8"),
            ),
        )

        val result = AssertImageTextRegexMatchActionExecutor(
            textRecognizer = recognizer,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(image), recognizer.seen)
        java.nio.file.Files.deleteIfExists(image)
    }

    @Test
    fun `image text assertion crops roi before recognizing text`() {
        val image = kotlin.io.path.createTempFile(suffix = ".png")
        ImageIO.write(BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB), "png", image.toFile())
        val recognizedSizes = mutableListOf<Pair<Int, Int>>()
        val recognizer = object : VisualTextRecognizer {
            override fun recognize(imagePath: Path): List<String> {
                val ocrImage = ImageIO.read(imagePath.toFile())
                recognizedSizes += ocrImage.width to ocrImage.height
                return listOf("Model: WS222")
            }
        }
        val action = ActionDefinition(
            id = "assert-manual-model",
            keyword = "assertImageTextRegexMatch",
            args = mapOf(
                "source" to objectMapper.valueToTree(image.toString()),
                "pattern" to objectMapper.valueToTree("WS222"),
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

        val result = AssertImageTextRegexMatchActionExecutor(
            textRecognizer = recognizer,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(50 to 50), recognizedSizes)
        java.nio.file.Files.deleteIfExists(image)
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
    fun `screen recording text assertion continues after candidate recognizer failure`() {
        val frames = List(2) { kotlin.io.path.createTempFile(suffix = ".png") }
        frames.forEachIndexed { index, frame ->
            java.nio.file.Files.write(frame, byteArrayOf(index.toByte()))
        }
        val extractor = RecordingFrameExtractor(frames)
        val selector = RecordingCandidateSelector(frames)
        val seen = mutableListOf<Path>()
        val recognizer = object : VisualTextRecognizer {
            override fun recognize(imagePath: Path): List<String> {
                seen.add(imagePath)
                if (imagePath == frames[0]) {
                    throw IllegalStateException("stream failed", java.io.InterruptedIOException("timeout"))
                }
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
                "source" to objectMapper.valueToTree(frames.first().toString()),
            ),
        )

        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizer = recognizer,
            sink = sink,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(frames, seen)
        assertEquals("screen_recording_text_match_frame", sink.resources.single().purpose)
        frames.forEach { java.nio.file.Files.deleteIfExists(it) }
    }

    @Test
    fun `screen recording text assertion recognizes multimodal candidates concurrently`() {
        val frames = List(2) { kotlin.io.path.createTempFile(suffix = ".png") }
        frames.forEachIndexed { index, frame ->
            java.nio.file.Files.write(frame, byteArrayOf(index.toByte()))
        }
        val extractor = RecordingFrameExtractor(frames)
        val selector = RecordingCandidateSelector(frames)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val recognizer = object : VisualTextRecognizer {
            override fun recognize(imagePath: Path): List<String> {
                if (imagePath == frames[0]) {
                    firstStarted.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                    return emptyList()
                }
                assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
                releaseFirst.countDown()
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
                "source" to objectMapper.valueToTree(frames.first().toString()),
                "recognizer" to objectMapper.valueToTree("multimodal"),
            ),
        )

        val startedAt = System.nanoTime()
        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizer = recognizer,
            sink = sink,
            multimodalCandidateParallelism = 2,
        ).execute(action, context())
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertTrue(elapsedMs < 1_500, "expected concurrent recognition, elapsed=${elapsedMs}ms")
        assertEquals("screen_recording_text_match_frame", sink.resources.single().purpose)
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
    fun `screen recording text assertion can select multimodal recognizer`() {
        val frame = kotlin.io.path.createTempFile(suffix = ".png")
        java.nio.file.Files.write(frame, byteArrayOf(1, 2, 3))
        val extractor = RecordingFrameExtractor(listOf(frame))
        val selector = RecordingCandidateSelector(listOf(frame))
        val recognizer = RecordingTextRecognizer(mapOf(frame to listOf("问题描述不能超过200个字符")))
        val provider = RecordingTextRecognizerProvider(recognizer)
        val sink = RecordingPlanResourceSink()
        val action = ActionDefinition(
            id = "assert-toast",
            keyword = "assertScreenRecordingTextRegexMatch",
            resourceId = "toast-match",
            value = objectMapper.valueToTree("问题描述.*200"),
            args = mapOf(
                "source" to objectMapper.valueToTree(frame.toString()),
                "recognizer" to objectMapper.valueToTree("multimodal"),
            ),
        )

        val result = AssertScreenRecordingTextRegexMatchActionExecutor(
            frameExtractor = extractor,
            candidateSelector = selector,
            textRecognizerProvider = provider,
            sink = sink,
        ).execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf(ScreenRecordingTextRecognizer.MULTIMODAL), provider.requested)
        assertEquals(listOf(frame), recognizer.seen)
        java.nio.file.Files.deleteIfExists(frame)
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
    fun `clear app data action delegates app data reset command`() {
        val driver = RecordingWebDriverAdapter()
        val executor = ClearAppDataActionExecutor(driver)
        val action = ActionDefinition(
            id = "clear-app-data",
            keyword = "clearAppData",
            args = mapOf("appId" to objectMapper.valueToTree("com.example.demo")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("clearData:session-1:com.example.demo:null"), driver.calls)
    }

    @Test
    fun `clear app data action passes explicit wait to driver`() {
        val driver = RecordingWebDriverAdapter()
        val executor = ClearAppDataActionExecutor(driver)
        val action = ActionDefinition(
            id = "clear-app-data",
            keyword = "clearAppData",
            args = mapOf("appId" to objectMapper.valueToTree("com.example.demo")),
            wait = WaitDefinition(timeoutMs = 20_000, intervalMs = 500),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(listOf("clearData:session-1:com.example.demo:DriverWaitOptions(timeoutMs=20000, intervalMs=500)"), driver.calls)
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
    fun `assert element attr equals action skips unsupported attribute candidates`() {
        val driver = RecordingWebDriverAdapter(
            attributes = mapOf("text" to "SolunaTester"),
            throwingAttributes = setOf("value"),
        )
        val executor = AssertElementAttrEqualsActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-nickname",
            keyword = "assertElementAttrEquals",
            locator = LocatorDefinition(
                strategy = "id",
                value = "nickname",
            ),
            value = objectMapper.valueToTree("SolunaTester"),
            args = mapOf("attr" to objectMapper.valueToTree("value/text")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("find:session-1:id=nickname:null", "attr:element-1:value", "attr:element-1:checked", "attr:element-1:text"),
            driver.calls,
        )
    }

    @Test
    fun `assert element attr equals action maps missing value to checked switch state`() {
        val driver = RecordingWebDriverAdapter(
            attributes = mapOf("checked" to "true"),
            throwingAttributes = setOf("value"),
        )
        val executor = AssertElementAttrEqualsActionExecutor(driver)
        val action = ActionDefinition(
            id = "assert-switch-on",
            keyword = "assertElementAttrEquals",
            locator = LocatorDefinition(
                strategy = "xpath",
                value = "//android.widget.Switch[1]",
            ),
            value = objectMapper.valueToTree("1"),
            args = mapOf("attr" to objectMapper.valueToTree("value")),
        )

        val result = executor.execute(action, context())

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(
            listOf("find:session-1:xpath=//android.widget.Switch[1]:null", "attr:element-1:value", "attr:element-1:checked"),
            driver.calls,
        )
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
    fun `app log capture start and end write filtered log resource and variables`() {
        val extClient = RecordingSolunaExtClient()
        extClient.readResults += ReadLogSessionResult(
            session = logSessionSnapshot(nextSeq = 1),
            cursor = 0,
            nextCursor = 1,
            cursorAdjusted = false,
            entries = listOf(
                UnifiedLogEntry(
                    seq = 0,
                    ts = "2026-06-21T00:00:00Z",
                    platform = Platform.IOS,
                    udid = "ios-1",
                    source = LogLineSource.STDOUT,
                    level = "notice",
                    process = "UgreenAudio",
                    pid = 42,
                    message = "BLE command reported",
                    raw = "raw BLE command reported",
                ),
            ),
        )
        extClient.readResults += ReadLogSessionResult(
            session = logSessionSnapshot(nextSeq = 1),
            cursor = 1,
            nextCursor = 1,
            cursorAdjusted = false,
            entries = emptyList(),
        )
        val sink = RecordingPlanResourceSink()
        val context = context(currentCaseId = "case-1")
        val filter = objectMapper.createObjectNode().also { root ->
            root.put("messageContains", "BLE")
            root.set<com.fasterxml.jackson.databind.JsonNode>(
                "ios",
                objectMapper.createObjectNode().put("processRegex", "UgreenAudio"),
            )
            root.set<com.fasterxml.jackson.databind.JsonNode>(
                "android",
                objectMapper.createObjectNode().put("tag", "BluetoothCmd"),
            )
        }

        val startResult = CaptureAppLogStartActionExecutor(extClient, "ios-1", "ios").execute(
            ActionDefinition(
                id = "start-log",
                keyword = "captureAppLogStart",
                args = mapOf(
                    "saveAs" to objectMapper.valueToTree("appLogWindow"),
                    "filter" to filter,
                ),
            ),
            context,
        )
        val endResult = CaptureAppLogEndActionExecutor(extClient, sink).execute(
            ActionDefinition(
                id = "end-log",
                keyword = "captureAppLogEnd",
                resourceId = "app-log",
                args = mapOf(
                    "source" to objectMapper.valueToTree("@{case.appLogWindow}"),
                    "saveAs" to objectMapper.valueToTree("appLogFile"),
                ),
            ),
            context,
        )

        assertEquals(ExecutionStatus.PASSED, startResult.status)
        assertEquals(ExecutionStatus.PASSED, endResult.status)
        assertEquals("ios-1", extClient.createRequests.single().udid)
        assertEquals("UgreenAudio", extClient.createRequests.single().filter?.path("ios")?.path("processRegex")?.asText())
        assertEquals(listOf("session-1"), extClient.deletedSessionIds)
        assertEquals("log", sink.resources.single().type)
        assertEquals("app_log_capture", sink.resources.single().purpose)
        assertTrue(sink.resources.single().bytes.toString(Charsets.UTF_8).contains("BLE command reported"))
        assertEquals("/tmp/app-log.jsonl", context.variables.get("case", "appLogFile", "_:case-1")?.path("path")?.asText())
        assertEquals("/tmp/app-log.jsonl", context.variables.get("case", "lastAppLogFile", "_:case-1")?.path("path")?.asText())
    }

    @Test
    fun `custom app log assertion fails when plugin assertion is missing`() {
        val result = CustomAssertAppLogActionExecutor(EmptyAppLogAssertionRegistry).execute(
            ActionDefinition(
                id = "assert-log",
                keyword = "customAssertAppLog",
                args = mapOf(
                    "plugin" to objectMapper.valueToTree("ugreen-audio"),
                    "assertion" to objectMapper.valueToTree("bluetoothCommandReported"),
                ),
            ),
            context(),
        )

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals("No app log assertion 'bluetoothCommandReported' registered by plugin 'ugreen-audio'", result.error)
    }

    @Test
    fun `custom app log assertion delegates to registered plugin assertion`() {
        val logFile = Files.createTempFile("soluna-app-log-", ".jsonl")
        Files.writeString(logFile, """{"message":"BLE command reported"}""")
        val context = context(currentCaseId = "case-1")
        context.variables.set(
            scope = "case",
            name = "lastAppLogFile",
            value = objectMapper.createObjectNode()
                .put("path", logFile.toString())
                .put("platform", "ios")
                .put("udid", "ios-1"),
            caseId = "_:case-1",
        )
        val assertion = RecordingAppLogAssertion()
        val result = CustomAssertAppLogActionExecutor(
            StaticAppLogAssertionRegistry("ugreen-audio", "bluetoothCommandReported", assertion),
        ).execute(
            ActionDefinition(
                id = "assert-log",
                keyword = "customAssertAppLog",
                args = mapOf(
                    "plugin" to objectMapper.valueToTree("ugreen-audio"),
                    "assertion" to objectMapper.valueToTree("bluetoothCommandReported"),
                    "args" to objectMapper.createObjectNode().put("operation", "customControl"),
                ),
            ),
            context,
        )

        assertEquals(ExecutionStatus.PASSED, result.status)
        assertEquals(logFile, assertion.inputs.single().logFile)
        assertEquals("customControl", assertion.inputs.single().args?.path("operation")?.asText())
        assertEquals("ios", assertion.inputs.single().context.platform)
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

    private class SavingScreenshotSink(
        private val localPath: Path,
    ) : ScreenshotSink {
        val screenshots = mutableListOf<ExplicitScreenshot>()

        override fun accept(screenshot: ExplicitScreenshot): CapturedPlanResource {
            screenshots += screenshot
            return CapturedPlanResource(
                resourceId = screenshot.resourceId ?: screenshot.actionId ?: "screenshot",
                type = "image",
                purpose = "explicit_screenshot",
                actionId = screenshot.actionId,
                name = screenshot.name,
                localPath = localPath,
                fileName = localPath.fileName.toString(),
                contentType = screenshot.data.contentType,
                sizeBytes = screenshot.data.bytes.size.toLong(),
                capturedAt = "2026-06-17T00:00:00Z",
            )
        }
    }

    private inner class RecordingSolunaExtClient : SolunaAppiumExtClient {
        val createRequests = mutableListOf<CreateLogSessionRequest>()
        val readRequests = mutableListOf<ReadLogSessionRequest>()
        val readResults = ArrayDeque<ReadLogSessionResult>()
        val deletedSessionIds = mutableListOf<String>()

        override fun getDevice(udid: String): DeviceLookupResult {
            error("not used")
        }

        override fun listDevices(): ListDevicesResult {
            error("not used")
        }

        override fun getApp(
            udid: String,
            appId: String,
        ): AppLookupResult {
            error("not used")
        }

        override fun getWdaBundle(udid: String): WdaBundleLookupResult {
            error("not used")
        }

        override fun executeCommand(request: CommandExecuteRequest): CommandExecuteResult {
            error("not used")
        }

        override fun createLogSession(request: CreateLogSessionRequest): CreateLogSessionResult {
            createRequests += request
            return CreateLogSessionResult(logSessionSnapshot())
        }

        override fun readLogSession(request: ReadLogSessionRequest): ReadLogSessionResult {
            readRequests += request
            return if (readResults.isEmpty()) {
                ReadLogSessionResult(
                    session = logSessionSnapshot(nextSeq = request.cursor ?: 0),
                    cursor = request.cursor ?: 0,
                    nextCursor = request.cursor ?: 0,
                    cursorAdjusted = false,
                    entries = emptyList(),
                )
            } else {
                readResults.removeFirst()
            }
        }

        override fun deleteLogSession(request: DeleteLogSessionRequest): DeleteLogSessionResult {
            deletedSessionIds += request.sessionId
            return DeleteLogSessionResult(sessionId = request.sessionId, removed = true)
        }
    }

    private object EmptyAppLogAssertionRegistry : AppLogAssertionRegistry {
        override fun find(
            pluginId: String,
            assertionName: String,
        ): AppLogAssertion? {
            return null
        }
    }

    private class StaticAppLogAssertionRegistry(
        private val pluginId: String,
        private val assertionName: String,
        private val assertion: AppLogAssertion,
    ) : AppLogAssertionRegistry {
        override fun find(
            pluginId: String,
            assertionName: String,
        ): AppLogAssertion? {
            return assertion.takeIf {
                this.pluginId == pluginId && this.assertionName == assertionName
            }
        }
    }

    private class RecordingAppLogAssertion : AppLogAssertion {
        val inputs = mutableListOf<AppLogAssertionInput>()
        override val name: String = "bluetoothCommandReported"

        override fun evaluate(input: AppLogAssertionInput): AppLogAssertionResult {
            inputs += input
            return AppLogAssertionResult.passed("ok")
        }
    }

    private class RecordingPlanResourceSink : PlanResourceSink {
        val resources = mutableListOf<ExplicitPlanResource>()

        override fun accept(resource: ExplicitPlanResource): CapturedPlanResource {
            resources += resource
            val extension = if (resource.contentType == "application/x-ndjson") "jsonl" else "mp4"
            val fileName = "${resource.resourceId ?: "recording"}.$extension"
            return CapturedPlanResource(
                resourceId = resource.resourceId ?: "recording",
                type = resource.type,
                purpose = resource.purpose,
                actionId = resource.actionId,
                name = resource.name,
                localPath = Path.of("/tmp/$fileName"),
                fileName = fileName,
                contentType = resource.contentType,
                sizeBytes = resource.bytes.size.toLong(),
                capturedAt = "2026-06-17T00:00:00Z",
            )
        }
    }

    private fun logSessionSnapshot(nextSeq: Long = 0): LogSessionSnapshot {
        return LogSessionSnapshot(
            sessionId = "session-1",
            udid = "ios-1",
            platform = Platform.IOS,
            status = LogSessionStatus.RUNNING,
            command = "ios",
            args = listOf("syslog"),
            startedAt = "2026-06-21T00:00:00Z",
            lastActivityAt = "2026-06-21T00:00:00Z",
            ttlMs = 600000,
            nextSeq = nextSeq,
            minSeq = 0,
            droppedCount = 0,
            maxBufferEntries = 1000,
            maxSessionBytes = 104857600,
        )
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

    private class RecordingTextRecognizerProvider(
        private val recognizer: VisualTextRecognizer,
    ) : VisualTextRecognizerProvider {
        val requested = mutableListOf<ScreenRecordingTextRecognizer>()

        override fun recognizerFor(kind: ScreenRecordingTextRecognizer): VisualTextRecognizer {
            requested += kind
            return recognizer
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
        private val elementScreenshotData: ByteArray = byteArrayOf(1, 2, 3),
        private val elementRect: ElementRect = ElementRect(0, 0, 10, 10, 100, 100),
        private val findFailuresBeforeSuccess: Int = 0,
        private val throwNoSuchElement: Boolean = false,
        private val throwTimeoutException: Boolean = false,
        private val throwingAttributes: Set<String> = emptySet(),
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
            if (throwNoSuchElement) {
                throw NoSuchElementException("element not found")
            }
            if (throwTimeoutException) {
                throw TimeoutException("element wait timed out")
            }
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

        override fun longPress(
            sessionId: String,
            element: DriverElement,
            durationMs: Long,
            xRatio: Double,
            yRatio: Double,
        ) {
            calls += "longPress:${element.elementId}:$durationMs:$xRatio:$yRatio"
        }

        override fun longPressViewport(
            sessionId: String,
            durationMs: Long,
            xRatio: Double,
            yRatio: Double,
        ) {
            calls += "longPressViewport:$sessionId:$durationMs:$xRatio:$yRatio"
        }

        override fun swipe(
            sessionId: String,
            element: DriverElement,
            durationMs: Long,
            startXRatio: Double,
            startYRatio: Double,
            endXRatio: Double,
            endYRatio: Double,
        ) {
            calls += "swipe:${element.elementId}:$durationMs:$startXRatio:$startYRatio:$endXRatio:$endYRatio"
        }

        override fun swipeViewport(
            sessionId: String,
            durationMs: Long,
            startXRatio: Double,
            startYRatio: Double,
            endXRatio: Double,
            endYRatio: Double,
        ) {
            calls += "swipeViewport:$sessionId:$durationMs:$startXRatio:$startYRatio:$endXRatio:$endYRatio"
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
            if (name in throwingAttributes) {
                error("unsupported attribute: $name")
            }
            attributeSequences[name]?.let { values ->
                val index = attributeReadCounts.getOrDefault(name, 0)
                attributeReadCounts[name] = index + 1
                return values[index.coerceAtMost(values.lastIndex)]
            }
            return attributes[name]
        }

        override fun getElementRect(
            sessionId: String,
            element: DriverElement,
        ): ElementRect {
            calls += "rect:${element.elementId}"
            return elementRect
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

        override fun clearAppData(
            sessionId: String,
            appId: String,
            wait: DriverWaitOptions?,
        ) {
            calls += "clearData:$sessionId:$appId:$wait"
        }

        override fun takeScreenshot(sessionId: String): ScreenshotData {
            calls += "screenshot:$sessionId"
            return ScreenshotData(screenshotData)
        }

        override fun takeElementScreenshot(
            sessionId: String,
            element: DriverElement,
        ): ScreenshotData {
            calls += "elementScreenshot:${element.elementId}"
            return ScreenshotData(elementScreenshotData)
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
        rgbAt: (x: Int, y: Int) -> Int = { _, _ -> Color.BLACK.rgb },
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, rgbAt(x, y))
            }
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
