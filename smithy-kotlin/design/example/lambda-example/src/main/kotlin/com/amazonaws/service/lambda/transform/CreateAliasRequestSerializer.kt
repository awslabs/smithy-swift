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
package com.amazonaws.service.lambda.transform

import com.amazonaws.service.lambda.model.CreateAliasRequest
import com.amazonaws.service.runtime.HttpSerialize
import software.aws.clientrt.http.HttpMethod
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.headers
import software.aws.clientrt.http.request.url
import software.aws.clientrt.serde.*


class CreateAliasRequestSerializer(val input: CreateAliasRequest): HttpSerialize {

    companion object {
        private val DESCRIPTION_FIELD_DESCRIPTOR = SdkFieldDescriptor("Description")
        private val FUNCTION_VERSION_DESCRIPTOR = SdkFieldDescriptor("FunctionVersion")
        private val NAME_DESCRIPTOR = SdkFieldDescriptor("Name")
        private val ROUTING_CONFIG_DESCRIPTOR = SdkFieldDescriptor("RoutingConfig")

        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(DESCRIPTION_FIELD_DESCRIPTOR)
            field(FUNCTION_VERSION_DESCRIPTOR)
            field(NAME_DESCRIPTOR)
            field(ROUTING_CONFIG_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, serializer: Serializer) {
        // URI
        builder.method = HttpMethod.POST
        builder.url {
            // NOTE: Individual serializers do not need to concern themselves with protocol/host
            path = "/2015-03-31/functions/${input.functionName}/aliases"
        }

        // Headers
        builder.headers {
            append("Content-Type", "application/json")
        }

        // payload
        serializer.serializeStruct {
            // required fields - TODO: this assumes these are validated already which is TBD. For now assume these are known not-null
            field(NAME_DESCRIPTOR, input.name!!)
            field(FUNCTION_VERSION_DESCRIPTOR, input.functionVersion!!)

            // optional fields
            input.description?.let { field(DESCRIPTION_FIELD_DESCRIPTOR, it) }
            input.routingConfig?.let { map ->
                mapField(ROUTING_CONFIG_DESCRIPTOR) {
                    map.entries.forEach{ entry(it.key, it.value) }
                }
            }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
