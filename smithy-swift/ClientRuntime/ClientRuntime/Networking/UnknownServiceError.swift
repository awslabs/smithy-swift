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

/// General networking protocol independent service error structure used when exact error could not be deduced during deserialization
public struct UnknownServiceError: ServiceError {
    public var _message: String?
    
    public var _retryable: Bool? = false
    
    public var _type: ErrorType = .unknown
}

extension UnknownServiceError {
    public init(message: String? = nil) {
        self._message = message
    }
}

