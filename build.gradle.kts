plugins {
    kotlin("jvm") version "2.3.21"
    application
}

group = "com.soluna"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    ivy {
        name = "ktVisualGitHubReleases"
        url = uri("https://github.com/xieliangji/kt-visual/releases/download")
        patternLayout {
            artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]")
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.networknt:json-schema-validator:1.5.1")
    implementation("io.appium:java-client:10.1.1")
    implementation("io.minio:minio:8.5.17")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    implementation("com.soluna:kt-visual:0.3.1")
    implementation("com.soluna:kt-visual-ocr-paddle:0.3.1:with-models@jar")
    implementation("com.soluna:kt-visual-ocr-multimodal:0.3.1")
    implementation("com.openai:openai-java-client-okhttp:4.39.1")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    implementation("org.openpnp:opencv:4.9.0-0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    applicationName = "soluna"
    mainClass.set("com.soluna.ui.autotest.cli.SolunaCli")
}

distributions {
    main {
        contents {
            from("tools") {
                into("tools")
                includeEmptyDirs = true
            }
            from("lib/soluna-appium-ext") {
                into("plugins/soluna-appium-ext")
                exclude("node_modules/**", "build/**")
                includeEmptyDirs = true
            }
            from("codex/skills") {
                into("codex/skills")
                includeEmptyDirs = true
            }
        }
    }
}
