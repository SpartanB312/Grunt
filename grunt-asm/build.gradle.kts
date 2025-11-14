plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // libraries
    library(libs.bundles.asm)
    library(libs.fastutil)

    testImplementation(kotlin("test"))
    testImplementation(project(":grunt-testcase"))
}