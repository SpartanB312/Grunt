package net.spartanb312.genesis.kotlin.extensions.insn

import net.spartanb312.genesis.kotlin.InsnListBuilder
import net.spartanb312.genesis.kotlin.extensions.BuilderDSL
import net.spartanb312.genesis.kotlin.extensions.node
import org.objectweb.asm.Label
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode

/**
 * Table switch insn node
 */
@JvmName("TABLESWITCH_LABEL")
@BuilderDSL
fun InsnListBuilder.TABLESWITCH(min: Int, max: Int, def: Label, vararg labels: Label) =
    +TableSwitchInsnNode(min, max, def.node, *labels.map { it.node }.toTypedArray())

@JvmName("TABLESWITCH_STRING")
@BuilderDSL
fun InsnListBuilder.TABLESWITCH(min: Int, max: Int, def: String, vararg labels: String) =
    +TableSwitchInsnNode(min, max, L[def].node, *labels.map { L[it].node }.toTypedArray())

@BuilderDSL
fun InsnListBuilder.TABLESWITCH(min: Int, max: Int, def: LabelNode, vararg labels: LabelNode) =
    +TableSwitchInsnNode(min, max, def, *labels)

/**
 * Lookup switch insn node
 */
@JvmName("LOOKUPSWITCH_LABEL")
@BuilderDSL
fun InsnListBuilder.LOOKUPSWITCH(def: Label, keys: IntArray, labels: Array<Label>) {
    require(keys.size == labels.size) {
        "The total amount of keys and labels must be the same"
    }
    val sortedBranch = Array(keys.size) { keys[it] to labels[it].node }.sortedBy { it.first }
    val sortedKeys = IntArray(keys.size) { sortedBranch[it].first }
    val sortedLabels = Array(keys.size) { sortedBranch[it].second }
    +LookupSwitchInsnNode(def.node, sortedKeys, sortedLabels)
}

@JvmName("LOOKUPSWITCH_STRING")
@BuilderDSL
fun InsnListBuilder.LOOKUPSWITCH(def: String, keys: IntArray, labels: Array<String>) {
    require(keys.size == labels.size) {
        "The total amount of keys and labels must be the same"
    }
    val sortedBranch = Array(keys.size) { keys[it] to L[labels[it]].node }.sortedBy { it.first }
    val sortedKeys = IntArray(keys.size) { sortedBranch[it].first }
    val sortedLabels = Array(keys.size) { sortedBranch[it].second }
    +LookupSwitchInsnNode(L[def].node, sortedKeys, sortedLabels)
}

fun InsnListBuilder.LOOKUPSWITCH(def: LabelNode, keys: IntArray, labels: Array<LabelNode>) {
    require(keys.size == labels.size) {
        "The total amount of keys and labels must be the same"
    }
    val sortedBranch = Array(keys.size) { keys[it] to labels[it] }.sortedBy { it.first }
    val sortedKeys = IntArray(keys.size) { sortedBranch[it].first }
    val sortedLabels = Array(keys.size) { sortedBranch[it].second }
    +LookupSwitchInsnNode(def, sortedKeys, sortedLabels)
}

@JvmName("LOOKUPSWITCH_LABEL")
@BuilderDSL
fun InsnListBuilder.LOOKUPSWITCH(def: Label, vararg branches: Pair<Int, Label>) {
    val sortedBranch = branches.sortedBy { it.first }
    val sortedKeys = IntArray(branches.size) { sortedBranch[it].first }
    val sortedLabels = Array(branches.size) { sortedBranch[it].second.node }
    +LookupSwitchInsnNode(def.node, sortedKeys, sortedLabels)
}

@JvmName("LOOKUPSWITCH_STRING")
@BuilderDSL
fun InsnListBuilder.LOOKUPSWITCH(def: String, vararg branches: Pair<Int, String>) {
    val sortedBranch = branches.sortedBy { it.first }
    val sortedKeys = IntArray(branches.size) { sortedBranch[it].first }
    val sortedLabels = Array(branches.size) { L[sortedBranch[it].second].node }
    +LookupSwitchInsnNode(L[def].node, sortedKeys, sortedLabels)
}

@BuilderDSL
fun InsnListBuilder.LOOKUPSWITCH(def: Label, vararg branches: Pair<Int, LabelNode>) {
    val sortedBranch = branches.sortedBy { it.first }
    val sortedKeys = IntArray(branches.size) { sortedBranch[it].first }
    val sortedLabels = Array(branches.size) { sortedBranch[it].second }
    +LookupSwitchInsnNode(def.node, sortedKeys, sortedLabels)
}