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
package software.aws.clientrt.serde

/**
 * This tag interface provides a mechanism to attach type-specific metadata to any field.
 * See [software.aws.clientrt.serde.xml.XmlList] for an example implementation.
 *
 * For example, to specify that a list should be serialized in XML such that values are wrapped
 * in a tag called "boo", pass an instance of XmlList to the FieldDescriptor of `XmlList(elementName="boo")`.
 */
interface FieldTrait

/**
 * A protocol-agnostic type description of a field.
 */
sealed class SerialKind {
    object Unit : SerialKind()
    object Integer : SerialKind()
    object Long : SerialKind()
    object Double : SerialKind()
    object String : SerialKind()
    object Boolean : SerialKind()
    object Byte : SerialKind()
    object Char : SerialKind()
    object Short : SerialKind()
    object Float : SerialKind()
    object Map : SerialKind()
    object List : SerialKind()
    object Struct : SerialKind()

    override fun toString(): kotlin.String {
        return this::class.simpleName ?: "SerialKind"
    }
}

/**
 * Metadata to describe how a given member property maps to serialization.
 *
 * @property serialName name to use when serializing/deserializing this field (e.g. in JSON, this is the property name)
 */
open class SdkFieldDescriptor(val serialName: String, val kind: SerialKind, var index: Int = 0, vararg val trait: FieldTrait) {

    companion object {
        // For use in formats which provide ways of encoding nameless entities.  Value is disregarded.
        val ANONYMOUS_DESCRIPTOR = SdkFieldDescriptor("ANONYMOUS_FIELD", SerialKind.Struct)
    }
    /**
     * Returns the singleton instance of required Trait, or IllegalArgumentException if does not exist.
     */
    inline fun <reified TExpected : FieldTrait> expectTrait(): TExpected {
        val x = trait.find { it::class == TExpected::class }
        requireNotNull(x) { "Expected to find trait ${TExpected::class} but was not present." }

        return x as TExpected
    }

    inline fun <reified TExpected : FieldTrait> findTrait(): TExpected? {
        val x = trait.find { it::class == TExpected::class }

        return x as TExpected?
    }

    override fun toString(): String {
        return "$serialName($kind, ${trait.joinToString(separator = ",") }})"
    }
}
