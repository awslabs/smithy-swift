// Code generated by smithy-swift-codegen. DO NOT EDIT!

import ClientRuntime
import SmithyJSON
import SmithyReadWrite

extension WeatherClientTypes.Precipitation {

    static func read(from reader: SmithyJSON.Reader) throws -> WeatherClientTypes.Precipitation {
        guard let nodeInfo = reader.children.first(where: { $0.hasContent && $0.nodeInfo != "__type" })?.nodeInfo else {
            throw SmithyReadWrite.ReaderError.requiredValueNotPresent
        }
        let name = "\(nodeInfo)"
        switch name {
            case "rain":
                return .rain(try reader["rain"].read() ?? false)
            case "sleet":
                return .sleet(try reader["sleet"].read() ?? false)
            case "hail":
                return .hail(try reader["hail"].readMap(valueReadingClosure: Swift.String.read(from:), keyNodeInfo: "key", valueNodeInfo: "value", isFlattened: false))
            case "snow":
                return .snow(try reader["snow"].read())
            case "mixed":
                return .mixed(try reader["mixed"].read())
            case "other":
                return .other(try reader["other"].read(with: WeatherClientTypes.OtherStructure.read(from:)))
            case "blob":
                return .blob(try reader["blob"].read())
            case "foo":
                return .foo(try reader["foo"].read(with: WeatherClientTypes.Foo.read(from:)))
            case "baz":
                return .baz(try reader["baz"].read(with: WeatherClientTypes.Baz.read(from:)))
            default:
                return .sdkUnknown(name)
        }
    }
}
