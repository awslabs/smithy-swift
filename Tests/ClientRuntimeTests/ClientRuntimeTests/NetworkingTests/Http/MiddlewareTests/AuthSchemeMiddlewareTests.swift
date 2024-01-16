//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import XCTest
import SmithyTestUtil
@testable import ClientRuntime

class AuthSchemeMiddlewareTests: XCTestCase {
    private var contextBuilder: HttpContextBuilder!
    private var operationStack: OperationStack<MockInput, MockOutput>!

    override func setUp() async throws {
        try await super.setUp()
        contextBuilder = HttpContextBuilder()
            .withAuthSchemeResolver(value: DefaultMockAuthSchemeResolver())
            .withAuthScheme(value: MockNoAuth())
            .withIdentityResolver(value: MockIdentityResolver(), type: .aws)
        operationStack = OperationStack<MockInput, MockOutput>(id: "auth scheme middleware test stack")
    }

    // Test exception cases
    func testNoAuthSchemeResolverConfigured() async throws {
        contextBuilder.attributes.remove(key: AttributeKeys.authSchemeResolver)
        contextBuilder.withOperation(value: "fillerOp")
        do {
            try await AssertSelectedAuthSchemeMatches(builtContext: contextBuilder.build(), expectedAuthScheme: "")
        } catch ClientError.authError(let message) {
            XCTAssertEqual(message, "No auth scheme resolver has been configured on the service.")
        } catch {
            XCTFail("Expected exception was not thrown.")
        }
    }

    func testNoIdentityResolverConfigured() async throws {
        contextBuilder.attributes.remove(key: AttributeKeys.identityResolvers)
        contextBuilder.withOperation(value: "fillerOp")
        do {
            try await AssertSelectedAuthSchemeMatches(builtContext: contextBuilder.build(), expectedAuthScheme: "")
        } catch ClientError.authError(let message) {
            XCTAssertEqual(message, "No identity resolver has been configured on the service.")
        } catch let error {
            print(error)
            XCTFail("Expected exception was not thrown.")
        }
    }

    func testNoAuthSchemeCouldBeLoaded() async throws {
        contextBuilder.withOperation(value: "fillerOp")
        do {
            try await AssertSelectedAuthSchemeMatches(builtContext: contextBuilder.build(), expectedAuthScheme: "")
        } catch ClientError.authError(let message) {
            XCTAssertEqual(message, "Could not resolve auth scheme for the operation call. Log: Auth scheme fillerAuth was not enabled for this request.")
        } catch {
            XCTFail("Expected exception was not thrown.")
        }
    }

    // Test success cases
    func testOnlyAuthSchemeA() async throws {
        let context = contextBuilder
            .withOperation(value: "authA")
            .withAuthScheme(value: MockAuthSchemeA())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "MockAuthSchemeA")
    }

    func testAuthOrderABSelectA() async throws {
        let context = contextBuilder
            .withOperation(value: "authAB")
            .withAuthScheme(value: MockAuthSchemeA())
            .withAuthScheme(value: MockAuthSchemeB())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "MockAuthSchemeA")
    }

    func testAuthOrderABSelectB() async throws {
        let context = contextBuilder
            .withOperation(value: "authAB")
            .withAuthScheme(value: MockAuthSchemeB())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "MockAuthSchemeB")
    }

    func testAuthOrderABCSelectA() async throws {
        let context = contextBuilder
            .withOperation(value: "authABC")
            .withAuthScheme(value: MockAuthSchemeA())
            .withAuthScheme(value: MockAuthSchemeB())
            .withAuthScheme(value: MockAuthSchemeC())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "MockAuthSchemeA")
    }

    func testAuthOrderABCSelectB() async throws {
        let context = contextBuilder
            .withOperation(value: "authABC")
            .withAuthScheme(value: MockAuthSchemeB())
            .withAuthScheme(value: MockAuthSchemeC())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "MockAuthSchemeB")
    }

    func testAuthOrderABCSelectC() async throws {
        let context = contextBuilder
            .withOperation(value: "authABC")
            .withAuthScheme(value: MockAuthSchemeC())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "MockAuthSchemeC")
    }

    func testAuthOderABCNoAuthSelectNoAuth() async throws {
        let context = contextBuilder
            .withOperation(value: "authABCNoAuth")
            .withAuthScheme(value: MockNoAuth())
            .build()
        try await AssertSelectedAuthSchemeMatches(builtContext: context, expectedAuthScheme: "smithy.api#noAuth")
    }

    private func AssertSelectedAuthSchemeMatches(builtContext: HttpContext, expectedAuthScheme: String) async throws {
        operationStack.buildStep.intercept(position: .before, middleware: AuthSchemeMiddleware<MockOutput, MockMiddlewareError>())

        let mockHandler = MockHandler(handleCallback: { (context, input) in
            let selectedAuthScheme = context.getSelectedAuthScheme()
            XCTAssertEqual(expectedAuthScheme, selectedAuthScheme?.schemeID)
            let httpResponse = HttpResponse(body: .noStream, statusCode: HttpStatusCode.ok)
            let mockOutput = try! MockOutput(httpResponse: httpResponse, decoder: nil)
            let output = OperationOutput<MockOutput>(httpResponse: httpResponse, output: mockOutput)
            return output
        })

        _ = try await operationStack.handleMiddleware(context: builtContext, input: MockInput(), next: mockHandler)
    }
}
