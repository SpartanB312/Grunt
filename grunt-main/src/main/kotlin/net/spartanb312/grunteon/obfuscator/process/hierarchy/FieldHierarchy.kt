package net.spartanb312.grunteon.obfuscator.process.hierarchy

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.genesis.kotlin.extensions.*
import org.objectweb.asm.tree.FieldNode
import java.util.function.ToIntFunction

/**
 * Key for field lookup, consisting of field name and descriptor.
 */
data class FieldNodeKey(
    val name: String,
    val desc: String
)

/**
 * Field hierarchy built on top of class hierarchy, data is stored in parallel array for better performance.
 *
 * It uses internal field indices to read data
 */
class FieldHierarchy(
    /**
     * Class hierarchy this field hierarchy is built on
     */
    val classHierarchy: ClassHierarchy,
    /**
     * All field nodes in this field hierarchy, indexed by internal field index
     */
    val fieldNodes: Array<FieldNode>,
    /**
     * Owner class index of each field, indexed by internal field index
     */
    val fieldOwner: IntArray,
    /**
     * Class node fields, indexed by class index
     */
    val classNodeFields: Array<IntArray>,
    /**
     * Lookup for fields in a class, indexed by class index, then by field key, returns internal field index
     */
    val classNodeFieldLookup: Array<Object2IntOpenHashMap<FieldNodeKey>>,
    /**
     * Whether a field is a source field, indexed by internal field index
     *
     * A source field is a either a private field,
     * or its owner class does not have an ancestor class that has the same field (same name and descriptor).
     */
    val isSourceField: BooleanArray,
    val descCodeLookup: Object2IntOpenHashMap<String>,
    val descCode: IntArray,
) {
    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findField(className: String, fieldName: String, fieldDesc: String): Entry {
        val classIdx = classHierarchy.findClass(className)
        if (classIdx == -1) return Entry(-1)
        return findField(classIdx, fieldName, fieldDesc)
    }

    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findField(classIdx: Int, fieldName: String, fieldDesc: String): Entry {
        val fieldLookup = classNodeFieldLookup[classIdx]
        val fieldKey = FieldNodeKey(fieldName, fieldDesc)
        return Entry(fieldLookup.getInt(fieldKey))
    }

    @JvmInline
    value class EntryArray(val array: IntArray) {
        val size get() = array.size

        operator fun get(index: Int): Entry {
            return Entry(array[index])
        }

        inline fun forEach(action: (Entry) -> Unit) {
            for (i in array.indices) {
                action(Entry(array[i]))
            }
        }

        companion object {
            val EMPTY = EntryArray(IntArray(0))
        }
    }

    @JvmInline
    value class Entry(val index: Int) {
        val isValid: Boolean get() = index != -1

        context(mh: FieldHierarchy)
        inline val name: String get() = mh.fieldNodes[index].name

        context(mh: FieldHierarchy)
        inline val desc: String get() = mh.fieldNodes[index].desc

        context(fh: FieldHierarchy)
        val owner: ClassHierarchy.Entry get() = ClassHierarchy.Entry(fh.fieldOwner[index])

        context(fh: FieldHierarchy)
        val descCode: Int get() = fh.descCode[index]

        context(fh: FieldHierarchy)
        val node: FieldNode get() = fh.fieldNodes[index]

        context(fh: FieldHierarchy)
        val isSourceField: Boolean get() = fh.isSourceField[index]
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun build(classHierarchy: ClassHierarchy): FieldHierarchy {
            val fieldNodes = ObjectArrayList<FieldNode>(classHierarchy.realClassCount)
            val fieldOwner = IntArrayList(classHierarchy.realClassCount)
            val classFieldNodeLookup = Array(classHierarchy.realClassCount) {
                Object2IntOpenHashMap<FieldNodeKey>().apply {
                    defaultReturnValue(-1)
                }
            }
            val classToField = arrayOfNulls<IntArrayList>(classHierarchy.realClassCount) as Array<IntArrayList>

            fun populateFieldNodesAndOwners() {
                for (i in 0..<classHierarchy.realClassCount) {
                    val classNode = classHierarchy.classNodes[i]
                    val fields = classNode.fields ?: emptyList()
                    val fieldList = IntArrayList()
                    classToField[i] = fieldList
                    val fieldLookup = classFieldNodeLookup[i]
                    fieldLookup.defaultReturnValue(-1)
                    for (j in fields.indices) {
                        val fieldNode = fields[j]
                        val index = fieldNodes.size
                        fieldNodes.add(fieldNode)
                        val key = FieldNodeKey(fieldNode.name, fieldNode.desc)
                        fieldLookup[key] = index
                        fieldOwner.add(i)
                        fieldList.add(index)
                    }
                }
            }
            populateFieldNodesAndOwners()

            val fieldCount = fieldNodes.size
            val fieldCodeLookup = Object2IntOpenHashMap<String>(fieldCount).apply {
                defaultReturnValue(-1)
            }
            val fieldCode = IntArray(fieldCount)
            val fieldAccess = IntArray(fieldCount)
            val fieldToFieldTree = Array(classHierarchy.realClassCount) {
                IntOpenHashSet()
            } // Membership of inherited non-private field codes.

            val descCodeLookup = Object2IntOpenHashMap<String>(fieldCount).apply {
                defaultReturnValue(-1)
            }
            val descCode = IntArray(fieldCount)

            fun assignFieldCodeAndBroadcastToDescendants() {
                for (fieldIdx in 0..<fieldCount) {
                    val fieldNode = fieldNodes[fieldIdx]
                    val codename = fieldNode.name + fieldNode.desc
                    val myFieldCode = fieldCodeLookup.computeIfAbsent(codename, ToIntFunction {
                        fieldCodeLookup.size
                    })
                    fieldCode[fieldIdx] = myFieldCode
                    fieldAccess[fieldIdx] = fieldNode.access

                    val dc = descCodeLookup.computeIfAbsent(fieldNode.desc, ToIntFunction {
                        descCodeLookup.size
                    })
                    descCode[fieldIdx] = dc

                    // Fill inherent field bits
                    if (fieldNode.access.isPrivate) continue
                    val fieldOwnerIdx = fieldOwner.getInt(fieldIdx)
                    fieldToFieldTree[fieldOwnerIdx].add(myFieldCode)
                    val descendents = classHierarchy.descendants[fieldOwnerIdx]
                    for (i in 0..<descendents.size) {
                        val descendentIdx = descendents[i]
                        if (descendentIdx < classHierarchy.realClassCount) {
                            fieldToFieldTree[descendentIdx].add(myFieldCode)
                        }
                    }
                }
            }
            assignFieldCodeAndBroadcastToDescendants()

            // Search up for source field
            val isSourceField = BooleanArray(fieldCount)

            fun assignFieldTreeToFields() {
                for (classIdx in 0..<classHierarchy.realClassCount) {
                    fun setSource(fieldIdx: Int) {
                        assert(!isSourceField[fieldIdx])
                        isSourceField[fieldIdx] = true
                    }

                    val myFields = classToField[classIdx]
                    val myFieldArray = myFields.elements()
                    for (j in myFields.indices) {
                        val myField = myFieldArray[j]
                        if (!fieldAccess[myField].isPrivate && !fieldAccess[myField].isStatic) continue
                        setSource(myField)
                    }

                    val parentIndices = classHierarchy.parents[classIdx]
                    val allParentFieldCodeBits = IntOpenHashSet()
                    for (i in parentIndices.indices) {
                        val parentIdx = parentIndices[i]
                        if (parentIdx >= classHierarchy.realClassCount) continue
                        val parentCodeBits = fieldToFieldTree[parentIdx]
                        allParentFieldCodeBits.addAll(parentCodeBits)
                    }

                    for (j in myFields.indices) {
                        val myField = myFieldArray[j]
                        if (fieldAccess[myField].isPrivate || fieldAccess[myField].isStatic) continue
                        val myFieldCode = fieldCode[myField]
                        if (!allParentFieldCodeBits.contains(myFieldCode)) {
                            setSource(myField)
                        }
                    }
                }
            }

            assignFieldTreeToFields()

            return FieldHierarchy(
                classHierarchy,
                fieldNodes.toTypedArray(),
                fieldOwner.toIntArray(),
                Array(classHierarchy.realClassCount) { classToField[it].toIntArray() },
                classFieldNodeLookup,
                isSourceField,
                descCodeLookup,
                descCode
            )
        }
    }
}