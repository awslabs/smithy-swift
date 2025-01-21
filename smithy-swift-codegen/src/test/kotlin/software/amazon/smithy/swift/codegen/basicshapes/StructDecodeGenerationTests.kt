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
import software.amazon.smithy.swift.codegen.model.NestedShapeTransformer
import software.amazon.smithy.swift.codegen.model.RecursiveShapeBoxer
import software.amazon.smithy.swift.codegen.newTestContext
import software.amazon.smithy.swift.codegen.shouldSyntacticSanityCheck

class StructDecodeGenerationTests {
    var model = javaClass.classLoader.getResource("http-binding-protocol-generator-test.smithy").asSmithy()
    private fun newTestContext(): TestContext {
        val settings = model.defaultSettings()
        model = AddOperationShapes.execute(model, settings.getService(model), settings.moduleName)
        model = RecursiveShapeBoxer.transform(model)
        model = NestedShapeTransformer.transform(model, settings.getService(model))
        model = NeedsReaderWriterTransformer.transform(model, settings.getService(model))
        return model.newTestContext()
    }
    val newTestContext = newTestContext()

    init {
        newTestContext.generator.generateDeserializers(newTestContext.generationCtx)
        newTestContext.generator.generateCodableConformanceForNestedTypes(newTestContext.generationCtx)
        newTestContext.generationCtx.delegator.flushWriters()
    }

    @Test
    fun `it creates decodable conformance for nested structures`() {
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested+ReadWrite.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested2+ReadWrite.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested3+ReadWrite.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("Sources/example/models/Nested4+ReadWrite.swift"))
    }

    @Test
    fun `it decodes nested documents with aggregate shapes`() {
        val contents = getModelFileContents("Sources/example", "Nested4+ReadWrite.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension ExampleClientTypes.Nested4 {

    static func write(value: ExampleClientTypes.Nested4?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["intList"].writeList(value.intList, memberWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), memberNodeInfo: "member", isFlattened: false)
        try writer["intMap"].writeMap(value.intMap, valueWritingClosure: SmithyReadWrite.WritingClosures.writeInt(value:to:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
        try writer["member1"].write(value.member1)
        try writer["stringMap"].writeMap(value.stringMap, valueWritingClosure: SmithyReadWrite.listWritingClosure(memberWritingClosure: SmithyReadWrite.WritingClosures.writeString(value:to:), memberNodeInfo: "member", isFlattened: false), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false)
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it decodes recursive boxed types correctly`() {
        val contents = getModelFileContents(
            "Sources/example",
            "RecursiveShapesInputOutputNested1+ReadWrite.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension ExampleClientTypes.RecursiveShapesInputOutputNested1 {

    static func write(value: ExampleClientTypes.RecursiveShapesInputOutputNested1?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["foo"].write(value.foo)
        try writer["nested"].write(value.nested, with: ExampleClientTypes.RecursiveShapesInputOutputNested2.write(value:to:))
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
extension ExampleClientTypes.RecursiveShapesInputOutputNested2 {

    static func write(value: ExampleClientTypes.RecursiveShapesInputOutputNested2?, to writer: SmithyJSON.Writer) throws {
        guard let value else { return }
        try writer["bar"].write(value.bar)
        try writer["recursiveMember"].write(value.recursiveMember, with: ExampleClientTypes.RecursiveShapesInputOutputNested1.write(value:to:))
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
