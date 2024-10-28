package net.spartanb312.grunt.gui.panel

import net.spartanb312.grunt.config.Configs
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext

class ConsolePanel : JPanel() {

    private val loggerPanel = JTextPane().apply { isEditable = false }
    private val printLayout: JPanel
    private val scrollPane: JScrollPane

    init {
        layout = BorderLayout()
        printLayout = JPanel(BorderLayout())
        printLayout.add(loggerPanel)
        scrollPane = JScrollPane(printLayout).apply {
            verticalScrollBar.unitIncrement = 16
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    private val scrollBar = scrollPane.verticalScrollBar

    fun info(str: String) {
        appendToPane(loggerPanel, "$str\n", if (Configs.UISetting.darkTheme) Color.lightGray else Color.BLACK)
    }

    fun err(str: String) {
        appendToPane(loggerPanel, "$str\n", Color.RED)
    }

    @Synchronized
    private fun appendToPane(text: JTextPane, msg: String, c: Color) {
        scrollBar.value = scrollBar.maximum
        text.document.insertString(
            text.document.length,
            msg,
            StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c)
        )
    }

}