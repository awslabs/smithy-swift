package software.amazon.smithy.swift.codegen.requestandresponse

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.swift.codegen.DefaultClientConfigurationIntegration
import software.amazon.smithy.swift.codegen.TestContext
import software.amazon.smithy.swift.codegen.defaultSettings
import software.amazon.smithy.swift.codegen.getFileContents
import software.amazon.smithy.swift.codegen.protocolgeneratormocks.MockHTTPRestJsonProtocolGenerator

class EventStreamTests {
    @Test
    fun `test MessageMarshallable`() {
        val context = setupTests("eventstream.smithy", "aws.protocoltests.restjson#TestService")
        println(context.manifest.files)
        val contents = getFileContents(context.manifest, "Sources/Example/models/TestStream+MessageMarshallable.swift")
        val expected = """
extension EventStreamTestClientTypes.TestStream {
    static var marshal: SmithyEventStreamsAPI.MarshalClosure<EventStreamTestClientTypes.TestStream> {
        { (self) in
            var headers: [SmithyEventStreamsAPI.Header] = [.init(name: ":message-type", value: .string("event"))]
            var payload: Foundation.Data? = nil
            switch self {
            case .messagewithblob(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithBlob")))
                headers.append(.init(name: ":content-type", value: .string("application/octet-stream")))
                payload = value.data
            case .messagewithstring(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithString")))
                headers.append(.init(name: ":content-type", value: .string("text/plain")))
                payload = value.data?.data(using: .utf8)
            case .messagewithstruct(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithStruct")))
                headers.append(.init(name: ":content-type", value: .string("application/json")))
                payload = try SmithyJSON.Writer.write(value.someStruct, rootNodeInfo: "", with: EventStreamTestClientTypes.TestStruct.write(value:to:))
            case .messagewithunion(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithUnion")))
                headers.append(.init(name: ":content-type", value: .string("application/json")))
                payload = try SmithyJSON.Writer.write(value.someUnion, rootNodeInfo: "", with: EventStreamTestClientTypes.TestUnion.write(value:to:))
            case .messagewithheaders(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithHeaders")))
                if let headerValue = value.blob {
                    headers.append(.init(name: "blob", value: .byteArray(headerValue)))
                }
                if let headerValue = value.boolean {
                    headers.append(.init(name: "boolean", value: .bool(headerValue)))
                }
                if let headerValue = value.byte {
                    headers.append(.init(name: "byte", value: .byte(headerValue)))
                }
                if let headerValue = value.int {
                    headers.append(.init(name: "int", value: .int32(Int32(headerValue))))
                }
                if let headerValue = value.long {
                    headers.append(.init(name: "long", value: .int64(Int64(headerValue))))
                }
                if let headerValue = value.short {
                    headers.append(.init(name: "short", value: .int16(headerValue)))
                }
                if let headerValue = value.string {
                    headers.append(.init(name: "string", value: .string(headerValue)))
                }
                if let headerValue = value.timestamp {
                    headers.append(.init(name: "timestamp", value: .timestamp(headerValue)))
                }
            case .messagewithheaderandpayload(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithHeaderAndPayload")))
                if let headerValue = value.header {
                    headers.append(.init(name: "header", value: .string(headerValue)))
                }
                headers.append(.init(name: ":content-type", value: .string("application/octet-stream")))
                payload = value.payload
            case .messagewithnoheaderpayloadtraits(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithNoHeaderPayloadTraits")))
                headers.append(.init(name: ":content-type", value: .string("application/json")))
                let writer = SmithyJSON.Writer(nodeInfo: "")
                try writer["someInt"].write(value.someInt, with: SmithyReadWrite.WritingClosures.writeInt(value:to:))
                try writer["someString"].write(value.someString, with: SmithyReadWrite.WritingClosures.writeString(value:to:))
                payload = try writer.data()
            case .messagewithunboundpayloadtraits(let value):
                headers.append(.init(name: ":event-type", value: .string("MessageWithUnboundPayloadTraits")))
                if let headerValue = value.header {
                    headers.append(.init(name: "header", value: .string(headerValue)))
                }
                headers.append(.init(name: ":content-type", value: .string("application/json")))
                let writer = SmithyJSON.Writer(nodeInfo: "")
                try writer["unboundString"].write(value.unboundString, with: SmithyReadWrite.WritingClosures.writeString(value:to:))
                payload = try writer.data()
            case .sdkUnknown(_):
                throw Smithy.ClientError.unknownError("cannot serialize the unknown event type!")
            }
            return SmithyEventStreamsAPI.Message(headers: headers, payload: payload ?? .init())
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `test MessageUnmarshallable`() {
        val context = setupTests("eventstream.smithy", "aws.protocoltests.restjson#TestService")
        val contents = getFileContents(context.manifest, "Sources/Example/models/TestStream+MessageUnmarshallable.swift")
        val expected = """
extension EventStreamTestClientTypes.TestStream {
    static var unmarshal: SmithyEventStreamsAPI.UnmarshalClosure<EventStreamTestClientTypes.TestStream> {
        { message in
            switch try message.type() {
            case .event(let params):
                switch params.eventType {
                case "MessageWithBlob":
                    var event = EventStreamTestClientTypes.MessageWithBlob()
                    event.data = message.payload
                    return .messagewithblob(event)
                case "MessageWithString":
                    var event = EventStreamTestClientTypes.MessageWithString()
                    event.data = String(data: message.payload, encoding: .utf8)
                    return .messagewithstring(event)
                case "MessageWithStruct":
                    var event = EventStreamTestClientTypes.MessageWithStruct()
                    let value = try SmithyJSON.Reader.from(data: message.payload).readStructureNonNull(schema: schema__namespace_aws_protocoltests_restjson__name_TestStruct)
                    event.someStruct = value
                    return .messagewithstruct(event)
                case "MessageWithUnion":
                    var event = EventStreamTestClientTypes.MessageWithUnion()
                    let value = try SmithyJSON.Reader.from(data: message.payload).readStructureNonNull(schema: schema__namespace_aws_protocoltests_restjson__name_TestUnion)
                    event.someUnion = value
                    return .messagewithunion(event)
                case "MessageWithHeaders":
                    var event = EventStreamTestClientTypes.MessageWithHeaders()
                    if case .byteArray(let value) = message.headers.value(name: "blob") {
                        event.blob = value
                    }
                    if case .bool(let value) = message.headers.value(name: "boolean") {
                        event.boolean = value
                    }
                    if case .byte(let value) = message.headers.value(name: "byte") {
                        event.byte = value
                    }
                    if case .int32(let value) = message.headers.value(name: "int") {
                        event.int = Int(value)
                    }
                    if case .int64(let value) = message.headers.value(name: "long") {
                        event.long = Int(value)
                    }
                    if case .int16(let value) = message.headers.value(name: "short") {
                        event.short = value
                    }
                    if case .string(let value) = message.headers.value(name: "string") {
                        event.string = value
                    }
                    if case .timestamp(let value) = message.headers.value(name: "timestamp") {
                        event.timestamp = value
                    }
                    return .messagewithheaders(event)
                case "MessageWithHeaderAndPayload":
                    var event = EventStreamTestClientTypes.MessageWithHeaderAndPayload()
                    if case .string(let value) = message.headers.value(name: "header") {
                        event.header = value
                    }
                    event.payload = message.payload
                    return .messagewithheaderandpayload(event)
                case "MessageWithNoHeaderPayloadTraits":
                    let value = try SmithyJSON.Reader.from(data: message.payload).readStructureNonNull(schema: schema__namespace_aws_protocoltests_restjson__name_MessageWithNoHeaderPayloadTraits)
                    return .messagewithnoheaderpayloadtraits(value)
                case "MessageWithUnboundPayloadTraits":
                    var event = EventStreamTestClientTypes.MessageWithUnboundPayloadTraits()
                    if case .string(let value) = message.headers.value(name: "header") {
                        event.header = value
                    }
                    let value = try SmithyJSON.Reader.from(data: message.payload).readStringNonNull(schema: SmithyReadWrite.stringSchema)
                    event.unboundString = value
                    return .messagewithunboundpayloadtraits(event)
                default:
                    return .sdkUnknown("error processing event stream, unrecognized event: \(params.eventType)")
                }
            case .exception(let params):
                let makeError: (SmithyEventStreamsAPI.Message, SmithyEventStreamsAPI.MessageType.ExceptionParams) throws -> Swift.Error = { message, params in
                    switch params.exceptionType {
                    case "SomeError":
                        let value = try SmithyJSON.Reader.from(data: message.payload).readStructureNonNull(schema: schema__namespace_aws_protocoltests_restjson__name_SomeError)
                        return value
                    default:
                        let httpResponse = SmithyHTTPAPI.HTTPResponse(body: .data(message.payload), statusCode: .ok)
                        return ClientRuntime.UnknownHTTPServiceError(httpResponse: httpResponse, message: "error processing event stream, unrecognized ':exceptionType': \(params.exceptionType); contentType: \(params.contentType ?? "nil")", requestID: nil, typeName: nil)
                    }
                }
                let error = try makeError(message, params)
                throw error
            case .error(let params):
                let httpResponse = SmithyHTTPAPI.HTTPResponse(body: .data(message.payload), statusCode: .ok)
                throw ClientRuntime.UnknownHTTPServiceError(httpResponse: httpResponse, message: "error processing event stream, unrecognized ':errorType': \(params.errorCode); message: \(params.message ?? "nil")", requestID: nil, typeName: nil)
            case .unknown(messageType: let messageType):
                throw Smithy.ClientError.unknownError("unrecognized event stream message ':message-type': \(messageType)")
            }
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `operation stack`() {
        val context = setupTests("eventstream.smithy", "aws.protocoltests.restjson#TestService")
        println(context.manifest.files)
        val contents = getFileContents(context.manifest, "Sources/Example/EventStreamTestClient.swift")
        var expected = """
    public func testStreamOp(input: TestStreamOpInput) async throws -> TestStreamOpOutput {
        let context = Smithy.ContextBuilder()
                      .withMethod(value: .post)
                      .withServiceName(value: serviceName)
                      .withOperation(value: "testStreamOp")
                      .withIdempotencyTokenGenerator(value: config.idempotencyTokenGenerator)
                      .withLogger(value: config.logger)
                      .withPartitionID(value: config.partitionID)
                      .withAuthSchemes(value: config.authSchemes ?? [])
                      .withAuthSchemeResolver(value: config.authSchemeResolver)
                      .withUnsignedPayloadTrait(value: false)
                      .withSocketTimeout(value: config.httpClientConfiguration.socketTimeout)
                      .withIdentityResolver(value: config.bearerTokenIdentityResolver, schemeID: "smithy.api#httpBearerAuth")
                      .build()
        let builder = ClientRuntime.OrchestratorBuilder<TestStreamOpInput, TestStreamOpOutput, SmithyHTTPAPI.HTTPRequest, SmithyHTTPAPI.HTTPResponse>()
        config.interceptorProviders.forEach { provider in
            builder.interceptors.add(provider.create())
        }
        config.httpInterceptorProviders.forEach { provider in
            builder.interceptors.add(provider.create())
        }
        builder.interceptors.add(ClientRuntime.URLPathMiddleware<TestStreamOpInput, TestStreamOpOutput>(TestStreamOpInput.urlPathProvider(_:)))
        builder.interceptors.add(ClientRuntime.URLHostMiddleware<TestStreamOpInput, TestStreamOpOutput>())
        builder.interceptors.add(ClientRuntime.ContentTypeMiddleware<TestStreamOpInput, TestStreamOpOutput>(contentType: "application/json"))
        builder.serialize(ClientRuntime.EventStreamBodyMiddleware<TestStreamOpInput, TestStreamOpOutput, EventStreamTestClientTypes.TestStream>(keyPath: \.value, defaultBody: "{}", marshalClosure: EventStreamTestClientTypes.TestStream.marshal))
        builder.interceptors.add(ClientRuntime.ContentLengthMiddleware<TestStreamOpInput, TestStreamOpOutput>())
        builder.deserialize(ClientRuntime.DeserializeMiddleware<TestStreamOpOutput>(TestStreamOpOutput.httpOutput(from:), TestStreamOpOutputError.httpError(from:)))
        builder.interceptors.add(ClientRuntime.LoggerMiddleware<TestStreamOpInput, TestStreamOpOutput>(clientLogMode: config.clientLogMode))
        builder.retryStrategy(SmithyRetries.DefaultRetryStrategy(options: config.retryStrategyOptions))
        builder.retryErrorInfoProvider(ClientRuntime.DefaultRetryErrorInfoProvider.errorInfo(for:))
        builder.applySigner(ClientRuntime.SignerMiddleware<TestStreamOpOutput>())
        builder.selectAuthScheme(ClientRuntime.AuthSchemeMiddleware<TestStreamOpOutput>())
        var metricsAttributes = Smithy.Attributes()
        metricsAttributes.set(key: ClientRuntime.OrchestratorMetricsAttributesKeys.service, value: "EventStreamTest")
        metricsAttributes.set(key: ClientRuntime.OrchestratorMetricsAttributesKeys.method, value: "TestStreamOp")
        let op = builder.attributes(context)
            .telemetry(ClientRuntime.OrchestratorTelemetry(
                telemetryProvider: config.telemetryProvider,
                metricsAttributes: metricsAttributes,
                meterScope: serviceName,
                tracerScope: serviceName
            ))
            .executeRequest(client)
            .build()
        return try await op.execute(input: input)
    }
"""
        contents.shouldContainOnlyOnce(expected)
    }

    private fun setupTests(smithyFile: String, serviceShapeId: String): TestContext {
        val context = TestContext.initContextFrom(
            listOf(smithyFile),
            serviceShapeId,
            MockHTTPRestJsonProtocolGenerator(),
            { model -> model.defaultSettings(serviceShapeId, "Example", "456", "EventStreamTest") },
            listOf(DefaultClientConfigurationIntegration())
        )
        context.generator.initializeMiddleware(context.generationCtx)
        context.generator.generateProtocolClient(context.generationCtx)
        context.generator.generateMessageMarshallable(context.generationCtx)
        context.generator.generateMessageUnmarshallable(context.generationCtx)
        context.generationCtx.delegator.flushWriters()
        return context
    }
}
