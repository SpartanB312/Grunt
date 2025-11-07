import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    maven("https://repo1.maven.org/maven2/")
    maven("https://mvnrepository.com/artifact/")
}

val library: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
val projectLib: Configuration by configurations.creating {
    configurations.api.get().extendsFrom(this)
}

dependencies {
    projectLib(project(":grunt-bootstrap"))
    //projectLib(project(":grunt-ir"))
    projectLib("net.spartanb312:genesis-kotlin:1.0")
    // libraries
    library(libs.bundles.asm)
    library("com.google.code.gson:gson:${libs.versions.gson.get()}")
}

tasks {

    java {
        setTargetCompatibility(21)
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.set(
                listOf(
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                    "-Xno-call-assertions",
                    "-Xcontext-parameters"
                )
            )
        }
    }

    jar {
        archiveBaseName.set(project.name.lowercase())
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Launch-Entry" to "net.spartanb312.grunteon.obfuscator.ApplicationEntry"
            )
        }
    }

}