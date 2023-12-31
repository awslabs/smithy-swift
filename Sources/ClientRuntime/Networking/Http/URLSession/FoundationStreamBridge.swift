//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

#if os(iOS) || os(macOS) || os(watchOS) || os(tvOS) || os(visionOS)

import class Foundation.DispatchQueue
import func Foundation.autoreleasepool
import class Foundation.NSObject
import class Foundation.Stream
import class Foundation.InputStream
import class Foundation.OutputStream
import class Foundation.Thread
import class Foundation.RunLoop
import protocol Foundation.StreamDelegate

/// Reads data from a smithy-swift native `ReadableStream` and streams the data to a Foundation `InputStream`.
///
/// Used to permit SDK streaming request bodies to be used with `URLSession`-based HTTP requests.
class FoundationStreamBridge: NSObject, StreamDelegate {

    /// The max number of bytes to buffer internally (and transfer) at any given time.
    let bufferSize: Int

    /// A buffer to hold data that has been read from the ReadableStream but not yet written to the OutputStream.
    private var buffer: Data

    /// The `ReadableStream` that will serve as the input to this bridge.
    /// The bridge will read bytes from this stream and dump them to the Foundation stream
    /// pair  as they become available.
    let readableStream: ReadableStream

    /// A Foundation stream that will carry the bytes read from the readableStream as they become available.
    let inputStream: InputStream

    /// A Foundation `OutputStream` that will read from the `ReadableStream`
    private let outputStream: OutputStream

    /// `true` if the readable stream has been found to be empty, `false` otherwise.  Will flip to `true` if the readable stream is read,
    /// and `nil` is returned.
    private var readableStreamEmpty = false

    private static let queue = DispatchQueue(label: "AWSFoundationStreamBridge")

    /// Foundation Streams require a run loop on which to post callbacks for their delegates.
    /// A single shared `Thread` is started and is used to host the RunLoop for all Foundation Stream callbacks.
    private static let thread: Thread = {
        let thread = Thread { autoreleasepool { RunLoop.current.run() } }
        thread.name = "AWSFoundationStreamBridge"
        thread.start()
        return thread
    }()

    // MARK: - init & deinit

    /// Creates a stream bridge taking the passed `ReadableStream` as its input
    ///
    /// Data will be buffered in an internal, in-memory buffer.  The Foundation `InputStream` that exposes `readableStream`
    /// is exposed by the `inputStream` property after creation.
    /// - Parameters:
    ///   - readableStream: The `ReadableStream` that serves as the input to the bridge.
    ///   - bufferSize: The number of bytes in the in-memory buffer.  The buffer is allocated for this size no matter if in use or not.
    ///   Defaults to 4096 bytes.
    init(readableStream: ReadableStream, bufferSize: Int = 4096) {
        var inputStream: InputStream?
        var outputStream: OutputStream?

        // Create a "bound stream pair" of Foundation streams.
        // Data written into the output stream will automatically flow to the inputStream for reading.
        // The bound streams have a buffer between them of size equal to the buffer held by this bridge.
        Foundation.Stream.getBoundStreams(
            withBufferSize: bufferSize, inputStream: &inputStream, outputStream: &outputStream
        )
        guard let inputStream, let outputStream else {
            // Fail with fatalError since this is not a failure that would happen in normal operation.
            fatalError("Get pair of bound streams failed.  Please file a bug with AWS SDK for Swift.")
        }
        self.bufferSize = bufferSize
        self.buffer = Data(capacity: bufferSize)
        self.readableStream = readableStream
        self.inputStream = inputStream
        self.outputStream = outputStream
    }

    // MARK: - Opening & closing

    /// Schedule the output stream on the special thread reserved for stream callbacks
    func open() async {
        await withCheckedContinuation { continuation in
            Self.queue.async {
                self.perform(#selector(self.scheduleOnThread), on: Self.thread, with: nil, waitUntilDone: true)
                continuation.resume()
            }
        }
    }

    /// Configure the output stream to make StreamDelegate callback to this bridge using the special thread / run loop, and open the output stream.
    /// The input stream is not included here.  It will be configured by URLSession when the HTTP request is initiated.
    @objc private func scheduleOnThread() {
        outputStream.delegate = self
        outputStream.schedule(in: RunLoop.current, forMode: .default)
        outputStream.open()
    }

    /// Unschedule the output stream.  Unscheduling must be performed on the special stream callback thread.
    func close() async {
        await withCheckedContinuation { continuation in
            Self.queue.async {
                self.perform(#selector(self.unscheduleOnThread), on: Self.thread, with: nil, waitUntilDone: true)
                continuation.resume()
            }
        }
    }

    /// Close the output stream and remove it from the thread / run loop.
    @objc private func unscheduleOnThread() {
        outputStream.close()
        outputStream.delegate = nil
        outputStream.remove(from: RunLoop.current, forMode: .default)
    }

    // MARK: - Status

    /// `true` when the bridge has no more data, nor will it ever.
    ///
    /// The `inputStream` may still have remaining data, however.
    var exhausted: Bool {
        readableStreamEmpty && buffer.isEmpty
    }

    // MARK: - Writing to bridge

    /// Tries to read from the readable stream if possible, then transfer the data to the output stream.
    private func writeToOutput() async throws {
        var data = Data()
        if !readableStreamEmpty, let newData = try await readableStream.readAsync(upToCount: bufferSize - bufferCount) {
            data = newData
        } else {
            readableStreamEmpty = true
            await close()
        }
        try await writeToOutputStream(data: data)
    }

    private class WriteToOutputStreamResult: NSObject {
        var data = Data()
        var error: Error?
    }

    private func writeToOutputStream(data: Data) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            Self.queue.async {
                let result = WriteToOutputStreamResult()
                result.data = data
                self.perform(#selector(self.writeToOutputStreamOnThread), on: Self.thread, with: result, waitUntilDone: true)
                if let error = result.error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    @objc private func writeToOutputStreamOnThread(_ result: WriteToOutputStreamResult) {
        buffer.append(result.data)
        var writeCount = 0
        buffer.withUnsafeBytes { bufferPtr in
            let bytePtr = bufferPtr.bindMemory(to: UInt8.self).baseAddress!
            writeCount = outputStream.write(bytePtr, maxLength: buffer.count)
        }
        if writeCount > 0 {
            buffer.removeFirst(writeCount)
        }
        result.error = outputStream.streamError
    }

    private class BufferCountResult: NSObject {
        var count = 0
    }

    private var bufferCount: Int {
        get async {
            await withCheckedContinuation { continuation in
                Self.queue.async {
                    let bc = BufferCountResult()
                    self.perform(#selector(self.bufferCountOnThread(_:)), on: Self.thread, with: bc, waitUntilDone: true)
                    continuation.resume(returning: bc.count)
                }
            }
        }
    }

    @objc private func bufferCountOnThread(_ result: BufferCountResult) {
        result.count = buffer.count
    }

    // MARK: - StreamDelegate protocol

    /// The stream places this callback when appropriate.  Call will be delivered on the special thread / run loop for stream callbacks.
    @objc func stream(_ aStream: Foundation.Stream, handle eventCode: Foundation.Stream.Event) {
        switch eventCode {
        case .hasSpaceAvailable:
            // Since space is available, try and read from the ReadableStream and
            // transfer the data to the Foundation stream pair.
            // Use a `Task` to perform the operation within Swift concurrency.
            Task { try await writeToOutput() }
        default:
            break
        }
    }
}

#endif
