package software.amazon.smithy.swift.codegen.integration

import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.middleware.OperationMiddleware

class SmithyHttpProtocolClientGeneratorFactory : HttpProtocolClientGeneratorFactory {
    override fun createHttpProtocolClientGenerator(
        ctx: ProtocolGenerator.GenerationContext,
        httpBindingResolver: HttpBindingResolver,
        writer: SwiftWriter,
        serviceName: String,
        defaultContentType: String,
        httpProtocolCustomizable: HTTPProtocolCustomizable,
        operationMiddleware: OperationMiddleware
    ): HttpProtocolClientGenerator {
        val config = SmithyServiceConfig(writer, ctx)
        return HttpProtocolClientGenerator(ctx, writer, config, httpBindingResolver, defaultContentType, httpProtocolCustomizable, operationMiddleware)
    }
}
