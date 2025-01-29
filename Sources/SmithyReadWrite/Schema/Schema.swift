//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
@_spi(SmithyDocumentImpl) import Smithy
@_spi(SmithyTimestamps) import SmithyTimestamps

@_spi(SmithyReadWrite)
public protocol SchemaProtocol {
    var id: String { get }
    var type: ShapeType { get }
    var defaultValue: (any SmithyDocument)? { get }
    var jsonName: String? { get }
    var httpPayload: Bool { get }
    var enumValue: (any SmithyDocument)? { get }
    var memberName: String? { get }
    var containerType: ShapeType? { get }
    var isRequired: Bool { get }
}

public extension SchemaProtocol {

    var lastResortDefaultValue: any SmithyDocument {
        get throws {
            switch type {
            case .structure, .union, .map: return StringMapDocument(value: [:])
            case .string, .enum: return StringDocument(value: "")
            case .integer, .intEnum: return IntegerDocument(value: 0)
            case .boolean: return BooleanDocument(value: false)
            case .blob: return BlobDocument(value: Data())
            case .timestamp: return TimestampDocument(value: Date(timeIntervalSince1970: 0.0))
            case .bigDecimal: return BigDecimalDocument(value: 0.0)
            case .bigInteger: return BigIntegerDocument(value: 0)
            case .byte: return ByteDocument(value: 0)
            case .document: return NullDocument()
            case .list, .set: return ListDocument(value: [])
            case .short: return ShortDocument(value: 0)
            case .long: return LongDocument(value: 0)
            case .float: return FloatDocument(value: 0.0)
            case .double: return DoubleDocument(value: 0.0)
            case .member, .service, .resource, .operation:
                throw ReaderError.invalidSchema("Last resort not defined for type \(type)")
            }
        }
    }
}

@_spi(SmithyReadWrite)
public protocol MemberProtocol<Base> {
    associatedtype Base
    var memberSchema: () -> (any SchemaProtocol) { get }
    func performRead(base: inout Base, key: String, reader: any ShapeDeserializer) throws
    func performWrite(base: Base, writer: any SmithyWriter) throws
}

@_spi(SmithyReadWrite)
public struct MemberContainer<Base> {
    public let member: any MemberProtocol<Base>

    public init(member: any MemberProtocol<Base>) {
        self.member = member
    }

    public func performRead(base: inout Base, key: String, reader: any ShapeDeserializer) throws {
        try member.performRead(base: &base, key: key, reader: reader)
    }

    public func performWrite(base: Base, writer: any SmithyWriter) throws {
        try member.performWrite(base: base, writer: writer)
    }
}

@_spi(SmithyReadWrite)
public struct Schema<Base>: SchemaProtocol {

    public struct Member<Target>: MemberProtocol {
        public var memberSchema: () -> (any SchemaProtocol) { { memberSchemaSpecific() } }
        public let memberSchemaSpecific: () -> Schema<Target>
        let readBlock: (inout Base, String, any ShapeDeserializer) throws -> Void
        let writeBlock: (Base, any SmithyWriter) throws -> Void

        public init(
            memberSchema: @escaping () -> Schema<Target>,
            readBlock: @escaping (inout Base, String, any ShapeDeserializer) throws -> Void = { _, _, _ in },
            writeBlock: @escaping (Base, any SmithyWriter) throws -> Void = { _, _ in }
        ) {
            self.memberSchemaSpecific = memberSchema
            self.readBlock = readBlock
            self.writeBlock = writeBlock
        }

        public func performRead(base: inout Base, key: String, reader: any ShapeDeserializer) throws {
            try readBlock(&base, key, reader)
        }

        public func performWrite(base: Base, writer: any SmithyWriter) throws {
            try writeBlock(base, writer)
        }
    }

    public let id: String
    public let type: ShapeType
    public let factory: (() -> Base)?
    public let members: [MemberContainer<Base>]
    public let targetSchema: () -> Schema<Base>?
    public let memberName: String?
    public let containerType: ShapeType?
    public let jsonName: String?
    public let httpPayload: Bool
    public let enumValue: (any SmithyDocument)?
    public let timestampFormat: SmithyTimestamps.TimestampFormat?
    public let isSparse: Bool
    public let isRequired: Bool
    public let defaultValue: (any Smithy.SmithyDocument)?

    public init(
        id: String,
        type: ShapeType,
        factory: (() -> Base)? = nil,
        members: [MemberContainer<Base>] = [],
        targetSchema: @escaping () -> Schema<Base>? = { nil },
        memberName: String? = nil,
        containerType: ShapeType? = nil,
        jsonName: String? = nil,
        httpPayload: Bool = false,
        enumValue: (any SmithyDocument)? = nil,
        timestampFormat: SmithyTimestamps.TimestampFormat? = nil,
        isSparse: Bool = false,
        isRequired: Bool = false,
        defaultValue: (any SmithyDocument)? = nil
    ) {
        self.id = id
        self.type = type
        self.factory = factory
        self.members = members
        self.targetSchema = targetSchema
        self.memberName = memberName
        self.containerType = containerType
        self.jsonName = jsonName
        self.httpPayload = httpPayload
        self.enumValue = enumValue
        self.timestampFormat = timestampFormat
        self.isSparse = isSparse
        self.isRequired = isRequired
        self.defaultValue = defaultValue
    }
}
