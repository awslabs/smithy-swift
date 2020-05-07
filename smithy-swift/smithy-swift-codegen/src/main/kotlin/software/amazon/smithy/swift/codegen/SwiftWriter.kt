package software.amazon.smithy.swift.codegen

import java.util.function.BiFunction
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolDependencyContainer
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.utils.CodeWriter

class SwiftWriter(private val fullPackageName: String) : CodeWriter() {
    init {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
        putFormatter('T', SwiftSymbolFormatter())
    }

    internal val imports: ImportDeclarations = ImportDeclarations()
    internal val dependencies: MutableList<SymbolDependency> = mutableListOf()

    companion object {
        val staticHeader: String = "// Code generated by smithy-swift-codegen. DO NOT EDIT!\n\n"
    }

    fun addImport(symbol: Symbol, packageName: String) {
        // always add dependencies
        dependencies.addAll(symbol.dependencies)
    }

    fun addImportReferences(symbol: Symbol, vararg options: SymbolReference.ContextOption) {
        symbol.references.forEach { reference ->
            for (option in options) {
                if (reference.hasOption(option)) {
                    addImport(reference.symbol, reference.alias)
                    break
                }
            }
        }
    }

    /**
     * Adds one or more dependencies to the generated code.
     *
     *
     * The dependencies of all writers created by the [SwiftDelegator]
     * are merged together to eventually generate a podspec file.
     *
     * @param dependencies Swift dependency to add.
     * @return Returns the writer.
     */
    fun addDependency(dependencies: SymbolDependencyContainer): SwiftWriter? {
        this.dependencies.addAll(dependencies.dependencies)
        return this
    }

    override fun toString(): String {
        val contents = super.toString()
        return staticHeader + contents
    }

    /**
     * Implements Swift symbol formatting for the `$T` formatter
     */
    private class SwiftSymbolFormatter : BiFunction<Any, String, String> {
        override fun apply(type: Any, indent: String): String {
            when (type) {
                is Symbol -> {
                    var formatted = type.name
                    if (type.isBoxed()) {
                        formatted += "?"
                    }

                    val defaultValue = type.defaultValue()
                    if (defaultValue != null) {
                        formatted += " = $defaultValue"
                    }
                    return formatted
                }
//            is SymbolReference -> println("symbol ref")
                else -> throw CodegenException("Invalid type provided for \$T. Expected a Symbol, but found `$type`")
            }
        }
    }
}
