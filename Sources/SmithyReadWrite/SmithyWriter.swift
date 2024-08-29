//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
@_spi(SmithyTimestamps) import enum SmithyTimestamps.TimestampFormat

@_spi(SmithyReadWrite)
public protocol SmithyWriter: AnyObject {
    associatedtype NodeInfo

    init(nodeInfo: NodeInfo)
    func data() throws -> Data

    subscript(_ nodeInfo: NodeInfo) -> Self { get }
    func write(_ value: Bool?) throws
    func write(_ value: String?) throws
    func write(_ value: Double?) throws
    func write(_ value: Float?) throws
    func write(_ value: Int?) throws
    func write(_ value: Int8?) throws
    func write(_ value: Int16?) throws
    func write(_ value: UInt8?) throws
    func write(_ value: Data?) throws
    func write(_ value: Document?) throws
    func writeTimestamp(_ value: Date?, format: TimestampFormat) throws
    func write<T: RawRepresentable>(_ value: T?) throws where T.RawValue == Int
    func write<T: RawRepresentable>(_ value: T?) throws where T.RawValue == String
    func writeMap<T>(
        _ value: [String: T]?,
        valueWritingClosure: WritingClosure<T, Self>,
        keyNodeInfo: NodeInfo,
        valueNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws
    func writeList<T>(
        _ value: [T]?,
        memberWritingClosure: WritingClosure<T, Self>,
        memberNodeInfo: NodeInfo,
        isFlattened: Bool
    ) throws
    func writeNull() throws
}

public extension SmithyWriter {

    static func write<T>(
        _ value: T,
        rootNodeInfo: NodeInfo,
        with writingClosure: WritingClosure<T, Self>
    ) throws -> Data? {
        let writer = Self(nodeInfo: rootNodeInfo)
        try writer.write(value, with: writingClosure)
        return try writer.data()
    }

    func write<T>(_ value: T, with writingClosure: WritingClosure<T, Self>) throws {
        return try writingClosure(value, self)
    }
}
