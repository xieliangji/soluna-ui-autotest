package io.soluna.ui.autotest.appium.server

import io.soluna.ui.autotest.tool.FfmpegToolResolution
import io.soluna.ui.autotest.tool.FfmpegToolResolver
import io.soluna.ui.autotest.tool.FfmpegToolSource
import io.soluna.ui.autotest.tool.NoOpFfmpegToolResolver
import java.nio.file.Files
import java.nio.file.Path
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
        val extensionInstaller = RecordingAppiumExtensionInstaller()
        val manager = LocalProcessAppiumServerManager(
            processLauncher = launcher,
            statusProbe = probe,
            portAllocator = FixedPortAllocator(49123),
            ffmpegToolResolver = NoOpFfmpegToolResolver,
            extensionInstaller = extensionInstaller,
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
        assertEquals("appium", extensionInstaller.appiumExecutable)
        assertEquals(listOf("soluna-ext"), extensionInstaller.pluginNames)
        assertEquals(listOf("uiautomator2", "xcuitest"), extensionInstaller.driverNames)
        assertEquals(1, probe.urls.size)
        assertTrue(manager.isRunning(handle))

        manager.stop(handle)

        assertFalse(process.alive)
        assertEquals(1, process.destroyCount)
    }

    @Test
    fun `local process manager bootstraps configured plugins and drivers before launch`() {
        val process = FakeAppiumServerProcess(pid = 102)
        val launcher = RecordingAppiumProcessLauncher(process)
        val extensionInstaller = RecordingAppiumExtensionInstaller()
        val manager = LocalProcessAppiumServerManager(
            processLauncher = launcher,
            statusProbe = RecordingStatusProbe(true),
            portAllocator = FixedPortAllocator(49125),
            ffmpegToolResolver = NoOpFfmpegToolResolver,
            extensionInstaller = extensionInstaller,
            sleeper = {},
        )

        manager.ensureRunning(
            AppiumServerConfig(
                managed = true,
                usePlugins = listOf("soluna-ext", "images"),
                ensureDrivers = listOf("uiautomator2", "xcuitest"),
            ),
        )

        assertEquals(listOf("soluna-ext", "images"), extensionInstaller.pluginNames)
        assertEquals(listOf("uiautomator2", "xcuitest"), extensionInstaller.driverNames)
        assertEquals(
            listOf(
                "appium",
                "--address",
                "127.0.0.1",
                "--port",
                "49125",
                "--use-plugins=soluna-ext,images",
            ),
            launcher.command,
        )
    }

    @Test
    fun `local process manager does not launch process for external server`() {
        val launcher = RecordingAppiumProcessLauncher(FakeAppiumServerProcess(pid = 101))
        val manager = LocalProcessAppiumServerManager(
            processLauncher = launcher,
            statusProbe = RecordingStatusProbe(true),
            ffmpegToolResolver = NoOpFfmpegToolResolver,
            extensionInstaller = NoOpAppiumExtensionInstaller,
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
            ffmpegToolResolver = NoOpFfmpegToolResolver,
            extensionInstaller = NoOpAppiumExtensionInstaller,
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
            ffmpegToolResolver = NoOpFfmpegToolResolver,
            extensionInstaller = NoOpAppiumExtensionInstaller,
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

    @Test
    fun `local process manager prepends bundled ffmpeg directory to managed server path`() {
        val process = FakeAppiumServerProcess(pid = 404)
        val launcher = RecordingAppiumProcessLauncher(process)
        val manager = LocalProcessAppiumServerManager(
            processLauncher = launcher,
            statusProbe = RecordingStatusProbe(true),
            portAllocator = FixedPortAllocator(49124),
            ffmpegToolResolver = FixedFfmpegToolResolver(Path.of("/opt/soluna/tools/ffmpeg/macos-arm64")),
            extensionInstaller = NoOpAppiumExtensionInstaller,
            sleeper = {},
        )

        manager.ensureRunning(
            AppiumServerConfig(
                managed = true,
                environment = mapOf("PATH" to "/usr/bin"),
            ),
        )

        assertEquals(
            "/opt/soluna/tools/ffmpeg/macos-arm64${java.io.File.pathSeparator}/usr/bin",
            launcher.environment["PATH"],
        )
    }

    @Test
    fun `extension installer installs missing soluna plugin and drivers`() {
        val sourceDir = tempPluginSource()
        val runner = RecordingAppiumExtensionCommandRunner(
            AppiumExtensionCommandResult(exitCode = 0, stdout = "{}"),
            AppiumExtensionCommandResult(exitCode = 0, stdout = "{}"),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
        )
        val installer = LocalAppiumExtensionInstaller(
            commandRunner = runner,
            sourceResolver = FixedPluginSourceResolver(sourceDir),
        )

        installer.ensureExtensions(
            appiumExecutable = "/opt/homebrew/bin/appium",
            pluginNames = listOf("soluna-ext"),
            driverNames = listOf("uiautomator2", "xcuitest"),
        )

        assertEquals(
            listOf(
                listOf("/opt/homebrew/bin/appium", "plugin", "list", "--installed", "--json", "--verbose"),
                listOf("npm", "ci"),
                listOf("npm", "run", "build"),
                listOf("/opt/homebrew/bin/appium", "plugin", "install", "--source=local", sourceDir.toString()),
                listOf("/opt/homebrew/bin/appium", "driver", "list", "--installed", "--json", "--verbose"),
                listOf("/opt/homebrew/bin/appium", "driver", "install", "uiautomator2"),
                listOf("/opt/homebrew/bin/appium", "driver", "install", "xcuitest"),
            ),
            runner.commands,
        )
    }

    @Test
    fun `extension installer reinstalls soluna plugin when installed source is not project source`() {
        val sourceDir = tempPluginSource()
        val runner = RecordingAppiumExtensionCommandRunner(
            AppiumExtensionCommandResult(
                exitCode = 0,
                stdout = """
                    {
                      "soluna-ext": {
                        "installType": "local",
                        "installSpec": "/tmp/old-soluna-appium-ext",
                        "installPath": "/Users/demo/.appium/node_modules/soluna-appium-ext",
                        "installed": true
                      }
                    }
                """.trimIndent(),
            ),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0),
            AppiumExtensionCommandResult(exitCode = 0, stdout = "{}"),
        )
        val installer = LocalAppiumExtensionInstaller(
            commandRunner = runner,
            sourceResolver = FixedPluginSourceResolver(sourceDir),
        )

        installer.ensureExtensions(
            appiumExecutable = "/opt/homebrew/bin/appium",
            pluginNames = listOf("soluna-ext"),
            driverNames = emptyList(),
        )

        assertEquals(
            listOf(
                listOf("/opt/homebrew/bin/appium", "plugin", "list", "--installed", "--json", "--verbose"),
                listOf("/opt/homebrew/bin/appium", "plugin", "uninstall", "soluna-ext"),
                listOf("npm", "ci"),
                listOf("npm", "run", "build"),
                listOf("/opt/homebrew/bin/appium", "plugin", "install", "--source=local", sourceDir.toString()),
            ),
            runner.commands.take(5),
        )
    }

    @Test
    fun `extension installer keeps soluna plugin when installed source is project source`() {
        val sourceDir = tempPluginSource()
        val runner = RecordingAppiumExtensionCommandRunner(
            AppiumExtensionCommandResult(
                exitCode = 0,
                stdout = """
                    {
                      "soluna-ext": {
                        "installType": "local",
                        "installSpec": "$sourceDir",
                        "installPath": "$sourceDir",
                        "installed": true
                      }
                    }
                """.trimIndent(),
            ),
            AppiumExtensionCommandResult(exitCode = 0, stdout = "{}"),
        )
        val installer = LocalAppiumExtensionInstaller(
            commandRunner = runner,
            sourceResolver = FixedPluginSourceResolver(sourceDir),
        )

        installer.ensureExtensions(
            appiumExecutable = "/opt/homebrew/bin/appium",
            pluginNames = listOf("soluna-ext"),
            driverNames = emptyList(),
        )

        assertEquals(
            listOf(
                listOf("/opt/homebrew/bin/appium", "plugin", "list", "--installed", "--json", "--verbose"),
            ),
            runner.commands,
        )
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

    private class RecordingAppiumExtensionInstaller : AppiumExtensionInstaller {
        var appiumExecutable: String? = null
        var pluginNames: List<String> = emptyList()
        var driverNames: List<String> = emptyList()
        var environment: Map<String, String> = emptyMap()

        override fun ensureExtensions(
            appiumExecutable: String,
            pluginNames: List<String>,
            driverNames: List<String>,
            environment: Map<String, String>,
        ) {
            this.appiumExecutable = appiumExecutable
            this.pluginNames = pluginNames
            this.driverNames = driverNames
            this.environment = environment
        }
    }

    private class RecordingAppiumExtensionCommandRunner(
        private vararg val results: AppiumExtensionCommandResult,
    ) : AppiumExtensionCommandRunner {
        val commands = mutableListOf<List<String>>()

        override fun run(
            command: List<String>,
            workingDirectory: Path?,
            environment: Map<String, String>,
            timeoutMs: Long,
        ): AppiumExtensionCommandResult {
            commands += command
            return results.getOrElse(commands.lastIndex) { AppiumExtensionCommandResult(exitCode = 0) }
        }
    }

    private class FixedPluginSourceResolver(
        private val sourceDir: Path,
    ) : AppiumPluginSourceResolver {
        override fun sourceFor(pluginName: String): Path? {
            return if (pluginName == "soluna-ext") sourceDir else null
        }
    }

    private fun tempPluginSource(): Path {
        val sourceDir = Files.createTempDirectory("soluna-appium-ext-test")
        Files.writeString(sourceDir.resolve("package.json"), """{"name":"soluna-appium-ext"}""")
        return sourceDir.toAbsolutePath().normalize()
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

    private class FixedFfmpegToolResolver(
        private val pathEntry: Path,
    ) : FfmpegToolResolver {
        override fun resolve(): FfmpegToolResolution {
            return FfmpegToolResolution(
                command = pathEntry.resolve("ffmpeg").toString(),
                directory = pathEntry,
                source = FfmpegToolSource.BUNDLED,
            )
        }

        override fun resolvePathEntryForManagedProcess(): Path {
            return pathEntry
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
