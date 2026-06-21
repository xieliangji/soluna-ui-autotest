package com.soluna.ui.autotest.notification

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class YamlNotificationSenderConfigParserTest {
    @Test
    fun `parses DingTalk upload alert config with direct robot credentials`() {
        val config = YamlNotificationSenderConfigParser().parse(
            Files.readString(Path.of("AIot-Tests/artifacts/dingtalk.template.yaml")),
        )

        assertEquals("dingtalk-upload-alert", config.id)
        assertEquals("dingtalk", config.type)
        assertEquals(
            "https://oapi.dingtalk.com/robot/send?access_token=dingtalk-access-token",
            config.robot.webhook,
        )
        assertEquals(
            "SEC_dingtalk_sign_secret",
            config.robot.secret,
        )
        assertEquals(5, config.uploadFailurePolicy.threshold)
    }
}
