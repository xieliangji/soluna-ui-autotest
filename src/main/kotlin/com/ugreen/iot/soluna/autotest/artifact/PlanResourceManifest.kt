package com.ugreen.iot.soluna.autotest.artifact

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ugreen.iot.soluna.autotest.appium.action.ExplicitScreenshot
import com.ugreen.iot.soluna.autotest.appium.action.ScreenshotSink
import com.ugreen.iot.soluna.autotest.runner.PlanRunResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LocalExplicitScreenshotSink(
    private val outputDirectory: Path,
    private val clock: Clock = Clock.systemUTC(),
) : ScreenshotSink {
    private val counter = AtomicInteger(0)
    private val screenshots = CopyOnWriteArrayList<CapturedExplicitScreenshot>()

    override fun accept(screenshot: ExplicitScreenshot) {
        Files.createDirectories(outputDirectory)
        val index = counter.incrementAndGet()
        val baseName = screenshot.resourceId
            ?: screenshot.actionId
            ?: "screenshot-$index"
        val fileName = "${baseName.sanitizeFileName()}-${index.toString().padStart(3, '0')}.${screenshot.data.fileExtension()}"
        val path = outputDirectory.resolve(fileName)
        Files.write(path, screenshot.data.bytes)
        screenshots += CapturedExplicitScreenshot(
            resourceId = screenshot.resourceId ?: baseName,
            actionId = screenshot.actionId,
            name = screenshot.name,
            localPath = path,
            fileName = fileName,
            contentType = screenshot.data.contentType,
            sizeBytes = screenshot.data.bytes.size.toLong(),
            capturedAt = clock.instant().toString(),
        )
    }

    fun captured(): List<CapturedExplicitScreenshot> {
        return screenshots.toList()
    }

    private fun String.sanitizeFileName(): String {
        return trim().replace(Regex("""[^A-Za-z0-9._-]"""), "_").ifBlank { "screenshot" }
    }

    private fun com.ugreen.iot.soluna.autotest.appium.driver.ScreenshotData.fileExtension(): String {
        return when (contentType.lowercase().substringBefore(";").trim()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "bin"
        }
    }
}

data class CapturedExplicitScreenshot(
    val resourceId: String,
    val actionId: String?,
    val name: String?,
    val localPath: Path,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val capturedAt: String,
)

data class PlanResourceManifestWriteResult(
    val file: Path,
    val resources: List<PublishedPlanResource>,
)

data class PublishedPlanResource(
    val resourceId: String,
    val type: String,
    val purpose: String,
    val name: String?,
    val actionId: String?,
    val localPath: Path,
    val objectKey: String?,
    val url: String?,
    val contentType: String,
    val sizeBytes: Long,
    val capturedAt: String,
)

class PlanResourceManifestWriter(
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun write(
        result: PlanRunResult,
        directory: Path,
        screenshots: List<CapturedExplicitScreenshot>,
        artifactUploader: ArtifactUploader?,
    ): PlanResourceManifestWriteResult {
        Files.createDirectories(directory)
        val resources = screenshots.map { screenshot ->
            val objectKey = artifactUploader?.objectKey(
                runId = result.executionResult.runId,
                kind = ArtifactKind.RESOURCE,
                fileName = screenshot.fileName,
            )
            PublishedPlanResource(
                resourceId = screenshot.resourceId,
                type = "image",
                purpose = "explicit_screenshot",
                name = screenshot.name,
                actionId = screenshot.actionId,
                localPath = screenshot.localPath,
                objectKey = objectKey,
                url = objectKey?.let { artifactUploader.urlFor(it) },
                contentType = screenshot.contentType,
                sizeBytes = screenshot.sizeBytes,
                capturedAt = screenshot.capturedAt,
            )
        }
        val data = PlanResourceManifestData(
            plan = PlanManifestPlanData(
                planId = result.plan.id,
                planName = result.plan.name,
                planVersion = result.plan.version,
                metadata = result.plan.metadata,
            ),
            resourceBatch = PlanManifestBatchData(
                runId = result.executionResult.runId,
                generatedAt = clock.instant().toString(),
                minioPrefix = artifactUploader?.objectKeyPrefix(result.executionResult.runId, ArtifactKind.REPORT),
            ),
            resources = resources.map { resource ->
                PlanManifestResourceData(
                    resourceId = resource.resourceId,
                    type = resource.type,
                    purpose = resource.purpose,
                    name = resource.name,
                    actionId = resource.actionId,
                    objectKey = resource.objectKey,
                    url = resource.url,
                    contentType = resource.contentType,
                    sizeBytes = resource.sizeBytes,
                    capturedAt = resource.capturedAt,
                )
            },
        )
        val file = directory.resolve("plan-resource-manifest.json")
        Files.writeString(file, objectMapper.writeValueAsString(data))
        return PlanResourceManifestWriteResult(file = file, resources = resources)
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper {
            return ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}

data class PlanResourceManifestData(
    val schemaVersion: String = "1.0",
    val plan: PlanManifestPlanData,
    val resourceBatch: PlanManifestBatchData,
    val resources: List<PlanManifestResourceData>,
)

data class PlanManifestPlanData(
    val planId: String,
    val planName: String,
    val planVersion: String?,
    val metadata: Map<String, com.fasterxml.jackson.databind.JsonNode>,
)

data class PlanManifestBatchData(
    val runId: String,
    val generatedAt: String,
    val minioPrefix: String?,
)

data class PlanManifestResourceData(
    val resourceId: String,
    val type: String,
    val purpose: String,
    val name: String?,
    val actionId: String?,
    val objectKey: String?,
    val url: String?,
    val contentType: String,
    val sizeBytes: Long,
    val capturedAt: String,
)
