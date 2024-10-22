package net.spartanb312.grunt.patch

import net.spartanb312.grunt.plugin.Plugin
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.utils.logging.Logger

/**
 * Convert java 6 bytecodes to java 5
 * For kotlin on Windows 98
 */

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