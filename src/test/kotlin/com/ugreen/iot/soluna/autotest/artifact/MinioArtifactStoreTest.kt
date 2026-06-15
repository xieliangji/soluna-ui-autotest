package com.ugreen.iot.soluna.autotest.artifact

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MinioArtifactStoreTest {
    @Test
    fun `compresses text artifact uploads with gzip content encoding`() {
        val root = Files.createTempDirectory("soluna-minio-store-test")
        val file = root.resolve("execution-result.json")
        val content = """{"runId":"run-001","status":"passed"}"""
        Files.writeString(file, content)
        val client = RecordingMinioObjectClient()
        val store = MinioArtifactStore(config(), client)

        store.put(
            ArtifactUploadRequest(
                localPath = file,
                objectKey = "soluna/runs/run-001/report/execution-result.json",
                contentType = "application/json",
            ),
        )

        val upload = client.uploads.single()
        assertEquals("gzip", upload.headers["Content-Encoding"])
        assertEquals(content, upload.bytes.gunzipToString())
    }

    @Test
    fun `does not compress already compressed image resources`() {
        val root = Files.createTempDirectory("soluna-minio-image-test")
        val file = root.resolve("screen.png")
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        Files.write(file, bytes)
        val client = RecordingMinioObjectClient()
        val store = MinioArtifactStore(config(), client)

        store.put(
            ArtifactUploadRequest(
                localPath = file,
                objectKey = "soluna/runs/run-001/resources/screen.png",
                contentType = "image/png",
                compress = false,
            ),
        )

        val upload = client.uploads.single()
        assertEquals(emptyMap(), upload.headers)
        assertContentEquals(bytes, upload.bytes)
    }

    private fun config(): ArtifactStoreConfigDefinition {
        return ArtifactStoreConfigDefinition(
            schemaVersion = "1.0",
            id = "test-minio",
            type = "minio",
            endpoint = "http://127.0.0.1:9000",
            secure = false,
            bucket = "autotest-reports",
            prefix = "soluna",
            credentials = ArtifactCredentialsDefinition(
                accessKey = "access",
                secretKey = "secret",
            ),
        )
    }

    private fun ByteArray.gunzipToString(): String {
        return GZIPInputStream(ByteArrayInputStream(this)).bufferedReader().use { it.readText() }
    }

    private class RecordingMinioObjectClient : MinioObjectClient {
        val uploads = mutableListOf<Upload>()

        override fun putObject(
            bucket: String,
            objectKey: String,
            bytes: ByteArray,
            contentType: String,
            headers: Map<String, String>,
        ) {
            uploads += Upload(
                bucket = bucket,
                objectKey = objectKey,
                bytes = bytes,
                contentType = contentType,
                headers = headers,
            )
        }
    }

    private data class Upload(
        val bucket: String,
        val objectKey: String,
        val bytes: ByteArray,
        val contentType: String,
        val headers: Map<String, String>,
    )
}
