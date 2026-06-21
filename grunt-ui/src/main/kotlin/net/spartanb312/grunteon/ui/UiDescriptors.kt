package net.spartanb312.grunteon.ui

internal object UiDescriptorPaths {
    const val AppConfig = "ui.app.config.config"
    const val GlobalConfig = "ui.config.global.config"
    const val NativePipelineConfig = "ui.config.nativePipeline.config"
}

internal fun uiDescriptorPath(path: String): String {
    return if (path.startsWith("ui.")) path else "ui.$path"
}

internal fun Map<String, String>.withUiDescriptorNamespace(): Map<String, String> {
    val namespaced = linkedMapOf<String, String>()
    for ((key, value) in this) {
        val namespacedKey = uiDescriptorPath(key)
        val previous = namespaced.putIfAbsent(namespacedKey, value)
        require(previous == null || previous == value) {
            "Duplicate UI i18n descriptor key '$namespacedKey' has conflicting fallbacks: '$previous' vs '$value'"
        }
    }
    return namespaced.toSortedMap()
}
