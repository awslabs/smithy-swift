package software.amazon.smithy.swift.codegen.middleware

import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.config.ConfigProperty
import software.amazon.smithy.swift.codegen.integration.HTTPProtocolCustomizable
import software.amazon.smithy.swift.codegen.integration.HttpBindingResolver
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.middlewares.handlers.MiddlewareShapeUtils
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.toLowerCamelCase
import software.amazon.smithy.swift.codegen.swiftFunctionParameterIndent
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyHTTPAPITypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTypes

typealias HttpMethodCallback = (OperationShape) -> String
open class MiddlewareExecutionGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: SwiftWriter,
    private val httpBindingResolver: HttpBindingResolver,
    private val httpProtocolCustomizable: HTTPProtocolCustomizable,
    private val operationMiddleware: OperationMiddleware,
    private val operationStackName: String,
    private val httpMethodCallback: HttpMethodCallback? = null
) {
    private val symbolProvider = ctx.symbolProvider

    fun render(
        serviceShape: ServiceShape,
        op: OperationShape,
        flowType: ContextAttributeCodegenFlowType = ContextAttributeCodegenFlowType.NORMAL,
    ) {
        val inputShape = MiddlewareShapeUtils.inputSymbol(symbolProvider, ctx.model, op)
        val outputShape = MiddlewareShapeUtils.outputSymbol(symbolProvider, ctx.model, op)
        writer.write("let context = \$N()", SmithyTypes.ContextBuilder)
        writer.swiftFunctionParameterIndent {
            renderContextAttributes(op, flowType)
        }
        httpProtocolCustomizable.renderEventStreamAttributes(ctx, writer, op)
        writer.write(
            "let builder = \$N<\$N, \$N, \$N, \$N>()",
            ClientRuntimeTypes.Core.OrchestratorBuilder,
            inputShape,
            outputShape,
            SmithyHTTPAPITypes.HTTPRequest,
            SmithyHTTPAPITypes.HTTPResponse,
        )
        writer.openBlock("config.interceptorProviders.forEach { provider in", "}") {
            writer.write("builder.interceptors.add(provider.create())")
        }
        writer.openBlock("config.httpInterceptorProviders.forEach { provider in", "}") {
            writer.write("builder.interceptors.add(provider.create())")
        }

        renderMiddlewares(ctx, op, operationStackName)

        val rpcService = symbolProvider.toSymbol(serviceShape).getName().removeSuffix("Client")
        val rpcMethod = op.getId().getName()
        writer.write(
            """
            var metricsAttributes = ${"$"}N()
            metricsAttributes.set(key: ${"$"}N.service, value: ${"$"}S)
            metricsAttributes.set(key: ${"$"}N.method, value: ${"$"}S)
            let op = builder.attributes(context)
                .telemetry(${"$"}N(
                    telemetryProvider: config.telemetryProvider,
                    metricsAttributes: metricsAttributes,
                    meterScope: serviceName,
                    tracerScope: serviceName
                ))
                .executeRequest(client)
                .build()
            """.trimIndent(),
            SmithyTypes.Attributes,
            ClientRuntimeTypes.Middleware.OrchestratorMetricsAttributesKeys,
            rpcService,
            ClientRuntimeTypes.Middleware.OrchestratorMetricsAttributesKeys,
            rpcMethod,
            ClientRuntimeTypes.Middleware.OrchestratorTelemetry
        )
    }

    private fun renderContextAttributes(op: OperationShape, flowType: ContextAttributeCodegenFlowType) {
        val httpMethod = resolveHttpMethod(op)

        // FIXME it over indents if i add another indent, come up with better way to properly indent or format for swift

        writer.write("  .withMethod(value: .\$L)", httpMethod)
        writer.write("  .withServiceName(value: serviceName)")
        writer.write("  .withOperation(value: \$S)", op.toLowerCamelCase())
        writer.write("  .withIdempotencyTokenGenerator(value: config.idempotencyTokenGenerator)")
        writer.write("  .withLogger(value: config.logger)")
        writer.write("  .withPartitionID(value: config.partitionID)")
        writer.write("  .withAuthSchemes(value: config.authSchemes ?? [])")
        writer.write("  .withAuthSchemeResolver(value: config.authSchemeResolver)")
        writer.write("  .withUnsignedPayloadTrait(value: \$L)", op.hasTrait<UnsignedPayloadTrait>())
        writer.write("  .withSocketTimeout(value: config.httpClientConfiguration.socketTimeout)")
        writer.write("  .withIdentityResolver(value: config.bearerTokenIdentityResolver, schemeID: \$S)", "smithy.api#httpBearerAuth")

        // Add flag for presign / presign-url flows
        if (flowType == ContextAttributeCodegenFlowType.PRESIGN_REQUEST) {
            writer.write("  .withFlowType(value: .PRESIGN_REQUEST)")
        } else if (flowType == ContextAttributeCodegenFlowType.PRESIGN_URL) {
            writer.write("  .withFlowType(value: .PRESIGN_URL)")
        }
        // Add expiration flag for presign / presign-url flows
        if (flowType != ContextAttributeCodegenFlowType.NORMAL) {
            writer.write("  .withExpiration(value: expiration)")
        }

        // Add context values for config fields
        val serviceShape = ctx.service
        httpProtocolCustomizable.renderContextAttributes(ctx, writer, serviceShape, op)
        writer.write("  .build()")
    }

    private fun resolveHttpMethod(op: OperationShape): String {
        return httpMethodCallback?.let {
            it(op)
        } ?: run {
            val httpTrait = httpBindingResolver.httpTrait(op)
            httpTrait.method.toLowerCase()
        }
    }

    private fun renderMiddlewares(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, operationStackName: String) {
        operationMiddleware.renderMiddleware(ctx, writer, op, operationStackName)
    }

    /*
     * The enum in this companion object is used to determine under which codegen flow
     * the middleware context is being code-generated.
     *
     * For PRESIGN_REQUEST & PRESIGN_URL flows:
     * - The value of expiration is saved to middleware context during codegen.
     * - The flow type information is saved to middleware context during codegen, for consumption by
     *   AWS auth schemes during runtime to determine where to put the request signature in the request.
     */
    companion object {
        enum class ContextAttributeCodegenFlowType {
            NORMAL, PRESIGN_REQUEST, PRESIGN_URL
        }
    }
}
