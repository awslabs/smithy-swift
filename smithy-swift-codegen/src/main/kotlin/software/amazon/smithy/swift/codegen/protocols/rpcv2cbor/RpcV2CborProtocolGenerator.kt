package software.amazon.smithy.swift.codegen.protocols.rpcv2cbor

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.model.traits.UnitTypeTrait
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait
import software.amazon.smithy.swift.codegen.EndpointTestGenerator
import software.amazon.smithy.swift.codegen.SyntheticClone
import software.amazon.smithy.swift.codegen.integration.DefaultHTTPProtocolCustomizations
import software.amazon.smithy.swift.codegen.integration.HTTPBindingProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.HttpBindingResolver
import software.amazon.smithy.swift.codegen.integration.HttpProtocolClientGeneratorFactory
import software.amazon.smithy.swift.codegen.integration.HttpProtocolTestGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolUnitTestErrorGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolUnitTestRequestGenerator
import software.amazon.smithy.swift.codegen.integration.HttpProtocolUnitTestResponseGenerator
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.SmithyHttpProtocolClientGeneratorFactory
import software.amazon.smithy.swift.codegen.integration.middlewares.ContentLengthMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.ContentTypeMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.MutateHeadersMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationEndpointResolverMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationInputBodyMiddleware
import software.amazon.smithy.swift.codegen.middleware.MiddlewareRenderable
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.targetOrSelf
import software.amazon.smithy.swift.codegen.testModuleName

class RpcV2CborProtocolGenerator(
    rpcCborCustomizations: DefaultHTTPProtocolCustomizations = RpcV2CborCustomizations(),
    private val operationEndpointResolverMiddlewareFactory: ((ProtocolGenerator.GenerationContext, Symbol) -> MiddlewareRenderable)? = null,
    private val userAgentMiddlewareFactory: ((ProtocolGenerator.GenerationContext) -> MiddlewareRenderable)? = null
) : HTTPBindingProtocolGenerator(rpcCborCustomizations) {
    override val defaultContentType = "application/cbor"
    override val protocol: ShapeId = Rpcv2CborTrait.ID
    val requestTestBuilder = HttpProtocolUnitTestRequestGenerator.Builder()
    val responseTestBuilder = HttpProtocolUnitTestResponseGenerator.Builder()
    val errorTestBuilder = HttpProtocolUnitTestErrorGenerator.Builder()
    open val protocolTestsToIgnore: Set<String> = setOf()
    open val protocolTestTagsToIgnore: Set<String> = setOf()
    private val myRpcCborCustoms = rpcCborCustomizations

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext): Int {
        return HttpProtocolTestGenerator(
            ctx,
            requestTestBuilder,
            responseTestBuilder,
            errorTestBuilder,
            myRpcCborCustoms,
            getProtocolHttpBindingResolver(ctx, defaultContentType),
            protocolTestsToIgnore,
            protocolTestTagsToIgnore,
        ).generateProtocolTests() + renderEndpointsTests(ctx) // does not render endpoint tests
    }

    override val httpProtocolClientGeneratorFactory: HttpProtocolClientGeneratorFactory = SmithyHttpProtocolClientGeneratorFactory()

    override val shouldRenderEncodableConformance = true

    override fun getProtocolHttpBindingResolver(
        ctx: ProtocolGenerator.GenerationContext,
        defaultContentType: String
    ): HttpBindingResolver = RPCV2CBORHttpBindingResolver(ctx, defaultContentType)

    override fun addProtocolSpecificMiddleware(ctx: ProtocolGenerator.GenerationContext, operation: OperationShape) {
        val operationEndpointResolverMiddleware = (
            operationEndpointResolverMiddlewareFactory ?: { _, endpointMiddlewareSymbol -> OperationEndpointResolverMiddleware(ctx, endpointMiddlewareSymbol) }
            )(ctx, myRpcCborCustoms.endpointMiddlewareSymbol)

        operationMiddleware.appendMiddleware(
            operation,
            operationEndpointResolverMiddleware
        )

        operationMiddleware.removeMiddleware(operation, "OperationInputBodyMiddleware")
        operationMiddleware.appendMiddleware(operation, OperationInputBodyMiddleware(ctx.model, ctx.symbolProvider, true))

        val hasEventStreamResponse = ctx.model.expectShape(operation.outputShape).hasTrait<StreamingTrait>()
        val hasEventStreamRequest = ctx.model.expectShape(operation.inputShape).hasTrait<StreamingTrait>()

        // Determine the value of the Accept header based on output shape
        val acceptHeaderValue = if (hasEventStreamResponse) {
            "application/vnd.amazon.eventstream"
        } else {
            "application/cbor"
        }

        // Determine the value of the Content-Type header based on input shape
        val contentTypeValue = if (hasEventStreamRequest) {
            "application/vnd.amazon.eventstream"
        } else {
            defaultContentType
        }

        // Middleware to set smithy-protocol and Accept headers
        // Every request for the rpcv2Cbor protocol MUST contain a smithy-protocol header with the value of rpc-v2-cbor
        val smithyProtocolRequestHeaderMiddleware = MutateHeadersMiddleware(
            overrideHeaders = mapOf(
                "smithy-protocol" to "rpc-v2-cbor",
                "Accept" to acceptHeaderValue
            )
        )

        operationMiddleware.appendMiddleware(operation, smithyProtocolRequestHeaderMiddleware)
        operationMiddleware.appendMiddleware(operation, CborValidateResponseHeaderMiddleware())

        if (operation.hasHttpBody(ctx)) {
            operationMiddleware.appendMiddleware(operation, ContentTypeMiddleware(ctx.model, ctx.symbolProvider, contentTypeValue, true))
        }

        // Only set Content-Length header if the request input shape doesn't have an event stream
        if (!hasEventStreamRequest) {
            operationMiddleware.appendMiddleware(operation, ContentLengthMiddleware(ctx.model, true, false, false))
        }
    }

    override fun addUserAgentMiddleware(ctx: ProtocolGenerator.GenerationContext, operation: OperationShape) {
        userAgentMiddlewareFactory?.let { factory ->
            val userAgentMiddleware = factory(ctx)
            operationMiddleware.appendMiddleware(operation, userAgentMiddleware)
        }
    }

    fun renderEndpointsTests(ctx: ProtocolGenerator.GenerationContext): Int {
        val ruleSetNode = ctx.service.getTrait<EndpointRuleSetTrait>()?.ruleSet
        val ruleSet = if (ruleSetNode != null) EndpointRuleSet.fromNode(ruleSetNode) else null
        var testCount = 0

        ctx.service.getTrait<EndpointTestsTrait>()?.let { testsTrait ->
            if (testsTrait.testCases?.isEmpty() == true) {
                return 0
            }

            ctx.delegator.useFileWriter("Tests/${ctx.settings.testModuleName}/EndpointResolverTest.swift") { swiftWriter ->
                testCount = + EndpointTestGenerator(testsTrait, ruleSet, ctx).render(swiftWriter)
            }
        }

        return testCount
    }

    /**
     * @return whether the operation input does _not_ target the unit shape ([UnitTypeTrait.UNIT])
     */
    private fun OperationShape.hasHttpBody(ctx: ProtocolGenerator.GenerationContext): Boolean {
        val input = ctx.model.expectShape(inputShape).targetOrSelf(ctx.model).let {
            // If the input has been synthetically cloned from the original (most likely),
            // pull the archetype and check _that_
            it.getTrait<SyntheticClone>()?.let { clone ->
                ctx.model.expectShape(clone.archetype).targetOrSelf(ctx.model)
            } ?: it
        }

        return input.id != UnitTypeTrait.UNIT
    }
}
