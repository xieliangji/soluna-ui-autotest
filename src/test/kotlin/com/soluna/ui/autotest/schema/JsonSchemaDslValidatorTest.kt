package com.soluna.ui.autotest.schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.soluna.ui.autotest.dsl.YamlPlanParser
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
            Path.of("AIot-Tests/apps/com.ugreen.iot/data/app-state.yaml"),
            Path.of("AIot-Tests/apps/com.ugreen.iot/data/common/mine.yaml"),
            Path.of("AIot-Tests/apps/com.ugreen.iot/data/common/visual-templates.yaml"),
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
            Path.of("AIot-Tests/devices/android/3B6F6KE910B3QRDN.debug.yaml"),
            Path.of("AIot-Tests/devices/android/AMRF026323000807.yaml"),
            Path.of("AIot-Tests/devices/android/ZT4225X3C2.yaml"),
            Path.of("AIot-Tests/devices/ios/00008140-001805D80C93801C.yaml"),
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
        val path = Path.of("AIot-Tests/artifacts/minio.template.yaml")
        val node = yamlMapper.readTree(Files.readString(path))

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/artifact-store.schema.json", node),
            "Expected valid artifact store schema for $path",
        )
    }

    @Test
    fun `validates example notification sender schema`() {
        val path = Path.of("AIot-Tests/artifacts/dingtalk.template.yaml")
        val node = yamlMapper.readTree(Files.readString(path))

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/notification-sender.schema.json", node),
            "Expected valid notification sender schema for $path",
        )
    }

    @Test
    fun `validates asset project schema example`() {
        val path = Path.of("AIot-Tests/soluna-project.yaml")
        val node = yamlMapper.readTree(Files.readString(path))

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/soluna-project.schema.json", node),
            "Expected valid Soluna asset project schema for $path",
        )
    }

    @Test
    fun `validates AIot asset project plan schemas`() {
        listOf(
            Path.of("AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml"),
            Path.of("AIot-Tests/apps/com.ugreen.iot/plans/app-state/ios.yaml"),
            Path.of("AIot-Tests/apps/com.ugreen.iot/plans/common/android.yaml"),
            Path.of("AIot-Tests/apps/com.ugreen.iot/plans/common/ios.yaml"),
        ).forEach { path ->
            val node = yamlMapper.readTree(Files.readString(path))

            assertEquals(
                emptyList(),
                validator.validate("/schemas/v1/plan.schema.json", node),
                "Expected valid plan schema for $path",
            )
        }
    }

    @Test
    fun `validates runner request schema`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "runId": "run-001",
              "planUri": "git://AIot-Tests/apps/com.ugreen.iot/plans/profile/nickname.yaml",
              "assetProject": {
                "id": "aiot-tests",
                "uri": "git://AIot-Tests",
                "revision": "commit-sha",
                "root": "."
              },
              "device": {
                "udid": "00008140-001805D80C93801C",
                "platform": "ios",
                "configUri": "devices/ios/00008140-001805D80C93801C.yaml"
              },
              "parameterOverrides": {
                "profile.newNickname": "SolunaTester"
              },
              "metadata": {
                "requestedBy": "platform"
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/run-request.schema.json", node),
        )
    }

    @Test
    fun `validates runner result summary schema`() {
        val node = jsonMapper.readTree(
            """
            {
              "schemaVersion": "1.0",
              "runId": "run-001",
              "status": "passed",
              "generatedAt": "2026-06-15T00:00:00Z",
              "startedAt": "2026-06-15T00:00:00Z",
              "finishedAt": "2026-06-15T00:01:00Z",
              "durationMs": 60000,
              "plan": {
                "id": "profile-nickname",
                "name": "Profile Nickname",
                "uri": "apps/com.ugreen.iot/plans/profile/nickname.yaml"
              },
              "assetProject": {
                "id": "aiot-tests",
                "uri": "git://AIot-Tests",
                "revision": "commit-sha"
              },
              "device": {
                "udid": "00008140-001805D80C93801C",
                "platform": "ios",
                "name": "iPhone"
              },
              "counts": {
                "stageCount": 1,
                "caseCount": 1,
                "actionCount": 12,
                "passedCount": 12,
                "failedCount": 0,
                "skippedCount": 0
              },
              "artifacts": {
                "reportUrl": "https://minio.example/runs/run-001/report/index.html",
                "resultJsonUrl": "https://minio.example/runs/run-001/report/execution-result.json",
                "resourceManifestUrl": "https://minio.example/runs/run-001/report/plan-resource-manifest.json",
                "artifactRootUrl": "https://minio.example/runs/run-001/"
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/run-result.schema.json", node),
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
                Path.of("AIot-Tests/apps/com.ugreen.iot/cases/common/app-state/login-page.yaml"),
                Path.of("AIot-Tests/apps/com.ugreen.iot/cases/common/app-state/guest-device-page.yaml"),
                Path.of("AIot-Tests/apps/com.ugreen.iot/cases/common/app-state/logged-in-device-page.yaml"),
                Path.of("AIot-Tests/apps/com.ugreen.iot/cases/common/TC001_MINE_ABOUT.yaml"),
            ),
            "/schemas/v1/element-catalog.schema.json" to listOf(
                Path.of("AIot-Tests/apps/com.ugreen.iot/elements/common.yaml"),
            ),
            "/schemas/v1/fragment-catalog.schema.json" to listOf(
                Path.of("AIot-Tests/apps/com.ugreen.iot/fragments/app-lifecycle.yaml"),
                Path.of("AIot-Tests/apps/com.ugreen.iot/fragments/app-state.yaml"),
            ),
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
              - assertElementExists: assert-title-exists
                element: home.title
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
    fun `validates screen recording text assertion action schemas`() {
        val node = yamlMapper.readTree(
            """
            schemaVersion: "1.0"
            id: recording-toast-actions
            name: Recording Toast Actions
            actions:
              - startScreenRecording:
                  id: start-toast-recording
                  timeLimitMs: 6000
              - tap:
                  id: submit-form
                  element: feedback.submit
              - stopScreenRecording:
                  id: stop-toast-recording
                  resourceId: toast-recording
                  saveAs: toastVideo
              - assertScreenRecordingTextRegexMatch:
                  id: assert-toast
                  source: "@{case.toastVideo}"
                  pattern: "提交成功"
                  framesPerSecond: 8
                  maxFrames: 60
                  resourceId: toast-frame
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/case.schema.json", node),
        )
    }

    @Test
    fun `validates fragment generic control flow schema`() {
        val node = yamlMapper.readTree(
            """
            schemaVersion: "1.0"
            id: app-state
            fragments:
              ensureLoginPage:
                elementRefs:
                  - id: common
                    file: ../elements/common.yaml
                actions:
                  - if:
                      assertElementExists: detect-login-page
                      element: common.loginPageMarker
                    then: []
                    else:
                      - tap: open-mine-tab
                        element: common.mineTab
            """.trimIndent(),
        )

        assertEquals(
            emptyList(),
            validator.validate("/schemas/v1/fragment-catalog.schema.json", node),
        )
    }

    @Test
    fun `rejects case generic control flow schema`() {
        val node = yamlMapper.readTree(
            """
            schemaVersion: "1.0"
            id: if-in-case
            name: If In Case
            actions:
              - if:
                  assertSourceRegexMatch: detect-login-page
                  pattern: "Login"
                then:
                  - tap: tap-login
                    element: login.button
            """.trimIndent(),
        )

        val violations = validator.validate("/schemas/v1/case.schema.json", node)

        assertTrue(
            violations.isNotEmpty(),
            "Expected case schema to reject if/then/else",
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
