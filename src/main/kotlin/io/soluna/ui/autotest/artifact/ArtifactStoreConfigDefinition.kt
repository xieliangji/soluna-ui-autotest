package io.soluna.ui.autotest.artifact

data class ArtifactStoreConfigDefinition(
    val schemaVersion: String,
    val id: String,
    val type: String,
    val endpoint: String,
    val secure: Boolean = true,
    val bucket: String,
    val prefix: String = "",
    val publicBaseUrl: String? = null,
    val credentials: ArtifactCredentialsDefinition,
    val upload: ArtifactUploadConfigDefinition = ArtifactUploadConfigDefinition(),
    val notifications: ArtifactNotificationsDefinition = ArtifactNotificationsDefinition(),
) {
    fun endpointWithScheme(): String {
        return endpoint.withScheme(secure)
    }

    fun publicBaseUrlWithScheme(): String {
        return (publicBaseUrl ?: endpoint).withScheme(secure)
    }

    fun normalizedPrefix(): String {
        return prefix.trim().trim('/')
    }

    private fun String.withScheme(secure: Boolean): String {
        val value = trim().trimEnd('/')
        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "${if (secure) "https" else "http"}://$value"
        }
    }
}

data class ArtifactNotificationsDefinition(
    val uploadFailures: String? = null,
    val planStarted: String? = null,
    val testFinished: String? = null,
    val reportPublished: String? = null,
    val planFinished: String? = null,
)

data class ArtifactCredentialsDefinition(
    val accessKey: String? = null,
    val secretKey: String? = null,
    val sessionToken: String? = null,
    val accessKeyEnv: String? = null,
    val secretKeyEnv: String? = null,
    val sessionTokenEnv: String? = null,
)

data class ArtifactUploadConfigDefinition(
    val workerCount: Int = 1,
    val queueCapacity: Int = 512,
    val drainTimeoutMs: Long = 60_000,
    val compression: ArtifactUploadCompressionConfigDefinition = ArtifactUploadCompressionConfigDefinition(),
    val retry: ArtifactUploadRetryConfigDefinition = ArtifactUploadRetryConfigDefinition(),
)

data class ArtifactUploadCompressionConfigDefinition(
    val enabled: Boolean = true,
    val minBytes: Long = 0,
    val contentTypes: List<String> = listOf(
        "text/*",
        "application/json",
        "application/xml",
        "application/javascript",
        "application/*+json",
        "application/*+xml",
    ),
)

data class ArtifactUploadRetryConfigDefinition(
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    val backoffMultiplier: Double = 2.0,
)
