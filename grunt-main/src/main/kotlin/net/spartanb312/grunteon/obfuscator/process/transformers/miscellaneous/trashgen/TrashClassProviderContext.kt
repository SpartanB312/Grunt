package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.trashgen

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.TrashClassGenerator

data class TrashClassProviderContext(
    val instance: Grunteon,
    val transformerSeed: String,
    val config: TrashClassGenerator.Config,
    val providerOptions: List<String>,
    val providerOptionMap: Map<String, String>,
)
