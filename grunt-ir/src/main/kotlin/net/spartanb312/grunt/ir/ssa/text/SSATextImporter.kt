package net.spartanb312.grunt.ir.ssa.text

import net.spartanb312.grunt.ir.ssa.core.*
import java.nio.file.Files
import java.nio.file.Path

class SSATextImporter {
    fun read(path: Path): SSAFunction {
        return parse(Files.readString(path))
    }

    fun parse(text: String): SSAFunction {
        val records = SSASExpressionParser(text).parseAll().map { it.asList() }
        if (records.isEmpty()) error("Empty .ir file")

        records[0].expectHead("grunt-ir")
        val version = records[0].field("version").asAtom().toInt()
        require(version == 1) { "Unsupported .ir version $version" }

        val functionRecord = records.getOrNull(1)?.expectHead("function")
            ?: error("Missing function record")
        val functionId = symbolId(functionRecord.field("id").asAtom())
        val functionName = functionRecord.field("name").asString()
        val returnType = type(functionRecord.field("return").asString())

        val blockRecords = records.filter { it.headAtom == "block" }
        val blocks = blockRecords.associate { record ->
            val id = blockId(record.field("id").asAtom())
            id to SSABlock(id)
        }.toMutableMap()

        val values = linkedMapOf<SSAValueId, SSAStructure>()
        val parameters = records
            .filter { it.headAtom == "param" }
            .map { record ->
                SSAParameter(
                    valueId(record.field("id").asAtom()),
                    record.field("index").asAtom().toInt(),
                    type(record.field("type").asString()),
                    record.fieldOrNull("name")?.asString()
                ).also { values[it.id] = it }
            }
            .sortedBy { it.index }
            .toMutableList()

        val symbol = SSAFunctionSymbol(
            functionId,
            functionName,
            parameters.map { it.type },
            returnType
        )

        parseBlockContents(records, blocks, values)

        val orderedBlocks = blockRecords.map { blocks.getValue(blockId(it.field("id").asAtom())) }.toMutableList()
        val entry = orderedBlocks.firstOrNull() ?: error("Function has no blocks")
        return SSAFunction(symbol, parameters, orderedBlocks, entry)
    }

    private fun parseBlockContents(
        records: List<SSASExpr.ListValue>,
        blocks: MutableMap<SSABlockId, SSABlock>,
        values: MutableMap<SSAValueId, SSAStructure>
    ) {
        var current: SSABlock? = null

        for (record in records.drop(2)) {
            when (record.headAtom) {
                "block" -> current = blocks.getValue(blockId(record.field("id").asAtom()))
                "arg" -> {
                    val block = current ?: error("Arg record outside block")
                    val arg = SSABlockArg(
                        valueId(record.field("id").asAtom()),
                        record.field("index").asAtom().toInt(),
                        type(record.field("type").asString()),
                        origin(record.field("origin").asList()),
                        record.fieldOrNull("name")?.asString()
                    )
                    block.args += arg
                    values[arg.id] = arg
                }
                "inst" -> {
                    val block = current ?: error("Instruction record outside block")
                    block.instructions += instruction(record, values)
                }
                "term" -> {
                    val block = current ?: error("Terminator record outside block")
                    block.terminator = terminator(record, blocks, values)
                }
                "end" -> {
                    val kind = record.items.getOrNull(1)?.asAtom()
                    if (kind == "block") current = null
                }
            }
        }
    }

    private fun instruction(
        record: SSASExpr.ListValue,
        values: MutableMap<SSAValueId, SSAStructure>
    ): SSAInstruction {
        val op = record.field("op").asAtom()
        val effect = effect(record.field("effect").asList())
        val result = result(record, values)

        return when (op) {
            "unary" -> SSAUnaryInstruction(
                requireResult(result, op),
                enumId(record.field("unary").asAtom()),
                value(record.field("value"), values),
                effect
            )
            "binary" -> SSABinaryInstruction(
                requireResult(result, op),
                enumId(record.field("binary").asAtom()),
                value(record.field("lhs"), values),
                value(record.field("rhs"), values),
                effect
            )
            "compare" -> SSACompareInstruction(
                requireResult(result, op),
                enumId(record.field("predicate").asAtom()),
                value(record.field("lhs"), values),
                value(record.field("rhs"), values),
                effect
            )
            "convert" -> SSAConvertInstruction(
                requireResult(result, op),
                enumId(record.field("kind").asAtom()),
                value(record.field("value"), values),
                type(record.field("target").asString()),
                effect
            )
            "load-field" -> SSALoadFieldInstruction(
                requireResult(result, op),
                field(record.field("field").asList()),
                nullableValue(record.field("receiver"), values),
                effect
            )
            "store-field" -> SSAStoreFieldInstruction(
                field(record.field("field").asList()),
                nullableValue(record.field("receiver"), values),
                value(record.field("value"), values),
                effect
            )
            "array-load" -> SSAArrayLoadInstruction(
                requireResult(result, op),
                value(record.field("array"), values),
                value(record.fieldOrNull("array-index") ?: record.fieldLast("index"), values),
                effect,
                record.fieldOrNull("element")?.asString()?.let(::type)
            )
            "array-store" -> SSAArrayStoreInstruction(
                value(record.field("array"), values),
                value(record.fieldOrNull("array-index") ?: record.fieldLast("index"), values),
                value(record.field("value"), values),
                effect,
                record.fieldOrNull("element")?.asString()?.let(::type)
            )
            "call" -> SSACallInstruction(
                result,
                callable(record.field("target").asList()),
                values(record.field("args").asList(), values),
                enumId(record.field("dispatch").asAtom()),
                effect
            )
            "resolve-dynamic-value" -> SSAResolveDynamicValueInstruction(
                requireResult(result, op),
                dynamicValueSite(record.field("site").asList()),
                effect
            )
            "dynamic-call" -> SSADynamicCallInstruction(
                result,
                dynamicCallSite(record.field("site").asList()),
                values(record.field("args").asList(), values),
                effect
            )
            "allocate" -> SSAAllocateInstruction(
                requireResult(result, op),
                allocation(record.field("allocation").asList()),
                values(record.field("args").asList(), values),
                effect
            )
            "intrinsic" -> SSAIntrinsicInstruction(
                result,
                callable(record.field("intrinsic").asList()) as SSAIntrinsicRef,
                values(record.field("args").asList(), values),
                effect
            )
            "barrier" -> SSABarrierInstruction(
                record.field("reason").let { if (it.asAtomOrNull() == "null") null else it.asString() },
                effect
            )
            else -> error("Unsupported instruction op '$op'")
        }
    }

    private fun terminator(
        record: SSASExpr.ListValue,
        blocks: Map<SSABlockId, SSABlock>,
        values: Map<SSAValueId, SSAStructure>
    ): SSATerminator {
        return when (val op = record.field("op").asAtom()) {
            "jump" -> SSAJumpTerminator(successor(record.field("target").asList(), blocks, values))
            "branch" -> SSABranchTerminator(
                value(record.field("condition"), values),
                successor(record.field("true").asList(), blocks, values),
                successor(record.field("false").asList(), blocks, values)
            )
            "switch" -> SSASwitchTerminator(
                value(record.field("value"), values),
                switchCases(record.field("cases").asList(), blocks, values),
                successor(record.field("default").asList(), blocks, values)
            )
            "return" -> SSAReturnTerminator(record.fieldOrNull("value")?.let { value(it, values) })
            "throw" -> SSAThrowTerminator(value(record.field("exception"), values))
            "unreachable" -> SSAUnreachableTerminator
            else -> error("Unsupported terminator op '$op'")
        }
    }

    private fun result(
        record: SSASExpr.ListValue,
        values: MutableMap<SSAValueId, SSAStructure>
    ): SSAInstructionResult? {
        val id = record.fieldOrNull("result")?.asAtom()?.let(::valueId) ?: return null
        val result = SSAInstructionResult(
            id,
            type(record.field("type").asString()),
            record.fieldOrNull("name")?.asString()
        )
        values[id] = result
        return result
    }

    private fun requireResult(result: SSAInstructionResult?, op: String): SSAInstructionResult {
        return result ?: error("Instruction '$op' requires a result")
    }

    private fun value(expr: SSASExpr, values: Map<SSAValueId, SSAStructure>): SSAValue {
        val list = expr.asList()
        return when (val head = list.headAtom) {
            "ssa" -> values[valueId(list.items[1].asAtom())]
                ?: error("Unknown SSA value ${list.items[1].asAtom()}")
            "bool" -> SSABoolLiteral(list.items[1].asAtom().toBooleanStrict())
            "int" -> SSAIntLiteral(
                list.field("value").asAtom().toLong(),
                type(list.field("type").asString()) as? SSAIntegerType ?: SSAI32Type
            )
            "float" -> SSAFloatLiteral(
                list.field("value").asAtom().toDouble(),
                type(list.field("type").asString()) as? SSAFloatType ?: SSAF64Type
            )
            "null" -> SSANullLiteral
            "opaque" -> SSAOpaqueLiteral(
                list.field("text").asString(),
                type(list.field("type").asString())
            )
            else -> error("Unsupported value kind '$head'")
        }
    }

    private fun nullableValue(expr: SSASExpr, values: Map<SSAValueId, SSAStructure>): SSAValue? {
        return if (expr.asAtomOrNull() == "null") null else value(expr, values)
    }

    private fun values(expr: SSASExpr.ListValue, values: Map<SSAValueId, SSAStructure>): List<SSAValue> {
        return expr.items.map { value(it, values) }
    }

    private fun successor(
        expr: SSASExpr.ListValue,
        blocks: Map<SSABlockId, SSABlock>,
        values: Map<SSAValueId, SSAStructure>
    ): SSASuccessor {
        expr.expectHead("succ")
        val block = blocks[blockId(expr.field("block").asAtom())]
            ?: error("Unknown block ${expr.field("block").asAtom()}")
        return SSASuccessor(block, values(expr.field("args").asList(), values))
    }

    private fun switchCases(
        expr: SSASExpr.ListValue,
        blocks: Map<SSABlockId, SSABlock>,
        values: Map<SSAValueId, SSAStructure>
    ): List<SSASwitchCase> {
        return expr.items.map {
            val case = it.asList().expectHead("case")
            SSASwitchCase(
                case.field("key").asAtom().toLong(),
                successor(case.field("target").asList(), blocks, values)
            )
        }
    }

    private fun field(expr: SSASExpr.ListValue): SSAFieldRef {
        return when (val head = expr.headAtom) {
            "symbol-field" -> {
                val symbol = SSAFieldSymbol(
                    symbolId(expr.field("id").asAtom()),
                    expr.field("name").asString(),
                    type(expr.field("type").asString()),
                    isStatic = expr.field("static").asAtom().toBooleanStrict()
                )
                SSASymbolFieldRef(symbol)
            }
            "external-field" -> SSAExternalFieldRef(
                external(expr.field("ref").asList()),
                type(expr.field("type").asString()),
                expr.field("static").asAtom().toBooleanStrict()
            )
            else -> error("Unsupported field ref '$head'")
        }
    }

    private fun callable(expr: SSASExpr.ListValue): SSACallableRef {
        return when (val head = expr.headAtom) {
            "function" -> SSAFunctionRef(
                SSAFunctionSymbol(
                    symbolId(expr.field("id").asAtom()),
                    expr.field("name").asString(),
                    types(expr.field("params").asList()),
                    type(expr.field("return").asString())
                )
            )
            "external-function" -> SSAExternalFunctionRef(
                external(expr.field("ref").asList()),
                types(expr.field("params").asList()),
                type(expr.field("return").asString())
            )
            "intrinsic" -> SSAIntrinsicRef(
                expr.field("name").asString(),
                types(expr.field("params").asList()),
                type(expr.field("return").asString())
            )
            else -> error("Unsupported callable ref '$head'")
        }
    }

    private fun dynamicValueSite(expr: SSASExpr.ListValue): SSADynamicValueSite {
        expr.expectHead("dynamic-value")
        return SSADynamicValueSite(
            dynamicSiteId(expr.field("id").asAtom()),
            type(expr.field("type").asString()),
            nullableExternal(expr.field("external")),
            nullableString(expr.field("name"))
        )
    }

    private fun dynamicCallSite(expr: SSASExpr.ListValue): SSADynamicCallSite {
        expr.expectHead("dynamic-call")
        return SSADynamicCallSite(
            dynamicSiteId(expr.field("id").asAtom()),
            types(expr.field("params").asList()),
            type(expr.field("return").asString()),
            nullableExternal(expr.field("external")),
            nullableString(expr.field("name"))
        )
    }

    private fun allocation(expr: SSASExpr.ListValue): SSAAllocation {
        return when (val head = expr.headAtom) {
            "object" -> SSAAllocation.Object(type(expr.field("type").asString()) as SSARefType)
            "array" -> SSAAllocation.Array(type(expr.field("type").asString()) as SSAArrayType)
            "opaque" -> SSAAllocation.Opaque(type(expr.field("type").asString()), expr.field("name").asString())
            else -> error("Unsupported allocation '$head'")
        }
    }

    private fun effect(expr: SSASExpr.ListValue): SSAEffect {
        return SSAEffect(
            mayThrow = expr.bool("mayThrow"),
            readsMemory = expr.bool("readsMemory"),
            writesMemory = expr.bool("writesMemory"),
            readsExternalState = expr.bool("readsExternalState"),
            writesExternalState = expr.bool("writesExternalState"),
            callsExternal = expr.bool("callsExternal"),
            resolvesExternal = expr.bool("resolvesExternal"),
            isBarrier = expr.bool("barrier"),
            canDuplicate = expr.bool("canDuplicate"),
            canMove = expr.bool("canMove")
        )
    }

    private fun origin(expr: SSASExpr.ListValue): SSABlockArgOrigin {
        return when (val head = expr.headAtom) {
            "join" -> SSABlockArgOrigin.Join
            "synthetic" -> SSABlockArgOrigin.Synthetic
            "exception" -> SSABlockArgOrigin.ExceptionObject
            "parameter" -> SSABlockArgOrigin.Parameter(expr.field("index").asAtom().toInt())
            "frontend" -> SSABlockArgOrigin.FrontendState(
                expr.field("name").asString(),
                expr.field("index").asAtom().toInt()
            )
            else -> error("Unsupported block arg origin '$head'")
        }
    }

    private fun external(expr: SSASExpr.ListValue): SSAExternalRef {
        return SSAExternalRef(
            externalRefId(expr.field("id").asAtom()),
            enumId<SSAExternalRefKind>(expr.field("kind").asAtom()),
            nullableString(expr.field("name"))
        )
    }

    private fun nullableExternal(expr: SSASExpr): SSAExternalRef? {
        if (expr.asAtomOrNull() == "null") return null
        val list = expr.asList()
        return if (list.items.size == 1 && list.items.first() is SSASExpr.ListValue) {
            external(list.items.first().asList())
        } else {
            external(list)
        }
    }

    private fun nullableString(expr: SSASExpr): String? {
        return if (expr.asAtomOrNull() == "null") null else expr.asString()
    }

    private fun types(expr: SSASExpr.ListValue): List<SSAType> {
        return expr.items.map { type(it.asString()) }
    }

    private fun type(displayName: String): SSAType {
        return typeParser.parse(displayName)
    }

    private fun SSASExpr.ListValue.bool(name: String): Boolean {
        return field(name).asAtom().toBooleanStrict()
    }

    private fun SSASExpr.asAtomOrNull(): String? = (this as? SSASExpr.Atom)?.value

    private fun SSASExpr.ListValue.fieldLast(name: String): SSASExpr {
        for (item in items.asReversed()) {
            val list = item as? SSASExpr.ListValue ?: continue
            if (list.items.firstOrNull()?.atomOrNull() != name) continue
            val rest = list.items.drop(1)
            return when (rest.size) {
                0 -> SSASExpr.ListValue(emptyList())
                1 -> rest.first()
                else -> SSASExpr.ListValue(rest)
            }
        }
        error("Missing field '$name' in ${headAtom ?: "list"}")
    }

    private fun blockId(value: String) = SSABlockId(value.removePrefix("b").toInt())

    private fun valueId(value: String) = SSAValueId(value.removePrefix("%").toInt())

    private fun symbolId(value: String) = SSASymbolId(value.removePrefix("sym").toInt())

    private fun externalRefId(value: String) = SSAExternalRefId(value.removePrefix("ext").toInt())

    private fun dynamicSiteId(value: String) = SSADynamicSiteId(value.removePrefix("dyn").toInt())

    private inline fun <reified T : Enum<T>> enumId(id: String): T {
        return enumValues<T>().firstOrNull { it.name.equals(id, ignoreCase = true) }
            ?: error("Unknown ${T::class.simpleName} '$id'")
    }

    private val typeParser = SSATextTypeParser()
}

private class SSATextTypeParser {
    private val typeSymbols = linkedMapOf<String, SSATypeSymbol>()
    private var nextTypeSymbolId = 0

    fun parse(text: String): SSAType {
        return when (text) {
            "bool" -> SSABoolType
            "i8" -> SSAI8Type
            "i16" -> SSAI16Type
            "char" -> SSACharType
            "i32" -> SSAI32Type
            "i64" -> SSAI64Type
            "f32" -> SSAF32Type
            "f64" -> SSAF64Type
            "void" -> SSAVoidType
            "unknown" -> SSAUnknownType
            "null" -> SSANullType
            else -> parseCompound(text)
        }
    }

    private fun parseCompound(text: String): SSAType {
        if (text.startsWith("ref<")) {
            val nullable = text.endsWith("?")
            val bodyEnd = if (nullable) text.length - 1 else text.length
            val name = text.substring(4, bodyEnd - 1)
            return SSARefType(if (name == "opaque") null else typeSymbol(name), nullable)
        }

        if (text.startsWith("array")) {
            val lt = text.indexOf('<')
            val nullable = text.endsWith("?")
            val dimensions = text.substring(5, lt).toInt()
            val end = findMatchingAngle(text, lt)
            val element = parse(text.substring(lt + 1, end))
            return SSAArrayType(element, dimensions, nullable)
        }

        return SSAOpaqueType(text)
    }

    private fun findMatchingAngle(text: String, start: Int): Int {
        var depth = 0
        for (index in start until text.length) {
            when (text[index]) {
                '<' -> depth++
                '>' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        error("Unclosed type '$text'")
    }

    private fun typeSymbol(name: String): SSATypeSymbol {
        return typeSymbols.getOrPut(name) {
            SSATypeSymbol(SSASymbolId(1_000_000 + nextTypeSymbolId++), name)
        }
    }
}
