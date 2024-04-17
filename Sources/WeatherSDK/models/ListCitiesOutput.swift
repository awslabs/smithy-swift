// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime

public struct ListCitiesOutput: Swift.Equatable {
    /// This member is required.
    public var items: [WeatherClientTypes.CitySummary]?
    public var nextToken: Swift.String?

    public init(
        items: [WeatherClientTypes.CitySummary]? = nil,
        nextToken: Swift.String? = nil
    )
    {
        self.items = items
        self.nextToken = nextToken
    }
}
