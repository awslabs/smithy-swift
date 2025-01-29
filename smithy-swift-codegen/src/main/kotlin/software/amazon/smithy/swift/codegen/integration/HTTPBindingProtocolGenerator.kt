/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen.integration

import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.neighbor.RelationshipType
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.BlobShape
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.TimestampShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpLabelTrait
import software.amazon.smithy.model.traits.HttpPayloadTrait
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait
import software.amazon.smithy.model.traits.HttpQueryParamsTrait
import software.amazon.smithy.model.traits.HttpQueryTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.RequiresLengthTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.customtraits.NeedsReaderTrait
import software.amazon.smithy.swift.codegen.customtraits.NeedsWriterTrait
import software.amazon.smithy.swift.codegen.customtraits.NestedTrait
import software.amazon.smithy.swift.codegen.events.MessageMarshallableGenerator
import software.amazon.smithy.swift.codegen.events.MessageUnmarshallableGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.HTTPResponseGenerator
import software.amazon.smithy.swift.codegen.integration.middlewares.AuthSchemeMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.ContentLengthMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.ContentMD5Middleware
import software.amazon.smithy.swift.codegen.integration.middlewares.ContentTypeMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.DeserializeMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.IdempotencyTokenMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.LoggingMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationInputBodyMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationInputHeadersMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationInputQueryItemMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationInputUrlHostMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.OperationInputUrlPathMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.RetryMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.SignerMiddleware
import software.amazon.smithy.swift.codegen.integration.middlewares.providers.HttpHeaderProvider
import software.amazon.smithy.swift.codegen.integration.middlewares.providers.HttpQueryItemProvider
import software.amazon.smithy.swift.codegen.integration.middlewares.providers.HttpUrlPathProvider
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.WireProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.responseWireProtocol
import software.amazon.smithy.swift.codegen.integration.serde.schema.SchemaGenerator
import software.amazon.smithy.swift.codegen.integration.serde.struct.StructDecodeGenerator
import software.amazon.smithy.swift.codegen.integration.serde.struct.StructEncodeGenerator
import software.amazon.smithy.swift.codegen.integration.serde.union.UnionDecodeGenerator
import software.amazon.smithy.swift.codegen.integration.serde.union.UnionEncodeGenerator
import software.amazon.smithy.swift.codegen.middleware.OperationMiddlewareGenerator
import software.amazon.smithy.swift.codegen.model.ShapeMetadata
import software.amazon.smithy.swift.codegen.model.findStreamingMember
import software.amazon.smithy.swift.codegen.model.hasEventStreamMember
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.isInputEventStream
import software.amazon.smithy.swift.codegen.model.isOutputEventStream
import software.amazon.smithy.swift.codegen.model.targetOrSelf
import software.amazon.smithy.swift.codegen.supportsStreamingAndIsRPC
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.utils.ModelFileUtils
import software.amazon.smithy.swift.codegen.utils.SchemaFileUtils
import software.amazon.smithy.utils.OptionalUtils
import java.util.Optional
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

private val Shape.isStreaming: Boolean
    get() = hasTrait<StreamingTrait>() && isUnionShape

/**
 * Checks to see if shape is in the body of the http request
 */
// TODO fix the edge case: a shape which is an operational input (i.e. has members bound to HTTP semantics) could be re-used elsewhere not as an operation input which means everything is in the body
fun Shape.isInHttpBody(): Boolean {
    val hasNoHttpTraitsOutsideOfPayload = !this.hasTrait(HttpLabelTrait::class.java) &&
        !this.hasTrait(HttpHeaderTrait::class.java) &&
        !this.hasTrait(HttpPrefixHeadersTrait::class.java) &&
        !this.hasTrait(HttpQueryTrait::class.java) &&
        !this.hasTrait(HttpQueryParamsTrait::class.java)
    return this.hasTrait(HttpPayloadTrait::class.java) || hasNoHttpTraitsOutsideOfPayload
}

/**
 * Adds the appropriate extension for serialization of special types i.e. timestamps, blobs, etc
 */
fun formatHeaderOrQueryValue(
    ctx: ProtocolGenerator.GenerationContext,
    writer: SwiftWriter,
    memberName: String,
    memberShape: MemberShape,
    location: HttpBinding.Location,
    bindingIndex: HttpBindingIndex,
    defaultTimestampFormat: TimestampFormatTrait.Format,
): Pair<String, Boolean> {
    return when (val shape = ctx.model.expectShape(memberShape.target)) {
        is TimestampShape -> {
            val timestampFormat = bindingIndex.determineTimestampFormat(memberShape, location, defaultTimestampFormat)
            Pair(ProtocolGenerator.getFormattedDateString(writer, timestampFormat, memberName), false)
        }
        is BlobShape -> {
            Pair("try $memberName.base64EncodedString()", true)
        }
        is StringShape -> {
            val enumRawValueSuffix = shape.getTrait(EnumTrait::class.java).map { ".rawValue" }.orElse("")
            var formattedItemValue = "$memberName$enumRawValueSuffix"
            var requiresDoCatch = false
            if (shape.hasTrait(MediaTypeTrait::class.java)) {
                formattedItemValue = "try $formattedItemValue.base64EncodedString()"
                requiresDoCatch = true
            }
            Pair(formattedItemValue, requiresDoCatch)
        }
        is IntEnumShape -> Pair("$memberName.rawValue", false)
        else -> Pair(memberName, false)
    }
}

/**
 * Abstract implementation useful for all HTTP protocols
 */
abstract class HTTPBindingProtocolGenerator(
    override val customizations: HTTPProtocolCustomizable,
) : ProtocolGenerator {
    private val LOGGER = Logger.getLogger(javaClass.name)
    private val idempotencyTokenValue = "idempotencyTokenGenerator.generateToken()"

    override var serviceErrorProtocolSymbol: Symbol = ClientRuntimeTypes.Http.HttpError

    override fun generateSerializers(ctx: ProtocolGenerator.GenerationContext) {
        // render conformance to HttpRequestBinding for all input shapes
        val inputShapesWithHttpBindings: MutableSet<ShapeId> = mutableSetOf()
        for (operation in getHttpBindingOperations(ctx)) {
            if (operation.input.isPresent) {
                val inputShapeId = operation.input.get()
                if (inputShapesWithHttpBindings.contains(inputShapeId)) {
                    // The input shape is referenced by more than one operation
                    continue
                }
                val httpBindingResolver = getProtocolHttpBindingResolver(ctx, defaultContentType)
                HttpUrlPathProvider.renderUrlPathMiddleware(ctx, operation, httpBindingResolver)
                HttpHeaderProvider.renderHeaderMiddleware(ctx, operation, httpBindingResolver, customizations.defaultTimestampFormat)
                HttpQueryItemProvider.renderQueryMiddleware(ctx, operation, httpBindingResolver, customizations.defaultTimestampFormat)
                inputShapesWithHttpBindings.add(inputShapeId)
            }
        }

        var inputShapesWithMetadata = resolveInputShapes(ctx)
        if (!supportsStreamingAndIsRPC(ctx.protocol)) {
            inputShapesWithMetadata = inputShapesWithMetadata.filter { !it.key.hasEventStreamMember(ctx.model) }
        }
        for ((shape, shapeMetadata) in inputShapesWithMetadata) {
            val symbol: Symbol = ctx.symbolProvider.toSymbol(shape)
            val symbolName = symbol.name
            val filename = ModelFileUtils.filename(ctx.settings, "$symbolName+Write")
            val encodeSymbol = Symbol.builder()
                .definitionFile(filename)
                .name(symbolName)
                .build()
            var httpBodyMembers = shape.members()
                .filter { it.isInHttpBody() }
                .toList()
            if (supportsStreamingAndIsRPC(ctx.protocol)) {
                // For RPC protocols that support event streaming, we need to send initial request
                // with streaming member excluded during encoding the input struct.
                httpBodyMembers = httpBodyMembers.filter {
                    !it.targetOrSelf(ctx.model).isStreaming
                }
            }
            if (httpBodyMembers.isNotEmpty() || shouldRenderEncodableConformance) {
                ctx.delegator.useShapeWriter(encodeSymbol) { writer ->
                    writer.openBlock(
                        "extension \$L {",
                        "}",
                        symbolName,
                    ) {
                        writer.write("")
                        renderStructEncode(ctx, shape, shapeMetadata, httpBodyMembers, writer)
                    }
                }
            }
        }
    }

    override fun generateDeserializers(ctx: ProtocolGenerator.GenerationContext) {
        val httpOperations = getHttpBindingOperations(ctx)
        val httpBindingResolver = getProtocolHttpBindingResolver(ctx, defaultContentType)
        httpResponseGenerator.render(ctx, httpOperations, httpBindingResolver)
    }

    override fun generateCodableConformanceForNestedTypes(ctx: ProtocolGenerator.GenerationContext) {
        val nestedShapes = resolveShapesNeedingCodableConformance(ctx)
            .filter { !it.isStreaming }
        for (shape in nestedShapes) {
            renderCodableExtension(ctx, shape)
        }
    }

    override fun generateSchemas(ctx: ProtocolGenerator.GenerationContext) {
        if (ctx.service.responseWireProtocol != WireProtocol.JSON) { return }
        val nestedShapes = resolveShapesNeedingSchema(ctx)
            .filter { !it.hasTrait<StreamingTrait>() }
            .filter {
                !(
                    it.asMemberShape().getOrNull()?.let {
                        ctx.model.expectShape(it.target).hasTrait<StreamingTrait>()
                    } ?: false
                    )
            }
        for (shape in nestedShapes) {
            renderSchemas(ctx, shape)
        }
    }

    private fun renderSchemas(ctx: ProtocolGenerator.GenerationContext, shape: Shape) {
        val symbol: Symbol = ctx.symbolProvider.toSymbol(shape)
        val symbolName = symbol.name
        val filename = SchemaFileUtils.filename(ctx.settings, "${shape.id.name}+Schema")
        val encodeSymbol = Symbol.builder()
            .definitionFile(filename)
            .name(symbolName)
            .build()
        ctx.delegator.useShapeWriter(encodeSymbol) { writer ->
            SchemaGenerator(ctx, writer).renderSchema(shape)
        }
    }

    fun renderCodableExtension(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
    ) {
        if (!shape.hasTrait<NeedsReaderTrait>() && !shape.hasTrait<NeedsWriterTrait>()) { return }
        val shapeUsesSchemaBasedRead = ctx.service.responseWireProtocol == WireProtocol.JSON && shape.hasTrait<NestedTrait>()
        if (shapeUsesSchemaBasedRead && !shape.hasTrait<NeedsWriterTrait>()) { return }
        val symbol: Symbol = ctx.symbolProvider.toSymbol(shape)
        val symbolName = symbol.name
        val filename = ModelFileUtils.filename(ctx.settings, "$symbolName+ReadWrite")
        val encodeSymbol = Symbol.builder()
            .definitionFile(filename)
            .name(symbolName)
            .build()
        ctx.delegator.useShapeWriter(encodeSymbol) { writer ->
            writer.openBlock("extension \$N {", "}", symbol) {
                val members = shape.members().toList()
                when (shape) {
                    is StructureShape -> {
                        // get all members sorted by name and filter out either all members with other traits OR members with the payload trait
                        val httpBodyMembers = members.filter { it.isInHttpBody() }
                        if (shape.hasTrait<NeedsWriterTrait>()) {
                            writer.write("")
                            renderStructEncode(ctx, shape, mapOf(), httpBodyMembers, writer)
                        }
                        if (shape.hasTrait<NeedsReaderTrait>() && !shapeUsesSchemaBasedRead) {
                            writer.write("")
                            renderStructDecode(ctx, shape, mapOf(), httpBodyMembers, writer)
                        }
                    }
                    is UnionShape -> {
                        if (shape.hasTrait<NeedsWriterTrait>()) {
                            writer.write("")
                            UnionEncodeGenerator(ctx, shape, members, writer).render()
                        }
                        if (shape.hasTrait<NeedsReaderTrait>() && !shapeUsesSchemaBasedRead) {
                            writer.write("")
                            UnionDecodeGenerator(ctx, shape, members, writer).render()
                        }
                    }
                }
            }
        }
    }

    private fun resolveInputShapes(ctx: ProtocolGenerator.GenerationContext): Map<Shape, Map<ShapeMetadata, Any>> {
        var shapesInfo: MutableMap<Shape, Map<ShapeMetadata, Any>> = mutableMapOf()
        val operations = getHttpBindingOperations(ctx)
        for (operation in operations) {
            val inputType = ctx.model.expectShape(operation.input.get())
            var metadata = mapOf<ShapeMetadata, Any>(
                Pair(ShapeMetadata.OPERATION_SHAPE, operation),
                Pair(ShapeMetadata.SERVICE_VERSION, ctx.service.version),
            )
            shapesInfo.put(inputType, metadata)
        }
        return shapesInfo
    }

    fun resolveErrorShapes(ctx: ProtocolGenerator.GenerationContext): Set<Shape> {
        val operationErrorShapes = getHttpBindingOperations(ctx)
            .flatMap { it.errors }
            .map { ctx.model.expectShape(it) }
            .toSet()

        val serviceErrorShapes = ctx.service.errors.map {
            ctx.model.expectShape(it)
        }.toSet()

        return operationErrorShapes.filter { shape ->
            shape.members().any { it.isInHttpBody() }
        }.toMutableSet() + serviceErrorShapes.filter { shape ->
            shape.members().any { it.isInHttpBody() }
        }.toMutableSet()
    }

    private fun resolveShapesNeedingCodableConformance(ctx: ProtocolGenerator.GenerationContext): Set<Shape> {
        val topLevelOutputMembers = getHttpBindingOperations(ctx).flatMap {
            val outputShape = ctx.model.expectShape(it.output.get())
            outputShape.members()
        }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        val topLevelErrorMembers = getHttpBindingOperations(ctx)
            .flatMap { it.errors }
            .flatMap { ctx.model.expectShape(it).members() }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        val topLevelServiceErrorMembers = ctx.service.errors
            .flatMap { ctx.model.expectShape(it).members() }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        val topLevelInputMembers = getHttpBindingOperations(ctx).flatMap {
            val inputShape = ctx.model.expectShape(it.input.get())
            inputShape.members()
        }
            .map { ctx.model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        val allTopLevelMembers =
            topLevelOutputMembers
                .union(topLevelErrorMembers)
                .union(topLevelServiceErrorMembers)
                .union(topLevelInputMembers)

        val nestedTypes = walkNestedShapesRequiringSerde(ctx, allTopLevelMembers)
        return nestedTypes
    }

    private fun walkNestedShapesRequiringSerde(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>): Set<Shape> {
        val resolved = mutableSetOf<Shape>()
        val walker = Walker(ctx.model)

        // walk all the shapes in the set and find all other
        // structs/unions (or collections thereof) in the graph from that shape
        shapes.forEach { shape ->
            walker.iterateShapes(shape) { relationship ->
                when (relationship.relationshipType) {
                    RelationshipType.MEMBER_TARGET,
                    RelationshipType.STRUCTURE_MEMBER,
                    RelationshipType.LIST_MEMBER,
                    RelationshipType.SET_MEMBER,
                    RelationshipType.MAP_VALUE,
                    RelationshipType.UNION_MEMBER,
                    -> true
                    else -> false
                }
            }.forEach {
                when (it) {
                    is UnionShape -> resolved.add(it)
                    is StructureShape -> resolved.add(it)
                }
            }
        }
        return resolved
    }

    private fun resolveShapesNeedingSchema(ctx: ProtocolGenerator.GenerationContext): Set<Shape> {
        val topLevelOutputMembers = getHttpBindingOperations(ctx)
            .map { ctx.model.expectShape(it.output.get()) }
            .toSet()

        val topLevelErrorMembers = getHttpBindingOperations(ctx)
            .flatMap { it.errors }
            .map { ctx.model.expectShape(it) }
            .toSet()

        val topLevelServiceErrorMembers = ctx.service.errors
            .map { ctx.model.expectShape(it) }
            .toSet()

        // Input members excluded from schema generation until schema-based deserialization is implemented

//        val topLevelInputMembers = getHttpBindingOperations(ctx).flatMap {
//            val inputShape = ctx.model.expectShape(it.input.get())
//            inputShape.members()
//        }
//            .map { ctx.model.expectShape(it.target) }
//            .toSet()

        val allTopLevelMembers =
            topLevelOutputMembers
                .union(topLevelErrorMembers)
                .union(topLevelServiceErrorMembers)
//                .union(topLevelInputMembers)

        return walkNestedShapesRequiringSchema(ctx, allTopLevelMembers)
    }

    private fun walkNestedShapesRequiringSchema(ctx: ProtocolGenerator.GenerationContext, shapes: Set<Shape>): Set<Shape> {
        val resolved = mutableSetOf<Shape>()
        val walker = Walker(ctx.model)

        // walk all the shapes in the set and find all other
        // structs/unions (or collections thereof) in the graph from that shape
        shapes.forEach { shape ->
            walker.iterateShapes(shape) { relationship ->
                when (relationship.relationshipType) {
                    RelationshipType.MEMBER_TARGET,
                    RelationshipType.STRUCTURE_MEMBER,
                    RelationshipType.LIST_MEMBER,
                    RelationshipType.SET_MEMBER,
                    RelationshipType.MAP_KEY,
                    RelationshipType.MAP_VALUE,
                    RelationshipType.UNION_MEMBER,
                    -> true
                    else -> false
                }
            }.forEach {
                // Don't generate schemas for Smithy built-in / "prelude" shapes.
                // Those are included in runtime.
                if (it.id.namespace != "smithy.api") { resolved.add(it) }
            }
        }
        return resolved
    }

    // Checks for @requiresLength trait
    // Returns true if the operation:
    // - has a streaming member with @httpPayload trait
    // - target is a blob shape with @requiresLength trait
    private fun hasRequiresLengthTrait(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean {
        if (op.input.isPresent) {
            val inputShape = ctx.model.expectShape(op.input.get())
            val streamingMember = inputShape.findStreamingMember(ctx.model)
            if (streamingMember != null) {
                val targetShape = ctx.model.expectShape(streamingMember.target)
                if (targetShape != null) {
                    return streamingMember.hasTrait<HttpPayloadTrait>() &&
                        targetShape.isBlobShape &&
                        targetShape.hasTrait<RequiresLengthTrait>()
                }
            }
        }
        return false
    }

    // Checks for @unsignedPayload trait on an operation
    private fun hasUnsignedPayloadTrait(op: OperationShape): Boolean = op.hasTrait<UnsignedPayloadTrait>()

    override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        ctx.delegator.useFileWriter("Sources/${ctx.settings.moduleName}/${symbol.name}.swift") { writer ->
            val serviceSymbol = ctx.symbolProvider.toSymbol(ctx.service)
            val clientGenerator = httpProtocolClientGeneratorFactory.createHttpProtocolClientGenerator(
                ctx,
                getProtocolHttpBindingResolver(ctx, defaultContentType),
                writer,
                serviceSymbol.name,
                defaultContentType,
                customizations,
                operationMiddleware,
            )
            clientGenerator.render()
        }
    }

    override fun generateSmokeTests(ctx: ProtocolGenerator.GenerationContext) {
        return SmokeTestGenerator(ctx).generateSmokeTests()
    }

    override fun initializeMiddleware(ctx: ProtocolGenerator.GenerationContext) {
        val resolver = getProtocolHttpBindingResolver(ctx, defaultContentType)
        for (operation in getHttpBindingOperations(ctx)) {
            /*
             * Note: the order of middlewares here does not reflect the order of execution in the actual client call.
             * The order here simply means the order in which middleware are added to CODEGEN middleware stack.
             */
            operationMiddleware.appendMiddleware(operation, IdempotencyTokenMiddleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, ContentMD5Middleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, OperationInputUrlPathMiddleware(ctx.model, ctx.symbolProvider, ""))
            operationMiddleware.appendMiddleware(operation, OperationInputUrlHostMiddleware(ctx.model, ctx.symbolProvider, operation))
            operationMiddleware.appendMiddleware(operation, OperationInputHeadersMiddleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, OperationInputQueryItemMiddleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, ContentTypeMiddleware(ctx.model, ctx.symbolProvider, resolver.determineRequestContentType(operation)))
            operationMiddleware.appendMiddleware(operation, OperationInputBodyMiddleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, ContentLengthMiddleware(ctx.model, shouldRenderEncodableConformance, hasRequiresLengthTrait(ctx, operation), hasUnsignedPayloadTrait(operation)))
            operationMiddleware.appendMiddleware(operation, DeserializeMiddleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, LoggingMiddleware(ctx.model, ctx.symbolProvider))
            operationMiddleware.appendMiddleware(operation, RetryMiddleware(ctx.model, ctx.symbolProvider, retryErrorInfoProviderSymbol))
            operationMiddleware.appendMiddleware(operation, SignerMiddleware(ctx.model, ctx.symbolProvider))
            addProtocolSpecificMiddleware(ctx, operation)
            operationMiddleware.appendMiddleware(operation, AuthSchemeMiddleware(ctx.model, ctx.symbolProvider))
            for (integration in ctx.integrations) {
                integration.customizeMiddleware(ctx, operation, operationMiddleware)
            }
            // must be last to support adding business metrics
            addUserAgentMiddleware(ctx, operation)
        }
    }

    override val operationMiddleware = OperationMiddlewareGenerator()

    protected abstract val httpProtocolClientGeneratorFactory: HttpProtocolClientGeneratorFactory

    protected val httpResponseGenerator = HTTPResponseGenerator(customizations)

    protected abstract val shouldRenderEncodableConformance: Boolean

    private fun renderStructEncode(
        ctx: ProtocolGenerator.GenerationContext,
        shapeContainingMembers: Shape,
        shapeMetadata: Map<ShapeMetadata, Any>,
        members: List<MemberShape>,
        writer: SwiftWriter,
    ) {
        StructEncodeGenerator(ctx, shapeContainingMembers, members, shapeMetadata, writer).render()
    }

    private fun renderStructDecode(
        ctx: ProtocolGenerator.GenerationContext,
        shapeContainingMembers: Shape,
        shapeMetadata: Map<ShapeMetadata, Any>,
        members: List<MemberShape>,
        writer: SwiftWriter,
    ) {
        StructDecodeGenerator(ctx, shapeContainingMembers, members, shapeMetadata, writer).render()
    }

    protected abstract fun addProtocolSpecificMiddleware(ctx: ProtocolGenerator.GenerationContext, operation: OperationShape)

    protected abstract fun addUserAgentMiddleware(ctx: ProtocolGenerator.GenerationContext, operation: OperationShape)

    /**
     * Get the operations with HTTP Bindings.
     *
     * @param ctx the generation context
     * @return the list of operation shapes
     */
    open fun getHttpBindingOperations(ctx: ProtocolGenerator.GenerationContext): List<OperationShape> {
        val topDownIndex: TopDownIndex = TopDownIndex.of(ctx.model)
        val containedOperations: MutableList<OperationShape> = mutableListOf()
        for (operation in topDownIndex.getContainedOperations(ctx.service)) {
            OptionalUtils.ifPresentOrElse(
                Optional.of(getProtocolHttpBindingResolver(ctx, defaultContentType).httpTrait(operation)::class.java),
                { containedOperations.add(operation) },
            ) {
                LOGGER.warning(
                    "Unable to fetch $protocolName protocol request bindings for ${operation.id} because " +
                        "it does not have an http binding trait",
                )
            }
        }
        return containedOperations
    }

    fun outputStreamingShapes(ctx: ProtocolGenerator.GenerationContext): MutableSet<MemberShape> {
        val streamingShapes = mutableMapOf<ShapeId, MemberShape>()
        val streamingOperations = getHttpBindingOperations(ctx).filter { it.isOutputEventStream(ctx.model) }
        streamingOperations.forEach { operation ->
            val input = operation.output.get()
            val streamingMember = ctx.model.expectShape(input).findStreamingMember(ctx.model)
            streamingMember?.let {
                val targetType = ctx.model.expectShape(it.target)
                streamingShapes[targetType.id] = it
            }
        }
        return streamingShapes.values.toMutableSet()
    }

    fun inputStreamingShapes(ctx: ProtocolGenerator.GenerationContext): MutableSet<UnionShape> {
        val streamingShapes = mutableSetOf<UnionShape>()
        val streamingOperations = getHttpBindingOperations(ctx).filter { it.isInputEventStream(ctx.model) }
        streamingOperations.forEach { operation ->
            val input = operation.input.get()
            val streamingMember = ctx.model.expectShape(input).findStreamingMember(ctx.model)
            streamingMember?.let {
                val targetType = ctx.model.expectShape(it.target)
                streamingShapes.add(targetType as UnionShape)
            }
        }
        return streamingShapes
    }

    override fun generateMessageMarshallable(ctx: ProtocolGenerator.GenerationContext) {
        var streamingShapes = inputStreamingShapes(ctx)
        val messageMarshallableGenerator = MessageMarshallableGenerator(ctx, defaultContentType)
        streamingShapes.forEach { streamingMember ->
            messageMarshallableGenerator.render(streamingMember)
        }
    }

    override fun generateMessageUnmarshallable(ctx: ProtocolGenerator.GenerationContext) {
        var streamingShapes = outputStreamingShapes(ctx)
        val messageUnmarshallableGenerator = MessageUnmarshallableGenerator(ctx, customizations)
        streamingShapes.forEach { streamingMember ->
            messageUnmarshallableGenerator.render(streamingMember)
        }
    }
}

class DefaultServiceConfig(writer: SwiftWriter, serviceName: String) :
    ServiceConfig(writer, serviceName, serviceName)
