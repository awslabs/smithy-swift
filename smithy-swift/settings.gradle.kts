rootProject.name = "smithy-swift"
include("smithy-swift-codegen")
include("smithy-swift-codegen-test")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
