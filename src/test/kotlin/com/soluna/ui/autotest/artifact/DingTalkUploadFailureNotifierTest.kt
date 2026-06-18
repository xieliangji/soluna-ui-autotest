package com.soluna.ui.autotest.artifact

import com.soluna.ui.autotest.notification.NotificationMessage
import com.soluna.ui.autotest.notification.NotificationSendResult
import com.soluna.ui.autotest.notification.NotificationSender
import com.soluna.ui.autotest.notification.UploadFailureNotificationPolicyDefinition
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DingTalkUploadFailureNotifierTest {
    @Test
    fun `sends one DingTalk alert after upload failures reach threshold`() {
        val sender = RecordingNotificationSender()
        val notifier = DingTalkUploadFailureNotifier(
            sender = sender,
            policy = UploadFailureNotificationPolicyDefinition(
                title = "Upload failures",
                windowMs = 60_000,
                threshold = 2,
                suppressForMs = 60_000,
            ),
        )

        notifier.notifyUploadFailures(snapshot("task-1", "report/index.html", Instant.parse("2026-06-13T00:00:00Z")))
        assertEquals(0, sender.messages.size)

        notifier.notifyUploadFailures(snapshot("task-2", "report/execution-result.json", Instant.parse("2026-06-13T00:00:01Z")))
        assertEquals(1, sender.messages.size)
        assertEquals("Upload failures", sender.messages.single().title)
        assertTrue(sender.messages.single().markdown.contains("report/index.html"))
        assertTrue(sender.messages.single().markdown.contains("report/execution-result.json"))
    }

    private fun snapshot(
        taskId: String,
        objectKey: String,
        at: Instant,
    ): ArtifactUploadFailureSnapshot {
        val request = ArtifactUploadRequest(
            localPath = Path.of("build/$taskId"),
            objectKey = objectKey,
            contentType = "text/plain",
            taskId = taskId,
        )
        return ArtifactUploadFailureSnapshot(
            generatedAt = at,
            states = listOf(
                ArtifactUploadTaskState(
                    taskId = taskId,
                    request = request,
                    status = ArtifactUploadStatus.FAILED_PERMANENT,
                    attempts = 2,
                    error = "failed",
                    updatedAt = at,
                ),
            ),
        )
    }

    private class RecordingNotificationSender : NotificationSender {
        val messages = mutableListOf<NotificationMessage>()

        override fun send(message: NotificationMessage): NotificationSendResult {
            messages += message
            return NotificationSendResult(delivered = true, statusCode = 200)
        }
    }
}
