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

interface FieldTrait
// class XmlAttribute : FieldTrait  // TBD
// NOTE: The XML specific Traits which describe names will need to be amended to include namespace (or a Qualified Name)
// If it's determined we need to serialize from/to specific namespaces.
class XmlMap(
    val parent: String? = "map",
    val entry: String = "entry",
    val keyName: String = "key",
    val valueName: String = "value",
    val flattened: Boolean = false
) : FieldTrait
class XmlList(
    val elementName: String = "element"
) : FieldTrait
class ObjectStruct(val fields: List<SdkFieldDescriptor>) : FieldTrait

/**
 * A protocol-agnostic type description of a field.
 */
sealed class SerialKind(vararg val trait: FieldTrait) {
    class Unit : SerialKind()
    class Integer : SerialKind()
    class Long : SerialKind()
    class Double : SerialKind()
    class String: SerialKind()
    class Boolean: SerialKind()
    class Byte: SerialKind()
    class Char: SerialKind()
    class Short: SerialKind()
    class Float: SerialKind()
    class Map(vararg trait: FieldTrait) : SerialKind(*trait)
    class List(vararg trait: FieldTrait): SerialKind(*trait)
    class Struct(vararg trait: FieldTrait): SerialKind(*trait)

    /**
     * Returns the singleton instance of required Trait, or IllegalArgumentException if does not exist.
     */
    inline fun <reified TExpected : FieldTrait> expectTrait(): TExpected {
        val x = trait.find { it::class == TExpected::class }
        requireNotNull(x) { "Expected to find trait ${TExpected::class} but was not present." }

        return x as TExpected
    }
}
/**
 * Metadata to describe how a given member property maps to serialization.
 *
 * @property serialName name to use when serializing/deserializing this field (e.g. in JSON, this is the property name)
 */
open class SdkFieldDescriptor(val serialName: String, val kind: SerialKind, var index: Int = 0)

