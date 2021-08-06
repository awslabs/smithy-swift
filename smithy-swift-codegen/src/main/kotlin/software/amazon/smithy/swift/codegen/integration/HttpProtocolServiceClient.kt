package software.amazon.smithy.swift.codegen.integration

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.SwiftWriter

open class HttpProtocolServiceClient(
    ctx: ProtocolGenerator.GenerationContext,
    private val writer: SwiftWriter,
    private val properties: List<ClientProperty>,
    private val serviceConfig: ServiceConfig
) {
    private val serviceName: String = ctx.settings.sdkId

    fun render(serviceSymbol: Symbol) {
        writer.openBlock("public class ${serviceSymbol.name} {", "}") {
            writer.write("let client: \$N", ClientRuntimeTypes.Http.SdkHttpClient)
            writer.write("let config: ${serviceConfig.typesToConformConfigTo.first().fullName}")
            writer.write("let serviceName = \"${serviceName}\"")
            writer.write("let encoder: \$N", ClientRuntimeTypes.Serde.RequestEncoder)
            writer.write("let decoder: \$N", ClientRuntimeTypes.Serde.ResponseDecoder)
            properties.forEach { prop ->
                prop.addImportsAndDependencies(writer)
            }
            writer.write("")
            writer.openBlock("public init(config: ${serviceConfig.typesToConformConfigTo.first().fullName}) {", "}") {
                writer.write("client = \$N(engine: config.httpClientEngine, config: config.httpClientConfiguration)", ClientRuntimeTypes.Http.SdkHttpClient)
                properties.forEach { prop ->
                    prop.renderInstantiation(writer)
                    if (prop.needsConfigure) {
                        prop.renderConfiguration(writer)
                    }
                    prop.renderInitialization(writer, "config")
                }

                writer.write("self.config = config")
            }
            writer.write("")
            renderConvenienceInit(serviceSymbol)
            writer.write("")
            writer.openBlock("deinit {", "}") {
                writer.write("client.close()")
            }
            writer.write("")
            renderConfig(serviceSymbol)
        }
        writer.write("")
        renderLogHandlerFactory(serviceSymbol)
        writer.write("")
    }

    open fun renderConvenienceInit(serviceSymbol: Symbol) {
        writer.openBlock("public convenience init() throws {", "}") {
            writer.write("let config = try ${serviceConfig.typeName}()")
            writer.write("self.init(config: config)")
        }
    }

    private fun renderLogHandlerFactory(serviceSymbol: Symbol) {
        writer.openBlock("public struct ${serviceSymbol.name}LogHandlerFactory: \$T {", "}", ClientRuntimeTypes.Core.SDKLogHandlerFactory) {
            writer.write("public var label = \"${serviceSymbol.name}\"")
            writer.write("let logLevel: \$T", ClientRuntimeTypes.Core.SDKLogLevel)

            writer.openBlock("public func construct(label: String) -> LogHandler {", "}") {
                writer.write("var handler = StreamLogHandler.standardOutput(label: label)")
                writer.write("handler.logLevel = logLevel.toLoggerType()")
                writer.write("return handler")
            }

            writer.openBlock("public init(logLevel: \$T) {", "}", ClientRuntimeTypes.Core.SDKLogLevel) {
                writer.write("self.logLevel = logLevel")
            }
        }
    }

    private fun renderConfig(serviceSymbol: Symbol) {
        val configFields = serviceConfig.sdkRuntimeConfigProperties()
        val otherConfigFields = serviceConfig.otherRuntimeConfigProperties()
        val inheritance = serviceConfig.getTypeInheritance()
        writer.openBlock("public class ${serviceSymbol.name}Configuration: $inheritance {", "}") {
            writer.write("")
            configFields.forEach {
                val optional = if (it.type == ClientRuntimeTypes.Serde.RequestEncoder || it.type == ClientRuntimeTypes.Serde.ResponseDecoder) "?" else ""
                writer.write("public var ${it.memberName}: \$L$optional", it.type)
            }
            writer.write("")
            otherConfigFields.forEach {
                writer.write("public var ${it.memberName}: \$L", it.type)
            }
            writer.write("")
            serviceConfig.renderInitializers(serviceSymbol)
        }
    }
}
