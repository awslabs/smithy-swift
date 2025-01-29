package software.amazon.smithy.swift.codegen.integration.httpResponse

import software.amazon.smithy.aws.traits.customizations.S3UnwrappedXmlOutputTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.swift.codegen.SwiftDependency
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.HTTPProtocolCustomizable
import software.amazon.smithy.swift.codegen.integration.HttpBindingDescriptor
import software.amazon.smithy.swift.codegen.integration.HttpBindingResolver
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.bindingTraits.HTTPResponseTraitPayload
import software.amazon.smithy.swift.codegen.integration.httpResponse.bindingTraits.HTTPResponseTraitResponseCode
import software.amazon.smithy.swift.codegen.integration.middlewares.handlers.MiddlewareShapeUtils
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.AWSProtocol
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.awsProtocol
import software.amazon.smithy.swift.codegen.integration.serde.struct.readerSymbol
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyHTTPAPITypes
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftSymbol
import software.amazon.smithy.swift.codegen.utils.ModelFileUtils

class HTTPResponseBindingOutputGenerator(
    val customizations: HTTPProtocolCustomizable,
) {

    fun render(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        httpBindingResolver: HttpBindingResolver,
        defaultTimestampFormat: TimestampFormatTrait.Format,
    ) {
        if (op.output.isEmpty) { return }
        val outputShape = ctx.model.expectShape(op.outputShape)
        val outputSymbol = MiddlewareShapeUtils.outputSymbol(ctx.symbolProvider, ctx.model, op)
        val responseBindings = httpBindingResolver.responseBindings(op)
        val headerBindings = responseBindings
            .filter { it.location == HttpBinding.Location.HEADER }
            .sortedBy { it.memberName }
        val baseFilename = "${outputSymbol.name}+HttpResponseBinding"
        val filename = ModelFileUtils.filename(ctx.settings, baseFilename)
        val httpBindingSymbol = Symbol.builder()
            .definitionFile(filename)
            .name(outputSymbol.name)
            .build()

        ctx.delegator.useShapeWriter(httpBindingSymbol) { writer ->
            writer.openBlock("extension \$N {", "}", outputSymbol) {
                writer.write("")
                writer.openBlock(
                    "static func httpOutput(from httpResponse: \$N) async throws -> \$N {",
                    "}",
                    SmithyHTTPAPITypes.HTTPResponse,
                    outputSymbol,
                ) {
                    if (responseBindings.isEmpty()) {
                        writer.write("return \$N()", outputSymbol)
                    } else {
                        if (needsAReader(ctx, responseBindings)) {
                            writer.addImport(SwiftSymbol.make("ClientRuntime", null, SwiftDependency.CLIENT_RUNTIME, emptyList(), listOf("SmithyReadWrite")))
                            writer.write("let data = try await httpResponse.data()")
                            writer.write("let responseReader = try \$N.from(data: data)", ctx.service.readerSymbol)
                            writer.write("let reader = \$L", reader(ctx, op, writer))
                            if (ctx.service.awsProtocol == AWSProtocol.REST_JSON_1) {
                                writer.write("reader.respectsJSONName = true")
                            }
                        }
                        writer.write("var value = \$N()", outputSymbol)
                        HTTPResponseHeaders(ctx, false, headerBindings, defaultTimestampFormat, writer).render()
                        HTTPResponsePrefixHeaders(ctx, responseBindings, writer).render()
                        HTTPResponseTraitPayload(ctx, responseBindings, outputShape, writer, customizations).render()
                        HTTPResponseTraitResponseCode(ctx, responseBindings, writer).render()
                        writer.write("return value")
                    }
                }
            }
            writer.write("")
        }
    }

    private fun needsAReader(ctx: ProtocolGenerator.GenerationContext, responseBindings: List<HttpBindingDescriptor>): Boolean {
        return responseBindings
            .filter { !ctx.model.expectShape(it.member.target).hasTrait<StreamingTrait>() }
            .any { hasPayloadThatNeedsReader(ctx, it) || it.location == HttpBinding.Location.DOCUMENT }
    }

    private fun hasPayloadThatNeedsReader(ctx: ProtocolGenerator.GenerationContext, binding: HttpBindingDescriptor): Boolean {
        val targetShape = ctx.model.expectShape(binding.member.target)
        return binding.location == HttpBinding.Location.PAYLOAD &&
            (targetShape is StructureShape || targetShape is UnionShape) &&
            !targetShape.members().isEmpty()
    }

    private fun reader(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: SwiftWriter): String {
        return when (ctx.service.awsProtocol) {
            // AwsQuery responses are nested in an XML element named with the operation's name + "Result"
            AWSProtocol.AWS_QUERY -> writer.format("responseReader[\$S]", "${op.id.name}Result")
            // For RestXML, "unwrap" the reader when the operation has S3UnwrappedXmlOutputTrait.
            AWSProtocol.REST_XML -> "responseReader.unwrap()".takeIf { op.hasTrait<S3UnwrappedXmlOutputTrait>() } ?: "responseReader"
            // Other AWS protocols simply read the root element
            else -> "responseReader"
        }
    }
}
