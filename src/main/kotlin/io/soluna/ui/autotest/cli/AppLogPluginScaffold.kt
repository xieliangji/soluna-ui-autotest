package io.soluna.ui.autotest.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class AppLogPluginScaffold {
    fun create(request: AppLogPluginScaffoldRequest): AppLogPluginScaffoldResult {
        val output = request.output.toAbsolutePath().normalize()
        if (Files.exists(output) && !request.force) {
            Files.list(output).use { entries ->
                if (entries.findAny().isPresent) {
                    throw IllegalArgumentException("Output directory is not empty: $output")
                }
            }
        }

        val packagePath = request.packageName.replace('.', '/')
        val pluginClassName = request.pluginClassName ?: request.pluginId.toPascalCase() + "AppLogPlugin"
        val assertionClassName = request.assertionName.toPascalCase() + "Assertion"
        val projectName = request.projectName ?: request.pluginId.toSlug() + "-app-log-plugin"
        val files = linkedMapOf(
            output.resolve("settings.gradle.kts") to settingsGradle(projectName),
            output.resolve("build.gradle.kts") to buildGradle(
                group = request.group ?: request.packageName,
                version = request.version,
                archiveBaseName = projectName,
            ),
            output.resolve("README.md") to readme(
                projectName = projectName,
                pluginId = request.pluginId,
                assertionName = request.assertionName,
            ),
            output.resolve("src/main/kotlin/$packagePath/$pluginClassName.kt") to pluginSource(
                packageName = request.packageName,
                pluginClassName = pluginClassName,
                pluginId = request.pluginId,
                assertionClassName = assertionClassName,
                assertionName = request.assertionName,
            ),
            output.resolve(
                "src/main/resources/META-INF/services/io.soluna.ui.autotest.extension.applog.AppLogAssertionPlugin",
            ) to "${request.packageName}.$pluginClassName\n",
            output.resolve("src/test/kotlin/$packagePath/${assertionClassName}Test.kt") to testSource(
                packageName = request.packageName,
                assertionClassName = assertionClassName,
                pluginId = request.pluginId,
                assertionName = request.assertionName,
            ),
        )

        files.forEach { (path, content) ->
            if (Files.exists(path) && !request.force) {
                throw IllegalArgumentException("Refusing to overwrite existing file: $path")
            }
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        }

        return AppLogPluginScaffoldResult(
            output = output,
            projectName = projectName,
            pluginId = request.pluginId,
            assertionName = request.assertionName,
            files = files.keys.toList(),
        )
    }

    private fun settingsGradle(projectName: String): String {
        return """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "$projectName"
        """.trimIndent() + "\n"
    }

    private fun buildGradle(
        group: String,
        version: String,
        archiveBaseName: String,
    ): String {
        return """
            plugins {
                kotlin("jvm") version "2.3.21"
            }

            group = "$group"
            version = "$version"

            val solunaHome = providers.gradleProperty("solunaHome")
                .orElse(providers.environmentVariable("SOLUNA_HOME"))
                .orNull
                ?: error("Set -PsolunaHome=/path/to/soluna or SOLUNA_HOME=/path/to/soluna before building this plugin.")
            val solunaLib = file(solunaHome).resolve("lib")
            val solunaPluginCompileClasspath = fileTree(solunaLib) {
                include("soluna-ui-autotest-*.jar")
                include("jackson-*.jar")
            }

            dependencies {
                compileOnly(solunaPluginCompileClasspath)

                testImplementation(kotlin("test"))
                testImplementation(solunaPluginCompileClasspath)
            }

            kotlin {
                jvmToolchain(21)
            }

            tasks.test {
                useJUnitPlatform()
            }

            tasks.jar {
                archiveBaseName.set("$archiveBaseName")
            }
        """.trimIndent() + "\n"
    }

    private fun pluginSource(
        packageName: String,
        pluginClassName: String,
        pluginId: String,
        assertionClassName: String,
        assertionName: String,
    ): String {
        return """
            package $packageName

            import io.soluna.ui.autotest.extension.applog.AppLogAssertion
            import io.soluna.ui.autotest.extension.applog.AppLogAssertionInput
            import io.soluna.ui.autotest.extension.applog.AppLogAssertionPlugin
            import io.soluna.ui.autotest.extension.applog.AppLogAssertionResult
            import java.nio.file.Files

            class $pluginClassName : AppLogAssertionPlugin {
                override val id: String = "$pluginId"

                override fun assertion(name: String): AppLogAssertion? {
                    return when (name) {
                        $assertionClassName.name -> $assertionClassName
                        else -> null
                    }
                }
            }

            object $assertionClassName : AppLogAssertion {
                override val name: String = "$assertionName"

                override fun evaluate(input: AppLogAssertionInput): AppLogAssertionResult {
                    val expectedText = input.args?.path("contains")?.takeIf { it.isTextual }?.asText()
                        ?: return AppLogAssertionResult.failed("args.contains is required")

                    val matched = Files.readAllLines(input.logFile).any { line ->
                        line.contains(expectedText)
                    }

                    return if (matched) {
                        AppLogAssertionResult.passed("Found app log text: " + expectedText)
                    } else {
                        AppLogAssertionResult.failed("No app log entry contains: " + expectedText)
                    }
                }
            }
        """.trimIndent() + "\n"
    }

    private fun testSource(
        packageName: String,
        assertionClassName: String,
        pluginId: String,
        assertionName: String,
    ): String {
        return """
            package $packageName

            import com.fasterxml.jackson.databind.ObjectMapper
            import io.soluna.ui.autotest.extension.applog.AppLogAssertionInput
            import io.soluna.ui.autotest.extension.applog.AppLogAssertionRunContext
            import java.nio.file.Files
            import kotlin.test.Test
            import kotlin.test.assertTrue

            class ${assertionClassName}Test {
                @Test
                fun `passes when expected text appears in app log file`() {
                    val logFile = Files.createTempFile("app-log-", ".jsonl")
                    Files.writeString(logFile, "{\"message\":\"BLE command ack success\"}\n")

                    val result = $assertionClassName.evaluate(
                        AppLogAssertionInput(
                            pluginId = "$pluginId",
                            assertionName = "$assertionName",
                            logFile = logFile,
                            source = null,
                            args = ObjectMapper().createObjectNode().put("contains", "BLE command ack"),
                            context = AppLogAssertionRunContext(
                                runId = "run-test",
                                planId = "plan-test",
                                stageId = "stage-test",
                                caseId = "case-test",
                                platform = "ios",
                                udid = "TEST_UDID",
                            ),
                        ),
                    )

                    assertTrue(result.passed)
                }
            }
        """.trimIndent() + "\n"
    }

    private fun readme(
        projectName: String,
        pluginId: String,
        assertionName: String,
    ): String {
        return """
            # $projectName

            Soluna App log 断言插件。

            当前项目的 package/group 应使用 `io.soluna` 或其子命名空间。ServiceLoader 文件名必须保持为：

            ```text
            META-INF/services/io.soluna.ui.autotest.extension.applog.AppLogAssertionPlugin
            ```

            ## 构建

            `SOLUNA_HOME` 指向已安装的 Soluna distribution 根目录：

            ```bash
            SOLUNA_HOME=/path/to/soluna gradle test jar
            ```

            也可以使用 Gradle property：

            ```bash
            gradle test jar -PsolunaHome=/path/to/soluna
            ```

            ## 安装

            将构建出的 JAR 放入任一 runtime plugin 目录：

            ```bash
            cp build/libs/$projectName-*.jar /path/to/soluna/plugins/app-log/
            ```

            `customAssertAppLog` 可以这样引用：

            ```yaml
            - customAssertAppLog:
                id: assert-app-log
                plugin: $pluginId
                assertion: $assertionName
                args:
                  contains: BLE command ack
            ```
        """.trimIndent() + "\n"
    }
}

data class AppLogPluginScaffoldRequest(
    val output: Path,
    val pluginId: String,
    val packageName: String,
    val assertionName: String = "contains-text",
    val projectName: String? = null,
    val pluginClassName: String? = null,
    val group: String? = null,
    val version: String = "0.1.0",
    val force: Boolean = false,
)

data class AppLogPluginScaffoldResult(
    val output: Path,
    val projectName: String,
    val pluginId: String,
    val assertionName: String,
    val files: List<Path>,
)

private fun String.toPascalCase(): String {
    return split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString(separator = "") { part ->
            part.replaceFirstChar { char -> char.uppercaseChar() }
        }
        .ifBlank { "Custom" }
        .let { value ->
            if (value.first().isDigit()) "Plugin$value" else value
        }
}

private fun String.toSlug(): String {
    return lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "app-log-plugin" }
}
