/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.swift.codegen.SwiftCodegenPlugin
import software.amazon.smithy.swift.codegen.customtraits.HashableTrait
import software.amazon.smithy.swift.codegen.model.HashableShapeTransformer
import software.amazon.smithy.swift.codegen.model.hasTrait
import kotlin.streams.toList

class HashableShapeTransformerTests {

    @Test
    fun `leave non-hashable models unchanged`() {
        val model = javaClass.getResource("simple-service-with-operation-and-dependency.smithy").asSmithy()
        val transformed = HashableShapeTransformer.transform(model)
        transformed.shapes().toList().forEach {
            Assertions.assertFalse(transformed.getShape(it.id).get().hasTrait<HashableTrait>())
        }
    }

    @Test
    fun `add the hashable trait to hashable shapes`() {
        val model = javaClass.getResource("hashable-trait-test.smithy").asSmithy()
        val transformed = HashableShapeTransformer.transform(model)

        val traitedMember = "smithy.example#HashableStructure"
        val traitedMemberShape = transformed.getShape(ShapeId.from(traitedMember)).get()

        Assertions.assertTrue(traitedMemberShape.hasTrait<HashableTrait>())
    }

    @Test
    fun `test for nested types with hashable trait`() {
        val model = javaClass.getResource("hashable-trait-test.smithy").asSmithy()
        val transformed = HashableShapeTransformer.transform(model)

        val traitedMember2 = "smithy.example#NestedHashableStructure"
        val traitedMemberShape2 = transformed.getShape(ShapeId.from(traitedMember2)).get()

        Assertions.assertTrue(traitedMemberShape2.hasTrait<HashableTrait>())
    }

    @Test
    fun `test that certain types do not receive the trait`() {
        val model = javaClass.getResource("hashable-trait-test.smithy").asSmithy()
        val transformed = HashableShapeTransformer.transform(model)

        val untraitedMember = "smithy.example#HashableInput"
        val untraitedMemberShape = transformed.getShape(ShapeId.from(untraitedMember)).get()

        Assertions.assertFalse(untraitedMemberShape.hasTrait<HashableTrait>())
    }

    @Test
    fun `add the hashable trait to hashable shapes during integration with SwiftCodegenPlugin`() {
        val model = javaClass.getResource("hashable-trait-test.smithy").asSmithy()
        val manifest = MockManifest()
        val context = buildMockPluginContext(model, manifest, "smithy.example#Example")
        SwiftCodegenPlugin().execute(context)

        val hashableShapeInput = manifest
            .getFileString("example/models/HashableShapesInput.swift").get()
        Assertions.assertNotNull(hashableShapeInput)
        val expected = """
            public struct HashableShapesInput: Swift.Equatable {
                public var `set`: Swift.Set<ExampleClientTypes.HashableStructure>?
                public var bar: Swift.String?
            
                public init (
                    `set`: Swift.Set<ExampleClientTypes.HashableStructure>? = nil,
                    bar: Swift.String? = nil
                )
                {
                    self.`set` = `set`
                    self.bar = bar
                }
            }
        """.trimIndent()
        hashableShapeInput.shouldContain(expected)

        val hashableShapeOutput = manifest
            .getFileString("example/models/HashableShapesOutputResponse.swift").get()
        Assertions.assertNotNull(hashableShapeOutput)
        val expectedOutput = """
            public struct HashableShapesOutputResponse: Swift.Equatable {
                public var quz: Swift.String?
            
                public init (
                    quz: Swift.String? = nil
                )
                {
                    self.quz = quz
                }
            }
        """.trimIndent()
        hashableShapeOutput.shouldContain(expectedOutput)

        val hashableSetShape = manifest
            .getFileString("example/models/HashableStructure.swift").get()
        Assertions.assertNotNull(hashableSetShape)
        val expectedStructureShape = """
            extension ExampleClientTypes {
                public struct HashableStructure: Swift.Equatable, Swift.Hashable {
                    public var baz: ExampleClientTypes.NestedHashableStructure?
                    public var foo: Swift.String?
            
                    public init (
                        baz: ExampleClientTypes.NestedHashableStructure? = nil,
                        foo: Swift.String? = nil
                    )
                    {
                        self.baz = baz
                        self.foo = foo
                    }
                }
            
            }
        """.trimIndent()
        hashableSetShape.shouldContain(expectedStructureShape)

        val hashableNestedStructure = manifest
            .getFileString("example/models/NestedHashableStructure.swift").get()
        Assertions.assertNotNull(hashableNestedStructure)
        val expectedNestedStructureShape = """
        extension ExampleClientTypes {
            public struct NestedHashableStructure: Swift.Equatable, Swift.Hashable {
                public var bar: Swift.String?
                public var quz: Swift.Int?
        
                public init (
                    bar: Swift.String? = nil,
                    quz: Swift.Int? = nil
                )
                {
                    self.bar = bar
                    self.quz = quz
                }
            }
        
        }
        """.trimIndent()
        hashableNestedStructure.shouldContain(expectedNestedStructureShape)
    }
}
