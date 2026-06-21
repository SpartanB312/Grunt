package net.spartanb312.grunteon.ui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UiI18nCatalogTest {

    @Test
    fun committedEnglishCatalogMatchesGeneratedDescriptors() {
        val text = catalogText("i18n/en.json")
        val committed = Json.decodeFromString<Map<String, String>>(text)

        assertEquals(UiI18nCatalog.buildEnglishCatalog(), committed)
    }

    @Test
    fun simplifiedChineseCatalogHasSameKeysAsEnglish() {
        val english = Json.decodeFromString<Map<String, String>>(catalogText("i18n/en.json"))
        val simplifiedChinese = Json.decodeFromString<Map<String, String>>(catalogText("i18n/zh-CN.json"))

        assertEquals(english.keys, simplifiedChinese.keys)
    }

    @Test
    fun simplifiedChineseCatalogHasNoBlankValues() {
        val simplifiedChinese = Json.decodeFromString<Map<String, String>>(catalogText("i18n/zh-CN.json"))

        assertEquals(emptyList(), simplifiedChinese.filterValues { it.isBlank() }.keys.toList())
    }

    @Test
    fun uiCatalogKeysAreNamespaced() {
        val english = Json.decodeFromString<Map<String, String>>(catalogText("i18n/en.json"))
        val simplifiedChinese = Json.decodeFromString<Map<String, String>>(catalogText("i18n/zh-CN.json"))

        assertEquals(emptyList(), english.keys.filterNot { it.startsWith("ui.") })
        assertEquals(emptyList(), simplifiedChinese.keys.filterNot { it.startsWith("ui.") })
    }

    @Test
    fun committedEnglishCatalogIsSortedByKey() {
        val keys = Regex("^    \"([^\"]+)\"", RegexOption.MULTILINE)
            .findAll(catalogText("i18n/en.json"))
            .map { it.groupValues[1] }
            .toList()

        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun committedSimplifiedChineseCatalogIsSortedByKey() {
        val keys = Regex("^    \"([^\"]+)\"", RegexOption.MULTILINE)
            .findAll(catalogText("i18n/zh-CN.json"))
            .map { it.groupValues[1] }
            .toList()

        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun nativeCandidateRulesUseItemDescriptorKeys() {
        val catalog = Json.decodeFromString<Map<String, String>>(catalogText("i18n/en.json"))

        assertEquals("Rules", catalog["ui.transformer.nativeCandidate.config.rules.name"])
        assertEquals("Detection rule", catalog["ui.transformer.nativeCandidate.config.rules.item.name"])
        assertEquals("Set detection rule for scanner", catalog["ui.transformer.nativeCandidate.config.rules.item.desc"])
        assertEquals("Name", catalog["ui.transformer.nativeCandidate.config.rules.item.name.name"])
    }

    @Test
    fun ordinaryUiTextKeysAreIncluded() {
        val catalog = Json.decodeFromString<Map<String, String>>(catalogText("i18n/en.json"))

        assertEquals("File", catalog["ui.toolbar.file"])
        assertEquals("Confirm", catalog["ui.dialog.confirm"])
        assertEquals("Ready", catalog["ui.status.ready"])
        assertEquals("Empty List", catalog["ui.configEditor.list.empty"])
        assertEquals("Enabled {count} transformers", catalog["ui.obfuscation.enabledTransformers"])
        assertEquals(
            "Enabled {count} transformers with native obfuscation",
            catalog["ui.obfuscation.enabledTransformersWithNative"]
        )
    }

    @Test
    fun ordinaryUiTextKeysAreTranslatedToSimplifiedChinese() {
        val catalog = Json.decodeFromString<Map<String, String>>(catalogText("i18n/zh-CN.json"))

        assertEquals("文件", catalog["ui.toolbar.file"])
        assertEquals("确认", catalog["ui.dialog.confirm"])
        assertEquals("就绪", catalog["ui.status.ready"])
        assertEquals("空列表", catalog["ui.configEditor.list.empty"])
        assertEquals("已启用 {count} 个 Transformer", catalog["ui.obfuscation.enabledTransformers"])
        assertEquals(
            "已启用 {count} 个 Transformer，并开启 Native 混淆",
            catalog["ui.obfuscation.enabledTransformersWithNative"]
        )
        assertEquals("语言", catalog["ui.app.config.config.language.name"])
        assertEquals("UI 使用的语言", catalog["ui.app.config.config.language.desc"])
        assertEquals("UI 启动时执行的操作", catalog["ui.app.config.config.startupAction.desc"])
        assertEquals("应用到整个 UI 布局的缩放系数", catalog["ui.app.config.config.uiScale.desc"])
        assertEquals("应用到 UI 文本的缩放系数", catalog["ui.app.config.config.fontScale.desc"])
        assertEquals("UI 使用的主题偏好", catalog["ui.app.config.config.themeMode.desc"])
        assertEquals("混淆日志面板显示的最低日志级别", catalog["ui.app.config.config.uiLogLevel.desc"])
        assertEquals("编译器可执行文件", catalog["ui.config.nativePipeline.config.compilerExecutable.name"])
        assertEquals(
            "可选的 C++ 编译器可执行文件路径或命令名。为空时使用自动检测。",
            catalog["ui.config.nativePipeline.config.compilerExecutable.desc"]
        )
        assertEquals("NativeCandidate", catalog["ui.transformer.nativeCandidate.name"])
        assertEquals("检测规则", catalog["ui.transformer.nativeCandidate.config.rules.item.name"])
        assertEquals("操作替换概率", catalog["ui.transformer.arithmeticSubstitute.config.chance.name"])
        assertEquals("字段访问代理替换概率", catalog["ui.transformer.fieldAccessProxy.config.chance.name"])
        assertEquals("调用分派替换概率", catalog["ui.transformer.invokeDispatcher.config.chance.name"])
        assertEquals("调用代理替换概率", catalog["ui.transformer.invokeProxy.config.chance.name"])
        assertEquals("调用替换概率", catalog["ui.transformer.referenceObfuscate.config.chance.name"])
    }

    private fun catalogText(resourceName: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(resourceName)
        assertNotNull(stream)
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
