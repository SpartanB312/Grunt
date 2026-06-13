plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
}

repositories {
    mavenCentral()
    maven("https://maven.noblesix.net/")
    google()
    maven("https://jitpack.io/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(project(":grunt-main"))

    implementation(libs.filekit.dialogs)
    implementation(libs.flatlaf)
    implementation(libs.kotlinReflect)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.fluent)
    implementation(libs.compose.fluent.icons)
}

compose.desktop {
    application {
        this.mainClass = "net.spartanb312.grunteon.ui.AppKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            packageName = "Grunteon"
            packageVersion = rootProject.version.toString()
        }
    }
}
