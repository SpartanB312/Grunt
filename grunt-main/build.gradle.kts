plugins {
    id("buildsrc.convention.kotlin-jvm")
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
    jar {
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Launch-Entry" to "net.spartanb312.grunteon.obfuscator.ApplicationEntry"
            )
        }
    }
}