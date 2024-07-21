package net.spartanb312.grunt.gui.util

import java.awt.Component
import java.io.File
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object FileChooseUtils {

    val CURRENT_PATH = Paths.get("").toAbsolutePath().toString() + File.separatorChar

    fun chooseJsonFile(parent: Component): String? {
        val chooser = JFileChooser(File(CURRENT_PATH))
        chooser.fileFilter = FileNameExtensionFilter("Json File", "json")
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath.removePrefix(CURRENT_PATH)
        } else null
    }

    fun choosePathSaveJsonFile(parent: Component, file: File): String? {
        val chooser = JFileChooser(file)
        chooser.fileFilter = FileNameExtensionFilter("Json File", "json")
        chooser.selectedFile = file
        return if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath.removePrefix(CURRENT_PATH)
        } else null
    }

    fun chooseJarFile(parent: Component): String? {
        val chooser = JFileChooser(File(CURRENT_PATH))
        chooser.fileFilter = FileNameExtensionFilter("Jar File", "jar")

        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath.removePrefix(CURRENT_PATH)
        } else null
    }

}