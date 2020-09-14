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

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken as RawToken
import java.nio.charset.Charset
import software.aws.clientrt.serde.DeserializationException

private class JsonStreamReaderGson(payload: ByteArray, charset: Charset = Charsets.UTF_8) : JsonStreamReader {
    private val reader = JsonReader(payload.inputStream().reader(charset))

    override fun nextToken(): JsonToken {
        return when (peek()) {
            RawJsonToken.BeginArray -> {
                reader.beginArray()
                JsonToken.BeginArray
            }
            RawJsonToken.EndArray -> {
                reader.endArray()
                JsonToken.EndArray
            }
            RawJsonToken.BeginObject -> {
                reader.beginObject()
                JsonToken.BeginObject
            }
            RawJsonToken.EndObject -> {
                reader.endObject()
                JsonToken.EndObject
            }
            RawJsonToken.Name -> JsonToken.Name(reader.nextName())
            RawJsonToken.String -> JsonToken.String(reader.nextString())
            RawJsonToken.Number -> JsonToken.Number(reader.nextString())
            RawJsonToken.Bool -> JsonToken.Bool(reader.nextBoolean())
            RawJsonToken.Null -> {
                reader.nextNull()
                JsonToken.Null
            }
            RawJsonToken.EndDocument -> JsonToken.EndDocument
        }
    }

    override fun skipNext() = reader.skipValue()

    override fun peek(): RawJsonToken {
        return when (reader.peek()) {
            RawToken.BEGIN_ARRAY -> RawJsonToken.BeginArray
            RawToken.END_ARRAY -> RawJsonToken.EndArray
            RawToken.BEGIN_OBJECT -> RawJsonToken.BeginObject
            RawToken.END_OBJECT -> RawJsonToken.EndObject
            RawToken.NAME -> RawJsonToken.Name
            RawToken.STRING -> RawJsonToken.String
            RawToken.NUMBER -> RawJsonToken.Number
            RawToken.BOOLEAN -> RawJsonToken.Bool
            RawToken.NULL -> RawJsonToken.Null
            RawToken.END_DOCUMENT -> RawJsonToken.EndDocument
            else -> throw DeserializationException("unknown JSON token encountered during deserialization")
        }
    }
}

/*
* Creates a [JsonStreamReader] instance
*/
internal actual fun jsonStreamReader(payload: ByteArray): JsonStreamReader = JsonStreamReaderGson(payload)
