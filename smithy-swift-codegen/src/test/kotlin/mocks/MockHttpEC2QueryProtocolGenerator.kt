/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package mocks

import TestHttpProtocolClientGeneratorFactory
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait
import software.amazon.smithy.model.pattern.UriPattern
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.swift.codegen.SmithyTestUtilTypes
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.DefaultHttpProtocolCustomizations
import software.amazon.smithy.swift.codegen.integration.HttpBindingProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.HttpBindingResolver
import software.amazon.smithy.swift.codegen.integration.HttpProtocolTestGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolUnitTestErrorGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolUnitTestRequestGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolUnitTestResponseGenerator
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.HttpResponseGeneratable
import software.amazon.smithy.swift.codegen.integration.httpResponse.HttpResponseGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.XMLHttpResponseBindingErrorInitGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.XMLHttpResponseBindingOutputGenerator
import software.amazon.smithy.swift.codegen.integration.protocols.core.StaticHttpBindingResolver
import software.amazon.smithy.swift.codegen.integration.serde.struct.StructDecodeGenerator
import software.amazon.smithy.swift.codegen.integration.serde.struct.StructEncodeGenerator
import software.amazon.smithy.swift.codegen.model.ShapeMetadata

class MockEC2QueryHttpProtocolCustomizations() : DefaultHttpProtocolCustomizations()

class MockEC2QueryHttpBindingResolver(
    private val context: ProtocolGenerator.GenerationContext,
    private val contentType: String
) : StaticHttpBindingResolver(context, awsQueryHttpTrait, contentType) {

    companion object {
        private val awsQueryHttpTrait: HttpTrait = HttpTrait
            .builder()
            .code(200)
            .method("POST")
            .uri(UriPattern.parse("/"))
            .build()
    }
}

class MockHttpEC2QueryProtocolGenerator : HttpBindingProtocolGenerator() {
    override val defaultContentType: String = "application/x-www-form-urlencoded"
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.DATE_TIME
    override val protocol: ShapeId = Ec2QueryTrait.ID
    override val httpProtocolClientGeneratorFactory = TestHttpProtocolClientGeneratorFactory()
    override val httpProtocolCustomizable = MockEC2QueryHttpProtocolCustomizations()
    override val httpResponseGenerator: HttpResponseGeneratable = HttpResponseGenerator(
        unknownServiceErrorSymbol,
        defaultTimestampFormat,
        XMLHttpResponseBindingOutputGenerator(),
        MockHttpResponseBindingErrorGenerator(),
        XMLHttpResponseBindingErrorInitGenerator(defaultTimestampFormat, SmithyTestUtilTypes.TestBaseError)
    )
    override val shouldRenderDecodableBodyStructForInputShapes = false
    override val shouldRenderEncodableConformance = true
    override fun renderStructEncode(
        ctx: ProtocolGenerator.GenerationContext,
        shapeContainingMembers: Shape,
        shapeMetadata: Map<ShapeMetadata, Any>,
        members: List<MemberShape>,
        writer: SwiftWriter,
        defaultTimestampFormat: TimestampFormatTrait.Format,
        path: String?
    ) {
        StructEncodeGenerator(ctx, shapeContainingMembers, members, shapeMetadata, writer).render()
    }
    override fun renderStructDecode(
        ctx: ProtocolGenerator.GenerationContext,
        shapeContainingMembers: Shape,
        shapeMetadata: Map<ShapeMetadata, Any>,
        members: List<MemberShape>,
        writer: SwiftWriter,
        defaultTimestampFormat: TimestampFormatTrait.Format,
        path: String
    ) {
        val decodeGenerator = StructDecodeGenerator(ctx, shapeContainingMembers, members, mapOf(), writer)
        decodeGenerator.render()
    }

    override fun addProtocolSpecificMiddleware(ctx: ProtocolGenerator.GenerationContext, operation: OperationShape) {
        // Intentionally empty
    }

    override fun generateMessageMarshallable(ctx: ProtocolGenerator.GenerationContext) {
        TODO("Not yet implemented")
    }

    override fun generateMessageUnmarshallable(ctx: ProtocolGenerator.GenerationContext) {
        TODO("Not yet implemented")
    }

    override fun getProtocolHttpBindingResolver(ctx: ProtocolGenerator.GenerationContext, defaultContentType: String):
        HttpBindingResolver = MockEC2QueryHttpBindingResolver(ctx, defaultContentType)

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext): Int {
        val requestTestBuilder = HttpProtocolUnitTestRequestGenerator.Builder()
        val responseTestBuilder = HttpProtocolUnitTestResponseGenerator.Builder()
        val errorTestBuilder = HttpProtocolUnitTestErrorGenerator.Builder()

        return HttpProtocolTestGenerator(
            ctx,
            requestTestBuilder,
            responseTestBuilder,
            errorTestBuilder,
            httpProtocolCustomizable,
            operationMiddleware,
            getProtocolHttpBindingResolver(ctx, defaultContentType),
//            HttpProtocolUnitTestGenerator.SerdeContext("FormURLEncoder()", null, ".secondsSince1970")
        ).generateProtocolTests()
    }
}
