//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
import struct Foundation.TimeInterval
import struct Smithy.Document
import protocol Smithy.SmithyDocument
import enum SmithyReadWrite.ReaderError
@_spi(SmithyReadWrite) import protocol SmithyReadWrite.ShapeDeserializer
@_spi(SmithyReadWrite) import protocol SmithyReadWrite.SchemaProtocol
@_spi(SmithyReadWrite) import struct SmithyReadWrite.Schema
@_spi(SmithyTimestamps) import enum SmithyTimestamps.TimestampFormat

@_spi(SmithyReadWrite)
extension Reader: SmithyReadWrite.ShapeDeserializer {

    public func readStructure<Target>(schema: SmithyReadWrite.Schema<Target>) throws -> Target? {
        let resolvedReader = try resolvedReader(schema: schema)
        let structureSchema = resolvedTargetSchema(schema: schema)
        guard let factory = structureSchema.factory else {
            throw ReaderError.invalidSchema("Missing factory for structure or union: \(structureSchema.id)")
        }
        guard resolvedReader.hasContent, resolvedReader.jsonNode == .object else {
            return resolvedDefault(schema: schema) != nil ? factory() : nil
        }
        var value = factory()
        try structureSchema.members.forEach { memberContainer in
            try memberContainer.performRead(base: &value, key: "", reader: resolvedReader)
        }
        return value
    }

    public func readList<T>(schema: Schema<[T]>) throws -> [T]? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, resolvedReader.jsonNode == .array else {
            return resolvedDefault(schema: schema) != nil ? [] : nil
        }
        let listSchema = resolvedTargetSchema(schema: schema)
        guard let memberContainer = listSchema.members.first(
            where: { $0.member.memberSchema().memberName == "member" }
        ) else {
            throw ReaderError.requiredValueNotPresent
        }
        var value = [T]()
        for child in resolvedReader.children {
            try memberContainer.performRead(base: &value, key: "", reader: child)
        }
        return value
    }

    public func readMap<T>(schema: Schema<[String: T]>) throws -> [String: T]? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, resolvedReader.jsonNode == .object else {
            return resolvedDefault(schema: schema) != nil ? [:] : nil
        }
        let mapSchema = resolvedTargetSchema(schema: schema)
        guard let valueContainer = mapSchema.members.first(
            where: { $0.member.memberSchema().memberName == "value" }
        ) else {
            throw ReaderError.requiredValueNotPresent
        }
        var value = [String: T]()
        for child in resolvedReader.children {
            if !mapSchema.isSparse && child.jsonNode == .null { continue }
            try valueContainer.performRead(base: &value, key: child.nodeInfo.name, reader: child)
        }
        return value
    }

    public func readEnum<T: RawRepresentable>(schema: Schema<T>) throws -> T? where T.RawValue == String {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .string = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema).map { T(rawValue: try $0.asString())! }
        }
        guard let rawValue: String = try resolvedReader.readIfPresent() else { return nil }
        return T(rawValue: rawValue)
    }

    public func readIntEnum<T: RawRepresentable>(schema: Schema<T>) throws -> T? where T.RawValue == Int {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .number = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema).map { T(rawValue: try $0.asInteger())! }
        }
        guard let rawValue: Int = try resolvedReader.readIfPresent() else { return nil }
        return T(rawValue: rawValue)
    }

    public func readString(schema: Schema<String>) throws -> String? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .string = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asString()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readBoolean(schema: SmithyReadWrite.Schema<Bool>) throws -> Bool? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .bool = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asBoolean()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readByte(schema: SmithyReadWrite.Schema<Int8>) throws -> Int8? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .number = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asByte()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readShort(schema: SmithyReadWrite.Schema<Int16>) throws -> Int16? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .number = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asShort()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readInteger(schema: SmithyReadWrite.Schema<Int>) throws -> Int? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .number = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asInteger()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readLong(schema: SmithyReadWrite.Schema<Int>) throws -> Int? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .number = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asInteger()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readFloat(schema: SmithyReadWrite.Schema<Float>) throws -> Float? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent else {
            return try resolvedDefault(schema: schema)?.asFloat()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readDouble(schema: SmithyReadWrite.Schema<Double>) throws -> Double? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent else {
            return try resolvedDefault(schema: schema)?.asDouble()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readBigInteger(schema: SmithyReadWrite.Schema<Int64>) throws -> Int64? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .number = resolvedReader.jsonNode else {
            return try resolvedDefault(schema: schema)?.asBigInteger()
        }
        let int: Int? = try resolvedReader.readIfPresent()
        return int.map { Int64($0) }
    }

    public func readBigDecimal(schema: SmithyReadWrite.Schema<Double>) throws -> Double? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent else {
            return try resolvedDefault(schema: schema)?.asDouble()
        }
        return try resolvedReader.readIfPresent()
    }

    public func readBlob(schema: SmithyReadWrite.Schema<Data>) throws -> Data? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .string = resolvedReader.jsonNode else {
            guard let base64String = try resolvedDefault(schema: schema)?.asString() else { return nil }
            return Data(base64Encoded: base64String)
        }
        return try resolvedReader.readIfPresent()
    }

    public func readTimestamp(schema: SmithyReadWrite.Schema<Date>) throws -> Date? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent else {
            guard let defaultValue = resolvedDefault(schema: schema) else { return nil }
            switch defaultValue.type {
            case .float:
                let interval = try TimeInterval(defaultValue.asFloat())
                return Date(timeIntervalSince1970: interval)
            case .double:
                return try Date(timeIntervalSince1970: defaultValue.asDouble())
            case .timestamp:
                return try defaultValue.asTimestamp()
            default:
                throw ReaderError.invalidSchema("Unsupported timestamp default type: \(defaultValue.type)")
            }
        }
        let memberSchema = schema.type == .member ? schema : nil
        let timestampSchema = schema.targetSchema() ?? schema
        let resolvedTimestampFormat = memberSchema?.timestampFormat ?? timestampSchema.timestampFormat
        return try resolvedReader.readTimestampIfPresent(format: resolvedTimestampFormat ?? .epochSeconds)
    }

    public func readDocument(schema: SmithyReadWrite.Schema<Document>) throws -> Document? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent else {
            return resolvedDefault(schema: schema).map { Document($0) }
        }
        return try resolvedReader.readIfPresent()
    }

    public func readNull(schema: any SmithyReadWrite.SchemaProtocol) throws -> Bool? {
        let resolvedReader = try resolvedReader(schema: schema)
        guard resolvedReader.hasContent, case .null = resolvedReader.jsonNode else {
            return false
        }
        return try resolvedReader.readIfPresent()
    }

    private func resolvedReader(schema: any SchemaProtocol) throws -> Reader {
        if schema.httpPayload {
            return self
        } else if schema.containerType == .map || schema.containerType == .list || schema.containerType == .set {
            return self
        } else if schema.type == .member {
            let resolvedName = try resolvedName(memberSchema: schema)
            return self[NodeInfo(resolvedName)]
        } else {
            return self
        }
    }

    private func resolvedDefault<Target>(schema: Schema<Target>) -> (any SmithyDocument)? {
        if schema.type == .member {
            return schema.defaultValue ?? schema.targetSchema()?.defaultValue
        } else {
            return schema.defaultValue
        }
    }

    private func resolvedTargetSchema<Target>(schema: Schema<Target>) -> Schema<Target> {
        schema.targetSchema() ?? schema
    }

    private func resolvedName(memberSchema: any SchemaProtocol) throws -> String {
        if respectsJSONName {
            guard let resolvedName = memberSchema.jsonName ?? memberSchema.memberName else {
                throw ReaderError.requiredValueNotPresent
            }
            return resolvedName
        } else {
            guard let resolvedName = memberSchema.memberName else {
                throw ReaderError.requiredValueNotPresent
            }
            return resolvedName
        }
    }
}
