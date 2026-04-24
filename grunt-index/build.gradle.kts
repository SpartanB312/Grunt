plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
    maven("https://maven.noblesix.net/")
    google()
    maven("https://jitpack.io/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val coroutineVersion: String = libs.versions.coroutine.get()

dependencies {
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
    library(libs.bundles.asm)
    library(libs.gson)
}

kotlin {
    jvmToolchain(8)
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations["library"].map { if (it.isDirectory) it else zipTree(it) })
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunteon.index.Main"
            )
        }
    }
}
