package com.ugreen.iot.soluna.autotest.dsl

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
        val planYaml = Files.readString(Path.of("examples/plans/daily-smoke.yaml"))

        val plan = parser.parse(planYaml)

        assertEquals("daily-smoke", plan.id)
        assertEquals("logged-out", plan.stages.single().id)
        assertEquals("capture-home", plan.stages.single().cases.single().actions.last().id)
    }

    @Test
    fun `normalizes chinese action keyword`() {
        assertEquals("tap", DefaultKeywordRegistry.normalize("点击"))
        assertEquals("screenshot", DefaultKeywordRegistry.normalize("显式截图"))
    }

    @Test
    fun `parses nested action keyword payload`() {
        val yaml = """
            schemaVersion: "1.0"
            id: nested-actions
            name: Nested Actions
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
    }

    @Test
    fun `parses screen recording action payloads`() {
        val yaml = """
            schemaVersion: "1.0"
            id: recording-actions
            name: Recording Actions
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
    fun `allows explicitly approved hardcoded brand logo locator in element catalog`() {
        val yaml = """
            schemaVersion: "1.0"
            id: common-elements
            elements:
              loginPageMarker:
                android:
                  strategy: xpath
                  value: "//*[contains(@text,'UgreenAudio')]"
                  textLocatorPurpose: brandLogo
                ios:
                  strategy: predicate
                  value: "label CONTAINS 'UgreenAudio' OR name CONTAINS 'UgreenAudio'"
                  textLocatorPurpose: brandLogo
        """.trimIndent()

        val catalog = elementCatalogParser.parse(yaml)

        assertEquals(
            "//*[contains(@text,'UgreenAudio')]",
            catalog.elements.getValue("loginPageMarker").locatorFor("android").value,
        )
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
    fun `allows parameterized text inside approved language title locator`() {
        val yaml = """
            schemaVersion: "1.0"
            id: language-elements
            elements:
              languageTitle:
                strategy: xpath
                value: "//*[@text='${'$'}{i18n.languageTitle}']"
                textLocatorPurpose: languageTitle
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
            error.violations.any { it.message.contains("Parameterized text locators are only allowed") },
            "Expected parameterized text locator violation, got ${error.violations}",
        )
    }

    @Test
    fun `rejects logic control in case dsl`() {
        val yaml = """
            schemaVersion: "1.0"
            id: daily-smoke
            name: Daily Smoke
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
                      assertElementAttrRegexMatch: detect-login-page
                      element: common.loginPageMarker
                      attr: name/label/text
                      pattern: "${'$'}{appState.loginPagePattern}"
                    then: []
                    else:
                      - tap: open-mine-tab
                        element: common.mineTab
        """.trimIndent()

        val catalog = fragmentCatalogParser.parse(yaml)
        val action = catalog.fragments.getValue("ensureLoginPage").actions.single()

        assertEquals("if", action.keyword)
        assertEquals("assertElementAttrRegexMatch", action.conditionAction?.keyword)
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
