package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.DeadCodeRemove
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.IrRoundTrip
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertIs

class TransformerConfigEnabledSmokeTest {
    @Test
    fun roundTripsInheritedEnabledFlag() {
        val path = createTempFile("grunteon-enabled", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(DeadCodeRemove.Config().apply { enabled = false })
                ),
                path
            )
            assertContains(path.readText(), "\"enabled\": false")
            assertFalse(ObfConfig.read(path).transformerConfigs.single().enabled)
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsIrRoundTripConfig() {
        val path = createTempFile("grunteon-ir-roundtrip-config", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(IrRoundTrip.Config())
                ),
                path
            )
            assertContains(path.readText(), "IrRoundTrip.Config")
            assertIs<IrRoundTrip.Config>(ObfConfig.read(path).transformerConfigs.single())
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsControlflowFlatteningConfig() {
        val path = createTempFile("grunteon-controlflow-flattening-config", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(ControlflowFlattening.Config())
                ),
                path
            )
            val text = path.readText()
            assertContains(text, "ControlflowFlattening.Config")
            assertContains(text, "\"skipSyntheticBridgeMethods\": true")
            assertContains(text, "\"skipDefaultMethods\": true")
            assertIs<ControlflowFlattening.Config>(ObfConfig.read(path).transformerConfigs.single())
        } finally {
            path.deleteIfExists()
        }
    }
}
