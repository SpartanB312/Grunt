plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(libs.junitAPI)
    implementation(kotlin("test"))
    implementation(libs.bundles.asm)
}