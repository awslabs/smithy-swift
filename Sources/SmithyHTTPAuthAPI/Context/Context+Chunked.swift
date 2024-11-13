//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import class Smithy.Context
import struct Smithy.AttributeKey

public extension Context {
    var isChunkedEligibleStream: Bool? {
        get { get(key: isChunkedEligibleStreamKey) }
        set { set(key: isChunkedEligibleStreamKey, value: newValue) }
    }
}

private let isChunkedEligibleStreamKey = AttributeKey<Bool>(name: "isChunkedEligibleStreamKey")
