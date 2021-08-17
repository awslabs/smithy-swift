/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.integration

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.swift.codegen.SwiftSettings
import software.amazon.smithy.swift.codegen.SwiftWriter

/**
 * Kotlin SPI for customizing Swift code generation, registering
 * new protocol code generators, renaming shapes, modifying the model,
 * adding custom code, etc.
 */
interface SwiftIntegration {
    /**
     * Gets the sort order of the customization from -128 to 127.
     *
     *
     * Customizations are applied according to this sort order. Lower values
     * are executed before higher values (for example, -128 comes before 0,
     * comes before 127). Customizations default to 0, which is the middle point
     * between the minimum and maximum order values. The customization
     * applied later can override the runtime configurations that provided
     * by customizations applied earlier.
     *
     * @return Returns the sort order, defaulting to 0.
     */
    val order: Byte
        get() = 0

    /**
     * Preprocess the model before code generation.
     *
     *
     * This can be used to remove unsupported features, remove traits
     * from shapes (e.g., make members optional), etc.
     *
     * @param model model definition.
     * @param settings Setting used to generate.
     * @return Returns the updated model.
     */
    fun preprocessModel(model: Model, settings: SwiftSettings): Model = model

    /**
     * Updates the [SymbolProvider] used when generating code.
     *
     *
     * This can be used to customize the names of shapes, the package
     * that code is generated into, add dependencies, add imports, etc.
     *
     * @param settings Setting used to generate.
     * @param model Model being generated.
     * @param symbolProvider The original `SymbolProvider`.
     * @return The decorated `SymbolProvider`.
     */
    fun decorateSymbolProvider(
        settings: SwiftSettings,
        model: Model,
        symbolProvider: SymbolProvider
    ): SymbolProvider {
        return symbolProvider
    }

    /**
     * Called each time a writer is used that defines a shape.
     *
     *
     * This method could be called multiple times for the same writer
     * but for different shapes. It gives an opportunity to intercept code
     * sections of a [SwiftWriter] by name using the shape for
     * context. For example:
     *
     * <pre>
     * `public class MyIntegration: SwiftIntegration {
     * fun onWriterUse(settings: SwiftSettings, model:Model, symbolProvider:SymbolProvider,
     * writer: SwiftWriter, definedShape: Shape) {
     * writer.onSection("example", text -&gt; writer.write("Intercepted: " + text"));
     * }
     * }
     `</pre> *
     *
     *
     * Any mutations made on the writer (for example, adding
     * section interceptors) are removed after the callback has completed;
     * the callback is invoked in between pushing and popping state from
     * the writer.
     *
     * @param settings Settings used to generate.
     * @param model Model to generate from.
     * @param symbolProvider Symbol provider used for codegen.
     * @param writer Writer that will be used.
     * @param definedShape Shape that is being defined in the writer.
     */
    fun onShapeWriterUse(
        settings: SwiftSettings,
        model: Model,
        symbolProvider: SymbolProvider,
        writer: SwiftWriter,
        definedShape: Shape
    ) {
        // pass
    }

    /**
     * Customize the middleware to use when generating a protocol client/service implementation. By default
     * the [resolved] is returned unmodified. Integrations are allowed to add/remove/re-order the middleware.
     *
     * NOTE: Protocol generators should only allow integrations to customize AFTER they have resolved the default set
     * of middleware for the protocol (if any).
     *
     * @param ctx The codegen generation context
     * @param resolved The middleware resolved by the protocol generator
     */
    fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<OperationMiddlewareRenderable>
    ): List<OperationMiddlewareRenderable> {
        return resolved
    }

    /**
     * Get the list of protocol generators to register
     */
    val protocolGenerators: List<ProtocolGenerator>
        get() = listOf()

    /**
     * Allows integration to specify [SectionWriterBinding]s to
     * override or change codegen at specific, defined points.
     * See [SectionWriter] for more details.
     */
    val sectionWriters: List<SectionWriterBinding>
        get() = listOf()

    /**
     * Determines if the integration should be applied to the current [ServiceShape].
     * Implementing this method allows to apply integrations to specific services.
     *
     * @param service The service under codegen
     * @return true if the Integration should be applied to the current codegen context, false otherwise.
     */
    fun enabledForService(model: Model, settings: SwiftSettings): Boolean = true
}
