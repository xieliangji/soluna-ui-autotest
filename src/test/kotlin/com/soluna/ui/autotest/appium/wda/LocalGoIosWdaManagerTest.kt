package com.soluna.ui.autotest.appium.wda

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalGoIosWdaManagerTest {
    @Test
    fun `starts tunnel runwda forward for iOS 17 and newer in order`() {
        val launcher = RecordingWdaProcessLauncher()
        val manager = LocalGoIosWdaManager(
            processLauncher = launcher,
            statusProbe = AlwaysReadyWdaStatusProbe,
            portAllocator = FixedWdaPortAllocator(18100, 28100, 28101),
            sleeper = {},
        )

        val handle = manager.ensureRunning(
            WdaConfig(
                udid = "ios-001",
                osVersion = "17.0",
                runwdaStartupDelayMs = 0,
            ),
        )

        assertEquals("http://127.0.0.1:18100", handle.url)
        assertTrue(handle.usesTunnel)
        assertEquals(28100, handle.tunnelInfoPort)
        assertEquals(28101, handle.userspaceTunnelPort)
        assertEquals(
            listOf(
                listOf(
                    "ios",
                    "--udid=ios-001",
                    "--tunnel-info-host=127.0.0.1",
                    "--tunnel-info-port=28100",
                    "tunnel",
                    "start",
                    "--userspace",
                    "--userspace-port=28101",
                ),
                listOf(
                    "ios",
                    "--udid=ios-001",
                    "--tunnel-info-host=127.0.0.1",
                    "--tunnel-info-port=28100",
                    "runwda",
                ),
                listOf(
                    "ios",
                    "--udid=ios-001",
                    "--tunnel-info-host=127.0.0.1",
                    "--tunnel-info-port=28100",
                    "forward",
                    "18100",
                    "8100",
                ),
            ),
            launcher.commands,
        )
        assertTrue(manager.isRunning(handle))
    }

    @Test
    fun `starts runwda and forward without tunnel for iOS before 17`() {
        val launcher = RecordingWdaProcessLauncher()
        val manager = LocalGoIosWdaManager(
            processLauncher = launcher,
            statusProbe = AlwaysReadyWdaStatusProbe,
            portAllocator = FixedWdaPortAllocator(18101),
            sleeper = {},
        )

        val handle = manager.ensureRunning(
            WdaConfig(
                udid = "ios-001",
                osVersion = "16.7",
                runwdaStartupDelayMs = 0,
            ),
        )

        assertFalse(handle.usesTunnel)
        assertEquals(
            listOf(
                listOf("ios", "--udid=ios-001", "runwda"),
                listOf("ios", "--udid=ios-001", "forward", "18101", "8100"),
            ),
            launcher.commands,
        )
    }

    @Test
    fun `system tunnel mode does not add userspace flag`() {
        val manager = LocalGoIosWdaManager()

        assertEquals(
            listOf("ios", "--udid=ios-001", "tunnel", "start"),
            manager.buildTunnelCommand(
                WdaConfig(
                    udid = "ios-001",
                    osVersion = "17.0",
                    tunnelMode = "system",
                ),
            ),
        )
    }

    @Test
    fun `passes resolved WDA runner bundle id to runwda`() {
        val launcher = RecordingWdaProcessLauncher()
        val manager = LocalGoIosWdaManager(
            processLauncher = launcher,
            statusProbe = AlwaysReadyWdaStatusProbe,
            portAllocator = FixedWdaPortAllocator(18104, 28104, 28105),
            sleeper = {},
        )

        manager.ensureRunning(
            WdaConfig(
                udid = "ios-001",
                osVersion = "17.0",
                bundleId = "com.facebook.WebDriverAgentRunner.xctrunner",
                testRunnerBundleId = "com.facebook.WebDriverAgentRunner.xctrunner",
                xctestConfig = "WebDriverAgentRunner.xctest",
                runwdaStartupDelayMs = 0,
            ),
        )

        assertEquals(
            listOf(
                "ios",
                "--udid=ios-001",
                "--tunnel-info-host=127.0.0.1",
                "--tunnel-info-port=28104",
                "runwda",
                "--bundleid=com.facebook.WebDriverAgentRunner.xctrunner",
                "--testrunnerbundleid=com.facebook.WebDriverAgentRunner.xctrunner",
                "--xctestconfig=WebDriverAgentRunner.xctest",
            ),
            launcher.commands[1],
        )
    }

    @Test
    fun `rejects partial go-ios WDA identity`() {
        val error = assertFailsWith<IllegalArgumentException> {
            WdaConfig(
                udid = "ios-001",
                osVersion = "17.0",
                testRunnerBundleId = "com.facebook.WebDriverAgentRunner.xctrunner",
            )
        }

        assertEquals(
            "bundleId, testRunnerBundleId and xctestConfig must be configured together for go-ios runwda",
            error.message,
        )
    }

    @Test
    fun `restart stops forward with runwda before starting a new forward`() {
        val launcher = RecordingWdaProcessLauncher()
        val manager = LocalGoIosWdaManager(
            processLauncher = launcher,
            statusProbe = AlwaysReadyWdaStatusProbe,
            portAllocator = FixedWdaPortAllocator(18102),
            sleeper = {},
        )
        val config = WdaConfig(
            udid = "ios-001",
            osVersion = "17.1",
            runwdaStartupDelayMs = 0,
        )

        val first = manager.ensureRunning(config)
        val restarted = manager.restart(first, config)

        assertTrue(restarted.usesTunnel)
        assertEquals(6, launcher.commands.size)
        assertEquals(
            listOf(
                first.forwardProcessId,
                first.runwdaProcessId,
                first.tunnelProcessId,
            ),
            launcher.destroyedPids,
        )
        assertTrue(restarted.forwardProcessId != first.forwardProcessId)
        assertTrue(restarted.runwdaProcessId != first.runwdaProcessId)
    }

    @Test
    fun `unmanaged WDA only probes configured URL`() {
        val launcher = RecordingWdaProcessLauncher()
        val statusProbe = RecordingWdaStatusProbe()
        val manager = LocalGoIosWdaManager(
            processLauncher = launcher,
            statusProbe = statusProbe,
            sleeper = {},
        )

        val handle = manager.ensureRunning(
            WdaConfig(
                udid = "ios-001",
                osVersion = "17.0",
                managed = false,
                url = "http://127.0.0.1:18103",
            ),
        )

        assertEquals("http://127.0.0.1:18103", handle.url)
        assertEquals(emptyList(), launcher.commands)
        assertEquals(listOf("http://127.0.0.1:18103"), statusProbe.urls)
    }

    private object AlwaysReadyWdaStatusProbe : WdaStatusProbe {
        override fun isReady(url: String): Boolean {
            return true
        }
    }

    private class RecordingWdaStatusProbe : WdaStatusProbe {
        val urls = mutableListOf<String>()

        override fun isReady(url: String): Boolean {
            urls += url
            return true
        }
    }

    private class FixedWdaPortAllocator(
        private vararg val ports: Int,
    ) : WdaPortAllocator {
        private var index = 0

        override fun findAvailablePort(): Int {
            val port = ports.getOrElse(index) { ports.last() }
            index += 1
            return port
        }
    }

    private class RecordingWdaProcessLauncher : WdaProcessLauncher {
        val commands = mutableListOf<List<String>>()
        val destroyedPids = mutableListOf<Long?>()
        private var nextPid = 100L

        override fun launch(
            command: List<String>,
            environment: Map<String, String>,
            logFile: Path?,
        ): WdaProcess {
            commands += command
            return RecordingWdaProcess(nextPid++, destroyedPids)
        }
    }

    private class RecordingWdaProcess(
        override val pid: Long,
        private val destroyedPids: MutableList<Long?>,
    ) : WdaProcess {
        private var alive = true

        override fun isAlive(): Boolean {
            return alive
        }

        override fun destroy() {
            destroyedPids += pid
            alive = false
        }

        override fun destroyForcibly() {
            alive = false
        }

        override fun waitFor(timeoutMs: Long): Boolean {
            return true
        }
    }
}
