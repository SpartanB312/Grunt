plugins {
    id("buildsrc.convention.java")
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