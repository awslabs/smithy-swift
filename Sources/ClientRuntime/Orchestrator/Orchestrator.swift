//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//

import class Smithy.Context
import enum Smithy.ClientError
import protocol Smithy.RequestMessage
import protocol Smithy.ResponseMessage
import struct Smithy.AttributeKey
import struct Smithy.Attributes
import SmithyHTTPAPI
import protocol SmithyRetriesAPI.RetryStrategy
import struct SmithyRetriesAPI.RetryErrorInfo
import struct Smithy.SwiftLogger
import enum AwsCommonRuntimeKit.CommonRunTimeError

/// Orchestrates operation execution
///
/// Execution performs the following steps in order:
/// 1. Interceptor.readBeforeExecution
/// 2. Interceptor.modifyBeforeSerialization
/// 3. Interceptor.readBeforeSerialization
/// 4. Serialize InputType into RequestType
/// 5. Interceptor.readAfterSerialization
/// 6. Interceptor.modifyBeforeRetryLoop
/// 7. If a RetryStrategy is present:
///     a. RetryStrategy.acquireInitialRetryToken
///     b. Copy the request (as it will be modified by subsequent steps, and we need to preserve it for retries)
///     c. Interceptor.readBeforeAttempt
///     d. Select the auth scheme
///     e. Apply the endpoint to the request
///     f. Interceptor.modifyBeforeSigning
///     g. Interceptor.readBeforeSigning
///     h. Sign the request
///     i. Interceptor.readAfterSigning
///     j. Interceptor.modifyBeforeTransmit
///     k. Interceptor.readBeforeTransmit
///     l. Send the request and receive the response
///     m. Interceptor.readAfterTransmit
///     n. Interceptor.modifyBeforeDeserialization
///     o. Interceptor.readBeforeDeserialization
///     p. Deserialize ResponseType into OutputType
///     q. Interceptor.readAfterDeserialization
///     r. Interceptor.modifyBeforeAttemptCompletion
///     s. Interceptor.readAfterAttempt
/// 8. If there was an error, try RetryStrategy.refreshTokenForRetry. If the token is refreshed, go to 7c
/// 9. RetryStrategy.recordSuccess
/// 10. Interceptor.modifyBeforeCompletion
/// 11. Interceptor.readAfterExecution
/// 12. If there was an error, throw it. Otherwise return the operation output
///
/// Error behavior works as follows:
/// - If any Interceptors fail, the last error is stored and the rest are logged, meaning the last error that occurred will be thrown
/// - If the service response is deserialized as a modeled error, it is stored as an error
/// - If an error occurs in steps 1 - 7b, store the error and go straight to 10
/// - If an error occurs in steps 7c - 7q, store the error and go straight to 7r
/// - If an error occurs in step 7r, store the error and go to 7s
/// - If an error occurs in step 7s, store the error and go to 8
/// - If an error occurs in step 10, store the error and go to 11
/// - If an error occurs in step 11, store the error and go to 12
public struct Orchestrator<
    InputType,
    OutputType,
    RequestType: RequestMessage,
    ResponseType: ResponseMessage
> {
    internal typealias InterceptorContextType = DefaultInterceptorContext<
        InputType, OutputType, RequestType, ResponseType
    >

    private let interceptors: Interceptors<InputType, OutputType, RequestType, ResponseType>
    private let attributes: Context
    private let serialize: (InputType, RequestType.RequestBuilderType, Context) throws -> Void
    private let deserialize: (ResponseType, Context) async throws -> OutputType
    private let retryStrategy: (any RetryStrategy)?
    private let retryErrorInfoProvider: (Error) -> RetryErrorInfo?
    private let telemetry: OrchestratorTelemetry
    private let selectAuthScheme: SelectAuthScheme
    private let applyEndpoint: any ApplyEndpoint<RequestType>
    private let applySigner: any ApplySigner<RequestType>
    private let executeRequest: any ExecuteRequest<RequestType, ResponseType>

    internal init(builder: OrchestratorBuilder<InputType, OutputType, RequestType, ResponseType>) {
        self.interceptors = builder.interceptors
        self.attributes = builder.attributes
        self.serialize = builder.serialize
        self.deserialize = builder.deserialize!
        self.retryStrategy = builder.retryStrategy
        self.telemetry = builder.telemetry!

        if let retryErrorInfoProvider = builder.retryErrorInfoProvider {
            self.retryErrorInfoProvider = retryErrorInfoProvider
        } else {
            self.retryErrorInfoProvider = { _ in nil }
        }

        if let selectAuthScheme = builder.selectAuthScheme {
            self.selectAuthScheme = selectAuthScheme
        } else {
            self.selectAuthScheme = WrappedSelectAuthScheme { _ in nil }
        }

        if let applyEndpoint = builder.applyEndpoint {
            self.applyEndpoint = applyEndpoint
        } else {
            self.applyEndpoint = WrappedApplyEndpoint { request, _, _ in request }
        }

        if let applySigner = builder.applySigner {
            self.applySigner = applySigner
        } else {
            self.applySigner = WrappedApplySigner { request, _, _ in request }
        }

        self.executeRequest = builder.executeRequest!
    }

    /// Executes the operation until just before the request would be sent, and returns the request.
    ///
    /// Unlike execute, there's no specific error behavior here. Errors are thrown from where they
    /// occur (although interceptors still throw the last error, and log the rest).
    ///
    /// - Parameter input: Operation input
    /// - Returns: Presigned request
    public func presignRequest(input: InputType) async throws -> RequestType {
        let context = DefaultInterceptorContext<InputType, OutputType, RequestType, ResponseType>(
            input: input,
            attributes: attributes
        )

        try await interceptors.readBeforeExecution(context: context)
        try await interceptors.modifyBeforeSerialization(context: context)
        try await interceptors.readBeforeSerialization(context: context)

        let finalizedInput = context.getInput()
        let builder = RequestType.RequestBuilderType()
        try serialize(finalizedInput, builder, context.getAttributes())
        context.updateRequest(updated: builder.build())

        try await interceptors.readAfterSerialization(context: context)
        try await interceptors.modifyBeforeRetryLoop(context: context)
        try await interceptors.readBeforeAttempt(context: context)

        let selectedAuthScheme = try await selectAuthScheme.select(attributes: context.getAttributes())
        let withEndpoint = try await applyEndpoint.apply(
            request: context.getRequest(),
            selectedAuthScheme: selectedAuthScheme,
            attributes: context.getAttributes()
        )
        context.updateRequest(updated: withEndpoint)

        try await interceptors.modifyBeforeSigning(context: context)
        try await interceptors.readBeforeSigning(context: context)

        let signed = try await applySigner.apply(
            request: context.getRequest(),
            selectedAuthScheme: selectedAuthScheme,
            attributes: context.getAttributes()
        )
        context.updateRequest(updated: signed)

        try await interceptors.readAfterSigning(context: context)
        try await interceptors.modifyBeforeTransmit(context: context)
        try await interceptors.readBeforeTransmit(context: context)

        return context.getRequest()
    }

    /// Executes the operation.
    ///
    /// - Parameter input: Operation input
    /// - Returns: Operation output
    public func execute(input: InputType) async throws -> OutputType {
        let telemetryContext = telemetry.contextManager.current()
        let tracer = telemetry.tracerProvider.getTracer(
            scope: telemetry.tracerScope,
            attributes: telemetry.tracerAttributes)

        // DURATION - smithy.client.call.duration
        do {
            let span = tracer.createSpan(
                name: telemetry.spanName,
                initialAttributes: telemetry.spanAttributes,
                spanKind: SpanKind.internal,
                parentContext: telemetryContext)
            let callStart = Date().timeIntervalSinceReferenceDate
            defer {
                telemetry.rpcCallDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - callStart,
                    attributes: telemetry.metricsAttributes,
                    context: telemetryContext)
                span.end()
            }
            let context = DefaultInterceptorContext<InputType, OutputType, RequestType, ResponseType>(
                input: input,
                attributes: attributes
            )

            do {
                try await interceptors.readBeforeExecution(context: context)
                try await interceptors.modifyBeforeSerialization(context: context)
                try await interceptors.readBeforeSerialization(context: context)

                let finalizedInput = context.getInput()
                let builder = RequestType.RequestBuilderType()

                // START - smithy.client.call.serialization_duration
                let serializeStart = Date().timeIntervalSinceReferenceDate
                try serialize(finalizedInput, builder, context.getAttributes())
                telemetry.serializationDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - serializeStart,
                    attributes: telemetry.metricsAttributes,
                    context: telemetryContext)
                // END - smithy.client.call.serialization_duration
                context.updateRequest(updated: builder.build())

                try await interceptors.readAfterSerialization(context: context)
                try await interceptors.modifyBeforeRetryLoop(context: context)

                // Skip retries if a strategy wasn't provided
                if let retryStrategy = self.retryStrategy {
                    try await enterRetryLoop(context: context, strategy: retryStrategy)
                } else {
                    await attempt(context: context, attemptCount: 1)
                }
            } catch let error {
                context.setResult(result: .failure(error))
            }

            return try await startCompletion(context: context)
        }
    }

    private func enterRetryLoop(context: InterceptorContextType, strategy: some RetryStrategy) async throws {
        let partitionId = try getPartitionId(context: context)
        let token = try await strategy.acquireInitialRetryToken(tokenScope: partitionId)
        await startAttempt(context: context, strategy: strategy, token: token, attemptCount: 1)
    }

    private func getPartitionId(context: InterceptorContextType) throws -> String {
        if let customId = context.getAttributes().partitionID, !customId.isEmpty {
            return customId
        } else if !context.getRequest().host.isEmpty {
            return context.getRequest().host
        } else {
            throw ClientError.unknownError("Retry partition ID could not be determined")
        }
    }

    private func startAttempt<S: RetryStrategy>(
        context: InterceptorContextType,
        strategy: S,
        token: S.Token,
        attemptCount: Int
    ) async {
        let copiedRequest = context.getRequest().toBuilder().build()

        let logger = SwiftLogger(label: "startAttemptLogger")
        logger.info("STARTING ATTEMPT")
        await attempt(context: context, attemptCount: attemptCount)
        logger.info("ENDING ATTEMPT")

        do {
            logger.info("ABOUT TO CALL getOutput() from START ATTEMPT")
            _ = try context.getOutput()
            await strategy.recordSuccess(token: token)
        } catch let error {
            logger.info("ERROR CAUGHT HERE BEFORE RETRY ERROR INFO \(error)")
            logger.info("RETRY PROVIDER: \(retryErrorInfoProvider)")
            // If we can't get errorInfo, we definitely can't retry
            guard let errorInfo = retryErrorInfoProvider(error) else { return }

            logger.info("ERROR INFO: \(errorInfo)")

            // Check if error is CRTError with code 2087
            let skipReadyBodyCheck: Bool
            if case CommonRunTimeError.crtError(let crtError) = error, crtError.code == 2087 {
                skipReadyBodyCheck = true
            } else {
                skipReadyBodyCheck = false
            }
            logger.info("SKIP BODY CHECK: \(skipReadyBodyCheck)")

            if !skipReadyBodyCheck {
                // If the body is a nonseekable stream, we also can't retry
                do {
                    guard try readyBodyForRetry(request: copiedRequest) else { return }
                } catch {
                    logger.info("Body is a nonseekable stream, can't retry")
                    return
                }
            } else {
                logger.info("SKIPPING READY BODY FOR RETRY")
            }

            // When refreshing fails it throws, indicating we're done retrying
            do {
                try await strategy.refreshRetryTokenForRetry(tokenToRenew: token, errorInfo: errorInfo)
            } catch {
                logger.info("[retries] refresh failed")
                return
            }

            logger.info("RETRYING, starting attempt for real")
            // clear previous error result
            context.setResult(result: nil)
            context.updateRequest(updated: copiedRequest)
            await startAttempt(context: context, strategy: strategy, token: token, attemptCount: attemptCount + 1)
        }
    }

    /// Readies the body for retry, and indicates whether the request body may be safely used in a retry.
    /// - Parameter request: The request to be retried.
    /// - Returns: `true` if the body of the request is safe to retry, `false` otherwise.  In general, a request body is retriable if it is not a stream, or
    ///   if the stream is seekable and successfully seeks to the start position / offset zero.
    private func readyBodyForRetry(request: RequestType) throws -> Bool {
        let logger = SwiftLogger(label: "readyBodyForRetryLogger")
        logger.info("ReadyBodyForRetry CALLED \(request.body)")
        switch request.body {
        case .stream(let stream):
            logger.info("SEEKABLE? \(stream.isSeekable)")
            guard stream.isSeekable else { return false }
            do {
                try stream.seek(toOffset: 0)
                return true
            } catch {
                return false
            }
        case .data, .noStream:
            return true
        }
    }

    private func attempt(context: InterceptorContextType, attemptCount: Int) async {
        // If anything in here fails, the attempt short-circuits and we go to modifyBeforeAttemptCompletion,
        // with the thrown error in context.result
        let telemetryContext = telemetry.contextManager.current()
        let tracer = telemetry.tracerProvider.getTracer(
            scope: telemetry.tracerScope,
            attributes: telemetry.tracerAttributes)

        // TICK - smithy.client.call.attempts
        telemetry.rpcAttempts.add(
            value: 1,
            attributes: telemetry.metricsAttributes,
            context: telemetryContext)

        // DURATION - smithy.client.call.attempt_duration
        do {
            let span = tracer.createSpan(
                name: "Attempt-\(attemptCount)",
                initialAttributes: telemetry.spanAttributes,
                spanKind: SpanKind.internal,
                parentContext: telemetryContext)
            let attemptStart = Date().timeIntervalSinceReferenceDate
            defer {
                telemetry.rpcAttemptDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - attemptStart,
                    attributes: telemetry.metricsAttributes,
                    context: telemetryContext)
                span.end()
            }
            do {
                try await interceptors.readBeforeAttempt(context: context)

                // START - smithy.client.call.auth.resolve_identity_duration
                let identityStart = Date().timeIntervalSinceReferenceDate
                let selectedAuthScheme = try await selectAuthScheme.select(attributes: context.getAttributes())
                if selectedAuthScheme == nil {
                    throw ClientError.authError("auth scheme could not be selected")
                }
                var authSchemeAttributes = telemetry.metricsAttributes
                authSchemeAttributes.set(
                    key: AttributeKey<String>(name: "auth.scheme_id"),
                    value: selectedAuthScheme!.schemeID)
                telemetry.resolveIdentityDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - identityStart,
                    attributes: authSchemeAttributes,
                    context: telemetryContext)
                // END - smithy.client.call.auth.resolve_identity_duration

                // START - smithy.client.call.resolve_endpoint_duration
                let endpointStart = Date().timeIntervalSinceReferenceDate
                let withEndpoint = try await applyEndpoint.apply(
                    request: context.getRequest(),
                    selectedAuthScheme: selectedAuthScheme,
                    attributes: context.getAttributes()
                )
                telemetry.resolveEndpointDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - endpointStart,
                    attributes: telemetry.metricsAttributes,
                    context: telemetryContext)
                // END - smithy.client.call.resolve_endpoint_duration

                context.updateRequest(updated: withEndpoint)

                try await interceptors.modifyBeforeSigning(context: context)
                try await interceptors.readBeforeSigning(context: context)

                // START - smithy.client.call.auth.signing_duration
                let signingStart = Date().timeIntervalSinceReferenceDate
                let signed = try await applySigner.apply(
                    request: context.getRequest(),
                    selectedAuthScheme: selectedAuthScheme,
                    attributes: context.getAttributes()
                )
                telemetry.signingDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - signingStart,
                    attributes: authSchemeAttributes,
                    context: telemetryContext)
                // END - smithy.client.call.auth.signing_duration

                context.updateRequest(updated: signed)

                try await interceptors.readAfterSigning(context: context)
                try await interceptors.modifyBeforeTransmit(context: context)
                try await interceptors.readBeforeTransmit(context: context)

                let logger = SwiftLogger(label: "attemptLogger")
                logger.info("ABOUT TO EXECUTE")
                let response = try await executeRequest.execute(
                    request: context.getRequest(),
                    attributes: context.getAttributes()
                )
                logger.info("EXECUTION COMPLETE")
                context.updateResponse(updated: response)

                try await interceptors.readAfterTransmit(context: context)
                try await interceptors.modifyBeforeDeserialization(context: context)
                try await interceptors.readBeforeDeserialization(context: context)

                // START - smithy.client.call.deserialization_duration
                let deserializeStart = Date().timeIntervalSinceReferenceDate
                let output = try await deserialize(context.getResponse(), context.getAttributes())
                telemetry.deserializationDuration.record(
                    value: Date().timeIntervalSinceReferenceDate - deserializeStart,
                    attributes: telemetry.metricsAttributes,
                    context: telemetryContext)
                // END - smithy.client.call.deserialization_duration
                context.updateOutput(updated: output)

                try await interceptors.readAfterDeserialization(context: context)
            } catch let error {
                let logger = SwiftLogger(label: "caught attemptLogger")
                logger.info("ERROR WAS CAUGHT IN ATTEMPT \(error)")
                context.setResult(result: .failure(error))
            }

            // If modifyBeforeAttemptCompletion fails, we still want to let readAfterAttempt run
            do {
                try await interceptors.modifyBeforeAttemptCompletion(context: context)
            } catch let error {
                context.setResult(result: .failure(error))
            }
            do {
                try await interceptors.readAfterAttempt(context: context)
            } catch let error {
                context.setResult(result: .failure(error))
            }
        }
    }

    private func startCompletion(context: InterceptorContextType) async throws -> OutputType {
        // If modifyBeforeCompletion fails, we still want to let readAfterExecution run
        do {
            try await interceptors.modifyBeforeCompletion(context: context)
        } catch let error {
            context.setResult(result: .failure(error))
        }
        do {
            try await interceptors.readAfterExecution(context: context)
        } catch let error {
            context.setResult(result: .failure(error))
        }
        let logger = SwiftLogger(label: "StartCompletionLogger")


        // The last error that occurred, if any, is thrown
        do {
            logger.info("ABOUT TO CALL getOutput() from START COMPLETION")
            return try context.getOutput()
        } catch {
            // TICK - smithy.client.call.errors
            telemetry.rpcErrors.add(
                value: 1,
                attributes: telemetry.metricsAttributes,
                context: telemetry.contextManager.current())
            logger.info("ERROR WAS CAUGHT IN START COMPLETION \(error)")
            throw error
        }
    }
}
