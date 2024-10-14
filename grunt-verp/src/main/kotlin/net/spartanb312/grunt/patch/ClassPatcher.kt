package net.spartanb312.grunt.patch

import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.plugin.Plugin
import net.spartanb312.grunt.plugin.PluginManager
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.runProcess
import net.spartanb312.grunt.utils.logging.Logger
import java.io.File
import java.util.jar.JarFile

/**
 * Remote loading and verification services
 * Working in progress
 */
fun main(args: Array<String>) {
    PluginManager.addInternalPlugin(ClassPatcher)
    net.spartanb312.grunt.main(arrayOf("config.json"))
    readDirectory(File("mcLibs/"))
}

private fun readDirectory(directory: File) {
    directory.listFiles()?.forEach { file ->
        if (file.isDirectory) readDirectory(file)
        else readJar(JarFile(file)) {
            println("Obfuscating ${it.name}")
            Configs.Settings.input = it.name
            Configs.Settings.output = it.name
            runProcess()
        }
    }
}

private fun readJar(jar: JarFile, action: (JarFile) -> Unit) = action.invoke(jar)

const val NAME = "ClassPatcher"
const val VERSION = "1.0.0"

object ClassPatcher : Plugin(
    NAME,
    VERSION,
    "B_312",
    "ClassVersion patcher",
    "2.4.0"
) {

    override fun onInit() {
        Logger.info("Initializing $NAME $VERSION")
        Transformers.register(ClassPatchTransformer, 2001) // After const pool encrypt
    }

}