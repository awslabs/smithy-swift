package serde.xml

import MockHttpRestXMLProtocolGenerator
import TestContext
import defaultSettings
import getFileContents
import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test

class MapDecodeXMLGenerationTests {

    @Test
    fun `decode wrapped map`() {
        val context = setupTests("Isolated/Restxml/xml-maps.smithy", "aws.protocoltests.restxml#RestXml")
        val contents = getFileContents(context.manifest, "/example/models/XmlMapsOutputBody+Decodable.swift")
        val expectedContents = """
        extension XmlMapsOutputBody: Decodable {
            private enum CodingKeys: String, CodingKey {
                case myMap
            }
        
            public init (from decoder: Decoder) throws {
                let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                let myMapWrappedContainer = try containerValues.nestedContainer(keyedBy: MapEntry<String, GreetingStruct>.CodingKeys.self, forKey: .myMap)
                let myMapContainer = try myMapWrappedContainer.decodeIfPresent([MapKeyValue<String, GreetingStruct>].self, forKey: .entry)
                var myMapBuffer: [String:GreetingStruct]? = nil
                if let myMapContainer = myMapContainer {
                    myMapBuffer = [String:GreetingStruct]()
                    for structureContainer0 in myMapContainer {
                        myMapBuffer?[structureContainer0.key] = structureContainer0.value
                    }
                }
                myMap = myMapBuffer
            }
        }
        """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }
    @Test
    fun `decode nested wrapped map`() {
        val context = setupTests("Isolated/Restxml/xml-maps-nested.smithy", "aws.protocoltests.restxml#RestXml")
        val contents = getFileContents(context.manifest, "/example/models/XmlMapsNestedOutputBody+Decodable.swift")
        val expectedContents =
            """
            extension XmlMapsNestedOutputBody: Decodable {
                private enum CodingKeys: String, CodingKey {
                    case myMap
                }
            
                public init (from decoder: Decoder) throws {
                    let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                    let myMapWrappedContainer = try containerValues.nestedContainer(keyedBy: MapEntry<String, MapEntry<String, GreetingStruct>>.CodingKeys.self, forKey: .myMap)
                    let myMapContainer = try myMapWrappedContainer.decodeIfPresent([MapKeyValue<String, MapEntry<String, GreetingStruct>>].self, forKey: .entry)
                    var myMapBuffer: [String:[String:GreetingStruct]]? = nil
                    if let myMapContainer = myMapContainer {
                        myMapBuffer = [String:[String:GreetingStruct]]()
                        for mapContainer0 in myMapContainer {
                            var nestedBuffer0: [String:GreetingStruct]? = nil
                            if let mapContainer0NestedEntry0 = mapContainer0.value.entry  {
                                nestedBuffer0 = [String:GreetingStruct]()
                                for structureContainer1 in mapContainer0NestedEntry0 {
                                    nestedBuffer0?[structureContainer1.key] = structureContainer1.value
                                }
                            }
                            myMapBuffer?[mapContainer0.key] = nestedBuffer0
                        }
                    }
                    myMap = myMapBuffer
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `decode nested nested wrapped map`() {
        val context = setupTests("Isolated/Restxml/xml-maps-nestednested.smithy", "aws.protocoltests.restxml#RestXml")
        val contents = getFileContents(context.manifest, "/example/models/XmlMapsNestedNestedOutputBody+Decodable.swift")
        val expectedContents =
            """
            extension XmlMapsNestedNestedOutputBody: Decodable {
                private enum CodingKeys: String, CodingKey {
                    case myMap
                }
            
                public init (from decoder: Decoder) throws {
                    let containerValues = try decoder.container(keyedBy: CodingKeys.self)
                    let myMapWrappedContainer = try containerValues.nestedContainer(keyedBy: MapEntry<String, MapEntry<String, MapEntry<String, GreetingStruct>>>.CodingKeys.self, forKey: .myMap)
                    let myMapContainer = try myMapWrappedContainer.decodeIfPresent([MapKeyValue<String, MapEntry<String, MapEntry<String, GreetingStruct>>>].self, forKey: .entry)
                    var myMapBuffer: [String:[String:[String:GreetingStruct]?]]? = nil
                    if let myMapContainer = myMapContainer {
                        myMapBuffer = [String:[String:[String:GreetingStruct]?]]()
                        for mapContainer0 in myMapContainer {
                            var nestedBuffer0: [String:[String:GreetingStruct]?]? = nil
                            if let mapContainer0NestedEntry0 = mapContainer0.value.entry  {
                                nestedBuffer0 = [String:[String:GreetingStruct]?]()
                                for mapContainer1 in mapContainer0NestedEntry0 {
                                    var nestedBuffer1: [String:GreetingStruct]? = nil
                                    if let mapContainer1NestedEntry1 = mapContainer1.value.entry  {
                                        nestedBuffer1 = [String:GreetingStruct]()
                                        for structureContainer2 in mapContainer1NestedEntry1 {
                                            nestedBuffer1?[structureContainer2.key] = structureContainer2.value
                                        }
                                    }
                                    nestedBuffer0?[mapContainer1.key] = nestedBuffer1
                                }
                            }
                            myMapBuffer?[mapContainer0.key] = nestedBuffer0
                        }
                    }
                    myMap = myMapBuffer
                }
            }
            """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    private fun setupTests(smithyFile: String, serviceShapeId: String): TestContext {
        val context = TestContext.initContextFrom(smithyFile, serviceShapeId, MockHttpRestXMLProtocolGenerator()) { model ->
            model.defaultSettings(serviceShapeId, "RestXml", "2019-12-16", "Rest Xml Protocol")
        }
        context.generator.generateDeserializers(context.generationCtx)
        context.generationCtx.delegator.flushWriters()
        return context
    }
}
