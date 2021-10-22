/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test

class HttpProtocolClientGeneratorTests {

    @Test
    fun `it renders client initialization block`() {
        val context = setupTests("service-generator-test-operations.smithy", "com.test#Example")
        val contents = getFileContents(context.manifest, "/RestJson/RestJsonProtocolClient.swift")
        contents.shouldSyntacticSanityCheck()
        contents.shouldContainOnlyOnce(
            """
            public class RestJsonProtocolClient {
                public static let clientName = "RestJsonProtocolClient"
                let client: ClientRuntime.SdkHttpClient
                let config: ClientRuntime.SDKRuntimeConfiguration
                let serviceName = "Rest Json Protocol"
                let encoder: ClientRuntime.RequestEncoder
                let decoder: ClientRuntime.ResponseDecoder
            
                public init(config: ClientRuntime.SDKRuntimeConfiguration) {
                    client = ClientRuntime.SdkHttpClient(engine: config.httpClientEngine, config: config.httpClientConfiguration)
                    let encoder = ClientRuntime.JSONEncoder()
                    encoder.dateEncodingStrategy = .secondsSince1970
                    encoder.nonConformingFloatEncodingStrategy = .convertToString(positiveInfinity: "Infinity", negativeInfinity: "-Infinity", nan: "NaN")
                    self.encoder = config.encoder ?? encoder
                    let decoder = ClientRuntime.JSONDecoder()
                    decoder.dateDecodingStrategy = .secondsSince1970
                    decoder.nonConformingFloatDecodingStrategy = .convertFromString(positiveInfinity: "Infinity", negativeInfinity: "-Infinity", nan: "NaN")
                    self.decoder = config.decoder ?? decoder
                    self.config = config
                }
            
                public convenience init() throws {
                    let config = try RestJsonProtocolClientConfiguration()
                    self.init(config: config)
                }
            
                deinit {
                    client.close()
                }
            
                public class RestJsonProtocolClientConfiguration: ClientRuntime.SDKRuntimeConfiguration {
            
                    public var clientLogMode: ClientRuntime.ClientLogMode
                    public var decoder: ClientRuntime.ResponseDecoder?
                    public var encoder: ClientRuntime.RequestEncoder?
                    public var httpClientConfiguration: ClientRuntime.HttpClientConfiguration
                    public var httpClientEngine: ClientRuntime.HttpClientEngine
                    public var idempotencyTokenGenerator: ClientRuntime.IdempotencyTokenGenerator
                    public var logger: ClientRuntime.LogAgent
                    public var retryer: ClientRuntime.SDKRetryer
            
                    public init(runtimeConfig: ClientRuntime.SDKRuntimeConfiguration) throws {
                        self.clientLogMode = runtimeConfig.clientLogMode
                        self.decoder = runtimeConfig.decoder
                        self.encoder = runtimeConfig.encoder
                        self.httpClientConfiguration = runtimeConfig.httpClientConfiguration
                        self.httpClientEngine = runtimeConfig.httpClientEngine
                        self.idempotencyTokenGenerator = runtimeConfig.idempotencyTokenGenerator
                        self.logger = runtimeConfig.logger
                        self.retryer = runtimeConfig.retryer
                    }
            
                    public convenience init() throws {
                        let defaultRuntimeConfig = try ClientRuntime.DefaultSDKRuntimeConfiguration("RestJsonProtocolClient")
                        try self.init(runtimeConfig: defaultRuntimeConfig)
                    }
                }
            }
            
            public struct RestJsonProtocolClientLogHandlerFactory: ClientRuntime.SDKLogHandlerFactory {
                public var label = "RestJsonProtocolClient"
                let logLevel: ClientRuntime.SDKLogLevel
                public func construct(label: String) -> LogHandler {
                    var handler = StreamLogHandler.standardOutput(label: label)
                    handler.logLevel = logLevel.toLoggerType()
                    return handler
                }
                public init(logLevel: ClientRuntime.SDKLogLevel) {
                    self.logLevel = logLevel
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `it renders host prefix with label in context correctly`() {
        val context = setupTests("host-prefix-operation.smithy", "com.test#Example")
        val contents = getFileContents(context.manifest, "/RestJson/RestJsonProtocolClient.swift")
        contents.shouldSyntacticSanityCheck()
        val expectedFragment = """
        let context = ClientRuntime.HttpContextBuilder()
                      .withEncoder(value: encoder)
                      .withDecoder(value: decoder)
                      .withMethod(value: .post)
                      .withServiceName(value: serviceName)
                      .withOperation(value: "getStatus")
                      .withIdempotencyTokenGenerator(value: config.idempotencyTokenGenerator)
                      .withLogger(value: config.logger)
        """
        contents.shouldContainOnlyOnce(expectedFragment)
    }

    @Test
    fun `it renders host prefix middleware with label correctly`() {
        val context = setupTests("host-prefix-operation.smithy", "com.test#Example")
        val contents = getFileContents(context.manifest, "/RestJson/models/GetStatusInput+UrlHostMiddleware.swift")
        contents.shouldSyntacticSanityCheck()
        val expectedFragment = """
        public struct GetStatusInputURLHostMiddleware: ClientRuntime.Middleware {
            public let id: Swift.String = "GetStatusInputURLHostMiddleware"
        
            let host: Swift.String?
        
            public init(host: Swift.String? = nil) {
                self.host = host
            }
        
            public func handle<H>(context: Context,
                          input: GetStatusInput,
                          next: H) -> Swift.Result<ClientRuntime.OperationOutput<GetStatusOutputResponse>, MError>
            where H: Handler,
            Self.MInput == H.Input,
            Self.MOutput == H.Output,
            Self.Context == H.Context,
            Self.MError == H.MiddlewareError
            {
                var copiedContext = context
                if let host = host {
                    copiedContext.attributes.set(key: AttributeKey<String>(name: "Host"), value: host)
                }
                copiedContext.attributes.set(key: AttributeKey<String>(name: "HostPrefix"), value: "\(input.foo!).data.")
                return next.handle(context: copiedContext, input: input)
            }
        """.trimIndent()
        contents.shouldContainOnlyOnce(expectedFragment)
    }

    @Test
    fun `it renders operation implementations in extension`() {
        val context = setupTests("service-generator-test-operations.smithy", "com.test#Example")
        val contents = getFileContents(context.manifest, "/RestJson/RestJsonProtocolClient.swift")
        contents.shouldSyntacticSanityCheck()
        contents.shouldContainOnlyOnce("extension RestJsonProtocolClient: RestJsonProtocolClientProtocol {")
    }

    @Test
    fun `it renders async operation implementations in extension`() {
        val context = setupTests("service-generator-test-operations.smithy", "com.test#Example")
        val contents = getFileContents(context.manifest, "/RestJson/RestJsonProtocolClient+Async.swift")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
        #if swift(>=5.5) && canImport(_Concurrency)
        @available(macOS 12.0, iOS 15.0, tvOS 15.0, watchOS 8.0, macCatalyst 15.0, *)
        public extension RestJsonProtocolClient {
            func allocateWidget(input: AllocateWidgetInput) async throws -> AllocateWidgetOutputResponse
            {
                typealias allocateWidgetContinuation = CheckedContinuation<AllocateWidgetOutputResponse, Swift.Error>
                return try await withCheckedThrowingContinuation { (continuation: allocateWidgetContinuation) in
                    allocateWidget(input: input) { result in
                        switch result {
                            case .success(let output):
                                continuation.resume(returning: output)
                            case .failure(let error):
                                continuation.resume(throwing: error)
                        }
                    }
                }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it renders an operation body`() {
        val context = setupTests("service-generator-test-operations.smithy", "com.test#Example")
        val contents = getFileContents(context.manifest, "/RestJson/RestJsonProtocolClient.swift")
        contents.shouldSyntacticSanityCheck()
        val expected = """
        extension RestJsonProtocolClient: RestJsonProtocolClientProtocol {
            public func allocateWidget(input: AllocateWidgetInput, completion: @escaping (ClientRuntime.SdkResult<AllocateWidgetOutputResponse, AllocateWidgetOutputError>) -> Void)
            {
                let context = ClientRuntime.HttpContextBuilder()
                              .withEncoder(value: encoder)
                              .withDecoder(value: decoder)
                              .withMethod(value: .post)
                              .withServiceName(value: serviceName)
                              .withOperation(value: "allocateWidget")
                              .withIdempotencyTokenGenerator(value: config.idempotencyTokenGenerator)
                              .withLogger(value: config.logger)
                var operation = ClientRuntime.OperationStack<AllocateWidgetInput, AllocateWidgetOutputResponse, AllocateWidgetOutputError>(id: "allocateWidget")
                operation.initializeStep.intercept(position: .after, id: "IdempotencyTokenMiddleware") { (context, input, next) -> Swift.Result<ClientRuntime.OperationOutput<AllocateWidgetOutputResponse>, ClientRuntime.SdkError<AllocateWidgetOutputError>> in
                    let idempotencyTokenGenerator = context.getIdempotencyTokenGenerator()
                    var copiedInput = input
                    if input.clientToken == nil {
                        copiedInput.clientToken = idempotencyTokenGenerator.generateToken()
                    }
                    return next.handle(context: context, input: copiedInput)
                }
                operation.initializeStep.intercept(position: .after, middleware: AllocateWidgetInputURLPathMiddleware())
                operation.initializeStep.intercept(position: .after, middleware: AllocateWidgetInputURLHostMiddleware())
                operation.serializeStep.intercept(position: .after, middleware: AllocateWidgetInputHeadersMiddleware())
                operation.serializeStep.intercept(position: .after, middleware: AllocateWidgetInputQueryItemMiddleware())
                operation.serializeStep.intercept(position: .after, middleware: ContentTypeMiddleware<AllocateWidgetInput, AllocateWidgetOutputResponse, AllocateWidgetOutputError>(contentType: "application/json"))
                operation.serializeStep.intercept(position: .after, middleware: AllocateWidgetInputBodyMiddleware())
                operation.finalizeStep.intercept(position: .before, middleware: ClientRuntime.ContentLengthMiddleware())
                operation.deserializeStep.intercept(position: .before, middleware: ClientRuntime.LoggerMiddleware(clientLogMode: config.clientLogMode))
                operation.deserializeStep.intercept(position: .after, middleware: ClientRuntime.DeserializeMiddleware())
                let result = operation.handleMiddleware(context: context.build(), input: input, next: client.getHandler())
                completion(result)
            }
        """.trimIndent()
        contents.shouldContainOnlyOnce(expected)
    }

    private fun setupTests(smithyFile: String, serviceShapeId: String): TestContext {
        val context = TestContext.initContextFrom(smithyFile, serviceShapeId, MockHttpRestJsonProtocolGenerator()) { model ->
            model.defaultSettings(serviceShapeId, "RestJson", "2019-12-16", "Rest Json Protocol")
        }
        context.generator.initializeMiddleware(context.generationCtx)
        context.generator.generateProtocolClient(context.generationCtx)
        context.generator.generateSerializers(context.generationCtx)
        context.generationCtx.delegator.flushWriters()
        return context
    }
}
