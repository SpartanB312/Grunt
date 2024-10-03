plugins {
    java
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://repo1.maven.org/maven2/")
    maven("https://mvnrepository.com/artifact/")
}

val library: Configuration by configurations.creating
val projectModule: Configuration by configurations.creating

dependencies {
    //Kotlin
    projectModule(project(":grunt-main"))

    implementation(library)
    api(projectModule)
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
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveBaseName.set(project.name.toLowerCase())

        manifest {
            attributes(
                "Entry-Point" to "net.spartanb312.grunt.example.Main"
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