group = "net.spartanb312"
version = "1.4.1"

plugins {
    java
    kotlin("jvm") version "1.6.10"
}

repositories {
    mavenCentral()
    maven("https://repo1.maven.org/maven2/")
    maven("https://mvnrepository.com/artifact/")
}

val kotlinVersion: String by project
val kotlinxCoroutineVersion: String by project
val asmVersion: String by project

val library: Configuration by configurations.creating

dependencies {
    //Kotlin
    library("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    //ASM
    library("org.ow2.asm:asm:$asmVersion")
    library("org.ow2.asm:asm-tree:$asmVersion")
    library("org.ow2.asm:asm-commons:$asmVersion")

    //Other dependencies
    library("com.google.code.gson:gson:2.10")

    implementation(library)
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
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn", "-Xinline-classes")
        }
    }

    jar {
        archiveBaseName.set(project.name.toLowerCase())

        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunt.GruntKt"
            )
        }

        from(
            library.map {
                if (it.isDirectory) it
                else zipTree(it)
            }
        )

        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
    }

}