package software.amazon.smithy.swift.codegen.protocolspecificserde.awsjson11

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.swift.codegen.TestContext
import software.amazon.smithy.swift.codegen.asSmithy
import software.amazon.smithy.swift.codegen.defaultSettings
import software.amazon.smithy.swift.codegen.getModelFileContents
import software.amazon.smithy.swift.codegen.model.AddOperationShapes
import software.amazon.smithy.swift.codegen.newTestContext
import software.amazon.smithy.swift.codegen.shouldSyntacticSanityCheck

// NOTE: protocol conformance is mostly handled by the protocol tests suite
class OutputDeserializerTests {
    private var model = javaClass.classLoader.getResource("awsjson-output-response-deserializer.smithy").asSmithy()
    private fun newTestContext(): TestContext {
        val settings = model.defaultSettings()
        model = AddOperationShapes.execute(model, settings.getService(model), settings.moduleName)
        return model.newTestContext()
    }

    val newTestContext = newTestContext()

    init {
        newTestContext.generator.generateSerializers(newTestContext.generationCtx)
        newTestContext.generator.generateProtocolClient(newTestContext.generationCtx)
        newTestContext.generator.generateDeserializers(newTestContext.generationCtx)
        newTestContext.generator.generateCodableConformanceForNestedTypes(newTestContext.generationCtx)
        newTestContext.generationCtx.delegator.flushWriters()
    }

    @Test
    fun `it creates correct init for simple structure payloads`() {
        val contents = getModelFileContents(
            "Sources/example",
            "SimpleStructureOutput+HttpResponseBinding.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension SimpleStructureOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> SimpleStructureOutput {
        let data = try await httpResponse.data()
        let responseReader = try SmithyJSON.Reader.from(data: data)
        let reader = responseReader
        var value = SimpleStructureOutput()
        value.name = try reader.readString(schema: schema__namespace_smithy_swift_synthetic__name_SimpleStructureOutput__member_name)
        value.number = try reader.readInteger(schema: schema__namespace_smithy_swift_synthetic__name_SimpleStructureOutput__member_number)
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it creates correct init for data streaming payloads`() {
        val contents = getModelFileContents(
            "Sources/example",
            "DataStreamingOutput+HttpResponseBinding.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension DataStreamingOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> DataStreamingOutput {
        var value = DataStreamingOutput()
        switch httpResponse.body {
        case .data(let data):
            value.streamingData = .data(data)
        case .stream(let stream):
            value.streamingData = .stream(stream)
        case .noStream:
            value.streamingData = nil
        }
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it creates correct init for event streaming payloads`() {
        val contents = getModelFileContents(
            "Sources/example",
            "EventStreamingOutput+HttpResponseBinding.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension EventStreamingOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> EventStreamingOutput {
        var value = EventStreamingOutput()
        if case let .stream(stream) = httpResponse.body {
            let messageDecoder = SmithyEventStreams.DefaultMessageDecoder()
            let decoderStream = SmithyEventStreams.DefaultMessageDecoderStream<EventStream>(stream: stream, messageDecoder: messageDecoder, unmarshalClosure: EventStream.unmarshal)
            value.eventStream = decoderStream.toAsyncStream()
        }
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
