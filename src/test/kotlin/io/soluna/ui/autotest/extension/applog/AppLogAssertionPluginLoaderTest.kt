package io.soluna.ui.autotest.extension.applog

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AppLogAssertionPluginLoaderTest {
    @Test
    fun `loads app log assertion plugin from inferred asset root directory`() {
        val root = Files.createTempDirectory("soluna-app-log-plugin-loader-test")
        val planPath = root.resolve("plans/debug/ios.yaml")
        Files.createDirectories(planPath.parent)
        Files.writeString(planPath, "schemaVersion: '1.0'\n")
        writeServiceJar(
            jar = root.resolve(AppLogAssertionPluginLoader.DEFAULT_PLUGIN_DIRECTORY).resolve("directory-test.jar"),
            providerClass = DirectoryLoadedAppLogAssertionPlugin::class.java.name,
        )

        val registry = AppLogAssertionPluginLoader.defaultRegistry(
            planPath = planPath,
            workingDirectory = root.resolve("work"),
            applicationRoot = root.resolve("dist"),
            parentClassLoader = DirectoryLoadedAppLogAssertionPlugin::class.java.classLoader,
        )

        val assertion = assertNotNull(registry.find("directory-test", "loaded"))
        assertEquals("loaded", assertion.name)
    }

    @Test
    fun `resolves configured app log plugin directories relative to working directory`() {
        val workingDirectory = Path.of("/tmp/soluna-app-log-plugin-config").toAbsolutePath().normalize()

        val directories = AppLogAssertionPluginLoader.configuredPluginDirectories(
            workingDirectory = workingDirectory,
            propertyValue = "relative/plugins${java.io.File.pathSeparator}/absolute/plugins",
            envValue = null,
        )

        assertEquals(
            listOf(
                workingDirectory.resolve("relative/plugins").normalize(),
                Path.of("/absolute/plugins").normalize(),
            ),
            directories,
        )
    }

    private fun writeServiceJar(
        jar: Path,
        providerClass: String,
    ) {
        Files.createDirectories(jar.parent)
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(JarEntry("META-INF/services/${AppLogAssertionPlugin::class.java.name}"))
            output.write((providerClass + "\n").toByteArray(StandardCharsets.UTF_8))
            output.closeEntry()
        }
    }
}

class DirectoryLoadedAppLogAssertionPlugin : AppLogAssertionPlugin {
    override val id: String = "directory-test"

    override fun assertion(name: String): AppLogAssertion? {
        return if (name == DirectoryLoadedAppLogAssertion.name) {
            DirectoryLoadedAppLogAssertion
        } else {
            null
        }
    }
}

object DirectoryLoadedAppLogAssertion : AppLogAssertion {
    override val name: String = "loaded"

    override fun evaluate(input: AppLogAssertionInput): AppLogAssertionResult {
        return AppLogAssertionResult.passed("loaded")
    }
}
