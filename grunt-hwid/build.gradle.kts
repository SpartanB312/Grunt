plugins {
    java
}

repositories {
    mavenCentral()
}

tasks {
    jar {
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunt.hwid.HWID"
            )
        }
    }
}