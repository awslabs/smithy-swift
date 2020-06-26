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
package software.aws.clientrt.http.feature

import kotlin.test.Test
import kotlin.test.assertFailsWith
import software.aws.clientrt.http.Headers
import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.HttpStatusCode
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.HttpResponseContext
import software.aws.clientrt.http.response.TypeInfo
import software.aws.clientrt.http.sdkHttpClient
import software.aws.clientrt.testing.runSuspendTest

class DefaultValidateResponseTest {
    @Test
    fun `it throws exception on non-200 response`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }

        val client = sdkHttpClient(mockEngine) {
            install(DefaultValidateResponse)
        }

        val httpResp = HttpResponse(
            HttpStatusCode.BadRequest,
            Headers {},
            HttpBody.Empty,
            HttpRequestBuilder().build()
        )

        val context = HttpResponseContext(httpResp, TypeInfo(Int::class))
        assertFailsWith(HttpResponseException::class) {
            client.responsePipeline.execute(context, httpResp.body)
        }

        return@runSuspendTest
    }

    @Test
    fun `it passes success responses`() = runSuspendTest {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse { throw NotImplementedError() }
        }

        val client = sdkHttpClient(mockEngine) {
            install(DefaultValidateResponse)
        }

        val httpResp = HttpResponse(
            HttpStatusCode.Accepted,
            Headers {},
            HttpBody.Empty,
            HttpRequestBuilder().build()
        )

        val context = HttpResponseContext(httpResp, TypeInfo(Int::class))
        client.responsePipeline.execute(context, httpResp.body)

        return@runSuspendTest
    }
}
