plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "com.sprigbyte"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    intellijPlatform {
        rider("2025.2.3") {
            useInstaller.set(false)
        }
        jetbrainsRuntime()

        // Add necessary plugin dependencies for compilation
        bundledPlugin("Git4Idea")

        bundledModule("intellij.platform.vcs.impl")
        
        // Plugin verification
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Fix error when trying to create pull request.
            Add AI optional diff summary to the pull request description.
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}