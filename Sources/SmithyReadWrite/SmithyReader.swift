//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
@_spi(SmithyTimestamps) import enum SmithyTimestamps.TimestampFormat
import struct Smithy.Document

@_spi(SmithyReadWrite)
public protocol SmithyReader: AnyObject {
    associatedtype NodeInfo

    static func from(data: Data) throws -> Self
    static func readFrom<T>(_ data: Data, with readingClosure: ReadingClosure<T, Self>) throws -> T

    var hasContent: Bool { get }
    subscript(_ nodeInfo: NodeInfo) -> Self { get }
    func readIfPresent() throws -> String?
    func readIfPresent() throws -> Int8?
    func readIfPresent() throws -> Int16?
    func readIfPresent() throws -> Int?
    func readIfPresent() throws -> Float?
    func readIfPresent() throws -> Double?
    func readIfPresent() throws -> Bool?
    func readIfPresent() throws -> Data?
    func readIfPresent() throws -> Document?
    func readIfPresent<T: RawRepresentable>() throws -> T? where T.RawValue == Int
    func readIfPresent<T: RawRepresentable>() throws -> T? where T.RawValue == String
    func readTimestampIfPresent(format: TimestampFormat) throws -> Date?
    func readMapIfPresent<Value>(
        valueReadingClosure: ReadingClosure<Value, Self>,
        keyNodeInfo: NodeInfo,
        valueNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws -> [String: Value]?
    func readListIfPresent<Member>(
        memberReadingClosure: ReadingClosure<Member, Self>,
        memberNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws -> [Member]?

    /// Attempts to read a `null` value from the source document.
    /// - Returns: `true` if the value read is null, `false` if a value is present but it is not null, `nil` if no value is present.
    func readNullIfPresent() throws -> Bool?
}

public extension SmithyReader {

    static func readFrom<T>(_ data: Data, with readingClosure: ReadingClosure<T, Self>) throws -> T {
        let reader = try Self.from(data: data)
        return try readingClosure(reader)
    }

    func read<T>(with readingClosure: ReadingClosure<T, Self>) throws -> T {
        try readingClosure(self)
    }

    func readIfPresent<T>(with readingClosure: ReadingClosure<T, Self>) throws -> T? {
        do {
            return try readingClosure(self)
        } catch ReaderError.requiredValueNotPresent {
            return nil
        }
    }

    func read() throws -> String {
        if let value: String = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Int8 {
        if let value: Int8 = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Int16 {
        if let value: Int16 = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Int {
        if let value: Int = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Float {
        if let value: Float = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Double {
        if let value: Double = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Bool {
        if let value: Bool = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Data {
        if let value: Data = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read() throws -> Document {
        if let value: Document = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func readTimestamp(format: TimestampFormat) throws -> Date {
        if let value: Date = try readTimestampIfPresent(format: format) {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read<T: RawRepresentable>() throws -> T where T.RawValue == Int {
        if let value: T = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func read<T: RawRepresentable>() throws -> T where T.RawValue == String {
        if let value: T = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func readMap<T>(
        valueReadingClosure: ReadingClosure<T, Self>,
        keyNodeInfo: NodeInfo,
        valueNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws -> [String: T] {
        if let value: [String: T] = try readMapIfPresent(
            valueReadingClosure: valueReadingClosure,
            keyNodeInfo: keyNodeInfo,
            valueNodeInfo: valueNodeInfo,
            isFlattened: isFlattened
        ) {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func readList<T>(
        memberReadingClosure: ReadingClosure<T, Self>,
        memberNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws -> [T] {
        if let value: [T] = try readListIfPresent(
            memberReadingClosure: memberReadingClosure,
            memberNodeInfo: memberNodeInfo,
            isFlattened: isFlattened
        ) {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    func readNull() throws -> Bool {
        if let isNull = try readNullIfPresent() {
            return isNull
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }
}

public enum ReaderError: Error {
    case requiredValueNotPresent
    case invalidType(String)
    case invalidSchema(String)
}
