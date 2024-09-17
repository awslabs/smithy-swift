//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import OpenTelemetryApi

import Smithy

extension Attributes {
    public func toOtelAttributes() -> [String: AttributeValue] {
        let keys: [String] = self.getKeys()
        var otelKeys: [String: AttributeValue] = [:]

        guard !keys.isEmpty else {
            return [:]
        }

        keys.forEach { key in
            otelKeys[key] = AttributeValue(self.get(key: AttributeKey(name: key))!)
        }

        return otelKeys
    }
}
