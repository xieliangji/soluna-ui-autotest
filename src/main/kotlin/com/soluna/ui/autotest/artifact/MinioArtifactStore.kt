package com.soluna.ui.autotest.artifact

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.credentials.StaticProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class MinioArtifactStore(
    private val config: ArtifactStoreConfigDefinition,
    private val client: MinioObjectClient,
) : ArtifactStore {
    override fun put(request: ArtifactUploadRequest): ArtifactUploadResult {
        val payload = request.payload()
        val headers = if (payload.gzipped) {
            mapOf("Content-Encoding" to "gzip")
        } else {
            emptyMap()
        }

        client.putObject(
            bucket = config.bucket,
            objectKey = request.objectKey,
            bytes = payload.bytes,
            contentType = request.contentType,
            headers = headers,
        )

        return ArtifactUploadResult(
            objectKey = request.objectKey,
            url = urlFor(request.objectKey),
            contentType = request.contentType,
        )
    }

    override fun urlFor(objectKey: String): String {
        val baseUrl = config.publicBaseUrlWithScheme().trimEnd('/')
        return "$baseUrl/${config.bucket.urlEncodePathPart()}/${objectKey.urlEncodePath()}"
    }

    private fun ArtifactUploadRequest.payload(): UploadPayload {
        val bytes = Files.readAllBytes(localPath)
        val compression = config.upload.compression
        if (!compress || !compression.enabled || bytes.size < compression.minBytes || !contentType.shouldGzip(compression.contentTypes)) {
            return UploadPayload(bytes = bytes, gzipped = false)
        }

        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(bytes)
        }
        return UploadPayload(bytes = output.toByteArray(), gzipped = true)
    }

    private fun String.shouldGzip(patterns: List<String>): Boolean {
        val normalized = lowercase().substringBefore(";").trim()
        return patterns.any { pattern -> normalized.matchesContentTypePattern(pattern.lowercase()) }
    }

    private fun String.matchesContentTypePattern(pattern: String): Boolean {
        return when {
            pattern.endsWith("/*") -> startsWith(pattern.removeSuffix("*"))
            pattern.contains("*+") -> {
                val prefix = pattern.substringBefore("*")
                val suffix = pattern.substringAfter("*")
                startsWith(prefix) && endsWith(suffix)
            }
            else -> this == pattern
        }
    }

    private fun String.urlEncodePath(): String {
        return split("/").joinToString("/") { it.urlEncodePathPart() }
    }

    private fun String.urlEncodePathPart(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private data class UploadPayload(
        val bytes: ByteArray,
        val gzipped: Boolean,
    )

    companion object {
        fun fromConfig(config: ArtifactStoreConfigDefinition): MinioArtifactStore {
            val credentials = config.credentials
            val accessKey = credentials.accessKey
                ?: credentials.accessKeyEnv?.let { System.getenv(it) }
                ?: error("Missing MinIO access key; set credentials.accessKey or credentials.accessKeyEnv")
            val secretKey = credentials.secretKey
                ?: credentials.secretKeyEnv?.let { System.getenv(it) }
                ?: error("Missing MinIO secret key; set credentials.secretKey or credentials.secretKeyEnv")
            val sessionToken = credentials.sessionToken
                ?: credentials.sessionTokenEnv?.let { System.getenv(it) }
            val client = MinioClient.builder()
                .endpoint(config.endpointWithScheme())
                .credentialsProvider(StaticProvider(accessKey, secretKey, sessionToken))
                .build()

            return MinioArtifactStore(config, SdkMinioObjectClient(client))
        }
    }
}

interface MinioObjectClient {
    fun putObject(
        bucket: String,
        objectKey: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    )
}

class SdkMinioObjectClient(
    private val client: MinioClient,
) : MinioObjectClient {
    override fun putObject(
        bucket: String,
        objectKey: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ) {
        ByteArrayInputStream(bytes).use { input ->
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectKey)
                    .stream(input, bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .headers(headers)
                    .build(),
            )
        }
    }
}

object ArtifactContentTypes {
    fun forFileName(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "txt", "log" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
