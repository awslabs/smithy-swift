//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Smithy.AttributeKey
import class Smithy.Context
import class Smithy.ContextBuilder

extension Context {

    public func getIdempotencyTokenGenerator() -> IdempotencyTokenGenerator {
        get(key: idempotencyTokenGeneratorKey)!
    }
}

extension ContextBuilder {

    @discardableResult
    public func withIdempotencyTokenGenerator(value: IdempotencyTokenGenerator) -> Self {
        self.attributes.set(key: idempotencyTokenGeneratorKey, value: value)
        return self
    }
}

private let idempotencyTokenGeneratorKey = AttributeKey<IdempotencyTokenGenerator>(name: "IdempotencyTokenGeneratorKey")
