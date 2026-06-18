package com.soluna.ui.autotest.notification

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class NotificationMessage(
    val title: String,
    val markdown: String,
)

data class NotificationSendResult(
    val delivered: Boolean,
    val statusCode: Int? = null,
    val error: String? = null,
)

interface NotificationSender {
    fun send(message: NotificationMessage): NotificationSendResult
}

object DefaultNotificationSenderFactory {
    fun create(config: NotificationSenderConfigDefinition): NotificationSender {
        require(config.type == "dingtalk") { "Unsupported notification sender type '${config.type}'" }
        return DingTalkRobotNotificationSender(config.robot)
    }
}

class DingTalkRobotNotificationSender(
    private val robot: DingTalkRobotDefinition,
    private val getenv: (String) -> String? = System::getenv,
    private val transport: DingTalkRobotTransport = JavaNetDingTalkRobotTransport(),
    private val clock: Clock = Clock.systemUTC(),
) : NotificationSender {
    private val objectMapper = jacksonObjectMapper()

    override fun send(message: NotificationMessage): NotificationSendResult {
        val webhook = robot.webhook
            ?: robot.webhookEnv?.let { getenv(it) }
            ?: return NotificationSendResult(
                delivered = false,
                error = "missing DingTalk webhook; set robot.webhook or robot.webhookEnv",
            )
        val secret = robot.secret ?: robot.secretEnv?.let { secretEnv ->
            getenv(secretEnv) ?: return NotificationSendResult(
                delivered = false,
                error = "missing DingTalk secret; set robot.secret or environment variable '$secretEnv'",
            )
        }
        val url = if (secret.isNullOrBlank()) {
            webhook
        } else {
            webhook.signedDingTalkUrl(secret)
        }
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "msgtype" to "markdown",
                "markdown" to mapOf(
                    "title" to message.title,
                    "text" to message.markdown,
                ),
                "at" to mapOf(
                    "atMobiles" to robot.atMobiles,
                    "atUserIds" to robot.atUserIds,
                    "isAtAll" to robot.isAtAll,
                ),
            ),
        )

        return runCatching { transport.postJson(url, payload) }
            .map { response ->
                val dingTalkError = response.dingTalkError()
                NotificationSendResult(
                    delivered = response.statusCode in 200..299 && dingTalkError == null,
                    statusCode = response.statusCode,
                    error = if (response.statusCode in 200..299) dingTalkError else response.body,
                )
            }
            .getOrElse { err ->
                NotificationSendResult(
                    delivered = false,
                    error = err.message ?: err::class.simpleName ?: "DingTalk notification failed",
                )
            }
    }

    private fun String.signedDingTalkUrl(secret: String): String {
        val timestamp = clock.millis().toString()
        val stringToSign = "$timestamp\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val sign = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8)))
        val separator = if (contains("?")) "&" else "?"
        return "$this${separator}timestamp=$timestamp&sign=${sign.urlEncode()}"
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8)
    }

    private fun DingTalkHttpResponse.dingTalkError(): String? {
        return runCatching {
            val node = objectMapper.readTree(body)
            val errcode = node.path("errcode").takeIf { it.isNumber }?.asInt()
            if (errcode == null || errcode == 0) {
                null
            } else {
                node.path("errmsg").takeIf { it.isTextual }?.asText() ?: body
            }
        }.getOrNull()
    }
}

data class DingTalkHttpResponse(
    val statusCode: Int,
    val body: String,
)

interface DingTalkRobotTransport {
    fun postJson(
        url: String,
        body: String,
    ): DingTalkHttpResponse
}

class JavaNetDingTalkRobotTransport(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : DingTalkRobotTransport {
    override fun postJson(
        url: String,
        body: String,
    ): DingTalkHttpResponse {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return DingTalkHttpResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }
}
