/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.codegen.core.directed.CreateContextDirective
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective
import software.amazon.smithy.codegen.core.directed.DirectedCodegen
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.traits.SensitiveTrait
import software.amazon.smithy.swift.codegen.core.GenerationContext
import software.amazon.smithy.swift.codegen.integration.CustomDebugStringConvertibleGenerator
import software.amazon.smithy.swift.codegen.integration.CustomDebugStringConvertibleGenerator.Companion.isSensitive
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.SwiftIntegration
import software.amazon.smithy.swift.codegen.model.hasTrait
import java.util.logging.Logger

class DirectedSwiftCodegen(val context: PluginContext) :
    DirectedCodegen<GenerationContext, SwiftSettings, SwiftIntegration> {
    private val LOGGER = Logger.getLogger(javaClass.name)

    override fun createSymbolProvider(directive: CreateSymbolProviderDirective<SwiftSettings>): SymbolProvider {
        return SwiftSymbolProvider(directive.model(), directive.settings())
    }

    override fun createContext(directive: CreateContextDirective<SwiftSettings, SwiftIntegration>): GenerationContext {
        val model = directive.model()
        val service = directive.service()
        val settings = directive.settings()
        val integrations = directive.integrations()

        val protocolGenerator = resolveProtocolGenerator(integrations, model, service, settings)

        for (integration in integrations) {
            integration.serviceErrorProtocolSymbol()?.let {
                protocolGenerator?.serviceErrorProtocolSymbol = it
            }
        }

        return GenerationContext(
            directive.model(),
            directive.symbolProvider(),
            directive.settings(),
            directive.fileManifest(),
            protocolGenerator,
            directive.integrations()
        )
    }

    override fun generateService(directive: GenerateServiceDirective<GenerationContext, SwiftSettings>) {
        val service = directive.service()
        val settings = directive.settings()
        val symbolProvider = directive.symbolProvider()
        val context = directive.context()
        val model = directive.model()
        val integrations = context.integrations
        val writers = context.writerDelegator()

        LOGGER.info("Generating Swift client for service ${directive.settings().service}")

        var shouldGenerateTestTarget = false
        context.protocolGenerator?.apply {
            val ctx = ProtocolGenerator.GenerationContext(settings, model, service, symbolProvider, integrations, this.protocol, writers)
            LOGGER.info("[${service.id}] Generating serde for protocol ${this.protocol}")
            generateSerializers(ctx)
            generateDeserializers(ctx)
            generateMessageMarshallable(ctx)
            generateMessageUnmarshallable(ctx)
            generateCodableConformanceForNestedTypes(ctx)
            generateSchemas(ctx)

            initializeMiddleware(ctx)

            LOGGER.info("[${service.id}] Generating unit tests for protocol ${this.protocol}")
            val numProtocolUnitTestsGenerated = generateProtocolUnitTests(ctx)
            shouldGenerateTestTarget = (numProtocolUnitTestsGenerated > 0)

            LOGGER.info("[${service.id}] Generated $numProtocolUnitTestsGenerated tests for protocol ${this.protocol}")

            LOGGER.info("[${service.id}] Generating service client for protocol ${this.protocol}")
            generateProtocolClient(ctx)

            integrations.forEach { it.writeAdditionalFiles(context, ctx, writers) }

            LOGGER.info("[${service.id}] Generating smoke tests for service")
            generateSmokeTests(ctx)

            LOGGER.info("Generating package manifest file")
            PackageManifestGenerator(ctx).writePackageManifest(writers.dependencies)

            LOGGER.info("Flushing swift writers")
            writers.flushWriters()
        }
    }

    override fun generateStructure(directive: GenerateStructureDirective<GenerationContext, SwiftSettings>) {
        val shape = directive.shape()
        val context = directive.context()
        val model = directive.model()
        val settings = directive.settings()
        val symbolProvider = directive.symbolProvider()
        val protocolGenerator = context.protocolGenerator
        val writers = context.writerDelegator()

        writers.useShapeWriter(shape) { writer: SwiftWriter ->
            StructureGenerator(model, symbolProvider, writer, shape, settings, protocolGenerator?.serviceErrorProtocolSymbol).render()
        }

        if (shape.hasTrait<SensitiveTrait>() || shape.members().any { it.isSensitive(model) }) {
            writers.useShapeExtensionWriter(shape, "CustomDebugStringConvertible") { writer: SwiftWriter ->
                CustomDebugStringConvertibleGenerator(symbolProvider, writer, shape, model).render()
            }
        }
    }

    override fun generateError(directive: GenerateErrorDirective<GenerationContext, SwiftSettings>) {
        val shape = directive.shape()
        val context = directive.context()
        val model = directive.model()
        val settings = directive.settings()
        val symbolProvider = directive.symbolProvider()
        val protocolGenerator = context.protocolGenerator
        val writers = context.writerDelegator()

        writers.useShapeWriter(shape) { writer: SwiftWriter ->
            StructureGenerator(model, symbolProvider, writer, shape, settings, protocolGenerator?.serviceErrorProtocolSymbol).renderErrors()
        }

        if (shape.hasTrait<SensitiveTrait>() || shape.members().any { it.isSensitive(model) }) {
            writers.useShapeExtensionWriter(shape, "CustomDebugStringConvertible") { writer: SwiftWriter ->
                CustomDebugStringConvertibleGenerator(symbolProvider, writer, shape, model).render()
            }
        }
    }

    override fun generateUnion(directive: GenerateUnionDirective<GenerationContext, SwiftSettings>) {
        val shape = directive.shape()
        val context = directive.context()
        val model = directive.model()
        val settings = directive.settings()
        val symbolProvider = directive.symbolProvider()
        val writers = context.writerDelegator()
        writers.useShapeWriter(shape) { writer: SwiftWriter -> UnionGenerator(model, symbolProvider, writer, shape, settings).render() }
    }

    override fun generateEnumShape(directive: GenerateEnumDirective<GenerationContext, SwiftSettings>) {
        val shape = directive.shape()
        val context = directive.context()
        val model = directive.model()
        val settings = directive.settings()
        val symbolProvider = directive.symbolProvider()
        val writers = context.writerDelegator()
        writers.useShapeWriter(shape) { writer: SwiftWriter -> EnumGenerator(model, symbolProvider, writer, shape, settings).render() }
    }

    override fun generateIntEnumShape(directive: GenerateIntEnumDirective<GenerationContext, SwiftSettings>) {
        val shape = directive.shape()
        val context = directive.context()
        val model = directive.model()
        val settings = directive.settings()
        val symbolProvider = directive.symbolProvider()
        val writers = context.writerDelegator()
        writers.useShapeWriter(shape) { writer: SwiftWriter -> IntEnumGenerator(model, symbolProvider, writer, shape.asIntEnumShape().get(), settings).render() }
    }

    private fun resolveProtocolGenerator(
        integrations: List<SwiftIntegration>,
        model: Model,
        service: ServiceShape,
        settings: SwiftSettings
    ): ProtocolGenerator? {
        val generators = integrations.flatMap { it.protocolGenerators }.associateBy { it.protocol }
        val serviceIndex = ServiceIndex.of(model)

        try {
            val protocolTrait = settings.resolveServiceProtocol(serviceIndex, service, generators.keys)
            return generators[protocolTrait]
        } catch (ex: UnresolvableProtocolException) {
            LOGGER.warning("Unable to find protocol generator for ${service.id}: ${ex.message}")
        }
        return null
    }
}
