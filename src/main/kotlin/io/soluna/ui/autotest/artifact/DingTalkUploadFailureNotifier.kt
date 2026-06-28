package io.soluna.ui.autotest.artifact

import io.soluna.ui.autotest.notification.NotificationMessage
import io.soluna.ui.autotest.notification.NotificationSender
import io.soluna.ui.autotest.notification.UploadFailureNotificationPolicyDefinition
import java.time.Instant

class DingTalkUploadFailureNotifier(
    private val sender: NotificationSender,
    private val policy: UploadFailureNotificationPolicyDefinition = UploadFailureNotificationPolicyDefinition(),
) : ArtifactUploadFailureNotifier {
    private val failures = mutableListOf<ArtifactUploadTaskState>()
    private var lastSentAt: Instant? = null

    override fun notifyUploadFailures(snapshot: ArtifactUploadFailureSnapshot) {
        val now = snapshot.generatedAt
        synchronized(this) {
            val windowStart = now.minusMillis(policy.windowMs.coerceAtLeast(0))
            failures.removeIf { it.updatedAt.isBefore(windowStart) }
            failures += snapshot.states.filter {
                it.status == ArtifactUploadStatus.FAILED_RETRYABLE ||
                    it.status == ArtifactUploadStatus.FAILED_PERMANENT ||
                    it.status == ArtifactUploadStatus.ABANDONED
            }
            val uniqueFailures = failures.distinctBy { it.taskId }
            if (uniqueFailures.size < policy.threshold.coerceAtLeast(1)) {
                return
            }
            val previousSentAt = lastSentAt
            if (previousSentAt != null && previousSentAt.plusMillis(policy.suppressForMs).isAfter(now)) {
                return
            }

            val delivered = sender.send(
                NotificationMessage(
                    title = policy.title,
                    markdown = renderMarkdown(now, uniqueFailures),
                ),
            )
            if (delivered.delivered) {
                lastSentAt = now
                failures.clear()
            }
        }
    }

    private fun renderMarkdown(
        now: Instant,
        states: List<ArtifactUploadTaskState>,
    ): String {
        val rows = states.take(10).joinToString(separator = "\n") { state ->
            "- `${state.status.name.lowercase()}` `${state.request.objectKey}` attempts=${state.attempts} error=${state.error ?: "-"}"
        }
        val omitted = (states.size - 10).takeIf { it > 0 }?.let { "\n- ... $it more task(s)" }.orEmpty()
        return """
            ### ${policy.title}
            Time: `$now`
            Window: `${policy.windowMs}ms`
            Failed tasks: `${states.size}`

            $rows$omitted
        """.trimIndent()
    }
}
