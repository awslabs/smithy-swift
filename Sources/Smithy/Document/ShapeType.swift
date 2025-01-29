//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

/// Reproduces the cases in Smithy `ShapeType`.
/// https://github.com/smithy-lang/smithy/blob/main/smithy-model/src/main/java/software/amazon/smithy/model/shapes/ShapeType.java
public enum ShapeType {

    public enum Category {
        case simple
        case aggregate
        case service
        case member
    }

    case blob
    case boolean
    case string
    case timestamp
    case byte
    case short
    case integer
    case long
    case float
    case document
    case double
    case bigDecimal
    case bigInteger
    case `enum`
    case intEnum
    case list
    case set
    case map
    case structure
    case union
    case member
    case service
    case resource
    case operation

    public var category: Category {
        switch self {
        case .blob, .boolean, .string, .timestamp, .byte, .short, .integer, .long,
             .float, .document, .double, .bigDecimal, .bigInteger, .enum, .intEnum:
            return .simple
        case .list, .set, .map, .structure, .union:
            return .aggregate
        case .service, .resource, .operation:
            return .service
        case .member:
            return .member
        }
    }
}
