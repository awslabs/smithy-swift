// swift-tools-version:5.7

import PackageDescription

// Define libxml2 only on Linux, since it causes warnings
// about "pkgconfig not found" on Mac
#if os(Linux)
let libXML2DependencyOrNil: Target.Dependency? = "libxml2"
let libXML2TargetOrNil: Target? = Target.systemLibrary(
    name: "libxml2",
    pkgConfig: "libxml-2.0",
    providers: [
        .apt(["libxml2 libxml2-dev"]),
        .yum(["libxml2 libxml2-devel"])
    ]
)
#else
let libXML2DependencyOrNil: Target.Dependency? = nil
let libXML2TargetOrNil: Target? = nil
#endif

let package = Package(
    name: "smithy-swift",
    platforms: [
        .macOS(.v10_15),
        .iOS(.v13),
        .tvOS(.v13),
        .watchOS(.v6)
    ],
    products: [
        .library(name: "Smithy", targets: ["Smithy"]),
        .library(name: "ClientRuntime", targets: ["ClientRuntime"]),
        .library(name: "SmithyRetriesAPI", targets: ["SmithyRetriesAPI"]),
        .library(name: "SmithyRetries", targets: ["SmithyRetries"]),
        .library(name: "SmithyReadWrite", targets: ["SmithyReadWrite"]),
        .library(name: "SmithyXML", targets: ["SmithyXML"]),
        .library(name: "SmithyJSON", targets: ["SmithyJSON"]),
        .library(name: "SmithyFormURL", targets: ["SmithyFormURL"]),
        .library(name: "SmithyIdentityAPI", targets: ["SmithyIdentityAPI"]),
        .library(name: "SmithyHTTPAPI", targets: ["SmithyHTTPAPI"]),
        .library(name: "SmithyHTTPAuthAPI", targets: ["SmithyHTTPAuthAPI"]),
        .library(name: "SmithyEventStreamsAPI", targets: ["SmithyEventStreamsAPI"]),
        .library(name: "SmithyEventStreamsAuthAPI", targets: ["SmithyEventStreamsAuthAPI"]),
        .library(name: "SmithyEventStreams", targets: ["SmithyEventStreams"]),
        .library(name: "SmithyChecksumsAPI", targets: ["SmithyChecksumsAPI"]),
        .library(name: "SmithyTestUtil", targets: ["SmithyTestUtil"]),
    ],
    dependencies: [
        .package(url: "https://github.com/awslabs/aws-crt-swift.git", exact: "0.30.0"),
        .package(url: "https://github.com/apple/swift-log.git", from: "1.0.0"),
        .package(url: "https://github.com/open-telemetry/opentelemetry-swift", from: "1.0.0"),
    ],
    targets: [
        .target(
            name: "Smithy",
            dependencies: [
                .product(name: "Logging", package: "swift-log"),
            ]
        ),
        .target(
            name: "ClientRuntime",
            dependencies: [
                "Smithy",
                "SmithyRetriesAPI",
                "SmithyRetries",
                "SmithyXML",
                "SmithyJSON",
                "SmithyFormURL",
                "SmithyIdentityAPI",
                "SmithyHTTPAPI",
                "SmithyHTTPAuthAPI",
                "SmithyEventStreamsAPI",
                "SmithyEventStreams",
                "SmithyEventStreamsAuthAPI",
                "SmithyChecksumsAPI",
                .product(name: "AwsCommonRuntimeKit", package: "aws-crt-swift"),
            ],
            resources: [
                .copy("PrivacyInfo.xcprivacy")
            ]
        ),
        .target(
            name: "SmithyRetriesAPI"
        ),
        .target(
            name: "SmithyRetries",
            dependencies: [
                "SmithyRetriesAPI",
                .product(name: "AwsCommonRuntimeKit", package: "aws-crt-swift"),
            ]
        ),
        .testTarget(
            name: "SmithyRetriesTests",
            dependencies: ["ClientRuntime", "SmithyRetriesAPI", "SmithyRetries"]
        ),
        .target(
            name: "SmithyReadWrite",
            dependencies: ["SmithyTimestamps"]
        ),
        .target(
            name: "SmithyXML",
            dependencies: [
                "SmithyReadWrite",
                "SmithyTimestamps",
                libXML2DependencyOrNil
            ].compactMap { $0 }
        ),
        .target(
            name: "SmithyJSON",
            dependencies: [
                "SmithyReadWrite",
                "SmithyTimestamps"
            ]
        ),
        .target(
            name: "SmithyFormURL",
            dependencies: [
                "SmithyReadWrite",
                "SmithyTimestamps"
            ]
        ),
        libXML2TargetOrNil,
        .target(
            name: "SmithyTimestamps"
        ),
        .target(
            name: "SmithyTestUtil",
            dependencies: ["ClientRuntime"]
        ),
        .target(
            name: "SmithyIdentityAPI",
            dependencies: ["Smithy"]
        ),
        .target(
            name: "SmithyHTTPAPI",
            dependencies: ["Smithy"]
        ),
        .target(
            name: "SmithyHTTPAuthAPI",
            dependencies: ["Smithy", "SmithyHTTPAPI"]
        ),
        .target(
            name: "SmithyEventStreamsAPI",
            dependencies: ["Smithy"]
        ),
        .target(
            name: "SmithyEventStreamsAuthAPI",
            dependencies: ["Smithy", "SmithyEventStreamsAPI"]
        ),
        .target(
            name: "SmithyEventStreams",
            dependencies: [
                "Smithy",
                "SmithyEventStreamsAPI",
                "SmithyEventStreamsAuthAPI",
                .product(name: "AwsCommonRuntimeKit", package: "aws-crt-swift")
            ]
        ),
        .target(
            name: "SmithyChecksumsAPI",
            dependencies: ["Smithy"]
        ),
        .testTarget(
            name: "ClientRuntimeTests",
            dependencies: ["ClientRuntime", "SmithyTestUtil"],
            resources: [ .process("Resources") ]
        ),
        .testTarget(
            name: "SmithyXMLTests",
            dependencies: ["SmithyXML", "ClientRuntime"]
        ),
        .testTarget(
            name: "SmithyJSONTests",
            dependencies: ["SmithyJSON", "ClientRuntime"]
        ),
        .testTarget(
            name: "SmithyFormURLTests",
            dependencies: ["SmithyFormURL", "ClientRuntime"]
        ),
        .testTarget(
            name: "SmithyTimestampsTests",
            dependencies: ["SmithyTimestamps"]
        ),
        .testTarget(
            name: "SmithyTestUtilTests",
            dependencies: ["SmithyTestUtil"]
        ),
        .testTarget(
            name: "SmithyEventStreamsTests",
            dependencies: ["SmithyEventStreams"]
        ),
    ].compactMap { $0 }
)

func addTestServiceTargets() {
    package.targets += [
        .target(
            name: "WeatherSDK",
            dependencies: ["SmithyTestUtil", "ClientRuntime", "SmithyRetriesAPI", "SmithyRetries"]
        ),
        .testTarget(
            name: "WeatherSDKTests",
            dependencies: ["WeatherSDK", "SmithyTestUtil"]
        )
    ]
}

addTestServiceTargets()
