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


val asmVersion = "9.7"

dependencies {
    projectLib(project(":grunt-bootstrap"))
    //projectLib(project(":grunt-ir"))
    projectLib("net.spartanb312:genesis-kotlin:1.0")
    //ASM
    library("org.ow2.asm:asm:$asmVersion")
    library("org.ow2.asm:asm-tree:$asmVersion")
    library("org.ow2.asm:asm-analysis:$asmVersion")
    library("org.ow2.asm:asm-commons:$asmVersion")
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