//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Smithy.Document
@_spi(SmithyDocumentImpl) import struct Smithy.NullDocument
import struct Foundation.Data
import struct Foundation.Date

@_spi(SmithyReadWrite)
public protocol ShapeDeserializer: SmithyReader {
    func readStructure<T>(schema: Schema<T>) throws -> T?
    func readList<T>(schema: Schema<[T]>) throws -> [T]?
    func readMap<T>(schema: Schema<[String: T]>) throws -> [String: T]?
    func readBoolean(schema: Schema<Bool>) throws -> Bool?
    func readByte(schema: Schema<Int8>) throws -> Int8?
    func readShort(schema: Schema<Int16>) throws -> Int16?
    func readInteger(schema: Schema<Int>) throws -> Int?
    func readLong(schema: Schema<Int>) throws -> Int?
    func readFloat(schema: Schema<Float>) throws -> Float?
    func readDouble(schema: Schema<Double>) throws -> Double?
    func readBigInteger(schema: Schema<Int64>) throws -> Int64?
    func readBigDecimal(schema: Schema<Double>) throws -> Double?
    func readString(schema: Schema<String>) throws -> String?
    func readBlob(schema: Schema<Data>) throws -> Data?
    func readTimestamp(schema: Schema<Date>) throws -> Date?
    func readDocument(schema: Schema<Document>) throws -> Document?
    func readNull(schema: SchemaProtocol) throws -> Bool?
    func readEnum<T: RawRepresentable>(schema: Schema<T>) throws -> T? where T.RawValue == String
    func readIntEnum<T: RawRepresentable>(schema: Schema<T>) throws -> T? where T.RawValue == Int
}

@_spi(SmithyReadWrite)
public extension ShapeDeserializer {

    func readEnumNonNull<T: RawRepresentable>(schema: Schema<T>) throws -> T where T.RawValue == String {
        guard let value: T = try readEnum(schema: schema) else {
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readIntEnumNonNull<T: RawRepresentable>(schema: Schema<T>) throws -> T where T.RawValue == Int {
        guard let value: T = try readIntEnum(schema: schema) else {
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }
}

@_spi(SmithyReadWrite)
public extension ShapeDeserializer {

    func readStructureNonNull<T>(schema: Schema<T>) throws -> T {
        guard let value = try readStructure(schema: schema) else {
            guard let factory = schema.factory else {
                throw SmithyReadWrite.ReaderError.invalidSchema("Missing factory for structure or union: \(schema.id)")
            }
            if schema.isRequired { return factory() }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readListNonNull<T>(schema: Schema<[T]>) throws -> [T] {
        guard let value = try readList(schema: schema) else {
            if schema.isRequired { return [] }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readMapNonNull<T>(schema: Schema<[String: T]>) throws -> [String: T] {
        guard let value = try readMap(schema: schema) else {
            if schema.isRequired { return [:] }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readBooleanNonNull(schema: Schema<Bool>) throws -> Bool {
        guard let value = try readBoolean(schema: schema) else {
            if schema.isRequired { return false }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readByteNonNull(schema: Schema<Int8>) throws -> Int8 {
        guard let value = try readByte(schema: schema) else {
            if schema.isRequired { return 0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readShortNonNull(schema: Schema<Int16>) throws -> Int16 {
        guard let value = try readShort(schema: schema) else {
            if schema.isRequired { return 0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readIntegerNonNull(schema: Schema<Int>) throws -> Int {
        guard let value = try readInteger(schema: schema) else {
            if schema.isRequired { return 0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readLongNonNull(schema: Schema<Int>) throws -> Int {
        guard let value = try readLong(schema: schema) else {
            if schema.isRequired { return 0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readFloatNonNull(schema: Schema<Float>) throws -> Float {
        guard let value = try readFloat(schema: schema) else {
            if schema.isRequired { return 0.0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readDoubleNonNull(schema: Schema<Double>) throws -> Double {
        guard let value = try readDouble(schema: schema) else {
            if schema.isRequired { return 0.0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readBigIntegerNonNull(schema: Schema<Int64>) throws -> Int64 {
        guard let value = try readBigInteger(schema: schema) else {
            if schema.isRequired { return 0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readBigDecimalNonNull(schema: Schema<Double>) throws -> Double {
        guard let value = try readBigDecimal(schema: schema) else {
            if schema.isRequired { return 0.0 }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readStringNonNull(schema: Schema<String>) throws -> String {
        guard let value = try readString(schema: schema) else {
            if schema.isRequired { return "" }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readBlobNonNull(schema: Schema<Data>) throws -> Data {
        guard let value = try readBlob(schema: schema) else {
            if schema.isRequired { return Data() }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readTimestampNonNull(schema: Schema<Date>) throws -> Date {
        guard let value = try readTimestamp(schema: schema) else {
            if schema.isRequired { return Date(timeIntervalSince1970: 0.0) }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readDocumentNonNull(schema: Schema<Document>) throws -> Document {
        guard let value = try readDocument(schema: schema) else {
            if schema.isRequired { return Document(NullDocument()) }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }

    func readNullNonNull(schema: SchemaProtocol) throws -> Bool {
        guard let value = try readNull(schema: schema) else {
            if schema.isRequired { return false }
            throw ReaderError.requiredValueNotPresent
        }
        return value
    }
}
