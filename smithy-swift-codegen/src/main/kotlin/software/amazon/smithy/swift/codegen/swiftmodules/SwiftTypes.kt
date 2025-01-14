/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.swiftmodules

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.SwiftDeclaration
import software.amazon.smithy.swift.codegen.SwiftDependency

object SwiftTypes {
    val Void = builtInSymbol("Void", SwiftDeclaration.STRUCT)
    val String = builtInSymbol("String", SwiftDeclaration.STRUCT)
    val Int = builtInSymbol("Int", SwiftDeclaration.STRUCT)
    val Int8 = builtInSymbol("Int8", SwiftDeclaration.STRUCT)
    val Int16 = builtInSymbol("Int16", SwiftDeclaration.STRUCT)
    val Float = builtInSymbol("Float", SwiftDeclaration.STRUCT)
    val Double = builtInSymbol("Double", SwiftDeclaration.STRUCT)
    val Bool = builtInSymbol("Bool", SwiftDeclaration.STRUCT)

    val StringArray = builtInSymbol("Array<Swift.String>")

    val List = builtInSymbol("List", SwiftDeclaration.STRUCT)
    val Set = builtInSymbol("Set", SwiftDeclaration.STRUCT)
    val Map = builtInSymbol("Map", SwiftDeclaration.STRUCT)
    val Error = builtInSymbol("Error", SwiftDeclaration.PROTOCOL)

    object Protocols {
        val Equatable = builtInSymbol("Equatable", SwiftDeclaration.PROTOCOL)
        val Hashable = builtInSymbol("Hashable", SwiftDeclaration.PROTOCOL)
        val RawRepresentable = builtInSymbol("RawRepresentable", SwiftDeclaration.PROTOCOL)
        val CaseIterable = builtInSymbol("CaseIterable", SwiftDeclaration.PROTOCOL)
        val CustomDebugStringConvertible = builtInSymbol("CustomDebugStringConvertible", SwiftDeclaration.PROTOCOL)
        val Sendable = builtInSymbol("Sendable", SwiftDeclaration.PROTOCOL)
    }
}

private fun builtInSymbol(name: String, declaration: SwiftDeclaration? = null): Symbol = SwiftSymbol.make(
    name,
    declaration,
    SwiftDependency.SWIFT,
    emptyList(),
    emptyList(),
)
