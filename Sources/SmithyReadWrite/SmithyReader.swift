//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
import enum SmithyTimestamps.TimestampFormat

public protocol SmithyReader: AnyObject {
    associatedtype NodeInfo

    static func from(data: Data) throws -> Self

    var content: String? { get }
    subscript(_ nodeInfo: NodeInfo) -> Self { get }
    func readIfPresent<T>(readingClosure: ReadingClosure<T, Self>) throws -> T?
    func readIfPresent() throws -> String?
    func readIfPresent() throws -> Int8?
    func readIfPresent() throws -> Int16?
    func readIfPresent() throws -> Int?
    func readIfPresent() throws -> Float?
    func readIfPresent() throws -> Double?
    func readIfPresent() throws -> Bool?
    func readIfPresent() throws -> Data?
    func readIfPresent() throws -> Document?
    func readTimestampIfPresent(format: TimestampFormat) throws -> Date?
    func readIfPresent<T: RawRepresentable>() throws -> T? where T.RawValue == Int
    func readIfPresent<T: RawRepresentable>() throws -> T? where T.RawValue == String
    func readMapIfPresent<T>(
        valueReadingClosure: ReadingClosure<T?, Self>,
        keyNodeInfo: NodeInfo,
        valueNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws -> [String: T]?
    func readListIfPresent<Member>(
        memberReadingClosure: ReadingClosure<Member, Self>,
        memberNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws -> [Member]?
    func detach()
}

extension SmithyReader {

    public func read<T>(readingClosure: ReadingClosure<T, Self>) throws -> T {
        if let value = try readingClosure(self) {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> String {
        if let value: String = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Int8 {
        if let value: Int8 = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Int16 {
        if let value: Int16 = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Int {
        if let value: Int = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Float {
        if let value: Float = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Double {
        if let value: Double = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Bool {
        if let value: Bool = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read() throws -> Data {
        if let value: Data = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func readTimestamp(format: TimestampFormat) throws -> Date {
        if let value: Date = try readTimestampIfPresent(format: format) {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read<T: RawRepresentable>() throws -> T where T.RawValue == Int {
        if let value: T = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func read<T: RawRepresentable>() throws -> T where T.RawValue == String {
        if let value: T = try readIfPresent() {
            return value
        } else {
            throw ReaderError.requiredValueNotPresent
        }
    }

    public func readMap<T>(
        valueReadingClosure: ReadingClosure<T?, Self>,
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

    public func readList<T>(
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
}

public enum ReaderError: Error {
    case requiredValueNotPresent
}