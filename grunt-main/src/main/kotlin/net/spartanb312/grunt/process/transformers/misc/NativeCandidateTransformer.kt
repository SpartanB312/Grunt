package net.spartanb312.grunt.process.transformers.misc

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.extensions.appendAnnotation
import net.spartanb312.grunt.utils.extensions.isAbstract
import net.spartanb312.grunt.utils.extensions.isAnnotation
import net.spartanb312.grunt.utils.extensions.isEnum
import net.spartanb312.grunt.utils.extensions.isInterface
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.matches
import net.spartanb312.grunt.utils.notInList
import org.apache.commons.lang3.StringEscapeUtils
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.*

/**
 * Append annotation for native obfuscate
 * Last update on 2024/10/25
 */
object NativeCandidateTransformer : Transformer("NativeCandidate", Category.Miscellaneous) {

    val annotation by setting("NativeAnnotation", "Lnet/spartanb312/example/Native;")
    private val enableSearch by setting("SearchCandidate", true)
    private val upCallLimit by setting("UpCallLimit", 0)
    private val exclusion by setting("Exclusion", listOf())

    private const val VALID_CLASS_NAME = "^(?:[^./\\[;]+/)*[^./\\[;]+\$"
    private const val VALID_METHOD_NAME = "^(?:[^./\\[;]+\\/)*(?:[^./\\[;])+\\.(?:[^./\\[;()\\/])+(?:\\(((\\[*L[^./\\[;]([^./\\[;]*[^.\\[;][^./\\[;])*;)|(\\[*[ZBCSIJFD]+))*\\))((\\[*L[^./\\[;]([^./\\[;]*[^.\\[;][^./\\[;])*;)|V|(\\[*[ZBCSIJFD]))$"
    private val annotationGroups by setting("AnnotationGroups", listOf(
        """{ "annotation": "Lnet/spartanb312/grunt/Native;", "includeRegexes": ["${StringEscapeUtils.escapeJava(VALID_CLASS_NAME)}"], "excludeRegexes": [] }""",
        """{ "annotation": "Lnet/spartanb312/grunt/VMProtect;", "includeRegexes": ["${StringEscapeUtils.escapeJava(VALID_METHOD_NAME)}"], "excludeRegexes": [] }"""
    ))

    val appendedMethods: MutableSet<MethodNode> = Collections.synchronizedSet(HashSet()) // from other place

    private data class AnnotationGroup(
        val annotation: String,
        val includeRegexes: List<String>,
        val excludeRegexes: List<String>
    )

    override fun ResourceCache.transform() {
        Logger.info(" - Adding annotations on native transformable methods...")
        val candidateMethod = mutableSetOf<MethodNode>()
        if (enableSearch) runBlocking {
            nonExcluded.asSequence()
                .filter {
                    !it.isInterface && !it.isAnnotation && !it.isEnum && !it.isAbstract
                            && it.name.notInList(exclusion)
                }.forEach { classNode ->
                    fun job() {
                        classNode.methods.forEach { methodNode ->
                            if (!appendedMethods.contains(methodNode)) {
                                var count = 0
                                for (insn in methodNode.instructions) {
                                    if (count > upCallLimit) break
                                    when (insn) {
                                        is FieldInsnNode -> count++
                                        is MethodInsnNode -> count++
                                        is InvokeDynamicInsnNode -> count++
                                    }
                                }
                                if (count <= upCallLimit) candidateMethod.add(methodNode)
                            }
                        }
                    }
                    if (Configs.Settings.parallel) launch(Dispatchers.Default) { job() } else job()
                }
        }
        candidateMethod.forEach { it.visitAnnotation(annotation, false) }

        val gson = Gson()
        annotationGroups.forEach { groupStr ->
            val group = gson.fromJson(groupStr, AnnotationGroup::class.java)
            val includeRegexes = group.includeRegexes.map { Regex(it) }
            val excludeRegexes = group.excludeRegexes.map { Regex(it) }
            nonExcluded.forEach { classNode ->
                if (classNode.matches(includeRegexes, excludeRegexes)) classNode.appendAnnotation(group.annotation)
                classNode.methods.forEach { methodNode ->
                    if (methodNode.matches(classNode.name, includeRegexes, excludeRegexes))
                    methodNode.appendAnnotation(group.annotation)
                }
            }
        }
        Logger.info("    Added ${candidateMethod.size + appendedMethods.size} annotations")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val str = """
{ "annotation": "Lnet/spartanb312/grunt/VMProtect;", "includeRegexes": ["${StringEscapeUtils.escapeJava(VALID_METHOD_NAME)}"], "excludeRegexes": [] }
    """
        val gson = Gson()
        val group = gson.fromJson(str, AnnotationGroup::class.java)
        println(group)
    }
}