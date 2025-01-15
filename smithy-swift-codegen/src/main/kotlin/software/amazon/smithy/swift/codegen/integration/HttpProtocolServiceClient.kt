/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.integration

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.config.ConfigProperty
import software.amazon.smithy.swift.codegen.integration.plugins.DefaultClientPlugin
import software.amazon.smithy.swift.codegen.model.renderSwiftType
import software.amazon.smithy.swift.codegen.model.toOptional
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.utils.toUpperCamelCase
import software.amazon.smithy.utils.CodeSection

open class HttpProtocolServiceClient(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val writer: SwiftWriter,
    private val serviceConfig: ServiceConfig
) {
    private val serviceName: String = ctx.settings.sdkId

    fun render(serviceSymbol: Symbol) {
        writer.openBlock(
            "public class \$L: \$N {",
            "}",
            serviceSymbol.name,
            ClientRuntimeTypes.Core.Client,
        ) {
            writer.write("public static let clientName = \$S", serviceSymbol.name)
            writer.write("public static let version = \$S", ctx.settings.moduleVersion)
            writer.write("let client: \$N", ClientRuntimeTypes.Http.SdkHttpClient)
            writer.write("let config: \$L", serviceConfig.typeName)
            writer.write("let serviceName = \$S", serviceName)
            writer.write("")
            renderInitFunction()
            writer.write("")
            renderConvenienceInitFunctions(serviceSymbol)
        }
        writer.write("")
        renderClientExtension(serviceSymbol)
        renderServiceSpecificPlugins()
    }

    open fun renderInitFunction() {
        writer.openBlock("public required init(config: \$L) {", "}", serviceConfig.typeName) {
            writer.write(
                "client = \$N(engine: config.httpClientEngine, config: config.httpClientConfiguration)",
                ClientRuntimeTypes.Http.SdkHttpClient
            )
            writer.write("self.config = config")
        }
        writer.write("")
    }

    open fun renderConvenienceInitFunctions(serviceSymbol: Symbol) {
        writer.openBlock("public convenience required init() throws {", "}") {
            writer.write("let config = try \$L()", serviceConfig.typeName)
            writer.write("self.init(config: config)")
        }
        writer.write("")
    }

    fun renderClientExtension(serviceSymbol: Symbol) {
        writer.openBlock("extension \$L {", "}", serviceSymbol.name) {
            writer.write("")
            renderClientConfig(serviceSymbol)
            writer.write("")

            writer.openBlock(
                "public static func builder() -> \$N<\$L> {",
                "}",
                ClientRuntimeTypes.Core.ClientBuilder,
                serviceSymbol.name,
            ) {
                writer.openBlock(
                    "return \$N<\$L>(defaultPlugins: [",
                    "])",
                    ClientRuntimeTypes.Core.ClientBuilder,
                    serviceSymbol.name,
                ) {
                    val defaultPlugins: MutableList<Plugin> = mutableListOf(DefaultClientPlugin())

                    ctx.integrations
                        .flatMap { it.plugins(serviceConfig) }
                        .filter { it.isDefault }
                        .onEach { defaultPlugins.add(it) }

                    val pluginsIterator = defaultPlugins.iterator()

                    while (pluginsIterator.hasNext()) {
                        pluginsIterator.next().customInitialization(writer)
                        if (pluginsIterator.hasNext()) {
                            writer.write(",")
                        }
                    }

                    writer.unwrite(",\n").write("")
                }
            }
        }
        writer.write("")
    }

    open fun renderClientConfig(serviceSymbol: Symbol) {
        val clientConfigurationProtocols =
            ctx.integrations
                .flatMap { it.clientConfigurations(ctx) }
                .mapNotNull { it.swiftProtocolName }
                .map { writer.format("\$N", it) }
                .joinToString(" & ")

        writer.openBlock(
            "public class \$LConfiguration: \$L {", "}",
            serviceConfig.clientName.toUpperCamelCase(),
            clientConfigurationProtocols
        ) {
            val clientConfigs = ctx.integrations.flatMap { it.clientConfigurations(ctx) }
            val properties: List<ConfigProperty> = clientConfigs
                .flatMap { it.getProperties(ctx) }
                .let { overrideConfigProperties(it) }
                .sortedBy { it.accessModifier }

            renderConfigClassVariables(serviceSymbol, properties)

            renderConfigInitializer(serviceSymbol, properties)

            renderSynchronousConfigInitializer(properties)

            renderAsynchronousConfigInitializer(properties)

            renderEmptyAsynchronousConfigInitializer(properties)

            renderCustomConfigInitializer(properties)

            renderPartitionID()

            clientConfigs
                .flatMap { it.getMethods(ctx) }
                .sortedBy { it.accessModifier }
                .forEach {
                    it.render(writer)
                    writer.write("")
                }
        }
        writer.write("")
    }

    open fun renderCustomConfigInitializer(properties: List<ConfigProperty>) {
    }

    open fun overrideConfigProperties(properties: List<ConfigProperty>): List<ConfigProperty> {
        return properties
    }

    private fun renderEmptyAsynchronousConfigInitializer(properties: List<ConfigProperty>) {
        writer.openBlock("public convenience required init() async throws {", "}") {
            writer.openBlock("try await self.init(", ")") {
                properties.forEach { property ->
                    writer.write("\$L: nil,", property.name)
                }
                writer.unwrite(",\n")
                writer.write("")
            }
        }
        writer.write("")
    }

    open fun renderPartitionID() {
        writer.openBlock("public var partitionID: String? {", "}") {
            writer.write("return \"\"")
        }
        writer.write("")
    }

    data class ConfigClassVariablesCustomization(val serviceSymbol: Symbol) : CodeSection

    /**
     * Declare class variables in client configuration class
     */
    private fun renderConfigClassVariables(serviceSymbol: Symbol, properties: List<ConfigProperty>) {
        properties.forEach {
            it.render(writer)
        }
        writer.injectSection(ConfigClassVariablesCustomization(serviceSymbol))
        writer.write("")
    }

    data class ConfigInitializerCustomization(val serviceSymbol: Symbol) : CodeSection

    private fun renderConfigInitializer(serviceSymbol: Symbol, properties: List<ConfigProperty>) {
        writer.openBlock("private init(", ") {") {
            properties.forEach { property ->
                writer.write("_ \$L: \$L,", property.name, property.type.renderSwiftType(writer))
            }
            writer.unwrite(",\n")
            writer.write("")
        }
        writer.indent {
            properties.forEach { property ->
                writer.write("self.\$L = \$L", property.name, property.name)
            }
            writer.injectSection(ConfigInitializerCustomization(serviceSymbol))
        }
        writer.write("}")
        writer.write("")
    }

    private fun renderSynchronousConfigInitializer(properties: List<ConfigProperty>) {
        writer.openBlock("public convenience init(", ") throws {") {
            properties.forEach { property ->
                writer.write("\$L: \$N = nil,", property.name, property.type.toOptional())
            }
            writer.unwrite(",\n")
            writer.write("")
        }
        writer.indent {
            writer.openBlock("self.init(", ")") {
                properties.forEach { property ->
                    if (property.default?.isAsync == true) {
                        writer.write("\$L,", property.name)
                    } else {
                        writer.write("\$L,", property.default?.render(writer, property.name) ?: property.name)
                    }
                }
                writer.unwrite(",\n")
                writer.write("")
            }
        }
        writer.write("}")
        writer.write("")
    }

    private fun renderAsynchronousConfigInitializer(properties: List<ConfigProperty>) {
        if (properties.none { it.default?.isAsync == true }) return

        writer.openBlock("public convenience init(", ") async throws {") {
            properties.forEach { property ->
                writer.write("\$L: \$L = nil,", property.name, property.type.toOptional().renderSwiftType(writer))
            }
            writer.unwrite(",\n")
            writer.write("")
        }
        writer.indent {
            writer.openBlock("self.init(", ")") {
                properties.forEach { property ->
                    if (property.default?.isAsync == true) {
                        writer.write("\$L,", property.default.render(writer))
                    } else {
                        writer.write("\$L,", property.default?.render(writer, property.name) ?: property.name)
                    }
                }
                writer.unwrite(",\n")
                writer.write("")
            }
        }
        writer.write("}")
        writer.write("")
    }

    private fun renderServiceSpecificPlugins() {
        ctx.delegator.useFileWriter("Sources/${ctx.settings.moduleName}/Plugins.swift") { writer ->
            ctx.integrations
                .flatMap { it.plugins(serviceConfig) }
                .onEach { it.render(ctx, writer) }
        }
    }
}
