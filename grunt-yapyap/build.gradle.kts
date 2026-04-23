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
    implementation(project(":grunt-bootstrap"))
    implementation("net.spartanb312:genesis-kotlin:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")

    // libraries
    implementation(libs.bundles.asm)
    implementation(libs.bundles.utils)
}

tasks {
    jar {
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Entry-Class" to "net.spartanb312.grunt.yapyap.Yapyap"
            )
        }
    }
}