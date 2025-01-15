//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import XCTest
import struct SmithyIdentity.CustomAWSCredentialIdentityResolver

class CustomAWSCredentialIdentityResolverTests: XCTestCase {

    func testGetCredentials() async throws {
        let mockProvider = MockAWSCredentialIdentityResolver()
        let subject = try CustomAWSCredentialIdentityResolver(mockProvider)
        let credentials = try await subject.getIdentity()
        
        XCTAssertEqual(credentials.accessKey, "mock_access_key")
        XCTAssertEqual(credentials.secret, "mock_secret")
    }
}
