package software.amazon.smithy.swift.codegen.requestandresponse

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.TestContext
import software.amazon.smithy.swift.codegen.asSmithy
import software.amazon.smithy.swift.codegen.defaultSettings
import software.amazon.smithy.swift.codegen.getModelFileContents
import software.amazon.smithy.swift.codegen.integration.DefaultServiceConfig
import software.amazon.smithy.swift.codegen.integration.HTTPProtocolCustomizable
import software.amazon.smithy.swift.codegen.integration.HttpBindingResolver
import software.amazon.smithy.swift.codegen.integration.HttpProtocolClientGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolClientGeneratorFactory
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.ServiceConfig
import software.amazon.smithy.swift.codegen.middleware.OperationMiddleware
import software.amazon.smithy.swift.codegen.model.AddOperationShapes
import software.amazon.smithy.swift.codegen.newTestContext
import software.amazon.smithy.swift.codegen.shouldSyntacticSanityCheck

class TestHttpProtocolClientGeneratorFactory : HttpProtocolClientGeneratorFactory {
    override fun createHttpProtocolClientGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        httpBindingResolver: HttpBindingResolver,
        writer: SwiftWriter,
        serviceName: String,
        defaultContentType: String,
        httpProtocolCustomizable: HTTPProtocolCustomizable,
        operationMiddleware: OperationMiddleware,
    ): HttpProtocolClientGenerator {
        val serviceSymbol = ctx.symbolProvider.toSymbol(ctx.service)
        val config = getConfigClass(writer, serviceSymbol.name)
        return HttpProtocolClientGenerator(ctx, writer, config, httpBindingResolver, defaultContentType, httpProtocolCustomizable, operationMiddleware)
    }

    private fun getConfigClass(writer: SwiftWriter, serviceName: String): ServiceConfig {
        return DefaultServiceConfig(writer, serviceName)
    }
}

// NOTE: protocol conformance is mostly handled by the protocol tests suite
class HTTPBindingProtocolGeneratorTests {
    private var model = javaClass.classLoader.getResource("http-binding-protocol-generator-test.smithy").asSmithy()
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
    fun `it creates correct init for explicit struct payloads`() {
        val contents = getModelFileContents("Sources/example", "ExplicitStructOutput+HttpResponseBinding.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension ExplicitStructOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> ExplicitStructOutput {
        let data = try await httpResponse.data()
        let responseReader = try SmithyJSON.Reader.from(data: data)
        let reader = responseReader
        reader.respectsJSONName = true
        var value = ExplicitStructOutput()
        value.payload1 = try reader.readStructure(schema: schema__namespace_smithy_swift_synthetic__name_ExplicitStructOutput__member_payload1)
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `getSanitizedName generates Swifty name`() {
        val testProtocolName = "aws.rest-json-1.1"
        val sanitizedProtocolName = ProtocolGenerator.getSanitizedName(testProtocolName)
        sanitizedProtocolName.shouldBeEqualComparingTo("AWSRestJson1_1")
    }

    @Test
    fun `httpResponseCodeOutput response init content`() {
        val contents = getModelFileContents("Sources/example", "HttpResponseCodeOutput+HttpResponseBinding.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension HttpResponseCodeOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> HttpResponseCodeOutput {
        var value = HttpResponseCodeOutput()
        value.status = httpResponse.statusCode.rawValue
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `decode the document type in HttpResponseBinding`() {
        val contents = getModelFileContents("Sources/example", "InlineDocumentAsPayloadOutput+HttpResponseBinding.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension InlineDocumentAsPayloadOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> InlineDocumentAsPayloadOutput {
        var value = InlineDocumentAsPayloadOutput()
        if let data = try await httpResponse.body.readData() {
            value.documentValue = try Smithy.Document.make(from: data)
        }
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `default fooMap to an empty map if keysForFooMap is empty`() {
        val contents = getModelFileContents("Sources/example", "HttpPrefixHeadersOutput+HttpResponseBinding.swift", newTestContext.manifest)
        val expectedContents = """
extension HttpPrefixHeadersOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> HttpPrefixHeadersOutput {
        var value = HttpPrefixHeadersOutput()
        if let fooHeaderValue = httpResponse.headers.value(for: "X-Foo") {
            value.foo = fooHeaderValue
        }
        let keysForFooMap = httpResponse.headers.dictionary.keys.filter({ ${'$'}0.starts(with: "X-Foo-") })
        if (!keysForFooMap.isEmpty) {
            var mapMember = [Swift.String: String]()
            for headerKey in keysForFooMap {
                let mapMemberValue = httpResponse.headers.dictionary[headerKey]?[0]
                let mapMemberKey = headerKey.removePrefix("X-Foo-")
                mapMember[mapMemberKey] = mapMemberValue
            }
            value.fooMap = mapMember
        } else {
            value.fooMap = [:]
        }
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
