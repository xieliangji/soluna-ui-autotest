package com.soluna.ui.autotest.appium.action

import com.soluna.ktvisual.api.Visual
import com.soluna.ktvisual.ocr.paddle.OcrLanguage
import com.soluna.ktvisual.ocr.paddle.PaddleOcrEngine
import com.soluna.ktvisual.ocr.paddle.PaddleOcrOnnxRuntime
import com.soluna.ktvisual.ocr.paddle.PaddleOcrResourceManager
import com.soluna.ui.autotest.tool.DefaultFfmpegToolResolver
import com.soluna.ui.autotest.tool.FfmpegToolResolver
import java.nio.file.Files
import java.nio.file.Path
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
    private val toolResolver: com.soluna.ui.autotest.tool.FfmpegToolResolver = _root_ide_package_.com.soluna.ui.autotest.tool.DefaultFfmpegToolResolver(),
) : com.soluna.ui.autotest.appium.action.VideoFrameExtractor {
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

object KtVisualTextRecognizer : com.soluna.ui.autotest.appium.action.VisualTextRecognizer {
    private val engine by lazy {
        PaddleOcrEngine(
            languages = setOf(OcrLanguage.SIMPLIFIED_CHINESE, OcrLanguage.ENGLISH),
            runtime = PaddleOcrOnnxRuntime(),
            resourceResolver = PaddleOcrResourceManager(),
        )
    }

    override fun recognize(imagePath: Path): List<String> {
        val bytes = Files.readAllBytes(imagePath)
        return Visual.recognizeText(bytes, engine)
            .map { it.text }
            .filter { it.isNotBlank() }
    }
}
