package io.soluna.ui.autotest.notification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DingTalkRobotNotificationSenderTest {
    @Test
    fun `sends markdown message to direct DingTalk webhook`() {
        val transport = RecordingDingTalkTransport()
        val sender = DingTalkRobotNotificationSender(
            robot = DingTalkRobotDefinition(
                webhook = "https://oapi.dingtalk.com/robot/send?access_token=token",
                secret = "secret-value",
                atMobiles = listOf("13800000000"),
            ),
            getenv = { null },
            transport = transport,
            clock = Clock.fixed(Instant.ofEpochMilli(1234567890), ZoneOffset.UTC),
        )

        val result = sender.send(
            NotificationMessage(
                title = "Upload Failure",
                markdown = "### Upload Failure\n- failed",
            ),
        )

        assertEquals(true, result.delivered)
        assertTrue(transport.url.startsWith("https://oapi.dingtalk.com/robot/send?access_token=token&timestamp=1234567890&sign="))
        assertTrue(transport.body.contains("\"msgtype\":\"markdown\""))
        assertTrue(transport.body.contains("\"title\":\"Upload Failure\""))
        assertTrue(transport.body.contains("13800000000"))
    }

    @Test
    fun `marks DingTalk errcode response as not delivered`() {
        val transport = RecordingDingTalkTransport(
            response = DingTalkHttpResponse(
                statusCode = 200,
                body = """{"errcode":310000,"errmsg":"keywords not in content"}""",
            ),
        )
        val sender = DingTalkRobotNotificationSender(
            robot = DingTalkRobotDefinition(
                webhook = "https://oapi.dingtalk.com/robot/send?access_token=token",
            ),
            transport = transport,
        )

        val result = sender.send(
            NotificationMessage(
                title = "Upload Failure",
                markdown = "not matching",
            ),
        )

        assertEquals(false, result.delivered)
        assertEquals("keywords not in content", result.error)
    }

    private class RecordingDingTalkTransport(
        private val response: DingTalkHttpResponse = DingTalkHttpResponse(statusCode = 200, body = """{"errcode":0}"""),
    ) : DingTalkRobotTransport {
        lateinit var url: String
        lateinit var body: String

        override fun postJson(
            url: String,
            body: String,
        ): DingTalkHttpResponse {
            this.url = url
            this.body = body
            return response
        }
    }
}
