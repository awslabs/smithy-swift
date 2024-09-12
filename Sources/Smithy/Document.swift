//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Foundation.Data
import struct Foundation.Date
import class Foundation.DateFormatter
import class Foundation.JSONSerialization
import struct Foundation.Locale
import class Foundation.NSNull
import class Foundation.NSNumber
import func CoreFoundation.CFGetTypeID
import func CoreFoundation.CFBooleanGetTypeID

public enum Document {
    case list([Document])
    case boolean(Bool)
    case double(Double)
    case integer(Int)
    case byte(Int8)
    case short(Int16)
    case long(Int64)
    case float(Float)
    case bigDecimal(Double)
    case bigInteger(Int64)
    case map([String: Document])
    case string(String)
    case blob(Data)
    case timestamp(Date)
    case null
}

extension Document: Equatable { }

extension Document: ExpressibleByArrayLiteral {
    public init(arrayLiteral elements: Document...) {
        self = .list(elements)
    }
}

extension Document: ExpressibleByBooleanLiteral {
    public init(booleanLiteral value: Bool) {
        self = .boolean(value)
    }
}

extension Document: ExpressibleByDictionaryLiteral {
    public init(dictionaryLiteral elements: (String, Document)...) {
        let dictionary = elements.reduce([String: Document]()) { acc, curr in
            var newValue = acc
            newValue[curr.0] = curr.1
            return newValue
        }
        self = .map(dictionary)
    }
}

extension Document: ExpressibleByFloatLiteral {
    public init(floatLiteral value: Float) {
        self = .float(value)
    }
}

extension Document: ExpressibleByIntegerLiteral {
    public init(integerLiteral value: Int) {
        self = .integer(value)
    }
}

extension Document: ExpressibleByNilLiteral {
    public init(nilLiteral: ()) {
        self = .null
    }
}

extension Document: ExpressibleByStringLiteral {
    public init(stringLiteral value: String) {
        self = .string(value)
    }
}

// extension to use subscribts to get the values from objects/arrays as normal
public extension Document {
    subscript(_ key: String) -> Document? {
        getMember(key)
    }

    subscript(_ key: Int) -> Document? {
        switch self {
        case .list(let array):
            return array[key]
        case .map:
            return getMember("\(key)")
        default:
            return nil
        }
    }
}

extension Document {

    private var jsonObject: Any {
        do {
            switch self {
            case .list:
                return try asList().map { $0.jsonObject }
            case .boolean:
                return try asBoolean()
            case .byte:
                return try asByte()
            case .double:
                return try asDouble()
            case .integer:
                return try asInteger()
            case .float:
                return try asFloat()
            case .short:
                return try asShort()
            case .long:
                return try asLong()
            case .bigInteger:
                return try asBigInteger()
            case .bigDecimal:
                return try asBigDecimal()
            case .map:
                return try asStringMap().mapValues { $0.jsonObject }
            case .string:
                return try asString()
            case .blob:
                return try asBlob().base64EncodedString()
            case .timestamp:
                let formatter = DateFormatter()
                formatter.locale = Locale(identifier: "en_US_POSIX")
                formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
                return formatter.string(from: try asTimestamp())
            case .null:
                return NSNull()
            }
        } catch {
            // Handle or propagate the error as needed
            return NSNull()
        }
    }

    public static func make(from jsonObject: Any) throws -> Document {
        if let object = jsonObject as? [String: Any] {
            return .map(try object.mapValues { try Document.make(from: $0) })
        } else if let array = jsonObject as? [Any] {
            return .list(try array.map { try Document.make(from: $0) })
        } else if let nsNumber = jsonObject as? NSNumber {
            // Check if the NSNumber is a boolean
            if CFGetTypeID(nsNumber) == CFBooleanGetTypeID() {
                return .boolean(nsNumber.boolValue)
            }

            // Check numeric types
            let numberType = String(cString: nsNumber.objCType)
            switch numberType {
            case "c":  // char
                return .byte(nsNumber.int8Value)
            case "s":  // short
                return .short(nsNumber.int16Value)
            case "i", "l":  // int, long
                return .integer(nsNumber.intValue)
            case "q":  // long long
                return .long(nsNumber.int64Value)
            case "f":  // float
                return .float(nsNumber.floatValue)
            case "d":  // double
                return .double(nsNumber.doubleValue)
            default:
                throw SmithyDocumentError.invalidJSONData
            }
        } else if let string = jsonObject as? String {
            return .string(string)
        } else if let data = jsonObject as? Data {
            return .blob(data)
        } else if let date = jsonObject as? Date {
            return .timestamp(date)
        } else if jsonObject is NSNull {
            return .null
        } else {
            throw SmithyDocumentError.invalidJSONData
        }
    }

    public static func make(from data: Data) throws -> Document {
        let jsonObject = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try Document.make(from: jsonObject)
    }
}

enum SmithyDocumentError: Error {
    case invalidJSONData
    case typeMismatch(String)
    case numberOverflow(String)
    case invalidBase64(String)
    case invalidDateFormat(String)
}