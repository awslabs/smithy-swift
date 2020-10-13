//
// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License").
// You may not use this file except in compliance with the License.
// A copy of the License is located at
//
// http://aws.amazon.com/apache2.0
//
// or in the "license" file accompanying this file. This file is distributed
// on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied. See the License for the specific language governing
// permissions and limitations under the License.
//

/// General Service Error structure used when exact error could not be deduced from the `HttpResponse`
public struct UnknownHttpServiceError: HttpServiceError {
    public var statusCode: HttpStatusCode?
    
    public var headers: HttpHeaders?
    
    public var message: String?
    
    public var retryable: Bool? = false
    
    public var type: ErrorType = .unknown
}

extension UnknownHttpServiceError {
    public init(httpResponse: HttpResponse, message: String? = nil) {
        self.statusCode = httpResponse.statusCode
        self.headers = httpResponse.headers
        self.message = message
    }
}

