package net.spartanb312.grunt.obfuscate.transformers

import net.spartanb312.grunt.obfuscate.Transformer
import net.spartanb312.grunt.obfuscate.resource.ResourceCache

object ControlFlowTransformer : Transformer("ControlFlowTransformer") {

    // Block Split
    // Replace Goto
    // TableSwitch
    // FakeJump
    override fun ResourceCache.transform() {

    }

}