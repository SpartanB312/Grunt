package net.spartanb312.grunt.gui.panel

import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import net.spartanb312.grunt.config.*
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.Transformers
import java.awt.Color
import java.awt.Font
import javax.swing.*
import javax.swing.JSpinner.DefaultEditor
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class TransformerPanel : JTabbedPane() {

    init {
        this.tabPlacement = LEFT
        this.border = BorderFactory.createEmptyBorder(5, 5, 1, 5)
        refreshElement()
    }

    fun refreshElement() {
        //Remove All Tab
        while (this.tabCount > 0) {
            this.remove(0)
        }
        //Build Tab
        for (category in Transformer.Category.entries) {
            add(category.name, buildValuesPanel(category))
        }
    }

    private fun buildValuesPanel(category: Transformer.Category): JComponent {
        val panel = JPanel()
        panel.layout = MigLayout(
            LC().fillX().flowY(),
            AC().grow().fill(),
            AC().gap("15").gap()
        )
        val scrollPane = JScrollPane(panel)
        scrollPane.verticalScrollBar.unitIncrement = 16

        val transformers = Transformers.filter { t -> t.category == category }.toSet()

        val span = CC().span()
        for (transformer in transformers) {
            val settings = JPanel(MigLayout())
            settings.border = TitledBorder(transformer.name).apply {
                titleFont = titleFont.deriveFont(Font.BOLD).deriveFont(17f)
                titleColor = Color(0x4d89c9)
            }
            //Values
            var row = 0
            for (value in transformer.getValues()) {
                when (value) {
                    is BooleanValue -> settings.addBooleanValueComponent(value, row++)
                    is StringValue -> settings.addStringValueComponent(value, row++)
                    is IntValue -> settings.addIntValueComponent(value, row++)
                    is FloatValue -> settings.addFloatValueComponent(value, row++)
                    is ListValue -> settings.addListValueComponent(value, row++)
                }
            }
            panel.add(settings, span)
        }
        return scrollPane
    }

    private fun JPanel.addBooleanValueComponent(value: BooleanValue, row: Int) {
        val checkBox = JCheckBox(null, null, value.value)
        checkBox.horizontalTextPosition = LEFT
        checkBox.addActionListener {
            value.value = checkBox.isSelected
        }

        this.add(JLabel("${value.name}:"), CC().cell(0, row))
        this.add(checkBox, CC().cell(1, row))
    }


    private fun JPanel.addStringValueComponent(value: StringValue, row: Int) {
        val textBox = JTextField(value.value)
        textBox.document.addDocumentListener(object : DocumentListener {

            override fun insertUpdate(e: DocumentEvent) {
                setValue()
            }

            override fun removeUpdate(e: DocumentEvent) {
                setValue()
            }

            override fun changedUpdate(e: DocumentEvent) {
                setValue()
            }

            fun setValue() {
                value.value = textBox.text
            }
        })

        this.add(JLabel("${value.name}:"), CC().cell(0, row))
        this.add(textBox, CC().wrap().minWidth("300"))
    }

    private fun JPanel.addIntValueComponent(value: IntValue, row: Int) {
        val spinner = JSpinner(SpinnerNumberModel(value.value, 0, null, 1))
        (spinner.editor as DefaultEditor).textField.horizontalAlignment = CENTER
        spinner.addChangeListener {
            value.value = (spinner.value as Int)
        }

        this.add(JLabel("${value.name}:"), CC().cell(0, row))
        this.add(spinner, CC().cell(1, row).minWidth("150"))
    }

    private fun JPanel.addFloatValueComponent(value: FloatValue, row: Int) {
        val spinner = JSpinner(SpinnerNumberModel(value.value, 0.0f, null, 0.5f))
        (spinner.editor as DefaultEditor).textField.horizontalAlignment = CENTER
        spinner.addChangeListener {
            value.value = (spinner.value as Float)
        }

        this.add(JLabel("${value.name}:"), CC().cell(0, row))
        this.add(spinner, CC().cell(1, row).minWidth("150"))
    }

    private fun JPanel.addListValueComponent(listValue: ListValue, row: Int) {
        val textBox = JTextArea(listValue.value.joinToString("\n"), 3, 35)
        textBox.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                setValue()
            }

            override fun removeUpdate(e: DocumentEvent) {
                setValue()
            }

            override fun changedUpdate(e: DocumentEvent) {
                setValue()
            }

            fun setValue() {
                listValue.value = textBox.text.split('\n').filter { it.isNotEmpty() }
            }
        })

        this.add(JLabel("${listValue.name}:"), CC().cell(0, row))
        this.add(textBox, CC().cell(1, row).growX())
    }
}