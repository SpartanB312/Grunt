package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingSource
import net.spartanb312.grunteon.obfuscator.util.IndyChecker
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import org.objectweb.asm.Type

/**
 * Last update on 2026/03/31 by FluixCarvin
 * Interface methods overlap
 * Bridge methods link
 * Invokedynamic remap
 * TODO: Reflection remap
 */
@Transformer.Stability(StableLevel.Stable)
@Transformer.Description(
    "process.rename.method_renamer.desc",
    "Renaming methods"
)
class MethodRenamer : Transformer<MethodRenamer.Config>(
    "MethodRenamer",
    Category.Renaming,
), MappingSource {

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
        after(ControlflowJump::class.java, "Renamer should run after ControlflowJump")
        after(ClassRenamer::class.java, "MethodRenamer should run after ClassRenamer")
        before(FakeSyntheticBridge::class.java, "MethodRenamer should run before FakeSyntheticBridge")
    }

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Dictionary for renamer")
        @SettingName("Dictionary")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc("Obfuscate methods in enum classes")
        @SettingName("Enums")
        val enums: Boolean = true,
        @SettingDesc("Obfuscate methods in interfaces")
        @SettingName("Interfaces")
        val interfaces: Boolean = true,
        @SettingDesc("Overload method names as much as possible")
        @SettingName("Heavy overloads")
        val heavyOverloads: Boolean = true,
        @SettingDesc("Shadow method names as much as possible")
        @SettingName("Aggressive shadow names")
        val aggressiveShadowNames: Boolean = true,
        @SettingName("Solve bridge")
        val solveBridge: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MethodRenamer: Generating method mappings...")
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
            val strategy = config.classFilter.buildFilterStrategy()
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
                            if (blackList.contains(methodEntry.index)) continue

                            // Bind group
                            val related = methodEntry.connectedComponent
                            if (related.any { !inputClassMap.containsKey(it.owner.name) }) continue
                            if (related.size > 1) {
                                Logger.debug("    Found multi source method group (${related.size} classes): ")
                                related.forEach {
                                    Logger.trace("     - " + it.full)
                                }
                            }
                            // apply group �?single pass avoids IntArrayList wrapper + intermediate List
                            val relatedWrapped = IntArrayList.wrap(related.array)
                            val entries = IntLinkedOpenHashSet(relatedWrapped)
                            blackList.addAll(relatedWrapped)
                            relatedGroups.add(entries to ObjectLinkedOpenHashSet.of(methodEntry.desc))
                        }
                    }
                }

                // Bind synthetic bridge method group
                if (config.solveBridge) {
                    // Build a name-indexed map to avoid O(G) linear scan per bridge method
                    val groupsByName = Int2ObjectOpenHashMap<IntArrayList>(relatedGroups.size * 2)
                    relatedGroups.forEachIndexed { index, sources ->
                        groupsByName.getOrPut(sources.first.firstInt()) { IntArrayList() }
                            .add(index)
                    }
                    val standaloneSyntheticSources = IntLinkedOpenHashSet()
                    bridgeMethodSources.forEach { bridge ->
                        //if (
                        //    !bridge.name.contains("access") &&
                        //    !bridge.name.contains("lambda") &&
                        //    !bridge.name.contains("deserializeLambda")
                        //){
                        //    println("Notice bridge method ${bridge.full}")
                        //}
                        // Pre-parse bridge arg types once, outside the group search loop
                        val bridgeTypes = Type.getArgumentTypes(bridge.desc) + Type.getReturnType(bridge.desc)
                        var findCommon = false

                        // Only visit groups whose source method name matches the bridge name
                        val candidateIndices = groupsByName.get(bridge.index)
                        if (candidateIndices != null) {
                            treeSearch@ for (i in candidateIndices.indices) {
                                val groupIndex = candidateIndices.getInt(i)
                                val sources = relatedGroups[groupIndex]
                                val first = MethodHierarchy.Entry(sources.first.firstInt())
                                // Try to link bridge method on override tree
                                //val inSameOverrideTree = sources.first.any { sourceIdx ->
                                //    val srcOwnerName = MethodHierarchy.Entry(sourceIdx).owner.name
                                //    bridge.owner.name == srcOwnerName ||
                                //            classHierarchy.isSubType(bridge.owner.name, srcOwnerName)
                                //}
                                val inSameOverrideTree =
                                    first.owner.name == bridge.owner.name || classHierarchy.isSubType(
                                        bridge.owner.index,
                                        first.owner.index
                                    )
                                if (!inSameOverrideTree) continue
                                // Must have the same name
                                if (first.name == bridge.name) {
                                    var descTypeMatch = true
                                    val firstTypes = Type.getArgumentTypes(first.desc) + Type.getReturnType(first.desc)
                                    if (bridgeTypes.size == firstTypes.size) {
                                        // Check bridge method types, must same type or subtype
                                        descTypeCheck@ for (i in firstTypes.indices) {
                                            val bType = bridgeTypes[i]
                                            val fType = firstTypes[i]
                                            if (bType.descriptor != fType.descriptor) continue@descTypeCheck // exact match, fast path
                                            val isSubType = classHierarchy.isSubType(fType.descriptor, bType.descriptor)
                                            if (!isSubType) {
                                                descTypeMatch = false
                                                break@descTypeCheck // Early exit: no need to check remaining params
                                            }
                                        }
                                    } else descTypeMatch = false

                                    // Here we link this bridge method on the tree
                                    if (descTypeMatch) {
                                        findCommon = true
//                                        println("Bridge link: ${first.full} and ${bridge.full}")
//                                        println("BT: ${bridgeTypes.joinToString(", ")}")
//                                        println("FT: ${firstTypes.joinToString(", ")}")
                                        sources.first.add(bridge.index)
                                        sources.second.add(bridge.desc)
                                        break@treeSearch
                                    }
                                }
                            }
                        }
                        if (!findCommon) {
                            standaloneSyntheticSources.add(bridge.index)
                        }
                    }
                    // Consider standalone synthetic methods - group those whose owners are in an
                    // ancestor-descendant relationship (same name+desc) so they all get the same
                    // name and don't produce duplicate-mapping conflicts.
                    val standaloneBySignature = Int2ObjectOpenHashMap<IntArrayList>()
                    standaloneSyntheticSources.forEach { bridge ->
                        standaloneBySignature.computeIfAbsent(MethodHierarchy.Entry(bridge).methodCode) { IntArrayList() }
                            .add(bridge)
                    }
                    standaloneBySignature.values.forEach { sameSignatureIndices ->
                        val n = sameSignatureIndices.size
                        // Local union-find to merge methods whose owners share a hierarchy edge
                        val ufLocal = IntArray(n) { it }
                        fun findLocal(x: Int): Int {
                            var r = x
                            while (ufLocal[r] != r) r = ufLocal[r]
                            var c = x; while (c != r) {
                                val nxt = ufLocal[c]; ufLocal[c] = r; c = nxt
                            }
                            return r
                        }
                        if (n > 1) {
                            for (i in 0 until n) {
                                val ownerI = MethodHierarchy.Entry(sameSignatureIndices.getInt(i)).owner.index
                                val descsI = classHierarchy.descendants[ownerI]
                                for (j in i + 1 until n) {
                                    val ownerJ = MethodHierarchy.Entry(sameSignatureIndices.getInt(j)).owner.index
                                    // Merge if one owner is ancestor/descendant of the other,
                                    // OR if they share a common descendant (diamond inheritance:
                                    // two sibling parents whose descendant overrides both cancel()s).
                                    val shouldMerge = classHierarchy.descendantsSet[ownerI].contains(ownerJ) ||
                                        classHierarchy.descendantsSet[ownerJ].contains(ownerI) ||
                                        run {
                                            val descsJSet = classHierarchy.descendantsSet[ownerJ]
                                            descsI.any { descsJSet.contains(it) }
                                        }
                                    if (shouldMerge) {
                                        val ri = findLocal(i)
                                        val rj = findLocal(j)
                                        if (ri != rj) ufLocal[rj] = ri
                                    }
                                }
                            }
                        }
                        // Collect components and add one relatedGroups entry per component
                        val components = Int2ObjectOpenHashMap<IntLinkedOpenHashSet>()
                        for (i in 0 until n) {
                            components.computeIfAbsent(findLocal(i)) { IntLinkedOpenHashSet() }
                                .add(sameSignatureIndices.getInt(i))
                        }
                        components.values.forEach { group ->
                            val descs = ObjectLinkedOpenHashSet<String>()
                            group.forEach { idx -> descs.add(MethodHierarchy.Entry(idx).desc) }
                            relatedGroups.add(group to descs)
                        }
                    }
                }

                Logger.info("    Generating mappings for method groups...")
                val dictionary = NameGenerator.getDictionary(config.dictionary)
                val sourceAndOverridesMapping = sourceAndOverridesMapping.global
                // share a same name in a group
                val nameGenerators = Array(classHierarchy.classCount) { NameGenerator(dictionary) }
                // Two-level map (name �?descriptor set) replaces a flat Set<name+desc>.
                // The inner set is only allocated/consulted when a name collision is possible,
                // and �?critically �?no string concatenation is needed in the hot check loop.
                val existedNameMap =
                    Array(classHierarchy.classCount) { Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>>() }
                var methodMappingCount = 0

                relatedGroups.forEach outer@{ group ->
                    // Pre-size to avoid rehashing; each source contributes itself + its descendants
                    val checkSet = IntLinkedOpenHashSet(group.first.size * 4)
                    group.first.forEach { sourceIdx ->
                        val source = MethodHierarchy.Entry(sourceIdx)
                        checkSet.add(source.owner.index)
                        // Disable up check for static and private fields
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
                        newName = dic.nextName(config.heavyOverloads, first.descCode)
                        var keepThisName = true
                        run check@{
                            checkList.forEach { owner ->
                                // One name-level probe; only scan descs when the name is already taken.
                                // This eliminates all string concatenation from the hot check loop.
                                val takenDescs = existedNameMap[owner.index][newName]
                                if (takenDescs != null) {
                                    for (desc in groupDescs) {
                                        if (takenDescs.contains(desc)) {
                                            keepThisName = false
                                            //if (groupDescs.size > 1) {
                                            //    println(
                                            //        "Bridge method desc collapse first=${first.desc}," +
                                            //                " desc=${groupDescs.joinToString(", ")}"
                                            //    )
                                            //}
                                            return@check
                                        }
                                    }
                                }
                            }
                        }
                        if (keepThisName) break
                    }
                    // Register ALL descs (source + bridge) to prevent name collisions on bridge descriptors.
                    // Previously only first.desc was registered, which allowed a later unrelated group to
                    // reuse the same name with a bridge descriptor �?duplicate name+desc in the same scope.
                    checkList.forEach { owner ->
                        val descsForName = existedNameMap[owner.index]
                            .computeIfAbsent(newName) { ObjectOpenHashSet(groupDescs.size * 2 + 1) }
                        for (desc in groupDescs) {
                            descsForName.add(desc)
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
                        // Hoist the static-method flag and its O(1) method-code lookup out of the
                        // descendant loop. The old code called descendant.classNode.methods.any { }
                        // which is O(methods-per-class) per descendant; the MethodHierarchy's
                        // classNodeMethodCodeMethodLookup provides the same query in O(1).
                        val srcIsStatic = sourceMethod.node.isStatic
                        val srcMethodCode =
                            if (srcIsStatic) methodHierarchy.methodToMethodCode[sourceMethod.index] else -1
                        sourceMethod.owner.descendants.forEach { descendant ->
                            // For static methods, skip descendants that declare their own version
                            // of the method �?those are independent and will get their own name
                            // from their own source group.  We still propagate to descendants that
                            // do NOT declare the method themselves, because mapMethodName() is a
                            // flat lookup and any INVOKESTATIC child.method call sites (which the
                            // Java compiler can emit when accessing an inherited static via the
                            // child type) would go un-remapped without an explicit entry here.
                            if (srcIsStatic &&
                                methodHierarchy.classNodeMethodCodeMethodLookup[descendant.index].containsKey(
                                    srcMethodCode
                                )
                            ) return@forEach
                            instance.nameMapping.putMethodMapping(
                                descendant.name,
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
