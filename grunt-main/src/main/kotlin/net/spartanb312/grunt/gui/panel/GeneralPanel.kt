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
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileNameExtensionFilter

class GeneralPanel : JPanel() {

    private val libs = DefaultListModel<String>()
    private val exclusions = JTextArea("", 3, 50)
    private val mixinPackages = JTextArea("", 3, 50)

    private val dictionaryButton = JButton("customDictionary.txt")

    private val fileRemovePrefix = JTextArea("", 3, 50)
    private val fileRemoveSuffix = JTextArea("", 3, 50)

    private val corruptOutput = JCheckBox("")
    private val darkTheme = JCheckBox("")

    init {

        layout = MigLayout(
            LC().fillX().flowX(),
            AC().gap().grow().gap()
        )

        //Libraries
        fun chooseFile() {
            val chooser =
                JFileChooser(File(this::class.java.protectionDomain.codeSource.location.toURI().path))
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
        dictionaryButton.addActionListener {
            FileChooseUtils.chooseAnyFile(parent)?.let { dictionaryButton.text = it }
        }
        jList.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent) {
                if (libs.isEmpty) chooseFile()
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

        //Exclusions
        val exclusions = JPanel(MigLayout(LC().fill()))
        exclusions.border = TitledBorder("Exclusions").apply {
            titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
            titleColor = Color(0x4d89c9)
        }
        exclusions.add(this.exclusions, CC().grow())
        add(exclusions, CC().span().grow())


        //Mixin Package
        val mixinsPackage = JPanel(MigLayout(LC().fill()))
        mixinsPackage.border = TitledBorder("MixinPackage").apply {
            titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
            titleColor = Color(0x4d89c9)
        }
        mixinsPackage.add(this.mixinPackages, CC().grow())
        add(mixinsPackage, CC().span().grow())

        //Custom Dictionary
        val dictionary = JPanel(MigLayout(LC().fillX().flowX(), AC().gap().grow()))
        dictionary.border = TitledBorder("CustomDictionary").apply {
            titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
            titleColor = Color(0x4d89c9)
        }
        dictionary.add(JLabel("CustomDictionaryFile:"), CC().cell(0, 1))
        dictionary.add(dictionaryButton, CC().cell(1, 1).growX())
        add(dictionary, CC().span().grow())

        // Resource processing
        val resProcess = JPanel(MigLayout(LC().fillX().flowX(), AC().gap().grow()))
        resProcess.border = TitledBorder("ResourceProcessing").apply {
            titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
            titleColor = Color(0x4d89c9)
        }
        resProcess.add(JLabel("FileRemovePrefix:"), CC().cell(0, 0))
        resProcess.add(fileRemovePrefix, CC().cell(1, 0).growX())
        resProcess.add(JLabel("FileRemoveSuffix:"), CC().cell(0, 1))
        resProcess.add(fileRemoveSuffix, CC().cell(1, 1).growX())
        resProcess.add(JLabel("CorruptOutput:"), CC().cell(0, 2))
        resProcess.add(corruptOutput, CC().cell(1, 2))
        add(resProcess, CC().span().grow())

        // UI
        val uiPanel = JPanel(MigLayout(LC().fillX().flowX(), AC().gap().grow()))
        uiPanel.border = TitledBorder("UI Options:").apply {
            titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
            titleColor = Color(0x4d89c9)
        }
        uiPanel.add(JLabel("DarkTheme:"), CC().cell(0, 0))
        uiPanel.add(darkTheme, CC().cell(1, 0))
        add(uiPanel, CC().span().grow())
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

        dictionaryButton.text = Configs.Settings.customDictionary

        fileRemovePrefix.text = Configs.Settings.fileRemovePrefix.joinToString("\n")
        fileRemoveSuffix.text = Configs.Settings.fileRemoveSuffix.joinToString("\n")

        corruptOutput.isSelected = Configs.Settings.corruptOutput

        darkTheme.isSelected = Configs.UISetting.darkTheme
    }

    fun setSetting() {
        Configs.Settings.libraries = libs.elements().toList()
        Configs.Settings.exclusions = exclusions.text.split("\n").filter { it.isNotEmpty() }
        Configs.Settings.mixinPackages = mixinPackages.text.split("\n").filter { it.isNotEmpty() }

        Configs.Settings.customDictionary = dictionaryButton.text

        Configs.Settings.fileRemovePrefix = fileRemovePrefix.text.split("\n").filter { it.isNotEmpty() }
        Configs.Settings.fileRemoveSuffix = fileRemoveSuffix.text.split("\n").filter { it.isNotEmpty() }

        Configs.Settings.corruptOutput = corruptOutput.isSelected
        Configs.UISetting.darkTheme = darkTheme.isSelected
    }

}