package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerEntry
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.roundtrip.FlowIRRoundTrip
import net.spartanb312.grunteon.testcase.Asserts
import org.objectweb.asm.ClassReader
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertNotNull

class FlowIRRoundTripTransformerTest {
    @Test
    fun dumpsClassAfterFlowIrRoundTripTransformer() {
        val output = createTempFile("grunteon-flow-ir-roundtrip", ".jar")
        try {
            val instance = readTestClasses(
                Asserts::class.java,
                ObfConfig(
                    globalConfig = GlobalConfig(
                        output = output.pathString,
                        dumpMappings = false
                    ),
                    transformers = listOf(
                        TransformerEntry(
                            config = FlowIRRoundTrip.Config(
                                classFilter = ClassFilterConfig(
                                    includeStrategy = listOf("net/spartanb312/grunteon/testcase/Asserts"),
                                    excludeStrategy = emptyList()
                                )
                            )
                        )
                    )
                )
            )

            context(instance.workRes, instance) {
                instance.run()
            }

            ZipFile(output.toFile()).use { zip ->
                val entry = zip.getEntry("net/spartanb312/grunteon/testcase/Asserts.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                ClassReader(bytes)
            }
        } finally {
            output.deleteIfExists()
        }
    }
}
