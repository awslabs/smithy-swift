package software.amazon.smithy.swift.codegen.swiftmodules

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.SwiftDeclaration
import software.amazon.smithy.swift.codegen.SwiftDependency

/**
 * Commonly used runtime types. Provides a single definition of a runtime symbol such that codegen isn't littered
 * with inline symbol creation which makes refactoring of the runtime more difficult and error prone.
 *
 * NOTE: Not all symbols need be added here but it doesn't hurt to define runtime symbols once.
 */
object SmithyTypes {
    val ByteStream = runtimeSymbol("ByteStream", SwiftDeclaration.ENUM)
    val Attributes = runtimeSymbol("Attributes", SwiftDeclaration.STRUCT)
    val AttributeKey = runtimeSymbol("AttributeKey", SwiftDeclaration.STRUCT)
    val ClientError = runtimeSymbol("ClientError", SwiftDeclaration.ENUM)
    val Context = runtimeSymbol("Context", SwiftDeclaration.CLASS)
    val ContextBuilder = runtimeSymbol("ContextBuilder", SwiftDeclaration.CLASS)
    val Document = runtimeSymbol("Document", SwiftDeclaration.STRUCT)
    val LogAgent = runtimeSymbol("LogAgent", SwiftDeclaration.PROTOCOL)
    val RequestMessageSerializer = runtimeSymbol("RequestMessageSerializer", SwiftDeclaration.PROTOCOL)
    val URIQueryItem = runtimeSymbol("URIQueryItem", SwiftDeclaration.STRUCT)
    val BigDecimalDocument = runtimeSymbol("BigDecimalDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val BigIntegerDocument = runtimeSymbol("BigIntegerDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val BlobDocument = runtimeSymbol("BlobDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val BooleanDocument = runtimeSymbol("BooleanDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val ByteDocument = runtimeSymbol("ByteDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val DoubleDocument = runtimeSymbol("DoubleDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val FloatDocument = runtimeSymbol("FloatDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val IntegerDocument = runtimeSymbol("IntegerDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val ListDocument = runtimeSymbol("ListDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val LongDocument = runtimeSymbol("LongDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val NullDocument = runtimeSymbol("NullDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val ShortDocument = runtimeSymbol("ShortDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val StringDocument = runtimeSymbol("StringDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val StringMapDocument = runtimeSymbol("StringMapDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
    val TimestampDocument = runtimeSymbol("TimestampDocument", SwiftDeclaration.STRUCT, listOf(), listOf("SmithyDocumentImpl"))
}

private fun runtimeSymbol(
    name: String,
    declaration: SwiftDeclaration?,
    additionalImports: List<Symbol> = emptyList(),
    spiName: List<String> = emptyList(),
): Symbol = SwiftSymbol.make(
    name,
    declaration,
    SwiftDependency.SMITHY.takeIf { additionalImports.isEmpty() },
    additionalImports,
    spiName,
)
