/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.swift.codegen.customtraits.EquatableConformanceTrait
import software.amazon.smithy.swift.codegen.customtraits.NestedTrait
import software.amazon.smithy.swift.codegen.customtraits.RecursiveUnionTrait
import software.amazon.smithy.swift.codegen.model.eventStreamEvents
import software.amazon.smithy.swift.codegen.model.expectShape
import software.amazon.smithy.swift.codegen.model.hasTrait
import software.amazon.smithy.swift.codegen.model.nestedNamespaceType
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes

/**
 * Generates an appropriate Swift type for a Smithy union shape
 *
 * Smithy unions are rendered as an Enum in Swift. Only a single member
 * can be set at any given time.
 *
 * For example, given the following Smithy model:
 *
 * ```
 * union Attacker {
 *     lion: Lion,
 *     bear: Bear
 * }
 * ```
 *
 * The following code is generated:
 *
 * ```
 * enum Attacker {
 *     case lion(Lion?)
 *     case bear(Bear?)
 *     case sdkUnknown(String?)
 * }
 *
 */

class UnionGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: SwiftWriter,
    private val shape: UnionShape,
    private val settings: SwiftSettings
) {

    val unionSymbol: Symbol by lazy {
        symbolProvider.toSymbol(shape)
    }

    fun render() {
        writer.putContext("union.name", unionSymbol.name)
        val isNestedType = shape.hasTrait<NestedTrait>()
        if (isNestedType) {
            val service = model.expectShape<ServiceShape>(settings.service)
            writer.openBlock("extension ${service.nestedNamespaceType(symbolProvider)} {", "}") {
                writer.write("")
                renderUnion()
            }
        } else {
            renderUnion()
        }
        writer.removeContext("union.name")
    }

    fun renderUnion() {
        writer.writeShapeDocs(shape)
        writer.writeAvailableAttribute(model, shape)
        val indirectOrNot = "indirect ".takeIf { shape.hasTrait<RecursiveUnionTrait>() } ?: ""
        val equatableConformance = writer.format(", \$N", SwiftTypes.Protocols.Equatable).takeIf { shape.hasTrait<EquatableConformanceTrait>() } ?: ""
        writer.openBlock("public ${indirectOrNot}enum \$union.name:L: \$N$equatableConformance {", "}", SwiftTypes.Protocols.Sendable) {
            // event streams (@streaming union) MAY have variants that target errors.
            // These errors if encountered on the stream will be thrown as an exception rather
            // than showing up as one of the possible events the consumer will see on the stream (AsyncThrowingStream<T>).
            val members = shape.eventStreamEvents(model)

            members.forEach { member: MemberShape ->
                writer.writeMemberDocs(model, member)
                val enumCaseName = symbolProvider.toMemberName(member)
                val enumCaseAssociatedType = symbolProvider.toSymbol(member)
                if (member.target.toString() != "smithy.api#Unit") {
                    writer.write("case \$L(\$N)", enumCaseName, enumCaseAssociatedType)
                } else {
                    writer.write("case \$L", enumCaseName)
                }
            }
            // add the sdkUnknown case which will always be last
            writer.write("case sdkUnknown(\$N)", SwiftTypes.String)
        }
    }
}
