/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen

import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import kotlin.jvm.optionals.getOrNull

class PackageManifestGenerator(val ctx: ProtocolGenerator.GenerationContext) {

    fun writePackageManifest(dependencies: List<SymbolDependency>) {
        ctx.delegator.useFileWriter("Package.swift.txt") { writer ->
            writer.write("// swift-tools-version: \$L", ctx.settings.swiftVersion)
            writer.write("")
            writer.write("import PackageDescription")
            writer.write("")

            writer.openBlock("let package = Package(", ")") {
                writer.write("name: \$S,", ctx.settings.moduleName)

                writer.openBlock("platforms: [", "],") {
                    writer.write(".macOS(.v10_15), .iOS(.v13)")
                }

                writer.openBlock("products: [", "],") {
                    writer.write(".library(name: \$S, targets: [\$S])", ctx.settings.moduleName, ctx.settings.moduleName)
                }

                val externalDependencies = dependencies
                    .filter { it.expectProperty("target", String::class.java) != "SmithyTestUtil" } // SmithyTestUtil links to test target only
                    .filter {
                        it.getProperty("url", String::class.java).getOrNull() != null ||
                            it.getProperty("scope", String::class.java).getOrNull() != null
                    }
                val dependenciesByURL = externalDependencies
                    .distinctBy {
                        it.getProperty("url", String::class.java).getOrNull()
                            ?: "${it.getProperty("scope", String::class.java).get()}.${it.packageName}"
                    }
                    .sorted()

                writer.openBlock("dependencies: [", "],") {
                    dependenciesByURL.forEach { writePackageDependency(writer, it) }
                }

                val dependenciesByTarget = externalDependencies
                    .distinctBy { it.expectProperty("target", String::class.java) + it.packageName }
                    .sorted()

                writer.openBlock("targets: [", "]") {
                    writer.openBlock(".target(", "),") {
                        writer.write("name: \$S,", ctx.settings.moduleName)
                        writer.openBlock("dependencies: [", "],") {
                            dependenciesByTarget.forEach { writeTargetDependency(writer, it) }
                        }
                        writer.openBlock("resources: [", "]") {
                            writer.write(".process(\"Resources\")")
                        }
                    }
                    writer.openBlock(".testTarget(", ")") {
                        writer.write("name: \$S,", ctx.settings.testModuleName)
                        writer.openBlock("dependencies: [", "]") {
                            writer.write("\$S,", ctx.settings.moduleName)
                            SwiftDependency.SMITHY_TEST_UTIL.dependencies.forEach { writeTargetDependency(writer, it) }
                        }
                    }
                }
            }
        }
    }

    private fun writePackageDependency(writer: SwiftWriter, dependency: SymbolDependency) {
        writer.openBlock(".package(", "),") {
            val scope = dependency.getProperty("scope", String::class.java).getOrNull()
            scope?.let {
                writer.write("id: \$S,", "$it.${dependency.packageName}")
            }
            val url = dependency.getProperty("url", String::class.java).getOrNull()
            url?.let {
                writer.write("url: \$S,", it)
            }
            writer.write("exact: \$S", dependency.version)
        }
    }

    private fun writeTargetDependency(writer: SwiftWriter, dependency: SymbolDependency) {
        writer.openBlock(".product(", "),") {
            val target = dependency.expectProperty("target", String::class.java)
            writer.write("name: \$S,", target)
            val scope = dependency.getProperty("scope", String::class.java).getOrNull()
            val packageName = scope?.let { "$it.${dependency.packageName}" } ?: dependency.packageName
            writer.write("package: \$S", packageName)
        }
    }
}
