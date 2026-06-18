package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.DeadCodeRemove
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.RuntimeMaterial
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowFlattening
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.exp.ControlflowFlatteningSSA
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.roundtrip.FlowIRRoundTrip
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.roundtrip.SSARoundTrip
import net.spartanb312.grunteon.obfuscator.process.transformers.nativecode.NativeCandidate
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReferenceObfuscate
import java.nio.file.Path
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
                configOf(DeadCodeRemove.Config(), enabled = false),
                path
            )
            assertContains(path.readText(), "\"enabled\": false")
            assertFalse(ObfConfig.read(path).transformers.single().enabled)
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsSsaRoundTripConfig() {
        val path = createTempFile("grunteon-ssa-roundtrip-config", ".json")
        try {
            ObfConfig.write(
                configOf(SSARoundTrip.Config()),
                path
            )
            assertContains(path.readText(), "SSARoundTrip.Config")
            assertIs<SSARoundTrip.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsFlowIrRoundTripConfig() {
        val path = createTempFile("grunteon-flow-ir-roundtrip-config", ".json")
        try {
            ObfConfig.write(
                configOf(FlowIRRoundTrip.Config()),
                path
            )
            assertContains(path.readText(), "FlowIRRoundTrip.Config")
            assertIs<FlowIRRoundTrip.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsControlflowFlatteningConfig() {
        val path = createTempFile("grunteon-controlflow-flattening-config", ".json")
        try {
            ObfConfig.write(
                configOf(ControlflowFlattening.Config()),
                path
            )
            val text = path.readText()
            assertContains(text, "ControlflowFlattening.Config")
            assertContains(text, "\"includeMethodEntry\": true")
            assertContains(text, "\"includeExceptionBlocks\": true")
            assertContains(text, "\"maxDispatcherIslands\": 0")
            assertContains(text, "\"fakeCasesPerDispatcher\": 1")
            assertContains(text, "\"sharedFakeCaseTerminatorChance\": \"0.65\"")
            assertContains(text, "\"minStateOpsPerCase\": 2")
            assertContains(text, "\"maxStateOpsPerCase\": 5")
            assertContains(text, "\"stateKeyMode\": \"Mixed\"")
            assertContains(text, "\"stateKeyProcessorChance\": \"0.5\"")
            assertContains(text, "\"keyProcessorComplexity\": \"Light\"")
            assertContains(text, "\"keyProcessorMinMainSteps\": 1")
            assertContains(text, "\"keyProcessorMaxMainSteps\": 3")
            assertContains(text, "\"keyProcessorMinExtraSteps\": 0")
            assertContains(text, "\"keyProcessorMaxExtraSteps\": 1")
            assertContains(text, "\"keyProcessorMinChainSteps\": 0")
            assertContains(text, "\"keyProcessorMaxChainSteps\": 0")
            assertContains(text, "\"keyProcessorNativeCandidate\": false")
            assertContains(text, "\"keyProcessorNativeCandidateRatio\": \"0.1\"")
            assertContains(text, "\"shuffleRegionBlocks\": false")
            assertContains(text, "\"dispatcherTrailingRealBlock\": false")
            assertContains(text, "\"dispatcherTrailingRealBlockChance\": \"1.0\"")
            assertIs<ControlflowFlattening.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsControlflowFlatteningSsaConfig() {
        val path = createTempFile("grunteon-controlflow-flattening-ssa-config", ".json")
        try {
            ObfConfig.write(
                configOf(ControlflowFlatteningSSA.Config()),
                path
            )
            val text = path.readText()
            assertContains(text, "ControlflowFlatteningSSA.Config")
            assertIs<ControlflowFlatteningSSA.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsControlflowJumpConfig() {
        val path = createTempFile("grunteon-controlflow-jump-config", ".json")
        try {
            ObfConfig.write(
                configOf(ControlflowJump.Config()),
                path
            )
            val text = path.readText()
            assertContains(text, "ControlflowJump.Config")
            assertContains(text, "\"mangledIfChance\": \"0.25\"")
            assertContains(text, "\"maxMangledIfsPerMethod\": 4")
            assertContains(text, "\"mangledFakeLoopChance\": \"0.35\"")
            assertContains(text, "\"sharedJunkExitChance\": \"0.65\"")
            assertContains(text, "\"junkTerminalThrowChance\": \"0.2\"")
            assertContains(text, "\"dispatcherLandingJunkChance\": \"0.0\"")
            assertContains(text, "\"maxDispatcherLandingJunkBlocksPerMethod\": 4")
            assertContains(text, "\"exceptionBridgeChance\": \"0.0\"")
            assertContains(text, "\"maxExceptionBridgesPerMethod\": 4")
            assertContains(text, "\"predicateProcessorMinMainSteps\": 1")
            assertContains(text, "\"predicateProcessorMaxMainSteps\": 2")
            assertContains(text, "\"predicateProcessorMinExtraSteps\": 0")
            assertContains(text, "\"predicateProcessorMaxExtraSteps\": 1")
            assertContains(text, "\"predicateRandomBoundChance\": \"0.15\"")
            assertContains(text, "\"randomBoundPredicateMinMainSteps\": 1")
            assertContains(text, "\"randomBoundPredicateMaxMainSteps\": 1")
            assertContains(text, "\"randomBoundPredicateMinExtraSteps\": 0")
            assertContains(text, "\"randomBoundPredicateMaxExtraSteps\": 0")
            assertContains(text, "\"randomBoundPredicateMinChainSteps\": 1")
            assertContains(text, "\"randomBoundPredicateMaxChainSteps\": 1")
            assertContains(text, "\"predicateProcessorNativeCandidate\": false")
            assertContains(text, "\"predicateProcessorNativeCandidateRatio\": \"0.1\"")
            assertIs<ControlflowJump.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsRuntimeMaterialConfig() {
        val path = createTempFile("grunteon-runtime-material-config", ".json")
        try {
            ObfConfig.write(
                configOf(RuntimeMaterial.Config()),
                path
            )
            val text = path.readText()
            assertContains(text, "RuntimeMaterial.Config")
            assertContains(text, "\"draftMaterialMetadata\": true")
            assertContains(text, "\"detectTokens\"")
            assertContains(text, "\"jdwp\": true")
            assertContains(text, "\"noVerify\": true")
            assertContains(text, "\"javaAgent\": false")
            assertContains(text, "\"jmxRemote\": false")
            assertIs<RuntimeMaterial.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsReferenceObfuscateNativeHelperConfig() {
        val path = createTempFile("grunteon-reference-obfuscate-config", ".json")
        try {
            ObfConfig.write(
                configOf(ReferenceObfuscate.Config()),
                path
            )
            val text = path.readText()
            assertContains(text, "ReferenceObfuscate.Config")
            assertContains(text, "\"massiveString\": true")
            assertContains(text, "\"generatedHelperNativeCandidate\": false")
            assertContains(text, "\"generatedHelperNativeCandidateRatio\": \"0.1\"")
            assertIs<ReferenceObfuscate.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    @Test
    fun roundTripsNativeCandidateConfig() {
        val path = createTempFile("grunteon-native-candidate-config", ".json")
        try {
            ObfConfig.write(
                configOf(
                    NativeCandidate.Config(
                        rules = listOf(
                            NativeCandidate.Rule(
                                name = "helpers",
                                methodInclude = listOf("helper/**"),
                                descriptorInclude = listOf("(I)*"),
                                requiredAnnotationList = listOf("user.Native")
                            )
                        )
                    )
                ),
                path
            )
            val text = path.readText()
            assertContains(text, "NativeCandidate.Config")
            assertContains(text, "\"rules\"")
            assertContains(text, "\"helpers\"")
            assertContains(text, "\"methodInclude\"")
            assertContains(text, "\"descriptorInclude\"")
            assertContains(text, "\"requiredAnnotationList\"")
            assertIs<NativeCandidate.Config>(readSingleConfig(path))
        } finally {
            path.deleteIfExists()
        }
    }

    private fun configOf(config: TransformerConfig, enabled: Boolean = true): ObfConfig {
        return ObfConfig(
            transformers = listOf(
                TransformerEntry(
                    name = config::class.simpleName ?: "",
                    enabled = enabled,
                    config = config
                )
            )
        )
    }

    private fun readSingleConfig(path: Path): TransformerConfig {
        return ObfConfig.read(path).transformers.single().config
    }
}
