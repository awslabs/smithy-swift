//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import protocol Smithy.ResponseMessage
import protocol Smithy.Stream
import enum Smithy.ByteStream

public class HttpResponse: HttpUrlResponse, ResponseMessage {

    public var headers: Headers
    public var body: ByteStream
    public var statusCode: HttpStatusCode

    public init(headers: Headers = .init(), statusCode: HttpStatusCode = .processing, body: ByteStream = .noStream) {
        self.headers = headers
        self.statusCode = statusCode
        self.body = body
    }

    public init(headers: Headers = .init(), body: ByteStream, statusCode: HttpStatusCode) {
        self.body = body
        self.statusCode = statusCode
        self.headers = headers
    }
}

extension HttpResponse: CustomDebugStringConvertible {
    public var debugDescriptionWithBody: String {
        return debugDescription + "\nResponseBody: \(body.debugDescription)"
    }
    public var debugDescription: String {
        return "\nStatus Code: \(statusCode.description) \n \(headers)"
    }
}