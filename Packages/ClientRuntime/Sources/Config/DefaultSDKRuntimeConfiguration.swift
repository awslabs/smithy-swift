//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

public struct DefaultSDKRuntimeConfiguration: SDKRuntimeConfiguration {
    let retryer: Retryer

    public var logger: LogAgent

    public init(_ clientName: String) throws {
        self.retryer = try SDKRetryer()
        self.logger = SwiftLogger(label: clientName)
    }
}
