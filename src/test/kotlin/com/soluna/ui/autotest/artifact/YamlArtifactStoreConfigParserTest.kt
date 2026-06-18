package com.soluna.ui.autotest.artifact

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class YamlArtifactStoreConfigParserTest {
    @Test
    fun `parses minio artifact store config using direct credentials`() {
        val config = YamlArtifactStoreConfigParser().parse(
            Files.readString(Path.of("examples/artifacts/minio.yaml")),
        )

        assertEquals("aiot-minio", config.id)
        assertEquals("minio", config.type)
        assertEquals("http://aiot-testing.ugreeniot.com:19000", config.endpointWithScheme())
        assertEquals(false, config.secure)
        assertEquals("autotest-reports", config.bucket)
        assertEquals("soluna-ui-autotest", config.normalizedPrefix())
        assertEquals("minio-access-key", config.credentials.accessKey)
        assertEquals("minio-secret-key", config.credentials.secretKey)
        assertEquals(true, config.upload.compression.enabled)
        assertEquals(0L, config.upload.compression.minBytes)
        assertEquals("application/json", config.upload.compression.contentTypes[1])
        assertEquals(5, config.upload.retry.maxAttempts)
        assertEquals("./dingtalk-upload-alert.yaml", config.notifications.uploadFailures)
        assertEquals("./dingtalk-upload-alert.yaml", config.notifications.planStarted)
        assertEquals("./dingtalk-upload-alert.yaml", config.notifications.testFinished)
        assertEquals("./dingtalk-upload-alert.yaml", config.notifications.reportPublished)
        assertEquals(null, config.notifications.planFinished)
    }
}
