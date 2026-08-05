import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}

group = "com.github.b3er"
version = "0.33"

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.sevenzip)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    intellijPlatform {
        intellijIdea("2026.2.0.1")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Archive Browser"
        ideaVersion {
            sinceBuild.set("251")
        }
    }
    pluginVerification {
        freeArgs = listOf("-mute", "TemplateWordInPluginName")
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            create(IntelliJPlatformType.IntellijIdea, "2026.2.0.1")
        }
    }
}
