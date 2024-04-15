// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime
import SmithyTestUtil

enum OnlyHttpApiKeyAuthOutputError: ClientRuntime.HttpResponseErrorBinding {
    static func makeError(httpResponse: ClientRuntime.HttpResponse, decoder: ClientRuntime.ResponseDecoder? = nil) async throws -> Swift.Error {
        let defaultError = try await SmithyTestUtil.JSONError(httpResponse: httpResponse)
        switch defaultError.errorType {
            default: return try await ClientRuntime.UnknownHTTPServiceError.makeError(httpResponse: httpResponse, message: defaultError.errorMessage, typeName: defaultError.errorType)
        }
    }
}
