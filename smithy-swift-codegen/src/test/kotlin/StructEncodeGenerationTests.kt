/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.swift.codegen.AddOperationShapes
import software.amazon.smithy.swift.codegen.RecursiveShapeBoxer

class StructEncodeGenerationTests {
    var model = javaClass.getResource("http-binding-protocol-generator-test.smithy").asSmithy()
    private fun newTestContext(): TestContext {
        val settings = model.defaultSettings()
        model = AddOperationShapes.execute(model, settings.getService(model), settings.moduleName)
        model = RecursiveShapeBoxer.transform(model)
        return model.newTestContext()
    }
    val newTestContext = newTestContext()
    init {
        newTestContext.generator.generateSerializers(newTestContext.generationCtx)
        newTestContext.generator.generateCodableConformanceForNestedTypes(newTestContext.generationCtx)
        newTestContext.generationCtx.delegator.flushWriters()
    }

    @Test
    fun `it creates encodable conformance in correct file`() {
        Assertions.assertTrue(newTestContext.manifest.hasFile("/example/models/SmokeTestInput+Encodable.swift"))
    }

    @Test
    fun `it creates encodable conformance for nested structures`() {
        Assertions.assertTrue(newTestContext.manifest.hasFile("/example/models/Nested+Codable.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("/example/models/Nested2+Codable.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("/example/models/Nested3+Codable.swift"))
        Assertions.assertTrue(newTestContext.manifest.hasFile("/example/models/Nested4+Codable.swift"))
    }

    @Test
    fun `it creates smoke test request encodable conformance`() {
        val contents = getModelFileContents("example", "SmokeTestInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension SmokeTestInput: Encodable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case payload1
                    case payload2
                    case payload3
                }

                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let payload1 = payload1 {
                        try container.encode(payload1, forKey: .payload1)
                    }
                    if let payload2 = payload2 {
                        try container.encode(payload2, forKey: .payload2)
                    }
                    if let payload3 = payload3 {
                        try container.encode(payload3, forKey: .payload3)
                    }
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes nested documents with aggregate shapes`() {
        val contents = getModelFileContents("example", "Nested4+Codable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension Nested4: Codable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case intList
                    case intMap
                    case member1
                    case stringMap
                }
            
                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let intList = intList {
                        var intListContainer = container.nestedUnkeyedContainer(forKey: .intList)
                        for intlist0 in intList {
                            try intListContainer.encode(intlist0)
                        }
                    }
                    if let intMap = intMap {
                        var intMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .intMap)
                        for (dictKey0, intmap0) in intMap {
                            try intMapContainer.encode(intmap0, forKey: Key(stringValue: dictKey0))
                        }
                    }
                    if let member1 = member1 {
                        try container.encode(member1, forKey: .member1)
                    }
                    if let stringMap = stringMap {
                        var stringMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .stringMap)
                        for (dictKey0, nestedstringmap0) in stringMap {
                            try stringMapContainer.encode(nestedstringmap0, forKey: Key(stringValue: dictKey0))
                        }
                    }
                }
            
                public init (from decoder: Decoder) throws {
                    let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                    let member1Decoded = try containerValues.decodeIfPresent(Int.self, forKey: .member1)
                    member1 = member1Decoded
                    let intListContainer = try containerValues.decodeIfPresent([Int].self, forKey: .intList)
                    var intListDecoded0:[Int]? = nil
                    if let intListContainer = intListContainer {
                        intListDecoded0 = [Int]()
                        for integer0 in intListContainer {
                            intListDecoded0?.append(integer0)
                        }
                    }
                    intList = intListDecoded0
                    let intMapContainer = try containerValues.decodeIfPresent([String:Int].self, forKey: .intMap)
                    var intMapDecoded0: [String:Int]? = nil
                    if let intMapContainer = intMapContainer {
                        intMapDecoded0 = [String:Int]()
                        for (key0, integer0) in intMapContainer {
                            intMapDecoded0?[key0] = integer0
                        }
                    }
                    intMap = intMapDecoded0
                    let stringMapContainer = try containerValues.decodeIfPresent([String:[String]?].self, forKey: .stringMap)
                    var stringMapDecoded0: [String:[String]?]? = nil
                    if let stringMapContainer = stringMapContainer {
                        stringMapDecoded0 = [String:[String]?]()
                        for (key0, stringlist0) in stringMapContainer {
                            var stringlist0Decoded0 = [String]()
                            if let stringlist0 = stringlist0 {
                                for string1 in stringlist0 {
                                    stringlist0Decoded0.append(string1)
                                }
                            }
                            stringMapDecoded0?[key0] = stringlist0Decoded0
                        }
                    }
                    stringMap = stringMapDecoded0
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it provides encodable conformance to operation inputs with timestamps`() {
        val contents = getModelFileContents("example", "TimestampInputInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension TimestampInputInput: Encodable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case dateTime
                    case epochSeconds
                    case httpDate
                    case normal
                    case timestampList
                }

                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let dateTime = dateTime {
                        try container.encode(dateTime.iso8601WithoutFractionalSeconds(), forKey: .dateTime)
                    }
                    if let epochSeconds = epochSeconds {
                        try container.encode(epochSeconds.timeIntervalSince1970, forKey: .epochSeconds)
                    }
                    if let httpDate = httpDate {
                        try container.encode(httpDate.rfc5322(), forKey: .httpDate)
                    }
                    if let normal = normal {
                        try container.encode(normal.iso8601WithoutFractionalSeconds(), forKey: .normal)
                    }
                    if let timestampList = timestampList {
                        var timestampListContainer = container.nestedUnkeyedContainer(forKey: .timestampList)
                        for timestamplist0 in timestampList {
                            try timestampListContainer.encode(timestamplist0.iso8601WithoutFractionalSeconds())
                        }
                    }
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes maps correctly`() {
        val contents = getModelFileContents("example", "MapInputInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension MapInputInput: Encodable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case blobMap
                    case dateMap
                    case enumMap
                    case intMap
                    case structMap
                }

                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let blobMap = blobMap {
                        var blobMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .blobMap)
                        for (dictKey0, blobmap0) in blobMap {
                            try blobMapContainer.encode(blobmap0.base64EncodedString(), forKey: Key(stringValue: dictKey0))
                        }
                    }
                    if let dateMap = dateMap {
                        var dateMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .dateMap)
                        for (dictKey0, datemap0) in dateMap {
                            try dateMapContainer.encode(datemap0.rfc5322(), forKey: Key(stringValue: dictKey0))
                        }
                    }
                    if let enumMap = enumMap {
                        var enumMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .enumMap)
                        for (dictKey0, enummap0) in enumMap {
                            try enumMapContainer.encode(enummap0.rawValue, forKey: Key(stringValue: dictKey0))
                        }
                    }
                    if let intMap = intMap {
                        var intMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .intMap)
                        for (dictKey0, intmap0) in intMap {
                            try intMapContainer.encode(intmap0, forKey: Key(stringValue: dictKey0))
                        }
                    }
                    if let structMap = structMap {
                        var structMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .structMap)
                        for (dictKey0, structmap0) in structMap {
                            try structMapContainer.encode(structmap0, forKey: Key(stringValue: dictKey0))
                        }
                    }
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes nested enums correctly`() {
        val contents = getModelFileContents("example", "EnumInputInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension EnumInputInput: Encodable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case nestedWithEnum
                }

                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let nestedWithEnum = nestedWithEnum {
                        try container.encode(nestedWithEnum, forKey: .nestedWithEnum)
                    }
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)

        val contents2 = getModelFileContents("example", "NestedEnum+Codable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents2 =
            """
            extension NestedEnum: Codable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case myEnum
                }
            
                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let myEnum = myEnum {
                        try container.encode(myEnum.rawValue, forKey: .myEnum)
                    }
                }
            
                public init (from decoder: Decoder) throws {
                    let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                    let myEnumDecoded = try containerValues.decodeIfPresent(MyEnum.self, forKey: .myEnum)
                    myEnum = myEnumDecoded
                }
            }
            """.trimIndent()
        contents2.shouldContainOnlyOnce(expectedContents2)
    }

    @Test
    fun `it encodes recursive boxed types correctly`() {
        val contents = getModelFileContents(
            "example",
            "RecursiveShapesInputOutputNested1+Codable.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension RecursiveShapesInputOutputNested1: Codable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case foo
                    case nested
                }
            
                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let foo = foo {
                        try container.encode(foo, forKey: .foo)
                    }
                    if let nested = nested {
                        try container.encode(nested.value, forKey: .nested)
                    }
                }
            
                public init (from decoder: Decoder) throws {
                    let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                    let fooDecoded = try containerValues.decodeIfPresent(String.self, forKey: .foo)
                    foo = fooDecoded
                    let nestedDecoded = try containerValues.decodeIfPresent(Box<RecursiveShapesInputOutputNested2>.self, forKey: .nested)
                    nested = nestedDecoded
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes one side of the recursive shape`() {
        val contents = getModelFileContents(
            "example",
            "RecursiveShapesInputOutputNested2+Codable.swift",
            newTestContext.manifest
        )
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
            extension RecursiveShapesInputOutputNested2: Codable, Reflection {
                private enum CodingKeys: String, CodingKey {
                    case bar
                    case recursiveMember
                }
            
                public func encode(to encoder: Encoder) throws {
                    var container = encoder.container(keyedBy: CodingKeys.self)
                    if let bar = bar {
                        try container.encode(bar, forKey: .bar)
                    }
                    if let recursiveMember = recursiveMember {
                        try container.encode(recursiveMember, forKey: .recursiveMember)
                    }
                }
            
                public init (from decoder: Decoder) throws {
                    let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                    let barDecoded = try containerValues.decodeIfPresent(String.self, forKey: .bar)
                    bar = barDecoded
                    let recursiveMemberDecoded = try containerValues.decodeIfPresent(RecursiveShapesInputOutputNested1.self, forKey: .recursiveMember)
                    recursiveMember = recursiveMemberDecoded
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes structure with sparse list`() {
        val contents = getModelFileContents("example", "JsonListsInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
extension JsonListsInput: Encodable, Reflection {
    private enum CodingKeys: String, CodingKey {
        case booleanList
        case integerList
        case nestedStringList
        case sparseStringList
        case stringList
        case stringSet
        case timestampList
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        if let booleanList = booleanList {
            var booleanListContainer = container.nestedUnkeyedContainer(forKey: .booleanList)
            for booleanlist0 in booleanList {
                try booleanListContainer.encode(booleanlist0)
            }
        }
        if let integerList = integerList {
            var integerListContainer = container.nestedUnkeyedContainer(forKey: .integerList)
            for integerlist0 in integerList {
                try integerListContainer.encode(integerlist0)
            }
        }
        if let nestedStringList = nestedStringList {
            var nestedStringListContainer = container.nestedUnkeyedContainer(forKey: .nestedStringList)
            for nestedstringlist0 in nestedStringList {
                var nestedstringlist0Container = nestedStringListContainer.nestedUnkeyedContainer()
                if let nestedstringlist0 = nestedstringlist0 {
                    for stringlist1 in nestedstringlist0 {
                        try nestedstringlist0Container.encode(stringlist1)
                    }
                }
            }
        }
        if let sparseStringList = sparseStringList {
            var sparseStringListContainer = container.nestedUnkeyedContainer(forKey: .sparseStringList)
            for sparsestringlist0 in sparseStringList {
                try sparseStringListContainer.encode(sparsestringlist0)
            }
        }
        if let stringList = stringList {
            var stringListContainer = container.nestedUnkeyedContainer(forKey: .stringList)
            for stringlist0 in stringList {
                try stringListContainer.encode(stringlist0)
            }
        }
        if let stringSet = stringSet {
            var stringSetContainer = container.nestedUnkeyedContainer(forKey: .stringSet)
            for stringset0 in stringSet {
                try stringSetContainer.encode(stringset0)
            }
        }
        if let timestampList = timestampList {
            var timestampListContainer = container.nestedUnkeyedContainer(forKey: .timestampList)
            for timestamplist0 in timestampList {
                try timestampListContainer.encode(timestamplist0.iso8601WithoutFractionalSeconds())
            }
        }
    }
}
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it encodes structure with sparse map`() {
        val contents = getModelFileContents("example", "JsonMapsInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
extension JsonMapsInput: Encodable, Reflection {
    private enum CodingKeys: String, CodingKey {
        case denseBooleanMap
        case denseNumberMap
        case denseStringMap
        case denseStructMap
        case sparseBooleanMap
        case sparseNumberMap
        case sparseStringMap
        case sparseStructMap
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        if let denseBooleanMap = denseBooleanMap {
            var denseBooleanMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .denseBooleanMap)
            for (dictKey0, densebooleanmap0) in denseBooleanMap {
                try denseBooleanMapContainer.encode(densebooleanmap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let denseNumberMap = denseNumberMap {
            var denseNumberMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .denseNumberMap)
            for (dictKey0, densenumbermap0) in denseNumberMap {
                try denseNumberMapContainer.encode(densenumbermap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let denseStringMap = denseStringMap {
            var denseStringMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .denseStringMap)
            for (dictKey0, densestringmap0) in denseStringMap {
                try denseStringMapContainer.encode(densestringmap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let denseStructMap = denseStructMap {
            var denseStructMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .denseStructMap)
            for (dictKey0, densestructmap0) in denseStructMap {
                try denseStructMapContainer.encode(densestructmap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let sparseBooleanMap = sparseBooleanMap {
            var sparseBooleanMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .sparseBooleanMap)
            for (dictKey0, sparsebooleanmap0) in sparseBooleanMap {
                try sparseBooleanMapContainer.encode(sparsebooleanmap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let sparseNumberMap = sparseNumberMap {
            var sparseNumberMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .sparseNumberMap)
            for (dictKey0, sparsenumbermap0) in sparseNumberMap {
                try sparseNumberMapContainer.encode(sparsenumbermap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let sparseStringMap = sparseStringMap {
            var sparseStringMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .sparseStringMap)
            for (dictKey0, sparsestringmap0) in sparseStringMap {
                try sparseStringMapContainer.encode(sparsestringmap0, forKey: Key(stringValue: dictKey0))
            }
        }
        if let sparseStructMap = sparseStructMap {
            var sparseStructMapContainer = container.nestedContainer(keyedBy: Key.self, forKey: .sparseStructMap)
            for (dictKey0, sparsestructmap0) in sparseStructMap {
                try sparseStructMapContainer.encode(sparsestructmap0, forKey: Key(stringValue: dictKey0))
            }
        }
    }
}
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `encode checks for 0 or false for primitive types`() {
        val contents = getModelFileContents("example", "PrimitiveTypesInput+Encodable.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()
        val expectedContents =
            """
extension PrimitiveTypesInput: Encodable, Reflection {
    private enum CodingKeys: String, CodingKey {
        case booleanVal
        case byteVal
        case doubleVal
        case floatVal
        case intVal
        case longVal
        case primitiveBooleanVal
        case primitiveByteVal
        case primitiveDoubleVal
        case primitiveFloatVal
        case primitiveIntVal
        case primitiveLongVal
        case primitiveShortVal
        case shortVal
        case str
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        if let booleanVal = booleanVal {
            try container.encode(booleanVal, forKey: .booleanVal)
        }
        if let byteVal = byteVal {
            try container.encode(byteVal, forKey: .byteVal)
        }
        if let doubleVal = doubleVal {
            try container.encode(doubleVal, forKey: .doubleVal)
        }
        if let floatVal = floatVal {
            try container.encode(floatVal, forKey: .floatVal)
        }
        if let intVal = intVal {
            try container.encode(intVal, forKey: .intVal)
        }
        if let longVal = longVal {
            try container.encode(longVal, forKey: .longVal)
        }
        if primitiveBooleanVal != false {
            try container.encode(primitiveBooleanVal, forKey: .primitiveBooleanVal)
        }
        if primitiveByteVal != 0 {
            try container.encode(primitiveByteVal, forKey: .primitiveByteVal)
        }
        if primitiveDoubleVal != 0.0 {
            try container.encode(primitiveDoubleVal, forKey: .primitiveDoubleVal)
        }
        if primitiveFloatVal != 0.0 {
            try container.encode(primitiveFloatVal, forKey: .primitiveFloatVal)
        }
        if primitiveIntVal != 0 {
            try container.encode(primitiveIntVal, forKey: .primitiveIntVal)
        }
        if primitiveLongVal != 0 {
            try container.encode(primitiveLongVal, forKey: .primitiveLongVal)
        }
        if primitiveShortVal != 0 {
            try container.encode(primitiveShortVal, forKey: .primitiveShortVal)
        }
        if let shortVal = shortVal {
            try container.encode(shortVal, forKey: .shortVal)
        }
        if let str = str {
            try container.encode(str, forKey: .str)
        }
    }
}
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }
}
