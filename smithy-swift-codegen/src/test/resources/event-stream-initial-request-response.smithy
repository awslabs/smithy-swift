namespace com.test

use aws.protocols#awsJson1_1
use aws.api#service
use aws.auth#sigv4

@awsJson1_1
@sigv4(name: "event-stream-test")
@service(sdkId: "InitialMessageEventStreams")
service Example {
    version: "123",
    operations: [TestStreamOperationWithInitialRequestResponse]
}

operation TestStreamOperationWithInitialRequestResponse {
    input: TestStreamInputOutputInitialRequestResponse,
    output: TestStreamInputOutputInitialRequestResponse,
    errors: [SomeError],
}

structure TestStreamInputOutputInitialRequestResponse {
    @required
    value: TestStream
    initial1: String
    initial2: String
}

@error("client")
structure SomeError {
    Message: String,
}

structure MessageWithString { @eventPayload data: String }

@streaming
union TestStream {
    MessageWithString: MessageWithString,
    SomeError: SomeError,
}