/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package software.aws.clientrt.smithy.test

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertFails

// some basic sanity tests that Jetbrains kotlinx-serialization: JsonElement::equals() method works for our purposes
class JsonAssertionsTest {
    @Test
    fun `it ignores key order`() {
        val expected = """
        {
            "string": "v1",
            "int": 1,
            "float": 2.17,
            "bool": true,
            "list": [1,2,3],
            "null": null,
            "struct": {
                "k1": "v1",
                "k2": ["v2"],
                "k3": {
                    "nested": "v3"
                }
            },
            "list2": [{"kl1": "vl1"}, {"kl2": "vl2"}]
        }
        """.trimIndent()

        val actual = """
        {
            "bool": true,
            "null": null,
            "float": 2.17,
            "int": 1,
            "list": [1,2,3],
            "list2": [{"kl1": "vl1"}, {"kl2": "vl2"}],
            "struct": {
                "k3": { "nested": "v3" },
                "k2": ["v2"],
                "k1": "v1"
            },
            "string": "v1"
        }
        """.trimIndent()

        // should not fail
        assertJsonStringsEqual(expected, actual)
    }

    @Test
    fun `it asserts missing keys`() {
        val expected = """
        {
            "string": "v1",
            "int": 1,
            "float": 2.17,
            "bool": true
        }
        """.trimIndent()

        val actual = """
        {
            "string": "v1",
            "int": 1,
            "bool": true
        }
        """.trimIndent()

        val ex = assertFails {
            assertJsonStringsEqual(expected, actual)
        }
        ex.message.shouldContain("expected JSON")
    }

    @Test
    fun `it asserts kitchen sink`() {
        val expected = """
        {
            "string": "v1",
            "int": 1,
            "float": 2.17,
            "bool": true,
            "list": [1,2,3],
            "null": null,
            "struct": {
                "k1": "v1",
                "k2": ["v2"],
                "k3": {
                    "nested": "v3"
                }
            },
            "list2": [{"kl1": "vl1"}, {"kl2": "vl2"}]
        }
        """.trimIndent()

        val actual = """
        {
            "bool": true,
            "null": null,
            "float": 2.17,
            "int": 1,
            "list": [1,2,3],
            "list2": [{"kl1": "vl1"}, {"kl2": "vl2"}],
            "struct": {
                "k3": { "nested": "i am the only different property" },
                "k2": ["v2"],
                "k1": "v1"
            },
            "string": "v1"
        }
        """.trimIndent()

        val ex = assertFails {
            assertJsonStringsEqual(expected, actual)
        }
        ex.message.shouldContain("expected JSON")
    }
}
