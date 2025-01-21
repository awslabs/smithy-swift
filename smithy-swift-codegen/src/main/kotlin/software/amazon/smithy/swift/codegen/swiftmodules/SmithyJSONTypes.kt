/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen.swiftmodules

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.SwiftDeclaration
import software.amazon.smithy.swift.codegen.SwiftDependency

object SmithyJSONTypes {
    val Writer = runtimeSymbol("Writer", SwiftDeclaration.CLASS, listOf(SmithyReadWriteTypes.SmithyWriter))
    val Reader = runtimeSymbol("Reader", SwiftDeclaration.CLASS, listOf(SmithyReadWriteTypes.SmithyReader, SmithyReadWriteTypes.ShapeDeserializer))
}

private fun runtimeSymbol(
    name: String,
    declaration: SwiftDeclaration,
    additionalImports: List<Symbol> = emptyList(),
): Symbol = SwiftSymbol.make(
    name,
    declaration,
    SwiftDependency.SMITHY_JSON,
    additionalImports,
    listOf("SmithyReadWrite"),
)
