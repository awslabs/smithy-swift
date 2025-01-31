//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import class SmithyHTTPAPI.HTTPResponse
@_spi(SmithyReadWrite) import class SmithyCBOR.Reader

public struct RpcV2CborError: BaseError {
    public let code: String
    public let message: String?
    public let requestID: String?
    @_spi(SmithyReadWrite) public var errorBodyReader: Reader { responseReader }

    public let httpResponse: HTTPResponse
    private let responseReader: Reader

    @_spi(SmithyReadWrite)
    public init(httpResponse: HTTPResponse, responseReader: Reader, noErrorWrapping: Bool, code: String? = nil) throws {
        switch responseReader.cborValue {
        case .map(let errorDetails):
            if case let .text(errorCode) = errorDetails["__type"] {
                self.code = sanitizeErrorType(errorCode)
            } else {
                self.code = "UnknownError"
            }

            if case let .text(errorMessage) = errorDetails["Message"] {
                self.message  = errorMessage
            } else {
                self.message = nil
            }
        default:
            self.code = "UnknownError"
            self.message = nil
        }

        self.httpResponse = httpResponse
        self.responseReader = responseReader
        self.requestID = nil
    }
}

/// Filter additional information from error name and sanitize it
/// Reference: https://awslabs.github.io/smithy/1.0/spec/aws/aws-restjson1-protocol.html#operation-error-serialization
func sanitizeErrorType(_ type: String) -> String {
    return type.substringAfter("#").substringBefore(":").trim()
}
