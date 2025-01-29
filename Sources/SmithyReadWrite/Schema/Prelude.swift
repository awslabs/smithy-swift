//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
@_spi(SmithyDocumentImpl) import Smithy

@_spi(SmithyReadWrite)
public var unitSchema: Schema<Unit> {
    Schema<Unit>(id: "smithy.api#Unit", type: .structure, factory: { Unit() })
}

@_spi(SmithyReadWrite)
public var booleanSchema: Schema<Bool> {
    Schema<Bool>(id: "smithy.api#Boolean", type: .boolean)
}

@_spi(SmithyReadWrite)
public var stringSchema: Schema<String> {
    Schema<String>(id: "smithy.api#String", type: .string)
}

@_spi(SmithyReadWrite)
public var integerSchema: Schema<Int> {
    Schema<Int>(id: "smithy.api#Integer", type: .integer)
}

@_spi(SmithyReadWrite)
public var blobSchema: Schema<Data> {
    Schema<Data>(id: "smithy.api#Blob", type: .blob)
}

@_spi(SmithyReadWrite)
public var timestampSchema: Schema<Date> {
    Schema<Date>(id: "smithy.api#Timestamp", type: .timestamp)
}

@_spi(SmithyReadWrite)
public var byteSchema: Schema<Int8> {
    Schema<Int8>(id: "smithy.api#Byte", type: .byte)
}

@_spi(SmithyReadWrite)
public var shortSchema: Schema<Int16> {
    Schema<Int16>(id: "smithy.api#Short", type: .short)
}

@_spi(SmithyReadWrite)
public var longSchema: Schema<Int> {
    Schema<Int>(id: "smithy.api#Long", type: .long)
}

@_spi(SmithyReadWrite)
public var floatSchema: Schema<Float> {
    Schema<Float>(id: "smithy.api#Float", type: .float)
}

@_spi(SmithyReadWrite)
public var doubleSchema: Schema<Double> {
    Schema<Double>(id: "smithy.api#Double", type: .double)
}

@_spi(SmithyReadWrite)
public var documentSchema: Schema<Document> {
    Schema<Document>(id: "smithy.api#PrimitiveDocument", type: .document)
}

@_spi(SmithyReadWrite)
public var primitiveBooleanSchema: Schema<Bool> {
    Schema<Bool>(id: "smithy.api#PrimitiveBoolean", type: .boolean, defaultValue: BooleanDocument(value: false))
}

@_spi(SmithyReadWrite)
public var primitiveIntegerSchema: Schema<Int> {
    Schema<Int>(id: "smithy.api#PrimitiveInteger", type: .integer, defaultValue: IntegerDocument(value: 0))
}

@_spi(SmithyReadWrite)
public var primitiveByteSchema: Schema<Int8> {
    Schema<Int8>(id: "smithy.api#PrimitiveByte", type: .byte, defaultValue: ByteDocument(value: 0))
}

@_spi(SmithyReadWrite)
public var primitiveShortSchema: Schema<Int16> {
    Schema<Int16>(id: "smithy.api#PrimitiveShort", type: .short, defaultValue: ShortDocument(value: 0))
}

@_spi(SmithyReadWrite)
public var primitiveLongSchema: Schema<Int> {
    Schema<Int>(id: "smithy.api#PrimitiveLong", type: .long, defaultValue: LongDocument(value: 0))
}

@_spi(SmithyReadWrite)
public var primitiveFloatSchema: Schema<Float> {
    Schema<Float>(id: "smithy.api#PrimitiveFloat", type: .float, defaultValue: FloatDocument(value: 0.0))
}

@_spi(SmithyReadWrite)
public var primitiveDoubleSchema: Schema<Double> {
    Schema<Double>(id: "smithy.api#PrimitiveDouble", type: .double, defaultValue: DoubleDocument(value: 0.0))
}
