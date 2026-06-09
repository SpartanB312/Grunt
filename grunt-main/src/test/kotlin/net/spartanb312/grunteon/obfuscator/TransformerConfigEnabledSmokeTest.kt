package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.DeadCodeRemove
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlatteningSSA
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.FlowIRRoundTrip
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.SSARoundTrip
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
    fun roundTripsSsaRoundTripConfig() {
        val path = createTempFile("grunteon-ssa-roundtrip-config", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(SSARoundTrip.Config())
                ),
                path
            )
            assertContains(path.readText(), "SSARoundTrip.Config")
            assertIs<SSARoundTrip.Config>(ObfConfig.read(path).transformerConfigs.single())
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsFlowIrRoundTripConfig() {
        val path = createTempFile("grunteon-flow-ir-roundtrip-config", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(FlowIRRoundTrip.Config())
                ),
                path
            )
            assertContains(path.readText(), "FlowIRRoundTrip.Config")
            assertIs<FlowIRRoundTrip.Config>(ObfConfig.read(path).transformerConfigs.single())
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
            assertContains(text, "\"includeMethodEntry\": true")
            assertContains(text, "\"includeExceptionBlocks\": true")
            assertContains(text, "\"maxDispatcherIslands\": 0")
            assertContains(text, "\"fakeCasesPerDispatcher\": 1")
            assertContains(text, "\"minStateOpsPerCase\": 2")
            assertContains(text, "\"maxStateOpsPerCase\": 5")
            assertContains(text, "\"stateKeyMode\": \"Mixed\"")
            assertContains(text, "\"stateKeyProcessorChance\": 0.5")
            assertContains(text, "\"keyProcessorMinMainSteps\": 1")
            assertContains(text, "\"keyProcessorMaxMainSteps\": 3")
            assertContains(text, "\"keyProcessorMinExtraSteps\": 0")
            assertContains(text, "\"keyProcessorMaxExtraSteps\": 1")
            assertContains(text, "\"keyProcessorMinChainSteps\": 1")
            assertContains(text, "\"keyProcessorMaxChainSteps\": 2")
            assertContains(text, "\"shuffleRegionBlocks\": false")
            assertIs<ControlflowFlattening.Config>(ObfConfig.read(path).transformerConfigs.single())
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsControlflowFlatteningSsaConfig() {
        val path = createTempFile("grunteon-controlflow-flattening-ssa-config", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(ControlflowFlatteningSSA.Config())
                ),
                path
            )
            val text = path.readText()
            assertContains(text, "ControlflowFlatteningSSA.Config")
            assertIs<ControlflowFlatteningSSA.Config>(ObfConfig.read(path).transformerConfigs.single())
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsControlflowJumpConfig() {
        val path = createTempFile("grunteon-controlflow-jump-config", ".json")
        try {
            ObfConfig.write(
                ObfConfig(
                    transformerConfigs = listOf(ControlflowJump.Config())
                ),
                path
            )
            val text = path.readText()
            assertContains(text, "ControlflowJump.Config")
            assertContains(text, "\"mangledIfChance\": 0.25")
            assertContains(text, "\"maxMangledIfsPerMethod\": 4")
            assertContains(text, "\"mangledFakeLoopChance\": 0.35")
            assertContains(text, "\"predicateProcessorMinMainSteps\": 1")
            assertContains(text, "\"predicateProcessorMaxMainSteps\": 2")
            assertContains(text, "\"predicateProcessorMinExtraSteps\": 0")
            assertContains(text, "\"predicateProcessorMaxExtraSteps\": 1")
            assertIs<ControlflowJump.Config>(ObfConfig.read(path).transformerConfigs.single())
        } finally {
            path.deleteIfExists()
        }
    }
}
