package com.ugreen.iot.soluna.autotest.schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.ugreen.iot.soluna.autotest.dsl.YamlPlanParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonSchemaDslValidatorTest {
    private val validator = JsonSchemaDslValidator()
    private val jsonMapper = ObjectMapper()
    private val yamlMapper = YamlPlanParser.defaultYamlMapper()

    @Test
    fun `validates parameter data schema`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "id": "default",
              "values": {
                "account": {
                  "username": "demo"
                }
              },
              "secrets": ["account.password"]
            }
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/parameter-data.schema.json", node),
        )
    }

    @Test
    fun `validates per device config schema`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "id": "iphone-001",
              "device": {
                "platform": "ios",
                "udid": "00008030-001C195E0E91802E"
              },
              "appium": {
                "server": {
                  "managed": true,
                  "host": "127.0.0.1",
                  "port": 4723
                },
                "capabilities": {
                  "platformName": "iOS"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/device-config.schema.json", node),
        )
    }

    @Test
    fun `validates example parameter data schema`() {
        listOf(
            Path.of("examples/data/default.yaml"),
            Path.of("examples/data/ugreen-profile.yaml"),
        ).forEach { path ->
            val node = yamlMapper.readTree(Files.readString(path))

            assertEquals(
                emptyList(),
                validator.validate("/schemas/v1/parameter-data.schema.json", node),
                "Expected valid parameter data schema for $path",
            )
        }
    }

    @Test
    fun `validates example per device config schema`() {
        listOf(
            Path.of("examples/devices/00008140-001805D80C93801C.yaml"),
            Path.of("examples/devices/00008150-001E15AA1140401C.yaml"),
            Path.of("examples/devices/AMRF026323000807.yaml"),
        ).forEach { path ->
            val node = yamlMapper.readTree(Files.readString(path))

            assertEquals(
                emptyList(),
                validator.validate("/schemas/v1/device-config.schema.json", node),
                "Expected valid device config schema for $path",
            )
        }
    }

    @Test
    fun `validates example artifact store schema`() {
        val path = Path.of("examples/artifacts/minio.yaml")
        val node = yamlMapper.readTree(Files.readString(path))

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/artifact-store.schema.json", node),
            "Expected valid artifact store schema for $path",
        )
    }

    @Test
    fun `validates example notification sender schema`() {
        val path = Path.of("examples/artifacts/dingtalk-upload-alert.yaml")
        val node = yamlMapper.readTree(Files.readString(path))

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/notification-sender.schema.json", node),
            "Expected valid notification sender schema for $path",
        )
    }

    @Test
    fun `validates empty plan resource manifest schema`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "plan": {
                "planId": "plan-001",
                "planName": "Plan 001",
                "metadata": {}
              },
              "resourceBatch": {
                "runId": "run-001",
                "generatedAt": "2026-06-13T00:00:00Z"
              },
              "resources": []
            }
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/plan-resource-manifest.schema.json", node),
        )
    }

    @Test
    fun `validates report data schema`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "generatedAt": "2026-06-13T00:00:00Z",
              "runId": "run-001",
              "planId": "plan-001",
              "planName": "Plan 001",
              "status": "failed",
              "deviceId": "android-device",
              "platform": "android",
              "traceArtifacts": [
                {
                  "captureId": "trace-001",
                  "stageId": "stage-001",
                  "caseId": "case-001",
                  "actionId": "tap-login",
                  "actionKeyword": "tap",
                  "phase": "case.action",
                  "index": 1,
                  "attempt": 1,
                  "timing": "before",
                  "href": "https://minio.example/runs/run-001/diagnostics/trace-001.png",
                  "contentType": "image/png",
                  "sizeBytes": 123,
                  "capturedAt": "2026-06-13T00:00:01Z"
                }
              ],
              "setupActions": [],
              "teardownActions": [],
              "stages": [
                {
                  "stageId": "stage-001",
                  "status": "failed",
                  "setupActions": [],
                  "teardownActions": [],
                  "cases": [
                    {
                      "caseId": "case-001",
                      "status": "failed",
                      "setupActions": [],
                      "teardownActions": [],
                      "actions": [
                        {
                          "index": 1,
                          "phase": "case.action",
                          "status": "failed",
                          "message": null,
                          "error": "boom"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/report-data.schema.json", node),
        )
    }

    @Test
    fun `validates referenced case element and fragment examples`() {
        val examples = mapOf(
            "/schemas/v1/case.schema.json" to listOf(
                Path.of("examples/cases/ugreen-profile-nickname.yaml"),
                Path.of("examples/cases/ugreen-profile-nickname-ios.yaml"),
            ),
            "/schemas/v1/element-catalog.schema.json" to listOf(
                Path.of("examples/elements/daily-smoke.yaml"),
                Path.of("examples/elements/ugreen-profile.yaml"),
            ),
            "/schemas/v1/fragment-catalog.schema.json" to listOf(Path.of("examples/fragments/app-lifecycle.yaml")),
        )

        examples.forEach { (schema, paths) ->
            paths.forEach { path ->
                val node = yamlMapper.readTree(Files.readString(path))

                assertEquals(
                    emptyList(),
                    validator.validate(schema, node),
                    "Expected valid schema $schema for $path",
                )
            }
        }
    }

    @Test
    fun `validates explicit assertion action schemas`() {
        val node = yamlMapper.readTree(
            """
            schemaVersion: "1.0"
            id: assertion-actions
            name: Assertion Actions
            actions:
              - assertElementAttrEquals: assert-title
                element: home.title
                attr: name/label/text
                expected: "${'$'}{home.expectedTitle}"
              - assertElementAttrRegexMatch: assert-title-pattern
                element: home.title
                attr: name/label/text
                pattern: "Soluna"
              - assertSourceRegexMatch: assert-source
                pattern: "XCUIElementType.*Soluna"
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/case.schema.json", node),
        )
    }

    @Test
    fun `rejects removed generic assert equals action schema`() {
        val node = yamlMapper.readTree(
            """
            schemaVersion: "1.0"
            id: generic-assert
            name: Generic Assert
            actions:
              - assertEquals: assert-title
                element: home.title
                expected: "${'$'}{home.expectedTitle}"
            """.trimIndent(),
        )

        val violations = validator.validate("/schemas/v1/case.schema.json", node)

        assertTrue(
            violations.isNotEmpty(),
            "Expected generic assertEquals action to be rejected",
        )
    }

    @Test
    fun `rejects plan without device config reference`() {
        val node = yamlMapper.readTree(
            """
            schemaVersion: "1.0"
            id: missing-device-config
            name: Missing Device Config
            stages:
              - id: main
                name: Main
                cases:
                  - id: smoke
                    name: Smoke
                    actions:
                      - tap: tap-smoke
                        xRatio: 0.5
                        yRatio: 0.5
            """.trimIndent(),
        )

        val violations = validator.validate("/schemas/v1/plan.schema.json", node)

        assertTrue(
            violations.any { it.message.contains("deviceConfig") },
            "Expected required deviceConfig violation, got $violations",
        )
    }

    @Test
    fun `rejects invalid per device config`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "id": "device-001",
              "device": {
                "platform": "web",
                "udid": "abc"
              },
              "appium": {
                "server": {
                  "managed": true
                }
              }
            }
            """.trimIndent(),
        )

        val violations = validator.validate("/schemas/v1/device-config.schema.json", node)

        assertTrue(
            violations.any { it.message.contains("android") || it.message.contains("ios") || it.path.contains("platform") },
            "Expected platform enum violation, got $violations",
        )
    }
}
