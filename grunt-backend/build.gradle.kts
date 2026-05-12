plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.0.6"
}

repositories {
    mavenCentral()
    maven("https://maven.noblesix.net/")
    google()
    maven("https://jitpack.io/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.6"))
    implementation(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom:2.3.21"))
    implementation(project(":grunt-main"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}

tasks {
    bootJar {
        archiveFileName.set("grunt-backend.jar")
    }
}
