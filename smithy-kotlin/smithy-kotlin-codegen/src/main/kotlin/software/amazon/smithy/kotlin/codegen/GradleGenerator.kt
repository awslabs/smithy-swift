/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.utils.CodeWriter

/**
 * Create the gradle build file for the generated code
 */
fun writeGradleBuild(
    settings: KotlinSettings,
    manifest: FileManifest,
    dependencies: List<KotlinDependency>,
    integrations: List<KotlinIntegration> = listOf()
) {
    val writer = CodeWriter().apply {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
    }

    writer.withBlock("plugins {", "}\n") {
        if (settings.build.rootProject) {
            write("kotlin(\"jvm\") version \"1.3.72\"")
        } else {
            write("kotlin(\"jvm\")")
        }
    }

    if (settings.build.rootProject) {
        writer.withBlock("repositories {", "}\n") {
            write("mavenLocal()")
            write("mavenCentral()")
            write("jcenter()")
        }
    }

    writer.withBlock("dependencies {", "}\n") {
        write("implementation(kotlin(\"stdlib\"))")
        // TODO - can we make kotlin dependencies not specify a version e.g. kotlin("kotlin-test")
        // TODO - Kotlin MPP setup (pass through KotlinSettings) - maybe separate gradle writers
        val orderedDependencies = dependencies.sortedWith(compareBy({ it.config }, { it.artifact }))
        for (dependency in orderedDependencies) {
            write("${dependency.config}(\"\$L:\$L:\$L\")", dependency.group, dependency.artifact, dependency.version)
        }
    }

    val annotations = integrations.flatMap { it.customBuildSettings?.experimentalAnnotations ?: listOf<String>() }.toSet()
    if (annotations.isNotEmpty()) {
        writer.openBlock("val experimentalAnnotations = listOf(")
            .call {
                annotations.forEach {
                    writer.write("\$S", it)
                }
            }
            .closeBlock(")")

        writer.openBlock("kotlin.sourceSets.all {")
            .write("experimentalAnnotations.forEach { languageSettings.useExperimentalAnnotation(it) } ")
            .closeBlock("}")
    }

    writer.write("")
        .openBlock("tasks.test {")
        .write("useJUnitPlatform()")
        .openBlock("testLogging {")
        .write("""events("passed", "skipped", "failed")""")
        .write("showStandardStreams = true")
        .closeBlock("}")
        .closeBlock("}")

    val contents = writer.toString()
    manifest.writeFile("build.gradle.kts", contents)
    if (settings.build.rootProject) {
        manifest.writeFile("settings.gradle.kts", "")
    }
}
