/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId

class KotlinSettingsTest {
    @Test fun `infers default service`() {
        val model = Model.assembler()
            .addImport(KotlinSettingsTest::class.java.getResource("simple-service.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val settings = KotlinSettings.from(model, Node.objectNodeBuilder()
            .withMember("module", Node.from("example"))
            .withMember("moduleVersion", Node.from("1.0.0"))
            .build())

        assertEquals(ShapeId.from("smithy.example#Example"), settings.service)
        assertEquals("example", settings.moduleName)
        assertEquals("1.0.0", settings.moduleVersion)
    }
}
