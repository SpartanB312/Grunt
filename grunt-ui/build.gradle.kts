plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
    alias(libs.plugins.kotlinxSerialization)
}

repositories {
    mavenCentral()
    maven("https://maven.noblesix.net/")
    google()
    maven("https://jitpack.io/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val universalComposeDesktopRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val composeVersion = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
    .named("libs")
    .findVersion("compose")
    .orElseThrow()
    .requiredVersion

dependencies {
    implementation(project(":grunt-main"))

    implementation(libs.filekit.dialogs)
    implementation(libs.flatlaf)
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerializationJson)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.fluent)
    implementation(libs.compose.fluent.icons)

    listOf(
        "windows-x64",
        "linux-x64",
        "macos-x64",
        "macos-arm64",
    ).forEach { target ->
        universalComposeDesktopRuntime("org.jetbrains.compose.desktop:desktop-jvm-$target:$composeVersion")
    }

    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("generateI18nCatalog") {
    group = "i18n"
    description = "Generate the English i18n descriptor catalog from annotations."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.spartanb312.grunteon.ui.I18nCatalogDumperKt")
    args(layout.projectDirectory.file("src/main/resources/i18n/en.json").asFile.absolutePath, "en")
}

tasks.register<Jar>("packageUniversalUberJar") {
    group = "compose desktop"
    description = "Package a runnable UI uber JAR with Compose native runtimes for Windows, Linux, and macOS."

    archiveBaseName.set("Grunteon")
    archiveClassifier.set("all")
    archiveVersion.set(rootProject.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("compose/jars"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "net.spartanb312.grunteon.ui.AppKt")
    }

    exclude(
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "META-INF/INDEX.LIST",
        "META-INF/versions/**",
        "module-info.class",
    )

    from(sourceSets.main.get().output)
    from({
        (configurations.runtimeClasspath.get() + universalComposeDesktopRuntime)
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
}

afterEvaluate {
    val runDir = File(rootProject.rootDir, "run")
    runDir.mkdir()
    tasks.withType<JavaExec>().configureEach {
        workingDir(runDir)
    }
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
