package net.spartanb312.grunt.gui

import com.github.weisj.darklaf.LafManager
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import net.spartanb312.grunt.SUBTITLE
import net.spartanb312.grunt.VERSION
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.gui.panel.BasicPanel
import net.spartanb312.grunt.gui.panel.GeneralPanel
import net.spartanb312.grunt.gui.panel.ConsolePanel
import net.spartanb312.grunt.gui.panel.TransformerPanel
import net.spartanb312.grunt.gui.util.LoggerPrinter
import net.spartanb312.grunt.runProcess
import net.spartanb312.grunt.utils.logging.SimpleLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.*
import javax.swing.JFrame.EXIT_ON_CLOSE
import javax.swing.border.EmptyBorder
import kotlin.concurrent.thread

object GuiFrame {

    val logger = SimpleLogger("Grunt-UI")

    private val mainFrame = JFrame("")
    private val frameSize = Dimension(755, 535)

    var currentConfig: String = "config.json"

    // Page
    val tabbedPanel = JTabbedPane()

    // Config
    val basicPanel = BasicPanel()
    val generalPanel = GeneralPanel()
    val transformerPanel = TransformerPanel()

    // Console
    var consolePanel = ConsolePanel()
    val captureInfo = LoggerPrinter(System.out) { consolePanel.info(it) }

    init {
        System.setOut(captureInfo)
    }

    init {
        val imageIcon = ImageIcon(GuiFrame::class.java.getResource("/logo.png"))
        mainFrame.iconImage = imageIcon.image.getScaledInstance(40, 40, Image.SCALE_DEFAULT)

        mainFrame.title = "Gruntpocalypse v$VERSION | $SUBTITLE"
        mainFrame.defaultCloseOperation = EXIT_ON_CLOSE
        mainFrame.size = frameSize
        mainFrame.layout = MigLayout(
            LC().fill().flowY(),
            AC().fill(),
            AC().grow().fill().gap()
        )

        tabbedPanel.addTab("General", JScrollPane(generalPanel).apply { verticalScrollBar.unitIncrement = 16 })
        tabbedPanel.addTab("Obfuscation", transformerPanel)
        tabbedPanel.addTab("Console", consolePanel)

        mainFrame.add(tabbedPanel, CC().cell(0, 0))
        mainFrame.add(basicPanel, CC().cell(0, 1))

        //Swing Theme
        LafManager.install()
    }

    fun startObf() {
        setValues()
        thread(
            name = "process",
            priority = Thread.MAX_PRIORITY
        ) {
            disableAll()
            tabbedPanel.selectedIndex = 2
            try {
                runProcess()
            } catch (e: Exception) {
                e.printStackTrace()

                val stacktrace = StringWriter().also {
                    e.printStackTrace(PrintWriter(it))
                }.toString()

                // Send To Gui Logger
                consolePanel.err(stacktrace)

                val panel = JPanel().apply {
                    border = EmptyBorder(5, 5, 5, 5)
                    layout = BorderLayout(0, 0)
                }

                panel.add(JScrollPane(JTextArea(stacktrace).also { it.isEditable = false }))

                JOptionPane.showMessageDialog(
                    this.mainFrame,
                    panel,
                    "ERROR encountered at " + e.stackTrace[0].className,
                    JOptionPane.ERROR_MESSAGE
                )
            }
            enableAll()
        }
    }

    /**
     * Disable All Value Change
     */
    fun disableAll() {
        basicPanel.disableAll()
        tabbedPanel.setEnabledAt(0, false)
        tabbedPanel.setEnabledAt(1, false)
    }

    /**
     * Enable All Value Change
     */
    fun enableAll() {
        basicPanel.enableAll()
        tabbedPanel.setEnabledAt(0, true)
        tabbedPanel.setEnabledAt(1, true)
    }


    fun loadConfig(path: String) {
        currentConfig = path
        logger.info("Loading config $currentConfig")
        Configs.loadConfig(currentConfig)
        refreshElement()
    }

    fun saveConfig(path: String) {
        currentConfig = path

        logger.info("Saving config $currentConfig")
        setValues()
        Configs.saveConfig(currentConfig)
    }

    fun resetValue() {
        logger.info("Reset all settings")
        Configs.resetConfig()
        refreshElement()
    }

    fun refreshElement() {
        basicPanel.updateConfigTitle(currentConfig)
        basicPanel.refreshElement()
        generalPanel.refreshElement()
        transformerPanel.refreshElement()
    }

    fun setValues() {
        basicPanel.setSetting()
        generalPanel.setSetting()
    }

    fun view() {
        mainFrame.isVisible = true
    }

}