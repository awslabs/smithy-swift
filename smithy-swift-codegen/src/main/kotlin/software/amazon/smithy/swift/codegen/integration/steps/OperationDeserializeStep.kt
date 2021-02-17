package software.amazon.smithy.swift.codegen.integration.steps

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.swift.codegen.OperationStep
import software.amazon.smithy.swift.codegen.SwiftDependency

class OperationDeserializeStep(
    outputType: Symbol,
    outputErrorType: Symbol
) : OperationStep(outputType, outputErrorType) {
    override val inputType: Symbol = Symbol
        .builder()
        .name("SdkHttpRequest")
        .dependencies(SwiftDependency.CLIENT_RUNTIME)
        .build()
}
