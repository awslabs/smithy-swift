// Code generated by smithy-swift-codegen. DO NOT EDIT!

import class SmithyHTTPAPI.HttpResponse
import struct Foundation.Data

extension InvokeOutput {

    static func httpOutput(from httpResponse: SmithyHTTPAPI.HttpResponse) async throws -> InvokeOutput {
        var value = InvokeOutput()
        switch httpResponse.body {
        case .data(let data):
            value.payload = data
        case .stream(let stream):
            value.payload = try stream.readToEnd()
        case .noStream:
            value.payload = nil
        }
        return value
    }
}
