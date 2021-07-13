package software.amazon.smithy.swift.codegen.integration.httpResponse.bindingTraits

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.HttpBindingDescriptor
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.HttpResponseBindingRenderable
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.isEnum

class HttpResponseTraitWithHttpPayload(
    val ctx: ProtocolGenerator.GenerationContext,
    val binding: HttpBindingDescriptor,
    val writer: SwiftWriter
) : HttpResponseBindingRenderable {
    override fun render() {
        val memberName = ctx.symbolProvider.toMemberName(binding.member)
        val target = ctx.model.expectShape(binding.member.target)
        val symbol = ctx.symbolProvider.toSymbol(target)
        // TODO: properly support event streams and other binary stream types besides blob
        val isBinaryStream =
            ctx.model.getShape(binding.member.target).get().hasTrait<StreamingTrait>() && target.type == ShapeType.BLOB
        val bodyType = if (isBinaryStream) ".stream" else ".data"
        val additionalUnwrap = if (!isBinaryStream) ",\n    let unwrappedData = data" else ""
        val data = if (isBinaryStream) "data" else "unwrappedData"
        writer.openBlock("if case $bodyType(let data) = httpResponse.body$additionalUnwrap {", "} else {") {
            when (target.type) {
                ShapeType.DOCUMENT -> {
                    writer.openBlock("if let responseDecoder = decoder {", "} else {") {
                        writer.write(
                            "let output: \$L = try responseDecoder.decode(responseBody: $data)",
                            symbol.name
                        )
                        writer.write("self.\$L = output", memberName)
                    }
                    writer.indent()
                    writer.write("self.\$L = nil", memberName).closeBlock("}")
                }
                ShapeType.STRING -> {
                    writer.openBlock("if let output = String(data: $data, encoding: .utf8) {", "} else {") {
                        if (target.isEnum) {
                            writer.write("self.\$L = \$L(rawValue: output)", memberName, symbol)
                        } else {
                            writer.write("self.\$L = output", memberName)
                        }
                    }
                    writer.indent()
                    writer.write("self.\$L = nil", memberName).closeBlock("}")
                }
                ShapeType.BLOB -> {
                    writer.write("self.\$L = $data", memberName)
                }
                ShapeType.STRUCTURE, ShapeType.UNION -> {
                    writer.openBlock("if let responseDecoder = decoder {", "} else {") {
                        writer.write(
                            "let output: \$L = try responseDecoder.decode(responseBody: $data)",
                            symbol
                        )
                        writer.write("self.\$L = output", memberName)
                    }
                    writer.indent()
                    writer.write("self.\$L = nil", memberName).closeBlock("}")
                }
                else -> throw CodegenException("member shape ${binding.member} serializer not implemented yet")
            }
        }
        writer.indent()
        writer.write("self.\$L = nil", memberName).closeBlock("}")
    }
}
