//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import protocol SmithyIdentity.AWSCredentialIdentityResolver
import struct Smithy.Attributes
import struct SmithyIdentity.AWSCredentialIdentity

struct MockAWSCredentialIdentityResolver: AWSCredentialIdentityResolver {
    let _getIdentity: () async throws -> AWSCredentialIdentity

    init(_ _getIdentity: @escaping () async throws -> AWSCredentialIdentity) {
        self._getIdentity = _getIdentity
    }

    init() {
        self._getIdentity = {
            AWSCredentialIdentity(
                accessKey: "mock_access_key",
                secret: "mock_secret",
                accountID: "mock_account_id"
            )
        }
    }
    func getIdentity(identityProperties: Attributes?) async throws -> AWSCredentialIdentity {
        try await _getIdentity()
    }
}
