plugins {
    java
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://repo1.maven.org/maven2/")
    maven("https://mvnrepository.com/artifact/")
}

val kotlinVersion = "1.9.21"
val kotlinxCoroutineVersion = "1.7.3"
val asmVersion = "9.7"

val library: Configuration by configurations.creating

dependencies {
    //Kotlin
    library(kotlin("stdlib"))
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutineVersion")
    library("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    //ASM
    library("org.ow2.asm:asm:$asmVersion")
    library("org.ow2.asm:asm-tree:$asmVersion")
    library("org.ow2.asm:asm-commons:$asmVersion")

    //GSON
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