/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

class IdempotencyTokenMiddlewareGenerator(
    private val writer: SwiftWriter,
    private val idempotentMemberName: String,
    private val operationMiddlewareStackName: String,
    private val outputShapeName: String,
    private val outputErrorShapeName: String
) {
    /**
     * If given the following smithy in the input of an operation:
     * ```
     * structure IdempotentTokenStruct {
     * @idempotencyToken
     * token: String
     * }
     *
     * ```
     * The operation would generate the following inside its implementation to provide a default token from the given generator
     * before the request is made
     * ```
     * operationStack.initializeStep.intercept(position: .before, id: "IdempotencyTokenMiddleware") { (context, input, next) -> Result<OperationOutput<IdempotencyTokenOutput>, SdkError<IdempotencyTokenError>> in
     *    let idempotencyTokenGenerator = context.getIdempotencyTokenGenerator()
     *    var copiedInput = input
     *    if input.token == nil {
     *        copiedInput.token = idempotencyTokenGenerator.generateToken()
     *    }
     *    return next.handle(context: context, input: copiedInput)
     * }
     * ```
     * */
    fun renderIdempotencyMiddleware() {
        writer.openBlock("$operationMiddlewareStackName.initializeStep.intercept(position: .before, id: \"IdempotencyTokenMiddleware\") { (context, input, next) -> \$N<\$N<$outputShapeName>, \$N<$outputErrorShapeName>> in", "}", SwiftTypes.Result, ClientRuntimeTypes.Middleware.OperationOutput, ClientRuntimeTypes.Core.SdkError) {
            writer.write("let idempotencyTokenGenerator = context.getIdempotencyTokenGenerator()")
            writer.write("var copiedInput = input")
            writer.openBlock("if input.$idempotentMemberName == nil {", "}") {
                writer.write("copiedInput.$idempotentMemberName = idempotencyTokenGenerator.generateToken()")
            }
            writer.write("return next.handle(context: context, input: copiedInput)")
        }
    }
}
