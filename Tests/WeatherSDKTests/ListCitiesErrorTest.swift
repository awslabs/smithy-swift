// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime
import SmithyTestUtil
@testable import WeatherSDK
import XCTest


class ListCitiesNoSuchResourceTest: HttpResponseTestBase {
    /// Does something
    func testWriteNoSuchResourceAssertions() async throws {
        do {
            guard let httpResponse = buildHttpResponse(
                code: 404,
                headers: nil,
                content: .data(Data("""
                {
                    "resourceType": "City",
                    "message": "Your custom message",
                    "errorType": "NoSuchResource"
                }
                """.utf8))
            ) else {
                XCTFail("Something is wrong with the created http response")
                return
            }

            let decoder = ClientRuntime.JSONDecoder()
            decoder.dateDecodingStrategy = .secondsSince1970
            decoder.nonConformingFloatDecodingStrategy = .convertFromString(positiveInfinity: "Infinity", negativeInfinity: "-Infinity", nan: "NaN")
            let listCitiesOutputError = try await responseErrorClosure(ListCitiesOutputError.self, decoder: decoder)(httpResponse)

            if let actual = listCitiesOutputError as? NoSuchResource {

                let expected = NoSuchResource(
                    message: "Your custom message",
                    resourceType: "City"
                )
                XCTAssertEqual(actual.httpResponse.statusCode, HttpStatusCode(rawValue: 404))
                XCTAssertEqual(expected.properties.resourceType, actual.properties.resourceType)
                XCTAssertEqual(expected.properties.message, actual.properties.message)
            } else {
                XCTFail("The deserialized error type does not match expected type")
            }

        } catch {
            XCTFail(error.localizedDescription)
        }
    }
}
