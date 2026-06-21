package net.spartanb312.grunteon.obfuscator.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageTest {

    @Test
    fun exposesCoreLanguageEnumEntries() {
        assertEquals(
            listOf(
                "Arabic",
                "ChineseCN",
                "ChineseTW",
                "English",
                "French",
                "German",
                "Japanese",
                "Korean",
                "Portuguese",
                "Russian",
                "Spanish",
            ),
            Language.entries.map { it.name }
        )
    }

    @Test
    fun languageCodesUseBcp47Style() {
        assertEquals(emptyList(), Language.entries.flatMap { it.resourceCodes }.filter { it.contains('_') })
        assertTrue(Language.entries.flatMap { it.resourceCodes }.all { it == it.trim() && it.isNotEmpty() })
    }

    @Test
    fun englishKeepsGenericResourceCodeAndCommonAliases() {
        assertEquals("en", Language.English.code)
        assertEquals(listOf("en", "en-US", "en-GB"), Language.English.resourceCodes)
    }

    @Test
    fun portugueseKeepsGenericResourceCodeAndCommonAliases() {
        assertEquals("pt", Language.Portuguese.code)
        assertEquals(listOf("pt", "pt-PT", "pt-BR"), Language.Portuguese.resourceCodes)
    }
}
