/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.swift.codegen.customtraits.NestedTrait
import software.amazon.smithy.swift.codegen.model.expectShape
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.nestedNamespaceType
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes

/**
 * Generates an appropriate Swift type for a Smithy enum string.
 *
 * <p>For example, given the following Smithy model:
 *
 * ```
 * @enum([{ value: "YES", name: "YEP"},
 *        { value: "NO", name: "NOPE"}])
 * string TypedYesNo
 * ```
 * We will generate the following:
 * ```
 * enum TypedYesNo {
 *     case yep
 *     case nope
 *     case sdkUnknown(String)
 * }
 *
 *
 * extension TypedYesNo : Equatable, RawRepresentable, Codable, CaseIterable, Hashable {
 *     static var allCases: [TypedYesNo] {
 *         return [.yep, .nope, .sdkUnknown("")]
 *     }
 *
 *     init?(rawValue: String) {
 *         let value = Self.allCases.first(where: { $0.rawValue == rawValue })
 *         self = value ?? Self.sdkUnknown(rawValue)
 *     }
 *
 *     var rawValue: String {
 *         switch self {
 *         case .yep: return "YES"
 *         case .no: return "NO"
 *         case let .sdkUnknown(s): return s
 *         }
 *     }
 *
 *     init(from decoder: Decoder) throws {
 *         let container = try decoder.singleValueContainer()
 *         let rawValue = try container.decode(RawValue.self)
 *         self = TypedYesNo(rawValue: rawValue) ?? TypedYesNo.sdkUnknown(rawValue)
 *     }
 * }
 * ```
 *
 * Another Example, given the following Smithy model:
 *
 * ```
 * @enum([{ value: "YES" },
 *        { value: "NO" }])
 * string SimpleYesNo
 * ```
 * We will generate the following:
 * ```
 * enum SimpleYesNo {
 *     case yes
 *     case no
 *     case sdkUnknown(String)
 * }
 *
 *
 * extension SimpleYesNo : Equatable, RawRepresentable, Codable, CaseIterable, Hashable {
 *     static var allCases: [SimpleYesNo] {
 *         return [.yes, .no, .sdkUnknown("")]
 *     }
 *
 *     init?(rawValue: String) {
 *         let value = Self.allCases.first(where: { $0.rawValue == rawValue })
 *         self = value ?? Self.sdkUnknown(rawValue)
 *     }
 *
 *     var rawValue: String {
 *         switch self {
 *         case .yes: return "YES"
 *         case .no: return "NO"
 *         case let .sdkUnknown(s): return s
 *         }
 *     }
 *
 *     init(from decoder: Decoder) throws {
 *         let container = try decoder.singleValueContainer()
 *         let rawValue = try container.decode(RawValue.self)
 *         self = SimpleYesNo(rawValue: rawValue) ?? SimpleYesNo.sdkUnknown(rawValue)
 *     }
 * }
 * ```
 */
class EnumGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: SwiftWriter,
    private val shape: Shape,
    private val settings: SwiftSettings
) {

    init {
        assert(shape.getTrait(EnumTrait::class.java).isPresent)
    }

    val enumTrait: EnumTrait by lazy {
        shape.getTrait(EnumTrait::class.java).get()
    }

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
            writer.write("case sdkUnknown(\$N)", SwiftTypes.String)
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

    fun addEnumCaseToEnum(definition: EnumDefinition) {
        writer.writeEnumDefinitionDocs(definition)
        writer.write("case ${definition.swiftEnumCaseName()}")
    }

    fun addEnumCaseToAllCases(definition: EnumDefinition) {
        allCasesBuilder.add(".${definition.swiftEnumCaseName(false)}")
    }

    fun addEnumCaseToRawValuesEnum(definition: EnumDefinition) {
        rawValuesBuilder.add("case .${definition.swiftEnumCaseName(false)}: return \"${definition.value}\"")
    }

    fun createEnumWriterContexts() {
        enumTrait
            .values
            .sortedBy { it.name.orElse(it.value) }
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
        writer.openBlock("public init?(rawValue: \$N) {", "}", SwiftTypes.String) {
            writer.write("let value = Self.allCases.first(where: { \$\$0.rawValue == rawValue })")
            writer.write("self = value ?? Self.sdkUnknown(rawValue)")
        }
    }

    fun generateRawValueEnumBlock() {
        rawValuesBuilder.add("case let .sdkUnknown(s): return s")
        writer.openBlock("public var rawValue: \$N {", "}", SwiftTypes.String) {
            writer.write("switch self {")
            writer.write(rawValuesBuilder.joinToString("\n"))
            writer.write("}")
        }
    }

    /**
     * Creates an idiomatic name for swift enum cases from Smithy EnumDefinition.
     * Uses either name or value attributes of EnumDefinition in that order and formats
     * them to camelCase after removing chars except alphanumeric, space and underscore.
     */
    fun EnumDefinition.swiftEnumCaseName(shouldBeEscaped: Boolean = true): String {
        return swiftEnumCaseName(name.orElse(null), value, shouldBeEscaped)
    }
}
