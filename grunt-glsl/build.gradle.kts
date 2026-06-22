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

dependencies {
    implementation(project(":grunt-main"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutine.get()}")
    implementation(libs.kotlinxSerializationCore)

    testImplementation(kotlin("test"))
}

tasks {
    jar {
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Entry-Class" to "net.spartanb312.grunt.glsl.GlslPlugin",
                "Grunt-Plugin-Id" to "glsl",
                "Grunt-Plugin-Name" to "glsl",
                "Grunt-Plugin-Version" to "1.0",
                "Grunt-Plugin-Api" to "1"
            )
        }
    }
}
