package software.amazon.smithy.swift.codegen.codegencomponents

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.swift.codegen.asSmithy
import software.amazon.smithy.swift.codegen.defaultSettings

class SwiftSettingsTest {
    @Test fun `infers default service`() {
        val model = javaClass.classLoader.getResource("simple-service.smithy").asSmithy()

        val settings = model.defaultSettings(serviceShapeId = "smithy.example#Example")

        assertEquals(ShapeId.from("smithy.example#Example"), settings.service)
        assertEquals("example", settings.moduleName)
        assertEquals("1.0.0", settings.moduleVersion)
        assertEquals("https://docs.amplify.aws/", settings.homepage)
        assertEquals("Amazon Web Services", settings.author)
        assertEquals("https://github.com/aws-amplify/amplify-codegen.git", settings.gitRepo)
        assertEquals(false, settings.mergeModels)
    }
}
