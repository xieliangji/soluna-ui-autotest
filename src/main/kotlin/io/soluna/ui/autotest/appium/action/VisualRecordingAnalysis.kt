package io.soluna.ui.autotest.appium.action

import com.soluna.ktvisual.api.Visual
import com.soluna.ktvisual.ocr.paddle.OcrLanguage
import com.soluna.ktvisual.ocr.paddle.PaddleOcrEngine
import com.soluna.ktvisual.ocr.paddle.PaddleOcrOnnxRuntime
import com.soluna.ktvisual.ocr.paddle.PaddleOcrResourceManager
import com.soluna.ktvisual.ocr.multimodal.MultimodalOcrEngine
import com.soluna.ktvisual.ocr.multimodal.MultimodalOcrOptions
import com.soluna.ktvisual.ocr.multimodal.OpenAiCompatibleMultimodalOcrClient
import com.soluna.ktvisual.ocr.multimodal.OpenAiCompatibleMultimodalOcrConfig
import com.soluna.ktvisual.ocr.multimodal.OpenAiCompatibleStreamEvent
import io.soluna.ui.autotest.tool.DefaultFfmpegToolResolver
import io.soluna.ui.autotest.tool.FfmpegToolResolver
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

interface VideoFrameExtractor {
    fun extract(
        videoPath: Path,
        outputDirectory: Path,
        framesPerSecond: Double,
        maxFrames: Int,
    ): List<Path>
}

class FfmpegVideoFrameExtractor(
    private val toolResolver: FfmpegToolResolver = DefaultFfmpegToolResolver(),
) : VideoFrameExtractor {
    override fun extract(
        videoPath: Path,
        outputDirectory: Path,
        framesPerSecond: Double,
        maxFrames: Int,
    ): List<Path> {
        require(framesPerSecond > 0) { "framesPerSecond must be > 0" }
        require(maxFrames > 0) { "maxFrames must be > 0" }
        Files.createDirectories(outputDirectory)
        val executable = toolResolver.resolve().command

        val outputPattern = outputDirectory.resolve("${videoPath.nameWithoutExtension}-%04d.png")
        val process = ProcessBuilder(
            executable,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            videoPath.toString(),
            "-vf",
            "fps=$framesPerSecond",
            outputPattern.toString(),
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            error("ffmpeg failed to extract frames from '$videoPath': $output")
        }
        val extracted = listPngFrames(outputDirectory, maxFrames)
        if (extracted.isNotEmpty()) {
            return extracted
        }

        val firstFrame = outputDirectory.resolve("${videoPath.nameWithoutExtension}-first.png")
        val fallbackProcess = ProcessBuilder(
            executable,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            videoPath.toString(),
            "-frames:v",
            "1",
            firstFrame.toString(),
        )
            .redirectErrorStream(true)
            .start()
        val fallbackOutput = fallbackProcess.inputStream.bufferedReader().readText()
        val fallbackExit = fallbackProcess.waitFor()
        if (fallbackExit != 0) {
            error("ffmpeg failed to extract first frame from '$videoPath': $fallbackOutput")
        }
        return listPngFrames(outputDirectory, maxFrames)
    }

    private fun listPngFrames(outputDirectory: Path, maxFrames: Int): List<Path> =
        Files.list(outputDirectory).use { stream ->
            stream
                .filter { it.extension.equals("png", ignoreCase = true) }
                .sorted()
                .limit(maxFrames.toLong())
                .toList()
        }
}

interface VisualTextRecognizer {
    fun recognize(imagePath: Path): List<String>
}

private object KtVisualPaddleOcr {
    val engine by lazy {
        PaddleOcrEngine(
            languages = setOf(OcrLanguage.SIMPLIFIED_CHINESE, OcrLanguage.ENGLISH),
            runtime = PaddleOcrOnnxRuntime(),
            resourceResolver = PaddleOcrResourceManager(),
        )
    }
}

object KtVisualTextRecognizer : VisualTextRecognizer {
    override fun recognize(imagePath: Path): List<String> {
        val bytes = Files.readAllBytes(imagePath)
        return Visual.recognizeText(bytes, KtVisualPaddleOcr.engine)
            .map { it.text }
            .filter { it.isNotBlank() }
    }
}

object KtVisualMultimodalTextRecognizer : VisualTextRecognizer {
    private const val DEFAULT_MODEL = "gpt-5.5"
    private const val DEFAULT_REASONING_EFFORT = "high"
    private const val DEFAULT_TIMEOUT_MS = 60_000L
    private const val DEFAULT_STREAM_HTTP_TIMEOUT_MS = 600_000L
    private const val DEFAULT_STREAM_IDLE_TIMEOUT_MS = 60_000L
    private const val DEFAULT_PROMPT =
        "Extract every visible UI text fragment in the image, including low-contrast overlay or toast text. " +
            "Do not infer hidden characters, translate, summarize, or complete text from context. " +
            "If text is clipped or partly covered, return the visible substring only. " +
            "Keep the exact visible characters and original language. " +
            "Return only JSON with this shape: " +
            "{\"texts\":[{\"text\":\"Login\",\"confidence\":0.98,\"bounds\":{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.05}}]}. " +
            "Bounds must tightly cover only the visible text pixels, normalized to the provided image, using top-left x/y plus width/height. " +
            "If no text is visible, return {\"texts\":[]}."
    private val logger = LoggerFactory.getLogger(KtVisualMultimodalTextRecognizer::class.java)

    private val engine by lazy {
        val config = multimodalConfig()
        val client = if (config.stream) {
            IdleAwareOpenAiCompatibleStreamingOcrClient.fromConfig(
                config = config,
                streamHttpTimeout = multimodalDurationConfig(
                    property = "soluna.visual.ocr.multimodal.streamHttpTimeoutMs",
                    env = "SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM_HTTP_TIMEOUT_MS",
                    defaultMs = DEFAULT_STREAM_HTTP_TIMEOUT_MS,
                ),
                streamIdleTimeout = multimodalDurationConfig(
                    property = "soluna.visual.ocr.multimodal.streamIdleTimeoutMs",
                    env = "SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM_IDLE_TIMEOUT_MS",
                    defaultMs = DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                ),
            ) { event ->
                logStreamEvent(event)
            }
        } else {
            OpenAiCompatibleMultimodalOcrClient.fromConfig(config) { event ->
                logStreamEvent(event)
            }
        }
        MultimodalOcrEngine(
            client = client,
            options = MultimodalOcrOptions(prompt = multimodalPrompt()),
        )
    }

    override fun recognize(imagePath: Path): List<String> {
        val bytes = rgbPngBytes(imagePath)
        return runCatching {
            Visual.recognizeText(bytes, engine)
                .map { it.text }
                .filter { it.isNotBlank() }
        }.getOrElse { err ->
            throw IllegalStateException(
                "Multimodal OCR failed for '$imagePath': ${err.causeChain()}",
                err,
            )
        }
    }

    private fun multimodalConfig(): OpenAiCompatibleMultimodalOcrConfig {
        val baseUrl = requiredRuntimeConfig(
            property = "soluna.visual.ocr.multimodal.baseUrl",
            env = "SOLUNA_VISUAL_OCR_MULTIMODAL_BASE_URL",
        )
        return OpenAiCompatibleMultimodalOcrConfig(
            baseUrl = URI.create(baseUrl),
            apiKey = optionalRuntimeConfig(
                property = "soluna.visual.ocr.multimodal.apiKey",
                env = "SOLUNA_VISUAL_OCR_MULTIMODAL_API_KEY",
            ),
            model = optionalRuntimeConfig(
                property = "soluna.visual.ocr.multimodal.model",
                env = "SOLUNA_VISUAL_OCR_MULTIMODAL_MODEL",
            ) ?: DEFAULT_MODEL,
            timeout = Duration.ofMillis(
                optionalRuntimeConfig(
                    property = "soluna.visual.ocr.multimodal.timeoutMs",
                    env = "SOLUNA_VISUAL_OCR_MULTIMODAL_TIMEOUT_MS",
                )?.toLongOrNull() ?: DEFAULT_TIMEOUT_MS,
            ),
            reasoningEffort = optionalRuntimeConfig(
                property = "soluna.visual.ocr.multimodal.reasoningEffort",
                env = "SOLUNA_VISUAL_OCR_MULTIMODAL_REASONING_EFFORT",
            ) ?: DEFAULT_REASONING_EFFORT,
            stream = optionalRuntimeConfig(
                property = "soluna.visual.ocr.multimodal.stream",
                env = "SOLUNA_VISUAL_OCR_MULTIMODAL_STREAM",
            )?.toBooleanStrictOrNull() ?: true,
        )
    }

    private fun multimodalDurationConfig(
        property: String,
        env: String,
        defaultMs: Long,
    ): Duration =
        Duration.ofMillis(
            optionalRuntimeConfig(property = property, env = env)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: defaultMs,
        )

    private fun multimodalPrompt(): String =
        optionalRuntimeConfig(
            property = "soluna.visual.ocr.multimodal.prompt",
            env = "SOLUNA_VISUAL_OCR_MULTIMODAL_PROMPT",
        ) ?: DEFAULT_PROMPT

    private fun requiredRuntimeConfig(property: String, env: String): String =
        optionalRuntimeConfig(property, env)
            ?: error("Multimodal OCR requires system property '$property' or environment variable '$env'")

    private fun optionalRuntimeConfig(property: String, env: String): String? =
        System.getProperty(property)
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    private fun rgbPngBytes(imagePath: Path): ByteArray {
        val image = ImageIO.read(imagePath.toFile())
            ?: error("Unable to read image '$imagePath' for multimodal OCR")
        if (!image.colorModel.hasAlpha() && image.type == BufferedImage.TYPE_INT_RGB) {
            return Files.readAllBytes(imagePath)
        }
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rgb.width, rgb.height)
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
            image.flush()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(rgb, "png", out)
        rgb.flush()
        return out.toByteArray()
    }

    private fun logStreamEvent(event: OpenAiCompatibleStreamEvent) {
        when (event) {
            is OpenAiCompatibleStreamEvent.Reasoning -> logger.info(
                "visual.ocr.multimodal.stream type=reasoning text={}",
                event.text,
            )

            is OpenAiCompatibleStreamEvent.Content -> logger.info(
                "visual.ocr.multimodal.stream type=content text={}",
                event.text,
            )
        }
    }

    private fun Throwable.causeChain(): String =
        generateSequence(this) { it.cause }
            .joinToString(separator = " <- ") { err ->
                "${err::class.java.simpleName}: ${err.message ?: "no message"}"
            }
            .take(1_000)
}

interface VisualTextRecognizerProvider {
    fun recognizerFor(kind: ScreenRecordingTextRecognizer): VisualTextRecognizer
}

object DefaultVisualTextRecognizerProvider : VisualTextRecognizerProvider {
    override fun recognizerFor(kind: ScreenRecordingTextRecognizer): VisualTextRecognizer =
        when (kind) {
            ScreenRecordingTextRecognizer.PADDLE -> KtVisualTextRecognizer
            ScreenRecordingTextRecognizer.MULTIMODAL -> KtVisualMultimodalTextRecognizer
        }
}

enum class ScreenRecordingTextRecognizer(
    val externalName: String,
    private val aliases: Set<String> = emptySet(),
) {
    PADDLE("paddle", setOf("paddle-ocr", "kt-visual-paddle")),
    MULTIMODAL("multimodal", setOf("multimodal-ocr", "vision")),
    ;

    companion object {
        fun fromExternalName(value: String, actionName: String): ScreenRecordingTextRecognizer {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.externalName == normalized || normalized in it.aliases }
                ?: error(
                    "Screen recording text assertion action '$actionName' requires recognizer to be one of " +
                        entries.joinToString(", ") { it.externalName },
                )
        }
    }
}
