//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import class Smithy.Context
import struct SmithyHTTPAPI.Endpoint
import class SmithyHTTPAPI.HTTPRequest
import class SmithyHTTPAPI.HTTPRequestBuilder
import struct SmithyHTTPAuthAPI.SelectedAuthScheme
import enum SmithyHTTPAuthAPI.SigningAlgorithm
import enum SmithyHTTPAuthAPI.SigningPropertyKeys

public struct EndpointResolverMiddleware<OperationStackOutput, Params: EndpointsRequestContextProviding> {
    public let id: Swift.String = "EndpointResolverMiddleware"

    let endpointResolverBlock: (Params) throws -> Endpoint

    let endpointParams: Params

    let authSchemeResolver: EndpointsAuthSchemeResolver

    public init(
        endpointResolverBlock: @escaping (Params) throws -> Endpoint,
        endpointParams: Params,
        authSchemeResolver: EndpointsAuthSchemeResolver = DefaultEndpointsAuthSchemeResolver()
    ) {
        self.endpointResolverBlock = endpointResolverBlock
        self.endpointParams = endpointParams
        self.authSchemeResolver = authSchemeResolver
    }
}

extension EndpointResolverMiddleware: ApplyEndpoint {

    public func apply(
        request: HTTPRequest,
        selectedAuthScheme: SelectedAuthScheme?,
        attributes: Smithy.Context
    ) async throws -> HTTPRequest {
        let builder = request.toBuilder()

        let endpoint = try endpointResolverBlock(endpointParams)

        var signingName: String?
        var signingAlgorithm: String?
        if let authSchemes = endpoint.authSchemes() {
            let schemes = try authSchemes.map { try EndpointsAuthScheme(from: $0) }
            let authScheme = try authSchemeResolver.resolve(authSchemes: schemes)
            signingAlgorithm = authScheme.name
            switch authScheme {
            case .sigV4(let param):
                signingName = param.signingName
            case .sigV4A(let param):
                signingName = param.signingName
            case .none:
                break
            }
        }

        let awsEndpoint = SmithyEndpoint(endpoint: endpoint, signingName: signingName)

        var host = ""
        if let hostOverride = attributes.host {
            host = hostOverride
        } else {
            host = "\(attributes.hostPrefix ?? "")\(awsEndpoint.endpoint.host)"
        }

        if let protocolType = awsEndpoint.endpoint.protocolType {
            builder.withProtocol(protocolType)
        }

        if let signingName = signingName {
            attributes.signingName = signingName
            attributes.selectedAuthScheme = selectedAuthScheme?.getCopyWithUpdatedSigningProperty(
                key: SigningPropertyKeys.signingName, value: signingName
            )
        }

        if let signingAlgorithm = signingAlgorithm {
            attributes.signingAlgorithm = SigningAlgorithm(rawValue: signingAlgorithm)
        }

        return builder.withMethod(attributes.method)
            .withHost(host)
            .withPort(awsEndpoint.endpoint.port)
            .withPath(awsEndpoint.endpoint.path.appendingPathComponent(attributes.path))
            .withHeaders(endpoint.headers)
            .withHeader(name: "Host", value: host)
            .build()
    }
}
