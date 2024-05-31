// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime
import Smithy
import SmithyHTTPAPI
import SmithyHTTPAuthAPI
import protocol ClientRuntime.EndpointsAuthSchemeResolver
import struct ClientRuntime.DefaultEndpointsAuthSchemeResolver

public struct EndpointParams {
    /// docs
    public let region: Swift.String

    public init(
        region: Swift.String
    )
    {
        self.region = region
    }
}

public protocol EndpointResolver {
    func resolve(params: EndpointParams) throws -> SmithyHTTPAPI.Endpoint
}

public struct DefaultEndpointResolver: EndpointResolver  {

    private let engine: ClientRuntime.EndpointsRuleEngine
    private let ruleSet = "{\"version\":\"1.3\",\"parameters\":{\"Region\":{\"required\":true,\"documentation\":\"docs\",\"type\":\"String\"}},\"rules\":[{\"conditions\":[],\"documentation\":\"base rule\",\"endpoint\":{\"url\":\"https://{Region}.amazonaws.com\",\"properties\":{},\"headers\":{}},\"type\":\"endpoint\"}]}"

    public init() throws {
        engine = try ClientRuntime.EndpointsRuleEngine(partitions: ClientRuntime.partitionJSON, ruleSet: ruleSet)
    }

    public func resolve(params: EndpointParams) throws -> SmithyHTTPAPI.Endpoint {
        let context = try ClientRuntime.EndpointsRequestContext()
        try context.add(name: "Region", value: params.region)

        guard let crtResolvedEndpoint = try engine.resolve(context: context) else {
            throw EndpointError.unresolved("Failed to resolved endpoint")
        }

        if crtResolvedEndpoint.getType() == .error {
            let error = crtResolvedEndpoint.getError()
            throw EndpointError.unresolved(error)
        }

        guard let url = crtResolvedEndpoint.getURL() else {
            assertionFailure("This must be a bug in either CRT or the rule engine, if the endpoint is not an error, it must have a url")
            throw EndpointError.unresolved("Failed to resolved endpoint")
        }

        let headers = crtResolvedEndpoint.getHeaders() ?? [:]
        let properties = crtResolvedEndpoint.getProperties() ?? [:]
        return try Endpoint(urlString: url, headers: Headers(headers), properties: properties)
    }
}

public struct EndpointResolverMiddleware<OperationStackOutput>: ClientRuntime.Middleware {
    public let id: Swift.String = "EndpointResolverMiddleware"

    let endpointResolver: EndpointResolver

    let endpointParams: EndpointParams

    let authSchemeResolver: ClientRuntime.EndpointsAuthSchemeResolver

    public init(endpointResolver: EndpointResolver, endpointParams: EndpointParams, authSchemeResolver: ClientRuntime.EndpointsAuthSchemeResolver = ClientRuntime.DefaultEndpointsAuthSchemeResolver()) {
        self.endpointResolver = endpointResolver
        self.endpointParams = endpointParams
        self.authSchemeResolver = authSchemeResolver
    }

    public func handle<H>(context: Smithy.Context,
                  input: SmithyHTTPAPI.SdkHttpRequestBuilder,
                  next: H) async throws -> ClientRuntime.OperationOutput<OperationStackOutput>
    where H: Handler,
    Self.MInput == H.Input,
    Self.MOutput == H.Output
    {
        let selectedAuthScheme = context.selectedAuthScheme
        let request = input.build()
        let updatedRequest = try await apply(request: request, selectedAuthScheme: selectedAuthScheme, attributes: context)
        return try await next.handle(context: context, input: updatedRequest.toBuilder())
    }

    public typealias MInput = SmithyHTTPAPI.SdkHttpRequestBuilder
    public typealias MOutput = ClientRuntime.OperationOutput<OperationStackOutput>
}
extension EndpointResolverMiddleware: ApplyEndpoint {
    public func apply(
        request: SdkHttpRequest,
        selectedAuthScheme: SelectedAuthScheme?,
        attributes: Smithy.Context) async throws -> SdkHttpRequest
    {
        let builder = request.toBuilder()

        let endpoint = try endpointResolver.resolve(params: endpointParams)

        var signingName: String? = nil
        var signingAlgorithm: String? = nil
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

        let smithyEndpoint = SmithyEndpoint(endpoint: endpoint, signingName: signingName)

        var host = ""
        if let hostOverride = attributes.host {
            host = hostOverride
        } else {
            host = "\(attributes.hostPrefix ?? "")\(smithyEndpoint.endpoint.host)"
        }

        if let protocolType = smithyEndpoint.endpoint.protocolType {
            builder.withProtocol(protocolType)
        }

        if let signingName = signingName {
           attributes.signingName = signingName
           attributes.selectedAuthScheme = selectedAuthScheme?.getCopyWithUpdatedSigningProperty(key: SigningPropertyKeys.signingName, value: signingName)
        }

        if let signingAlgorithm = signingAlgorithm {
            attributes.signingAlgorithm = signingAlgorithm
        }

        if !endpoint.headers.isEmpty {
            builder.withHeaders(endpoint.headers)
        }

        return builder.withMethod(attributes.method)
            .withHost(host)
            .withPort(smithyEndpoint.endpoint.port)
            .withPath(smithyEndpoint.endpoint.path.appendingPathComponent(attributes.path))
            .withHeader(name: "Host", value: host)
            .build()
    }
}
