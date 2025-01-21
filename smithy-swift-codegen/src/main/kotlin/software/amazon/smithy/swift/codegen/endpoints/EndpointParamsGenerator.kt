/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait
import software.amazon.smithy.swift.codegen.AuthSchemeResolverGenerator
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.model.boxed
import software.amazon.smithy.swift.codegen.model.defaultValue
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SwiftTypes
import software.amazon.smithy.swift.codegen.utils.clientName
import software.amazon.smithy.swift.codegen.utils.toLowerCamelCase

/**
 * Generates EndpointParams struct for the service
 */
class EndpointParamsGenerator(
    val ctx: ProtocolGenerator.GenerationContext,
) {
    fun render() {
        val ruleSetNode = ctx.service.getTrait<EndpointRuleSetTrait>()?.ruleSet
        val endpointRuleSet = ruleSetNode?.let { EndpointRuleSet.fromNode(it) }
        ctx.delegator.useFileWriter("Sources/${ctx.settings.moduleName}/Endpoints.swift") { writer ->
            renderParams(writer, endpointRuleSet)
            writer.write("")
            renderContextExtension(writer, endpointRuleSet)
        }
    }

    private fun renderParams(writer: SwiftWriter, endpointRuleSet: EndpointRuleSet?) {
        writer.openBlock("public struct EndpointParams {", "}") {
            endpointRuleSet?.parameters?.toList()?.sortedBy { it.name.toString() }?.let { sortedParameters ->
                renderMembers(writer, sortedParameters)
                writer.write("")
                renderInit(writer, sortedParameters)
                // Convert auth scheme params to endpoint params for rules-based auth scheme resolvers
                if (AuthSchemeResolverGenerator.usesRulesBasedAuthResolver(ctx)) {
                    renderConversionInit(writer, sortedParameters)
                }
            }
        }
    }

    private fun renderInit(writer: SwiftWriter, parameters: List<Parameter>) {
        writer.openBlock("public init(", ")") {
            for ((index, param) in parameters.withIndex()) {
                val memberName = param.name.toString().toLowerCamelCase()
                val memberSymbol = param.toSymbol()
                val terminator = if (index != parameters.lastIndex) "," else ""
                writer.write("\$L: \$D\$L", memberName, memberSymbol, terminator)
            }
        }

        writer.openBlock("{", "}") {
            parameters.forEach {
                val memberName = it.name.toString().toLowerCamelCase()
                writer.write("self.\$1L = \$1L", memberName)
            }
        }
    }

    private fun renderMembers(writer: SwiftWriter, parameters: List<Parameter>) {
        parameters.forEach { param ->
            val memberName = param.name.toString().toLowerCamelCase()
            val memberSymbol = param.toSymbol()
            val optional = if (param.isRequired) "" else "?"
            param.documentation.orElse(null)?.let { writer.write("/// $it") }
            writer.write("public let \$L: \$L$optional", memberName, memberSymbol)
        }
    }

    private fun renderConversionInit(
        writer: SwiftWriter,
        parameters: List<Parameter>,
    ) {
        writer.apply {
            val paramsType = ctx.settings.sdkId.clientName() + "AuthSchemeResolverParameters"
            openBlock("public init (authSchemeParams: \$L) {", "}", paramsType) {
                parameters.forEach {
                    val memberName = it.name.toString().toLowerCamelCase()
                    writer.write("self.\$1L = authSchemeParams.\$1L", memberName)
                }
            }
        }
    }

    private fun renderContextExtension(writer: SwiftWriter, endpointRuleSet: EndpointRuleSet?) {
        writer.openBlock(
            "extension EndpointParams: \$N {",
            "}",
            ClientRuntimeTypes.Core.EndpointsRequestContextProviding,
        ) {
            writer.write("")
            writer.openBlock(
                "public var context: \$N {",
                "}",
                ClientRuntimeTypes.Core.EndpointsRequestContext,
            ) {
                writer.openBlock("get throws {", "}") {
                    writer.write("let context = try \$N()", ClientRuntimeTypes.Core.EndpointsRequestContext)
                    endpointRuleSet?.parameters?.toList()?.sortedBy { it.name.toString() }?.let { sortedParameters ->
                        sortedParameters.forEach { param ->
                            val memberName = param.name.toString().toLowerCamelCase()
                            val paramName = param.name.toString()
                            writer.write("try context.add(name: \$S, value: self.\$L)", paramName, memberName)
                        }
                    }
                    writer.write("return context")
                }
            }
        }
    }
}

fun Parameter.toSymbol(): Symbol {
    val swiftType = when (type) {
        ParameterType.STRING -> SwiftTypes.String
        ParameterType.BOOLEAN -> SwiftTypes.Bool
        ParameterType.STRING_ARRAY -> SwiftTypes.StringArray
    }
    var builder = Symbol.builder().name(swiftType.fullName)
    if (!isRequired) {
        builder = builder.boxed()
    }

    default.ifPresent { defaultValue ->
        if (type.equals(ParameterType.STRING)) {
            builder.defaultValue("\"$defaultValue\"")
        } else if (type.equals(ParameterType.STRING_ARRAY)) {
            val elementsWrappedWithEscapedQuotes = defaultValue.toString()
                .removeSurrounding("[", "]")
                .split(", ")
                .joinToString(", ", "[", "]") { "\"$it\"" }
            builder.defaultValue(elementsWrappedWithEscapedQuotes)
        } else {
            builder.defaultValue(defaultValue.toString())
        }
    }

    return builder.build()
}
