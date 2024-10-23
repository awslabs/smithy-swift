/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import protocol SmithyChecksumsAPI.Checksum
import enum SmithyChecksumsAPI.ChecksumAlgorithm
import enum SmithyChecksumsAPI.HashResult
import struct Foundation.Data
import AwsCommonRuntimeKit

public enum HashError: Error {
    case invalidInput
    case hashingFailed(reason: String)
}

public enum UnknownChecksumError: Error {
    case notSupported(checksum: String)
}

extension ChecksumAlgorithm {

    public static func from(string: String) -> (ChecksumAlgorithm)? {
        switch string.lowercased() {
        case "crc32": return .crc32
        case "crc32c": return .crc32c
        case "crc64nvme": return .crc64nvme
        case "sha1": return .sha1
        case "sha256": return .sha256
        case "md5": return .md5 // md5 is not a valid flexible checksum algorithm
        default: return nil
        }
    }

    public static func fromList(_ stringArray: [String]) -> [ChecksumAlgorithm] {
        var hashFunctions = [ChecksumAlgorithm]()
        for string in stringArray {
            if let hashFunction = ChecksumAlgorithm.from(string: string) {
                hashFunctions.append(hashFunction)
            }
        }

        return hashFunctions
    }

    public var isFlexibleChecksum: Bool {
        switch self {
        case .crc32, .crc32c, .crc64nvme, .sha256, .sha1:
            return true
        default:
            return false
        }
    }

    public func createChecksum() -> any Checksum {
        switch self {
        case .crc32:
            return CRC32()
        case .crc32c:
            return CRC32C()
        case .crc64nvme:
            return CRC64NVME()
        case .sha1:
            return SHA1()
        case .sha256:
            return SHA256()
        case .md5:
            return MD5()
        }
    }
}

extension ChecksumAlgorithm: Comparable {
    /*
     * Priority-order for validating checksum = [ CRC32C, CRC32, CRC64NVME, SHA1, SHA256 ]
     * Order is determined by speed of the algorithm's implementation
     * MD5 is not supported by list ordering
     */
    public static func < (lhs: ChecksumAlgorithm, rhs: ChecksumAlgorithm) -> Bool {
        let order: [ChecksumAlgorithm] = [.crc32c, .crc32, .crc64nvme, .sha1, .sha256]

        let lhsIndex = order.firstIndex(of: lhs) ?? Int.max
        let rhsIndex = order.firstIndex(of: rhs) ?? Int.max

        return lhsIndex < rhsIndex
    }
}

extension [ChecksumAlgorithm] {
    public func getPriorityOrderValidationList() -> [ChecksumAlgorithm] {
        // Filter out .md5 if present and then sort the remaining hash functions
        return self.filter { $0 != .md5 }.sorted()
    }
}

extension UInt32 {
    public func toBase64EncodedString() -> String {
        // Create a Data instance from the UInt32 value
        let value = self
        var bigEndianValue = value.bigEndian
        let data = Data(bytes: &bigEndianValue, count: MemoryLayout<UInt32>.size)

        // Base64 encode the data
        return data.base64EncodedString()
    }
}

extension UInt64 {
    public func toBase64EncodedString() -> String {
        // Create a Data instance from the UInt64 value
        let value = self
        var bigEndianValue = value.bigEndian
        let data = Data(bytes: &bigEndianValue, count: MemoryLayout<UInt64>.size)

        // Base64 encode the data
        return data.base64EncodedString()
    }
}

extension HashResult {

    // Convert a HashResult to a hexadecimal String
    public func toHexString() -> String {
        switch self {
        case .data(let data):
            return data.map { String(format: "%02x", $0) }.joined()
        case .integer(let integer):
            return String(format: "%08x", integer)
        case .integer64(let integer64):
            return String(format: "%016x", integer64)
        }
    }

    // Convert a HashResult to a base64-encoded String
    public func toBase64String() -> String {
        switch self {
        case .data(let data):
            return data.base64EncodedString()
        case .integer(let integer):
            return integer.toBase64EncodedString()
        case .integer64(let integer64):
            return integer64.toBase64EncodedString()
        }
    }
}

public enum ChecksumMismatchException: Error {
    case message(String)
}
