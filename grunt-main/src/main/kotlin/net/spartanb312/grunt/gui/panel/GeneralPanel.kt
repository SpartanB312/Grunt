package net.spartanb312.grunt.gui.panel

import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.gui.util.FileChooseUtils
import java.awt.Color
import java.awt.Font
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.File
import javax.swing.*
import javax.swing.JSpinner.DefaultEditor
import javax.swing.JTabbedPane.CENTER
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileNameExtensionFilter


class GeneralPanel : JPanel() {
    private val libs = DefaultListModel<String>()
    private val exclusions = JTextArea("", 3, 50)
    private val mixinPackages = JTextArea("", 3, 50)

    private val dictionaryStartIndex = JSpinner(SpinnerNumberModel(0, 0, null, 1))
    private val customDictionary = JTextArea("", 3, 50)

    private val fileRemovePrefix = JTextArea("", 3, 50)
    private val fileRemoveSuffix = JTextArea("", 3, 50)

    init {

        layout = MigLayout(
            LC().fillX().flowX(),
            AC().gap().grow().gap()
        )

        //Libraries
        run {
            fun chooseFile() {
                val chooser = JFileChooser(File(this::class.java.getProtectionDomain().codeSource.location.toURI().getPath()))
                chooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                chooser.isMultiSelectionEnabled = true
                chooser.fileFilter = FileNameExtensionFilter("Jar File", "jar")
                if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                    for (file in chooser.selectedFiles) {
                        libs.addElement(file.absolutePath.removePrefix(FileChooseUtils.CURRENT_PATH))
                    }
                }
            }

            val jList = JList(libs)
            val addButton = JButton("Add")
            val removeButton = JButton("Remove")
            addButton.addActionListener { chooseFile() }
            removeButton.addActionListener {
                for (index in jList.selectedIndices.reversedArray()) {
                    libs.remove(index)
                }
            }
            jList.addMouseListener(object : MouseListener {
                override fun mouseClicked(e: MouseEvent) {
                    if (libs.isEmpty) {
                        chooseFile()
                    }
//                    else if (e.clickCount == 2) {
//                        libs.remove(jList.selectedIndex)
//                    }
                }

                override fun mousePressed(e: MouseEvent?) {}
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseExited(e: MouseEvent?) {}
            })

            val libraries = JPanel(MigLayout(LC().fillX().flowX(), AC().grow().gap()))
            libraries.border = TitledBorder("Libraries").apply {
                titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
                titleColor = Color(0x4d89c9)
            }
            libraries.add(jList, CC().cell(0, 0, 1, 2).minWidth("150").minHeight("70").growX())
            libraries.add(addButton, CC().cell(1, 0).growX())
            libraries.add(removeButton, CC().cell(1, 1).growX().alignY("top"))
            add(libraries, CC().span().grow())
        }

        //Exclusions
        run {
            val exclusions = JPanel(MigLayout(LC().fill()))
            exclusions.border = TitledBorder("Exclusions").apply {
                titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
                titleColor = Color(0x4d89c9)
            }
            exclusions.add(this.exclusions, CC().grow())
            add(exclusions, CC().span().grow())
        }

        //Mixin Package
        run {
            val mixinsPackage = JPanel(MigLayout(LC().fill()))
            mixinsPackage.border = TitledBorder("MixinPackage").apply {
                titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
                titleColor = Color(0x4d89c9)
            }
            mixinsPackage.add(this.mixinPackages, CC().grow())
            add(mixinsPackage, CC().span().grow())
        }

        //Custom Dictionary
        run {
            val dictionary = JPanel(MigLayout(LC().fillX().flowX(), AC().gap().grow()))
            dictionary.border = TitledBorder("CustomDictionary").apply {
                titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
                titleColor = Color(0x4d89c9)
            }
            (dictionaryStartIndex.editor as DefaultEditor).textField.horizontalAlignment = CENTER
            dictionary.add(JLabel("DictionaryStartIndex:"), CC().cell(0, 0))
            dictionary.add(dictionaryStartIndex, CC().wrap().minWidth("150"))
            dictionary.add(JLabel("CustomDictionary:"), CC().cell(0, 1))
            dictionary.add(customDictionary, CC().cell(1, 1).growX())
            add(dictionary, CC().span().grow())
        }

        //File Remove
        run {
            val fileRemove = JPanel(MigLayout(LC().fillX().flowX(), AC().gap().grow()))
            fileRemove.border = TitledBorder("FileRemove").apply {
                titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
                titleColor = Color(0x4d89c9)
            }
            fileRemove.add(JLabel("FileRemovePrefix:"), CC().cell(0, 0))
            fileRemove.add(fileRemovePrefix, CC().cell(1, 0).growX())
            fileRemove.add(JLabel("FileRemoveSuffix:"), CC().cell(0, 1))
            fileRemove.add(fileRemoveSuffix, CC().cell(1, 1).growX())
            add(fileRemove, CC().span().grow())
        }
    }

    /**
     * Let Panel View Correct Value
     */
    fun refreshElement() {
        libs.clear()
        for (library in Configs.Settings.libraries) {
            libs.addElement(library)
        }
        exclusions.text = Configs.Settings.exclusions.joinToString("\n")
        mixinPackages.text = Configs.Settings.mixinPackages.joinToString("\n")

        dictionaryStartIndex.value = Configs.Settings.dictionaryStartIndex
        customDictionary.text = Configs.Settings.customDictionary.joinToString("\n")

        fileRemovePrefix.text = Configs.Settings.fileRemovePrefix.joinToString("\n")
        fileRemoveSuffix.text = Configs.Settings.fileRemoveSuffix.joinToString("\n")
    }

    fun setSetting() {
        Configs.Settings.libraries = libs.elements().toList()
        Configs.Settings.exclusions = exclusions.text.split("\n").filter { it.isNotEmpty() }
        Configs.Settings.mixinPackages = mixinPackages.text.split("\n").filter { it.isNotEmpty() }

        Configs.Settings.dictionaryStartIndex = dictionaryStartIndex.value as Int
        Configs.Settings.customDictionary = customDictionary.text.split("\n").filter { it.isNotEmpty() }

        Configs.Settings.fileRemovePrefix = fileRemovePrefix.text.split("\n").filter { it.isNotEmpty() }
        Configs.Settings.fileRemoveSuffix = fileRemoveSuffix.text.split("\n").filter { it.isNotEmpty() }
    }
}