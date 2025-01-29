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
object SmithyReadWriteTypes {
    val SmithyReader = runtimeSymbol("SmithyReader", SwiftDeclaration.PROTOCOL)
    val SmithyWriter = runtimeSymbol("SmithyWriter", SwiftDeclaration.PROTOCOL)
    val ReaderError = runtimeSymbol("ReaderError", SwiftDeclaration.ENUM, emptyList())
    val WriterError = runtimeSymbol("WriterError", SwiftDeclaration.ENUM, emptyList())
    val mapWritingClosure = runtimeSymbol("mapWritingClosure", SwiftDeclaration.FUNC)
    val listWritingClosure = runtimeSymbol("listWritingClosure", SwiftDeclaration.FUNC)
    val timestampWritingClosure = runtimeSymbol("timestampWritingClosure", SwiftDeclaration.FUNC)
    val mapReadingClosure = runtimeSymbol("mapReadingClosure", SwiftDeclaration.FUNC)
    val listReadingClosure = runtimeSymbol("listReadingClosure", SwiftDeclaration.FUNC)
    val timestampReadingClosure = runtimeSymbol("timestampReadingClosure", SwiftDeclaration.FUNC)
    val sparseFormOf = runtimeSymbol("sparseFormOf", SwiftDeclaration.FUNC)
    val optionalFormOf = runtimeSymbol("optionalFormOf", SwiftDeclaration.FUNC)
    val ReadingClosures = runtimeSymbol("ReadingClosures", SwiftDeclaration.ENUM)
    val WritingClosures = runtimeSymbol("WritingClosures", SwiftDeclaration.ENUM)
    val ReadingClosureBox = runtimeSymbol("ReadingClosureBox", SwiftDeclaration.STRUCT)
    val WritingClosureBox = runtimeSymbol("WritingClosureBox", SwiftDeclaration.STRUCT)
    val Schema = runtimeSymbol("Schema", SwiftDeclaration.STRUCT)
    val ShapeDeserializer = runtimeSymbol("ShapeDeserializer", SwiftDeclaration.PROTOCOL)
    val Unit = runtimeSymbol("Unit", SwiftDeclaration.STRUCT)
    val unitSchema = runtimeSymbol("unitSchema", SwiftDeclaration.VAR)
    val stringSchema = runtimeSymbol("stringSchema", SwiftDeclaration.VAR)
    val blobSchema = runtimeSymbol("blobSchema", SwiftDeclaration.VAR)
    val integerSchema = runtimeSymbol("integerSchema", SwiftDeclaration.VAR)
    val timestampSchema = runtimeSymbol("timestampSchema", SwiftDeclaration.VAR)
    val booleanSchema = runtimeSymbol("booleanSchema", SwiftDeclaration.VAR)
    val floatSchema = runtimeSymbol("floatSchema", SwiftDeclaration.VAR)
    val doubleSchema = runtimeSymbol("doubleSchema", SwiftDeclaration.VAR)
    val longSchema = runtimeSymbol("longSchema", SwiftDeclaration.VAR)
    val shortSchema = runtimeSymbol("shortSchema", SwiftDeclaration.VAR)
    val byteSchema = runtimeSymbol("byteSchema", SwiftDeclaration.VAR)
    val primitiveBooleanSchema = runtimeSymbol("primitiveBooleanSchema", SwiftDeclaration.VAR)
    val primitiveFloatSchema = runtimeSymbol("primitiveFloatSchema", SwiftDeclaration.VAR)
    val primitiveDoubleSchema = runtimeSymbol("primitiveDoubleSchema", SwiftDeclaration.VAR)
    val primitiveLongSchema = runtimeSymbol("primitiveLongSchema", SwiftDeclaration.VAR)
    val primitiveIntegerSchema = runtimeSymbol("primitiveIntegerSchema", SwiftDeclaration.VAR)
    val primitiveShortSchema = runtimeSymbol("primitiveShortSchema", SwiftDeclaration.VAR)
    val primitiveByteSchema = runtimeSymbol("primitiveByteSchema", SwiftDeclaration.VAR)
    val documentSchema = runtimeSymbol("documentSchema", SwiftDeclaration.VAR)
}

private fun runtimeSymbol(
    name: String,
    declaration: SwiftDeclaration,
    spiName: List<String> = listOf("SmithyReadWrite")
): Symbol = SwiftSymbol.make(
    name,
    declaration,
    SwiftDependency.SMITHY_READ_WRITE,
    emptyList(),
    spiName,
)
