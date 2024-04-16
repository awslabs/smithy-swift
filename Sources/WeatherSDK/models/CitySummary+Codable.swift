// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime

extension WeatherClientTypes.CitySummary: Swift.Codable {
    enum CodingKeys: Swift.String, Swift.CodingKey {
        case `case` = "case"
        case cityId
        case name
        case number
    }

    public func encode(to encoder: Swift.Encoder) throws {
        var encodeContainer = encoder.container(keyedBy: CodingKeys.self)
        if let `case` = self.`case` {
            try encodeContainer.encode(`case`, forKey: .`case`)
        }
        if let cityId = self.cityId {
            try encodeContainer.encode(cityId, forKey: .cityId)
        }
        if let name = self.name {
            try encodeContainer.encode(name, forKey: .name)
        }
        if let number = self.number {
            try encodeContainer.encode(number, forKey: .number)
        }
    }

    public init(from decoder: Swift.Decoder) throws {
        let containerValues = try decoder.container(keyedBy: CodingKeys.self)
        let cityIdDecoded = try containerValues.decodeIfPresent(Swift.String.self, forKey: .cityId)
        cityId = cityIdDecoded
        let nameDecoded = try containerValues.decodeIfPresent(Swift.String.self, forKey: .name)
        name = nameDecoded
        let numberDecoded = try containerValues.decodeIfPresent(Swift.String.self, forKey: .number)
        number = numberDecoded
        let caseDecoded = try containerValues.decodeIfPresent(Swift.String.self, forKey: .case)
        `case` = caseDecoded
    }
}
