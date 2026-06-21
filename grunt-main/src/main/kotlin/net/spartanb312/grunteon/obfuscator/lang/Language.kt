package net.spartanb312.grunteon.obfuscator.lang

import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum

enum class Language(
    override val displayName: CharSequence,
    val code: String,
    private vararg val aliases: String,
) : DisplayEnum {
    Arabic("العربية", "ar"),
    ChineseCN("简体中文(中国大陆)", "zh-CN"),
    ChineseTW("繁體中文(台灣)", "zh-TW"),
    English("English", "en", "en-US", "en-GB"),
    French("français", "fr-FR"),
    German("Deutsch", "de-DE"),
    Japanese("日本語", "ja-JP"),
    Korean("한국어", "ko-KR"),
    Portuguese("português", "pt", "pt-PT", "pt-BR"),
    Russian("русский", "ru-RU"),
    Spanish("español", "es-ES"),
    ;

    val resourceCodes: List<String>
        get() = listOf(code) + aliases
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class I18NDescriptorPath(val value: String)
