/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.ReservedWordSymbolProvider
import software.amazon.smithy.codegen.core.ReservedWordSymbolProvider.Escaper
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.BigDecimalShape
import software.amazon.smithy.model.shapes.BigIntegerShape
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.BooleanShape
import software.amazon.smithy.model.shapes.ByteShape
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.DocumentShape
import software.amazon.smithy.model.shapes.DoubleShape
import software.amazon.smithy.model.shapes.EnumShape
import software.amazon.smithy.model.shapes.FloatShape
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.IntegerShape
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.LongShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ResourceShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.SetShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeVisitor
import software.amazon.smithy.model.shapes.ShortShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.ClientOptionalTrait
import software.amazon.smithy.model.traits.DefaultTrait
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.InputTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.swift.codegen.customtraits.NestedTrait
import software.amazon.smithy.swift.codegen.lang.swiftReservedWords
import software.amazon.smithy.swift.codegen.model.SymbolProperty
import software.amazon.smithy.swift.codegen.model.boxed
import software.amazon.smithy.swift.codegen.model.buildSymbol
import software.amazon.smithy.swift.codegen.model.defaultName
import software.amazon.smithy.swift.codegen.model.defaultValue
import software.amazon.smithy.swift.codegen.model.defaultValueClosure
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.nestedNamespaceType
import software.amazon.smithy.swift.codegen.swiftmodules.FoundationTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTimestampsTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes
import software.amazon.smithy.swift.codegen.utils.ModelFileUtils
import software.amazon.smithy.swift.codegen.utils.clientName
import software.amazon.smithy.swift.codegen.utils.toLowerCamelCase
import software.amazon.smithy.utils.StringUtils.lowerCase
import java.util.logging.Logger

private val Shape.isStreaming: Boolean
    get() {
        return this.hasTrait<StreamingTrait>()
    }

class SwiftSymbolProvider(private val model: Model, val swiftSettings: SwiftSettings) :
    SymbolProvider,
    ShapeVisitor<Symbol> {
    private val sdkId = swiftSettings.sdkId
    private val service: ServiceShape? = try { swiftSettings.getService(model) } catch (e: CodegenException) { null }
    private val logger = Logger.getLogger(SwiftSymbolProvider::class.java.name)
    private val escaper: Escaper
    private val nullableIndex: NullableIndex
    // model depth; some shapes use `toSymbol()` internally as they convert (e.g.) member shapes to symbols, this tracks
    // how deep in the model we have recursed
    private var depth = 0

    init {
        val reservedWords = swiftReservedWords
        nullableIndex = NullableIndex.of(model)
        escaper = ReservedWordSymbolProvider
            .builder()
            .nameReservedWords(reservedWords) // Only escape words when the symbol has a definition file to
            .memberReservedWords(reservedWords)
            // prevent escaping intentional references to built-in types.
            .escapePredicate { _, symbol: Symbol -> symbol.definitionFile.isNotEmpty() }
            .buildEscaper()
    }

    override fun toSymbol(shape: Shape): Symbol {
        depth++
        val symbol = shape.accept(this)
        depth--
        this.logger.fine("Creating symbol from $shape: $symbol")
        return escaper.escapeSymbol(shape, symbol)
    }

    override fun toMemberName(shape: MemberShape): String {
        val containingShape = model.expectShape(shape.container)
        if (containingShape is UnionShape) {
            val name = escaper.escapeMemberName(shape.memberName.toLowerCamelCase())
            return if (!name.equals("sdkUnknown")) lowerCase(name) else name
        }
        return escaper.escapeMemberName(shape.memberName.toLowerCamelCase())
    }

    override fun integerShape(shape: IntegerShape): Symbol = numberShape(shape, "Int")

    override fun floatShape(shape: FloatShape): Symbol = numberShape(shape, "Float")

    override fun longShape(shape: LongShape): Symbol = numberShape(shape, "Int")

    override fun doubleShape(shape: DoubleShape): Symbol = numberShape(shape, "Double")

    override fun byteShape(shape: ByteShape): Symbol = numberShape(shape, "Int8")

    override fun shortShape(shape: ShortShape): Symbol = numberShape(shape, "Int16")

    override fun bigIntegerShape(shape: BigIntegerShape): Symbol = numberShape(shape, "Int")

    override fun bigDecimalShape(shape: BigDecimalShape): Symbol = numberShape(shape, "Double")

    override fun stringShape(shape: StringShape): Symbol {
        val enumTrait = shape.getTrait(EnumTrait::class.java)
        if (enumTrait.isPresent) {
            return createEnumSymbol(shape)
        }
        return createSymbolBuilder(shape, "String", namespace = "Swift", SwiftDeclaration.STRUCT, boxed = true).build()
    }

    override fun enumShape(shape: EnumShape): Symbol {
        return createEnumSymbol(shape)
    }

    private fun createEnumSymbol(shape: Shape): Symbol {
        val name = shape.defaultName(service)
        val builder = createSymbolBuilder(shape, name, SwiftDeclaration.ENUM, boxed = true)
            .definitionFile(formatModuleName(name))

        // add a reference to each member symbol
        if (shape is UnionShape) {
            addDeclareMemberReferences(builder, shape.allMembers.values)
        }

        if (shape.hasTrait<NestedTrait>() && service != null) {
            builder.namespace(service.nestedNamespaceType(this).name, ".")
        }
        return builder.build()
    }

    override fun booleanShape(shape: BooleanShape): Symbol {
        return createSymbolBuilder(shape, "Bool", namespace = "Swift", SwiftDeclaration.STRUCT).build()
    }

    override fun structureShape(shape: StructureShape): Symbol {
        val name = shape.defaultName(service)
        val builder = createSymbolBuilder(shape, name, SwiftDeclaration.STRUCT, boxed = true)
            .definitionFile(formatModuleName(name))

        // add a reference to each member symbol
        addDeclareMemberReferences(builder, shape.allMembers.values)

        if (shape.hasTrait<NestedTrait>() && service != null && !shape.hasTrait<ErrorTrait>()) {
            builder.namespace(service.nestedNamespaceType(this).name, ".")
        }
        if (shape.id.namespace == "smithy.api") {
            when (shape.id.name) {
                "Unit" -> {
                    builder.addDependency(SwiftDependency.SMITHY_READ_WRITE)
                    builder.namespace(SwiftDependency.SMITHY_READ_WRITE.target, ".")
                    builder.name("Unit")
                    builder.putProperty("spiNames", listOf("SmithyReadWrite"))
                }
                else -> throw Exception("Unhandled prelude type converted to Symbol")
            }
        }
        return builder.build()
    }

    override fun listShape(shape: ListShape): Symbol {
        val reference = toSymbol(shape.member)
        val referenceTypeName = if (shape.hasTrait<SparseTrait>()) "$reference?" else "$reference"
        return createSymbolBuilder(shape, "[$referenceTypeName]", SwiftDeclaration.STRUCT, true).addReference(reference).build()
    }

    override fun mapShape(shape: MapShape): Symbol {
        val reference = toSymbol(shape.value)
        val referenceTypeName = if (shape.hasTrait<SparseTrait>()) "$reference?" else "$reference"
        return createSymbolBuilder(shape, "[${SwiftTypes.String}: $referenceTypeName]", SwiftDeclaration.STRUCT, true)
            .addReference(reference)
            .putProperty(SymbolProperty.ENTRY_EXPRESSION, "(String, $referenceTypeName)")
            .build()
    }

    override fun setShape(shape: SetShape): Symbol {
        val reference = toSymbol(shape.member)
        return createSymbolBuilder(shape, "Set<$reference>", "Swift", SwiftDeclaration.STRUCT, true).addReference(reference)
            .build()
    }

    override fun resourceShape(shape: ResourceShape): Symbol {
        // May implement a resource type in future
        return createSymbolBuilder(shape, "Any", SwiftDeclaration.PROTOCOL, true).build()
    }

    override fun memberShape(shape: MemberShape): Symbol {
        val targetShape = model.getShape(shape.target).orElseThrow { CodegenException("Shape not found: ${shape.target}") }
        var symbol = toSymbol(targetShape)
        if (nullableIndex.isMemberNullable(shape, NullableIndex.CheckMode.CLIENT_ZERO_VALUE_V1)) {
            symbol = symbol.toBuilder().boxed().build()
        }

        val isEventStream = targetShape.isStreaming && targetShape.isUnionShape
        if (isEventStream) {
            return createSymbolBuilder(shape, "AsyncThrowingStream<${symbol.fullName}, Swift.Error>", SwiftDeclaration.STRUCT, true)
                .putProperty(SymbolProperty.NESTED_SYMBOL, symbol)
                .build()
        }
        return handleDefaultValue(shape, symbol.toBuilder()).build()
    }

    override fun timestampShape(shape: TimestampShape): Symbol {
        return createSymbolBuilder(shape, "Date", "Foundation", SwiftDeclaration.STRUCT, true)
            .build()
    }

    override fun unionShape(shape: UnionShape): Symbol {
        return createEnumSymbol(shape)
    }

    override fun operationShape(shape: OperationShape): Symbol {
        // The Swift SDK does not produce code explicitly based on Operations, returning an empty symbol
        return buildSymbol { }
    }

    override fun blobShape(shape: BlobShape): Symbol {
        if (shape.hasTrait<StreamingTrait>()) {
            return createSymbolBuilder(shape, "ByteStream", "Smithy", SwiftDeclaration.ENUM, true).build()
        } else {
            return createSymbolBuilder(shape, "Data", "Foundation", SwiftDeclaration.STRUCT, true).build()
        }
    }

    override fun documentShape(shape: DocumentShape): Symbol {
        return createSymbolBuilder(shape, "Document", "Smithy", SwiftDeclaration.STRUCT, true)
            .addDependency(SwiftDependency.SMITHY)
            .build()
    }

    override fun serviceShape(shape: ServiceShape): Symbol {
        val name = sdkId.clientName()
        return createSymbolBuilder(shape, "${name}Client", SwiftDeclaration.CLASS)
            .definitionFile(formatModuleName(name))
            .build()
    }

    private fun numberShape(shape: Shape, typeName: String): Symbol {
        if (shape.isIntEnumShape()) {
            return createEnumSymbol(shape)
        }
        return createSymbolBuilder(shape, typeName, "Swift", SwiftDeclaration.STRUCT).build()
    }

    /**
     * Creates a symbol builder for the shape with the given type name in the root namespace.
     */
    private fun createSymbolBuilder(
        shape: Shape,
        typeName: String,
        declaration: SwiftDeclaration,
        boxed: Boolean = false
    ): Symbol.Builder {
        val builder = Symbol.builder()
            .putProperty("shape", shape)
            .putProperty("decl", declaration.keyword)
            .name(typeName)
        if (boxed) {
            builder.boxed()
        }
        return handleDefaultValue(shape, builder)
    }

    /**
     * Creates a symbol builder for the shape with the given type name in a child namespace relative
     * to the root namespace e.g. `relativeNamespace = bar` with a root namespace of `foo` would set
     * the namespace (and ultimately the package name) to `foo.bar` for the symbol.
     */
    private fun createSymbolBuilder(
        shape: Shape,
        typeName: String,
        namespace: String,
        declaration: SwiftDeclaration,
        boxed: Boolean = false
    ): Symbol.Builder {
        return createSymbolBuilder(shape, typeName, declaration, boxed)
            .namespace(namespace, ".")
    }

    private fun formatModuleName(name: String): String {
        return ModelFileUtils.filename(swiftSettings, name)
    }

    /**
     * Resolve default value for a given shape and save it as a property in symbol builder if needed.
     *
     * The default trait can be applied to list shape, map shape, and all simple types as per Smithy spec.
     * Both the member shape and the target shape may have the default trait.
     *
     * There exist default value restrictions for the following shapes:
     *  - enum: can be set to any valid string value of the enum.
     *  - intEnum: can be set to any valid integer value of the enum.
     *  - document: can be set to null, true, false, string, numbers, an empty list, or an empty map.
     *  - list: can only be set to an empty list.
     *  - map: can only be set to an empty map.
     */
    private fun handleDefaultValue(shape: Shape, builder: Symbol.Builder): Symbol.Builder {
        // Skip if the current shape is a member shape with @clientOptional trait
        if (shape.hasTrait<ClientOptionalTrait>()) return builder
        // Skip if the current shape doesn't have default trait. Otherwise, get the default value as literal string
        val defaultValueLiteral = shape.getTrait<DefaultTrait>()?.toNode()?.toString() ?: return builder
        // If default value is "null", it is explicit notation for no default value. Return unmodified builder.
        if (defaultValueLiteral == "null") return builder

        // The current shape may be a member shape or a root level shape.
        val targetShape = when (shape) {
            is MemberShape -> {
                // If containing shape is an input shape, return unmodified builder.
                if (model.expectShape(shape.container).hasTrait<InputTrait>()) return builder
                model.expectShape(shape.target)
            }
            else -> shape
        }
        val node = shape.getTrait<DefaultTrait>()!!.toNode()

        return when (targetShape) {
            is ListShape -> builder.defaultValue("[]")
            is EnumShape -> builder.defaultValue(".${swiftEnumCaseName(null, defaultValueLiteral)}")
            is IntEnumShape -> {
                // Get the corresponding enum member name (enum case name) for the int value from default trait
                val enumMemberName = targetShape.enumValues.entries.firstOrNull {
                    it.value == defaultValueLiteral.toInt()
                }!!.key
                builder.defaultValue(".${swiftEnumCaseName(enumMemberName, defaultValueLiteral)}")
            }
            is StringShape -> builder.defaultValue("\"$defaultValueLiteral\"")
            is MapShape -> builder.defaultValue("[:]")
            is BlobShape -> handleBlobDefaultValue(defaultValueLiteral, targetShape, builder)
            is DocumentShape -> {
                handleDocumentDefaultValue(defaultValueLiteral, node, builder)
            }
            is TimestampShape -> handleTimestampDefaultValue(defaultValueLiteral, node, builder)
            is FloatShape, is DoubleShape -> {
                val decimal = ".0".takeIf { !defaultValueLiteral.contains(".") } ?: ""
                builder.defaultValue(defaultValueLiteral + decimal)
            }
            // For: boolean, byte, short, integer, long, bigInteger, bigDecimal,
            //      just take the literal string value from the trait.
            else -> builder.defaultValue(defaultValueLiteral)
        }
    }

    // Document: default value can be set to null, true, false, string, numbers, an empty list, or an empty map.
    private fun handleDocumentDefaultValue(literal: String, node: Node, builder: Symbol.Builder): Symbol.Builder {
        var formatString = when {
            node.isObjectNode -> "[:]"
            node.isArrayNode -> "[]"
            node.isBooleanNode -> literal
            node.isStringNode -> "\"$literal\""
            node.isNumberNode -> literal
            else -> return builder // no-op
        }
        return builder.defaultValueClosure { writer ->
            writer.addImport(SwiftDependency.SMITHY_JSON.target)
            writer.format(formatString)
        }
    }

    private fun handleBlobDefaultValue(literal: String, shape: Shape, builder: Symbol.Builder): Symbol.Builder {
        return builder.defaultValueClosure(
            if (shape.hasTrait<StreamingTrait>()) {
                { writer ->
                    writer.format(
                        "\$N.data(\$N(base64Encoded: \"$literal\"))",
                        SmithyTypes.ByteStream,
                        FoundationTypes.Data
                    )
                }
            } else {
                { writer ->
                    writer.format("\$N(base64Encoded: \"$literal\")", FoundationTypes.Data)
                }
            }
        )
    }

    private fun handleTimestampDefaultValue(literal: String, node: Node, builder: Symbol.Builder): Symbol.Builder {
        // Smithy validates that default value given to timestamp shape must either be a
        // number (for epoch-seconds) or a date-time string compliant with RFC3339.
        return builder.defaultValueClosure(
            if (node.isNumberNode) {
                { writer ->
                    writer.format("\$N(timeIntervalSince1970: $literal)", FoundationTypes.Date)
                }
            } else {
                { writer ->
                    writer.format(
                        "\$N(format: .dateTime).date(from: \"$literal\")",
                        SmithyTimestampsTypes.TimestampFormatter
                    )
                }
            }
        )
    }

    /**
     * Add all the [members] as references needed to declare the given symbol being built.
     */
    private fun addDeclareMemberReferences(builder: Symbol.Builder, members: Collection<MemberShape>) {
        // when converting a shape to a symbol we only need references to top level members
        // in order to declare the symbol. This prevents recursive shapes from causing a stack overflow (and doing
        // unnecessary work since we don't need the inner references)
        if (depth > 1) return
        members.forEach {
            val memberSymbol = toSymbol(it)
            val ref = SymbolReference.builder()
                .symbol(memberSymbol)
                .options(SymbolReference.ContextOption.DECLARE)
                .build()
            builder.addReference(ref)

            val targetShape = model.expectShape(it.target)
            if (targetShape is CollectionShape) {
                val targetSymbol = toSymbol(targetShape)
                targetSymbol.references.forEach { builder.addReference(it) }
            }
        }
    }

    companion object {
        /**
         * Check if a given string can be a valid swift identifier.
         * Valid swift identifier has only alphanumerics and underscore and does not start with a number
         */
        fun isValidSwiftIdentifier(value: String): Boolean {
            return !value.contains(Regex("[^a-zA-Z0-9_]")) &&
                !Character.isDigit(value.first())
        }

        fun escapeReservedWords(word: String): String = "`$word`"
    }
}
