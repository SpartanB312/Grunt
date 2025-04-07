import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    java
    kotlin("jvm")
    id("dev.luna5ama.jar-optimizer")
}

repositories {
    mavenCentral()
    maven("https://repo1.maven.org/maven2/")
    maven("https://mvnrepository.com/artifact/")
}

val kotlinxCoroutineVersion = "1.7.3"
val asmVersion = "9.7.1"

val library: Configuration by configurations.creating

configurations {
    api.extendsFrom(named("library"))
}

dependencies {
    library(project(":genesis"))
    //Kotlin
    library(kotlin("stdlib"))
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutineVersion")

    //ASM
    library("org.ow2.asm:asm:$asmVersion")
    library("org.ow2.asm:asm-tree:$asmVersion")
    library("org.ow2.asm:asm-commons:$asmVersion")
    library("org.ow2.asm:asm-analysis:$asmVersion")

    //GSON
    library("com.google.code.gson:gson:2.12.1")

    //GUI
    library("com.miglayout:miglayout-swing:5.3")
    library("com.github.weisj:darklaf-core:3.0.2")

    library("org.apache.commons:commons-lang3:3.0")
    library("javassist:javassist:3.12.1.GA")
    library("it.unimi.dsi:fastutil:8.5.9")

//    api(library)

}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
    jar {
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunt.GruntKt"
            )
        }
    }
    val fatJar by creating(Jar::class) {
        group = "build"
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunt.GruntKt"
            )
        }
        from(jar.get().archiveFile.map { zipTree(it) })
        from(
            library.map {
                if (it.isDirectory) it
                else zipTree(it)
            }
        )
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveClassifier.set("full")
    }
    val optimizeFatJar = jarOptimizer.register(
        fatJar,
        "net", "org", "com"
    )
    artifacts {
        archives(optimizeFatJar)
    }
}