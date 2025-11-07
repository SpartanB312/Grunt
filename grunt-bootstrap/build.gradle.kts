plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
}

tasks {
    jar {
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunteon.bootstrap.Main"
            )
        }
    }
}