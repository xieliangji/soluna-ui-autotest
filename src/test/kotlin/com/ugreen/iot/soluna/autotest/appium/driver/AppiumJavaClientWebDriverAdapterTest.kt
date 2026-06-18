package com.ugreen.iot.soluna.autotest.appium.driver

import com.ugreen.iot.soluna.autotest.core.model.LocatorDefinition
import io.appium.java_client.AppiumBy
import org.openqa.selenium.By
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppiumJavaClientWebDriverAdapterTest {
    @Test
    fun `maps common locator strategies`() {
        assertEquals(AppiumBy.id("username").toString(), LocatorDefinition("id", "username").toBy().toString())
        assertEquals(AppiumBy.accessibilityId("login").toString(), LocatorDefinition("accessibilityId", "login").toBy().toString())
        assertEquals(By.xpath("//*[@name='x']").toString(), LocatorDefinition("xpath", "//*[@name='x']").toBy().toString())
        assertEquals(AppiumBy.iOSNsPredicateString("name == 'x'").toString(), LocatorDefinition("predicate", "name == 'x'").toBy().toString())
        assertEquals(AppiumBy.androidUIAutomator("new UiSelector().resourceId(\"x\")").toString(), LocatorDefinition("uiautomator", "new UiSelector().resourceId(\"x\")").toBy().toString())
    }

    @Test
    fun `maps text locator to cross platform xpath`() {
        val by = LocatorDefinition("text", "Login").toBy()

        assertEquals(
            By.xpath("//*[@text='Login' or @label='Login' or @name='Login' or @value='Login']").toString(),
            by.toString(),
        )
    }

    @Test
    fun `rejects unsupported locator strategy`() {
        assertFailsWith<IllegalStateException> {
            LocatorDefinition("unknown", "x").toBy()
        }
    }

    @Test
    fun `keyboard overlay parser ignores hidden keyboard nodes`() {
        val source = """
            <XCUIElementTypeKeyboard visible="false" x="0" y="561" width="393" height="233"/>
            <XCUIElementTypeButton visible="true" x="264" y="770" width="127" height="48"/>
        """.trimIndent()

        assertEquals(null, KeyboardOverlaySourceParser.topFromPageSource(source))
    }

    @Test
    fun `keyboard overlay parser returns top of visible keyboard node`() {
        val source = """
            <XCUIElementTypeToolbar visible="true" x="0" y="517" width="393" height="44"/>
            <XCUIElementTypeKeyboard visible="true" x="0" y="561" width="393" height="233"/>
        """.trimIndent()

        assertEquals(561, KeyboardOverlaySourceParser.topFromPageSource(source))
    }
}
