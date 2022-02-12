import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
}

group = "com.github.b3er"
version = "0.23"

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
  implementation(libs.commons.compress)
  implementation(libs.xz)
}

intellij {
  pluginName.set("idea-archive-browser")
  version.set("2021.1")
  type.set("IC")
  plugins.add("IntelliLang")
}

val patchPluginXml by tasks
patchPluginXml.enabled = false



