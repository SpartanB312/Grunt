package net.spartanb312.grunteon.obfuscator.lang

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {

    @AfterTest
    fun tearDown() {
        I18n.clearCatalogsForTesting()
        I18n.setLanguage(Language.English)
    }

    @Test
    fun returnsResourceTextWhenKeyExists() {
        I18n.replaceCatalogForTesting(Language.English, mapOf("known.key" to "Catalog text"))

        assertEquals("Catalog text", I18n.text("known.key", "Fallback text"))
    }

    @Test
    fun fallsBackToEnglishCatalogWhenCurrentLanguageResourceIsMissing() {
        I18n.setLanguage(Language.Japanese)
        I18n.replaceCatalogForTesting(Language.Japanese, emptyMap())
        I18n.replaceCatalogForTesting(Language.English, mapOf("known.key" to "English catalog text"))

        assertEquals("English catalog text", I18n.text("known.key", "Fallback text"))
    }

    @Test
    fun fallsBackToAnnotationTextWhenResourceKeyIsMissing() {
        I18n.replaceCatalogForTesting(Language.English, emptyMap())

        assertEquals("Fallback text", I18n.text("missing.key", "Fallback text"))
    }

    @Test
    fun fallsBackToDescriptorKeyWhenResourceAndAnnotationTextAreMissing() {
        I18n.replaceCatalogForTesting(Language.English, emptyMap())

        assertEquals("missing.key", I18n.text("missing.key", ""))
    }
}
