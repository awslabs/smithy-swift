// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime
import SmithyTestUtil
@testable import WeatherSDK
import XCTest


class GetCityRequestTest: HttpRequestTestBase {
    /// Does something
    func testWriteGetCityAssertions() async throws {
        let urlPrefix = urlPrefixFromHost(host: "")
        let hostOnly = hostOnlyFromHost(host: "")
        let expected = buildExpectedHttpRequest(
            method: .get,
            path: "/cities/123",
            body: nil,
            host: "",
            resolvedHost: ""
        )

        let decoder = ClientRuntime.JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        decoder.nonConformingFloatDecodingStrategy = .convertFromString(positiveInfinity: "Infinity", negativeInfinity: "-Infinity", nan: "NaN")

        let input = GetCityInput(
            cityId: "123"
        )
        let encoder = ClientRuntime.JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        encoder.nonConformingFloatEncodingStrategy = .convertToString(positiveInfinity: "Infinity", negativeInfinity: "-Infinity", nan: "NaN")
        let context = HttpContextBuilder()
                      .withEncoder(value: encoder)
                      .withMethod(value: .get)
                      .build()
        var operationStack = OperationStack<GetCityInput, GetCityOutput>(id: "WriteGetCityAssertions")
        operationStack.initializeStep.intercept(position: .after, middleware: ClientRuntime.URLPathMiddleware<GetCityInput, GetCityOutput>(urlPrefix: urlPrefix, GetCityInput.urlPathProvider(_:)))
        operationStack.initializeStep.intercept(position: .after, middleware: ClientRuntime.URLHostMiddleware<GetCityInput, GetCityOutput>(host: hostOnly))
        operationStack.buildStep.intercept(position: .before, middleware: ClientRuntime.ContentMD5Middleware<GetCityInput, GetCityOutput>())
        operationStack.buildStep.intercept(position: .after, id: "RequestTestEndpointResolver") { (context, input, next) -> ClientRuntime.OperationOutput<GetCityOutput> in
            input.withMethod(context.getMethod())
            input.withPath(context.getPath())
            let host = "\(context.getHostPrefix() ?? "")\(context.getHost() ?? "")"
            input.withHost(host)
            return try await next.handle(context: context, input: input)
        }
        operationStack.deserializeStep.intercept(
            position: .after,
            middleware: MockDeserializeMiddleware<GetCityOutput>(
                id: "TestDeserializeMiddleware",
                responseClosure: responseClosure(decoder: decoder),
                callback: { context, actual in
                    try await self.assertEqual(expected, actual)
                    return OperationOutput(httpResponse: HttpResponse(body: ByteStream.noStream, statusCode: .ok), output: GetCityOutput())
                }
            )
        )
        _ = try await operationStack.handleMiddleware(context: context, input: input, next: MockHandler() { (context, request) in
            XCTFail("Deserialize was mocked out, this should fail")
            throw SmithyTestUtilError("Mock handler unexpectedly failed")
        })
    }
}
