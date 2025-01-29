package software.amazon.smithy.swift.codegen.swiftintegrations

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.DoubleShape
import software.amazon.smithy.model.shapes.FloatShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.swift.codegen.SwiftDelegator
import software.amazon.smithy.swift.codegen.SwiftDependency
import software.amazon.smithy.swift.codegen.core.SwiftCodegenContext
import software.amazon.smithy.swift.codegen.customtraits.EquatableConformanceTrait
import software.amazon.smithy.swift.codegen.customtraits.TestEquatableConformanceTrait
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.SwiftIntegration
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes

class TestEquatableConformanceIntegration : SwiftIntegration {

    override fun writeAdditionalFiles(
        ctx: SwiftCodegenContext,
        protocolGenerationContext: ProtocolGenerator.GenerationContext,
        delegator: SwiftDelegator,
    ) {
        // For shapes that need Equatable conformance for testing and didn't already get it in the
        // SDK, generate Equatable conformance & place it in the protocol test target.
        ctx.model.shapes()
            .filter { it.hasTrait<TestEquatableConformanceTrait>() }
            .filter { !it.hasTrait<EquatableConformanceTrait>() }
            .forEach { writeEquatableFor(it, ctx, delegator) }
    }

    private fun writeEquatableFor(shape: Shape, ctx: SwiftCodegenContext, delegator: SwiftDelegator) {
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val httpBindingSymbol = Symbol.builder()
            .definitionFile("Tests/${ctx.settings.moduleName}Tests/models/${symbol.name}+Equatable.swift")
            .name(symbol.name)
            .build()
        delegator.useShapeWriter(httpBindingSymbol) { writer ->
            writer.addImport(ctx.settings.moduleName)
            writer.addImport(SwiftDependency.SMITHY_TEST_UTIL.target)
            writer.openBlock("extension \$L: \$N {", "}", symbol.fullName, SwiftTypes.Protocols.Equatable) {
                writer.write("")
                writer.openBlock("public static func ==(lhs: \$L, rhs: \$L) -> Bool {", "}", symbol.fullName, symbol.fullName) {
                    when (shape) {
                        is StructureShape -> {
                            shape.members().forEach { member ->
                                val propertyName = ctx.symbolProvider.toMemberName(member)
                                val path = "properties.".takeIf { shape.hasTrait<ErrorTrait>() } ?: ""
                                val propertyAccessor = "$path$propertyName"
                                val target = ctx.model.expectShape(member.target)
                                when (target) {
                                    is FloatShape, is DoubleShape -> {
                                        writer.write(
                                            "if (!floatingPointValuesMatch(lhs: lhs.\$L, rhs: rhs.\$L)) { return false }",
                                            propertyAccessor,
                                            propertyAccessor
                                        )
                                    }
                                    else -> {
                                        writer.write("if lhs.\$L != rhs.\$L { return false }", propertyAccessor, propertyAccessor)
                                    }
                                }
                            }
                            writer.write("return true")
                        }
                        is UnionShape -> {
                            writer.openBlock("switch (lhs, rhs) {", "}") {
                                shape.members().forEach { member ->
                                    val enumCaseName = ctx.symbolProvider.toMemberName(member)
                                    if (member.target.toString() != "smithy.api#Unit") {
                                        writer.write("case (.\$L(let lhs), .\$L(let rhs)):", enumCaseName, enumCaseName)
                                        writer.indent {
                                            writer.write("return lhs == rhs")
                                        }
                                    } else {
                                        writer.write("case (.\$L, .\$L):", enumCaseName, enumCaseName)
                                        writer.indent {
                                            writer.write("return true")
                                        }
                                    }
                                }
                                writer.write("default: return false")
                            }
                        }
                    }
                }
            }
        }
    }
}
