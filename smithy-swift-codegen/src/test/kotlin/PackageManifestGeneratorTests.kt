/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.string.shouldContain
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.swift.codegen.SwiftCodegenPlugin
import software.amazon.smithy.swift.codegen.SwiftSettings
import software.amazon.smithy.swift.codegen.writePackageManifest

class PackageManifestGeneratorTests {
    private val model: Model = javaClass.getResource("simple-service-with-operation-and-dependency.smithy").asSmithy()
    private val settings: SwiftSettings = model.defaultSettings(moduleName = "MockSDK")
    private val manifest: MockManifest = MockManifest()
    private val mockDependencies: MutableList<SymbolDependency>

    init {
        val provider: SymbolProvider = SwiftCodegenPlugin.createSymbolProvider(model, "MockSDK")
        mockDependencies = getMockDependenciesFromModel(model, provider)
    }

    @Test
    fun `it renders package manifest file with correct dependencies block`() {
        writePackageManifest(settings, manifest, mockDependencies)
        val packageManifest = manifest.getFileString("Package.swift").get()
        assertNotNull(packageManifest)
        packageManifest.shouldContain(
            "dependencies: [\n" +
                    "        .package(\n" +
                    "            url: \"https://github.com/apple/swift-numerics\",\n" +
                    "            from: 0.0.5\n" +
                    "        ),\n" +
                    "        .package(path: \"../../../../../../ClientRuntime\"),\n" +
                    "    ]")
    }

    @Test
    fun `it renders package manifest file with correct platforms block`() {
        writePackageManifest(settings, manifest, mockDependencies)
        val packageManifest = manifest.getFileString("Package.swift").get()
        assertNotNull(packageManifest)
        packageManifest.shouldContain(
            "platforms: [\n" +
                    "        .macOS(.v10_15), .iOS(.v13)\n" +
                    "    ]")
    }

    @Test
    fun `it renders package manifest file with correct products block`() {
        writePackageManifest(settings, manifest, mockDependencies)
        val packageManifest = manifest.getFileString("Package.swift").get()
        assertNotNull(packageManifest)
        packageManifest.shouldContain(
            "products: [\n" +
                    "        .library(name: \"MockSDK\", targets: [\"MockSDK\"])\n" +
                    "    ]")
    }

    @Test
    fun `it renders package manifest file with correct targets block`() {
        writePackageManifest(settings, manifest, mockDependencies, true)
        val packageManifest = manifest.getFileString("Package.swift").get()
        assertNotNull(packageManifest)
        packageManifest.shouldContain(
            "targets: [\n" +
                    "        .target(\n" +
                    "            name: \"MockSDK\",\n" +
                    "            dependencies: [\n" +
                    "                .product(\n" +
                    "                    name: \"ComplexModule\",\n" +
                    "                    package: \"swift-numerics\"\n" +
                    "                ),\n" +
                    "                .product(\n" +
                    "                    name: \"ClientRuntime\",\n" +
                    "                    package: \"ClientRuntime\"\n" +
                    "                ),\n" +
                    "            ],\n" +
                    "            path: \"./MockSDK\"\n" +
                    "        ),\n" +
                    "        .testTarget(\n" +
                    "            name: \"MockSDKTests\",\n" +
                    "            dependencies: [\n" +
                    "                \"MockSDK\",\n" +
                    "                .product(name: \"SmithyTestUtil\", package: \"ClientRuntime\")\n" +
                    "            ],\n" +
                    "            path: \"./MockSDKTests\"\n" +
                    "        )\n" +
                    "    ]"
            )
    }

    fun getMockDependenciesFromModel(model: Model, symbolProvider: SymbolProvider): MutableList<SymbolDependency> {
        val mockDependencies = mutableListOf<SymbolDependency>()

        // BigInteger and Document types have dependencies on other packages
        val bigIntTypeMember = model.shapes().filter { it.isBigIntegerShape }.toList().first()
        mockDependencies.addAll(symbolProvider.toSymbol(bigIntTypeMember).dependencies)

        val documentTypeMember = model.shapes().filter { it.isDocumentShape }.toList().first()
        mockDependencies.addAll(symbolProvider.toSymbol(documentTypeMember).dependencies)
        return mockDependencies
    }
}
