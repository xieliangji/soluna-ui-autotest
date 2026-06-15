package com.ugreen.iot.soluna.autotest.artifact

import com.ugreen.iot.soluna.autotest.appium.driver.ScreenshotData
import com.ugreen.iot.soluna.autotest.appium.driver.WebDriverAdapter
import com.ugreen.iot.soluna.autotest.core.execution.ActionExecutionResult
import com.ugreen.iot.soluna.autotest.core.execution.ActionTraceCollector
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionContext
import com.ugreen.iot.soluna.autotest.core.execution.ExecutionStatus
import com.ugreen.iot.soluna.autotest.core.model.ActionDefinition
import com.ugreen.iot.soluna.autotest.core.model.CaseDefinition
import com.ugreen.iot.soluna.autotest.core.model.StageDefinition
import com.ugreen.iot.soluna.autotest.core.model.TraceScreenshotDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class FailureTraceScreenshotCollector(
    private val driver: WebDriverAdapter,
    private val outputDirectory: Path,
    private val config: TraceScreenshotDefinition,
    private val artifactUploader: ArtifactUploader?,
    private val clock: Clock = Clock.systemUTC(),
) : ActionTraceCollector {
    private val retained = ArrayDeque<CapturedTraceScreenshot>()
    private val published = CopyOnWriteArrayList<PublishedTraceArtifact>()
    private val publishedCaptureIds = linkedSetOf<String>()

    override fun beforeAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
        attempt: Int,
    ) {
        if (!config.enabled || config.beforeAction == "never") {
            return
        }
        val sessionId = context.driverSessionId ?: return
        val screenshot = runCatching { driver.takeScreenshot(sessionId) }.getOrNull() ?: return
        retain(
            CapturedTraceScreenshot(
                captureId = UUID.randomUUID().toString(),
                runId = context.runId,
                planId = context.plan.id,
                stageId = stage?.id,
                caseId = case?.id,
                actionId = action.id,
                actionKeyword = action.keyword,
                phase = phase,
                index = index,
                attempt = attempt,
                timing = "before",
                data = screenshot,
                capturedAt = clock.instant().toString(),
            ),
        )
    }

    override fun afterAction(
        context: ExecutionContext,
        stage: StageDefinition?,
        case: CaseDefinition?,
        action: ActionDefinition,
        phase: String,
        index: Int,
        result: ActionExecutionResult,
    ) {
        if (!config.enabled || result.status != ExecutionStatus.FAILED || config.upload == "never") {
            return
        }
        publishRetained()
    }

    fun published(): List<PublishedTraceArtifact> {
        return published.toList()
    }

    @Synchronized
    private fun retain(screenshot: CapturedTraceScreenshot) {
        retained.addLast(screenshot)
        while (retained.size > config.retainBeforeActionCount.coerceAtLeast(1)) {
            retained.removeFirst()
        }
    }

    @Synchronized
    private fun publishRetained() {
        Files.createDirectories(outputDirectory)
        retained.forEach { screenshot ->
            if (!publishedCaptureIds.add(screenshot.captureId)) {
                return@forEach
            }
            val fileName = screenshot.fileName()
            val path = outputDirectory.resolve(fileName)
            Files.write(path, screenshot.data.bytes)
            val objectKey = artifactUploader?.objectKey(
                runId = screenshot.runId,
                kind = ArtifactKind.DIAGNOSTIC,
                fileName = fileName,
            )
            val artifact = PublishedTraceArtifact(
                captureId = screenshot.captureId,
                runId = screenshot.runId,
                planId = screenshot.planId,
                stageId = screenshot.stageId,
                caseId = screenshot.caseId,
                actionId = screenshot.actionId,
                actionKeyword = screenshot.actionKeyword,
                phase = screenshot.phase,
                index = screenshot.index,
                attempt = screenshot.attempt,
                timing = screenshot.timing,
                localPath = path,
                fileName = fileName,
                objectKey = objectKey,
                url = objectKey?.let { artifactUploader.urlFor(it) },
                contentType = screenshot.data.contentType,
                sizeBytes = screenshot.data.bytes.size.toLong(),
                capturedAt = screenshot.capturedAt,
            )
            published += artifact
            if (objectKey != null) {
                artifactUploader.enqueue(
                    ArtifactUploadRequest(
                        localPath = path,
                        objectKey = objectKey,
                        contentType = screenshot.data.contentType,
                        requiredForReport = true,
                        compress = false,
                        metadata = mapOf(
                            "purpose" to "trace_screenshot",
                            "phase" to screenshot.phase,
                            "timing" to screenshot.timing,
                        ),
                    ),
                )
            }
        }
    }

    private fun CapturedTraceScreenshot.fileName(): String {
        val actionPart = actionId ?: actionKeyword
        val base = listOf(
            index.toString().padStart(4, '0'),
            phase,
            actionPart,
            "attempt$attempt",
            timing,
        ).joinToString("-")
        return "trace-${base.sanitizeFileName()}.${data.fileExtension()}"
    }

    private fun String.sanitizeFileName(): String {
        return trim().replace(Regex("""[^A-Za-z0-9._-]"""), "_").ifBlank { "trace" }
    }

    private fun ScreenshotData.fileExtension(): String {
        return when (contentType.lowercase().substringBefore(";").trim()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "bin"
        }
    }
}

private data class CapturedTraceScreenshot(
    val captureId: String,
    val runId: String,
    val planId: String,
    val stageId: String?,
    val caseId: String?,
    val actionId: String?,
    val actionKeyword: String,
    val phase: String,
    val index: Int,
    val attempt: Int,
    val timing: String,
    val data: ScreenshotData,
    val capturedAt: String,
)

data class PublishedTraceArtifact(
    val captureId: String,
    val runId: String,
    val planId: String,
    val stageId: String?,
    val caseId: String?,
    val actionId: String?,
    val actionKeyword: String,
    val phase: String,
    val index: Int,
    val attempt: Int,
    val timing: String,
    val localPath: Path,
    val fileName: String,
    val objectKey: String?,
    val url: String?,
    val contentType: String,
    val sizeBytes: Long,
    val capturedAt: String,
)
