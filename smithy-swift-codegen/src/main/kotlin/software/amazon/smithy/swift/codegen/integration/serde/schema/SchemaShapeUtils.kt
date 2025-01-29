package software.amazon.smithy.swift.codegen.integration.serde.schema

import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyReadWriteTypes
import kotlin.jvm.optionals.getOrNull

fun Shape.schemaVar(writer: SwiftWriter): String {
    return if (this.id.namespace == "smithy.api") {
        this.id.preludeSchemaVarName(writer)
    } else {
        this.id.schemaVarName()
    }
}

private fun ShapeId.preludeSchemaVarName(writer: SwiftWriter): String {
    return when (this.name) {
        "Unit" -> writer.format("\$N", SmithyReadWriteTypes.unitSchema)
        "String" -> writer.format("\$N", SmithyReadWriteTypes.stringSchema)
        "Blob" -> writer.format("\$N", SmithyReadWriteTypes.blobSchema)
        "Integer" -> writer.format("\$N", SmithyReadWriteTypes.integerSchema)
        "Timestamp" -> writer.format("\$N", SmithyReadWriteTypes.timestampSchema)
        "Boolean" -> writer.format("\$N", SmithyReadWriteTypes.booleanSchema)
        "Float" -> writer.format("\$N", SmithyReadWriteTypes.floatSchema)
        "Double" -> writer.format("\$N", SmithyReadWriteTypes.doubleSchema)
        "Long" -> writer.format("\$N", SmithyReadWriteTypes.longSchema)
        "Short" -> writer.format("\$N", SmithyReadWriteTypes.shortSchema)
        "Byte" -> writer.format("\$N", SmithyReadWriteTypes.byteSchema)
        "PrimitiveInteger" -> writer.format("\$N", SmithyReadWriteTypes.primitiveIntegerSchema)
        "PrimitiveBoolean" -> writer.format("\$N", SmithyReadWriteTypes.primitiveBooleanSchema)
        "PrimitiveFloat" -> writer.format("\$N", SmithyReadWriteTypes.primitiveFloatSchema)
        "PrimitiveDouble" -> writer.format("\$N", SmithyReadWriteTypes.primitiveDoubleSchema)
        "PrimitiveLong" -> writer.format("\$N", SmithyReadWriteTypes.primitiveLongSchema)
        "PrimitiveShort" -> writer.format("\$N", SmithyReadWriteTypes.primitiveShortSchema)
        "PrimitiveByte" -> writer.format("\$N", SmithyReadWriteTypes.primitiveByteSchema)
        "Document" -> writer.format("\$N", SmithyReadWriteTypes.documentSchema)
        else -> throw Exception("Unhandled prelude type converted to schemaVar: ${this.name}")
    }
}

private fun ShapeId.schemaVarName(): String {
    val namespacePortion = this.namespace.replace(".", "_")
    val namePortion = this.name
    val memberPortion = this.member.getOrNull()?.let { "__member_$it" } ?: ""
    return "schema__namespace_${namespacePortion}__name_${namePortion}$memberPortion"
}

val Shape.readMethodName: String
    get() = when (type) {
        ShapeType.BLOB -> "readBlob"
        ShapeType.BOOLEAN -> "readBoolean"
        ShapeType.STRING -> "readEnum".takeIf { hasTrait<EnumTrait>() } ?: "readString"
        ShapeType.ENUM -> "readEnum"
        ShapeType.TIMESTAMP -> "readTimestamp"
        ShapeType.BYTE -> "readByte"
        ShapeType.SHORT -> "readShort"
        ShapeType.INTEGER -> "readInteger"
        ShapeType.INT_ENUM -> "readIntEnum"
        ShapeType.LONG -> "readLong"
        ShapeType.FLOAT -> "readFloat"
        ShapeType.DOCUMENT -> "readDocument"
        ShapeType.DOUBLE -> "readDouble"
        ShapeType.BIG_DECIMAL -> "readBigDecimal"
        ShapeType.BIG_INTEGER -> "readBigInteger"
        ShapeType.LIST, ShapeType.SET -> "readList"
        ShapeType.MAP -> "readMap"
        ShapeType.STRUCTURE, ShapeType.UNION -> "readStructure"
        ShapeType.MEMBER, ShapeType.SERVICE, ShapeType.RESOURCE, ShapeType.OPERATION, null ->
            throw Exception("Unsupported member target type: $type")
    }
