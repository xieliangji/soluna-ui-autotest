package io.soluna.ui.autotest.dsl

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YamlPlanParserTest {
    private val parser = YamlPlanParser()
    private val elementCatalogParser = YamlElementCatalogParser()
    private val fragmentCatalogParser = YamlFragmentCatalogParser()

    @Test
    fun `parses valid plan yaml`() {
        val plan = parser.parse(validPlanYaml())

        assertEquals("daily-smoke", plan.id)
        assertEquals("Daily Smoke", plan.name)
        assertEquals(3_000, plan.defaults.implicitWaitMs)
        assertEquals("logged-out", plan.stages.single().id)
        assertEquals("login-success", plan.stages.single().cases.single().id)

        val action = plan.stages.single().cases.single().actions.first()
        assertEquals("tap", action.keyword)
        assertEquals("tap-login", action.id)
        assertEquals("login.loginButton", action.element)
        assertEquals(null, action.locator)
    }

    @Test
    fun `parses example plan`() {
        val planYaml = Files.readString(Path.of("AIot-Tests/apps/com.ugreen.iot/plans/app-state/android.yaml"))

        val plan = parser.parse(planYaml)

        assertEquals("ugreen-app-state-android", plan.id)
        assertEquals("app-state", plan.stages.single().id)
        assertEquals(3, plan.stages.single().caseRefs.size)
    }

    @Test
    fun `normalizes chinese action keyword`() {
        assertEquals("tap", DefaultKeywordRegistry.normalize("点击"))
        assertEquals("tapPosition", DefaultKeywordRegistry.normalize("按位置点击"))
        assertEquals("swipe", DefaultKeywordRegistry.normalize("滑动"))
        assertEquals("screenshot", DefaultKeywordRegistry.normalize("显式截图"))
        assertEquals("assertElementExists", DefaultKeywordRegistry.normalize("元素存在"))
        assertEquals("assertImageColorRatio", DefaultKeywordRegistry.normalize("图片含蓝色量"))
    }

    @Test
    fun `parses nested action keyword payload`() {
        val yaml = """
            schemaVersion: "1.0"
            id: nested-actions
            name: Nested Actions
            productModel: Test Product
            deviceConfig: devices/android.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: login-success
                    name: Login success
                    actions:
                      - input:
                          id: input-username
                          element: login.usernameInput
                          value: "${'$'}{account.username}"
                          clearFirst: true
                          desc: 输入账号
                      - assertElementAttrRegexMatch:
                          id: assert-login-marker
                          element: login.logo
                          attr: name/label/text
                          pattern: "${'$'}{appState.patterns.loginPage}"
                          wait:
                            timeoutMs: 3000
                            intervalMs: 500
                      - swipe:
                          id: scroll-login-page
                          startXRatio: 0.5
                          startYRatio: 0.8
                          endXRatio: 0.5
                          endYRatio: 0.2
                          durationMs: 600
                      - assertImageColorRatio:
                          id: assert-blue-dot
                          source: "@{case.mapScreenshot}"
                          color: blue
                          minRatio: 0.001
                      - tap:
                          id: dismiss-firmware-prompt-if-present
                          element: common.firmwareUpgradeIgnoreButton
                          ignoreMissingElement: true
                          ignoreMissingElementReason: optionalFirmwareUpgradePrompt
        """.trimIndent()

        val plan = parser.parse(yaml)
        val actions = plan.stages.single().cases.single().actions

        assertEquals("input", actions[0].keyword)
        assertEquals("input-username", actions[0].id)
        assertEquals("login.usernameInput", actions[0].element)
        assertEquals("${'$'}{account.username}", actions[0].value?.asText())
        assertEquals(true, actions[0].args["clearFirst"]?.asBoolean())
        assertEquals("assertElementAttrRegexMatch", actions[1].keyword)
        assertEquals(3_000, actions[1].wait?.timeoutMs)
        assertEquals("swipe", actions[2].keyword)
        assertEquals(0.8, actions[2].args["startYRatio"]?.asDouble())
        assertEquals(600, actions[2].args["durationMs"]?.asInt())
        assertEquals("assertImageColorRatio", actions[3].keyword)
        assertEquals("blue", actions[3].args["color"]?.asText())
        assertEquals(0.001, actions[3].args["minRatio"]?.asDouble())
        assertEquals("tap", actions[4].keyword)
        assertEquals(true, actions[4].args["ignoreMissingElement"]?.asBoolean())
        assertEquals("optionalFirmwareUpgradePrompt", actions[4].args["ignoreMissingElementReason"]?.asText())
    }

    @Test
    fun `normalizes tap position element ratios to tap executor args`() {
        val yaml = """
            schemaVersion: "1.0"
            id: tap-position-actions
            name: Tap Position Actions
            productModel: Test Product
            deviceConfig: devices/ios.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: slider
                    name: Slider
                    actions:
                      - tapPosition:
                          id: set-volume
                          element: settings.volumeSlider
                          xRatio: 0.64
                          yRatio: 0.30
                      - tapPosition:
                          id: dismiss-backdrop
                          xRatio: 0.50
                          yRatio: 0.10
        """.trimIndent()

        val actions = parser.parse(yaml).stages.single().cases.single().actions

        assertEquals("tap", actions[0].keyword)
        assertEquals("settings.volumeSlider", actions[0].element)
        assertEquals(0.64, actions[0].args["elementXRatio"]?.asDouble())
        assertEquals(0.30, actions[0].args["elementYRatio"]?.asDouble())
        assertEquals(null, actions[0].args["xRatio"])
        assertEquals(null, actions[0].args["yRatio"])
        assertEquals("tap", actions[1].keyword)
        assertEquals(0.50, actions[1].args["xRatio"]?.asDouble())
        assertEquals(0.10, actions[1].args["yRatio"]?.asDouble())
    }

    @Test
    fun `parses screen recording action payloads`() {
        val yaml = """
            schemaVersion: "1.0"
            id: recording-actions
            name: Recording Actions
            productModel: Test Product
            deviceConfig: devices/android.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: toast-check
                    name: Toast Check
                    actions:
                      - startScreenRecording:
                          id: start-toast-recording
                          timeLimitMs: 6000
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
        """.trimIndent()

        val actions = parser.parse(yaml).stages.single().cases.single().actions

        assertEquals("startScreenRecording", actions[0].keyword)
        assertEquals(6000, actions[0].args["timeLimitMs"]?.asInt())
        assertEquals("stopScreenRecording", actions[1].keyword)
        assertEquals("toast-recording", actions[1].resourceId)
        assertEquals("toastVideo", actions[1].args["saveAs"]?.asText())
        assertEquals("assertScreenRecordingTextRegexMatch", actions[2].keyword)
        assertEquals("提交成功", actions[2].value?.asText())
        assertEquals("@{case.toastVideo}", actions[2].args["source"]?.asText())
        assertEquals("toast-frame", actions[2].resourceId)
    }

    @Test
    fun `parses app log capture and custom assertion payloads`() {
        val yaml = """
            schemaVersion: "1.0"
            id: app-log-actions
            name: App Log Actions
            productModel: Test Product
            deviceConfig: devices/ios.yaml
            stages:
              - id: main
                name: Main
                cases:
                  - id: bt-log
                    name: Bluetooth Log
                    actions:
                      - captureAppLogStart:
                          id: start-bt-log
                          saveAs: btLogWindow
                          filter:
                            messageContains: "BLE"
                            ios:
                              processRegex: "UgreenAudio"
                            android:
                              tag: "BluetoothCmd"
                      - captureAppLogEnd:
                          id: stop-bt-log
                          source: "@{case.btLogWindow}"
                          saveAs: btLogFile
                      - customAssertAppLog:
                          id: assert-bt-log
                          plugin: ugreen-audio
                          assertion: bluetoothCommandReported
                          source: "@{case.btLogFile}"
                          args:
                            operation: customControl
                            expected: playPause
        """.trimIndent()

        val actions = parser.parse(yaml).stages.single().cases.single().actions

        assertEquals("captureAppLogStart", actions[0].keyword)
        assertEquals("btLogWindow", actions[0].args["saveAs"]?.asText())
        assertEquals("UgreenAudio", actions[0].args["filter"]?.path("ios")?.path("processRegex")?.asText())
        assertEquals("captureAppLogEnd", actions[1].keyword)
        assertEquals("@{case.btLogWindow}", actions[1].args["source"]?.asText())
        assertEquals("customAssertAppLog", actions[2].keyword)
        assertEquals("ugreen-audio", actions[2].args["plugin"]?.asText())
        assertEquals("bluetoothCommandReported", actions[2].args["assertion"]?.asText())
        assertEquals("customControl", actions[2].args["args"]?.path("operation")?.asText())
    }

    @Test
    fun `rejects hardcoded text locator in element catalog`() {
        val yaml = elementCatalogYaml(
            locatorValue = "登录",
        )

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("must not hardcode fixed UI copy") },
            "Expected hardcoded text locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects inline locator in case action`() {
        val yaml = """
            schemaVersion: "1.0"
            id: daily-smoke
            name: Daily Smoke
            productModel: Test Product
            deviceConfig: devices/android.yaml
            stages:
              - id: logged-out
                name: Logged out
                cases:
                  - id: login-success
                    name: Login success
                    actions:
                      - tap: tap-login
                        locator:
                          strategy: id
                          value: login_button
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            parser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("locator") },
            "Expected inline locator schema violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects hardcoded text inside xpath locator in element catalog`() {
        val yaml = elementCatalogYaml(
            locatorStrategy = "xpath",
            locatorValue = "//*[@text='登录']",
        )

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.path.endsWith(".value") },
            "Expected xpath locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `allows hardcoded brand locator with language insensitive reason in element catalog`() {
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              loginPageMarker:
                hardcodedTextReason: language_insensitive_text
                android:
                  strategy: xpath
                  value: "//*[contains(@text,'UgreenAudio')]"
                ios:
                  strategy: predicate
                  value: "label CONTAINS 'UgreenAudio' OR name CONTAINS 'UgreenAudio'"
        """.trimIndent()

        val catalog = elementCatalogParser.parse(yaml)

        assertEquals(
            "//*[contains(@text,'UgreenAudio')]",
            catalog.elements.getValue("loginPageMarker").locatorFor("android").value,
        )
    }

    @Test
    fun `allows hardcoded accessibility name locator with language insensitive reason in element catalog`() {
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              addButton:
                hardcodedTextReason: language_insensitive_text
                ios:
                  strategy: xpath
                  value: "//XCUIElementTypeButton[@name='icon common add']"
        """.trimIndent()

        val catalog = elementCatalogParser.parse(yaml)

        assertEquals(
            "//XCUIElementTypeButton[@name='icon common add']",
            catalog.elements.getValue("addButton").locatorFor("ios").value,
        )
    }

    @Test
    fun `rejects hardcoded text locator with unsupported reason`() {
        val unsupportedReason = "${DslPolicyConfig.LANGUAGE_INSENSITIVE_TEXT_REASON}_invalid"
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              addButton:
                ios:
                  strategy: xpath
                  value: "//XCUIElementTypeButton[@name='icon common add']"
                  hardcodedTextReason: $unsupportedReason
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("Allowed reasons: language_insensitive_text") },
            "Expected unsupported hardcoded text reason violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects starts-with hardcoded text without reason`() {
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              checkbox:
                ios:
                  strategy: xpath
                  value: "//XCUIElementTypeButton[starts-with(@name,'icon round')]"
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("hardcode fixed UI copy") },
            "Expected starts-with hardcoded text locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `allows state value comparison in xpath locator`() {
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              uncheckedTerms:
                ios:
                  strategy: xpath
                  value: "//XCUIElementTypeButton[not(@value='1')]"
        """.trimIndent()

        val catalog = elementCatalogParser.parse(yaml)

        assertEquals("xpath", catalog.elements.getValue("uncheckedTerms").locatorFor("ios").strategy)
    }

    @Test
    fun `rejects hardcoded text inside predicate locator in element catalog`() {
        val yaml = elementCatalogYaml(
            locatorStrategy = "predicate",
            locatorValue = "label CONTAINS '登录'",
        )

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.path.endsWith(".value") },
            "Expected predicate locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `allows parameterized text with language insensitive reason`() {
        val yaml = """
            schemaVersion: "1.0"
            id: language-elements
            elements:
              languageTitle:
                strategy: xpath
                value: "//*[@text='${'$'}{i18n.languageTitle}']"
                parameterizedTextReason: language_insensitive_text
        """.trimIndent()

        val catalog = elementCatalogParser.parse(yaml)

        assertEquals("xpath", catalog.elements.getValue("languageTitle").locatorFor("android").strategy)
    }

    @Test
    fun `rejects parameterized text inside ordinary xpath locator`() {
        val yaml = elementCatalogYaml(
            locatorStrategy = "xpath",
            locatorValue = "//*[@text='\${i18n.loginButtonText}']",
        )

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("require parameterizedTextReason") },
            "Expected parameterized text locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects coordinate attributes inside element locator`() {
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              fragileRow:
                ios:
                  strategy: xpath
                  value: "//XCUIElementTypeOther[@x = 16 and @y = 536 and @width = 361 and @height = 55]"
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("must not use coordinate or size attributes") },
            "Expected coordinate locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects parameterized text locator with unsupported reason`() {
        val unsupportedReason = "${DslPolicyConfig.LANGUAGE_INSENSITIVE_TEXT_REASON}_invalid"
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              targetDevice:
                ios:
                  strategy: xpath
                  value: "//*[contains(@name,'${'$'}{device.targetMacSuffix}')]"
                  parameterizedTextReason: $unsupportedReason
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            elementCatalogParser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("Allowed reasons: language_insensitive_text") },
            "Expected unsupported parameterized text reason violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects logic control in case dsl`() {
        val yaml = """
            schemaVersion: "1.0"
            id: daily-smoke
            name: Daily Smoke
            productModel: Test Product
            deviceConfig: devices/android.yaml
            stages:
              - id: logged-out
                name: Logged out
                cases:
                  - id: login-success
                    name: Login success
                    actions:
                      - tap: tap-login
                        if: "${'$'}{account.enabled}"
                        element: login.loginButton
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            parser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("logic control key 'if'") },
            "Expected linear DSL violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects if control action in case dsl`() {
        val yaml = """
            schemaVersion: "1.0"
            id: daily-smoke
            name: Daily Smoke
            productModel: Test Product
            deviceConfig: devices/android.yaml
            stages:
              - id: logged-out
                name: Logged out
                cases:
                  - id: login-success
                    name: Login success
                    actions:
                      - if:
                          assertSourceRegexMatch: detect-login-page
                          pattern: "Login"
                        then:
                          - tap: tap-login
                            element: login.loginButton
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            parser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("logic control key 'if'") || it.message.contains("if") },
            "Expected case if-control violation, got ${error.violations}",
        )
    }

    @Test
    fun `parses generic if control in fragment dsl`() {
        val yaml = """
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
        """.trimIndent()

        val catalog = fragmentCatalogParser.parse(yaml)
        val action = catalog.fragments.getValue("ensureLoginPage").actions.single()

        assertEquals("if", action.keyword)
        assertEquals("assertElementExists", action.conditionAction?.keyword)
        assertEquals("detect-login-page", action.conditionAction?.id)
        assertEquals(emptyList(), action.thenActions)
        assertEquals(listOf("open-mine-tab"), action.elseActions.map { it.id })
    }

    @Test
    fun `rejects unknown action keyword`() {
        val yaml = validPlanYaml(keyword = "unknown-action")

        val error = assertFailsWith<DslValidationException> {
            parser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("Unknown action keyword") },
            "Expected unknown keyword violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects yaml missing required stages`() {
        val yaml = """
            schemaVersion: "1.0"
            id: daily-smoke
            name: Daily Smoke
        """.trimIndent()

        val error = assertFailsWith<DslValidationException> {
            parser.parse(yaml)
        }

        assertTrue(
            error.violations.any { it.message.contains("required property 'stages'") || it.message.contains("stages") },
            "Expected stages schema violation, got ${error.violations}",
        )
    }

    private fun validPlanYaml(
        keyword: String = "点击",
    ): String {
        return """
            schemaVersion: "1.0"
            id: daily-smoke
            name: Daily Smoke
            productModel: Test Product
            parameters:
              - id: default
                file: data/default.yaml
            deviceConfig: devices/android.yaml
            defaults:
              implicitWaitMs: 3000
            stages:
              - id: logged-out
                name: Logged out
                initialState: fresh-install
                cases:
                  - id: login-success
                    name: Login success
                    elementRefs:
                      - id: login
                        file: elements/login.yaml
                    actions:
                      - $keyword: tap-login
                        element: login.loginButton
        """.trimIndent()
    }

    private fun elementCatalogYaml(
        locatorStrategy: String = "text",
        locatorValue: String = "\${i18n.loginButtonText}",
    ): String {
        return """
            schemaVersion: "1.0"
            id: login-elements
            elements:
              loginButton:
                strategy: $locatorStrategy
                value: "$locatorValue"
        """.trimIndent()
    }
}
