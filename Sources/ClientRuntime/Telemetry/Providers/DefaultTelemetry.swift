//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import struct Smithy.AttributeKey
import struct Smithy.Attributes
import protocol Smithy.LogAgent
import struct Smithy.SwiftLogger

/// Namespace for the Default SDK Telemetry implementations.
public enum DefaultTelemetry: Sendable {
    /// The Default SDK Telemetry Provider Implementation.
    ///
    /// - contextManager: no-op
    /// - loggerProvider: provides SwiftLoggers
    /// - meterProvider: no-op
    /// - tracerProvider: no-op
    public static let provider: TelemetryProvider = _DefaultTelemetryProvider()

    fileprivate final class _DefaultTelemetryProvider: TelemetryProvider {
        let contextManager: TelemetryContextManager = defaultContextManager
        let loggerProvider: LoggerProvider = defaultLoggerProvider
        let meterProvider: MeterProvider = defaultMeterProvider
        let tracerProvider: TracerProvider = defaultTracerProvider
    }
}

// Context
extension DefaultTelemetry {
    public static let defaultContextManager: TelemetryContextManager = NoOpTelemetryContextManager()
    fileprivate static let defaultTelemetryContext: TelemetryContext = NoOpTelemetryContext()
    fileprivate static let defaultTelemetryScope: TelemetryScope = NoOpTelemetryScope()

    fileprivate final class NoOpTelemetryContextManager: TelemetryContextManager {
        func current() -> TelemetryContext { defaultTelemetryContext }
    }

    fileprivate final class NoOpTelemetryContext: TelemetryContext {
        func makeCurrent() -> TelemetryScope { defaultTelemetryScope }
    }

    fileprivate final class NoOpTelemetryScope: TelemetryScope {
        func end() {}
    }
}

// Logging
extension DefaultTelemetry {
    public static let defaultLoggerProvider: LoggerProvider = _DefaultLoggerProvider()

    fileprivate final class _DefaultLoggerProvider: LoggerProvider {
        func getLogger(name: String) -> LogAgent { SwiftLogger(label: name) }
    }
}

// Metrics
extension DefaultTelemetry {
    public static let defaultMeterProvider: MeterProvider = NoOpMeterProvider()
    fileprivate static let defaultMeter: Meter = NoOpMeter()
    fileprivate static let defaultAsyncMeasurementHandle: AsyncMeasurementHandle = NoOpAsyncMeasurementHandle()
    fileprivate static let defaultUpDownCounter: UpDownCounter = NoOpUpDownCounter()
    fileprivate static let defaultMonotonicCounter: MonotonicCounter = NoOpMonotonicCounter()
    fileprivate static let defaultHistogram: Histogram = NoOpHistogram()

    fileprivate final class NoOpMeterProvider: MeterProvider {
        func getMeter(scope: String, attributes: Attributes?) -> Meter { defaultMeter }
    }

    fileprivate final class NoOpMeter: Meter {
        func createGauge(
            name: String,
            callback: @escaping (any DoubleAsyncMeasurement) -> Void,
            units: String?,
            description: String?
        ) -> AsyncMeasurementHandle {
            defaultAsyncMeasurementHandle
        }

        func createUpDownCounter(
            name: String,
            units: String?,
            description: String?
        ) -> UpDownCounter {
            defaultUpDownCounter
        }

        func createAsyncUpDownCounter(
            name: String,
            callback: @escaping (any LongAsyncMeasurement) -> Void,
            units: String?,
            description: String?
        ) -> AsyncMeasurementHandle {
            defaultAsyncMeasurementHandle
        }

        func createCounter(
            name: String,
            units: String?,
            description: String?
        ) -> MonotonicCounter {
            defaultMonotonicCounter
        }

        func createAsyncMonotonicCounter(
            name: String,
            callback: @escaping (any LongAsyncMeasurement) -> Void,
            units: String?,
            description: String?
        ) -> AsyncMeasurementHandle {
            defaultAsyncMeasurementHandle
        }

        func createHistogram(
            name: String,
            units: String?,
            description: String?
        ) -> Histogram {
            defaultHistogram
        }
    }

    fileprivate final class NoOpAsyncMeasurementHandle: AsyncMeasurementHandle {
        func stop() {}
    }

    fileprivate final class NoOpUpDownCounter: UpDownCounter {
        func add(value: Int, attributes: Attributes?, context: TelemetryContext?) {}
    }

    fileprivate final class NoOpMonotonicCounter: MonotonicCounter {
        func add(value: Int, attributes: Attributes?, context: TelemetryContext?) {}
    }

    fileprivate final class NoOpHistogram: Histogram {
        func record(value: Double, attributes: Attributes?, context: TelemetryContext?) {}
    }
}

// Trace
extension DefaultTelemetry {
    public static let defaultTracerProvider: TracerProvider = NoOpTracerProvider()
    fileprivate static let defaultTracer: Tracer = NoOpTracer()
    fileprivate static let defaultTraceSpan: TraceSpan = NoOpTraceSpan()

    fileprivate final class NoOpTracerProvider: TracerProvider {
        func getTracer(scope: String, attributes: Attributes?) -> Tracer { defaultTracer }
    }

    fileprivate final class NoOpTracer: Tracer {
        func createSpan(
            name: String,
            initialAttributes: Attributes?,
            spanKind: SpanKind,
            parentContext: TelemetryContext?
        ) -> TraceSpan {
            defaultTraceSpan
        }
    }

    fileprivate final class NoOpTraceSpan: TraceSpan {
        let name: String = ""
        func emitEvent(name: String, attributes: Attributes?) {}
        func setAttribute<T>(key: AttributeKey<T>, value: T) {}
        func setStatus(status: TraceSpanStatus) {}
        func end() {}
    }
}
