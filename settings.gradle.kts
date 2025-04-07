rootProject.name = "Grunt"

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://maven.luna5ama.dev")
        gradlePluginPortal()
    }

    plugins {
        id("dev.luna5ama.jar-optimizer") version "1.2-SNAPSHOT"
    }
}

// Main
include(":genesis")
include(":grunt-main")

// Plugin
include(":grunt-plugin")
include(":grunt-authenticator")
include(":grunt-verp")

// Subproject
include(":grunt-annotation")
include(":grunt-hwid")