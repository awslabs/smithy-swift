//
// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License").
// You may not use this file except in compliance with the License.
// A copy of the License is located at
//
// http://aws.amazon.com/apache2.0
//
// or in the "license" file accompanying this file. This file is distributed
// on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied. See the License for the specific language governing
// permissions and limitations under the License.
//

import XCTest
@testable import ClientRuntime
import AwsCommonRuntimeKit

class CRTClientEngineIntegrationTests: NetworkingTestUtils {
    
    var httpClient: SdkHttpClient!
    
    override func setUp() {
        super.setUp()
        let httpClientConfiguration = HttpClientConfiguration()
        let crtEngine = try! CRTClientEngine()
        httpClient = try! SdkHttpClient(engine: crtEngine, config: httpClientConfiguration)
    }
    
    override func tearDown() {
        super.tearDown()
    }
    
    func testMakeHttpGetRequest() {
        let expectation = XCTestExpectation(description: "Request has been completed")
        var headers = Headers()
        headers.add(name: "Content-type", value: "application/json")
        let request = SdkHttpRequest(method: .get, endpoint: Endpoint(host: "httpbin.org", path: "/get"), headers: headers)
        httpClient.execute(request: request) { result in
            switch result {
            case .success(let response):
                XCTAssertNotNil(response)
                XCTAssert(response.statusCode == HttpStatusCode.ok)
                expectation.fulfill()
            case .failure(let error):
                print(error)
                XCTFail(error.localizedDescription)
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 10.0)
    }
    
    func testMakeHttpPostRequest() {
        //used https://httpbin.org
        let expectation = XCTestExpectation(description: "Request has been completed")
        var headers = Headers()
        headers.add(name: "Content-type", value: "application/json")
        let body = TestBody(test: "testval")
        let encoder = JSONEncoder()
        let encodedData = try! encoder.encode(body)
        let request = SdkHttpRequest(method: .post,
                                     endpoint: Endpoint(host: "httpbin.org", path: "/post"),
                                     headers: headers,
                                     body: HttpBody.data(encodedData))
        httpClient.execute(request: request) { result in
            switch result {
            case .success(let response):
                XCTAssertNotNil(response)
                XCTAssert(response.statusCode == HttpStatusCode.ok)
                expectation.fulfill()
            case .failure(let error):
                print(error)
                XCTFail(error.localizedDescription)
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 20.0)
    }
    
    func testMakeHttpStreamRequestDynamicReceive() {
        //used https://httpbin.org
        let expectation = XCTestExpectation(description: "Request has been completed")
        let dataReceivedExpectation = XCTestExpectation(description: "Data was received")
        var headers = Headers()
        headers.add(name: "Content-type", value: "application/json")
        let stream = MockSinkStream(testExpectation: dataReceivedExpectation)
        let request = SdkHttpRequest(method: .get,
                                     endpoint: Endpoint(host: "httpbin.org", path: "/stream-bytes/1024"),
                                     headers: headers,
                                     body: HttpBody.streamSink(stream))
        httpClient.execute(request: request) { result in
            switch result {
            case .success(let response):
                XCTAssertNotNil(response)
                XCTAssert(response.statusCode == HttpStatusCode.ok)
                expectation.fulfill()
            case .failure(let error):
                print(error)
                XCTFail(error.localizedDescription)
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation, dataReceivedExpectation], timeout: 20.0)
    }
    
    func testMakeHttpStreamRequestFromData() {
        //used https://httpbin.org
        let expectation = XCTestExpectation(description: "Request has been completed")
        var headers = Headers()
        headers.add(name: "Content-type", value: "application/json")
        let body = TestBody(test: "testval")
        let encoder = JSONEncoder()
        let encodedData = try! encoder.encode(body)
        let stream = fromData(data: encodedData)
        let request = SdkHttpRequest(method: .post,
                                     endpoint: Endpoint(host: "httpbin.org", path: "/post"),
                                     headers: headers,
                                     body: HttpBody.streamSource(stream))
        httpClient.execute(request: request) { result in
            switch result {
            case .success(let response):
                XCTAssertNotNil(response)
                XCTAssert(response.statusCode == HttpStatusCode.ok)
                expectation.fulfill()
            case .failure(let error):
                print(error)
                XCTFail(error.localizedDescription)
                expectation.fulfill()
            }
        }
        
        wait(for: [expectation], timeout: 20.0)
    }
}

struct MockSinkStream: StreamSink {
    var receivedData: Data?
    var error: StreamError?
    let testExpectation: XCTestExpectation
    
    public init(testExpectation: XCTestExpectation) {
        self.testExpectation = testExpectation
    }
    mutating func receiveData(readFrom buffer: ByteBuffer) {
        receivedData?.append(buffer.toData())
        testExpectation.fulfill()
    }
    
    mutating func onError(error: StreamError) {
        self.error = error
    }
    
    
}

struct TestBody: Encodable {
    let test: String
}
