package net.spartanb312.grunt.gui.panel

import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import net.spartanb312.grunt.config.Configs.Settings
import net.spartanb312.grunt.gui.GuiFrame
import net.spartanb312.grunt.gui.util.FileChooseUtils
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder

class BasicPanel : JPanel() {

    val input = JTextField()
    val output = JTextField()
    private val inputBrowse = JButton("Browse")
    private val outputBrowse = JButton("Browse")

    val useCMPTMaxIfMissing = JCheckBox("LibsMissingCheck")
    val useComputeMax = JCheckBox("ForceUseComputeMax")
    val corruptOutput = JCheckBox("CorruptOutput")
    val dumpMappings = JCheckBox("DumpMappings")

    val configPanel = JPanel()

    val obfButton = JButton("Obfuscate")

    val loadButton = JButton("Load Config")
    val saveButton = JButton("Save Config")
    val resetButton = JButton("Reset Values")
    val startSave = JCheckBox("Save when obfuscation starts", true)

    init {
        layout = MigLayout(
            LC().fillX().flowX(),
            AC().gap().grow().gap().gap()
        )

        refreshElement()

        obfButton.addActionListener {
            if (startSave.isSelected) {
                GuiFrame.saveConfig(GuiFrame.currentConfig)
            }
            GuiFrame.startObf()
        }
        inputBrowse.addActionListener {
            FileChooseUtils.chooseJarFile(parent)?.let { input.text = it }
        }
        outputBrowse.addActionListener {
            FileChooseUtils.chooseJarFile(parent)?.let { output.text = it }
        }
        loadButton.addActionListener {
            val file = FileChooseUtils.chooseJsonFile(parent) ?: return@addActionListener
            GuiFrame.loadConfig(file)
        }
        saveButton.addActionListener {
            val file =
                FileChooseUtils.choosePathSaveJsonFile(parent, File(GuiFrame.currentConfig)) ?: return@addActionListener
            GuiFrame.saveConfig(file.removeSuffix(".json").plus(".json"))
        }
        resetButton.addActionListener {
            GuiFrame.resetValue()
        }

        add(obfButton, CC().cell(3, 0, 1, 2).grow())
        add(useCMPTMaxIfMissing, CC().cell(3, 2).grow())
        add(useComputeMax, CC().cell(3, 3).grow())
        add(corruptOutput, CC().cell(3, 4).grow())
        add(dumpMappings, CC().cell(3, 5).grow())

        add(JLabel("Input:"), CC().cell(0, 0).growX())
        add(input, CC().cell(1, 0).growX())
        add(inputBrowse, CC().cell(2, 0).growX())
        add(JLabel("Output:"), CC().cell(0, 1).growX())
        add(output, CC().cell(1, 1).growX())
        add(outputBrowse, CC().cell(2, 1).growX())

        configPanel.border = TitledBorder("Current Config: ${GuiFrame.currentConfig}")
        configPanel.layout = MigLayout(LC().fillY(), AC(), AC())
        configPanel.add(loadButton, CC().growY())
        configPanel.add(saveButton, CC().growY())
        configPanel.add(resetButton, CC().growY())
        configPanel.add(startSave, CC().growY())
        add(configPanel, CC().cell(0, 2, 3, 3).grow())
    }

    fun updateConfigTitle(path: String) {
        configPanel.border = TitledBorder("Current Config: $path")
    }

    fun disableAll() {
        this.components.forEach { it.isEnabled = false }
        loadButton.isEnabled = false
        saveButton.isEnabled = false
    }

    fun enableAll() {
        this.components.forEach { it.isEnabled = true }
        loadButton.isEnabled = true
        saveButton.isEnabled = true
    }

    /**
     * Let Panel View Correct Value
     */
    fun refreshElement() {
        input.text = Settings.input
        output.text = Settings.output

        useComputeMax.isSelected = Settings.forceUseComputeMax
        useCMPTMaxIfMissing.isSelected = Settings.libsMissingCheck
        corruptOutput.isSelected = Settings.corruptOutput
        dumpMappings.isSelected = Settings.generateRemap
    }

    fun setSetting() {
        Settings.input = this.input.text
        Settings.output = this.output.text

        Settings.forceUseComputeMax = this.useComputeMax.isSelected
        Settings.libsMissingCheck = this.useCMPTMaxIfMissing.isSelected
        Settings.corruptOutput = this.corruptOutput.isSelected
        Settings.generateRemap = this.dumpMappings.isSelected
    }

}