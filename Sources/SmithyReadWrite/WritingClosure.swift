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
public typealias WritingClosure<T, Writer> = (T, Writer) throws -> Void

@_spi(SmithyReadWrite)
public func mapWritingClosure<T, Writer: SmithyWriter>(
    valueWritingClosure: @escaping WritingClosure<T, Writer>,
    keyNodeInfo: Writer.NodeInfo,
    valueNodeInfo: Writer.NodeInfo,
    isFlattened: Bool
) -> WritingClosure<[String: T]?, Writer> {
    return { map, writer in
        try writer.writeMap(
            map,
            valueWritingClosure: valueWritingClosure,
            keyNodeInfo: keyNodeInfo,
            valueNodeInfo: valueNodeInfo,
            isFlattened: isFlattened
        )
    }
}

@_spi(SmithyReadWrite)
public func listWritingClosure<T, Writer: SmithyWriter>(
    memberWritingClosure: @escaping WritingClosure<T, Writer>,
    memberNodeInfo: Writer.NodeInfo,
    isFlattened: Bool
) -> WritingClosure<[T]?, Writer> {
    return { array, writer in
        try writer.writeList(
            array,
            memberWritingClosure: memberWritingClosure,
            memberNodeInfo: memberNodeInfo,
            isFlattened: isFlattened
        )
    }
}

@_spi(SmithyReadWrite)
public func timestampWritingClosure<Writer: SmithyWriter>(format: TimestampFormat) -> WritingClosure<Date?, Writer> {
    return { date, writer in
        try writer.writeTimestamp(date, format: format)
    }
}

@_spi(SmithyReadWrite)
public struct WritingClosureBox<Value: RawRepresentable> {

    public init() {}

    public func write<Writer: SmithyWriter>(value: Value?, to writer: Writer) throws where Value.RawValue == Int {
        try writer.write(value)
    }

    public func write<Writer: SmithyWriter>(value: Value?, to writer: Writer) throws where Value.RawValue == String {
        try writer.write(value)
    }
}

@_spi(SmithyReadWrite)
public enum WritingClosures {

    public static func writeString<Writer: SmithyWriter>(value: String?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeBool<Writer: SmithyWriter>(value: Bool?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeInt<Writer: SmithyWriter>(value: Int?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeInt8<Writer: SmithyWriter>(value: Int8?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeInt16<Writer: SmithyWriter>(value: Int16?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeDouble<Writer: SmithyWriter>(value: Double?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeFloat<Writer: SmithyWriter>(value: Float?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeData<Writer: SmithyWriter>(value: Data?, to writer: Writer) throws {
        try writer.write(value)
    }

    public static func writeDocument<Writer: SmithyWriter>(value: Document?, to writer: Writer) throws {
        try writer.write(value)
    }
}

@_spi(SmithyReadWrite)
public func sparseFormOf<T, Writer: SmithyWriter>(
    writingClosure: @escaping WritingClosure<T?, Writer>
) -> WritingClosure<T?, Writer> {
    return { value, writer in
        if let value {
            try writingClosure(value, writer)
        } else {
            try writer.writeNull()
        }
    }
}
