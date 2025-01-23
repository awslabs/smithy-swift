//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import AwsCommonRuntimeKit

public final class SDKDefaultIO: @unchecked Sendable {
    public let eventLoopGroup: EventLoopGroup
    public let hostResolver: HostResolver
    public let clientBootstrap: ClientBootstrap
    public let tlsContext: TLSContext

    /// Provide singleton access since we want to share and re-use the instance properties
    public static let shared = SDKDefaultIO()

    /// The public setter for setting log level of CRT logger.
    ///
    /// If any log level other than the default log level of `.none` is desired, this setter **MUST** be called before accessing the `SDKDefaultIO.shared` static field.
    public static func setLogLevel(level: LogLevel) {
        SDKDefaultIO.setupLogger(level: level)
    }

    private init() {
        CommonRuntimeKit.initialize()
        do {
            try Logger.initialize(target: .standardOutput, level: .trace)
        } catch CommonRunTimeError.crtError(let error)
                    where error.code == 6 && error.name == "AWS_ERROR_UNSUPPORTED_OPERATION" {
            // logger was already initialized, no need to initialize it
        } catch {
            Self.failOnLogger()
        }

        do {
            self.eventLoopGroup = try EventLoopGroup(threadCount: 0)
        } catch {
            fatalError("""
            Event Loop Group failed to create. This should never happen. Please open a
            Github issue with us at https://github.com/awslabs/aws-sdk-swift.
            """)
        }

        do {
            self.hostResolver = try HostResolver.makeDefault(
                eventLoopGroup: eventLoopGroup,
                maxHosts: 8,
                maxTTL: 30
            )
        } catch {
            fatalError("""
            Host Resolver failed to create. This should never happen. Please open a
            Github issue with us at https://github.com/awslabs/aws-sdk-swift.
            """)
        }

        do {
            self.clientBootstrap = try ClientBootstrap(eventLoopGroup: eventLoopGroup,
                                                       hostResolver: hostResolver)
        } catch {
            fatalError("""
                       Client Bootstrap failed to create. This could be due to lack
                       of memory but should never happen. Please open a Github issue with
                       us at https://github.com/awslabs/aws-sdk-swift.
                       """)
        }

        let tlsContextOptions = TLSContextOptions.makeDefault()
        tlsContextOptions.setVerifyPeer(true)

        do {
            self.tlsContext = try TLSContext(options: tlsContextOptions,
                                             mode: .client)
        } catch {
            fatalError("""
                        Tls Context failed to create. This should never happen.Please open a
                        Github issue with us at https://github.com/awslabs/aws-sdk-swift.
                        """)
        }
    }

    private static func setupLogger(level: LogLevel) {
        do {
            try Logger.initialize(target: .standardOutput, level: level)
        } catch {
            fatalError("""
            Logger failed to create. This should never happen. Please open a
            Github issue with us at https://github.com/awslabs/aws-sdk-swift.
            """)
        }
    }

    private static func failOnLogger() -> Never {
        fatalError("""
        Logger failed to create. This should never happen. Please open a
        Github issue with us at https://github.com/awslabs/aws-sdk-swift.
        """)
    }
}
