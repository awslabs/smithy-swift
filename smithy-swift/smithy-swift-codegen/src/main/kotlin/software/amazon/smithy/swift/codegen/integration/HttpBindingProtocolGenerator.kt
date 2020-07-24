/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.swift.codegen.integration

import java.util.logging.Logger
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*
import software.amazon.smithy.swift.codegen.*
import software.amazon.smithy.utils.OptionalUtils

/**
 * Abstract implementation useful for all HTTP protocols
 */
abstract class HttpBindingProtocolGenerator : ProtocolGenerator {
    private val LOGGER = Logger.getLogger(javaClass.name)

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
                renderInputRequestConformanceToHttpRequestBinding(ctx, operation)
                inputShapesWithHttpBindings.add(inputShapeId)
            }
        }
    }

    /**
     * Generate conformace to (HttpRequestBinding) for the input request (if not already present)
     * and implement (buildRequest) method
     */
    private fun renderInputRequestConformanceToHttpRequestBinding(ctx: ProtocolGenerator.GenerationContext, op: OperationShape) {
        if (op.input.isEmpty()) {
            return
        }

        val inputShape = ctx.model.expectShape(op.input.get())
        val opIndex = ctx.model.getKnowledge(OperationIndex::class.java)
        val inputShapeName = ServiceGenerator.getOperationInputShapeName(ctx.symbolProvider, opIndex, op)

        val bindingIndex = ctx.model.getKnowledge(HttpBindingIndex::class.java)
        val requestBindings = bindingIndex.getRequestBindings(op)
        val queryBindings = requestBindings.values.filter { it.location == HttpBinding.Location.QUERY }
        val headerBindings = requestBindings.values
            .filter { it.location == HttpBinding.Location.HEADER }
            .sortedBy { it.memberName }
        val contentType = bindingIndex.determineRequestContentType(op, defaultContentType).orElse(defaultContentType)

        val prefixHeaderBindings = requestBindings.values
            .filter { it.location == HttpBinding.Location.PREFIX_HEADERS }

        ctx.delegator.useShapeWriter(inputShape) { writer ->
            writer.openBlock("extension ${inputShapeName!!.get()}: HttpRequestBinding {", "}") {
                writer.openBlock("func buildHttpRequest(method: HttpMethodType, path: String) -> HttpRequest {", "}") {
                    renderQueryItems(ctx, queryBindings, writer)
                    // TODO:: Replace host appropriately
                    writer.write("let endpoint = Endpoint(host: \"my-api.us-east-2.amazonaws.com\", path: path, queryItems: queryItems)")
                    renderHeaders(ctx, headerBindings, prefixHeaderBindings, writer, contentType)
                    writer.write("return HttpRequest(method: method, endpoint: endpoint, headers: headers)")
                }
            }
            writer.write("")
        }
    }

    private fun renderQueryItems(ctx: ProtocolGenerator.GenerationContext, queryBindings: List<HttpBinding>, writer: SwiftWriter) {
        writer.write("var queryItems: [URLQueryItem] = [URLQueryItem]()")
        if (queryBindings.isNotEmpty()) {
            writer.write("var queryItem: URLQueryItem")
        }
        queryBindings.forEach {
            var memberName = it.member.memberName
            val memberTarget = ctx.model.expectShape(it.member.target)
            val paramName = it.locationName

            writer.openBlock("if let $memberName = $memberName {", "}") {
                if (memberTarget is CollectionShape) {
                    if (memberTarget.member.hasTrait(EnumTrait::class.java)) {
                        // TODO:: use rawValue for Enum Collection
                        print("found enum collection!")
                    }
                    // Handle cases where member is a List or Set type
                    writer.openBlock("$memberName.forEach { queryItemValue in ", "}") {
                        writer.write("queryItem = URLQueryItem(name: \"$paramName\", value: queryItemValue)")
                        writer.write("queryItems.append(queryItem)")
                    }
                } else {
                    if (memberTarget.type == ShapeType.STRING &&
                        memberTarget.getTrait(EnumTrait::class.java).isPresent) {
                        memberName = "$memberName.rawValue"
                    }
                    writer.write("queryItem = URLQueryItem(name: \"$paramName\", value: $memberName)")
                    writer.write("queryItems.append(queryItem)")
                }
            }
        }
    }

    private fun renderHeaders(ctx: ProtocolGenerator.GenerationContext, headerBindings: List<HttpBinding>, prefixHeaderBindings: List<HttpBinding>, writer: SwiftWriter, contentType: String) {
        writer.write("var headers = HttpHeaders()")
        writer.write("headers.add(name: \"Content-Type\", value: $contentType)")
        headerBindings.forEach {
            var memberName = it.member.memberName
            val memberTarget = ctx.model.expectShape(it.member.target)
            val paramName = it.locationName
            writer.openBlock("if let $memberName = $memberName {", "}") {
                if (memberTarget is CollectionShape) {
                    // TODO:: use rawValue for Enum Collection
                    writer.openBlock("$memberName.forEach { headerValue in ", "}") {
                        writer.write("headers.add(name: \"$paramName\", value: headerValue)")
                    }
                } else {
                    if (memberTarget.type == ShapeType.STRING &&
                        memberTarget.getTrait(EnumTrait::class.java).isPresent) {
                        memberName = "$memberName.rawValue"
                    }
                    writer.write("headers.add(name: \"$paramName\", value: $memberName)")
                }
            }
        }

        prefixHeaderBindings.forEach {
            val memberName = it.member.memberName
            val paramName = it.locationName

            writer.openBlock("if let $memberName = $memberName {", "}") {
                writer.openBlock("for (headerKey, headerValue) in $memberName { ", "}") {
                    writer.write("headers.add(name: \"$paramName\"headerKey, value: headerValue)")
                }
            }
        }
    }

    override fun generateDeserializers(ctx: ProtocolGenerator.GenerationContext) {
        // render HttpDeserialize for all operation outputs, render normal serde for all shapes that appear as nested on any operation output
        // TODO("Not yet implemented")
    }

    override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = ctx.symbolProvider.toSymbol(ctx.service)
        ctx.delegator.useFileWriter("./${ctx.settings.moduleName}/${symbol.name}.swift") { writer ->
            val features = getHttpFeatures(ctx)
            HttpProtocolClientGenerator(ctx.model, ctx.symbolProvider, writer, ctx.service, features).render()
        }
    }

    /**
     * The default content-type when a document is synthesized in the body.
     */
    protected abstract val defaultContentType: String

    /**
     * Get all of the features that are used as middleware
     */
    open fun getHttpFeatures(ctx: ProtocolGenerator.GenerationContext): List<HttpFeature> = listOf()

    /**
     * Get the operations with HTTP Bindings.
     *
     * @param ctx the generation context
     * @return the list of operation shapes
     */
    open fun getHttpBindingOperations(ctx: ProtocolGenerator.GenerationContext): List<OperationShape> {
        val topDownIndex: TopDownIndex = ctx.model.getKnowledge(TopDownIndex::class.java)
        val containedOperations: MutableList<OperationShape> = mutableListOf()
        for (operation in topDownIndex.getContainedOperations(ctx.service)) {
            OptionalUtils.ifPresentOrElse(
                operation.getTrait(HttpTrait::class.java),
                { containedOperations.add(operation) }
            ) {
                LOGGER.warning(
                    "Unable to fetch $protocol protocol request bindings for ${operation.id} because " +
                            "it does not have an http binding trait"
                )
            }
        }
        return containedOperations
    }
}
