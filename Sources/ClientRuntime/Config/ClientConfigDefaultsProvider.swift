//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import SmithyIdentity
import SmithyIdentityAPI
import protocol SmithyHTTPAPI.HTTPClient
import struct SmithyRetries.DefaultRetryStrategy
import struct SmithyRetries.ExponentialBackoffStrategy
import struct SmithyRetriesAPI.RetryStrategyOptions

public typealias RuntimeConfigType
    = DefaultSDKRuntimeConfiguration<DefaultRetryStrategy, DefaultRetryErrorInfoProvider>

open class ClientConfigDefaultsProvider {
    /// Returns a default `HTTPClient` engine.
    open class func httpClientEngine() -> HTTPClient {
        return RuntimeConfigType.makeClient(
            httpClientConfiguration: RuntimeConfigType.defaultHttpClientConfiguration
        )
    }

    /// Returns default `HttpClientConfiguration`.
    open class func httpClientConfiguration() -> HttpClientConfiguration {
        return RuntimeConfigType.defaultHttpClientConfiguration
    }

    /// Returns a default idempotency token generator.
    open class func idempotencyTokenGenerator() -> IdempotencyTokenGenerator {
        return RuntimeConfigType.defaultIdempotencyTokenGenerator
    }

    /// Returns a default client logging mode.
    open class func clientLogMode() -> ClientLogMode {
        return RuntimeConfigType.defaultClientLogMode
    }

    /// Returns default retry strategy options *without* referencing AWS-specific config.
    open class func retryStrategyOptions(maxAttempts: Int? = nil) -> RetryStrategyOptions {
        // Provide some simple fallback for non-AWS usage, e.g. a standard exponential backoff.
        let attempts = maxAttempts ?? 3
        return RetryStrategyOptions(
            backoffStrategy: ExponentialBackoffStrategy(),
            maxRetriesBase: attempts - 1,
            rateLimitingMode: .standard
        )
    }
}
