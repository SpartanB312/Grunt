package buildsrc.convention

group = "net.spartanb312"

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val library: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
val projectLib: Configuration by configurations.creating {
    configurations.api.get().extendsFrom(this)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}