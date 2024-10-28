package net.spartanb312.grunt.process.transformers.flow

import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache

/**
 * Using control flow expr to build constant
 * Coming in 2.5.0
 */
object ConstBuilderTransformer : Transformer("ConstBuilder", Category.Controlflow) {

    override fun ResourceCache.transform() {

    }

}