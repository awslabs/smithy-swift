// Code generated by smithy-swift-codegen. DO NOT EDIT!

import SmithyTestUtil
import WeatherSDK

extension ListCitiesOutput: Swift.Equatable {

    public static func ==(lhs: ListCitiesOutput, rhs: ListCitiesOutput) -> Bool {
        if lhs.nextToken != rhs.nextToken { return false }
        if lhs.items != rhs.items { return false }
        return true
    }
}