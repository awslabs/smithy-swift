/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen.integration.serde.json

import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.XmlFlattenedTrait
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.AWSProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.NodeInfoUtils
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.WireProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.WritingClosureUtils
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.awsProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.requestWireProtocol
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyReadWriteTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTimestampsTypes

abstract class MemberShapeEncodeGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: SwiftWriter,
) {

    abstract fun render()

    private val writingClosureUtils = WritingClosureUtils(ctx, writer)

    private val nodeInfoUtils = NodeInfoUtils(ctx, writer, ctx.service.requestWireProtocol)

    fun writeMember(memberShape: MemberShape, unionMember: Boolean, errorMember: Boolean) {
        if (unionMember && memberShape.target.toString() == "smithy.api#Unit") {
            writeUnitMember(memberShape)
            return
        }
        val prefix1 = "value.".takeIf { !unionMember } ?: ""
        val prefix2 = "properties.".takeIf { errorMember } ?: ""
        val prefix = prefix1 + prefix2
        val targetShape = ctx.model.expectShape(memberShape.target)
        val isSparse = targetShape.hasTrait<SparseTrait>()
        when (targetShape) {
            is StructureShape, is UnionShape -> {
                writeStructureOrUnionMember(memberShape, prefix)
            }
            is ListShape -> {
                writeListMember(memberShape, targetShape, prefix, isSparse)
            }
            is MapShape -> {
                writeMapMember(memberShape, targetShape, prefix, isSparse)
            }
            is TimestampShape -> {
                writeTimestampMember(memberShape, targetShape, prefix)
            }
            else -> {
                writePropertyMember(memberShape, prefix)
            }
        }
    }

    private fun writeUnitMember(memberShape: MemberShape) {
        val propertyKey = nodeInfoUtils.nodeInfo(memberShape)
        writer.write(
            "try writer[\$L].write(\$N(), with: \$N.write(value:to:))",
            propertyKey,
            SmithyReadWriteTypes.Unit,
            SmithyReadWriteTypes.Unit,
        )
    }

    private fun writeStructureOrUnionMember(memberShape: MemberShape, prefix: String) {
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val propertyKey = nodeInfoUtils.nodeInfo(memberShape)
        val writingClosure = writingClosureUtils.writingClosure(memberShape)
        writer.write(
            "try writer[\$L].write(\$L\$L, with: \$L)",
            propertyKey,
            prefix,
            memberName,
            writingClosure
        )
    }

    private fun writeTimestampMember(memberShape: MemberShape, timestampShape: TimestampShape, prefix: String) {
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        val timestampKey = nodeInfoUtils.nodeInfo(memberShape)
        val memberTimestampFormatTrait = memberShape.getTrait<TimestampFormatTrait>()
        val swiftTimestampFormatCase = TimestampUtils.timestampFormat(ctx, memberTimestampFormatTrait, timestampShape)
        writer.write(
            "try writer[\$L].writeTimestamp(\$L\$L, format: \$N\$L)",
            timestampKey,
            prefix,
            memberName,
            SmithyTimestampsTypes.TimestampFormat,
            swiftTimestampFormatCase,
        )
    }

    private fun writePropertyMember(memberShape: MemberShape, prefix: String) {
        val propertyNodeInfo = nodeInfoUtils.nodeInfo(memberShape)
        val memberName = ctx.symbolProvider.toMemberName(memberShape)
        writer.write(
            "try writer[\$L].write(\$L\$L)",
            propertyNodeInfo,
            prefix,
            memberName
        )
    }

    private fun writeListMember(member: MemberShape, listShape: ListShape, prefix: String, isSparse: Boolean) {
        val memberName = ctx.symbolProvider.toMemberName(member)
        val listMemberWriter = writingClosureUtils.writingClosure(listShape.member, isSparse)
        val listKey = nodeInfoUtils.nodeInfo(member)
        val isFlattened = member.hasTrait<XmlFlattenedTrait>() || ctx.service.awsProtocol == AWSProtocol.EC2_QUERY
        val memberNodeInfo = nodeInfoUtils.nodeInfo(listShape.member)
        // AWS Query protocol keeps a list member in the request even if it is empty, e.g., List=&Version=2020-01-08
        // EC2 Query protocol leaves out a list member from the request if it is empty, e.g., Version=2020-01-08
        if (ctx.service.awsProtocol == AWSProtocol.EC2_QUERY) {
            writer.write("if !(\$L\$L?.isEmpty ?? true) {", prefix, memberName)
            writer.indent()
        }
        writer.write(
            "try writer[\$L].writeList(\$L\$L, memberWritingClosure: \$L, memberNodeInfo: \$L, isFlattened: \$L)",
            listKey,
            prefix,
            memberName,
            listMemberWriter,
            memberNodeInfo,
            isFlattened
        )
        if (ctx.service.awsProtocol == AWSProtocol.EC2_QUERY) {
            writer.dedent()
            writer.write("}")
        }
    }

    private fun writeMapMember(member: MemberShape, mapShape: MapShape, prefix: String, isSparse: Boolean) {
        val memberName = ctx.symbolProvider.toMemberName(member)
        val mapKey = nodeInfoUtils.nodeInfo(member)
        val keyNodeInfo = nodeInfoUtils.nodeInfo(mapShape.key)
        val valueNodeInfo = nodeInfoUtils.nodeInfo(mapShape.value)
        val valueWriter = writingClosureUtils.writingClosure(mapShape.value, isSparse)
        val isFlattened = member.hasTrait<XmlFlattenedTrait>()
        writer.write(
            "try writer[\$L].writeMap(\$L\$L, valueWritingClosure: \$L, keyNodeInfo: \$L, valueNodeInfo: \$L, isFlattened: \$L)",
            mapKey,
            prefix,
            memberName,
            valueWriter,
            keyNodeInfo,
            valueNodeInfo,
            isFlattened
        )
    }
}

object TimestampUtils {

    fun timestampFormat(ctx: ProtocolGenerator.GenerationContext, memberTimestampFormatTrait: TimestampFormatTrait?, timestampShape: TimestampShape): String {
        // CBOR wire protocol ignores TimestampFormatTrait
        if (ctx.service.requestWireProtocol == WireProtocol.CBOR) {
            return ".epochSeconds"
        }

        // Resolve TimestampFormatTrait normally
        val timestampFormat = memberTimestampFormatTrait?.value ?: timestampShape.getTrait<TimestampFormatTrait>()?.value ?: defaultTimestampFormat(ctx)
        return when (timestampFormat) {
            TimestampFormatTrait.EPOCH_SECONDS -> ".epochSeconds"
            TimestampFormatTrait.HTTP_DATE -> ".httpDate"
            else -> ".dateTime"
        }
    }

    private fun defaultTimestampFormat(ctx: ProtocolGenerator.GenerationContext): String {
        return when (ctx.service.requestWireProtocol) {
            WireProtocol.XML, WireProtocol.FORM_URL -> TimestampFormatTrait.DATE_TIME
            WireProtocol.JSON, WireProtocol.CBOR -> TimestampFormatTrait.EPOCH_SECONDS
        }
    }
}
