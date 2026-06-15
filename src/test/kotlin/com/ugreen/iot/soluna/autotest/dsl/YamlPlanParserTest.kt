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
    fun `allows parameterized text inside xpath locator in element catalog`() {
        val yaml = elementCatalogYaml(
            locatorStrategy = "xpath",
            locatorValue = "//*[@text='\${i18n.loginButtonText}']",
        )

        val catalog = elementCatalogParser.parse(yaml)

        assertEquals("xpath", catalog.elements.getValue("loginButton").locatorFor("android").strategy)
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
