package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.I18nDescriptor
import net.spartanb312.grunteon.obfuscator.lang.Language
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UiTextTest {

    @AfterTest
    fun tearDown() {
        I18n.clearCatalogsForTesting()
        I18n.setLanguage(Language.English)
    }

    @Test
    fun returnsFallbackWhenResourceKeyIsMissing() {
        I18n.replaceCatalogForTesting(Language.English, emptyMap())

        assertEquals("Fallback text", uiText(I18nDescriptor("ui.test.missing", "Fallback text")))
    }

    @Test
    fun replacesProvidedPlaceholdersAndLeavesMissingPlaceholders() {
        I18n.replaceCatalogForTesting(Language.English, emptyMap())

        assertEquals(
            "Hello Codex, {missing}",
            uiText(I18nDescriptor("ui.test.placeholder", "Hello {name}, {missing}"), "name" to "Codex")
        )
    }

    @Test
    fun returnsCatalogTextBeforeFallback() {
        I18n.replaceCatalogForTesting(Language.English, mapOf(UiText.Toolbar.File.key to "Catalog File"))

        assertEquals("Catalog File", uiText(UiText.Toolbar.File))
    }

    @Test
    fun returnsSimplifiedChineseCatalogTextWhenLanguageIsSelected() {
        I18n.setLanguage(Language.ChineseCN)

        assertEquals("文件", uiText(UiText.Toolbar.File))
        assertEquals(
            "已启用 3 个 Transformer，并开启 Native 混淆",
            uiText(UiText.Obfuscation.EnabledTransformersWithNative, "count" to 3)
        )
    }
}
