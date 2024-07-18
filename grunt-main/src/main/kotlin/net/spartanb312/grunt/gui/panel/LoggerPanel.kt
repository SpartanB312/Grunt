package net.spartanb312.grunt.gui.panel

import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext


class LoggerPanel: JPanel() {
    private val loggerPanel = JTextPane().apply {
        isEditable = false
    }

    init {
        layout = BorderLayout()
        val fillPanel = JPanel(BorderLayout())
        fillPanel.add(loggerPanel)
        add(JScrollPane(fillPanel).apply {
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)
    }

    fun info(str: String) {
        appendToPane(loggerPanel, "$str\n", Color.BLACK)
    }

    fun err(str: String) {
        appendToPane(loggerPanel, "$str\n", Color.RED)
    }

    private fun appendToPane(text: JTextPane, msg: String, c: Color) {
        text.document.insertString(text.document.length, msg, StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c))
    }
}