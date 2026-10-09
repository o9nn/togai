pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

plugins {
    // Enables Gradle to auto-download JDK toolchains (e.g. JDK 11) when the
    // required version isn't installed locally. Only the machine here had
    // JDK 21; this was added to unblock `gradle test` without changing the
    // project's declared jvmToolchain(11) target.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

rootProject.name = "togai"
include(":app")
