package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.IndyChecker
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import org.objectweb.asm.Type

/**
 * Last update on 2026/03/31 by FluixCarvin
 * Interface methods overlap √
 * Bridge methods link √
 * Invokedynamic remap √
 * TODO: Reflection remap
 */
class MethodRenamer : Transformer<MethodRenamer.Config>(
    name = enText("process.rename.method_renamer", "MethodRenamer"),
    category = Category.Renaming,
    description = enText(
        "process.rename.method_renamer.desc",
        "Renaming methods"
    )
), MappingSource {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.Controlflow, "Renamer should run after controlflow category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
        after(ClassRenamer::class.java, "MethodRenamer should run after ClassRenamer")
    }

    class Config : TransformerConfig() {
        val mode by setting(
            name = enText("process.rename.method_renamer.mode", "Mode"),
            value = Mode.Full,
            desc = enText(
                "process.rename.method_renamer.mode.desc",
                "Interface method name overlap will also be obfuscated in full mode"
            )
        )
        val dictionary by setting(
            name = enText("process.rename.method_renamer.dictionary", "Dictionary"),
            value = NameGenerator.DictionaryType.Alphabet,
            desc = enText("process.rename.method_renamer.dictionary.desc", "Dictionary for renamer")
        )
        val enums by setting(
            name = enText("process.rename.method_renamer.enums", "Enums"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.enums.desc",
                "Obfuscate methods in enum classes"
            )
        )
        val interfaces by setting(
            name = enText("process.rename.method_renamer.interfaces", "Interfaces"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.interfaces.desc",
                "Obfuscate methods in interfaces"
            )
        )
        val heavyOverloads by setting(
            name = enText("process.rename.method_renamer.heavy_overloads", "Heavy overloads"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.heavy_overloads.desc",
                "Overload method names as much as possible"
            )
        )
        val aggressiveShadowNames by setting(
            name = enText("process.rename.method_renamer.aggressive_shadow_names", "Aggressive shadow names"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.aggressive_shadow_names.desc",
                "Shadow method names as much as possible"
            )
        )
        val solveBridge = true
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MethodRenamer[${config.mode.displayName}]: Generating method mappings...")
        }
        buildFull(config)
    }

    context(instance: Grunteon, _: PipelineBuilder)
    private fun buildFull(config: Config) {
        val methodHierarchy = globalScopeValue {
            Logger.info("    Building method hierarchies...")
            val classHierarchy = ClassHierarchy.build(
                instance.workRes.inputClassCollection, // Only include input classes
                instance.workRes::getClassNode
            )
            MethodHierarchy.build(classHierarchy)
        }
        val sourceAndOverridesMapping = globalScopeValue { Int2ObjectOpenHashMap<String>() }
        val bridgeMethodSources = globalScopeValue { ObjectOpenHashSet<MethodHierarchy.Entry>() }
        seq {
            val methodHierarchy = methodHierarchy.global
            val classHierarchy = methodHierarchy.classHierarchy
            val strategy = buildFilterStrategy(config)
            // ClassHierarchy.build() sorts inputClassNodes by name and places them at
            // classNodes[0..inputClassCount) before appending any looked-up library ancestors.
            // Iterating that slice directly gives a deterministic name-sorted order with no
            // secondary sort pass and no intermediate filtered-list allocation.
            val inputClassCount = instance.workRes.inputClassCollection.size
            val nonExcluded = IntArrayList(inputClassCount)
            // BooleanArray keyed by class index replaces ObjectOpenHashSet<String>:
            // owner.index lookup is a single array read vs. a string-hash probe on the hot path.
            val nonExcludedFlags = BooleanArray(classHierarchy.classCount)
            for (i in 0 until inputClassCount) {
                val cn = classHierarchy.classNodes[i]
                if (strategy.testClass(cn) && !cn.isAnnotation
                    && (config.enums || !cn.isEnum)
                    && (config.interfaces || !cn.isInterface)
                ) {
                    nonExcluded.add(i)
                    nonExcludedFlags[i] = true
                }
            }

            context(classHierarchy, methodHierarchy) {
                Logger.info("    Splitting method groups...")
                val blackList = IntOpenHashSet()
                // Pre-size: at most one group per source method avoids frequent ArrayList resizes.
                val relatedGroups =
                    ArrayList<Pair<IntLinkedOpenHashSet, ObjectLinkedOpenHashSet<String>>>(methodHierarchy.sourceMethods.size)
                val bridgeMethodSources = bridgeMethodSources.global
                val inputClassMap = instance.workRes.inputClassMap // cache to avoid repeated property lookup
                nonExcluded.forEach { classIndex ->
                    val classNode = classHierarchy.classNodes[classIndex]
                    val classEntry = ClassHierarchy.Entry(classIndex)
                    if (!classEntry.hasMissingDependency) {
                        val isEnum = classNode.isEnum
                        for (methodIndex in classEntry.methods.array) {
                            val methodEntry = MethodHierarchy.Entry(methodIndex)
                            // Source check
                            if (!methodEntry.isSourceMethod) continue
                            if (blackList.contains(methodEntry.index)) continue
                            // Info check
                            val methodNode = methodEntry.node
                            if (methodNode.isNative) continue
                            if (methodNode.isInitializer) continue
                            if (methodNode.isMainMethod) continue
                            if (isEnum && methodNode.name == "values") continue
                            if (methodNode.name in HARD_EXCLUDE) continue
                            // if (methodNode.reflectionExcluded) continue
                            // TODO: method exclusion
                            // val combined = combine(classNode.name, methodNode.name, methodNode.desc)
                            // Check bridge method
                            if (config.solveBridge) {
                                if (methodNode.isSynthetic || methodNode.isBridge) {
                                    bridgeMethodSources.add(methodEntry)
                                    continue
                                }
                            }

                            // Bind group
                            val related = methodEntry.connectedComponent
                            if (related.any { !inputClassMap.containsKey(it.owner.name) }) continue
                            if (related.size > 1) {
                                Logger.debug("    Found multi source method group (${related.size} classes): ")
                                related.forEach {
                                    Logger.trace("     - " + it.full)
                                }
                            }
                            // apply group – single pass avoids IntArrayList wrapper + intermediate List
                            val entries = IntLinkedOpenHashSet(related.size * 2 + 1)
                            for (i in 0 until related.size) {
                                val idx = related.array[i]
                                blackList.add(idx)
                                entries.add(idx)
                            }
                            relatedGroups.add(entries to ObjectLinkedOpenHashSet.of(methodEntry.desc))
                        }
                    }
                }

                // Bind synthetic bridge method group
                if (config.solveBridge) {
                    // Build a name-indexed map to avoid O(G) linear scan per bridge method
                    val groupsByName = HashMap<String, MutableList<Int>>(relatedGroups.size * 2)
                    relatedGroups.forEachIndexed { index, sources ->
                        groupsByName.getOrPut(MethodHierarchy.Entry(sources.first.first()).name) { mutableListOf() }
                            .add(index)
                    }

                    // Cache parsed arg-type arrays per group index: Type.getArgumentTypes() re-parses
                    // the descriptor string each call; a group targeted by N bridges only needs one parse.
                    val firstArgTypesCache = arrayOfNulls<Array<Type>>(relatedGroups.size)
                    val standaloneSyntheticSources = mutableSetOf<MethodHierarchy.Entry>()
                    bridgeMethodSources.forEach { bridge ->
                        // Pre-parse bridge arg types once, outside the group search loop
                        val bridgeArgTypes = Type.getArgumentTypes(bridge.desc)
                        var findCommon = false

                        // Only visit groups whose source method name matches the bridge name
                        val candidateIndices = groupsByName[bridge.name]
                        if (candidateIndices != null) {
                            treeSearch@ for (groupIndex in candidateIndices) {
                                val sources = relatedGroups[groupIndex]
                                val first = MethodHierarchy.Entry(sources.first.first())
                                // Try to link bridge method on override tree
                                val inSameOverrideTree =
                                    first.owner.name == bridge.owner.name || classHierarchy.isSubType(
                                        bridge.owner.name,
                                        first.owner.name
                                    )
                                if (!inSameOverrideTree) continue

                                // Cache parsed arg types per group; avoids re-parsing the same descriptor
                                // when multiple bridge methods all target the same source group.
                                val firstArgTypes = firstArgTypesCache[groupIndex]
                                    ?: Type.getArgumentTypes(first.desc).also { firstArgTypesCache[groupIndex] = it }
                                if (bridgeArgTypes.size != firstArgTypes.size) continue

                                // Check each parameter: fType must be same or a subtype of bType
                                var descTypeMatch = true
                                for (i in firstArgTypes.indices) {
                                    val bType = bridgeArgTypes[i]
                                    val fType = firstArgTypes[i]
                                    if (bType.descriptor == fType.descriptor) continue // exact match, fast path
                                    // Only object types can have a subtype relationship;
                                    // internalName strips the L…; wrapper required by ClassHierarchy
                                    if (bType.sort != Type.OBJECT || fType.sort != Type.OBJECT
                                        || !classHierarchy.isSubType(fType.internalName, bType.internalName)
                                    ) {
                                        descTypeMatch = false
                                        break // Early exit: no need to check remaining params
                                    }
                                }

                                // Here we link this bridge method on the tree
                                if (descTypeMatch) {
                                    findCommon = true
                                    //println("Bridge link: ${first.full} and ${bridge.full}")
                                    sources.first.add(bridge.index)
                                    sources.second.add(bridge.desc)
                                    break@treeSearch
                                }
                            }
                        }
                        if (!findCommon) {
                            //println("Can't find common tree for ${bridge.full}")
                            standaloneSyntheticSources.add(bridge)
                        }
                    }
                    // Consider each standalone synthetic method as a standalone group (Kotlin and Scala compiler gen)
                    standaloneSyntheticSources.forEach {
                        relatedGroups.add(IntLinkedOpenHashSet.of(it.index) to ObjectLinkedOpenHashSet.of(it.desc))
                    }
                }

                Logger.info("    Generating mappings for method groups...")
                val dictionary = NameGenerator.getDictionary(config.dictionary)
                val sourceAndOverridesMapping = sourceAndOverridesMapping.global
                // share a same name in a group
                val nameGenerators = Array(classHierarchy.classCount) { NameGenerator(dictionary) }
                val existedNameMap = Array(classHierarchy.classCount) { ObjectOpenHashSet<String>() }
                var methodMappingCount = 0

                relatedGroups.forEach outer@{ group ->
                    // Pre-size to avoid rehashing; each source contributes itself + its descendants
                    val checkSet = IntLinkedOpenHashSet(group.first.size * 4)
                    group.first.forEach { sourceIdx ->
                        val source = MethodHierarchy.Entry(sourceIdx)
                        checkSet.add(source.owner.index)
                        // Disable up check for static and private fields TODO: check this
                        if ((!source.node.isStatic && !source.node.isPrivate) || !config.aggressiveShadowNames) {
                            source.owner.descendants.forEach {
                                checkSet.add(it.index)
                            }
                        }
                    }
                    val checkList = ClassHierarchy.EntryArray(checkSet.toIntArray())
                    checkList.forEach { owner ->
                        if (!nonExcludedFlags[owner.index]) {
                            Logger.debug("${owner.classNode.name} is not included in working range. Discarded all group (${group.first.size} methods)")
                            checkList.forEach {
                                Logger.trace(" - ${it.name}")
                            }
                            return@outer
                        }
                    }
                    val first = MethodHierarchy.Entry(group.first.first())
                    // Pre-convert to array once per group: avoids set-iterator overhead and per-iteration
                    // List allocation from group.second.map { newName + it } inside the name-search loop.
                    val groupDescs = group.second.toTypedArray()
                    val dic = nameGenerators[first.owner.index]
                    var newName: String
                    // TODO: add prefix/suffix support here when needed
                    loop@ while (true) {
                        newName = dic.nextName(config.heavyOverloads, first.desc)
                        var keepThisName = true
                        run check@{
                            checkList.forEach { owner ->
                                val nameMap = existedNameMap[owner.index]
                                for (desc in groupDescs) {
                                    if (nameMap.contains(newName + desc)) {
                                        keepThisName = false
                                        return@check
                                    }
                                }
                            }
                        }
                        if (keepThisName) break
                    }
                    // Register ALL descs (source + bridge) to prevent name collisions on bridge descriptors.
                    // Previously only first.desc was registered, which allowed a later unrelated group to
                    // reuse the same name with a bridge descriptor → duplicate name+desc in the same scope.
                    checkList.forEach { owner ->
                        val nameMap = existedNameMap[owner.index]
                        for (desc in groupDescs) {
                            nameMap.add(newName + desc)
                        }
                    }
                    // Apply to all affected
                    methodMappingCount += group.first.size
                    group.first.forEach { sourceMethodIdx ->
                        val sourceMethod = MethodHierarchy.Entry(sourceMethodIdx)
                        sourceAndOverridesMapping[sourceMethod.index] = newName
                        instance.nameMapping.putMethodMapping(
                            sourceMethod.owner.name,
                            sourceMethod.name,
                            sourceMethod.desc,
                            newName
                        )
                        if (sourceMethod.access.isPrivate) return@forEach
                        // Disable up apply for private and static
                        if (!sourceMethod.node.isPrivate && !sourceMethod.node.isStatic) {
                            //println("Disable up apply for ${sourceMethod.full}")
                            sourceMethod.overrideMethods.forEach {
                                sourceAndOverridesMapping[it.index] = newName
                            }
                        }
                        sourceMethod.owner.descendants.forEach {
                            val descendantName = it.name
                            instance.nameMapping.putMethodMapping(
                                descendantName,
                                sourceMethod.name,
                                sourceMethod.desc,
                                newName
                            )
                            methodMappingCount++
                        }
                    }
                }
                Logger.info("    Generated mapping for $methodMappingCount methods")
            }
        }
        IndyChecker.check(methodHierarchy, sourceAndOverridesMapping)
    }


    enum class Mode(override val displayName: CharSequence) : DisplayEnum {
        Fast("Fast"),
        Full("Full"),
    }


    companion object {
        val HARD_EXCLUDE = setOf(
            // Java serialization methods
            "writeObject",
            "writeExternal",
            "writeReplace",
            "useProtocolVersion",
            "readObject",
            "readObjectNoData",
            "readExternal",
            "readResolve"
        )
    }
}