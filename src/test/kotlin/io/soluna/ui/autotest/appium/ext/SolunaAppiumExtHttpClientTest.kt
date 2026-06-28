package io.soluna.ui.autotest.appium.ext

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolunaAppiumExtHttpClientTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `gets device by udid`() {
        val transport = RecordingTransport { request ->
            assertEquals("GET", request.method)
            assertEquals("/soluna/device", request.path)
            assertEquals("abc123", request.query["udid"])
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "value": {
                        "exists": true,
                        "device": {
                          "platform": "android",
                          "udid": "abc123",
                          "name": "Pixel 8",
                          "model": "Pixel 8",
                          "osVersion": "14"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).getDevice("abc123")

        assertTrue(result.exists)
        assertEquals(Platform.ANDROID, result.device?.platform)
        assertEquals("Pixel 8", result.device?.name)
    }

    @Test
    fun `returns missing device response for 404`() {
        val transport = RecordingTransport {
            SolunaAppiumExtHttpResponse(
                statusCode = 404,
                body = """
                    {
                      "value": {
                        "exists": false,
                        "message": "Device 'missing' not found on this host"
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).getDevice("missing")

        assertFalse(result.exists)
        assertEquals("Device 'missing' not found on this host", result.message)
    }

    @Test
    fun `lists devices`() {
        val transport = RecordingTransport { request ->
            assertEquals("GET", request.method)
            assertEquals("/soluna/devices", request.path)
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "value": {
                        "count": 2,
                        "devices": [
                          {
                            "platform": "android",
                            "udid": "android-1",
                            "name": "Pixel",
                            "model": "Pixel",
                            "osVersion": "14"
                          },
                          {
                            "platform": "ios",
                            "udid": "ios-1",
                            "name": "iPhone",
                            "model": "iPhone15,4",
                            "osVersion": "17.5"
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).listDevices()

        assertEquals(2, result.count)
        assertEquals(listOf(Platform.ANDROID, Platform.IOS), result.devices.map { it.platform })
    }

    @Test
    fun `gets installed app by id`() {
        val transport = RecordingTransport { request ->
            assertEquals("GET", request.method)
            assertEquals("/soluna/app", request.path)
            assertEquals("abc123", request.query["udid"])
            assertEquals("com.example.app", request.query["appId"])
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "value": {
                        "exists": true,
                        "app": {
                          "platform": "android",
                          "udid": "abc123",
                          "appId": "com.example.app",
                          "name": "Example App",
                          "version": "1.2.3",
                          "versionCode": "42"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).getApp("abc123", "com.example.app")

        assertTrue(result.exists)
        assertEquals("Example App", result.app?.name)
        assertEquals(Platform.ANDROID, result.app?.platform)
    }

    @Test
    fun `returns missing app response for 404`() {
        val transport = RecordingTransport {
            SolunaAppiumExtHttpResponse(
                statusCode = 404,
                body = """
                    {
                      "value": {
                        "exists": false,
                        "message": "App not installed"
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).getApp("abc123", "missing")

        assertFalse(result.exists)
        assertEquals("App not installed", result.message)
    }

    @Test
    fun `gets WDA bundle by udid`() {
        val transport = RecordingTransport { request ->
            assertEquals("GET", request.method)
            assertEquals("/soluna/ios/wda-bundle", request.path)
            assertEquals("ios-001", request.query["udid"])
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "value": {
                        "exists": true,
                        "udid": "ios-001",
                        "bundleId": "com.facebook.WebDriverAgentRunner.xctrunner",
                        "app": {
                          "bundleId": "com.facebook.WebDriverAgentRunner.xctrunner",
                          "name": "WebDriverAgentRunner-Runner",
                          "version": "1.0"
                        }
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).getWdaBundle("ios-001")

        assertTrue(result.exists)
        assertEquals("com.facebook.WebDriverAgentRunner.xctrunner", result.bundleId)
        assertEquals("WebDriverAgentRunner-Runner", result.app?.name)
    }

    @Test
    fun `returns missing WDA bundle response for 404`() {
        val transport = RecordingTransport {
            SolunaAppiumExtHttpResponse(
                statusCode = 404,
                body = """
                    {
                      "value": {
                        "exists": false,
                        "udid": "ios-001",
                        "message": "WDA runner bundle was not found"
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).getWdaBundle("ios-001")

        assertFalse(result.exists)
        assertEquals("WDA runner bundle was not found", result.message)
    }

    @Test
    fun `executes command and serializes tool wire value`() {
        val transport = RecordingTransport { request ->
            assertEquals("POST", request.method)
            assertEquals("/soluna/command", request.path)
            val requestBody = objectMapper.readTree(request.body!!)
            assertEquals("adb", requestBody.path("tool").asText())
            assertEquals("devices", requestBody.path("args").first().asText())
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "value": {
                        "ok": true,
                        "command": "adb",
                        "args": ["devices"],
                        "exitCode": 0,
                        "timedOut": false,
                        "truncated": false,
                        "durationMs": 12,
                        "stdout": "List of devices attached",
                        "stderr": ""
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).executeCommand(
            CommandExecuteRequest(
                tool = SupportedCommandTool.ADB,
                args = listOf("devices"),
            ),
        )

        assertTrue(result.ok)
        assertEquals("adb", result.command)
    }

    @Test
    fun `returns command result for non zero exit 422`() {
        val transport = RecordingTransport {
            SolunaAppiumExtHttpResponse(
                statusCode = 422,
                body = """
                    {
                      "value": {
                        "ok": false,
                        "command": "adb",
                        "args": ["bad"],
                        "exitCode": 1,
                        "timedOut": false,
                        "truncated": false,
                        "durationMs": 12,
                        "stdout": "",
                        "stderr": "bad command"
                      }
                    }
                """.trimIndent(),
            )
        }

        val result = SolunaAppiumExtHttpClient(transport).executeCommand(
            CommandExecuteRequest(
                tool = SupportedCommandTool.ADB,
                args = listOf("bad"),
            ),
        )

        assertFalse(result.ok)
        assertEquals(1, result.exitCode)
        assertEquals("bad command", result.stderr)
    }

    @Test
    fun `maps log session lifecycle`() {
        val transport = QueueTransport(
            SolunaAppiumExtHttpResponse(
                statusCode = 201,
                body = sessionBody(nextSeq = 1, entries = false),
            ),
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = sessionBody(nextSeq = 2, entries = true),
            ),
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = """{"value":{"sessionId":"s1","removed":true}}""",
            ),
        )
        val client = SolunaAppiumExtHttpClient(transport)

        val created = client.createLogSession(CreateLogSessionRequest(udid = "abc123"))
        val read = client.readLogSession(ReadLogSessionRequest(sessionId = "s1", cursor = 0, limit = 10))
        val deleted = client.deleteLogSession(DeleteLogSessionRequest(sessionId = "s1"))

        assertEquals(LogSessionStatus.RUNNING, created.session.status)
        assertEquals(1, read.entries.size)
        assertEquals(LogLineSource.STDOUT, read.entries.single().source)
        assertTrue(deleted.removed)
        assertEquals(
            listOf(
                "/soluna/logs/sessions",
                "/soluna/logs/sessions/s1",
                "/soluna/logs/sessions/s1",
            ),
            transport.requests.map { it.path },
        )
        assertEquals("0", transport.requests[1].query["cursor"])
        assertEquals("10", transport.requests[1].query["limit"])
    }

    @Test
    fun `throws typed exception for unexpected error response`() {
        val transport = RecordingTransport {
            SolunaAppiumExtHttpResponse(
                statusCode = 400,
                body = """{"value":{"error":"invalid_argument","message":"bad request"}}""",
            )
        }

        val error = assertFailsWith<SolunaAppiumExtHttpException> {
            SolunaAppiumExtHttpClient(transport).listDevices()
        }

        assertEquals(400, error.statusCode)
        assertEquals("invalid_argument", error.error)
        assertEquals("bad request", error.detail)
    }

    @Test
    fun `throws typed exception for invalid json response`() {
        val transport = RecordingTransport {
            SolunaAppiumExtHttpResponse(
                statusCode = 200,
                body = "not-json",
            )
        }

        val error = assertFailsWith<SolunaAppiumExtHttpException> {
            SolunaAppiumExtHttpClient(transport).listDevices()
        }

        assertEquals("invalid_json", error.error)
    }

    private fun sessionBody(
        nextSeq: Int,
        entries: Boolean,
    ): String {
        val entriesJson = if (entries) {
            """
            ,
            "cursor": 0,
            "nextCursor": 1,
            "cursorAdjusted": false,
            "entries": [
              {
                "seq": 0,
                "ts": "1970-01-01T00:00:00.000Z",
                "platform": "android",
                "udid": "abc123",
                "source": "stdout",
                "message": "hello",
                "raw": "hello"
              }
            ]
            """.trimIndent()
        } else {
            ""
        }
        return """
            {
              "value": {
                "session": {
                  "sessionId": "s1",
                  "udid": "abc123",
                  "platform": "android",
                  "status": "running",
                  "command": "adb",
                  "args": ["-s", "abc123", "logcat"],
                  "startedAt": "1970-01-01T00:00:00.000Z",
                  "lastActivityAt": "1970-01-01T00:00:00.000Z",
                  "ttlMs": 600000,
                  "nextSeq": $nextSeq,
                  "minSeq": 0,
                  "droppedCount": 0,
                  "maxBufferEntries": 1000,
                  "maxSessionBytes": 104857600
                }
                $entriesJson
              }
            }
        """.trimIndent()
    }

    private class RecordingTransport(
        private val handler: (SolunaAppiumExtHttpRequest) -> SolunaAppiumExtHttpResponse,
    ) : SolunaAppiumExtTransport {
        val requests = mutableListOf<SolunaAppiumExtHttpRequest>()

        override fun exchange(request: SolunaAppiumExtHttpRequest): SolunaAppiumExtHttpResponse {
            requests += request
            return handler(request)
        }
    }

    private class QueueTransport(
        vararg responses: SolunaAppiumExtHttpResponse,
    ) : SolunaAppiumExtTransport {
        private val responses = ArrayDeque(responses.toList())
        val requests = mutableListOf<SolunaAppiumExtHttpRequest>()

        override fun exchange(request: SolunaAppiumExtHttpRequest): SolunaAppiumExtHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
