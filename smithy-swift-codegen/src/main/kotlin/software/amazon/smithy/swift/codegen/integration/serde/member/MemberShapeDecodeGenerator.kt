/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.integration.serde.member

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.BigDecimalShape
import software.amazon.smithy.model.shapes.BigIntegerShape
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.BooleanShape
import software.amazon.smithy.model.shapes.ByteShape
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
import software.amazon.smithy.model.shapes.SetShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShortShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.DefaultTrait
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.EnumValueTrait
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.XmlFlattenedTrait
import software.amazon.smithy.swift.codegen.SwiftDependency
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.serde.json.TimestampUtils
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.NodeInfoUtils
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.ReadingClosureUtils
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.WireProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.responseWireProtocol
import software.amazon.smithy.swift.codegen.integration.serde.schema.readMethodName
import software.amazon.smithy.swift.codegen.integration.serde.schema.schemaVar
import software.amazon.smithy.swift.codegen.model.expectTrait
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.isError
import software.amazon.smithy.swift.codegen.swiftEnumCaseName
import software.amazon.smithy.swift.codegen.swiftmodules.FoundationTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTimestampsTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTypes

open class MemberShapeDecodeGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: SwiftWriter,
    private val shapeContainingMembers: Shape,
) {
    private val nodeInfoUtils = NodeInfoUtils(ctx, writer, ctx.service.responseWireProtocol)
    private val readingClosureUtils = ReadingClosureUtils(ctx, writer)

    fun render(member: MemberShape, isPayload: Boolean = false) {
        val targetShape = ctx.model.expectShape(member.target)
        val readExp = when (targetShape) {
            is StructureShape, is UnionShape -> renderStructOrUnionExp(member, isPayload)
            is MapShape -> renderMapExp(member, targetShape)
            is ListShape -> renderListExp(member, targetShape)
            is TimestampShape -> renderTimestampExp(member, targetShape)
            else -> renderMemberExp(member, isPayload)
        }
        val memberName = ctx.symbolProvider.toMemberName(member)
        if (decodingUnion && member.target.toString() != "smithy.api#Unit") {
            writer.write("return .\$L(\$L)", memberName, readExp)
        } else if (decodingUnion) {
            writer.write("return .\$L", memberName)
        } else if (shapeContainingMembers.isError) {
            writer.write("value.properties.\$L = \$L", memberName, readExp)
        } else {
            writer.write("value.\$L = \$L", memberName, readExp)
        }
    }

    private fun renderStructOrUnionExp(memberShape: MemberShape, isPayload: Boolean): String {
        if (useSBS) {
            val target = ctx.model.expectShape(memberShape.target)
            return writer.format(
                "try \$L.readStructure\$L(schema: \$L)",
                reader(memberShape, isPayload),
                "NonNull".takeIf { decodingUnion || (memberShape.hasTrait<RequiredTrait>() || memberShape.hasTrait<DefaultTrait>() || target.hasTrait<DefaultTrait>()) } ?: "",
                memberShape.schemaVar(writer),
            )
        }
        val readingClosure = readingClosureUtils.readingClosure(memberShape)
        return writer.format(
            "try \$L.\$L(with: \$L)",
            reader(memberShape, isPayload),
            readMethodName("read"),
            readingClosure
        )
    }

    private fun renderListExp(memberShape: MemberShape, listShape: ListShape): String {
        if (useSBS) {
            val target = ctx.model.expectShape(memberShape.target)
            return writer.format(
                "try \$L.readList\$L(schema: \$L)",
                reader(memberShape, false),
                "NonNull".takeIf { decodingUnion || (memberShape.hasTrait<RequiredTrait>() || memberShape.hasTrait<DefaultTrait>() || target.hasTrait<DefaultTrait>()) } ?: "",
                memberShape.schemaVar(writer),
            )
        }
        val isSparse = listShape.hasTrait<SparseTrait>()
        val memberReadingClosure = readingClosureUtils.readingClosure(listShape.member, isSparse)
        val memberNodeInfo = nodeInfoUtils.nodeInfo(listShape.member)
        val isFlattened = memberShape.hasTrait<XmlFlattenedTrait>()
        return writer.format(
            "try \$L.\$L(memberReadingClosure: \$L, memberNodeInfo: \$L, isFlattened: \$L)\$L",
            reader(memberShape, false),
            readMethodName("readList"),
            memberReadingClosure,
            memberNodeInfo,
            isFlattened,
            default(memberShape)
        )
    }

    private fun renderMapExp(memberShape: MemberShape, mapShape: MapShape): String {
        if (useSBS) {
            val target = ctx.model.expectShape(memberShape.target)
            return writer.format(
                "try \$L.readMap\$L(schema: \$L)",
                reader(memberShape, false),
                "NonNull".takeIf { decodingUnion || (memberShape.hasTrait<RequiredTrait>() || memberShape.hasTrait<DefaultTrait>() || target.hasTrait<DefaultTrait>()) } ?: "",
                memberShape.schemaVar(writer),
            )
        }
        val isSparse = mapShape.hasTrait<SparseTrait>()
        val valueReadingClosure = ReadingClosureUtils(ctx, writer).readingClosure(mapShape.value, isSparse)
        val keyNodeInfo = nodeInfoUtils.nodeInfo(mapShape.key)
        val valueNodeInfo = nodeInfoUtils.nodeInfo(mapShape.value)
        val isFlattened = memberShape.hasTrait<XmlFlattenedTrait>()
        return writer.format(
            "try \$L.\$L(valueReadingClosure: \$L, keyNodeInfo: \$L, valueNodeInfo: \$L, isFlattened: \$L)\$L",
            reader(memberShape, false),
            readMethodName("readMap"),
            valueReadingClosure,
            keyNodeInfo,
            valueNodeInfo,
            isFlattened,
            default(memberShape)
        )
    }

    private fun renderTimestampExp(memberShape: MemberShape, timestampShape: TimestampShape): String {
        if (useSBS) {
            return renderMemberExp(memberShape, isPayload = false)
        }
        val memberTimestampFormatTrait = memberShape.getTrait<TimestampFormatTrait>()
        val swiftTimestampFormatCase = TimestampUtils.timestampFormat(ctx, memberTimestampFormatTrait, timestampShape)
        return writer.format(
            "try \$L.\$L(format: \$N\$L)\$L",
            reader(memberShape, false),
            readMethodName("readTimestamp"),
            SmithyTimestampsTypes.TimestampFormat,
            swiftTimestampFormatCase,
            default(memberShape)
        )
    }

    private fun renderMemberExp(memberShape: MemberShape, isPayload: Boolean): String {
        if (useSBS) {
            val target = ctx.model.expectShape(memberShape.target)
            return writer.format(
                "try \$L.\$L\$L(schema: \$L)",
                reader(memberShape, isPayload),
                target.readMethodName,
                "NonNull".takeIf { decodingUnion || (memberShape.hasTrait<RequiredTrait>() || memberShape.hasTrait<DefaultTrait>() || target.hasTrait<DefaultTrait>()) } ?: "",
                memberShape.schemaVar(writer),
            )
        }
        return writer.format(
            "try \$L.\$L()\$L",
            reader(memberShape, isPayload),
            readMethodName("read"),
            default(memberShape),
        )
    }

    private fun readMethodName(baseName: String): String {
        val extension = "".takeIf { decodingUnion } ?: "IfPresent"
        return writer.format("\$L\$L", baseName, extension)
    }

    private fun reader(memberShape: MemberShape, isPayload: Boolean): String {
        val nodeInfo = nodeInfoUtils.nodeInfo(memberShape)
        return "reader".takeIf { isPayload || useSBS } ?: writer.format("reader[\$L]", nodeInfo)
    }

    private fun default(memberShape: MemberShape): String {
        // If decoding the member of a union, then no default is needed.
        if (decodingUnion) { return "" }

        val targetShape = ctx.model.expectShape(memberShape.target)
        val defaultTrait = memberShape.getTrait<DefaultTrait>() ?: targetShape.getTrait<DefaultTrait>()
        val requiredTrait = memberShape.getTrait<RequiredTrait>()
        // If member is required but there isn't a default value, use zero-equivalents for error correction
        if (requiredTrait != null && defaultTrait == null) {
            return when (targetShape) {
                is EnumShape -> " ?? .sdkUnknown(\"\")"
                is IntEnumShape -> " ?? .sdkUnknown(0)"
                is StringShape -> {
                    // Enum trait is deprecated but many services still use it in their models
                    if (targetShape.hasTrait<EnumTrait>()) {
                        " ?? .sdkUnknown(\"\")"
                    } else {
                        " ?? \"\""
                    }
                }
                is ByteShape, is ShortShape, is IntegerShape, is LongShape -> " ?? 0"
                is FloatShape, is DoubleShape -> " ?? 0.0"
                is BooleanShape -> " ?? false"
                is ListShape -> " ?? []"
                is MapShape -> " ?? [:]"
                is TimestampShape -> resolveTimestampDefault(true, requiredTrait.toNode())
                is DocumentShape -> resolveDocumentDefault(true, requiredTrait.toNode())
                is BlobShape -> resolveBlobDefault(targetShape)
                // No default provided for other types
                else -> ""
            }
        }
        return defaultTrait?.toNode()?.let {
            // If the default value is null, provide no default.
            if (it.isNullNode) { return "" }
            // Provide a default value dependent on the type.
            return when (targetShape) {
                is EnumShape -> " ?? .${enumDefaultValue(targetShape, it.expectStringNode().value)}"
                is IntEnumShape -> intEnumDefaultValue(it)
                is StringShape -> " ?? \"${it.expectStringNode().value}\""
                is ByteShape -> " ?? ${it.expectNumberNode().value}"
                is ShortShape -> " ?? ${it.expectNumberNode().value}"
                is IntegerShape -> " ?? ${it.expectNumberNode().value}"
                is LongShape -> " ?? ${it.expectNumberNode().value}"
                is FloatShape -> " ?? ${it.expectNumberNode().value}"
                is DoubleShape -> " ?? ${it.expectNumberNode().value}"
                is BigIntegerShape -> " ?? ${it.expectNumberNode().value}"
                is BigDecimalShape -> " ?? ${it.expectNumberNode().value}"
                is BooleanShape -> " ?? ${it.expectBooleanNode().value}"
                // Lists can only have empty list as default value
                is ListShape, is SetShape -> " ?? []"
                // Maps can only have empty map as default value
                is MapShape -> " ?? [:]"
                is TimestampShape -> resolveTimestampDefault(false, it)
                is DocumentShape -> resolveDocumentDefault(false, it)
                is BlobShape -> resolveBlobDefault(targetShape, it.toString())
                // No default provided for other shapes
                else -> ""
            }
        } ?: "" // If there is no default trait, provide no default value.
    }

    // From the Smithy docs at https://smithy.io/2.0/spec/type-refinement-traits.html#default-value-constraints :
    // > The following shapes have restrictions on their default values:
    // >
    // > enum: can be set to any valid string _value_ of the enum.
    // So, find the member with the default value, then render it as a Swift enum case.
    private fun enumDefaultValue(enumShape: EnumShape, value: String): String {
        val matchingMember = enumShape.members().first { member ->
            value == member.expectTrait<EnumValueTrait>().expectStringValue()
        }
        return swiftEnumCaseName(matchingMember.memberName, value)
    }

    private fun intEnumDefaultValue(node: Node): String {
        return when (node) {
            is StringNode -> " ?? .${node.value}"
            is NumberNode -> " ?? .init(rawValue: ${node.value})"
            else -> ""
        }
    }

    private fun resolveBlobDefault(targetShape: Shape, value: String = ""): String {
        writer.addImport(FoundationTypes.Data)
        return if (targetShape.hasTrait<StreamingTrait>()) {
            writer.format(
                " ?? \$N.data(\$N(base64Encoded: \$S))",
                SmithyTypes.ByteStream,
                FoundationTypes.Data,
                value,
            )
        } else {
            writer.format(
                " ?? \$N(base64Encoded: \$S)",
                FoundationTypes.Data,
                value,
            )
        }
    }

    private fun resolveDocumentDefault(useZeroValue: Boolean, node: Node): String {
        writer.addImport(SwiftDependency.SMITHY_JSON.target)
        return when {
            node.isObjectNode -> writer.format(" ?? [:]")
            node.isArrayNode -> writer.format(" ?? []")
            node.isStringNode -> {
                val resolvedValue = "".takeIf { useZeroValue } ?: node.expectStringNode().value
                writer.format(" ?? \"$resolvedValue\"")
            }
            node.isBooleanNode -> {
                val resolvedValue = "false".takeIf { useZeroValue } ?: node.expectBooleanNode().value
                writer.format(" ?? $resolvedValue")
            }
            node.isNumberNode -> {
                val resolvedValue = "0".takeIf { useZeroValue } ?: node.expectNumberNode().value
                writer.format(" ?? $resolvedValue")
            }
            else -> "" // null node type means no default value but explicit
        }
    }

    private fun resolveTimestampDefault(useZeroValue: Boolean, node: Node): String {
        // Smithy validates that default value given to timestamp shape must either be a
        // number (for epoch-seconds) or a date-time string compliant with RFC3339.
        return if (node.isNumberNode) {
            val value = "0".takeIf { useZeroValue } ?: node.expectNumberNode().value
            writer.format(
                " ?? \$N(timeIntervalSince1970: $value)",
                FoundationTypes.Date
            )
        } else {
            val value = "1970-01-01T00:00:00Z".takeIf { useZeroValue } ?: node.expectStringNode().value
            writer.format(
                " ?? \$N(format: .dateTime).date(from: \"$value\")",
                SmithyTimestampsTypes.TimestampFormatter
            )
        }
    }

    private var decodingUnion: Boolean = shapeContainingMembers.isUnionShape

    private val useSBS: Boolean = ctx.service.responseWireProtocol == WireProtocol.JSON
}
