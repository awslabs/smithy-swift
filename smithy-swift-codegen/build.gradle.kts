/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    jacoco
    `maven-publish`
}

description = "Generates Swift code from Smithy models"
extra["displayName"] = "Smithy :: Swift :: Codegen"
extra["moduleName"] = "software.amazon.smithy.swift.codegen"

group = "software.amazon.smithy"
version = "0.1.0"

val smithyVersion: String by project
val commonMarkParserVersion: String by project
val jsoupVersion: String by project
val kotestVersion: String by project
val junitVersion: String by project
val jacocoVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    api("software.amazon.smithy:smithy-codegen-core:$smithyVersion")
    api("software.amazon.smithy:smithy-waiters:$smithyVersion")
    api("software.amazon.smithy:smithy-smoke-test-traits:${smithyVersion}")
    api("software.amazon.smithy:smithy-aws-smoke-test-model:${smithyVersion}")
    api("com.atlassian.commonmark:commonmark:$commonMarkParserVersion")
    api("org.jsoup:jsoup:$jsoupVersion")
    implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    implementation("software.amazon.smithy:smithy-rules-engine:$smithyVersion")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

jacoco {
    toolVersion = "$jacocoVersion"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// Reusable license copySpec
val licenseSpec = copySpec {
    from("${project.rootDir}/LICENSE")
    from("${project.rootDir}/NOTICE")
}

val sourcesJar by tasks.creating(Jar::class) {
    group = "publishing"
    description = "Assembles Kotlin sources jar"
    archiveClassifier = "sources"
    from(sourceSets.getByName("main").allSource)
}

// Configure jars to include license related info
tasks.jar {
    metaInf.with(licenseSpec)
    inputs.property("moduleName", project.name)
    manifest {
        attributes["Automatic-Module-Name"] = project.name
    }
}

// Configure jacoco (code coverage) to generate an HTML report
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.outputLocation.set(file("$buildDir/reports/jacoco"))
    }
}

// Always run the jacoco test report after testing.
tasks["test"].finalizedBy(tasks.jacocoTestReport)

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            url = uri("$buildDir/repository")
        }
    }
}
