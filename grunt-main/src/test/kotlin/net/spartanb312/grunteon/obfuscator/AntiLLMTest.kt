package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.AntiLLM
import net.spartanb312.grunteon.obfuscator.util.ANTI_LLM_JUNK_CALL
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import net.spartanb312.grunteon.testcase.ParameterMarkerDummy
import org.objectweb.asm.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AntiLLMTest {

    @Test
    fun publishesDefensePoolAndCarrierMethods() {
        val instance = readTestClasses(
            ParameterMarkerDummy::class.java,
            ObfConfig(
                output = null,
                transformerConfigs = listOf(
                    AntiLLM.Config(
                        poolSize = 8,
                        resourceCount = 1,
                        carrierClassCount = 1,
                        maxAnnotatedMembersPerClass = 2
                    )
                )
            )
        )

        context(instance.workRes, instance) {
            instance.execute()
        }

        val pool = instance.workRes.getStringPool(WorkResources.ANTI_LLM_STRING_POOL)
        assertEquals(8, pool.size)
        assertTrue(pool.any { "prompt injection" in it || "system prompt boundary" in it })
        assertTrue(instance.workRes.generatedResources.keys.any { it.startsWith("META-INF/grunteon/anti-llm/defense-") })

        val carrier = instance.workRes.inputClassCollection.firstOrNull {
            it.name.startsWith("net/spartanb312/grunteon/internal/llm/LLM")
        }
        assertNotNull(carrier)
        assertTrue(
            carrier.methods.any { method ->
                method.hasAnnotation(ANTI_LLM_JUNK_CALL) &&
                    Type.getArgumentTypes(method.desc).any { it.descriptor == "Ljava/lang/String;" }
            }
        )

        val target = instance.workRes.inputClassMap["net/spartanb312/grunteon/testcase/ParameterMarkerDummy"]
        assertNotNull(target)
        assertTrue(target.sourceDebug?.contains("ANTI_LLM_BEGIN") == true)
    }
}
