/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import Foundation
extension URL {
    func toQueryItems() -> [URLQueryItem]? { return URLComponents(url: self,
                                                                  resolvingAgainstBaseURL: false)?.queryItems }
}
