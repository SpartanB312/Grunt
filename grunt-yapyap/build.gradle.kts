plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinxSerialization)
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
    implementation(project(":grunt-main"))
    implementation(project(":grunt-ui"))
    implementation(project(":grunt-bootstrap"))
    implementation("net.spartanb312:genesis-kotlin:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
    implementation(libs.kotlinxSerializationCore)

    // libraries
    implementation(libs.bundles.asm)
    implementation(libs.bundles.utils)
    implementation(libs.bundles.apache.common)
    implementation(libs.bundles.jpbc)
}

tasks {
    test {
        enabled = false
    }

    jar {
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Entry-Class" to "net.spartanb312.grunt.yapyap.Yapyap",
                "Grunt-Plugin-Id" to "grunt-yapyap",
                "Grunt-Plugin-Name" to "Grunt Yapyap",
                "Grunt-Plugin-Version" to "1.0",
                "Grunt-Plugin-Api" to "1"
            )
        }
    }
}
