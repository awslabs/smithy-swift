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
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.DeserializationException
import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SdkObjectDescriptor

/**
 * Exception thrown when the tokenent deserializer state does not meet the expected
 * state for the last operation requested (e.g. attempting to deserialize a struct
 * when the next token is a property name or number).
 */
class DeserializerStateException : DeserializationException {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}

private fun JsonToken.number(): Double? = when (this) {
    is JsonToken.Number -> this.value
    else -> null
}

class JsonDeserializer(payload: ByteArray) : Deserializer, Deserializer.ElementIterator, Deserializer.FieldIterator {
    private val reader = JsonStreamReader(payload)

    // return the next token and require that it be of type [TExpected] or else throw an exception
    private inline fun <reified TExpected> nextToken(): JsonToken {
        val token = reader.nextToken()
        requireToken<TExpected>(token)
        return token
    }

    // require that the given token be of type [TExpected] or else throw an exception
    private inline fun <reified TExpected> requireToken(token: JsonToken) {
        if (token::class != TExpected::class) {
            throw DeserializerStateException("expected ${TExpected::class}; found ${token::class}")
        }
    }

    override fun deserializeByte(): Byte {
        TODO("Not yet implemented")
    }

    override fun deserializeInt(): Int = deserializeDouble().toInt()

    override fun deserializeShort(): Short = deserializeDouble().toShort()

    override fun deserializeLong(): Long = deserializeDouble().toLong()

    override fun deserializeFloat(): Float = deserializeDouble().toFloat()

    override fun deserializeDouble(): Double {
        val token = nextToken<JsonToken.Number>()
        val result = token.number()
        return result!!
    }

    override fun deserializeString(): String {
        val token = nextToken<JsonToken.String>()
        return (token as JsonToken.String).value
    }

    override fun deserializeBool(): Boolean {
        val token = nextToken<JsonToken.Bool>()
        return (token as JsonToken.Bool).value
    }

    override fun deserializeStruct(descriptor: SdkFieldDescriptor?): Deserializer.FieldIterator {
        // TODO - have to handle root object vs nested object
        nextToken<JsonToken.BeginObject>()
        return this
    }

    override fun nextField(descriptor: SdkObjectDescriptor): Int {
        return when (reader.peek()) {
            RawJsonToken.EndObject -> {
                // consume the token
                nextToken<JsonToken.EndObject>()
                Deserializer.FieldIterator.EXHAUSTED
            }
            RawJsonToken.EndDocument -> Deserializer.FieldIterator.EXHAUSTED
            else -> {
                val token = nextToken<JsonToken.Name>()
                val propertyName = (token as JsonToken.Name).value
                val field = descriptor.fields.find { it.serialName == propertyName }
                field?.index ?: Deserializer.FieldIterator.UNKNOWN_FIELD
            }
        }
    }

    override fun skipValue() {
        // stream reader skips the *next* token
        reader.skipNext()
    }

    override fun deserializeList(): Deserializer.ElementIterator {
        nextToken<JsonToken.BeginArray>()
        return this
    }

    override fun next(): Int {
        return when (reader.peek()) {
            RawJsonToken.EndArray -> {
                // consume the token
                nextToken<JsonToken.EndArray>()
                Deserializer.ElementIterator.EXHAUSTED
            }
            RawJsonToken.EndDocument -> Deserializer.ElementIterator.EXHAUSTED
            else -> 0
        }
    }
}
