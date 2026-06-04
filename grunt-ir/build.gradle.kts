plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    library(libs.bundles.asm)

    testImplementation(kotlin("test"))
}

application {
    mainClass = "net.spartanb312.grunt.ir.ssa.MainKt"
}
