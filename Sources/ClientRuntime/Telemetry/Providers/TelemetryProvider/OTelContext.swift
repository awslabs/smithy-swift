//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import OpenTelemetryApi
import OpenTelemetrySdk

// Context (Not included at this time for Swift)
extension TelemetryProviderOTel {
    public static let defaultContextManager: TelemetryContextManager = NoOpTelemetryContextManager()
    fileprivate static let defaultTelemetryContext: TelemetryContext = NoOpTelemetryContext()
    fileprivate static let defaultTelemetryScope: TelemetryScope = NoOpTelemetryScope()

    fileprivate class NoOpTelemetryContextManager: TelemetryContextManager {
        func current() -> TelemetryContext { defaultTelemetryContext }
    }

    fileprivate class NoOpTelemetryContext: TelemetryContext {
        func makeCurrent() -> TelemetryScope { defaultTelemetryScope }
    }

    fileprivate class NoOpTelemetryScope: TelemetryScope {
        func end() {}
    }
}
