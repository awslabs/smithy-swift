rootProject.name = "smithy-kotlin"
enableFeaturePreview("GRADLE_METADATA")

include(":smithy-kotlin-codegen")
include(":smithy-kotlin-codegen-test")

include(":client-runtime")
include(":client-runtime:client-rt-core")
include(":client-runtime:utils")
include(":client-runtime:protocol:http")
