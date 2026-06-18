package com.soluna.ui.autotest.appium.server

import kotlin.test.Test
import kotlin.test.assertTrue

class ManagedAppiumServerSmokeTest {
    @Test
    fun `starts and stops managed Appium server`() {
        if (System.getenv("SOLUNA_MANAGED_APPIUM_SMOKE") != "true") {
            return
        }

        val executable = System.getenv("SOLUNA_APPIUM_EXECUTABLE") ?: "appium"
        val manager = LocalProcessAppiumServerManager()
        val handle = manager.ensureRunning(
            AppiumServerConfig(
                managed = true,
                usePlugins = listOf("soluna-ext"),
                executable = executable,
                startupTimeoutMs = 20_000,
            ),
        )

        try {
            assertTrue(manager.isRunning(handle))
        } finally {
            manager.stop(handle)
        }
    }
}
