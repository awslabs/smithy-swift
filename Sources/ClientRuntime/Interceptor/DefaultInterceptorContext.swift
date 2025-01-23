//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import class Smithy.Context
import protocol Smithy.RequestMessage
import protocol Smithy.ResponseMessage
import struct Smithy.SwiftLogger

/// Default implementation for all interceptor context types.
///
/// This object will be created before operation execution, and passed through each interceptor
/// hook in the execution pipeline.
public class DefaultInterceptorContext<
    InputType,
    OutputType,
    RequestType: RequestMessage,
    ResponseType: ResponseMessage
>: InterceptorContext {
    private var attributes: Context
    private var input: InputType
    private var request: RequestType?
    private var response: ResponseType?
    private var result: Result<OutputType, Error>?

    public init(input: InputType, attributes: Context) {
        self.input = input
        self.attributes = attributes
    }

    public func getInput() -> InputType {
        self.input
    }

    public func getAttributes() -> Context {
        return self.attributes
    }

    internal func setResult(result: Result<OutputType, Error>?) {
        self.result = result
    }
}

extension DefaultInterceptorContext: BeforeSerialization {}

extension DefaultInterceptorContext: MutableInput {
    public func updateInput(updated: InputType) {
        self.input = updated
    }
}

extension DefaultInterceptorContext: AfterSerialization {
    public func getRequest() -> RequestType {
        self.request!
    }
}

extension DefaultInterceptorContext: MutableRequest {
    public func updateRequest(updated: RequestType) {
        self.request = updated
    }
}

extension DefaultInterceptorContext: BeforeDeserialization {
    public func getResponse() -> ResponseType {
        self.response!
    }
}

extension DefaultInterceptorContext: MutableResponse {
    public func updateResponse(updated: ResponseType) {
        self.response = updated
    }
}

extension DefaultInterceptorContext: AfterDeserialization {
    public func getOutput() throws -> OutputType {
        switch self.result! {
        case .success(let output):
            return output
        case .failure(let error):
            let logger = SwiftLogger(label: "GetOutputLogger")
            logger.info("ABOUT TO THROW AN ERROR \(error)")
            throw error
        }
    }
}

extension DefaultInterceptorContext: AfterAttempt {
    public func getResponse() -> ResponseType? {
        self.response
    }
}

extension DefaultInterceptorContext: MutableOutputAfterAttempt {
    public func updateOutput(updated: OutputType) {
        self.result = .success(updated)
    }
}

extension DefaultInterceptorContext: Finalization {
    public func getRequest() -> RequestType? {
        self.request
    }
}

extension DefaultInterceptorContext: MutableOutputFinalization {}
