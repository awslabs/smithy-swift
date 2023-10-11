//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import Foundation

protocol IdentityResolverConfiguration {
    func getIdentityResolver(identityType: IdentityKind) -> (any IdentityResolver)?
}
