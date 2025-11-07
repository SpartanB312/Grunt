package buildsrc.convention

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

group = "net.spartanb312"

plugins {
    id("buildsrc.convention.java")
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-call-assertions",
            "-Xcontext-parameters",
            "-Xbackend-threads=0"
//            "-Xcontext-parameters",
//            "-Xassertions=jvm",
//            "-Xno-call-assertions",
//            "-Xno-receiver-assertions",
//            "-Xno-param-assertions",
//            "-Xuse-type-table",
//            "-Xuse-fast-jar-file-system",
//            "-Xcompile-java",
//            "-Xjvm-default=all",
//            "-Xnested-type-aliases",
//            "-Xcontext-sensitive-resolution"
        )
    }
}