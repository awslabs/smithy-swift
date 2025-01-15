//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import XCTest
import struct SmithyIdentity.StaticAWSCredentialIdentityResolver

class StaticAWSCredentialIdentityResolverTests: XCTestCase {

    func testGetCredentials() async throws {
        let subject = try StaticAWSCredentialIdentityResolver(.init(
            accessKey: "static_access_key",
            secret: "static_secret",
            accountID: "static_account_id"
        ))
        let credentials = try await subject.getIdentity()
        
        XCTAssertEqual(credentials.accessKey, "static_access_key")
        XCTAssertEqual(credentials.secret, "static_secret")
        XCTAssertEqual(credentials.accountID, "static_account_id")
    }
}
