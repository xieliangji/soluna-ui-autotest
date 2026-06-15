package com.ugreen.iot.soluna.autotest.appium.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppiumServerManagerTest {
    @Test
    fun `server config builds localhost url`() {
        val config = AppiumServerConfig(
            managed = true,
            host = "127.0.0.1",
            port = 4725,
            usePlugins = listOf("soluna-ext"),
        )

        assertEquals("http://127.0.0.1:4725", config.url)
    }

    @Test
    fun `server config rejects invalid port`() {
        assertFailsWith<IllegalArgumentException> {
            AppiumServerConfig(
                managed = true,
                port = 0,
            )
        }
    }

    @Test
    fun `server config prefers explicit base url`() {
        val config = AppiumServerConfig(
            managed = false,
            baseUrl = "http://localhost:4725/wd/hub",
            host = "127.0.0.1",
            port = 4723,
        )

        assertEquals("http://localhost:4725/wd/hub", config.url)
    }

    @Test
    fun `local process manager builds Appium command`() {
        val manager = LocalProcessAppiumServerManager()

        val command = manager.buildCommand(
            AppiumServerConfig(
                managed = true,
                host = "0.0.0.0",
                port = 4725,
                usePlugins = listOf("soluna-ext", "inspector"),
                executable = "/opt/homebrew/bin/appium",
                extraArgs = listOf("--log-level", "info"),
            ),
        )

        assertEquals(
            listOf(
                "/opt/homebrew/bin/appium",
                "--address",
                "0.0.0.0",
                "--port",
                "4725",
                "--use-plugins=soluna-ext,inspector",
                "--log-level",
                "info",
            ),
            command,
        )
    }

    @Test
    fun `local process manager launches managed server and stops process`() {
        val process = FakeAppiumServerProcess(pid = 101)
        val launcher = RecordingAppiumProcessLauncher(process)
        val probe = RecordingStatusProbe(true)
        val manager = LocalProcessAppiumServerManager(
            processLauncher = launcher,
            statusProbe = probe,
            portAllocator = FixedPortAllocator(49123),
            sleeper = {},
        )

        val handle = manager.ensureRunning(
            AppiumServerConfig(
                managed = true,
                environment = mapOf(
                    "ANDROID_HOME" to "/Users/demo/Library/Android/sdk",
                    "PATH" to "/Users/demo/Library/Android/sdk/platform-tools:/usr/bin",
                ),
            ),
        )

        assertEquals("http://127.0.0.1:49123", handle.url)
        assertEquals(
            listOf(
                "appium",
                "--address",
                "127.0.0.1",
                "--port",
                "49123",
                "--use-plugins=soluna-ext",
            ),
            launcher.command,
        )
        assertEquals(101, handle.processId)
        assertTrue(handle.managed)
        assertEquals("/Users/demo/Library/Android/sdk", launcher.environment["ANDROID_HOME"])
        assertEquals(1, probe.urls.size)
        assertTrue(manager.isRunning(handle))

        manager.stop(handle)

        assertFalse(process.alive)
        assertEquals(1, process.destroyCount)
    }

    @Test
    fun `local process manager does not launch process for external server`() {
        val launcher = RecordingAppiumProcessLauncher(FakeAppiumServerProcess(pid = 101))
        val manager = LocalProcessAppiumServerManager(
            processLauncher = launcher,
            statusProbe = RecordingStatusProbe(true),
            sleeper = {},
        )

        val handle = manager.ensureRunning(
            AppiumServerConfig(
                managed = false,
                baseUrl = "http://127.0.0.1:4725",
            ),
        )

        assertEquals("http://127.0.0.1:4725", handle.url)
        assertFalse(handle.managed)
        assertEquals(null, handle.processId)
        assertEquals(emptyList(), launcher.command)
    }

    @Test
    fun `local process manager destroys process when startup times out`() {
        val process = FakeAppiumServerProcess(pid = 202)
        val manager = LocalProcessAppiumServerManager(
            processLauncher = RecordingAppiumProcessLauncher(process),
            statusProbe = RecordingStatusProbe(false),
            probeIntervalMs = 1,
            sleeper = {},
        )

        assertFailsWith<AppiumServerStartupException> {
            manager.ensureRunning(
                AppiumServerConfig(
                    managed = true,
                    port = 4725,
                    startupTimeoutMs = 1,
                ),
            )
        }

        assertFalse(process.alive)
        assertEquals(1, process.destroyCount)
    }

    @Test
    fun `local process manager fails fast when managed process exits before ready`() {
        val process = FakeAppiumServerProcess(pid = 303, alive = false)
        val manager = LocalProcessAppiumServerManager(
            processLauncher = RecordingAppiumProcessLauncher(process),
            statusProbe = RecordingStatusProbe(false),
            sleeper = {},
        )

        assertFailsWith<AppiumServerStartupException> {
            manager.ensureRunning(
                AppiumServerConfig(
                    managed = true,
                    port = 4725,
                ),
            )
        }

        assertEquals(0, process.destroyCount)
    }

    private class RecordingAppiumProcessLauncher(
        private val process: AppiumServerProcess,
    ) : AppiumProcessLauncher {
        var command: List<String> = emptyList()
        var environment: Map<String, String> = emptyMap()

        override fun launch(
            command: List<String>,
            environment: Map<String, String>,
        ): AppiumServerProcess {
            this.command = command
            this.environment = environment
            return process
        }
    }

    private class RecordingStatusProbe(
        private vararg val readiness: Boolean,
    ) : AppiumServerStatusProbe {
        val urls = mutableListOf<String>()
        private var index = 0

        override fun isReady(url: String): Boolean {
            urls += url
            val value = readiness.getOrElse(index) { readiness.lastOrNull() ?: false }
            index += 1
            return value
        }
    }

    private class FixedPortAllocator(
        private val port: Int,
    ) : AppiumPortAllocator {
        override fun findAvailablePort(): Int {
            return port
        }
    }

    private class FakeAppiumServerProcess(
        override val pid: Long,
        var alive: Boolean = true,
    ) : AppiumServerProcess {
        var destroyCount: Int = 0
        var destroyForciblyCount: Int = 0

        override fun isAlive(): Boolean {
            return alive
        }

        override fun destroy() {
            destroyCount += 1
            alive = false
        }

        override fun destroyForcibly() {
            destroyForciblyCount += 1
            alive = false
        }

        override fun waitFor(timeoutMs: Long): Boolean {
            return !alive
        }
    }
}
