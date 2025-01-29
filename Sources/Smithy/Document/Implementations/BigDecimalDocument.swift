//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

@_spi(SmithyDocumentImpl)
public struct BigDecimalDocument: SmithyDocument {
    public var type: ShapeType { .bigDecimal }
    let value: Double

    public init(value: Double) {
        self.value = value
    }

    public func asByte() throws -> Int8 {
        guard let byte = Int8(exactly: value) else {
            throw DocumentError.numberOverflow("BigDecimal \(value) overflows byte")
        }
        return byte
    }

    public func asShort() throws -> Int16 {
        guard let short = Int16(exactly: value) else {
            throw DocumentError.numberOverflow("BigDecimal \(value) overflows short")
        }
        return short
    }

    public func asInteger() throws -> Int {
        guard let int = Int(exactly: value) else {
            throw DocumentError.numberOverflow("BigDecimal \(value) overflows int")
        }
        return int
    }

    public func asLong() throws -> Int {
        guard let long = Int(exactly: value) else {
            throw DocumentError.numberOverflow("BigDecimal \(value) overflows long")
        }
        return long
    }

    public func asFloat() throws -> Float {
        guard let float = Float(exactly: value) else {
            throw DocumentError.numberOverflow("BigDecimal \(value) overflows float")
        }
        return float
    }

    public func asDouble() throws -> Double {
        value
    }

    public func asBigInteger() throws -> Int64 {
        Int64(value)
    }

    public func asBigDecimal() throws -> Double {
        value
    }
}
