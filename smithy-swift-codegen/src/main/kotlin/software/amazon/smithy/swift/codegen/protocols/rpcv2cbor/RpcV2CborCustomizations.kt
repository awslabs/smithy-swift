package software.amazon.smithy.swift.codegen.protocols.rpcv2cbor

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.DefaultHTTPProtocolCustomizations
import software.amazon.smithy.swift.codegen.integration.HttpProtocolServiceClient
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.ServiceConfig
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes

class RpcV2CborCustomizations : DefaultHTTPProtocolCustomizations() {

    // Defaults that may need to be changed in future
    override fun renderEventStreamAttributes(
        ctx: ProtocolGenerator.GenerationContext,
        writer: SwiftWriter,
        op: OperationShape
    ) {
        // Event streams are not supported
    }

    override fun serviceClient(
        ctx: ProtocolGenerator.GenerationContext,
        writer: SwiftWriter,
        serviceConfig: ServiceConfig
    ): HttpProtocolServiceClient {
        return HttpProtocolServiceClient(ctx, writer, serviceConfig)
    }

    override val endpointMiddlewareSymbol: Symbol = ClientRuntimeTypes.Core.EndpointResolverMiddleware

    override val unknownServiceErrorSymbol: Symbol = ClientRuntimeTypes.Http.UnknownHttpServiceError

    // Required by RPCv2 CBOR
    override val baseErrorSymbol: Symbol = ClientRuntimeTypes.RpcV2Cbor.RpcV2CborError

    // Timestamp format is not used in RpcV2Cbor since it's a binary protocol. We seem to be missing an abstraction
    // between text-based and binary-based protocols
    override val defaultTimestampFormat = TimestampFormatTrait.Format.UNKNOWN
}
