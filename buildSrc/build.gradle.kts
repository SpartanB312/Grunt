plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.kotlinDSLGradlePlugin)
}