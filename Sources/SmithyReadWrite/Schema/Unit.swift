//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

@_spi(SmithyDocumentImpl) import Smithy

@_spi(SmithyReadWrite)
public struct Unit: Sendable, Equatable {

    public static func write<Writer: SmithyWriter>(value: Unit, to writer: Writer) throws {
        try writer.write(Document(StringMapDocument(value: [:])))
    }

    public init() {}
}
