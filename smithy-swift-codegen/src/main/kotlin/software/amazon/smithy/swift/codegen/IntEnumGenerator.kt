package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.traits.EnumValueTrait
import software.amazon.smithy.swift.codegen.customtraits.NestedTrait
import software.amazon.smithy.swift.codegen.model.expectShape
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.nestedNamespaceType
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes

class IntEnumGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: SwiftWriter,
    private val shape: IntEnumShape,
    private val settings: SwiftSettings
) {
    private var allCasesBuilder: MutableList<String> = mutableListOf()
    private var rawValuesBuilder: MutableList<String> = mutableListOf()

    fun render() {
        val symbol = symbolProvider.toSymbol(shape)
        writer.putContext("enum.name", symbol.name)
        val isNestedType = shape.hasTrait<NestedTrait>()
        if (isNestedType) {
            val service = model.expectShape<ServiceShape>(settings.service)
            writer.openBlock("extension ${service.nestedNamespaceType(symbolProvider)} {", "}") {
                writer.write("")
                renderEnum()
            }
        } else {
            renderEnum()
        }
        writer.removeContext("enum.name")
    }

    private fun renderEnum() {
        writer.writeShapeDocs(shape)
        writer.writeAvailableAttribute(null, shape)
        val conformances = writer.format(
            "\$N, \$N, \$N, \$N, \$N",
            SwiftTypes.Protocols.Sendable,
            SwiftTypes.Protocols.Equatable,
            SwiftTypes.Protocols.RawRepresentable,
            SwiftTypes.Protocols.CaseIterable,
            SwiftTypes.Protocols.Hashable,
        )
        writer.openBlock(
            "public enum \$enum.name:L: \$L {",
            "}",
            conformances,
        ) {
            createEnumWriterContexts()
            // add the sdkUnknown case which will always be last
            writer.write("case sdkUnknown(\$N)", SwiftTypes.Int)
            writer.write("")

            // Generate allCases static array
            generateAllCasesBlock()
            writer.write("")

            // Generate initializer from rawValue
            generateInitFromRawValueBlock()
            writer.write("")

            // Generate rawValue internal enum
            generateRawValueEnumBlock()
        }
    }

    fun addEnumCaseToEnum(caseShape: MemberShape) {
        writer.writeMemberDocs(model, caseShape)
        writer.write("case ${caseShape.swiftEnumCaseName()}")
    }

    fun addEnumCaseToAllCases(caseShape: MemberShape) {
        allCasesBuilder.add(".${caseShape.swiftEnumCaseName(false)}")
    }

    fun addEnumCaseToRawValuesEnum(caseShape: MemberShape) {
        rawValuesBuilder.add("case .${caseShape.swiftEnumCaseName(false)}: return ${caseShape.swiftEnumCaseValue()}")
    }

    fun createEnumWriterContexts() {
        shape
            .getCaseMembers()
            .sortedBy { it.memberName }
            .forEach {
                // Add all given enum cases to generated enum definition
                addEnumCaseToEnum(it)
                addEnumCaseToAllCases(it)
                addEnumCaseToRawValuesEnum(it)
            }
    }

    fun generateAllCasesBlock() {
        writer.openBlock("public static var allCases: [\$enum.name:L] {", "}") {
            writer.openBlock("return [", "]") {
                writer.write(allCasesBuilder.joinToString(",\n"))
            }
        }
    }

    fun generateInitFromRawValueBlock() {
        writer.openBlock("public init(rawValue: \$N) {", "}", SwiftTypes.Int) {
            writer.write("let value = Self.allCases.first(where: { \$\$0.rawValue == rawValue })")
            writer.write("self = value ?? Self.sdkUnknown(rawValue)")
        }
    }

    fun generateRawValueEnumBlock() {
        rawValuesBuilder.add("case let .sdkUnknown(s): return s")
        writer.openBlock("public var rawValue: \$N {", "}", SwiftTypes.Int) {
            writer.write("switch self {")
            writer.write(rawValuesBuilder.joinToString("\n"))
            writer.write("}")
        }
    }

    fun IntEnumShape.getCaseMembers(): List<MemberShape> {
        return members().filter {
            it.hasTrait<EnumValueTrait>()
        }
    }

    fun MemberShape.swiftEnumCaseName(shouldBeEscaped: Boolean = true): String {
        return swiftEnumCaseName(
            memberName,
            "${swiftEnumCaseValue()}",
            shouldBeEscaped
        )
    }

    fun MemberShape.swiftEnumCaseValue(): Int {
        return expectTrait<EnumValueTrait>(EnumValueTrait::class.java).expectIntValue()
    }
}
