package software.amazon.smithy.swift.codegen.basicshapes

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.swift.codegen.TestContext
import software.amazon.smithy.swift.codegen.asSmithy
import software.amazon.smithy.swift.codegen.defaultSettings
import software.amazon.smithy.swift.codegen.getModelFileContents
import software.amazon.smithy.swift.codegen.model.AddOperationShapes
import software.amazon.smithy.swift.codegen.model.NeedsReaderWriterTransformer
import software.amazon.smithy.swift.codegen.model.RecursiveShapeBoxer
import software.amazon.smithy.swift.codegen.newTestContext
import software.amazon.smithy.swift.codegen.shouldSyntacticSanityCheck

class StructEncodeGenerationTests {
    var model = javaClass.classLoader.getResource("http-binding-protocol-generator-test.smithy").asSmithy()
    private fun newTestContext(): TestContext {
        val settings = model.defaultSettings()
        model = AddOperationShapes.execute(model, settings.getService(model), settings.moduleName)
        model = RecursiveShapeBoxer.transform(model)
        model = NeedsReaderWriterTransformer.transform(model, settings.getService(model))
        return model.newTestContext()
    }
    val newTestContext = newTestContext()
    init {
        newTestContext.generator.generateSerializers(newTestContext.generationCtx)
        newTestContext.generator.generateCodableConformanceForNestedTypes(newTestContext.generationCtx)
        newTestContext.generationCtx.delegator.flushWriters()
    }

    @Test
    fun `it creates encodable conformance in correct file`() {
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/SmokeTestInput+Write.swift"))
    }

    @Test
    fun `it creates encodable conformance for nested structures`() {
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested+ReadWrite.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested2+ReadWrite.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested3+ReadWrite.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested4+ReadWrite.swift"))
    }

    @Test
    fun `it creates smoke test request encodable conformance`() {
        val contents = getModelFileContents("Sources/example", "SmokeTestInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension SmokeTestInput {

    static func write(value: SmokeTestInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["payload1"].write(value.payload1)
        try writer["payload2"].write(value.payload2)
        try writer["payload3"].write(value.payload3, with: Nested.write(value:to:))
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes nested documents with aggregate shapes`() {
        val contents = getModelFileContents("Sources/example", "Nested4+ReadWrite.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension Nested4 {

    static func write(value: Nested4?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["intList"].writeList(value.intList, memberWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), memberNodeInfo: "member", isFlattened: false)
        try writer["intMap"].writeMap(value.intMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["member1"].write(value.member1)
        try writer["stringMap"].writeMap(value.stringMap, valueWritingClosure: SmithyReadWrite.listWritingClosure(memberWritingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:), memberNodeInfo: "member", isFlattened: false), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
    }

    static func read(from reader: SmithyJSON.Reader) throws -> Nested4 {
        guard reader.hasContent else { throw SmithyReadWrite.ReaderError.requiredValueNotPresent }
        var value = Nested4()
        value.member1 = try reader.readInteger(schema: schema__namespace_com_test__name_Nested4__member_member1)
        value.intList = try reader.readList(schema: schema__namespace_com_test__name_Nested4__member_intList)
        value.intMap = try reader.readMap(schema: schema__namespace_com_test__name_Nested4__member_intMap)
        value.stringMap = try reader.readMap(schema: schema__namespace_com_test__name_Nested4__member_stringMap)
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it provides encodable conformance to operation inputs with timestamps`() {
        val contents = getModelFileContents("Sources/example", "TimestampInputInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension TimestampInputInput {

    static func write(value: TimestampInputInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["dateTime"].writeTimestamp(value.dateTime, format: SmithyTimestamps.TimestampFormat.dateTime)
        try writer["epochSeconds"].writeTimestamp(value.epochSeconds, format: SmithyTimestamps.TimestampFormat.epochSeconds)
        try writer["httpDate"].writeTimestamp(value.httpDate, format: SmithyTimestamps.TimestampFormat.httpDate)
        try writer["inheritedTimestamp"].writeTimestamp(value.inheritedTimestamp, format: SmithyTimestamps.TimestampFormat.httpDate)
        try writer["normal"].writeTimestamp(value.normal, format: SmithyTimestamps.TimestampFormat.epochSeconds)
        try writer["timestampList"].writeList(value.timestampList, memberWritingClosure: SmithyReadWrite.timestampWritingClosure(format: SmithyTimestamps.TimestampFormat.dateTime), memberNodeInfo: "member", isFlattened: false)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes maps correctly`() {
        val contents = getModelFileContents("Sources/example", "MapInputInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension MapInputInput {

    static func write(value: MapInputInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["blobMap"].writeMap(value.blobMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeData(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["dateMap"].writeMap(value.dateMap, valueWritingClosure: SmithyReadWrite.timestampWritingClosure(format: SmithyTimestamps.TimestampFormat.httpDate), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["enumMap"].writeMap(value.enumMap, valueWritingClosure: SmithyReadWrite.WritingClosureBox<MyEnum>().write(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["intMap"].writeMap(value.intMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["structMap"].writeMap(value.structMap, valueWritingClosure: ReachableOnlyThroughMap.write(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes nested enums correctly`() {
        val contents = getModelFileContents("Sources/example", "EnumInputInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension EnumInputInput {

    static func write(value: EnumInputInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["nestedWithEnum"].write(value.nestedWithEnum, with: NestedEnum.write(value:to:))
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)

        val contents2 = getModelFileContents("Sources/example", "NestedEnum+ReadWrite.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents2 = """
extension NestedEnum {

    static func write(value: NestedEnum?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["myEnum"].write(value.myEnum)
    }
}
"""
        contents2.shouldContainOnlyOnce(expectedContents2)
    }

    @Test
    fun `it encodes recursive boxed types correctly`() {
        val contents = getModelFileContents(
            "Sources/example",
            "RecursiveShapesInputOutputNested1+ReadWrite.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension RecursiveShapesInputOutputNested1 {

    static func write(value: RecursiveShapesInputOutputNested1?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["foo"].write(value.foo)
        try writer["nested"].write(value.nested, with: RecursiveShapesInputOutputNested2.write(value:to:))
    }

    static func read(from reader: SmithyJSON.Reader) throws -> RecursiveShapesInputOutputNested1 {
        guard reader.hasContent else { throw SmithyReadWrite.ReaderError.requiredValueNotPresent }
        var value = RecursiveShapesInputOutputNested1()
        value.foo = try reader.readString(schema: schema__namespace_com_test__name_RecursiveShapesInputOutputNested1__member_foo)
        value.nested = try reader.readStructure(schema: schema__namespace_com_test__name_RecursiveShapesInputOutputNested1__member_nested)
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes one side of the recursive shape`() {
        val contents = getModelFileContents(
            "Sources/example",
            "RecursiveShapesInputOutputNested2+ReadWrite.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension RecursiveShapesInputOutputNested2 {

    static func write(value: RecursiveShapesInputOutputNested2?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["bar"].write(value.bar)
        try writer["recursiveMember"].write(value.recursiveMember, with: RecursiveShapesInputOutputNested1.write(value:to:))
    }

    static func read(from reader: SmithyJSON.Reader) throws -> RecursiveShapesInputOutputNested2 {
        guard reader.hasContent else { throw SmithyReadWrite.ReaderError.requiredValueNotPresent }
        var value = RecursiveShapesInputOutputNested2()
        value.bar = try reader.readString(schema: schema__namespace_com_test__name_RecursiveShapesInputOutputNested2__member_bar)
        value.recursiveMember = try reader.readStructure(schema: schema__namespace_com_test__name_RecursiveShapesInputOutputNested2__member_recursiveMember)
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes structure with sparse list`() {
        val contents = getModelFileContents("Sources/example", "JsonListsInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension JsonListsInput {

    static func write(value: JsonListsInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["booleanList"].writeList(value.booleanList, memberWritingClosure: SmithyReadWrite.WritingClosures.writeBool(value:to:), memberNodeInfo: "member", isFlattened: false)
        try writer["integerList"].writeList(value.integerList, memberWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), memberNodeInfo: "member", isFlattened: false)
        try writer["nestedStringList"].writeList(value.nestedStringList, memberWritingClosure: SmithyReadWrite.listWritingClosure(memberWritingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:), memberNodeInfo: "member", isFlattened: false), memberNodeInfo: "member", isFlattened: false)
        try writer["sparseStringList"].writeList(value.sparseStringList, memberWritingClosure: SmithyReadWrite.sparseFormOf(writingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:)), memberNodeInfo: "member", isFlattened: false)
        try writer["stringList"].writeList(value.stringList, memberWritingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:), memberNodeInfo: "member", isFlattened: false)
        try writer["stringSet"].writeList(value.stringSet, memberWritingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:), memberNodeInfo: "member", isFlattened: false)
        try writer["timestampList"].writeList(value.timestampList, memberWritingClosure: SmithyReadWrite.timestampWritingClosure(format: SmithyTimestamps.TimestampFormat.dateTime), memberNodeInfo: "member", isFlattened: false)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes structure with sparse map`() {
        val contents = getModelFileContents("Sources/example", "JsonMapsInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension JsonMapsInput {

    static func write(value: JsonMapsInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["denseBooleanMap"].writeMap(value.denseBooleanMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeBool(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["denseNumberMap"].writeMap(value.denseNumberMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["denseStringMap"].writeMap(value.denseStringMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["denseStructMap"].writeMap(value.denseStructMap, valueWritingClosure: GreetingStruct.write(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["sparseBooleanMap"].writeMap(value.sparseBooleanMap, valueWritingClosure: SmithyReadWrite.sparseFormOf(writingClosure: SmithyReadWrite.WritingClosures.writeBool(value:to:)), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["sparseNumberMap"].writeMap(value.sparseNumberMap, valueWritingClosure: SmithyReadWrite.sparseFormOf(writingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:)), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["sparseStringMap"].writeMap(value.sparseStringMap, valueWritingClosure: SmithyReadWrite.sparseFormOf(writingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:)), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["sparseStructMap"].writeMap(value.sparseStructMap, valueWritingClosure: SmithyReadWrite.sparseFormOf(writingClosure: GreetingStruct.write(value:to:)), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `encode checks for 0 or false for primitive types`() {
        val contents = getModelFileContents("Sources/example", "PrimitiveTypesInput+Write.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension PrimitiveTypesInput {

    static func write(value: PrimitiveTypesInput?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["booleanVal"].write(value.booleanVal)
        try writer["byteVal"].write(value.byteVal)
        try writer["doubleVal"].write(value.doubleVal)
        try writer["floatVal"].write(value.floatVal)
        try writer["intVal"].write(value.intVal)
        try writer["longVal"].write(value.longVal)
        try writer["primitiveBooleanVal"].write(value.primitiveBooleanVal)
        try writer["primitiveByteVal"].write(value.primitiveByteVal)
        try writer["primitiveDoubleVal"].write(value.primitiveDoubleVal)
        try writer["primitiveFloatVal"].write(value.primitiveFloatVal)
        try writer["primitiveIntVal"].write(value.primitiveIntVal)
        try writer["primitiveLongVal"].write(value.primitiveLongVal)
        try writer["primitiveShortVal"].write(value.primitiveShortVal)
        try writer["shortVal"].write(value.shortVal)
        try writer["str"].write(value.str)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
