/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.integration.httpResponse

import software.amazon.smithy.aws.traits.protocols.AwsQueryCompatibleTrait
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.swift.codegen.SwiftDependency
import software.amazon.smithy.swift.codegen.integration.HTTPProtocolCustomizable
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.AWSProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.awsProtocol
import software.amazon.smithy.swift.codegen.integration.serde.struct.readerSymbol
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.toUpperCamelCase
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyHTTPAPITypes
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftSymbol
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes
import software.amazon.smithy.swift.codegen.utils.ModelFileUtils
import software.amazon.smithy.swift.codegen.utils.errorShapeName

class HTTPResponseBindingErrorGenerator(
    val customizations: HTTPProtocolCustomizable,
) {

    fun renderServiceError(ctx: ProtocolGenerator.GenerationContext) {
        val serviceShape = ctx.service
        val serviceName = ctx.service.id.name
        val filename = ModelFileUtils.filename(ctx.settings, "$serviceName+HTTPServiceError")

        ctx.delegator.useFileWriter(filename) { writer ->
            with(writer) {
                openBlock(
                    "func httpServiceError(baseError: \$N) throws -> \$N? {",
                    "}",
                    customizations.baseErrorSymbol,
                    SwiftTypes.Error
                ) {
                    customizations.serviceErrorCustomRenderer(ctx)?.let { it.render(writer) }
                    val serviceErrorShapes =
                        serviceShape.errors
                            .map { ctx.model.expectShape(it) as StructureShape }
                            .toSet()
                            .sorted()
                    if (serviceErrorShapes.isNotEmpty()) {
                        openBlock("switch baseError.code {", "}") {
                            serviceErrorShapes.forEach { errorShape ->
                                val errorShapeName = errorShape.errorShapeName(ctx.symbolProvider)
                                val errorShapeType = ctx.symbolProvider.toSymbol(errorShape).name
                                write(
                                    "case \$S: return try \$L.makeError(baseError: baseError)",
                                    errorShapeName,
                                    errorShapeType
                                )
                            }
                            write("default: return nil")
                        }
                    } else {
                        write("return nil")
                    }
                }
            }
        }
    }

    fun renderOperationError(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, unknownServiceErrorSymbol: Symbol) {
        val operationErrorName = "${op.toUpperCamelCase()}OutputError"
        val filename = ModelFileUtils.filename(ctx.settings, "$operationErrorName+HttpResponseErrorBinding")
        val httpBindingSymbol = Symbol.builder()
            .definitionFile(filename)
            .name(operationErrorName)
            .build()

        ctx.delegator.useShapeWriter(httpBindingSymbol) { writer ->
            writer.openBlock("enum \$L {", "}", operationErrorName) {
                writer.write("")
                writer.openBlock(
                    "static func httpError(from httpResponse: \$N) async throws -> \$N {", "}",
                    SmithyHTTPAPITypes.HTTPResponse,
                    SwiftTypes.Error,
                ) {
                    val errorShapes = op.errors
                        .map { ctx.model.expectShape(it) as StructureShape }
                        .toSet()
                        .sorted()
                    writer.addImport(SwiftSymbol.make("ClientRuntime", null, SwiftDependency.CLIENT_RUNTIME, emptyList(), listOf("SmithyReadWrite")))
                    writer.write("let data = try await httpResponse.data()")
                    writer.write("let responseReader = try \$N.from(data: data)", ctx.service.readerSymbol)
                    if (ctx.service.awsProtocol == AWSProtocol.REST_JSON_1) {
                        writer.write("responseReader.respectsJSONName = true")
                    }
                    val noErrorWrapping = ctx.service.getTrait<RestXmlTrait>()?.isNoErrorWrapping ?: false
                    if (ctx.service.hasTrait<AwsQueryCompatibleTrait>()) {
                        writer.write("let errorDetails = httpResponse.headers.value(for: \"x-amzn-query-error\")")
                        writer.write(
                            "let baseError = try \$N.makeQueryCompatibleError(httpResponse: httpResponse, responseReader: responseReader, noErrorWrapping: \$L, errorDetails: errorDetails)",
                            customizations.baseErrorSymbol,
                            noErrorWrapping
                        )
                    } else {
                        writer.write(
                            "let baseError = try \$N(httpResponse: httpResponse, responseReader: responseReader, noErrorWrapping: \$L)",
                            customizations.baseErrorSymbol,
                            noErrorWrapping
                        )
                    }
                    writer.write("if let error = baseError.customError() { return error }")
                    if (ctx.service.errors.isNotEmpty() || customizations.serviceErrorCustomRenderer(ctx) != null) {
                        writer.write("if let error = try httpServiceError(baseError: baseError) { return error }")
                    }
                    writer.openBlock("switch baseError.code {", "}") {
                        errorShapes.forEach { errorShape ->
                            val errorShapeName = errorShape.errorShapeName(ctx.symbolProvider)
                            val errorShapeType = ctx.symbolProvider.toSymbol(errorShape).name
                            writer.write(
                                "case \$S: return try \$L.makeError(baseError: baseError)",
                                errorShapeName,
                                errorShapeType
                            )
                        }
                        writer.write(
                            "default: return try \$N.makeError(baseError: baseError)",
                            unknownServiceErrorSymbol
                        )
                    }
                }
            }
        }
    }
}
