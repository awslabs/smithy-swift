// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime

extension CreateCityOutput: ClientRuntime.HttpResponseBinding {
    public init(httpResponse: ClientRuntime.HttpResponse, decoder: ClientRuntime.ResponseDecoder? = nil) async throws {
        if let data = try await httpResponse.body.readData(),
            let responseDecoder = decoder {
            let output: CreateCityOutputBody = try responseDecoder.decode(responseBody: data)
            self.cityId = output.cityId
        } else {
            self.cityId = nil
        }
    }
}
