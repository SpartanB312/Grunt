rootProject.name = "Grunteon"

// Launch bootstrap
include(":grunt-bootstrap")

// Components
include(":grunt-main")
include(":grunt-ir")
include(":grunt-index")
include(":grunt-testcase")
include(":grunt-yapyap")
include(":grunt-ui")
include(":grunt-backend")

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
