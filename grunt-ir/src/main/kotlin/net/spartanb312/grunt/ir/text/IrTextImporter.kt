package net.spartanb312.grunt.ir.text

import net.spartanb312.grunt.ir.core.*
import java.nio.file.Files
import java.nio.file.Path

class IrTextImporter {
    fun read(path: Path): IrFunction {
        return parse(Files.readString(path))
    }

    fun parse(text: String): IrFunction {
        val records = IrSExpressionParser(text).parseAll().map { it.asList() }
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
            id to IrBlock(id)
        }.toMutableMap()

        val values = linkedMapOf<IrValueId, IrSsaValue>()
        val parameters = records
            .filter { it.headAtom == "param" }
            .map { record ->
                IrParameter(
                    valueId(record.field("id").asAtom()),
                    record.field("index").asAtom().toInt(),
                    type(record.field("type").asString()),
                    record.fieldOrNull("name")?.asString()
                ).also { values[it.id] = it }
            }
            .sortedBy { it.index }
            .toMutableList()

        val symbol = IrFunctionSymbol(
            functionId,
            functionName,
            parameters.map { it.type },
            returnType
        )

        parseBlockContents(records, blocks, values)

        val orderedBlocks = blockRecords.map { blocks.getValue(blockId(it.field("id").asAtom())) }.toMutableList()
        val entry = orderedBlocks.firstOrNull() ?: error("Function has no blocks")
        return IrFunction(symbol, parameters, orderedBlocks, entry)
    }

    private fun parseBlockContents(
        records: List<IrSExpr.ListValue>,
        blocks: MutableMap<IrBlockId, IrBlock>,
        values: MutableMap<IrValueId, IrSsaValue>
    ) {
        var current: IrBlock? = null

        for (record in records.drop(2)) {
            when (record.headAtom) {
                "block" -> current = blocks.getValue(blockId(record.field("id").asAtom()))
                "arg" -> {
                    val block = current ?: error("Arg record outside block")
                    val arg = IrBlockArg(
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
        record: IrSExpr.ListValue,
        values: MutableMap<IrValueId, IrSsaValue>
    ): IrInstruction {
        val op = record.field("op").asAtom()
        val effect = effect(record.field("effect").asList())
        val result = result(record, values)

        return when (op) {
            "unary" -> IrUnaryInstruction(
                requireResult(result, op),
                enumId(record.field("unary").asAtom()),
                value(record.field("value"), values),
                effect
            )
            "binary" -> IrBinaryInstruction(
                requireResult(result, op),
                enumId(record.field("binary").asAtom()),
                value(record.field("lhs"), values),
                value(record.field("rhs"), values),
                effect
            )
            "compare" -> IrCompareInstruction(
                requireResult(result, op),
                enumId(record.field("predicate").asAtom()),
                value(record.field("lhs"), values),
                value(record.field("rhs"), values),
                effect
            )
            "convert" -> IrConvertInstruction(
                requireResult(result, op),
                enumId(record.field("kind").asAtom()),
                value(record.field("value"), values),
                type(record.field("target").asString()),
                effect
            )
            "load-field" -> IrLoadFieldInstruction(
                requireResult(result, op),
                field(record.field("field").asList()),
                nullableValue(record.field("receiver"), values),
                effect
            )
            "store-field" -> IrStoreFieldInstruction(
                field(record.field("field").asList()),
                nullableValue(record.field("receiver"), values),
                value(record.field("value"), values),
                effect
            )
            "array-load" -> IrArrayLoadInstruction(
                requireResult(result, op),
                value(record.field("array"), values),
                value(record.fieldOrNull("array-index") ?: record.fieldLast("index"), values),
                effect,
                record.fieldOrNull("element")?.asString()?.let(::type)
            )
            "array-store" -> IrArrayStoreInstruction(
                value(record.field("array"), values),
                value(record.fieldOrNull("array-index") ?: record.fieldLast("index"), values),
                value(record.field("value"), values),
                effect,
                record.fieldOrNull("element")?.asString()?.let(::type)
            )
            "call" -> IrCallInstruction(
                result,
                callable(record.field("target").asList()),
                values(record.field("args").asList(), values),
                enumId(record.field("dispatch").asAtom()),
                effect
            )
            "resolve-dynamic-value" -> IrResolveDynamicValueInstruction(
                requireResult(result, op),
                dynamicValueSite(record.field("site").asList()),
                effect
            )
            "dynamic-call" -> IrDynamicCallInstruction(
                result,
                dynamicCallSite(record.field("site").asList()),
                values(record.field("args").asList(), values),
                effect
            )
            "allocate" -> IrAllocateInstruction(
                requireResult(result, op),
                allocation(record.field("allocation").asList()),
                values(record.field("args").asList(), values),
                effect
            )
            "intrinsic" -> IrIntrinsicInstruction(
                result,
                callable(record.field("intrinsic").asList()) as IrIntrinsicRef,
                values(record.field("args").asList(), values),
                effect
            )
            "barrier" -> IrBarrierInstruction(
                record.field("reason").let { if (it.asAtomOrNull() == "null") null else it.asString() },
                effect
            )
            else -> error("Unsupported instruction op '$op'")
        }
    }

    private fun terminator(
        record: IrSExpr.ListValue,
        blocks: Map<IrBlockId, IrBlock>,
        values: Map<IrValueId, IrSsaValue>
    ): IrTerminator {
        return when (val op = record.field("op").asAtom()) {
            "jump" -> IrJumpTerminator(successor(record.field("target").asList(), blocks, values))
            "branch" -> IrBranchTerminator(
                value(record.field("condition"), values),
                successor(record.field("true").asList(), blocks, values),
                successor(record.field("false").asList(), blocks, values)
            )
            "switch" -> IrSwitchTerminator(
                value(record.field("value"), values),
                switchCases(record.field("cases").asList(), blocks, values),
                successor(record.field("default").asList(), blocks, values)
            )
            "return" -> IrReturnTerminator(record.fieldOrNull("value")?.let { value(it, values) })
            "throw" -> IrThrowTerminator(value(record.field("exception"), values))
            "unreachable" -> IrUnreachableTerminator
            else -> error("Unsupported terminator op '$op'")
        }
    }

    private fun result(
        record: IrSExpr.ListValue,
        values: MutableMap<IrValueId, IrSsaValue>
    ): IrInstructionResult? {
        val id = record.fieldOrNull("result")?.asAtom()?.let(::valueId) ?: return null
        val result = IrInstructionResult(
            id,
            type(record.field("type").asString()),
            record.fieldOrNull("name")?.asString()
        )
        values[id] = result
        return result
    }

    private fun requireResult(result: IrInstructionResult?, op: String): IrInstructionResult {
        return result ?: error("Instruction '$op' requires a result")
    }

    private fun value(expr: IrSExpr, values: Map<IrValueId, IrSsaValue>): IrValue {
        val list = expr.asList()
        return when (val head = list.headAtom) {
            "ssa" -> values[valueId(list.items[1].asAtom())]
                ?: error("Unknown SSA value ${list.items[1].asAtom()}")
            "bool" -> IrBoolLiteral(list.items[1].asAtom().toBooleanStrict())
            "int" -> IrIntLiteral(
                list.field("value").asAtom().toLong(),
                type(list.field("type").asString()) as? IrIntegerType ?: IrI32Type
            )
            "float" -> IrFloatLiteral(
                list.field("value").asAtom().toDouble(),
                type(list.field("type").asString()) as? IrFloatType ?: IrF64Type
            )
            "null" -> IrNullLiteral
            "opaque" -> IrOpaqueLiteral(
                list.field("text").asString(),
                type(list.field("type").asString())
            )
            else -> error("Unsupported value kind '$head'")
        }
    }

    private fun nullableValue(expr: IrSExpr, values: Map<IrValueId, IrSsaValue>): IrValue? {
        return if (expr.asAtomOrNull() == "null") null else value(expr, values)
    }

    private fun values(expr: IrSExpr.ListValue, values: Map<IrValueId, IrSsaValue>): List<IrValue> {
        return expr.items.map { value(it, values) }
    }

    private fun successor(
        expr: IrSExpr.ListValue,
        blocks: Map<IrBlockId, IrBlock>,
        values: Map<IrValueId, IrSsaValue>
    ): IrSuccessor {
        expr.expectHead("succ")
        val block = blocks[blockId(expr.field("block").asAtom())]
            ?: error("Unknown block ${expr.field("block").asAtom()}")
        return IrSuccessor(block, values(expr.field("args").asList(), values))
    }

    private fun switchCases(
        expr: IrSExpr.ListValue,
        blocks: Map<IrBlockId, IrBlock>,
        values: Map<IrValueId, IrSsaValue>
    ): List<IrSwitchCase> {
        return expr.items.map {
            val case = it.asList().expectHead("case")
            IrSwitchCase(
                case.field("key").asAtom().toLong(),
                successor(case.field("target").asList(), blocks, values)
            )
        }
    }

    private fun field(expr: IrSExpr.ListValue): IrFieldRef {
        return when (val head = expr.headAtom) {
            "symbol-field" -> {
                val symbol = IrFieldSymbol(
                    symbolId(expr.field("id").asAtom()),
                    expr.field("name").asString(),
                    type(expr.field("type").asString()),
                    isStatic = expr.field("static").asAtom().toBooleanStrict()
                )
                IrSymbolFieldRef(symbol)
            }
            "external-field" -> IrExternalFieldRef(
                external(expr.field("ref").asList()),
                type(expr.field("type").asString()),
                expr.field("static").asAtom().toBooleanStrict()
            )
            else -> error("Unsupported field ref '$head'")
        }
    }

    private fun callable(expr: IrSExpr.ListValue): IrCallableRef {
        return when (val head = expr.headAtom) {
            "function" -> IrFunctionRef(
                IrFunctionSymbol(
                    symbolId(expr.field("id").asAtom()),
                    expr.field("name").asString(),
                    types(expr.field("params").asList()),
                    type(expr.field("return").asString())
                )
            )
            "external-function" -> IrExternalFunctionRef(
                external(expr.field("ref").asList()),
                types(expr.field("params").asList()),
                type(expr.field("return").asString())
            )
            "intrinsic" -> IrIntrinsicRef(
                expr.field("name").asString(),
                types(expr.field("params").asList()),
                type(expr.field("return").asString())
            )
            else -> error("Unsupported callable ref '$head'")
        }
    }

    private fun dynamicValueSite(expr: IrSExpr.ListValue): IrDynamicValueSite {
        expr.expectHead("dynamic-value")
        return IrDynamicValueSite(
            dynamicSiteId(expr.field("id").asAtom()),
            type(expr.field("type").asString()),
            nullableExternal(expr.field("external")),
            nullableString(expr.field("name"))
        )
    }

    private fun dynamicCallSite(expr: IrSExpr.ListValue): IrDynamicCallSite {
        expr.expectHead("dynamic-call")
        return IrDynamicCallSite(
            dynamicSiteId(expr.field("id").asAtom()),
            types(expr.field("params").asList()),
            type(expr.field("return").asString()),
            nullableExternal(expr.field("external")),
            nullableString(expr.field("name"))
        )
    }

    private fun allocation(expr: IrSExpr.ListValue): IrAllocation {
        return when (val head = expr.headAtom) {
            "object" -> IrAllocation.Object(type(expr.field("type").asString()) as IrRefType)
            "array" -> IrAllocation.Array(type(expr.field("type").asString()) as IrArrayType)
            "opaque" -> IrAllocation.Opaque(type(expr.field("type").asString()), expr.field("name").asString())
            else -> error("Unsupported allocation '$head'")
        }
    }

    private fun effect(expr: IrSExpr.ListValue): IrEffect {
        return IrEffect(
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

    private fun origin(expr: IrSExpr.ListValue): IrBlockArgOrigin {
        return when (val head = expr.headAtom) {
            "join" -> IrBlockArgOrigin.Join
            "synthetic" -> IrBlockArgOrigin.Synthetic
            "exception" -> IrBlockArgOrigin.ExceptionObject
            "parameter" -> IrBlockArgOrigin.Parameter(expr.field("index").asAtom().toInt())
            "frontend" -> IrBlockArgOrigin.FrontendState(
                expr.field("name").asString(),
                expr.field("index").asAtom().toInt()
            )
            else -> error("Unsupported block arg origin '$head'")
        }
    }

    private fun external(expr: IrSExpr.ListValue): IrExternalRef {
        return IrExternalRef(
            externalRefId(expr.field("id").asAtom()),
            enumId<IrExternalRefKind>(expr.field("kind").asAtom()),
            nullableString(expr.field("name"))
        )
    }

    private fun nullableExternal(expr: IrSExpr): IrExternalRef? {
        if (expr.asAtomOrNull() == "null") return null
        val list = expr.asList()
        return if (list.items.size == 1 && list.items.first() is IrSExpr.ListValue) {
            external(list.items.first().asList())
        } else {
            external(list)
        }
    }

    private fun nullableString(expr: IrSExpr): String? {
        return if (expr.asAtomOrNull() == "null") null else expr.asString()
    }

    private fun types(expr: IrSExpr.ListValue): List<IrType> {
        return expr.items.map { type(it.asString()) }
    }

    private fun type(displayName: String): IrType {
        return typeParser.parse(displayName)
    }

    private fun IrSExpr.ListValue.bool(name: String): Boolean {
        return field(name).asAtom().toBooleanStrict()
    }

    private fun IrSExpr.asAtomOrNull(): String? = (this as? IrSExpr.Atom)?.value

    private fun IrSExpr.ListValue.fieldLast(name: String): IrSExpr {
        for (item in items.asReversed()) {
            val list = item as? IrSExpr.ListValue ?: continue
            if (list.items.firstOrNull()?.atomOrNull() != name) continue
            val rest = list.items.drop(1)
            return when (rest.size) {
                0 -> IrSExpr.ListValue(emptyList())
                1 -> rest.first()
                else -> IrSExpr.ListValue(rest)
            }
        }
        error("Missing field '$name' in ${headAtom ?: "list"}")
    }

    private fun blockId(value: String) = IrBlockId(value.removePrefix("b").toInt())

    private fun valueId(value: String) = IrValueId(value.removePrefix("%").toInt())

    private fun symbolId(value: String) = IrSymbolId(value.removePrefix("sym").toInt())

    private fun externalRefId(value: String) = IrExternalRefId(value.removePrefix("ext").toInt())

    private fun dynamicSiteId(value: String) = IrDynamicSiteId(value.removePrefix("dyn").toInt())

    private inline fun <reified T : Enum<T>> enumId(id: String): T {
        return enumValues<T>().firstOrNull { it.name.equals(id, ignoreCase = true) }
            ?: error("Unknown ${T::class.simpleName} '$id'")
    }

    private val typeParser = IrTextTypeParser()
}

private class IrTextTypeParser {
    private val typeSymbols = linkedMapOf<String, IrTypeSymbol>()
    private var nextTypeSymbolId = 0

    fun parse(text: String): IrType {
        return when (text) {
            "bool" -> IrBoolType
            "i8" -> IrI8Type
            "i16" -> IrI16Type
            "char" -> IrCharType
            "i32" -> IrI32Type
            "i64" -> IrI64Type
            "f32" -> IrF32Type
            "f64" -> IrF64Type
            "void" -> IrVoidType
            "unknown" -> IrUnknownType
            "null" -> IrNullType
            else -> parseCompound(text)
        }
    }

    private fun parseCompound(text: String): IrType {
        if (text.startsWith("ref<")) {
            val nullable = text.endsWith("?")
            val bodyEnd = if (nullable) text.length - 1 else text.length
            val name = text.substring(4, bodyEnd - 1)
            return IrRefType(if (name == "opaque") null else typeSymbol(name), nullable)
        }

        if (text.startsWith("array")) {
            val lt = text.indexOf('<')
            val nullable = text.endsWith("?")
            val dimensions = text.substring(5, lt).toInt()
            val end = findMatchingAngle(text, lt)
            val element = parse(text.substring(lt + 1, end))
            return IrArrayType(element, dimensions, nullable)
        }

        return IrOpaqueType(text)
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

    private fun typeSymbol(name: String): IrTypeSymbol {
        return typeSymbols.getOrPut(name) {
            IrTypeSymbol(IrSymbolId(1_000_000 + nextTypeSymbolId++), name)
        }
    }
}
