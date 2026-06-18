package com.soluna.ui.autotest.artifact

import com.soluna.ui.autotest.core.execution.Sleeper
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncArtifactUploaderTest {
    @Test
    fun `retries upload task and drains required artifacts`() {
        val root = Files.createTempDirectory("soluna-uploader-test")
        val file = root.resolve("execution-result.json")
        Files.writeString(file, """{"ok":true}""")
        val store = FlakyArtifactStore(failuresBeforeSuccess = 1)
        val uploader = AsyncArtifactUploader(
            store = store,
            objectKeyFactory = ArtifactObjectKeyFactory("soluna"),
            uploadConfig = ArtifactUploadConfigDefinition(
                workerCount = 1,
                queueCapacity = 8,
                drainTimeoutMs = 1_000,
                retry = ArtifactUploadRetryConfigDefinition(
                    maxAttempts = 2,
                    initialDelayMs = 0,
                    maxDelayMs = 0,
                    backoffMultiplier = 1.0,
                ),
            ),
            sleeper = NoOpTestSleeper,
            clock = Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC),
        )

        uploader.enqueue(
            ArtifactUploadRequest(
                localPath = file,
                objectKey = uploader.objectKey("run-1", ArtifactKind.REPORT, "execution-result.json"),
                contentType = "application/json",
                requiredForReport = true,
            ),
        )

        val drain = uploader.drainRequired(1_000)
        uploader.close()

        assertTrue(drain.completed)
        assertEquals(1, drain.uploadedCount)
        assertEquals(2, drain.states.single().attempts)
        assertEquals(2, store.attempts)
        assertEquals("soluna/runs/run-1/report/execution-result.json", drain.states.single().request.objectKey)
    }

    private class FlakyArtifactStore(
        private val failuresBeforeSuccess: Int,
    ) : ArtifactStore {
        var attempts: Int = 0

        override fun put(request: ArtifactUploadRequest): ArtifactUploadResult {
            attempts += 1
            if (attempts <= failuresBeforeSuccess) {
                error("temporary upload failure")
            }
            return ArtifactUploadResult(
                objectKey = request.objectKey,
                url = urlFor(request.objectKey),
                contentType = request.contentType,
            )
        }

        override fun urlFor(objectKey: String): String {
            return "https://artifact.local/$objectKey"
        }
    }

    private object NoOpTestSleeper : Sleeper {
        override fun sleep(delayMs: Long) {
            // Keep uploader retry tests deterministic.
        }
    }
}
