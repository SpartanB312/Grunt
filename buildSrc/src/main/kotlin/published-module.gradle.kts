package buildsrc.convention

import org.gradle.jvm.tasks.Jar

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}

val extraJarEntries by configurations.registering

dependencies {
    extraJarEntries(rootProject.files("LICENSE"))
}

tasks {
    jar {
        from(extraJarEntries)
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Echonolix"
            )
        }
    }
    named<Jar>("sourcesJar") {
        from(extraJarEntries)
    }
}

tasks.compileJava {
    options.javaModuleVersion = provider { version as String }
}