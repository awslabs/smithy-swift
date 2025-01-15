/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.model.getTrait
import software.amazon.smithy.swift.codegen.swiftmodules.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.swiftmodules.SmithyHTTPAPITypes

/**
 * Generates a per/service endpoint resolver (internal to the generated SDK)
 */
class EndpointResolverGenerator(
    private val partitionDefinition: Symbol,
) {
    fun render(ctx: ProtocolGenerator.GenerationContext) {
        val ruleSetNode = ctx.service.getTrait<EndpointRuleSetTrait>()?.ruleSet
        val ruleSet = if (ruleSetNode != null) EndpointRuleSet.fromNode(ruleSetNode) else null

        ctx.delegator.useFileWriter("Sources/${ctx.settings.moduleName}/Endpoints.swift") {
            renderResolverProtocol(it)
            it.write("")
            renderResolver(it, ruleSet)
            renderStaticResolver(it)
            it.write("")
        }
    }

    private fun renderResolverProtocol(writer: SwiftWriter) {
        writer.openBlock("public protocol \$N {", "}", EndpointTypes.EndpointResolver) {
            writer.write("func resolve(params: EndpointParams) throws -> \$N", SmithyHTTPAPITypes.Endpoint)
        }
    }

    private fun renderResolver(writer: SwiftWriter, endpointRuleSet: EndpointRuleSet?) {
        writer.write(
            "typealias DefaultEndpointResolver = \$N<EndpointParams>",
            ClientRuntimeTypes.Core.DefaultEndpointResolver,
        )
        writer.write("")
        writer.openBlock("extension DefaultEndpointResolver {", "}") {
            endpointRuleSet?.let {
                writer.write("private static let ruleSet = \$S", Node.printJson(it.toNode()))
            } ?: run {
                writer.write("private static let ruleSet = \"{}\"")
            }
            writer.write("")
            writer.openBlock("init() throws {", "}") {
                writer.write("try self.init(partitions: \$N, ruleSet: Self.ruleSet)", partitionDefinition)
            }
        }
        writer.write("")
        writer.write("extension DefaultEndpointResolver: EndpointResolver {}")
    }

    private fun renderStaticResolver(writer: SwiftWriter) {
        writer.write("")
        writer.write(
            "typealias StaticEndpointResolver = \$N<EndpointParams>",
            ClientRuntimeTypes.Core.StaticEndpointResolver,
        )
        writer.write("")
        writer.write("extension StaticEndpointResolver: EndpointResolver {}")
    }
}
