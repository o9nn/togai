buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
        classpath("com.google.gms:google-services:4.4.1")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    kotlin("android") version "1.9.25" apply false
    kotlin("plugin.serialization") version "1.9.25" apply false
}

// Repositories are centralized in settings.gradle.kts via dependencyResolutionManagement

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
