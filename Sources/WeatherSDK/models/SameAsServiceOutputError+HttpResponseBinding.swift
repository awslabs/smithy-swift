// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime

enum SameAsServiceOutputError: ClientRuntime.HttpResponseErrorBinding {
    static func makeError(httpResponse: ClientRuntime.HttpResponse, decoder: ClientRuntime.ResponseDecoder? = nil) async throws -> Swift.Error {
        let defaultError = try await ClientRuntime.DefaultError(httpResponse: httpResponse)
        switch defaultError.errorType {
            default: return try await ClientRuntime.UnknownHTTPServiceError.makeError(httpResponse: httpResponse, message: defaultError.errorMessage, typeName: defaultError.errorType)
        }
    }
}
