import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}

group = "com.github.b3er"
version = "0.32"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.sevenzip)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

intellij {
    pluginName.set("idea-archive-browser")
    version.set("2022.2")
    type.set("IC")
    plugins.add("IntelliLang")
    tasks {
        buildSearchableOptions {
            enabled = false
        }
    }
}

tasks.withType<PatchPluginXmlTask> {
    untilBuild.set("")
}
