package com.ugreen.iot.soluna.autotest.notification

data class NotificationSenderConfigDefinition(
    val schemaVersion: String,
    val id: String,
    val type: String,
    val robot: DingTalkRobotDefinition,
    val uploadFailurePolicy: UploadFailureNotificationPolicyDefinition = UploadFailureNotificationPolicyDefinition(),
)

data class DingTalkRobotDefinition(
    val webhook: String? = null,
    val secret: String? = null,
    val webhookEnv: String? = null,
    val secretEnv: String? = null,
    val atMobiles: List<String> = emptyList(),
    val atUserIds: List<String> = emptyList(),
    val isAtAll: Boolean = false,
)

data class UploadFailureNotificationPolicyDefinition(
    val title: String = "Soluna artifact upload failures",
    val windowMs: Long = 300_000,
    val threshold: Int = 5,
    val suppressForMs: Long = 600_000,
)
