package net.spartanb312.grunteon.ui

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val output = Path.of(args.firstOrNull() ?: "src/main/resources/i18n/en.json")
    val languageCode = args.getOrNull(1) ?: output.fileName.toString().removeSuffix(".json")
    require(languageCode == "en") {
        "Only the English source catalog is generated. Translation catalogs are maintained as resource files."
    }
    output.parent?.createDirectories()
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }
    output.writeText(json.encodeToString(UiI18nCatalog.buildEnglishCatalog()))
}
