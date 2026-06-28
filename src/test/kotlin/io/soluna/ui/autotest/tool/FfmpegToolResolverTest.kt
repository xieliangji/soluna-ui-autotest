package io.soluna.ui.autotest.tool

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FfmpegToolResolverTest {
    @Test
    fun `explicit system property ffmpeg path wins`() {
        val explicit = "/opt/soluna/ffmpeg"
        val bundled = "/opt/soluna/tools/ffmpeg/macos-arm64/ffmpeg"
        val resolver = DefaultFfmpegToolResolver(
            platform = FfmpegPlatform("macos", "arm64"),
            environment = emptyMap(),
            propertyProvider = { key ->
                when (key) {
                    DefaultFfmpegToolResolver.PROPERTY_FFMPEG_PATH -> explicit
                    DefaultFfmpegToolResolver.PROPERTY_TOOLS_DIR -> "/opt/soluna/tools"
                    else -> null
                }
            },
            probe = FakeToolProbe(setOf(explicit, bundled)),
        )

        val resolution = resolver.resolve()

        assertEquals(explicit, resolution.command)
        assertEquals(FfmpegToolSource.EXPLICIT, resolution.source)
        assertEquals(Path.of("/opt/soluna"), resolution.directory)
    }

    @Test
    fun `bundled app home ffmpeg path is selected before PATH fallback`() {
        val appHome = createTempDirectory()
        val expected = appHome.resolve("tools/ffmpeg/linux-x64/ffmpeg").toString()
        val resolver = DefaultFfmpegToolResolver(
            platform = FfmpegPlatform("linux", "x64"),
            environment = emptyMap(),
            propertyProvider = { null },
            workingDirectory = createTempDirectory(),
            classPath = appHome.resolve("lib/soluna.jar").toString(),
            probe = FakeToolProbe(setOf(expected, "ffmpeg")),
        )

        val resolution = resolver.resolve()

        assertEquals(expected, resolution.command)
        assertEquals(FfmpegToolSource.BUNDLED, resolution.source)
        assertEquals(appHome.resolve("tools/ffmpeg/linux-x64"), resolution.directory)
    }

    @Test
    fun `PATH fallback remains available when no bundled ffmpeg exists`() {
        val resolver = DefaultFfmpegToolResolver(
            platform = FfmpegPlatform("macos", "arm64"),
            environment = emptyMap(),
            propertyProvider = { null },
            workingDirectory = createTempDirectory(),
            classPath = "",
            probe = FakeToolProbe(setOf("ffmpeg")),
        )

        val resolution = resolver.resolve()

        assertEquals("ffmpeg", resolution.command)
        assertEquals(FfmpegToolSource.PATH, resolution.source)
        assertEquals(null, resolution.directory)
        assertEquals(null, resolver.resolvePathEntryForManagedProcess())
    }

    @Test
    fun `windows platform resolves ffmpeg exe bundled path`() {
        val tools = createTempDirectory()
        val expected = tools.resolve("ffmpeg/windows-x64/ffmpeg.exe").toString()
        val resolver = DefaultFfmpegToolResolver(
            platform = FfmpegPlatform("windows", "x64"),
            environment = emptyMap(),
            propertyProvider = { key ->
                if (key == DefaultFfmpegToolResolver.PROPERTY_TOOLS_DIR) tools.toString() else null
            },
            probe = FakeToolProbe(setOf(expected)),
        )

        val resolution = resolver.resolve()

        assertEquals(expected, resolution.command)
        assertEquals(FfmpegToolSource.BUNDLED, resolution.source)
    }

    @Test
    fun `missing ffmpeg reports platform and expected bundled location`() {
        val resolver = DefaultFfmpegToolResolver(
            platform = FfmpegPlatform("linux", "arm64"),
            environment = emptyMap(),
            propertyProvider = { null },
            workingDirectory = createTempDirectory(),
            classPath = "",
            probe = FakeToolProbe(emptySet()),
        )

        val error = assertFailsWith<IllegalStateException> {
            resolver.resolve()
        }

        val message = error.message.orEmpty()
        kotlin.test.assertTrue(message.contains("linux-arm64"))
        kotlin.test.assertTrue(message.contains("tools/ffmpeg/linux-arm64/ffmpeg"))
    }

    @Test
    fun `prepend path entry keeps existing path value`() {
        val result = prependPathEntry(
            environment = mapOf("PATH" to "/usr/bin"),
            pathEntry = Path.of("/opt/soluna/ffmpeg"),
            baseEnvironment = emptyMap(),
        )

        assertEquals("/opt/soluna/ffmpeg${java.io.File.pathSeparator}/usr/bin", result["PATH"])
    }

    private class FakeToolProbe(
        private val runnableCommands: Set<String>,
    ) : ToolExecutableProbe {
        override fun canRun(command: String): Boolean {
            return command in runnableCommands
        }
    }
}
