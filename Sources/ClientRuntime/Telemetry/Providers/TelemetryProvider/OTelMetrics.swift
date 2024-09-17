//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import OpenTelemetryApi
import OpenTelemetrySdk
import Smithy

// StableMeter is newer than Meter and will be renamed to Meter after a deprecation period
public typealias OpenTelemetryMeter = OpenTelemetryApi.StableMeter

// Metrics
public final class OTelMeterProvider: MeterProvider {
    private let sdkMeterProvider: StableMeterProviderSdk
    private let metricExporter: StableMetricExporter
    private let stablePeriodicMetricReader: StablePeriodicMetricReaderSdk

    public init(metricExporter: StableMetricExporter) {
        self.metricExporter = metricExporter
        self.stablePeriodicMetricReader = StablePeriodicMetricReaderBuilder(exporter: metricExporter).build()
        self.sdkMeterProvider = StableMeterProviderBuilder()
            .registerView(
                selector: InstrumentSelector.builder().setInstrument(name: ".*").build(),
                view: StableView.builder().build()
            )
            .registerMetricReader(reader: self.stablePeriodicMetricReader)
            .build()
        OpenTelemetry.registerStableMeterProvider(meterProvider: self.sdkMeterProvider)
    }

    /// Provides a Meter.
    ///
    /// - Parameter scope: the name of the instrumentation scope that uniquely identifies this meter
    /// - Parameter attributes: instrumentation scope attributes to associate with emitted telemetry data
    /// - Returns: a Meter
    public func getMeter(scope: String, attributes: Attributes?) -> any Meter {
        let meter = OpenTelemetry.instance.stableMeterProvider!.meterBuilder(name: scope).build()
        return OTelMeter(meter)
    }
}

public final class OTelMeter: Meter {
    private let otelMeter: OpenTelemetryMeter

    internal init(_ otelMeter: OpenTelemetryMeter) {
        self.otelMeter = otelMeter
    }

    public func createGauge(
        name: String,
        callback: @escaping (any DoubleAsyncMeasurement) -> Void, units: String?, description: String?
    ) -> any AsyncMeasurementHandle {
        // unused args: description
        let builder = self.otelMeter.gaugeBuilder(name: name).buildWithCallback { observer in
            callback(OTelDoubleAsyncMeasurementImpl(measurement: observer ))
        }
        return OTelDoubleGaugeAsyncMeasurementHandleImpl(otelHandle: builder)
    }

    public func createUpDownCounter(name: String, units: String?, description: String?) -> any UpDownCounter {
        // unused args: units, description
        let counter = self.otelMeter.upDownCounterBuilder(name: name).build()
        return OTelUpDownCounterImpl(instrument: counter)
    }

    public func createAsyncUpDownCounter(
        name: String,
        callback: @escaping (any LongAsyncMeasurement) -> Void, units: String?, description: String?
    ) -> any AsyncMeasurementHandle {
        // unused args: description
        let builder = self.otelMeter.upDownCounterBuilder(name: name).buildWithCallback { observer in
            callback(OTelLongAsyncMeasurementImpl(measurement: observer ))
        }
        return OTelLongUpDownAsyncMeasurementHandleImpl(otelHandle: builder)
    }

    public func createCounter(name: String, units: String?, description: String?) -> any MonotonicCounter {
        let builder = self.otelMeter.counterBuilder(name: name).build()
        return OTelMonotonicCounterImpl(instrument: builder)
    }

    public func createAsyncMonotonicCounter(
        name: String,
        callback: @escaping (any LongAsyncMeasurement) -> Void, units: String?, description: String?
    ) -> any AsyncMeasurementHandle {
        let builder = self.otelMeter.counterBuilder(name: name).buildWithCallback { observer in
            callback(OTelLongAsyncMeasurementImpl(measurement: observer ))
        }
        return OTelLongCounterAsyncMeasurementHandleImpl(otelHandle: builder)
    }

    public func createHistogram(name: String, units: String?, description: String?) -> any Histogram {
        let hist = self.otelMeter.histogramBuilder(name: name).build()
        return OTelDoubleHistogramImpl(instrument: hist)
    }
}

private class OTelDoubleAsyncMeasurementImpl: AsyncMeasurement {
    private let measurement: ObservableDoubleMeasurement

    public init(measurement: ObservableDoubleMeasurement) {
        self.measurement = measurement
    }

    func record(value: Double, attributes: Attributes?, context: (any TelemetryContext)?) {
        if let attributes = attributes, !(attributes.size == 0) {
            self.measurement.record(value: value, attributes: attributes.toOtelAttributes())
        } else {
            self.measurement.record(value: value)
        }
    }
}

private class OTelLongAsyncMeasurementImpl: AsyncMeasurement {
    private let measurement: ObservableLongMeasurement

    public init(measurement: ObservableLongMeasurement) {
        self.measurement = measurement
    }

    func record(value: Int, attributes: Attributes?, context: (any TelemetryContext)?) {
        if let attributes = attributes, !(attributes.size == 0) {
            self.measurement.record(value: value, attributes: attributes.toOtelAttributes())
        } else {
            self.measurement.record(value: value)
        }
    }
}

private class OTelUpDownCounterImpl: UpDownCounter {
    private var instrument: LongUpDownCounter

    public init(instrument: LongUpDownCounter) {
        self.instrument = instrument
    }

    func add(value: Int, attributes: Attributes?, context: (any TelemetryContext)?) {
        if let attributes = attributes, !(attributes.size == 0) {
            self.instrument.add(value: Int(value), attributes: attributes.toOtelAttributes())
        } else {
            self.instrument.add(value: Int(value))
        }
    }
}

private final class OTelMonotonicCounterImpl: MonotonicCounter {
    private var instrument: LongCounter

    public init(instrument: LongCounter) {
        self.instrument = instrument
    }

    func add(value: Int, attributes: Attributes?, context: (any TelemetryContext)?) {
        if let attributes = attributes, !(attributes.size == 0) {
            self.instrument.add(value: Int(value), attribute: attributes.toOtelAttributes())
        } else {
            self.instrument.add(value: Int(value))
        }
    }
}

private final class OTelDoubleHistogramImpl: Histogram {
    private var instrument: DoubleHistogram

    public init(instrument: DoubleHistogram) {
        self.instrument = instrument
    }

    func record(value: Double, attributes: Attributes?, context: (any TelemetryContext)?) {
        if let attributes = attributes, !(attributes.size == 0) {
            self.instrument.record(value: value, attributes: attributes.toOtelAttributes())
        } else {
            self.instrument.record(value: value)
        }
    }
}

private final class OTelDoubleGaugeAsyncMeasurementHandleImpl: AsyncMeasurementHandle {
    private let otelHandle: any ObservableDoubleGauge

    public init(otelHandle: any ObservableDoubleGauge) {
        self.otelHandle = otelHandle
    }

    func stop() {
        self.otelHandle.close()
    }
}

private final class OTelLongUpDownAsyncMeasurementHandleImpl: AsyncMeasurementHandle {
    private let otelHandle: any ObservableLongUpDownCounter

    public init(otelHandle: any ObservableLongUpDownCounter) {
        self.otelHandle = otelHandle
    }

    func stop() {
        self.otelHandle.close()
    }
}

private final class OTelLongCounterAsyncMeasurementHandleImpl: AsyncMeasurementHandle {
    private let otelHandle: any ObservableLongCounter

    public init(otelHandle: any ObservableLongCounter) {
        self.otelHandle = otelHandle
    }

    func stop() {
        self.otelHandle.close()
    }
}
