plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(project(":client-runtime:client-rt-core"))
    implementation(project(":client-runtime:protocol:http"))
    implementation(project(":client-runtime:protocol:http-client-engines:http-client-engine-ktor"))
    implementation(project(":client-runtime:serde"))
    implementation(project(":client-runtime:serde:serde-json"))

    // FIXME - this is only necessary for a conversion from ByteStream to HttpBody (which belongs in client runtime)
    implementation(project(":client-runtime:io"))

    // FIXME - this isn't necessary it's only here for the example main function
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.6")
}

//compileKotlin {
//    kotlinOptions.jvmTarget = "1.8"
//}
//
//compileTestKotlin {
//    kotlinOptions.jvmTarget = "1.8"
//}
// FIXME - intellij 2020.2 is not resolving mpp dependencies and the default run configurations are not adding
// project dependencies to the classpath. As a workaround just use gradle (i.e ./gradlew :example:s3-example:application)
// Similar to: https://youtrack.jetbrains.com/issue/KT-38651
application {
    mainClassName = "com.amazonaws.service.lambda.DefaultLambdaClientKt"
}
