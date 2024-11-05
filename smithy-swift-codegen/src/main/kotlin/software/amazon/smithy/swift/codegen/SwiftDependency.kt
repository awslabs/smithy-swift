/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.SymbolDependency

class SwiftDependency(
    override val target: String,
    private val branch: String? = null,
    private val version: String,
    private val location: String,
    private val localPath: String,
    override var packageName: String,
    private val distributionMethod: DistributionMethod,
) : Dependency {

    enum class DistributionMethod {
        SPR, GIT
    }

    companion object {
        val NONE = SwiftDependency(
            "",
            "",
            "",
            "",
            "",
            "",
            DistributionMethod.GIT,
        )
        val SWIFT = SwiftDependency(
            "Swift",
            "",
            "",
            "",
            "",
            "",
            DistributionMethod.GIT,
        )
        val XCTest = SwiftDependency(
            "XCTest",
            null,
            "",
            "",
            "",
            "",
            DistributionMethod.GIT,
        )
        val CLIENT_RUNTIME = smithySwiftTargetNamed("ClientRuntime")
        val SMITHY = smithySwiftTargetNamed("Smithy")
        val SMITHY_IDENTITY = smithySwiftTargetNamed("SmithyIdentity")
        val SMITHY_RETRIES_API = smithySwiftTargetNamed("SmithyRetriesAPI")
        val SMITHY_RETRIES = smithySwiftTargetNamed("SmithyRetries")
        val SMITHY_HTTP_API = smithySwiftTargetNamed("SmithyHTTPAPI")
        val SMITHY_HTTP_AUTH_API = smithySwiftTargetNamed("SmithyHTTPAuthAPI")
        val SMITHY_HTTP_AUTH = smithySwiftTargetNamed("SmithyHTTPAuth")
        val SMITHY_STREAMS = smithySwiftTargetNamed("SmithyStreams")
        val SMITHY_EVENT_STREAMS_API = smithySwiftTargetNamed("SmithyEventStreamsAPI")
        val SMITHY_EVENT_STREAMS = smithySwiftTargetNamed("SmithyEventStreams")
        val SMITHY_TEST_UTIL = smithySwiftTargetNamed("SmithyTestUtil")
        val SMITHY_READ_WRITE = smithySwiftTargetNamed("SmithyReadWrite")
        val SMITHY_TIMESTAMPS = smithySwiftTargetNamed("SmithyTimestamps")
        val SMITHY_XML = smithySwiftTargetNamed("SmithyXML")
        val SMITHY_JSON = smithySwiftTargetNamed("SmithyJSON")
        val SMITHY_FORM_URL = smithySwiftTargetNamed("SmithyFormURL")
        val SMITHY_WAITERS_API = smithySwiftTargetNamed("SmithyWaitersAPI")

        private fun smithySwiftTargetNamed(name: String): SwiftDependency {
            return SwiftDependency(
                name,
                "main",
                "0.54.0",
                "https://github.com/awslabs/smithy-swift",
                "../../../smithy-swift",
                "smithy-swift",
                DistributionMethod.GIT,
            )
        }
    }

    override fun getDependencies(): List<SymbolDependency> {
        return listOf(toSymbolDependency())
    }

    private fun toSymbolDependency(): SymbolDependency {
        val builder = SymbolDependency.builder()
            .putProperty("target", target)
            .putProperty("branch", branch)
            .putProperty("localPath", localPath)
            .packageName(packageName)
            .version(version)
        when (distributionMethod) {
            DistributionMethod.GIT -> {
                builder.putProperty("url", location)
            }
            DistributionMethod.SPR -> {
                builder.putProperty("scope", location)
            }
        }
        return builder.build()
    }
}
