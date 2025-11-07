rootProject.name = "Grunteon"


// Launch bootstrap
include(":grunt-bootstrap")

// Components
include(":grunt-main")
include(":grunt-testcase")
//include(":grunt-ir")

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

includeBuild("../Genesis") {
    dependencySubstitution {
        substitute(module("net.spartanb312:genesis-kotlin")).using(project(":genesis-kotlin"))
    }
}
