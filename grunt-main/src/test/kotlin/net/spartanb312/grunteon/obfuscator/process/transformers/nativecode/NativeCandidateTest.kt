package net.spartanb312.grunteon.obfuscator.process.transformers.nativecode

import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.NATIVE_EXCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeCandidateTest {

    @Test
    fun defaultConfigMarksNothing() {
        val target = method("target", "()I")
        val clazz = clazz("test/Foo", target)

        val result = NativeCandidate().markCandidates(listOf(clazz), NativeCandidate.Config())

        assertEquals(0, result.marked.size)
        assertFalse(target.hasAnnotation(NATIVE_INCLUDED))
    }

    @Test
    fun marksMethodByFullDescriptorAndSkipsConstructorsByDefault() {
        val target = method("target", "(I)I")
        val other = method("other", "(I)I")
        val init = method("<init>", "()V", Opcodes.ACC_PUBLIC)
        val clazz = clazz("test/Foo", target, other, init)

        val result = NativeCandidate().markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                rules = listOf(
                    NativeCandidate.Rule(
                        name = "exact",
                        methodInclude = listOf("test/Foo.target(I)I")
                    )
                )
            )
        )

        assertEquals(listOf("test/Foo.target(I)I"), result.marked.map { it.displayName })
        assertEquals(NativeCandidate.MarkStatus.NewlyMarked, result.marked.single().status)
        assertTrue(target.hasAnnotation(NATIVE_INCLUDED))
        assertFalse(other.hasAnnotation(NATIVE_INCLUDED))
        assertFalse(init.hasAnnotation(NATIVE_INCLUDED))
    }

    @Test
    fun requiredClassAnnotationCanSelectMethodsAndExcludedMethodWins() {
        val kept = method("kept", "()V")
        val blocked = method("blocked", "()V").apply {
            appendAnnotation(NATIVE_EXCLUDED)
        }
        val clazz = clazz("test/Annotated", kept, blocked).apply {
            appendAnnotation("Luser/Native;")
        }

        val result = NativeCandidate().markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                annotation = "net.spartanb312.grunteon.annotation.native.NativeIncluded",
                rules = listOf(
                    NativeCandidate.Rule(
                        name = "annotation",
                        requiredAnnotationList = listOf("user.Native")
                    )
                )
            )
        )

        assertEquals(listOf("test/Annotated.kept()V"), result.marked.map { it.displayName })
        assertTrue(kept.hasAnnotation(NATIVE_INCLUDED))
        assertFalse(blocked.hasAnnotation(NATIVE_INCLUDED))
    }

    @Test
    fun descriptorRulesAndGeneratedFlagAreApplied() {
        val intMethod = method("intMethod", "(I)I")
        val stringMethod = method("stringMethod", "(Ljava/lang/String;)I")
        val generated = method("generated", "(I)I").apply {
            appendAnnotation(GENERATED_METHOD)
        }
        val clazz = clazz("test/Descriptors", intMethod, stringMethod, generated)

        val result = NativeCandidate().markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                rules = listOf(
                    NativeCandidate.Rule(
                        name = "int-desc",
                        methodInclude = listOf("test/Descriptors"),
                        descriptorInclude = listOf("(I)*"),
                        descriptorExclude = listOf("*String*"),
                        includeGenerated = false
                    )
                )
            )
        )

        assertEquals(listOf("test/Descriptors.intMethod(I)I"), result.marked.map { it.displayName })
        assertTrue(intMethod.hasAnnotation(NATIVE_INCLUDED))
        assertFalse(stringMethod.hasAnnotation(NATIVE_INCLUDED))
        assertFalse(generated.hasAnnotation(NATIVE_INCLUDED))
    }

    @Test
    fun inactiveRulesAreReportedAndDoNotMarkEverything() {
        val target = method("target", "()V")
        val clazz = clazz("test/Foo", target)

        val result = NativeCandidate().markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                rules = listOf(NativeCandidate.Rule(name = "empty"))
            )
        )

        assertEquals(listOf("empty"), result.inactiveRuleNames)
        assertEquals(0, result.marked.size)
        assertFalse(target.hasAnnotation(NATIVE_INCLUDED))
    }

    @Test
    fun alreadyMarkedMethodsAreNotDuplicatedButStillReported() {
        val target = method("target", "()V").apply {
            appendAnnotation(NATIVE_INCLUDED)
        }
        val clazz = clazz("test/Foo", target)

        val result = NativeCandidate().markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                rules = listOf(
                    NativeCandidate.Rule(
                        name = "class",
                        methodInclude = listOf("test/Foo")
                    )
                )
            )
        )

        assertEquals(listOf("test/Foo.target()V"), result.marked.map { it.displayName })
        assertEquals(NativeCandidate.MarkStatus.AlreadyMarked, result.marked.single().status)
        val annotationCount = target.invisibleAnnotations.count { it.desc == NATIVE_INCLUDED }
        assertEquals(1, annotationCount)
    }

    @Test
    fun interfaceMethodsRequireExplicitOptIn() {
        val target = method("target", "()V", Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT)
        val clazz = clazz("test/Api", target).apply {
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
        }
        val transformer = NativeCandidate()

        val defaultResult = transformer.markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                rules = listOf(
                    NativeCandidate.Rule(
                        name = "class",
                        methodInclude = listOf("test/Api"),
                        includeAbstract = true
                    )
                )
            )
        )
        assertEquals(0, defaultResult.marked.size)
        assertFalse(target.hasAnnotation(NATIVE_INCLUDED))

        val optInResult = transformer.markCandidates(
            listOf(clazz),
            NativeCandidate.Config(
                rules = listOf(
                    NativeCandidate.Rule(
                        name = "class",
                        methodInclude = listOf("test/Api"),
                        includeAbstract = true,
                        includeInterfaceMethods = true
                    )
                )
            )
        )
        assertEquals(listOf("test/Api.target()V"), optInResult.marked.map { it.displayName })
        assertTrue(target.hasAnnotation(NATIVE_INCLUDED))
    }

    private fun clazz(name: String, vararg methods: MethodNode): ClassNode {
        return ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            this.name = name
            superName = "java/lang/Object"
            this.methods.addAll(methods)
        }
    }

    private fun method(
        name: String,
        desc: String,
        access: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC
    ): MethodNode {
        return MethodNode(access, name, desc, null, null)
    }
}
