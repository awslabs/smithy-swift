//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

#if os(iOS) || os(macOS) || os(watchOS) || os(tvOS) || os(visionOS)

import struct Smithy.Attributes
import struct Smithy.SwiftLogger
import protocol Smithy.LogAgent
import protocol SmithyHTTPAPI.HTTPClient
import struct SmithyHTTPAPI.Headers
import class SmithyHTTPAPI.HTTPResponse
import class SmithyHTTPAPI.HTTPRequest
import enum SmithyHTTPAPI.HTTPStatusCode
import protocol Smithy.ReadableStream
import enum Smithy.ByteStream
import class SmithyStreams.BufferedStream
import class Foundation.Bundle
import class Foundation.InputStream
import class Foundation.NSObject
import class Foundation.NSRecursiveLock
import var Foundation.NSURLAuthenticationMethodClientCertificate
import var Foundation.NSURLAuthenticationMethodServerTrust
import class Foundation.URLAuthenticationChallenge
import struct Foundation.URLComponents
import class Foundation.URLCredential
import struct Foundation.URLQueryItem
import struct Foundation.URLRequest
import class Foundation.URLResponse
import class Foundation.HTTPURLResponse
import struct Foundation.TimeInterval
import class Foundation.URLSession
import class Foundation.URLSessionConfiguration
import class Foundation.URLSessionTask
import class Foundation.URLSessionTaskMetrics
import class Foundation.URLSessionDataTask
import protocol Foundation.URLSessionDataDelegate
import struct Foundation.Data
import AwsCommonRuntimeKit
import Security

/// A client that can be used to make requests to AWS services using `Foundation`'s `URLSession` HTTP client.
///
/// This client is usable on all Swift platforms that support both the `URLSession` library and Objective-C interoperability features
/// (these are generally the Apple platforms.)
///
/// Use of this client is recommended on all Apple platforms, and is required on Apple Watch ( see
/// [TN3135: Low-level networking on watchOS](https://developer.apple.com/documentation/technotes/tn3135-low-level-networking-on-watchos)
/// for details about allowable modes of networking on the Apple Watch platform.)
///
/// On Linux platforms, we recommend using the CRT-based HTTP client for its configurability and performance.
public final class URLSessionHTTPClient: HTTPClient {
    public static let noOpURLSessionHTTPClientTelemetry = HttpTelemetry(
        httpScope: "URLSessionHTTPClient",
        telemetryProvider: DefaultTelemetry.provider
    )

    /// Holds a connection's associated resources from the time the connection is executed to when it completes.
    private final class Connection {

        /// The `FoundationStreamBridge` for the request body, if any.
        ///
        /// This reference is stored with the connection so that it may be closed (and its resources disposed of)
        /// if the connection fails.
        let streamBridge: FoundationStreamBridge?

        /// The continuation for the asynchronous call that was made to initiate this request.
        ///
        /// Once the initial response is received, the continuation is called, and is subsequently set to `nil` so its
        /// resources may be deallocated and to prevent it from being resumed twice.
        private var continuation: CheckedContinuation<HTTPResponse, Error>?

        /// HTTP Client Telemetry
        private let telemetry: HttpTelemetry

        /// Returns `true` once the continuation is set to `nil`, which will happen once it has been resumed.
        var hasBeenResumed: Bool { continuation == nil }

        /// Resumes the continuation, returning the passed value.
        ///
        /// Calling this method and/or `resume(throwing:)` more than once has no effect.
        /// - Parameter httpResponse: The HTTP response to be asynchronously returned to the caller.
        func resume(returning httpResponse: HTTPResponse) {
            continuation?.resume(returning: httpResponse)
            continuation = nil
        }

        /// Resumes the continuation, throwing the passed error.
        ///
        /// Calling this method and/or `resume(returning:)` more than once has no effect.
        /// - Parameter error: The error to be asynchronously thrown to the caller.
        func resume(throwing error: Error) {
            continuation?.resume(throwing: error)
            continuation = nil
        }

        /// Any error received during a delegate callback for this request.
        ///
        /// The stored error is thrown back to the caller once the URLSessionDelegate receives
        /// `urlSession(_:task:didCompleteWithError)` for this connection.
        var error: Error?

        /// A response stream that streams the response back to the caller.  Data is buffered in-memory until read by the caller.
        let responseStream = BufferedStream()

        /// Creates a new connection object
        /// - Parameters:
        ///   - streamBridge: The `FoundationStreamBridge` for the connection.
        ///   - continuation: The continuation object for the `execute(request:)` call that initiated this connection.
        ///   - telemetry:    The HTTP client telemetry.
        init(
            streamBridge: FoundationStreamBridge?,
            continuation: CheckedContinuation<HTTPResponse, Error>,
            telemetry: HttpTelemetry
        ) {
            self.streamBridge = streamBridge
            self.continuation = continuation
            self.telemetry = telemetry
        }

        /// Ensure continuation is resumed and stream is closed before deallocation.
        ///
        /// This should never happen in practice but is being done defensively.
        deinit {
            if let continuation {
                continuation.resume(throwing: URLSessionHTTPClientError.unresumedConnection)
                responseStream.close()
            } else {
                // This has no effect if the response stream was already closed
                responseStream.closeWithError(URLSessionHTTPClientError.unclosedResponseStream)
            }
        }
    }

    /// Provides thread-safe associative storage of `Connection`s keyed by their `URLSessionDataTask`.
    private final class Storage: @unchecked Sendable {

        /// Lock used to enforce exclusive access to this `Storage` object.
        private let lock = NSRecursiveLock()

        /// A dictionary of `Connection`s, keyed by the `URLSessionTask` associated with them.
        private var connections = [URLSessionTask: Connection]()

        /// Adds a connection to the storage, keyed by its `URLSessionTask`.
        func set(_ connection: Connection, for key: URLSessionTask) {
            lock.lock()
            defer { lock.unlock() }
            connections[key] = connection
        }

        /// Allows modification of a `Connection` while holding exclusive access to it.
        ///
        /// Do not keep a reference to the connection outside the scope of `block`, or modify the connection after `block` returns.
        func modify(_ key: URLSessionTask, block: (Connection) -> Void) {
            lock.lock()
            defer { lock.unlock() }
            guard let connection = connections[key] else { return }
            block(connection)
        }

        /// Removes the connection keyed by `key` from storage.
        func remove(_ key: URLSessionTask) {
            lock.lock()
            defer { lock.unlock() }
            connections.removeValue(forKey: key)
        }
    }

    /// Handles URLSession delegate callbacks.
    private final class SessionDelegate: NSObject, URLSessionDataDelegate {

        /// Holds connection records for all in-progress connections.
        let storage = Storage()

        /// HTTP Client Telemetry
        let telemetry: HttpTelemetry

        /// Logger for HTTP-related events.
        let logger: LogAgent

        /// TLS options
        let tlsOptions: URLSessionTLSOptions?

        init(telemetry: HttpTelemetry, logger: LogAgent, tlsOptions: URLSessionTLSOptions?) {
            self.telemetry = telemetry
            self.logger = logger
            self.tlsOptions = tlsOptions
        }

        /// Handles server trust challenges by validating against a custom certificate.
        func didReceive(
            serverTrustChallenge challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            guard let tlsOptions = tlsOptions, tlsOptions.useSelfSignedCertificate,
                  let certFile = tlsOptions.certificate,
                  let serverTrust = challenge.protectionSpace.serverTrust else {
                logger.debug("Either TLSOptions not set or missing values! Using default trust store.")
                completionHandler(.performDefaultHandling, nil)
                return
            }

            guard let customRoot = Bundle.main.certificate(named: certFile) else {
                logger.debug("Certificate not found! Using default trust store.")
                completionHandler(.performDefaultHandling, nil)
                return
            }

            do {
                if try serverTrust.evaluateAllowing(rootCertificates: [customRoot]) {
                    completionHandler(.useCredential, URLCredential(trust: serverTrust))
                } else {
                    logger.error("Trust evaluation failed, cancelling authentication challenge.")
                    completionHandler(.cancelAuthenticationChallenge, nil)
                }
            } catch {
                logger.error("Trust evaluation threw an error: \(error.localizedDescription)")
                completionHandler(.cancelAuthenticationChallenge, nil)
            }
        }

        /// Handles client identity challenges by presenting a client certificate.
        func didReceive(
            clientIdentityChallenge challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            guard let tlsOptions, tlsOptions.useProvidedKeystore,
                  let keystoreName = tlsOptions.pkcs12Path,
                  let keystorePasword = tlsOptions.pkcs12Password else {
                logger.debug("Either TLSOptions not set or missing values! Using default keystore.")
                completionHandler(.performDefaultHandling, nil)
                return
            }

            guard let identity = Bundle.main.identity(named: keystoreName, password: keystorePasword) else {
                logger.error(
                    "Error accessing keystore! Ensure keystore file exists and password is correct!" +
                    " Using default keystore."
                )
                completionHandler(.performDefaultHandling, nil)
                return
            }

            completionHandler(
                .useCredential,
                URLCredential(identity: identity, certificates: nil, persistence: .forSession)
            )
        }

        /// The URLSession delegate method where authentication challenges are handled.
        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            switch challenge.protectionSpace.authenticationMethod {
            case NSURLAuthenticationMethodServerTrust:
                self.didReceive(serverTrustChallenge: challenge, completionHandler: completionHandler)
            case NSURLAuthenticationMethodClientCertificate:
                self.didReceive(clientIdentityChallenge: challenge, completionHandler: completionHandler)
            default:
                completionHandler(.performDefaultHandling, nil)
            }
        }

        /// Called when the initial response to a HTTP request is received.
        /// This callback is made as soon as the initial response + headers is complete.
        /// Response body data may continue to stream in after this callback is received.
        func urlSession(
            _ session: URLSession,
            dataTask: URLSessionDataTask,
            didReceive response: URLResponse,
            completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
        ) {
            logger.debug("urlSession(_:dataTask:didReceive response:) called")
            storage.modify(dataTask) { connection in
                guard let httpResponse = response as? HTTPURLResponse else {
                    logger.error("Received non-HTTP urlResponse")
                    let error = URLSessionHTTPClientError.responseNotHTTP
                    connection.resume(throwing: error)
                    return
                }
                let statusCode = HTTPStatusCode(rawValue: httpResponse.statusCode) ?? .insufficientStorage
                let httpHeaders: [HTTPHeader] = httpResponse.allHeaderFields.compactMap { (name, value) in
                    guard let name = name as? String else { return nil }
                    return HTTPHeader(name: name, value: String(describing: value))
                }
                let headers = Headers(httpHeaders: httpHeaders)
                let body = ByteStream.stream(connection.responseStream)
                let response = HTTPResponse(headers: headers, body: body, statusCode: statusCode)
                connection.resume(returning: response)
            }
            completionHandler(.allow)
        }

        /// Called when the task needs a new `InputStream` to continue streaming the request body.
        ///
        /// The `FoundationStreamBridge` is called and told to replace its bound streams; the new `InputStream` is then passed
        /// back through this method's `completionHandler` block.
        ///
        /// In practice, this seems to get called when multiple requests are made concurrently.
        /// - Parameters:
        ///   - session: The `URLSession` the task belongs to.
        ///   - task: The `URLSessionTask` that needs a new body stream.
        ///   - completionHandler: A block to be called with the new `InputStream` when it is ready.
        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            needNewBodyStream completionHandler: @escaping (InputStream?) -> Void
        ) {
            storage.modify(task) { connection in
                guard let streamBridge = connection.streamBridge else { completionHandler(nil); return }
                streamBridge.replaceStreams(completion: completionHandler)
            }
        }

        /// Called when response data is received.
        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
            logger.debug("urlSession(_:dataTask:didReceive data:) called (\(data.count) bytes)")
            storage.modify(dataTask) { connection in
                do {
                    // TICK - smithy.client.http.bytes_received
                    try connection.responseStream.write(contentsOf: data)
                    var attributes = Attributes()
                    attributes.set(
                        key: HttpMetricsAttributesKeys.serverAddress,
                        value: URLSessionHTTPClient.makeServerAddress(sessionTask: dataTask))
                    telemetry.bytesReceived.add(
                        value: data.count,
                        attributes: attributes,
                        context: telemetry.contextManager.current())
                } catch {
                    // If the response stream errored on write, save the error for later return &
                    // cancel the HTTP request.
                    connection.error = error
                    dataTask.cancel()
                }
            }
        }

        /// Called when a HTTP request completes, either successfully or not.
        /// If an error occurs, it will be returned here.
        /// If the error is returned prior to the initial response, the request fails with an error.
        /// If the error is returned after the initial response, the error is used to fail the response stream.
        func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
            let httpMethod = task.originalRequest?.httpMethod ?? ""
            let url = task.originalRequest?.url?.absoluteString ?? ""
            if let error {
                logger.error("URLRequest(\(httpMethod) \(url)) failed with error: \(error)")
            } else {
                logger.debug("URLRequest(\(httpMethod) \(url)) succeeded")
            }

            // This connection is complete.  No further data will be sent, and none will be received.
            // Below, we ensure that, successful or not, before disposing of the connection:
            //  - The continuation has been resumed.
            //  - The response stream is closed.
            //  - The stream bridge is closed.
            // This ensures that resources are freed and stream readers/writers are continued.
            storage.modify(task) { connection in
                if let error = connection.error ?? error {
                    if connection.hasBeenResumed {
                        connection.responseStream.closeWithError(error)
                    } else {
                        connection.resume(throwing: error)
                        connection.responseStream.close()
                    }
                } else {
                    if !connection.hasBeenResumed {
                        connection.resume(throwing: URLSessionHTTPClientError.closedBeforeResponse)
                    }
                    connection.responseStream.close()
                }

                // Close the stream bridge so that its resources are deallocated
                connection.streamBridge?.close()

                // Task is complete & no longer needed.  Remove it from storage.
                storage.remove(task)
            }
        }
    }

    /// The `HttpClientConfiguration` for this HTTP client.
    let config: HttpClientConfiguration

    /// The `URLSession` used to perform HTTP requests.
    let session: URLSession

    /// The delegate object used to handle `URLSessionTask` callbacks.
    private let delegate: SessionDelegate

    /// HTTP Client Telemetry
    private let telemetry: HttpTelemetry

    /// The logger for this HTTP client.
    private var logger: LogAgent

    /// The TLS options for this HTTP client.
    private let tlsConfiguration: URLSessionTLSOptions?

    /// The initial connection timeout for this HTTP client.
    let connectionTimeout: TimeInterval

    // MARK: - init & deinit

    /// Creates a new `URLSessionHTTPClient`.
    ///
    /// The client is created with its own internal `URLSession`, which is configured with system defaults and with a private delegate for handling
    /// URL task lifecycle events.
    /// - Parameters:
    ///   - httpClientConfiguration: The configuration to use for the client's `URLSession`.
    public convenience init(
        httpClientConfiguration: HttpClientConfiguration
    ) {
        self.init(httpClientConfiguration: httpClientConfiguration, sessionType: URLSession.self)
    }

    /// Creates a new `URLSessionHTTPClient`.
    ///
    /// The client is created with its own internal `URLSession`.  A mocked subclass may be substituted.
    /// - Parameters:
    ///   - httpClientConfiguration: The configuration to use for the client's `URLSession`.
    ///   - SessionType: The type for the URLSession to be created.  Used for testing.  Defaults to `URLSession`.
    init(
        httpClientConfiguration: HttpClientConfiguration,
        sessionType SessionType: URLSession.Type
    ) {
        self.config = httpClientConfiguration
        self.telemetry = httpClientConfiguration.telemetry ?? URLSessionHTTPClient.noOpURLSessionHTTPClientTelemetry
        self.logger = self.telemetry.loggerProvider.getLogger(name: "URLSessionHTTPClient")
        self.tlsConfiguration = config.tlsConfiguration as? URLSessionTLSOptions
        self.delegate = SessionDelegate(telemetry: telemetry, logger: logger, tlsOptions: tlsConfiguration)
        self.connectionTimeout = httpClientConfiguration.connectTimeout ?? 60.0
        var urlsessionConfiguration = URLSessionConfiguration.default
        urlsessionConfiguration = URLSessionConfiguration.from(httpClientConfiguration: httpClientConfiguration)
        self.session = SessionType.init(configuration: urlsessionConfiguration, delegate: delegate, delegateQueue: nil)
    }

    /// On deallocation, finish any in-process tasks before disposing of the `URLSession`.
    deinit {
        session.finishTasksAndInvalidate()
    }

    // MARK: - HttpClientEngine protocol

    /// Executes the passed HTTP request using Foundation's `URLSession` HTTP client.
    ///
    /// The request is converted to a `URLRequest`, and (if required) the streaming body is bridged to a Foundation `InputStream` and streamed to
    /// the remote server.
    /// - Parameter request: The request to be submitted to the server.  Fields must be filled in sufficiently to form a valid URL.
    /// - Returns: The response to the request.  This call may return as soon as a complete response is received but before the body finishes streaming;
    /// the response body will continue to stream back to the caller.
    public func send(request: HTTPRequest) async throws -> HTTPResponse {
        let telemetryContext = telemetry.contextManager.current()
        let tracer = telemetry.tracerProvider.getTracer(
            scope: telemetry.tracerScope,
            attributes: telemetry.tracerAttributes)
        do {
            // START - smithy.client.http.requests.queued_duration
            let queuedStart = Date().timeIntervalSinceReferenceDate
            let span = tracer.createSpan(
                name: telemetry.spanName,
                initialAttributes: telemetry.spanAttributes,
                spanKind: SpanKind.internal,
                parentContext: telemetryContext)
            defer {
                span.end()
            }
            return try await withCheckedThrowingContinuation { continuation in
                // Get the in-memory data or request stream to use for the body, if any.
                // Keep a reference to the stream bridge for a streaming request.
                let body: Body
                var streamBridge: FoundationStreamBridge?

                // START - smithy.client.http.connections.acquire_duration
                let acquireConnectionStart = Date().timeIntervalSinceReferenceDate

                // Convert the HTTP request body into a URLSession `Body`.
                switch request.body {
                case .data(let data):
//                    body = .data(data)
                    // Create a stream bridge that streams data from a SDK stream to a Foundation InputStream
                    // that URLSession can stream its request body from.
                    // Allow 16kb of in-memory buffer for request body streaming
                    let bridge = FoundationStreamBridge(
                        readableStream: BufferedStream(data: data, isClosed: true),
                        bridgeBufferSize: 16_384,
                        logger: logger,
                        telemetry: telemetry,
                        serverAddress: URLSessionHTTPClient.makeServerAddress(request: request)
                    )
                    streamBridge = bridge
                    body = .stream(bridge)
                case .stream(let stream):
                    // Create a stream bridge that streams data from a SDK stream to a Foundation InputStream
                    // that URLSession can stream its request body from.
                    // Allow 16kb of in-memory buffer for request body streaming
                    let bridge = FoundationStreamBridge(
                        readableStream: stream,
                        bridgeBufferSize: 16_384,
                        logger: logger,
                        telemetry: telemetry,
                        serverAddress: URLSessionHTTPClient.makeServerAddress(request: request)
                    )
                    streamBridge = bridge
                    body = .stream(bridge)
                case .noStream:
                    body = .data(nil)
                }

                let acquireConnectionEnd = Date().timeIntervalSinceReferenceDate
                telemetry.connectionsAcquireDuration.record(
                    value: acquireConnectionEnd - acquireConnectionStart,
                    attributes: Attributes(),
                    context: telemetryContext)
                // END - smithy.client.http.connections.acquire_duration
                let queuedEnd = acquireConnectionEnd
                telemetry.requestsQueuedDuration.record(
                    value: queuedEnd - queuedStart,
                    attributes: Attributes(),
                    context: telemetryContext)
                // END - smithy.client.http.requests.queued_duration

                let connectionsLimit = session.configuration.httpMaximumConnectionsPerHost
                let totalCount = session.delegateQueue.operationCount
                let maxConcurrentOperationCount = session.delegateQueue.maxConcurrentOperationCount
                telemetry.updateHTTPMetricsUsage { httpMetricsUsage in
                    // TICK - smithy.client.http.connections.limit
                    httpMetricsUsage.connectionsLimit = connectionsLimit

                    // TICK - smithy.client.http.connections.usage
                    // TODO(observability): instead of the transient stores, should rely on the Key/Value observer patttern
                    httpMetricsUsage.acquiredConnections =
                        totalCount < maxConcurrentOperationCount ? totalCount : maxConcurrentOperationCount
                    httpMetricsUsage.idleConnections = totalCount - httpMetricsUsage.acquiredConnections

                    // TICK - smithy.client.http.requests.usage
                    httpMetricsUsage.inflightRequests = httpMetricsUsage.acquiredConnections
                    httpMetricsUsage.queuedRequests = httpMetricsUsage.idleConnections
                }

                // Create the request (with a streaming body when needed.)
                do {
                    // DURATION - smithy.client.http.connections.uptime
                    let connectionUptimeStart = acquireConnectionEnd
                    defer {
                        telemetry.connectionsUptime.record(
                            value: Date().timeIntervalSinceReferenceDate - connectionUptimeStart,
                            attributes: Attributes(),
                            context: telemetryContext)
                    }
                    do {
                        // Create a data task for the request, and store it as a Connection along with its continuation.
                        let urlRequest = try self.makeURLRequest(from: request, body: body)
                        let dataTask = session.dataTask(with: urlRequest)

                        // Create a Connection and store it, keyed by its data task for retrieval on future
                        // delegate callbacks.
                        let connection = Connection(
                            streamBridge: streamBridge,
                            continuation: continuation,
                            telemetry: telemetry)
                        delegate.storage.set(connection, for: dataTask)

                        // Start the HTTP connection and start streaming the request body data, if needed
                        let httpMethod = urlRequest.httpMethod ?? ""
                        let url = urlRequest.url?.absoluteString ?? ""
                        logger.debug("URLRequest(\(httpMethod) \(url)) started")
                        logBodyDescription(body)
                        dataTask.resume()
                        streamBridge?.open()
                    } catch {
                        continuation.resume(throwing: error)
                    }
                }
            }
        }
    }

    // MARK: - Private methods & types

    /// A private type used to encapsulate the body to be used for a URLRequest.
    private enum Body {
        case stream(FoundationStreamBridge)
        case data(Data?)
    }

    /// Create a `URLRequest` for the Smithy operation to be performed.
    /// - Parameters:
    ///   - request: The SDK-native, signed `HTTPRequest` ready to be transmitted.
    ///   - body: A `Body` with either a stream bridge or data for this request's body.
    /// - Returns: A `URLRequest` ready to be transmitted by `URLSession` for this operation.
    private func makeURLRequest(from request: HTTPRequest, body: Body) throws -> URLRequest {
        var components = URLComponents()
        components.scheme = config.protocolType?.rawValue ?? request.destination.scheme.rawValue
        components.host = request.endpoint.uri.host
        components.port = port(for: request)
        components.percentEncodedPath = request.destination.path
        if let queryItems = request.queryItems, !queryItems.isEmpty {
            components.percentEncodedQueryItems = queryItems.map {
                URLQueryItem(name: $0.name, value: $0.value)
            }
        }
        guard let url = components.url else { throw URLSessionHTTPClientError.incompleteHTTPRequest }
        var urlRequest = URLRequest(url: url, timeoutInterval: self.connectionTimeout)
        urlRequest.httpMethod = request.method.rawValue
        switch body {
        case .stream(let bridge):
            urlRequest.httpBodyStream = bridge.inputStream
        case .data(let data):
            urlRequest.httpBody = data
        }
        for header in request.headers.headers + config.defaultHeaders.headers {
            for value in header.value {
                urlRequest.addValue(value, forHTTPHeaderField: header.name)
            }
        }
        return urlRequest
    }

    private func port(for request: HTTPRequest) -> Int? {
        switch (request.destination.scheme, request.destination.port) {
        case (.https, 443), (.http, 80):
            // Don't set port explicitly if it's the default port for the scheme
            return nil
        default:
            return request.destination.port.map { Int($0) }
        }
    }

    private func logBodyDescription(_ body: Body) {
        switch body {
        case .stream(let stream):
            let lengthString: String
            if let length = stream.readableStream.length {
                lengthString = "\(length) bytes"
            } else {
                lengthString = "unknown length"
            }
            logger.debug("body is InputStream (\(lengthString))")
        case .data(let data):
            if let data {
                logger.debug("body is Data (\(data.count) bytes)")
            } else {
                logger.debug("body is empty")
            }
        }
    }

    private static func makeServerAddress(sessionTask: URLSessionTask) -> String {
        let url = sessionTask.originalRequest?.url
        let host = url?.host ?? "unknown"
        if let port = url?.port {
            return "\(host):\(port)"
        } else {
            return host
        }
    }

    private static func makeServerAddress(request: HTTPRequest) -> String {
        let host = request.destination.host
        if let port = request.destination.port {
            return "\(host):\(port)"
        } else {
            return host
        }
    }
}

/// Errors that are particular to the URLSession-based Smithy HTTP client.
public enum URLSessionHTTPClientError: Error {

    /// A URL could not be formed from the `HTTPRequest`.
    /// Please file a bug with aws-sdk-swift if you experience this error.
    case incompleteHTTPRequest

    /// A non-HTTP response was returned by the server.
    /// Please file a bug with aws-sdk-swift if you experience this error.
    case responseNotHTTP

    /// A HTTP connection was closed before a response could be returned,
    /// and there was no Foundation error returned.
    /// Please file a bug with aws-sdk-swift if you experience this error.
    case closedBeforeResponse

    /// A connection was not ended before disposing the connection.
    /// Please file a bug with aws-sdk-swift if you experience this error.
    case unresumedConnection

    /// A response stream was not closed before disposing the connection.
    /// Please file a bug with aws-sdk-swift if you experience this error.
    case unclosedResponseStream
}

#endif
