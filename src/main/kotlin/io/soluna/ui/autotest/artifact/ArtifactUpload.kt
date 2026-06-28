package io.soluna.ui.autotest.artifact

import io.soluna.ui.autotest.core.execution.Sleeper
import io.soluna.ui.autotest.core.execution.ThreadSleeper
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.pow

enum class ArtifactKind(
    val directory: String,
) {
    REPORT("report"),
    RESOURCE("resources"),
    DIAGNOSTIC("diagnostics"),
}

enum class ArtifactUploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    ABANDONED,
}

data class ArtifactUploadRequest(
    val localPath: Path,
    val objectKey: String,
    val contentType: String,
    val requiredForReport: Boolean = false,
    val compress: Boolean = true,
    val metadata: Map<String, String> = emptyMap(),
    val taskId: String = UUID.randomUUID().toString(),
)

data class ArtifactUploadResult(
    val objectKey: String,
    val url: String,
    val contentType: String,
    val etag: String? = null,
)

data class ArtifactUploadTaskState(
    val taskId: String,
    val request: ArtifactUploadRequest,
    val status: ArtifactUploadStatus,
    val attempts: Int = 0,
    val url: String? = null,
    val error: String? = null,
    val updatedAt: Instant,
) {
    fun isTerminal(): Boolean {
        return status == ArtifactUploadStatus.UPLOADED ||
            status == ArtifactUploadStatus.FAILED_PERMANENT ||
            status == ArtifactUploadStatus.ABANDONED
    }
}

data class ArtifactUploadDrainResult(
    val completed: Boolean,
    val states: List<ArtifactUploadTaskState>,
) {
    val uploadedCount: Int = states.count { it.status == ArtifactUploadStatus.UPLOADED }
    val failedCount: Int = states.count { it.status == ArtifactUploadStatus.FAILED_PERMANENT }
    val abandonedCount: Int = states.count { it.status == ArtifactUploadStatus.ABANDONED }
}

data class ArtifactUploadFailureSnapshot(
    val generatedAt: Instant,
    val states: List<ArtifactUploadTaskState>,
)

interface ArtifactStore {
    fun put(request: ArtifactUploadRequest): ArtifactUploadResult

    fun urlFor(objectKey: String): String
}

interface ArtifactUploader : AutoCloseable {
    fun objectKeyPrefix(
        runId: String,
        kind: ArtifactKind,
    ): String

    fun objectKey(
        runId: String,
        kind: ArtifactKind,
        fileName: String,
    ): String

    fun urlFor(objectKey: String): String

    fun enqueue(request: ArtifactUploadRequest): ArtifactUploadTaskState

    fun drainRequired(timeoutMs: Long): ArtifactUploadDrainResult

    fun drainAll(timeoutMs: Long): ArtifactUploadDrainResult

    fun snapshot(): List<ArtifactUploadTaskState>

    override fun close()
}

interface ArtifactUploadFailureNotifier {
    fun notifyUploadFailures(snapshot: ArtifactUploadFailureSnapshot)
}

object NoOpArtifactUploadFailureNotifier : ArtifactUploadFailureNotifier {
    override fun notifyUploadFailures(snapshot: ArtifactUploadFailureSnapshot) {
        // Notification delivery is provided by a pluggable sender in a later iteration.
    }
}

class ArtifactObjectKeyFactory(
    private val prefix: String,
) {
    fun prefixFor(
        runId: String,
        kind: ArtifactKind,
    ): String {
        val normalizedPrefix = prefix.trim().trim('/')
        val suffix = "runs/${runId.sanitizePathPart()}/${kind.directory}"
        return listOf(normalizedPrefix, suffix)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    fun objectKey(
        runId: String,
        kind: ArtifactKind,
        fileName: String,
    ): String {
        return "${prefixFor(runId, kind)}/${fileName.sanitizePathPart()}"
    }

    private fun String.sanitizePathPart(): String {
        return trim().replace(Regex("""[^A-Za-z0-9._:-]"""), "_").ifBlank { "artifact" }
    }
}

class AsyncArtifactUploader(
    private val store: ArtifactStore,
    private val objectKeyFactory: ArtifactObjectKeyFactory,
    uploadConfig: ArtifactUploadConfigDefinition = ArtifactUploadConfigDefinition(),
    private val notifier: ArtifactUploadFailureNotifier = NoOpArtifactUploadFailureNotifier,
    private val sleeper: Sleeper = ThreadSleeper,
    private val clock: Clock = Clock.systemUTC(),
) : ArtifactUploader {
    private val retryConfig = uploadConfig.retry
    private val queue = ArrayBlockingQueue<ArtifactUploadRequest>(uploadConfig.queueCapacity)
    private val states = ConcurrentHashMap<String, ArtifactUploadTaskState>()
    private val closed = AtomicBoolean(false)
    private val workers = List(uploadConfig.workerCount.coerceAtLeast(1)) { index ->
        Thread({ workerLoop() }, "soluna-artifact-uploader-$index").apply {
            isDaemon = true
            start()
        }
    }

    override fun objectKeyPrefix(
        runId: String,
        kind: ArtifactKind,
    ): String {
        return objectKeyFactory.prefixFor(runId, kind)
    }

    override fun objectKey(
        runId: String,
        kind: ArtifactKind,
        fileName: String,
    ): String {
        return objectKeyFactory.objectKey(runId, kind, fileName)
    }

    override fun urlFor(objectKey: String): String {
        return store.urlFor(objectKey)
    }

    override fun enqueue(request: ArtifactUploadRequest): ArtifactUploadTaskState {
        val pending = request.state(ArtifactUploadStatus.PENDING)
        states[request.taskId] = pending

        if (closed.get()) {
            return request.state(
                status = ArtifactUploadStatus.ABANDONED,
                error = "artifact uploader is closed",
            ).also { states[request.taskId] = it }
        }

        if (!queue.offer(request)) {
            val abandoned = request.state(
                status = ArtifactUploadStatus.ABANDONED,
                error = "artifact upload queue is full",
            )
            states[request.taskId] = abandoned
            notifyFailures(listOf(abandoned))
            return abandoned
        }

        return pending
    }

    override fun drainRequired(timeoutMs: Long): ArtifactUploadDrainResult {
        return drain(timeoutMs) { it.request.requiredForReport }
    }

    override fun drainAll(timeoutMs: Long): ArtifactUploadDrainResult {
        return drain(timeoutMs) { true }
    }

    private fun drain(
        timeoutMs: Long,
        filter: (ArtifactUploadTaskState) -> Boolean,
    ): ArtifactUploadDrainResult {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(0))

        while (System.nanoTime() <= deadlineNanos) {
            val states = snapshot().filter(filter)
            if (states.all { it.isTerminal() }) {
                return ArtifactUploadDrainResult(completed = true, states = states)
            }
            Thread.sleep(25)
        }

        return ArtifactUploadDrainResult(
            completed = false,
            states = snapshot().filter(filter),
        )
    }

    override fun snapshot(): List<ArtifactUploadTaskState> {
        return states.values.sortedBy { it.updatedAt }
    }

    override fun close() {
        closed.set(true)
        workers.forEach { worker ->
            worker.join(1_000)
        }
    }

    private fun workerLoop() {
        while (!closed.get() || queue.isNotEmpty()) {
            val request = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            uploadWithRetry(request)
        }
    }

    private fun uploadWithRetry(request: ArtifactUploadRequest) {
        var attempt = 1
        while (true) {
            states[request.taskId] = request.state(
                status = ArtifactUploadStatus.UPLOADING,
                attempts = attempt,
            )

            val result = runCatching { store.put(request) }
            if (result.isSuccess) {
                val uploaded = request.state(
                    status = ArtifactUploadStatus.UPLOADED,
                    attempts = attempt,
                    url = result.getOrThrow().url,
                )
                states[request.taskId] = uploaded
                return
            }

            val error = result.exceptionOrNull()?.message
                ?: result.exceptionOrNull()?.javaClass?.simpleName
                ?: "artifact upload failed"
            val shouldRetry = retryConfig.maxAttempts == 0 || attempt < retryConfig.maxAttempts
            val status = if (shouldRetry) {
                ArtifactUploadStatus.FAILED_RETRYABLE
            } else {
                ArtifactUploadStatus.FAILED_PERMANENT
            }
            val failed = request.state(
                status = status,
                attempts = attempt,
                error = error,
            )
            states[request.taskId] = failed

            if (!shouldRetry) {
                notifyFailures(listOf(failed))
                return
            }

            val delayMs = retryDelayMs(attempt)
            if (delayMs > 0) {
                sleeper.sleep(delayMs)
            }
            attempt += 1
        }
    }

    private fun retryDelayMs(attempt: Int): Long {
        val raw = retryConfig.initialDelayMs * retryConfig.backoffMultiplier.pow((attempt - 1).toDouble())
        return min(raw.toLong(), retryConfig.maxDelayMs).coerceAtLeast(0)
    }

    private fun notifyFailures(failedStates: List<ArtifactUploadTaskState>) {
        runCatching {
            notifier.notifyUploadFailures(ArtifactUploadFailureSnapshot(clock.instant(), failedStates))
        }
    }

    private fun ArtifactUploadRequest.state(
        status: ArtifactUploadStatus,
        attempts: Int = 0,
        url: String? = null,
        error: String? = null,
    ): ArtifactUploadTaskState {
        return ArtifactUploadTaskState(
            taskId = taskId,
            request = this,
            status = status,
            attempts = attempts,
            url = url,
            error = error,
            updatedAt = clock.instant(),
        )
    }
}

object DefaultArtifactUploaderFactory {
    fun create(
        config: ArtifactStoreConfigDefinition,
        notifier: ArtifactUploadFailureNotifier = NoOpArtifactUploadFailureNotifier,
    ): ArtifactUploader {
        require(config.type == "minio") { "Unsupported artifact store type '${config.type}'" }
        return AsyncArtifactUploader(
            store = MinioArtifactStore.fromConfig(config),
            objectKeyFactory = ArtifactObjectKeyFactory(config.normalizedPrefix()),
            uploadConfig = config.upload,
            notifier = notifier,
        )
    }
}
