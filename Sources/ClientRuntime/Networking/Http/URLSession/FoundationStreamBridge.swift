//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

#if os(iOS) || os(macOS) || os(watchOS) || os(tvOS) || os(visionOS)

import protocol Smithy.LogAgent
import protocol Smithy.ReadableStream
import struct Smithy.Attributes
import func Foundation.CFWriteStreamSetDispatchQueue
import class Foundation.DispatchQueue
import class Foundation.NSObject
import class Foundation.Stream
import class Foundation.InputStream
import class Foundation.OutputStream
import class Foundation.Thread
import class Foundation.RunLoop
import class Foundation.Timer
import struct Foundation.TimeInterval
import struct Foundation.Data
import protocol Foundation.StreamDelegate

/// Reads data from a smithy-swift native `ReadableStream` and streams the data through to a Foundation `InputStream`.
///
/// A pair of Foundation "bound streams" is created.  Data from the `ReadableStream` is transferred into the Foundation
/// `OutputStream` until the `ReadableStream` is closed and all data has been read from it.  The Foundation
/// `InputStream` is exposed as a property, and may be used to stream the data to other components.
///
/// Used to permit SDK streaming request bodies to be used with `URLSession`-based HTTP requests.
class FoundationStreamBridge: NSObject, StreamDelegate {

    /// The max number of bytes to buffer between the `ReadableStream` and the Foundation `OutputStream`
    /// at any given time.
    let bridgeBufferSize: Int

    /// The max number of bytes to buffer between the Foundation `OutputStream` and the Foundation `InputStream`
    /// at any given time.
    let boundStreamBufferSize: Int

    /// A buffer to hold data that has been read from the `ReadableStream` but not yet written to the
    /// Foundation `OutputStream`.
    ///
    /// Access the buffer only from the stream bridge queue.  At most, at any time it will contain `bridgeBufferSize` bytes.
    private var _buffer: Data

    /// The `ReadableStream` that will serve as the input to this bridge.
    /// The bridge will read bytes from this stream and dump them to the Foundation stream
    /// pair as they become available.
    let readableStream: ReadableStream

    /// A Foundation stream that will carry the bytes read from the readableStream as they become available.
    ///
    /// May be replaced if needed by calling the `replaceStreams(_:)` method.
    var inputStream: InputStream

    /// A Foundation `OutputStream` that will read from the `ReadableStream`.
    ///
    /// Will be replaced when `replaceStreams(_:)` is called to replace the input stream.
    private var outputStream: OutputStream

    /// A Logger for logging events.
    private let logger: LogAgent

    /// HTTP Client Telemetry
    private let telemetry: HttpTelemetry

    /// Server address
    private let serverAddress: String

    /// A serial `DispatchQueue` to run the stream operations for the Foundation `OutputStream`.
    ///
    /// Operations performed on the queue include:
    /// - Opening the stream
    /// - Closing the stream
    /// - Writing to the stream
    /// - Receiving `StreamDelegate` callbacks
    ///
    /// Queue operations are run in the order they are placed on the queue, and only one operation
    /// runs at a time (i.e. this is a "serial queue".)
    private let queue = DispatchQueue(label: "AWSFoundationStreamBridge")

    /// `true` if the readable stream has been closed, `false` otherwise.  Will be flipped to `true` once the readable stream is read,
    /// and `nil` is returned.
    ///
    /// Access this variable only during a write operation to ensure exclusive access.
    private var readableStreamIsClosed = false

    // MARK: - init & deinit

    /// Creates a stream bridge taking the passed `ReadableStream` as its input., and exposing a Foundation `InputStream`
    /// that may be used for streaming data on to Foundation components.
    ///
    /// Data will be buffered in an internal, in-memory buffer.  The Foundation `InputStream` that exposes `readableStream`
    /// is exposed by the `inputStream` property after creation.
    /// - Parameters:
    ///   - readableStream: The `ReadableStream` that serves as the input to the bridge.
    ///   - bridgeBufferSize: The number of bytes in the in-memory buffer.  The buffer is allocated for this size no matter if in use or not.
    ///   Defaults to 65536 bytes (64 kb).
    ///   - boundStreamBufferSize: The number of bytes in the buffer between the bound Foundation streams.  If `nil`, uses the
    ///   same size as `bridgeBufferSize`.  Defaults to `nil`.  Primary use of this parameter is for testing.
    ///   - logger: A logger that can be used to log stream events.
    init(
        readableStream: ReadableStream,
        bridgeBufferSize: Int = 65_536,
        boundStreamBufferSize: Int? = nil,
        logger: LogAgent,
        telemetry: HttpTelemetry,
        serverAddress: String = "unknown"
    ) {
        self.bridgeBufferSize = bridgeBufferSize
        self.boundStreamBufferSize = boundStreamBufferSize ?? bridgeBufferSize
        self._buffer = Data(capacity: bridgeBufferSize)
        self.readableStream = readableStream
        self.logger = logger
        self.telemetry = telemetry
        self.serverAddress = serverAddress
        (inputStream, outputStream) = Self.makeStreams(boundStreamBufferSize: self.boundStreamBufferSize, queue: queue)
    }

    deinit {
        _streamTask?.cancel()
    }

    func replaceStreams(completion: @escaping (InputStream?) -> Void) {

        queue.async { [self] in
            // Close the current output stream, since it will accept no more data and is about to
            // be replaced.
            _close()

            // Replace the bound stream pair with new bound streams.
            (inputStream, outputStream) = Self.makeStreams(boundStreamBufferSize: boundStreamBufferSize, queue: queue)

            // Call the completion block.  When this method is called from `urlSession(_:task:needNewBodyStream:)`,
            // the completion block will be that method's completion handler.
            completion(inputStream)

            // Re-open the `OutputStream` for writing.
            _open()
        }
    }

    private static func makeStreams(boundStreamBufferSize: Int, queue: DispatchQueue) -> (InputStream, OutputStream) {
        var inputStream: InputStream?
        var outputStream: OutputStream?

        // Create a "bound stream pair" of Foundation streams.
        // Data written into the output stream will automatically flow to the inputStream for reading.
        // The bound streams have a buffer between them of size equal to the buffer held by this bridge.
        Foundation.Stream.getBoundStreams(
            withBufferSize: boundStreamBufferSize,
            inputStream: &inputStream,
            outputStream: &outputStream
        )
        guard let inputStream, let outputStream else {
            // Fail with fatalError since this is not a failure that would happen in normal operation.
            fatalError("Get pair of bound streams failed.  Please file a bug with AWS SDK for Swift.")
        }

        // The Foundation `OutputStream` is configured to deliver its callbacks on the dispatch queue.
        // This precludes the need for a Thread with RunLoop.
        // For safety, all interactions with the output stream will be performed on this queue.
        CFWriteStreamSetDispatchQueue(outputStream, queue)

        // Return the input & output streams to the caller in a tuple
        return (inputStream, outputStream)
    }

    // MARK: - Opening & closing

    /// Open the output stream and schedule this bridge to receive stream delegate callbacks.
    ///
    /// Stream operations are performed on the bridge's queue using a synchronous task, so that the stream has been opened
    /// before returning.  Do not call this method from the bridge's queue, or a deadlock will occur.
    func open() {
        queue.sync { _open() }
    }

    private func _open() {
        self.outputStream.delegate = self
        self.outputStream.open()
    }

    /// Close the output stream and unschedule this bridge from receiving stream delegate callbacks.
    ///
    /// Stream operations are performed on the bridge's queue using a synchronous task, so that the stream has been closed
    /// before returning.  Do not call this method from the bridge's queue, or a deadlock will occur.
    func close() {
        _streamTask?.cancel()
        queue.sync { _close() }
    }

    private func _close() {
        self.outputStream.close()
        self.outputStream.delegate = nil
    }

    // MARK: - Writing to bridge

    private var _streamTask: Task<Void, Never>?
    private var _spaceAvailableState = SpaceAvailableState.noSpaceAvailable

    enum SpaceAvailableState {
        case noSpaceAvailable
        case hasSpaceAvailable
        case awaitingSpaceAvailable(CheckedContinuation<Void, Never>)
    }

    private func _startFeed() {
        guard _streamTask == nil else { return }
        _streamTask = Task {
            var readableStreamIsOpen = true
            var bufferCount = 0

            while bufferCount > 0 || readableStreamIsOpen {
                var readableStreamData: Data?
                if readableStreamIsOpen {
                    let availableBufferSize = self.boundStreamBufferSize - bufferCount
                    do {
                        readableStreamData = try await readableStream.readAsync(upToCount: availableBufferSize)
                    } catch {
                        logger.error("Readable stream error received: \(error)")
                        readableStreamIsOpen = false
                    }
                }
                if let readableStreamData {
                    await waitForSpaceAvailable()
                    bufferCount = await writeToBufferAndFlush(readableStreamData)
                } else {
                    readableStreamIsOpen = false
                    bufferCount = await writeToBufferAndFlush(Data())
                }
            }
            await withCheckedContinuation { continuation in
                self.close()
                continuation.resume()
            }
        }
    }

    private func writeToBufferAndFlush(_ data: Data) async -> Int {
        await withCheckedContinuation { continuation in
            queue.async {
                self._buffer.append(data)
                if !self._buffer.isEmpty {
                    self._writeToOutputStream()
                }
                continuation.resume(returning: self._buffer.count)
            }
        }
    }

    private func waitForSpaceAvailable() async {
        await withCheckedContinuation { continuation in
            queue.async {
                switch self._spaceAvailableState {
                case .noSpaceAvailable:
                    self._spaceAvailableState = .awaitingSpaceAvailable(continuation)
                case .hasSpaceAvailable:
                    self._spaceAvailableState = .noSpaceAvailable
                    continuation.resume()
                case .awaitingSpaceAvailable:
                    fatalError()
                }
            }
        }
    }

    /// Write the buffered data to the Foundation `OutputStream` and write metrics.
    ///
    /// Call this method only from the bridge queue.
    private func _writeToOutputStream() {
        // Write to the output stream.  It may not accept all data, so get the number of bytes
        // it accepted in `writeCount`.
        var writeCount = 0
        _buffer.withUnsafeBytes { bufferPtr in
            guard let bytePtr = bufferPtr.bindMemory(to: UInt8.self).baseAddress else { return }
            writeCount = outputStream.write(bytePtr, maxLength: _buffer.count)
        }

        // `writeCount` will be a positive number if bytes were written.
        // Remove the written bytes from the front of the buffer.
        if writeCount > 0 {
            logger.debug("FoundationStreamBridge: wrote \(writeCount) bytes to request body")
            _buffer.removeFirst(writeCount)
            // TICK - smithy.client.http.bytes_sent
            var attributes = Attributes()
            attributes.set(
                key: HttpMetricsAttributesKeys.serverAddress,
                value: serverAddress)
            telemetry.bytesSent.add(
                value: writeCount,
                attributes: attributes,
                context: telemetry.contextManager.current())
        }
    }

    private func _updateHasSpaceAvailable() {
        switch _spaceAvailableState {
        case .noSpaceAvailable:
            _spaceAvailableState = .hasSpaceAvailable
        case .hasSpaceAvailable:
            break
        case .awaitingSpaceAvailable(let continuation):
            _spaceAvailableState = .noSpaceAvailable
            continuation.resume()
        }
    }

    // MARK: - StreamDelegate protocol

    /// The `FoundationStreamBridge` sets itself as the delegate of the Foundation `OutputStream` whenever the
    /// `OutputStream` is open.  Stream callbacks will be delivered on the stream bridge's GCD serial queue by calling this method.
    ///
    /// The `.openCompleted` event starts a `Task` to perform the data transfer the first time it is called; that `Task` will continue
    /// until the transfer is complete, even if the streams have to be replaced for the transfer.
    ///
    /// The `.hasSpaceAvailable` event updates the write coordinator so that data transfer will start or resume.
    /// - Parameters:
    ///   - aStream: The stream which experienced the event.
    ///   - eventCode: A code describing the type of event that happened.
    @objc func stream(_ aStream: Foundation.Stream, handle eventCode: Foundation.Stream.Event) {
        switch eventCode {
        case .openCompleted:
            _startFeed()
        case .hasBytesAvailable:
            // not used by OutputStream
            break
        case .hasSpaceAvailable:
            // Inform the write coordinator that there is space available
            // on the output stream so that writing of data may be resumed at the proper time.
            _updateHasSpaceAvailable()
        case .errorOccurred:
            logger.error("FoundationStreamBridge: .errorOccurred event")
            logger.error("FoundationStreamBridge: Stream error: \(aStream.streamError.debugDescription)")
        case .endEncountered:
            // not used by OutputStream
            break
        default:
            logger.info("FoundationStreamBridge: Other stream event occurred: \(eventCode)")
        }
    }
}

#endif
