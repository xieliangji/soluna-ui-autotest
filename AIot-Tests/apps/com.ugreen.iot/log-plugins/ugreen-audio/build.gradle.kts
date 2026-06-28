plugins {
    kotlin("jvm") version "2.3.21"
}

group = "io.soluna.ugreen.applog"
version = "0.1.0"

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
    archiveBaseName.set("ugreen-audio-app-log-plugin")
}
