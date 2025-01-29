package software.amazon.smithy.swift.codegen.requestandresponse.responseflow

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.swift.codegen.TestContext
import software.amazon.smithy.swift.codegen.defaultSettings
import software.amazon.smithy.swift.codegen.getFileContents
import software.amazon.smithy.swift.codegen.protocolgeneratormocks.MockHTTPRestJsonProtocolGenerator
import software.amazon.smithy.swift.codegen.shouldSyntacticSanityCheck

class HttpResponseBindingIgnoreQuery {

    @Test
    fun `001 Output httpResponseBinding ignores query trait & decodes field`() {
        val context = setupTests("http-query-payload.smithy", "aws.protocoltests.restjson#RestJson")
        val contents = getFileContents(context.manifest, "Sources/RestJson/models/IgnoreQueryParamsInResponseOutput+HttpResponseBinding.swift")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
extension IgnoreQueryParamsInResponseOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HTTPResponse) async throws -> IgnoreQueryParamsInResponseOutput {
        let data = try await httpResponse.data()
        let responseReader = try SmithyJSON.Reader.from(data: data)
        let reader = responseReader
        reader.respectsJSONName = true
        var value = IgnoreQueryParamsInResponseOutput()
        value.baz = try reader.readString(schema: schema__namespace_smithy_swift_synthetic__name_IgnoreQueryParamsInResponseOutput__member_baz)
        return value
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    private fun setupTests(smithyFile: String, serviceShapeId: String): TestContext {
        val context =
            TestContext.initContextFrom(smithyFile, serviceShapeId, MockHTTPRestJsonProtocolGenerator()) { model ->
                model.defaultSettings(serviceShapeId, "RestJson", "2019-12-16", "Rest Json Protocol")
            }
        context.generator.generateDeserializers(context.generationCtx)
        context.generationCtx.delegator.flushWriters()
        return context
    }
}
