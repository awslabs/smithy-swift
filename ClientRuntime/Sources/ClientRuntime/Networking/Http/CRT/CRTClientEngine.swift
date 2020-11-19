/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import AwsCommonRuntimeKit
import Logging

class CRTClientEngine: HttpClientEngine {
    
    private var logger: LogAgent
    private var connectionPools: [Endpoint: HttpClientConnectionManager] = [:]
    private let HOST_HEADER = "Host"
    private let CONTENT_LENGTH_HEADER = "Content-Length"
    private let CONNECTION_HEADER = "Connection"
    private let KEEP_ALIVE = "keep-alive"
    private let AWS_COMMON_RUNTIME = "AwsCommonRuntime"
    private let DEFAULT_STREAM_WINDOW_SIZE = 16 * 1024 * 1024 // 16 MB
    
    private let bootstrap: ClientBootstrap
    private let socketOptions: SocketOptions
    private let tlsContextOptions: TlsContextOptions
    private let tlsContext: TlsContext
    private let windowSize: Int
    private let maxConnectionsPerEndpoint: Int
    
    public init(config: CRTClientEngineConfig = CRTClientEngineConfig()) throws {
        AwsCommonRuntimeKit.initialize()
        self.maxConnectionsPerEndpoint = config.maxConnectionsPerEndpoint
        let elg = EventLoopGroup(threadCount: 1)
        let hostResolver = DefaultHostResolver(eventLoopGroup: elg, maxHosts: 8, maxTTL: 30)
        self.bootstrap = try ClientBootstrap(eventLoopGroup: elg, hostResolver: hostResolver)
        self.socketOptions = SocketOptions(socketType: .stream)
        let tlsContextOptions = TlsContextOptions()
        tlsContextOptions.setVerifyPeer(config.verifyPeer)
        self.tlsContextOptions = tlsContextOptions
        self.tlsContext = try TlsContext(options: tlsContextOptions, mode: .client)
        self.windowSize = config.windowSize
        self.logger = Logger(label: "CRTClientEngine")
    }
    
    private func createConnectionPool(endpoint: Endpoint) -> HttpClientConnectionManager {
        let tlsConnectionOptions = tlsContext.newConnectionOptions()
        let options = HttpClientConnectionOptions(clientBootstrap: bootstrap,
                                                  hostName: endpoint.host,
                                                  initialWindowSize: windowSize,
                                                  port: UInt16(endpoint.port),
                                                  proxyOptions: nil,
                                                  socketOptions: socketOptions,
                                                  tlsOptions: tlsConnectionOptions,
                                                  monitoringOptions: nil,
                                                  maxConnections: maxConnectionsPerEndpoint,
                                                  enableManualWindowManagement: true)
        logger.debug("Creating connection pool for \(endpoint.urlString)" +
                        "with max connections: \(maxConnectionsPerEndpoint)")
        return HttpClientConnectionManager(options: options)
    }
    
    private func getOrCreateConnectionPool(endpoint: Endpoint) -> HttpClientConnectionManager {
        
        guard let connectionPool = connectionPools[endpoint] else {
            let newConnectionPool = createConnectionPool(endpoint: endpoint)
            connectionPools[endpoint] = newConnectionPool //save in dictionary
            return newConnectionPool
        }
        
        return connectionPool
    }
    
    private func addHttpHeaders(endpoint: Endpoint, request: SdkHttpRequest) -> HttpRequest {
        
        var headers = request.headers
        headers.update(name: HOST_HEADER, value: endpoint.host)
        headers.update(name: CONNECTION_HEADER, value: KEEP_ALIVE)
        
        let contentLength: Int64 = {
            switch request.body {
            case .data(let data):
                return Int64(data?.count ?? 0)
            case .streamSource(let stream):
                //TODO: implement dynamic streaming with transfer-encoded-chunk header
                return stream.unwrap().contentLength
            case .none, .streamSink:
                return 0
            }
        }()
        
        headers.update(name: CONTENT_LENGTH_HEADER, value: "\(contentLength)")
        
        request.headers = headers
        return request.toHttpRequest(bufferSize: windowSize)
    }
    
    public func execute(request: SdkHttpRequest, completion: @escaping NetworkResult) {
        let isStreaming = { () -> Bool in
            switch request.body {
            case .streamSink, .streamSource: return true
            default: return false
            }
        }()
        let connectionMgr = getOrCreateConnectionPool(endpoint: request.endpoint)
        connectionMgr.acquireConnection().then { [self] (result) in
            logger.debug("connection was acquired to: \(request.endpoint.urlString)")
            switch result {
            case .success(let connection):
                let (requestOptions, future) = isStreaming ?
                    makeHttpRequestStreamOptions(request) : makeHttpRequestOptions(request)
                let stream = connection.makeRequest(requestOptions: requestOptions)
                stream.activate()
                future.then { (result) in
                    switch result {
                    case .success(let response):
                        logger.debug("Future of response came back with success: \(response)")
                        let statusCode = Int(stream.getResponseStatusCode())
                        response.statusCode = HttpStatusCode(rawValue: statusCode) ?? HttpStatusCode.notFound
                        completion(.success(response))
                    case .failure(let error):
                        logger.error("Future of response came back with an error: \(error)")
                        completion(.failure(error))
                    }
                }
            case .failure(let error):
                completion(.failure(error))
            }
        }
        
    }
    
    public func close() {
        for (endpoint, value) in connectionPools {
            logger.debug("connection to endpoint: \(endpoint.urlString)")
            value.closePendingConnections()
        }
    }
    
    public func makeHttpRequestStreamOptions(_ request: SdkHttpRequest) -> (HttpRequestOptions, Future<HttpResponse>) {
        let future = Future<HttpResponse>()
        let requestWithHeaders = addHttpHeaders(endpoint: request.endpoint, request: request)
        let response = HttpResponse()
        
        var streamSink: StreamSink?
        if case let HttpBody.streamSink(unwrappedStream) = request.body {
            //we know they want to receive a stream via their request body type
            streamSink = unwrappedStream.unwrap()
        }
        let requestOptions = HttpRequestOptions(request: requestWithHeaders) { [self] (stream, _, httpHeaders) in
            logger.debug("headers were received")
            response.statusCode = HttpStatusCode(rawValue: Int(stream.getResponseStatusCode()))
                ?? HttpStatusCode.notFound
            response.headers.addAll(httpHeaders: httpHeaders)
        } onIncomingHeadersBlockDone: { [self] (stream, _) in
            logger.debug("header block is done")
            response.statusCode = HttpStatusCode(rawValue: Int(stream.getResponseStatusCode()))
                ?? HttpStatusCode.notFound
        } onIncomingBody: { [self] (_, data) in
            logger.debug("incoming data")
            
            if let streamSink = streamSink {
                let byteBuffer = ByteBuffer(data: data)
                streamSink.receiveData(readFrom: byteBuffer)
            }
        } onStreamComplete: { [self] (_, error) in
            logger.debug("stream completed")
            if case let CRTError.crtError(unwrappedError) = error {
                if unwrappedError.errorCode != 0 {
                    logger.error("Response encountered an error: \(error)")
                    if let streamSink = streamSink {
                        streamSink.onError(error: StreamError.unknown(error))
                    }
                    future.fail(error)
                }
            }
             
            if let streamSink = streamSink {
                response.body = HttpBody.streamSink(.provider(streamSink))
            } else {
                response.body = HttpBody.none
            }
    
            future.fulfill(response)
        }
        
        return (requestOptions, future)
    }
    
    public func makeHttpRequestOptions(_ request: SdkHttpRequest) -> (HttpRequestOptions, Future<HttpResponse>) {
        let future = Future<HttpResponse>()
        let requestWithHeaders = addHttpHeaders(endpoint: request.endpoint, request: request)
        
        let response = HttpResponse()
        let incomingByteBuffer = ByteBuffer(size: 0)

        let requestOptions = HttpRequestOptions(request: requestWithHeaders) { [self] (stream, _, httpHeaders) in
            logger.debug("headers were received")
            response.statusCode = HttpStatusCode(rawValue: Int(stream.getResponseStatusCode()))
                ?? HttpStatusCode.notFound
            response.headers.addAll(httpHeaders: httpHeaders)
        } onIncomingHeadersBlockDone: { [self] (stream, _) in
            logger.debug("header block is done")
            response.statusCode = HttpStatusCode(rawValue: Int(stream.getResponseStatusCode()))
                ?? HttpStatusCode.notFound
        } onIncomingBody: { [self] (_, data) in
            logger.debug("incoming data")
            incomingByteBuffer.put(data)
        } onStreamComplete: { [self] (_, error) in
            logger.debug("stream completed")
            if case let CRTError.crtError(unwrappedError) = error {
                if unwrappedError.errorCode != 0 {
                    logger.error("Response encountered an error: \(error)")
                    if let streamClosure = stream.streamResponse {
                        streamClosure(.errorOccurred, incomingByteBuffer, StreamErrors.unknown(error))
                    }
                    future.fail(error)
                }
            }
            
            response.body = HttpBody.data(incomingByteBuffer.toData())
            future.fulfill(response)
        }
        
        return (requestOptions, future)
    }
    
    deinit {
        AwsCommonRuntimeKit.cleanUp()
    }
}
