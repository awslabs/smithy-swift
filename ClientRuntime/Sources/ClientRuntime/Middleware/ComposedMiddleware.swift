// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0.

// used to create middleware from a handler
struct ComposedMiddleware<MInput, MOutput> {
    // the handler to call in the middleware
    let handler: AnyHandler<MInput, MOutput>
    public var id: String
    
    public init<H: Handler> (_ handler: H, id: String)
           where H.Input == MInput, H.Output == MOutput {
        
        self.handler = handler.eraseToAnyHandler()
        self.id = id
    }
}

extension ComposedMiddleware: Middleware {
 
    func handle<H: Handler>(context: MiddlewareContext, result: Result<MInput, Error>, next: H) -> Result<MOutput, Error> where
        H.Input == MInput, H.Output == MOutput {
        let newResult = handler.handle(context: context, result: result)
        let mappedResult = newResult.map { (output) -> MInput in
            (output as! MInput)
        }
        return next.handle(context: context, result: mappedResult)
    }
}
