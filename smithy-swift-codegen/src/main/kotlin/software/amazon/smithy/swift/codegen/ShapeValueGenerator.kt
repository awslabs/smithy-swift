/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.BooleanNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NodeVisitor
import software.amazon.smithy.model.node.NullNode
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.DoubleShape
import software.amazon.smithy.model.shapes.FloatShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.toMemberNames
import software.amazon.smithy.swift.codegen.swiftmodules.FoundationTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyStreamsTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTimestampsTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTypes
import software.amazon.smithy.swift.codegen.utils.toLowerCamelCase
import software.amazon.smithy.utils.StringUtils.lowerCase

/**
 * Generates a shape type declaration based on the parameters provided.
 */
class ShapeValueGenerator(
    internal val model: Model,
    internal val symbolProvider: SymbolProvider,
) {

    /**
     * Writes generation of a shape value type declaration for the given the parameters.
     *
     * @param writer writer to write generated code with.
     * @param shape the shape that will be declared.
     * @param params parameters to fill the generated shape declaration.
     */
    fun writeShapeValueInline(writer: SwiftWriter, shape: Shape, params: Node) {
        val nodeVisitor = ShapeValueNodeVisitor(writer, this, shape)

        when (shape.type) {
            ShapeType.STRUCTURE -> structDecl(writer, shape.asStructureShape().get()) {
                params.accept(nodeVisitor)
            }
            ShapeType.MAP -> mapDecl(writer) {
                params.accept(nodeVisitor)
            }
            ShapeType.LIST -> listDecl(writer, shape as CollectionShape) {
                params.accept(nodeVisitor)
            }
            ShapeType.SET -> unorderedSetDecl(writer, shape as CollectionShape, params) {
                params.accept(nodeVisitor)
            }
            ShapeType.UNION -> unionDecl(writer, shape.asUnionShape().get()) {
                params.accept(nodeVisitor)
            }
            ShapeType.DOCUMENT -> documentDecl(writer, params)
            else -> primitiveDecl(writer, shape) {
                params.accept(nodeVisitor)
            }
        }
    }

    private fun structDecl(writer: SwiftWriter, shape: StructureShape, block: () -> Unit) {
        var symbol = symbolProvider.toSymbol(shape)
        writer.writeInline("\$N(", symbol)
            .indent()
            .call { block() }
            .dedent()
            .writeInline("\n)")
    }

    private fun unionDecl(writer: SwiftWriter, shape: UnionShape, block: () -> Unit) {
        val symbol = symbolProvider.toSymbol(shape)
        writer.writeInline("\$N.", symbol).call { block() }
    }

    private fun documentDecl(writer: SwiftWriter, node: Node) {
        writer.openBlock(
            "try \$N.make(from: Data(\"\"\"", "\"\"\".utf8))",
            SmithyTypes.Document,
        ) {
            writer.write(Node.prettyPrintJson(node))
        }
    }

    private fun mapDecl(writer: SwiftWriter, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)
        writer.writeInline("[")
            .indent()
            .call { block() }
            .dedent()
            .write("]")

        writer.popState()
    }

    private fun listDecl(writer: SwiftWriter, shape: CollectionShape, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)

        val targetMemberShape = model.expectShape(shape.member.target)
        writer.writeInline("[")
            .indent()
            .call { block() }
            .dedent()
            .writeInline("\n]")
        writer.popState()
    }

    private fun unorderedSetDecl(writer: SwiftWriter, shape: CollectionShape, params: Node, block: () -> Unit) {
        val currNode = params as ArrayNode
        val isEmpty = currNode.elements.count() == 0

        writer.pushState()
        writer.trimTrailingSpaces(false)

        val targetMemberShape = model.expectShape(shape.member.target)
        val memberSymbol = symbolProvider.toSymbol(targetMemberShape)
        if (!isEmpty) {
            writer.writeInline("Set<\$N>(arrayLiteral: ", memberSymbol)
                .indent()
                .call { block() }
                .dedent()
                .writeInline("\n)")
        } else {
            writer.writeInline("Set<\$N>()", memberSymbol)
        }
        writer.popState()
    }

    private fun primitiveDecl(writer: SwiftWriter, shape: Shape, block: () -> Unit) {
        val suffix = when (shape.type) {
            ShapeType.STRING -> {
                if (shape.hasTrait(EnumTrait::class.java)) {
                    val symbol = symbolProvider.toSymbol(shape)
                    writer.writeInline("\$N(rawValue: ", symbol)
                    ")!"
                } else { "" }
            }
            ShapeType.ENUM -> {
                val symbol = symbolProvider.toSymbol(shape)
                writer.writeInline("\$N(rawValue: ", symbol)
                ")!"
            }
            ShapeType.BLOB -> {
                if (shape.hasTrait<StreamingTrait>()) {
                    writer.writeInline(".stream(\$N(data: \$N(", SmithyStreamsTypes.Core.BufferedStream, FoundationTypes.Data)
                    ".utf8), isClosed: true))"
                } else {
                    // TODO: properly handle this optional with an unwrapped statement before it's passed as a value to a shape.
                    writer.writeInline("\$N(", FoundationTypes.Data)
                    ".utf8)"
                }
            }
            else -> { "" }
        }

        block()

        if (suffix.isNotBlank()) {
            writer.writeInline(suffix)
        }
    }

    /**
     * NodeVisitor to walk shape value declarations with node values.
     */
    private class ShapeValueNodeVisitor(
        val writer: SwiftWriter,
        val generator: ShapeValueGenerator,
        val currShape: Shape
    ) : NodeVisitor<Unit> {

        override fun objectNode(node: ObjectNode) {
            var i = 0
            // this is important because when a struct is generated in swift it is generated with its members sorted by name.
            // when you instantiate that struct you have to call params in order with their param names. if you don't it won't compile
            // so we sort here before we write any of the members with their values
            val sortedMembers = node.members.toSortedMap(compareBy<StringNode> { it.value.toLowerCamelCase() })
            sortedMembers.forEach { (keyNode, valueNode) ->
                val memberShape: Shape
                when (currShape) {
                    is StructureShape -> {
                        val member = currShape.getMember(keyNode.value).orElseThrow {
                            CodegenException("unknown member ${currShape.id}.${keyNode.value}")
                        }
                        memberShape = generator.model.expectShape(member.target)
                        val memberName = generator.symbolProvider.toMemberNames(member).second
                        // NOTE - `write()` appends a newline and keeps indentation,
                        // `writeInline()` doesn't keep indentation but also doesn't append a newline
                        // ...except it does insert indentation if it encounters a newline.
                        // This is our workaround for the moment to keep indentation but not insert
                        // a newline at the end.
                        writer.writeInline("\n\$L: ", memberName)
                        generator.writeShapeValueInline(writer, memberShape, valueNode)
                        if (i < node.members.size - 1) {
                            writer.writeInline(",")
                        }
                    }
                    is MapShape -> {
                        memberShape = generator.model.expectShape(currShape.value.target)
                        writer.writeInline("\n\$S: ", keyNode.value)
                        if (valueNode.isNullNode) {
                            writer.writeInline("nil")
                        } else {
                            generator.writeShapeValueInline(writer, memberShape, valueNode)
                        }
                        if (i < node.members.size - 1) {
                            writer.writeInline(",")
                        }
                    }
                    is UnionShape -> {
                        val member = currShape.getMember(keyNode.value).orElseThrow {
                            CodegenException("unknown member ${currShape.id}.${keyNode.value}")
                        }
                        memberShape = generator.model.expectShape(member.target)
                        if (member.target.toString() != "smithy.api#Unit") {
                            writer.writeInline("\$L(", lowerCase(keyNode.value))
                            generator.writeShapeValueInline(writer, memberShape, valueNode)
                            writer.writeInline(")")
                        } else {
                            writer.writeInline("\$L", lowerCase(keyNode.value))
                        }
                    }
                    else -> throw CodegenException("unexpected shape type " + currShape.type)
                }
                i++
            }
            if (sortedMembers.isEmpty()) {
                when (currShape) {
                    is MapShape -> writer.writeInline(":") // to pass an empty map you need to have a colon like `[:]`
                }
            }
        }

        override fun stringNode(node: StringNode) {
            when (currShape) {
                is TimestampShape -> {
                    val value = node.expectStringNode().value
                    writer.writeInline(
                        "\$N(format: .dateTime).date(from: \$S)",
                        SmithyTimestampsTypes.TimestampFormatter,
                        value
                    )
                }
                is DoubleShape, is FloatShape -> {
                    val symbol = generator.symbolProvider.toSymbol(currShape)
                    val value = when (node.value.toString()) {
                        "NaN" -> {
                            "$symbol.nan"
                        }
                        "-Infinity", "Infinity" -> {
                            val isNegative = if (node.value.toString() == "-Infinity") "-" else ""
                            "$isNegative$symbol.infinity"
                        }
                        else -> "${node.value}"
                    }
                    writer.writeInline("\$L", value)
                }
                else -> writer.writeInline("\$S", node.value)
            }
        }

        override fun nullNode(node: NullNode) {
            writer.writeInline("nil")
        }

        override fun arrayNode(node: ArrayNode) {
            val memberShape = generator.model.expectShape((currShape as CollectionShape).member.target)
            var i = 0
            node.elements.forEach { element ->
                writer.writeInline("\n")
                generator.writeShapeValueInline(writer, memberShape, element)
                if (i < node.elements.size - 1) {
                    writer.writeInline(",")
                }
                i++
            }
        }

        override fun numberNode(node: NumberNode) {
            when (currShape.type) {
                ShapeType.TIMESTAMP -> {
                    writer.writeInline("Date(timeIntervalSince1970: \$L)", node.value)
                }

                ShapeType.INT_ENUM -> {
                    val enumSymbol = generator.symbolProvider.toSymbol(currShape)
                    writer.writeInline(
                        "\$L(rawValue: \$L)",
                        enumSymbol, node.value
                    )
                }

                ShapeType.BYTE, ShapeType.SHORT, ShapeType.INTEGER,
                ShapeType.LONG, ShapeType.DOUBLE, ShapeType.FLOAT,
                ShapeType.BIG_INTEGER, ShapeType.BIG_DECIMAL -> writer.writeInline("\$L", node.value)

                else -> throw CodegenException("unexpected shape type $currShape for numberNode")
            }
        }

        override fun booleanNode(node: BooleanNode) {
            if (currShape.type != ShapeType.BOOLEAN) {
                throw CodegenException("unexpected shape type $currShape for boolean value")
            }

            writer.writeInline("\$L", if (node.value) "true" else "false")
        }
    }
}
