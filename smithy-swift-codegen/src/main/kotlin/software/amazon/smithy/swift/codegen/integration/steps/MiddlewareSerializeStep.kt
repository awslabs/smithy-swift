package software.amazon.smithy.swift.codegen.integration.steps

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.MiddlewareStep
import software.amazon.smithy.swift.codegen.SwiftDependency

class MiddlewareSerializeStep(
    inputType: Symbol,
    outputType: Symbol,
    outputErrorType: Symbol
) : MiddlewareStep(outputType, outputErrorType) {
    override val inputType: Symbol = Symbol
        .builder()
        .name("SerializeStepInput<$inputType>")
        .dependencies(SwiftDependency.CLIENT_RUNTIME).build()
}
