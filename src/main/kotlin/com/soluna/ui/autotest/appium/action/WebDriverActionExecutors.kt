package com.soluna.ui.autotest.appium.action

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.TextNode
import com.soluna.ktvisual.api.Visual
import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import com.soluna.ui.autotest.appium.action.asBooleanOrNull
import com.soluna.ui.autotest.appium.action.asDoubleOrNull
import com.soluna.ui.autotest.appium.action.asLongOrNull
import com.soluna.ui.autotest.appium.action.asTextOrNull
import com.soluna.ui.autotest.appium.action.requireDriverSessionId
import com.soluna.ui.autotest.appium.action.requireLocator
import com.soluna.ui.autotest.appium.action.requireTextValue
import com.soluna.ui.autotest.appium.action.resolveRuntimeText
import com.soluna.ui.autotest.appium.action.toDriverWaitOptions
import com.soluna.ui.autotest.appium.driver.DriverElement
import com.soluna.ui.autotest.appium.driver.DriverWaitOptions
import com.soluna.ui.autotest.appium.driver.ElementRect
import com.soluna.ui.autotest.appium.driver.ScreenRecordingOptions
import com.soluna.ui.autotest.appium.driver.ScreenshotData
import com.soluna.ui.autotest.appium.driver.WebDriverAdapter
import com.soluna.ui.autotest.appium.ext.SolunaAppiumExtClient
import com.soluna.ui.autotest.artifact.CapturedPlanResource
import com.soluna.ui.autotest.core.execution.ActionExecutionResult
import com.soluna.ui.autotest.core.execution.ActionExecutor
import com.soluna.ui.autotest.core.execution.ExecutionContext
import com.soluna.ui.autotest.core.execution.ExecutionStatus
import com.soluna.ui.autotest.core.execution.Sleeper
import com.soluna.ui.autotest.core.execution.ThreadSleeper
import com.soluna.ui.autotest.core.model.ActionDefinition
import com.soluna.ui.autotest.core.model.LocatorDefinition
import com.soluna.ui.autotest.core.model.WaitDefinition
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

class TapActionExecutor(
    private val driver: com.soluna.ui.autotest.appium.driver.WebDriverAdapter,
    private val sleeper: com.soluna.ui.autotest.core.execution.Sleeper = _root_ide_package_.com.soluna.ui.autotest.core.execution.ThreadSleeper,
    private val defaultSettleMs: Long = 800,
) : com.soluna.ui.autotest.core.execution.ActionExecutor {
    override val keyword: String = "tap"

    override fun execute(
        action: com.soluna.ui.autotest.core.model.ActionDefinition,
        context: com.soluna.ui.autotest.core.execution.ExecutionContext,
    ): com.soluna.ui.autotest.core.execution.ActionExecutionResult {
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
            sleepAfterTap(action)
            return _root_ide_package_.com.soluna.ui.autotest.core.execution.ActionExecutionResult.passed("tap viewport executed")
        }

        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        driver.tap(
            sessionId = sessionId,
            element = element,
            xRatio = action.args["elementXRatio"]?.asDoubleOrNull() ?: 0.5,
            yRatio = action.args["elementYRatio"]?.asDoubleOrNull() ?: 0.5,
        )
        sleepAfterTap(action)
        return _root_ide_package_.com.soluna.ui.autotest.core.execution.ActionExecutionResult.passed("tap executed")
    }

    private fun sleepAfterTap(action: com.soluna.ui.autotest.core.model.ActionDefinition) {
        val settleMs = action.args["settleMs"]?.asLongOrNull() ?: defaultSettleMs
        if (settleMs > 0) {
            sleeper.sleep(settleMs)
        }
    }
}

class LongPressActionExecutor(
    private val driver: WebDriverAdapter,
    private val sleeper: Sleeper = ThreadSleeper,
    private val defaultDurationMs: Long = 1000,
    private val defaultSettleMs: Long = 800,
) : ActionExecutor {
    override val keyword: String = "longPress"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val durationMs = action.args["durationMs"]?.asLongOrNull() ?: defaultDurationMs
        require(durationMs >= 0) {
            "Long press action '${action.id ?: action.keyword}' requires durationMs >= 0"
        }
        val xRatio = action.args["xRatio"]?.asDoubleOrNull()
        val yRatio = action.args["yRatio"]?.asDoubleOrNull()
        if (xRatio != null || yRatio != null) {
            require(xRatio != null && yRatio != null) {
                "Long press action '${action.id ?: action.keyword}' requires both args.xRatio and args.yRatio"
            }
            driver.longPressViewport(
                sessionId = sessionId,
                durationMs = durationMs,
                xRatio = xRatio,
                yRatio = yRatio,
            )
            sleepAfterPress(action)
            return ActionExecutionResult.passed("long press viewport executed")
        }

        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        driver.longPress(
            sessionId = sessionId,
            element = element,
            durationMs = durationMs,
            xRatio = action.args["elementXRatio"]?.asDoubleOrNull() ?: 0.5,
            yRatio = action.args["elementYRatio"]?.asDoubleOrNull() ?: 0.5,
        )
        sleepAfterPress(action)
        return ActionExecutionResult.passed("long press executed")
    }

    private fun sleepAfterPress(action: ActionDefinition) {
        val settleMs = action.args["settleMs"]?.asLongOrNull() ?: defaultSettleMs
        if (settleMs > 0) {
            sleeper.sleep(settleMs)
        }
    }
}

class SwipeActionExecutor(
    private val driver: WebDriverAdapter,
    private val sleeper: Sleeper = ThreadSleeper,
    private val defaultDurationMs: Long = 500,
    private val defaultSettleMs: Long = 800,
) : ActionExecutor {
    override val keyword: String = "swipe"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val durationMs = action.args["durationMs"]?.asLongOrNull() ?: defaultDurationMs
        require(durationMs >= 0) {
            "Swipe action '${action.id ?: action.keyword}' requires durationMs >= 0"
        }

        if (action.locator == null) {
            driver.swipeViewport(
                sessionId = sessionId,
                durationMs = durationMs,
                startXRatio = action.requireRatioArg("startXRatio", "Swipe"),
                startYRatio = action.requireRatioArg("startYRatio", "Swipe"),
                endXRatio = action.requireRatioArg("endXRatio", "Swipe"),
                endYRatio = action.requireRatioArg("endYRatio", "Swipe"),
            )
            sleepAfterSwipe(action)
            return ActionExecutionResult.passed("swipe viewport executed")
        }

        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        driver.swipe(
            sessionId = sessionId,
            element = element,
            durationMs = durationMs,
            startXRatio = action.requireRatioArg("startElementXRatio", "Swipe"),
            startYRatio = action.requireRatioArg("startElementYRatio", "Swipe"),
            endXRatio = action.requireRatioArg("endElementXRatio", "Swipe"),
            endYRatio = action.requireRatioArg("endElementYRatio", "Swipe"),
        )
        sleepAfterSwipe(action)
        return ActionExecutionResult.passed("swipe executed")
    }

    private fun sleepAfterSwipe(action: ActionDefinition) {
        val settleMs = action.args["settleMs"]?.asLongOrNull() ?: defaultSettleMs
        if (settleMs > 0) {
            sleeper.sleep(settleMs)
        }
    }
}

class InputActionExecutor(
    private val driver: com.soluna.ui.autotest.appium.driver.WebDriverAdapter,
) : com.soluna.ui.autotest.core.execution.ActionExecutor {
    override val keyword: String = "input"

    override fun execute(
        action: com.soluna.ui.autotest.core.model.ActionDefinition,
        context: com.soluna.ui.autotest.core.execution.ExecutionContext,
    ): com.soluna.ui.autotest.core.execution.ActionExecutionResult {
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
        return _root_ide_package_.com.soluna.ui.autotest.core.execution.ActionExecutionResult.passed("input executed")
    }
}

class TapVisualTemplateActionExecutor(
    private val driver: com.soluna.ui.autotest.appium.driver.WebDriverAdapter,
    private val matcher: com.soluna.ui.autotest.appium.action.VisualTemplateMatcher = _root_ide_package_.com.soluna.ui.autotest.appium.action.KtVisualTemplateMatcher,
    private val sleeper: com.soluna.ui.autotest.core.execution.Sleeper = _root_ide_package_.com.soluna.ui.autotest.core.execution.ThreadSleeper,
    private val defaultSettleMs: Long = 800,
) : com.soluna.ui.autotest.core.execution.ActionExecutor {
    override val keyword: String = "tapVisualTemplate"

    override fun execute(
        action: com.soluna.ui.autotest.core.model.ActionDefinition,
        context: com.soluna.ui.autotest.core.execution.ExecutionContext,
    ): com.soluna.ui.autotest.core.execution.ActionExecutionResult {
        val template = action.args["template"]?.asTextOrNull()
            ?: action.value?.takeIf { it.isTextual }?.asText()
            ?: error("Tap visual template action '${action.id ?: action.keyword}' requires template")
        val templatePath = Path.of(template.resolveRuntimeText(context))
        require(Files.isRegularFile(templatePath)) {
            "Tap visual template action '${action.id ?: action.keyword}' requires an existing template file: $templatePath"
        }
        val threshold = action.args["threshold"]?.asDoubleOrNull() ?: 0.88
        require(threshold in 0.0..1.0) {
            "Tap visual template action '${action.id ?: action.keyword}' requires threshold between 0 and 1"
        }
        val scales = action.args["scales"]?.toDoubleListOrNull() ?: listOf(1.0)
        require(scales.isNotEmpty() && scales.all { it > 0.0 }) {
            "Tap visual template action '${action.id ?: action.keyword}' requires positive scales"
        }
        val targetXRatio = action.args["targetXRatio"]?.asDoubleOrNull() ?: 0.5
        val targetYRatio = action.args["targetYRatio"]?.asDoubleOrNull() ?: 0.5
        require(targetXRatio in 0.0..1.0 && targetYRatio in 0.0..1.0) {
            "Tap visual template action '${action.id ?: action.keyword}' requires targetXRatio/targetYRatio between 0 and 1"
        }

        val sessionId = context.requireDriverSessionId()
        val templateBytes = Files.readAllBytes(templatePath)
        val roi = action.args["roi"]?.toFrameRoi(action.id ?: action.keyword, context)
        val timeoutMs = action.wait?.timeoutMs ?: 0
        val intervalMs = action.wait?.intervalMs?.takeIf { it > 0 } ?: 500
        val attempts = if (timeoutMs <= 0) {
            1
        } else {
            ((timeoutMs + intervalMs - 1) / intervalMs + 1)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }.coerceAtLeast(1)

        var lastFailure = "Visual template '${templatePath.fileName}' was not found"
        repeat(attempts) { attempt ->
            val screenshot = driver.takeScreenshot(sessionId)
            val screenshotImage = ImageIO.read(ByteArrayInputStream(screenshot.bytes))
                ?: error("Tap visual template action '${action.id ?: action.keyword}' could not read current screenshot")
            var matcherFailure: String? = null
            val match = runCatching {
                matcher.find(
                    screenshot = screenshot.bytes,
                    template = templateBytes,
                    targetName = action.id ?: templatePath.fileName.toString(),
                    threshold = threshold,
                    scales = scales,
                    roi = roi?.toRegion(screenshotImage.width, screenshotImage.height),
                )
            }.getOrElse { err ->
                matcherFailure = "Visual template match failed: ${err.message ?: err::class.simpleName}"
                null
            }
            if (matcherFailure != null) {
                lastFailure = matcherFailure
            } else if (match == null) {
                lastFailure = "Visual template '${templatePath.fileName}' was not found"
            } else if (match.score < threshold) {
                lastFailure = "Visual template '${templatePath.fileName}' score ${match.score} is below threshold $threshold"
            } else {
                val targetX = match.bounds.x + match.bounds.width * targetXRatio
                val targetY = match.bounds.y + match.bounds.height * targetYRatio
                driver.tapViewport(
                    sessionId = sessionId,
                    xRatio = targetX / screenshotImage.width,
                    yRatio = targetY / screenshotImage.height,
                )
                val settleMs = action.args["settleMs"]?.asLongOrNull() ?: defaultSettleMs
                if (settleMs > 0) {
                    sleeper.sleep(settleMs)
                }
                return ActionExecutionResult.passed("visual template tapped with score ${match.score}")
            }
            if (attempt < attempts - 1) {
                sleeper.sleep(intervalMs)
            }
        }
        return ActionExecutionResult.failed(lastFailure)
    }
}

interface VisualTemplateMatcher {
    fun find(
        screenshot: ByteArray,
        template: ByteArray,
        targetName: String,
        threshold: Double,
        scales: List<Double>,
        roi: Region?,
    ): MatchResult?
}

object KtVisualTemplateMatcher : VisualTemplateMatcher {
    override fun find(
        screenshot: ByteArray,
        template: ByteArray,
        targetName: String,
        threshold: Double,
        scales: List<Double>,
        roi: Region?,
    ): MatchResult? {
        return Visual.findTemplate(
            screenshot,
            template,
            targetName,
            MatchOptions(
                threshold = threshold,
                scales = scales,
                roi = roi,
                maxMatches = 1,
            ),
        )
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

class StartScreenRecordingActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "startScreenRecording"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val timeLimitMs = action.args["timeLimitMs"]?.asLongOrNull()
            ?: action.args["timeoutMs"]?.asLongOrNull()
            ?: 10_000
        require(timeLimitMs > 0) {
            "Start screen recording action '${action.id ?: action.keyword}' requires timeLimitMs > 0"
        }
        driver.startScreenRecording(
            sessionId = sessionId,
            options = ScreenRecordingOptions(timeLimitMs = timeLimitMs),
        )
        return ActionExecutionResult.passed("screen recording started")
    }
}

class StopScreenRecordingActionExecutor(
    private val driver: WebDriverAdapter,
    private val sink: PlanResourceSink = NoOpPlanResourceSink,
) : ActionExecutor {
    override val keyword: String = "stopScreenRecording"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val recording = driver.stopScreenRecording(sessionId)
        val captured = sink.accept(
            ExplicitPlanResource(
                runId = context.runId,
                planId = context.plan.id,
                actionId = action.id,
                resourceId = action.resourceId,
                name = action.name,
                type = "video",
                purpose = "explicit_screen_recording",
                contentType = recording.contentType,
                bytes = recording.bytes,
            ),
        )
        val saveAs = action.args["saveAs"]?.asTextOrNull()
        if (saveAs != null) {
            val path = captured?.localPath
                ?: error("Stop screen recording action '${action.id ?: action.keyword}' requires a resource sink when saveAs is used")
            val scope = action.args["scope"]?.asTextOrNull() ?: "case"
            context.variables.set(
                scope = scope,
                name = saveAs,
                value = TextNode(path.toString()),
                caseId = context.caseVariableScopeId(),
            )
        }
        context.variables.set(
            scope = "case",
            name = "lastScreenRecording",
            value = TextNode(captured?.localPath?.toString().orEmpty()),
            caseId = context.caseVariableScopeId(),
        )
        return ActionExecutionResult.passed("screen recording stopped")
    }
}

class AssertScreenRecordingTextRegexMatchActionExecutor(
    private val frameExtractor: VideoFrameExtractor = FfmpegVideoFrameExtractor(),
    private val candidateSelector: VisualFrameCandidateSelector = KtVisualFrameCandidateSelector,
    private val textRecognizer: VisualTextRecognizer? = null,
    private val textRecognizerProvider: VisualTextRecognizerProvider = DefaultVisualTextRecognizerProvider,
    private val sink: PlanResourceSink = NoOpPlanResourceSink,
    private val multimodalCandidateParallelism: Int = defaultMultimodalCandidateParallelism(),
) : ActionExecutor {
    override val keyword: String = "assertScreenRecordingTextRegexMatch"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val source = action.args["source"]?.asTextOrNull()
            ?: "@{case.lastScreenRecording}"
        val videoPath = Path.of(source.resolveRuntimeText(context))
        require(Files.isRegularFile(videoPath)) {
            "Screen recording text assertion action '${action.id ?: action.keyword}' requires an existing video file: $videoPath"
        }
        val pattern = action.requirePatternText(context)
        val framesPerSecond = action.args["framesPerSecond"]?.asDoubleOrNull() ?: 5.0
        val maxFrames = action.args["maxFrames"]?.asIntOrNull() ?: 40
        val candidateMaxFrames = action.args["candidateMaxFrames"]?.asIntOrNull() ?: 5
        val candidateStrategy = action.args["candidateStrategy"]?.asTextOrNull()
            ?.let { FrameCandidateStrategy.fromExternalName(it, action.id ?: action.keyword) }
            ?: FrameCandidateStrategy.VISUAL_DIFF
        val recognizerKind = action.args["recognizer"]?.asTextOrNull()
            ?.let { ScreenRecordingTextRecognizer.fromExternalName(it, action.id ?: action.keyword) }
            ?: ScreenRecordingTextRecognizer.PADDLE
        val recognizer = textRecognizer ?: textRecognizerProvider.recognizerFor(recognizerKind)
        val visualDifferenceThreshold = action.args["visualDifferenceThreshold"]?.asDoubleOrNull() ?: 0.01
        require(candidateMaxFrames > 0) {
            "Screen recording text assertion action '${action.id ?: action.keyword}' requires candidateMaxFrames > 0"
        }
        require(visualDifferenceThreshold >= 0.0) {
            "Screen recording text assertion action '${action.id ?: action.keyword}' requires visualDifferenceThreshold >= 0"
        }
        val frameDir = videoPath.parent.resolve("${videoPath.fileName.toString().substringBeforeLast('.')}-frames")
        val roi = action.args["roi"]?.toFrameRoi(action.id ?: action.keyword, context)
        val roiDir = roi?.let {
            Files.createDirectories(frameDir.resolve("roi"))
        }
        val frames = frameExtractor.extract(
            videoPath = videoPath,
            outputDirectory = frameDir,
            framesPerSecond = framesPerSecond,
            maxFrames = maxFrames,
        )
        if (frames.isEmpty()) {
            return ActionExecutionResult.failed("No frames extracted from screen recording '$videoPath'")
        }

        val analysisFrames = frames.map { frame ->
            if (roi != null && roiDir != null) {
                roi.crop(frame, roiDir)
            } else {
                frame
            }
        }
        val candidates = candidateSelector.selectCandidates(
            frames = analysisFrames,
            maxCandidates = candidateMaxFrames,
            differenceThreshold = visualDifferenceThreshold,
            strategy = candidateStrategy,
        )
        val recognition = recognizeCandidates(
            candidates = candidates,
            recognizer = recognizer,
            pattern = pattern,
            parallelism = recognitionParallelism(recognizerKind, candidates.size),
        )
        val match = recognition.match

        if (match != null) {
            sink.accept(
                ExplicitPlanResource(
                    runId = context.runId,
                    planId = context.plan.id,
                    actionId = action.id,
                    resourceId = action.resourceId?.let { "$it-match-frame" }
                        ?: "${action.id ?: "screen-recording-text"}-match-frame",
                    name = action.name?.let { "$it Match Frame" },
                    type = "image",
                    purpose = "screen_recording_text_match_frame",
                    contentType = "image/png",
                    bytes = Files.readAllBytes(match),
                ),
            )
            return ActionExecutionResult.passed("screen recording text regex match passed")
        }

        val observedText = recognition.observations.joinToString(separator = "\n---\n").take(1000)
        val observedErrors = recognition.errors.joinToString(separator = "\n---\n").take(1000)
        val detail = listOfNotNull(
            observedText.takeIf { it.isNotBlank() }?.let { "OCR text was: $it" },
            observedErrors.takeIf { it.isNotBlank() }?.let { "OCR errors were: $it" },
        ).joinToString(separator = "; ").ifBlank { "OCR produced no text or errors" }
        return ActionExecutionResult.failed(
            "Expected screen recording candidate frames to match '${pattern.pattern}', $detail",
        )
    }

    private fun recognitionParallelism(
        recognizerKind: ScreenRecordingTextRecognizer,
        candidateCount: Int,
    ): Int =
        when (recognizerKind) {
            ScreenRecordingTextRecognizer.PADDLE -> 1
            ScreenRecordingTextRecognizer.MULTIMODAL -> multimodalCandidateParallelism.coerceIn(1, candidateCount)
        }

    private fun recognizeCandidates(
        candidates: List<Path>,
        recognizer: VisualTextRecognizer,
        pattern: Regex,
        parallelism: Int,
    ): CandidateRecognitionSummary {
        if (parallelism <= 1 || candidates.size <= 1) {
            return recognizeCandidatesSequentially(candidates, recognizer, pattern)
        }
        return recognizeCandidatesConcurrently(candidates, recognizer, pattern, parallelism)
    }

    private fun recognizeCandidatesSequentially(
        candidates: List<Path>,
        recognizer: VisualTextRecognizer,
        pattern: Regex,
    ): CandidateRecognitionSummary {
        val observations = mutableListOf<String>()
        val errors = mutableListOf<String>()
        candidates.forEach { candidate ->
            val result = recognizeCandidate(candidate, recognizer)
            if (result.error != null) {
                errors += result.errorMessage()
                return@forEach
            }
            val combined = result.combinedText.orEmpty()
            observations += combined
            if (pattern.containsMatchIn(combined)) {
                return CandidateRecognitionSummary(
                    match = candidate,
                    observations = observations,
                    errors = errors,
                )
            }
        }
        return CandidateRecognitionSummary(match = null, observations = observations, errors = errors)
    }

    private fun recognizeCandidatesConcurrently(
        candidates: List<Path>,
        recognizer: VisualTextRecognizer,
        pattern: Regex,
        parallelism: Int,
    ): CandidateRecognitionSummary {
        val threadIndex = AtomicInteger()
        val executor = Executors.newFixedThreadPool(parallelism) { runnable ->
            Thread(runnable, "soluna-screen-recording-ocr-${threadIndex.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
        val completion = ExecutorCompletionService<CandidateRecognitionResult>(executor)
        val futures = candidates.map { candidate ->
            completion.submit(Callable { recognizeCandidate(candidate, recognizer) })
        }
        val observations = mutableListOf<String>()
        val errors = mutableListOf<String>()
        try {
            repeat(candidates.size) {
                val result = try {
                    completion.take().get()
                } catch (err: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("Interrupted while waiting for OCR candidate recognition", err)
                } catch (err: ExecutionException) {
                    CandidateRecognitionResult(
                        frame = Path.of("<unknown>"),
                        combinedText = null,
                        error = err.cause ?: err,
                    )
                }
                if (result.error != null) {
                    errors += result.errorMessage()
                    return@repeat
                }
                val combined = result.combinedText.orEmpty()
                observations += combined
                if (pattern.containsMatchIn(combined)) {
                    futures.forEach { it.cancel(true) }
                    return CandidateRecognitionSummary(
                        match = result.frame,
                        observations = observations,
                        errors = errors,
                    )
                }
            }
            return CandidateRecognitionSummary(match = null, observations = observations, errors = errors)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(250, TimeUnit.MILLISECONDS)
        }
    }

    private fun recognizeCandidate(
        candidate: Path,
        recognizer: VisualTextRecognizer,
    ): CandidateRecognitionResult =
        try {
            CandidateRecognitionResult(
                frame = candidate,
                combinedText = recognizer.recognize(candidate).joinToString(separator = "\n"),
                error = null,
            )
        } catch (err: Exception) {
            CandidateRecognitionResult(frame = candidate, combinedText = null, error = err)
        }

    private fun CandidateRecognitionResult.errorMessage(): String =
        "Candidate frame '$frame' OCR failed: ${error?.shortCauseChain() ?: "unknown error"}"

    private fun Throwable.shortCauseChain(): String =
        generateSequence(this) { it.cause }
            .joinToString(separator = " <- ") { err ->
                "${err::class.java.simpleName}: ${err.message ?: "no message"}"
            }
            .take(1000)

    private data class CandidateRecognitionSummary(
        val match: Path?,
        val observations: List<String>,
        val errors: List<String>,
    )

    private data class CandidateRecognitionResult(
        val frame: Path,
        val combinedText: String?,
        val error: Throwable?,
    )
}

private fun defaultMultimodalCandidateParallelism(): Int =
    optionalRuntimeConfig(
        property = "soluna.visual.ocr.multimodal.parallelism",
        env = "SOLUNA_VISUAL_OCR_MULTIMODAL_PARALLELISM",
    )?.toIntOrNull()
        ?.coerceAtLeast(1)
        ?.coerceAtMost(16)
        ?: 4

private fun optionalRuntimeConfig(property: String, env: String): String? =
    System.getProperty(property)
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

interface VisualFrameCandidateSelector {
    fun selectCandidates(
        frames: List<Path>,
        maxCandidates: Int,
        differenceThreshold: Double,
        strategy: FrameCandidateStrategy,
    ): List<Path>
}

object KtVisualFrameCandidateSelector : VisualFrameCandidateSelector {
    override fun selectCandidates(
        frames: List<Path>,
        maxCandidates: Int,
        differenceThreshold: Double,
        strategy: FrameCandidateStrategy,
    ): List<Path> {
        if (frames.isEmpty()) {
            return emptyList()
        }
        if (strategy == FrameCandidateStrategy.ALL) {
            return frames
        }
        if (frames.size <= maxCandidates) {
            return frames
        }
        if (strategy == FrameCandidateStrategy.UNIFORM) {
            return frames.uniformSample(maxCandidates)
        }
        if (strategy == FrameCandidateStrategy.VISUAL_DIFF_UNIFORM) {
            val visualLimit = (maxCandidates / 3).coerceAtLeast(1)
            val uniformLimit = (maxCandidates - visualLimit).coerceAtLeast(1)
            val visualCandidates = frames.visualDiffSample(
                maxCandidates = visualLimit,
                differenceThreshold = differenceThreshold,
            )
            val uniformCandidates = frames.uniformSample(uniformLimit)
            return (visualCandidates + uniformCandidates).distinct().take(maxCandidates)
        }
        return frames.visualDiffSample(
            maxCandidates = maxCandidates,
            differenceThreshold = differenceThreshold,
        )
    }
}

enum class FrameCandidateStrategy(
    val externalName: String,
) {
    VISUAL_DIFF("visual-diff"),
    VISUAL_DIFF_UNIFORM("visual-diff-uniform"),
    UNIFORM("uniform"),
    ALL("all"),
    ;

    companion object {
        fun fromExternalName(
            value: String,
            actionName: String,
        ): FrameCandidateStrategy {
            return entries.firstOrNull { it.externalName == value }
                ?: error(
                    "Screen recording text assertion action '$actionName' requires candidateStrategy to be one of " +
                        entries.joinToString(", ") { it.externalName },
                )
        }
    }
}

private fun List<Path>.visualDiffSample(
    maxCandidates: Int,
    differenceThreshold: Double,
): List<Path> {
    val baseline = first()
    val scored = drop(1).map { frame ->
        val diff = runCatching {
            Visual.compareResized(baseline, frame, differenceThreshold, 0.0).differenceRatio
        }.getOrDefault(0.0)
        frame to diff
    }
    val changed = scored
        .filter { (_, diff) -> diff >= differenceThreshold }
        .sortedByDescending { (_, diff) -> diff }
        .map { (frame, _) -> frame }
        .take(maxCandidates)
    return if (changed.isNotEmpty()) {
        changed
    } else {
        take(maxCandidates)
    }
}

private fun List<Path>.uniformSample(maxCandidates: Int): List<Path> {
    if (maxCandidates <= 1) {
        return listOf(this[size / 2])
    }
    val lastIndex = size - 1
    return (0 until maxCandidates)
        .map { index ->
            ((index.toDouble() * lastIndex) / (maxCandidates - 1)).toInt().coerceIn(0, lastIndex)
        }
        .distinct()
        .map { this[it] }
}

private data class FrameRoi(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    fun toRegion(
        imageWidth: Int,
        imageHeight: Int,
    ): Region {
        val regionX = (imageWidth * x).toInt().coerceIn(0, imageWidth - 1)
        val regionY = (imageHeight * y).toInt().coerceIn(0, imageHeight - 1)
        val regionWidth = (imageWidth * width).toInt().coerceAtLeast(1).coerceAtMost(imageWidth - regionX)
        val regionHeight = (imageHeight * height).toInt().coerceAtLeast(1).coerceAtMost(imageHeight - regionY)
        return Region(regionX, regionY, regionWidth, regionHeight)
    }

    fun crop(
        frame: Path,
        outputDirectory: Path,
    ): Path {
        val image = ImageIO.read(frame.toFile())
            ?: error("Unable to read extracted frame '$frame' for ROI OCR analysis")
        val cropX = (image.width * x).toInt().coerceIn(0, image.width - 1)
        val cropY = (image.height * y).toInt().coerceIn(0, image.height - 1)
        val cropWidth = (image.width * width).toInt().coerceAtLeast(1).coerceAtMost(image.width - cropX)
        val cropHeight = (image.height * height).toInt().coerceAtLeast(1).coerceAtMost(image.height - cropY)
        val cropped = image.getSubimage(cropX, cropY, cropWidth, cropHeight)
        val copy = BufferedImage(cropped.width, cropped.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        try {
            graphics.drawImage(cropped, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val output = outputDirectory.resolve("${frame.fileName.toString().substringBeforeLast('.')}-roi.png")
        ImageIO.write(copy, "png", output.toFile())
        return output
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
        driver.restartApp(
            sessionId = sessionId,
            appId = appId,
            wait = action.wait.toDriverWaitOptions(),
        )
        return ActionExecutionResult.passed("app restarted")
    }
}

class ClearAppDataActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "clearAppData"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val appId = action.args["appId"]?.asTextOrNull()
            ?: action.value?.takeIf { it.isTextual }?.asText()
            ?: error("Clear app data action '${action.id ?: action.keyword}' requires args.appId or string value")
        driver.clearAppData(
            sessionId = sessionId,
            appId = appId,
            wait = action.wait.toDriverWaitOptions(),
        )
        return ActionExecutionResult.passed("app data cleared")
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

class SaveElementRectActionExecutor(
    private val driver: WebDriverAdapter,
) : ActionExecutor {
    override val keyword: String = "saveElementRect"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        val saveAs = action.args["saveAs"]?.asTextOrNull()
            ?: error("Save element rect action '${action.id ?: action.keyword}' requires args.saveAs")
        val scope = action.args["scope"]?.asTextOrNull() ?: "case"
        val element = driver.findElement(
            sessionId = sessionId,
            locator = action.requireLocator(),
            wait = action.wait.toDriverWaitOptions(),
        )
        val rect = driver.getElementRect(sessionId, element)
        val value = if (action.args["asRoi"]?.asBooleanOrNull() == true) {
            rect.toRoiNode(action)
        } else {
            rect.toRectNode()
        }
        context.variables.set(
            scope = scope,
            name = saveAs,
            value = value,
            caseId = context.caseVariableScopeId(),
        )
        return ActionExecutionResult.passed("saved element rect to $scope.$saveAs")
    }

    private fun ElementRect.toRectNode(): JsonNode {
        return JsonNodeFactory.instance.objectNode().also { node ->
            node.put("x", x)
            node.put("y", y)
            node.put("width", width)
            node.put("height", height)
            node.put("viewportWidth", viewportWidth)
            node.put("viewportHeight", viewportHeight)
        }
    }

    private fun ElementRect.toRoiNode(action: ActionDefinition): JsonNode {
        require(viewportWidth > 0 && viewportHeight > 0) {
            "Save element rect action '${action.id ?: action.keyword}' requires positive viewport size"
        }
        require(width > 0 && height > 0) {
            "Save element rect action '${action.id ?: action.keyword}' requires an element with positive visible area"
        }
        val fullWidth = action.args["fullWidth"]?.asBooleanOrNull() ?: false
        val fullHeight = action.args["fullHeight"]?.asBooleanOrNull() ?: false
        val expandLeft = width * action.nonNegativeRatioArg("expandLeftRatio")
        val expandRight = width * action.nonNegativeRatioArg("expandRightRatio")
        val expandTop = height * action.nonNegativeRatioArg("expandTopRatio")
        val expandBottom = height * action.nonNegativeRatioArg("expandBottomRatio")

        val viewportRight = viewportWidth.toDouble()
        val viewportBottom = viewportHeight.toDouble()
        val left = if (fullWidth) {
            0.0
        } else {
            (x - expandLeft).coerceIn(0.0, viewportRight)
        }
        val right = if (fullWidth) {
            viewportRight
        } else {
            (x + width + expandRight).coerceIn(0.0, viewportRight)
        }
        val top = if (fullHeight) {
            0.0
        } else {
            (y - expandTop).coerceIn(0.0, viewportBottom)
        }
        val bottom = if (fullHeight) {
            viewportBottom
        } else {
            (y + height + expandBottom).coerceIn(0.0, viewportBottom)
        }
        require(right > left && bottom > top) {
            "Save element rect action '${action.id ?: action.keyword}' produced an empty ROI"
        }

        return JsonNodeFactory.instance.objectNode().also { node ->
            node.put("x", left / viewportWidth)
            node.put("y", top / viewportHeight)
            node.put("width", (right - left) / viewportWidth)
            node.put("height", (bottom - top) / viewportHeight)
        }
    }

    private fun ActionDefinition.nonNegativeRatioArg(name: String): Double {
        val value = args[name]?.asDoubleOrNull() ?: return 0.0
        require(value >= 0.0) {
            "Save element rect action '${id ?: keyword}' requires $name to be >= 0"
        }
        return value
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
        return action.pollAssertion(sleeper) {
            val element = driver.findElement(
                sessionId = sessionId,
                locator = action.requireLocator(),
                wait = action.assertionProbeFindWait(),
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

class AssertElementExistsActionExecutor(
    private val driver: WebDriverAdapter,
    private val sleeper: Sleeper = ThreadSleeper,
) : ActionExecutor {
    override val keyword: String = "assertElementExists"

    override fun execute(
        action: ActionDefinition,
        context: ExecutionContext,
    ): ActionExecutionResult {
        val sessionId = context.requireDriverSessionId()
        return action.pollAssertion(sleeper) {
            driver.findElement(
                sessionId = sessionId,
                locator = action.requireLocator(),
                wait = action.assertionProbeFindWait(),
            )
            ActionExecutionResult.passed("assert element exists passed")
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
        return action.pollAssertion(sleeper) {
            val element = driver.findElement(
                sessionId = sessionId,
                locator = action.requireLocator(),
                wait = action.assertionProbeFindWait(),
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

data class ExplicitPlanResource(
    val runId: String,
    val planId: String,
    val actionId: String?,
    val resourceId: String?,
    val name: String?,
    val type: String,
    val purpose: String,
    val contentType: String,
    val bytes: ByteArray,
)

interface PlanResourceSink {
    fun accept(resource: ExplicitPlanResource): CapturedPlanResource?
}

object NoOpPlanResourceSink : PlanResourceSink {
    override fun accept(resource: ExplicitPlanResource): CapturedPlanResource? {
        return null
    }
}

interface ScreenshotSink : PlanResourceSink {
    fun accept(screenshot: ExplicitScreenshot): CapturedPlanResource?

    override fun accept(resource: ExplicitPlanResource): CapturedPlanResource? {
        return null
    }
}

object NoOpScreenshotSink : ScreenshotSink {
    override fun accept(screenshot: ExplicitScreenshot): CapturedPlanResource? {
        return null
    }
}

fun defaultWebDriverActionExecutors(
    driver: WebDriverAdapter,
    resourceSink: PlanResourceSink = NoOpPlanResourceSink,
    extClient: SolunaAppiumExtClient? = null,
    deviceUdid: String? = null,
    platform: String? = null,
): List<ActionExecutor> {
    val screenshotSink = resourceSink as? ScreenshotSink ?: NoOpScreenshotSink
    return listOf(
        RestartAppActionExecutor(driver),
        ClearAppDataActionExecutor(driver),
        GetTextActionExecutor(driver),
        SaveElementRectActionExecutor(driver),
        TapActionExecutor(driver),
        LongPressActionExecutor(driver),
        SwipeActionExecutor(driver),
        TapVisualTemplateActionExecutor(driver),
        InputActionExecutor(driver),
        StartScreenRecordingActionExecutor(driver),
        StopScreenRecordingActionExecutor(driver, resourceSink),
        AssertScreenRecordingTextRegexMatchActionExecutor(sink = resourceSink),
        CaptureAppLogStartActionExecutor(extClient, deviceUdid, platform),
        CaptureAppLogEndActionExecutor(extClient, resourceSink),
        CustomAssertAppLogActionExecutor(),
        AssertElementExistsActionExecutor(driver),
        AssertElementAttrEqualsActionExecutor(driver),
        AssertElementAttrRegexMatchActionExecutor(driver),
        AssertSourceRegexMatchActionExecutor(driver),
        ScreenshotActionExecutor(driver, screenshotSink),
        WaitActionExecutor(),
    )
}

fun ExplicitScreenshot.toPlanResource(): ExplicitPlanResource {
    return ExplicitPlanResource(
        runId = runId,
        planId = planId,
        actionId = actionId,
        resourceId = resourceId,
        name = name,
        type = "image",
        purpose = "explicit_screenshot",
        contentType = data.contentType,
        bytes = data.bytes,
    )
}

private fun ExecutionContext.requireDriverSessionId(): String {
    return driverSessionId ?: error("Action requires an Appium/WebDriver session id")
}

private fun ActionDefinition.requireLocator(): LocatorDefinition {
    return locator ?: error("Action '${id ?: keyword}' requires a locator")
}

private fun ActionDefinition.requireRatioArg(
    name: String,
    actionLabel: String,
): Double {
    val value = args[name]?.asDoubleOrNull()
        ?: error("$actionLabel action '${id ?: keyword}' requires args.$name")
    require(value in 0.0..1.0) {
        "$actionLabel action '${id ?: keyword}' requires args.$name between 0 and 1"
    }
    return value
}

private fun ActionDefinition.requireTextValue(): String {
    val node = value ?: error("Input action '${id ?: keyword}' requires value")
    if (!node.isValueNode || node.isNull) {
        error("Input action '${id ?: keyword}' value must be a scalar text-compatible value")
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

private fun ActionDefinition.assertionProbeFindWait(): DriverWaitOptions? {
    if (wait == null) {
        return null
    }
    return DriverWaitOptions(timeoutMs = 1)
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

private fun JsonNode.asIntOrNull(): Int? {
    return when {
        isIntegralNumber -> asInt()
        isTextual -> asText().toIntOrNull()
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

private fun JsonNode.toDoubleListOrNull(): List<Double>? {
    return when {
        isArray -> mapNotNull { it.asDoubleOrNull() }
        isNumber || isTextual -> asDoubleOrNull()?.let { listOf(it) }
        else -> null
    }
}

private fun JsonNode.toFrameRoi(
    actionName: String,
    context: ExecutionContext,
): FrameRoi {
    if (isTextual) {
        val reference = asText()
        val match = runtimeVariableReference.matchEntire(reference)
            ?: error("Visual action '$actionName' requires textual roi to be an exact runtime variable reference")
        return context.lookupRuntimeVariable(match.groupValues[1], match.groupValues[2])
            .toFrameRoi(actionName, context)
    }
    require(isObject) {
        "Visual action '$actionName' requires roi to be an object"
    }
    fun requiredRatio(name: String): Double {
        val value = get(name)?.asDoubleOrNull()
            ?: error("Visual action '$actionName' requires roi.$name")
        require(value in 0.0..1.0) {
            "Visual action '$actionName' requires roi.$name to be between 0 and 1"
        }
        return value
    }
    val x = requiredRatio("x")
    val y = requiredRatio("y")
    val width = requiredRatio("width")
    val height = requiredRatio("height")
    require(width > 0.0 && height > 0.0) {
        "Visual action '$actionName' requires roi width and height to be > 0"
    }
    require(x + width <= 1.0 && y + height <= 1.0) {
        "Visual action '$actionName' requires roi to stay inside the frame"
    }
    return FrameRoi(x = x, y = y, width = width, height = height)
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
