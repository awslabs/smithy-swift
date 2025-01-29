/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen.integration

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.swift.codegen.SwiftDelegator
import software.amazon.smithy.swift.codegen.SwiftSettings
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.serde.TimestampHelpers
import software.amazon.smithy.swift.codegen.middleware.OperationMiddleware
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyTimestampsTypes
import software.amazon.smithy.utils.CaseUtils

/**
 * Smithy protocol code generator(s)
 */
interface ProtocolGenerator {
    companion object {
        /**
         * Sanitizes the name of the protocol so it can be used as a symbol.
         *
         * For example, the default implementation converts '.' to '_' and converts '-'
         * to become camelCase separated words. `aws.rest-json-1.1` becomes `AWSRestJson1_1`
         *
         * @param name Name of the protocol to sanitize
         * @return sanitized protocol name
         */
        fun getSanitizedName(name: String): String {
            var replacedString = name
            replacedString = replacedString.replace("^aws.".toRegex(), "AWS-")
            replacedString = replacedString.replace(".", "_")
            replacedString = CaseUtils.toCamelCase(replacedString, true, '-')
            return replacedString.replace("^Aws".toRegex(), "AWS")
        }

        fun getFormattedDateString(
            writer: SwiftWriter,
            tsFormat: TimestampFormatTrait.Format,
            memberName: String,
            urlEncode: Boolean = false
        ): String {
            val stringDateTerminator = if (urlEncode) ".urlPercentEncoding()" else ""
            val timestampFormat = TimestampHelpers.generateTimestampFormatEnumValue(tsFormat)
            return writer.format(
                "\$N(format: .$timestampFormat).string(from: $memberName)$stringDateTerminator",
                SmithyTimestampsTypes.TimestampFormatter,
            )
        }

        val DefaultServiceErrorProtocolSymbol: Symbol = ClientRuntimeTypes.Core.ServiceError

        val DefaultRetryErrorInfoProviderSymbol: Symbol = ClientRuntimeTypes.Core.DefaultRetryErrorInfoProvider
    }

    /**
     * Get the supported protocol [ShapeId]
     * e.g. `software.amazon.smithy.aws.traits.protocols.RestJson1Trait.ID`
     */
    val protocol: ShapeId

    /**
     * Get the name of the protocol
     */
    val protocolName: String
        get() {
            var prefix = protocol.namespace
            val idx = prefix.indexOf('.')
            if (idx != -1) {
                prefix = prefix.substring(0, idx)
            }
            return CaseUtils.toCamelCase(prefix) + getSanitizedName(protocol.name)
        }

    /**
     * Symbol that should be used as the base class for generated service errors.
     * It defaults to the ServiceError available in smithy-swift's client-runtime.
     */
    var serviceErrorProtocolSymbol: Symbol

    /**
     * Symbol that should be used when the deserialized service error type cannot be determined
     * It defaults to the UnknownServiceError available in smithy-swift's client-runtime.
     */
    val retryErrorInfoProviderSymbol: Symbol
        get() = DefaultRetryErrorInfoProviderSymbol

    /**
     * Generate serializers required by the protocol
     */
    fun generateSerializers(ctx: GenerationContext)

    /**
     * Generate deserializers required by the protocol
     */
    fun generateDeserializers(ctx: GenerationContext)

    /**
     * Generate message marshallable required by the protocol
     */
    fun generateMessageMarshallable(ctx: GenerationContext)

    /**
     * Generate message unmarshallable required by the protocol
     */
    fun generateMessageUnmarshallable(ctx: GenerationContext)

    /**
     * Generate serializers or deserializers for any nested types referenced by operation inputs/outputs
     */
    fun generateCodableConformanceForNestedTypes(ctx: GenerationContext)

    fun generateSchemas(ctx: GenerationContext)

    /**
     *
     * Generate unit tests for the protocol
     */
    fun generateProtocolUnitTests(ctx: GenerationContext): Int

    /**
     * Generate an actual client implementation of the service interface
     */
    fun generateProtocolClient(ctx: GenerationContext)

    /**
     * Generate smoke tests defined on the service model
     */
    fun generateSmokeTests(ctx: GenerationContext)

    fun initializeMiddleware(ctx: GenerationContext)

    fun getProtocolHttpBindingResolver(ctx: GenerationContext, defaultContentType: String): HttpBindingResolver =
        HttpTraitResolver(ctx, defaultContentType)

    val operationMiddleware: OperationMiddleware
    val customizations: HTTPProtocolCustomizable
    val defaultContentType: String

    /**
     * Context object used for service serialization and deserialization
     */
    data class GenerationContext(
        val settings: SwiftSettings,
        val model: Model,
        val service: ServiceShape,
        val symbolProvider: SymbolProvider,
        val integrations: List<SwiftIntegration>,
        val protocol: ShapeId,
        val delegator: SwiftDelegator
    )
}
