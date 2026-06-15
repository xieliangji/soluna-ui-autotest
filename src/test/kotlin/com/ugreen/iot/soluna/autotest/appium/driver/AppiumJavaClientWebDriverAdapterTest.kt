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
}
