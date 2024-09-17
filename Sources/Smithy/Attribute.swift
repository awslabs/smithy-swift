//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

/// Type safe property bag key
public struct AttributeKey<ValueType>: Sendable {
    let name: String

    public init(name: String) {
        self.name = name
    }

    public func getName() -> String {
        return self.name
    }

    func toString() -> String {
        return "AttributeKey: \(name)"
    }
}

/// Type safe property bag
public struct Attributes {
    private var attributes = [String: Any]()
    public var size: Int { attributes.count }

    public init() {}

    public func get<T>(key: AttributeKey<T>) -> T? {
        attributes[key.name] as? T
    }

    public func contains<T>(key: AttributeKey<T>) -> Bool {
        get(key: key) != nil
    }

    public mutating func set<T>(key: AttributeKey<T>, value: T?) {
        attributes[key.name] = value
    }

    public mutating func remove<T>(key: AttributeKey<T>) {
        attributes.removeValue(forKey: key.name)
    }

    public func getKeys() -> [String] {
        return Array(self.attributes.keys)
    }
}
