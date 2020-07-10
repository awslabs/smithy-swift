/*
 *
 *  * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License").
 *  * You may not use this file except in compliance with the License.
 *  * A copy of the License is located at
 *  *
 *  *  http://aws.amazon.com/apache2.0
 *  *
 *  * or in the "license" file accompanying this file. This file is distributed
 *  * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  * express or implied. See the License for the specific language governing
 *  * permissions and limitations under the License.
 *
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.swift.codegen.integration.SwiftIntegration
/*
* Generates a Swift protocol for the service
 */
class ServiceGenerator(
    settings: SwiftSettings,
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: SwiftWriter,
    private val integrations: List<SwiftIntegration>
) {
    private var service = settings.getService(model)
    private val serviceSymbol: Symbol by lazy {
        symbolProvider.toSymbol(service)
    }

    fun render() {
        // add imports
        writer.write("import ClientRuntime")
        writer.addImport(serviceSymbol)
        // generate protocol
        renderSwiftProtocol()
    }
    /**
     * Generates an appropriate Swift Protocol for a Smithy Service shape.
     *
     * For example, given the following Smithy model:
     *
     * ```
     * namespace smithy.example
     *
     * use aws.protocols#awsJson1_1
     *
     *   @awsJson1_1
     *  service Example {
     *   version: "1.0.0",
     *   operations: [GetFoo]
     *   }
     *
     *  operation GetFoo {
     *   input: GetFooInput,
     *   output: GetFooOutput,
     *   errors: [GetFooError]
     *   }
     *
     * ```
     * We will generate the following:
     * ```
     * public protocol ExampleServiceProtocol {
     *      func getFoo(input: GetFooInput, completion: @escaping (SdkResult<GetFooOutput, GetFooError>) -> Void)
     * }
     * ```
     */
    private fun renderSwiftProtocol() {
        val topDownIndex = model.getKnowledge(TopDownIndex::class.java)
        val operations = topDownIndex.getContainedOperations(service)
        val operationsIndex = model.getKnowledge(OperationIndex::class.java)

        writer.writeShapeDocs(service)
        writer.openBlock("public protocol ${serviceSymbol.name}Protocol {")
            .call {
                    operations.forEach { op ->
                        renderOperation(operationsIndex, op)
                    }
                }
            .closeBlock("}")
            .write("")
    }

    private fun renderOperation(opIndex: OperationIndex, op: OperationShape) {
        val inputShape = opIndex.getInput(op)
        val outputShape = opIndex.getOutput(op)
        val input = inputShape.map { symbolProvider.toSymbol(it).name }
        val output = outputShape.map { symbolProvider.toSymbol(it).name }
        val operationName = op.camelCaseName()
        val errorTypeName = "${op.defaultName()}OperationError"
        val inputParam = input.map { "input: $it" }.orElse("")
        // get error object here
        val outputParam = output.map { "completion: (SdkResult<$it, $errorTypeName>) -> Void" }.orElse("")
        var paramTerminator = ""
        if (inputParam != "" && outputParam != "") {
            paramTerminator = ", "
        }
        writer.writeShapeDocs(op)

        val hasOutputStream = outputShape.map { it.hasStreamingMember(model) }.orElse(false)
        if (!hasOutputStream) {
            writer.write("func \$L(\$L${paramTerminator}\$L)", operationName, inputParam, outputParam)
        } else {
            writer.write("func \$L(\$L${paramTerminator}streamingHandler: StreamingProvider, \$L)", operationName, inputParam, outputParam)
        }
    }
}
/**
 * An extension to see if a structure shape has a the streaming trait*.
 *
 * @model model is the smithy model.
 * @return true if has the streaming trait on itself or its target.
 */
fun StructureShape.hasStreamingMember(model: Model): Boolean {
    return this.allMembers.values.any {
        val streamingTrait = StreamingTrait::class.java
        it.hasTrait(streamingTrait) || model.getShape(it.target).get().hasTrait(streamingTrait)
    }
}
