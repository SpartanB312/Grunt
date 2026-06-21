package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.I18nDescriptor

object UiText {
    private val values = linkedMapOf<String, String>()

    fun desc(key: String, fallback: String): I18nDescriptor {
        val fullKey = "ui.$key"
        val previous = values.putIfAbsent(fullKey, fallback)
        require(previous == null || previous == fallback) {
            "Duplicate UI text key '$fullKey' has conflicting fallbacks: '$previous' vs '$fallback'"
        }
        return I18nDescriptor(fullKey, fallback)
    }

    fun englishCatalog(): Map<String, String> {
        ensureInitialized()
        return values.toSortedMap()
    }

    private fun ensureInitialized() {
        listOf(
            App,
            Status,
            Dialog,
            Toolbar,
            Page,
            ConfigEditor,
            Editor,
            Obfuscation,
            Plugins,
            FileDialog,
            Welcome,
        )
    }

    object App {
        val WindowTitle = desc("app.windowTitle", "Grunteon")
    }

    object Status {
        val Ready = desc("status.ready", "Ready")
        val ObfuscationStarted = desc("status.obfuscation.started", "Obfuscation started")
        val ObfuscationFinished = desc("status.obfuscation.finished", "Obfuscation finished")
        val ObfuscationFailed = desc("status.obfuscation.failed", "Obfuscation failed")
        val CreatedNewConfig = desc("status.config.created", "Created new empty config")
        val AddedTransformer = desc("status.transformer.added", "Added {name}")
        val LoadedConfig = desc("status.config.loaded", "Loaded {count} transformer nodes from {path}")
        val FailedToLoadConfig = desc("status.config.loadFailed", "Failed to load {path}: {message}")
        val FailedToLoadAppConfig = desc("status.appConfig.loadFailed", "Failed to load app config {path}: {message}")
        val FailedToSaveAppConfig = desc("status.appConfig.saveFailed", "Failed to save app config {path}: {message}")
        val FailedToLoadAppState = desc("status.appState.loadFailed", "Failed to load app state {path}: {message}")
        val FailedToSaveAppState = desc("status.appState.saveFailed", "Failed to save app state {path}: {message}")
        val FailedToLoadLastConfig = desc(
            "status.config.lastLoadFailed",
            "Failed to load last config {path}. Opened a new empty config."
        )
        val FailedToUpdateAppState = desc(
            "status.appState.updateFailed",
            "Failed to update app state; {path} may be retried next launch."
        )
        val SavedConfig = desc(
            "status.config.saved",
            "Saved config with {count} transformer nodes to {path}"
        )
        val FailedToSaveConfig = desc("status.config.saveFailed", "Failed to save config {path}: {message}")
    }

    object Dialog {
        val Ok = desc("dialog.ok", "OK")
        val Confirm = desc("dialog.confirm", "Confirm")
        val Cancel = desc("dialog.cancel", "Cancel")
        val Save = desc("dialog.save", "Save")
        val Discard = desc("dialog.discard", "Discard")
        val ConfirmDiscardConfigChangesTitle = desc(
            "dialog.discardConfigChanges.title",
            "Confirm Discard Config Changes"
        )
        val ConfirmDiscardConfigChangesMessage = desc(
            "dialog.discardConfigChanges.message",
            "You have unsaved config changes. Do you want to save them before proceeding?"
        )
        val OpenConfigFailedTitle = desc("dialog.openConfigFailed.title", "Open Config Failed")
        val SaveConfigFailedTitle = desc("dialog.saveConfigFailed.title", "Save Config Failed")
    }

    object Toolbar {
        val File = desc("toolbar.file", "File")
        val Tool = desc("toolbar.tool", "Tool")
        val Help = desc("toolbar.help", "Help")
        val NewConfig = desc("toolbar.file.newConfig", "New Config")
        val OpenConfig = desc("toolbar.file.openConfig", "Open Config")
        val SaveConfig = desc("toolbar.file.saveConfig", "Save Config")
        val SaveConfigAs = desc("toolbar.file.saveConfigAs", "Save Config As")
        val Exit = desc("toolbar.file.exit", "Exit")
        val RunObfuscation = desc("toolbar.tool.runObfuscation", "Run Obfuscation")
        val HelpItem = desc("toolbar.help.help", "Help")
        val SubmitBugReport = desc("toolbar.help.submitBugReport", "Submit a Bug Report")
        val SubmitFeatureRequest = desc("toolbar.help.submitFeatureRequest", "Submit Feature Request")
        val CheckForUpdates = desc("toolbar.help.checkForUpdates", "Check for Updates")
        val About = desc("toolbar.help.about", "About")
        val Minimize = desc("toolbar.window.minimize", "Minimize")
        val Restore = desc("toolbar.window.restore", "Restore")
        val Maximize = desc("toolbar.window.maximize", "Maximize")
        val Close = desc("toolbar.window.close", "Close")
        val GeneralTab = desc("toolbar.tab.general", "General")
        val EditorTab = desc("toolbar.tab.editor", "Editor")
        val NativeTab = desc("toolbar.tab.native", "Native")
        val ObfuscationTab = desc("toolbar.tab.obfuscation", "Obfuscation")
        val SettingsTab = desc("toolbar.tab.settings", "Settings")
    }

    object Page {
        val GeneralTitle = desc("page.general.title", "General Configuration")
        val GeneralDescription = desc("page.general.description", "Top-level config options.")
        val NativeTitle = desc("page.native.title", "Native Configuration")
        val NativeDescription = desc("page.native.description", "Native pipeline config options.")
        val AppConfigTitle = desc("page.settings.appConfig.title", "App Configuration")
        val AppConfigDescription = desc(
            "page.settings.appConfig.description",
            "Configure Grunteon GUI behavior and appearance."
        )
        val PluginsTitle = desc("page.settings.plugins.title", "Plugins")
        val PluginsDescription = desc("page.settings.plugins.description", "Loaded {count} plugin(s).")
        val TransformerLibraryTitle = desc("page.editor.library.title", "Transformer Library")
        val TransformerLibraryDescription = desc(
            "page.editor.library.description",
            "Browse available transformers and add them to the pipeline stack."
        )
        val PipelineStackTitle = desc("page.editor.stack.title", "Pipeline Stack")
        val PipelineStackDescription = desc(
            "page.editor.stack.description",
            "Execution order is top to bottom. Duplicate transformers are allowed."
        )
        val InspectorTitle = desc("page.editor.inspector.title", "Inspector")
        val InspectorTitleWithTransformer = desc("page.editor.inspector.titleWithTransformer", "Inspector - {name}")
        val InspectorDescription = desc(
            "page.editor.inspector.description",
            "Select a transformer node to edit its Config."
        )
    }

    object ConfigEditor {
        val On = desc("configEditor.boolean.on", "On")
        val Off = desc("configEditor.boolean.off", "Off")
        val ClearValue = desc("configEditor.clearValue", "Clear value")
        val DuplicateEntry = desc("configEditor.list.duplicateEntry", "Duplicate Entry")
        val DeleteEntry = desc("configEditor.list.deleteEntry", "Delete Entry")
        val AddEntry = desc("configEditor.list.addEntry", "Add entry")
        val ClearList = desc("configEditor.list.clearList", "Clear list")
        val EmptyList = desc("configEditor.list.empty", "Empty List")
        val File = desc("configEditor.path.file", "File")
        val Directory = desc("configEditor.path.directory", "Dir")
        val Browse = desc("configEditor.path.browse", "Browse")
    }

    object Editor {
        val Search = desc("editor.library.search", "Search")
        val AddTransformer = desc("editor.library.addTransformer", "Add transformer")
        val DragToReorder = desc("editor.stack.dragToReorder", "Drag to reorder")
        val Duplicate = desc("editor.stack.duplicate", "Duplicate")
        val Delete = desc("editor.stack.delete", "Delete")
        val DeleteTransformerTitle = desc("editor.stack.deleteTransformer.title", "Delete Transformer")
        val DeleteTransformerMessage = desc(
            "editor.stack.deleteTransformer.message",
            "Are you sure you want to delete this transformer?"
        )
        val MappingApplierInserted = desc(
            "editor.stack.mappingApplierInserted",
            "Mapping applier inserted automatically after the last renamer."
        )
        val Reset = desc("editor.inspector.reset", "Reset")
        val ResetTransformerConfigTitle = desc(
            "editor.inspector.resetTransformerConfig.title",
            "Reset Transformer Config"
        )
        val ResetTransformerConfigMessage = desc(
            "editor.inspector.resetTransformerConfig.message",
            "Are you sure you want to reset the config of this transformer? This action cannot be undone."
        )
    }

    object Obfuscation {
        val EnabledTransformers = desc("obfuscation.enabledTransformers", "Enabled {count} transformers")
        val EnabledTransformersWithNative = desc(
            "obfuscation.enabledTransformersWithNative",
            "Enabled {count} transformers with native obfuscation"
        )
        val NoRunYet = desc("obfuscation.noRunYet", "No obfuscation run yet.")
        val Running = desc("obfuscation.running", "Running...")
        val Obfuscate = desc("obfuscation.obfuscate", "Obfuscate")
        val StartingLog = desc(
            "obfuscation.log.starting",
            "Starting obfuscation with {count} enabled transformer nodes"
        )
        val FinishedLog = desc("obfuscation.log.finished", "Obfuscation finished")
        val FailedLog = desc("obfuscation.log.failed", "Obfuscation failed: {message}")
    }

    object Plugins {
        val NoPluginsLoaded = desc("plugins.empty.title", "No plugins loaded")
        val OnlyBuiltInTransformers = desc("plugins.empty.description", "Only built-in transformers are available.")
        val PluginId = desc("plugins.metadata.id", "Plugin ID: {id}")
        val Version = desc("plugins.metadata.version", "Version: {version}")
        val Entry = desc("plugins.metadata.entry", "Entry: {entry}")
    }

    object FileDialog {
        val OpenExistingConfig = desc("fileDialog.openExistingConfig", "Open existed config")
        val NewConfig = desc("fileDialog.newConfig", "New config")
        val SaveConfigAs = desc("fileDialog.saveConfigAs", "Save config as")
        val SelectInputJar = desc("fileDialog.selectInputJar", "Select input jar")
        val SelectInputDirectory = desc("fileDialog.selectInputDirectory", "Select input directory")
        val SelectOutputJar = desc("fileDialog.selectOutputJar", "Select output jar")
    }

    object Welcome {
        val OpenExistingConfig = desc("welcome.openExistingConfig", "Open existed config")
        val NewConfig = desc("welcome.newConfig", "New config")
    }
}

fun uiText(descriptor: I18nDescriptor): String {
    return I18n.text(descriptor)
}

fun uiText(descriptor: I18nDescriptor, vararg replacements: Pair<String, Any?>): String {
    return replacements.fold(uiText(descriptor)) { text, (name, value) ->
        text.replace("{$name}", value?.toString().orEmpty())
    }
}
