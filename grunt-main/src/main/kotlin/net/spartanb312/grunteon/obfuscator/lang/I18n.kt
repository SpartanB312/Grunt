package net.spartanb312.grunteon.obfuscator.lang

import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

data class I18nDescriptor(
    val key: String,
    val fallback: String,
)

object I18n {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val catalogs = ConcurrentHashMap<Language, Map<String, String>>()
    @Volatile
    var currentLanguage: Language = Language.English
        private set

    fun setLanguage(language: Language) {
        currentLanguage = language
    }

    fun text(descriptor: I18nDescriptor): String {
        return text(descriptor.key, descriptor.fallback)
    }

    fun text(key: String, fallback: String): String {
        return catalog(currentLanguage)[key]
            ?: catalog(Language.English)[key]
            ?: fallback.ifBlank { key }
    }

    fun catalog(language: Language): Map<String, String> {
        return catalogs.getOrPut(language) { loadCatalog(language) }
    }

    fun replaceCatalogForTesting(language: Language, catalog: Map<String, String>?) {
        if (catalog == null) {
            catalogs.remove(language)
        } else {
            catalogs[language] = catalog
        }
    }

    fun clearCatalogsForTesting() {
        catalogs.clear()
    }

    private fun loadCatalog(language: Language): Map<String, String> {
        val stream = language.resourceCodes.firstNotNullOfOrNull { code ->
            val resourceName = "i18n/$code.json"
            Thread.currentThread().contextClassLoader?.getResourceAsStream(resourceName)
                ?: I18n::class.java.classLoader.getResourceAsStream(resourceName)
        } ?: return emptyMap()
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            json.decodeFromString<Map<String, String>>(reader.readText())
        }
    }
}
