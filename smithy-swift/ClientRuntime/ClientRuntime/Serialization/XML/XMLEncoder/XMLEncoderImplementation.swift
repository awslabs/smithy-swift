//
//  XMLEncoderImplementation.swift
//  ClientRuntime
//
// TODO:: Add copyrights

class XMLEncoderImplementation: Encoder {
    // MARK: Properties

    /// The encoder's storage.
    var storage: XMLEncodingStorage

    /// Options set on the top-level encoder.
    let options: XMLEncoderOptions

    /// The path to the current point in encoding.
    public var codingPath: [CodingKey]

    public var nodeEncodings: [(CodingKey) -> XMLEncoder.NodeEncoding]

    /// Contextual user-provided information for use during encoding.
    public var userInfo: [CodingUserInfoKey: Any] {
        return options.userInfo
    }

    // MARK: - Initialization

    /// Initializes `self` with the given top-level encoder options.
    init(
        options: XMLEncoderOptions,
        nodeEncodings: [(CodingKey) -> XMLEncoder.NodeEncoding],
        codingPath: [CodingKey] = []
    ) {
        self.options = options
        storage = XMLEncodingStorage()
        self.codingPath = codingPath
        self.nodeEncodings = nodeEncodings
    }

    /// Returns whether a new element can be encoded at this coding path.
    ///
    /// `true` if an element has not yet been encoded at this coding path; `false` otherwise.
    var canEncodeNewValue: Bool {
        // Every time a new value gets encoded, the key it's encoded for is
        // pushed onto the coding path (even if it's a nil key from an unkeyed container).
        // At the same time, every time a container is requested, a new value
        // gets pushed onto the storage stack.
        // If there are more values on the storage stack than on the coding path,
        // it means the value is requesting more than one container, which
        // violates the precondition.
        //
        // This means that anytime something that can request a new container
        // goes onto the stack, we MUST push a key onto the coding path.
        // Things which will not request containers do not need to have the
        // coding path extended for them (but it doesn't matter if it is,
        // because they will not reach here).
        return storage.count == codingPath.count
    }

    // MARK: - Encoder Methods

    public func container<Key>(keyedBy _: Key.Type) -> KeyedEncodingContainer<Key> {
        guard canEncodeNewValue else {
            return mergeWithExistingKeyedContainer(keyedBy: Key.self)
        }
        return keyedContainer(keyedBy: Key.self)
    }

    public func unkeyedContainer() -> UnkeyedEncodingContainer {
        // If an existing unkeyed container was already requested, return that one.
        let topContainer: XMLSharedContainer<XMLArrayBasedContainer>
        if canEncodeNewValue {
            // We haven't yet pushed a container at this level; do so here.
            topContainer = storage.pushUnkeyedContainer()
        } else {
            guard let container = storage.lastContainer as? XMLSharedContainer<XMLArrayBasedContainer> else {
                preconditionFailure(
                    """
                    Attempt to push new unkeyed encoding container when already previously encoded \
                    at this path.
                    """
                )
            }

            topContainer = container
        }

        return XMLUnkeyedEncodingContainer(referencing: self, codingPath: codingPath, wrapping: topContainer)
    }

    public func singleValueContainer() -> SingleValueEncodingContainer {
        return self
    }

    private func keyedContainer<Key>(keyedBy _: Key.Type) -> KeyedEncodingContainer<Key> {
        let container = XMLKeyedEncodingContainer<Key>(
            referencing: self,
            codingPath: codingPath,
            wrapping: storage.pushKeyedContainer()
        )
        return KeyedEncodingContainer(container)
    }

    private func mergeWithExistingKeyedContainer<Key>(keyedBy _: Key.Type) -> KeyedEncodingContainer<Key> {
        switch storage.lastContainer {
        case let keyed as XMLSharedContainer<XMLKeyBasedContainer>:
            let container = XMLKeyedEncodingContainer<Key>(
                referencing: self,
                codingPath: codingPath,
                wrapping: keyed
            )
            return KeyedEncodingContainer(container)
        default:
            preconditionFailure(
                """
                No existing keyed encoding container to merge with.
                """
            )
        }
    }
}

extension XMLEncoderImplementation {
    /// Returns the given value boxed in a container appropriate for pushing onto the container stack.
    func addToXMLContainer() -> XMLSimpleContainer {
        return XMLNullContainer()
    }

    func addToXMLContainer(_ value: Bool) -> XMLSimpleContainer {
        return XMLBoolContainer(value)
    }

    func addToXMLContainer<T: BinaryInteger & SignedInteger & Encodable>(_ value: T) -> XMLSimpleContainer {
        return XMLIntContainer(value)
    }

    func addToXMLContainer(_ value: Float) throws -> XMLSimpleContainer {
        return try addToXMLContainer(value, XMLFloatContainer.self)
    }

    func addToXMLContainer<T: BinaryFloatingPoint & Encodable, B: XMLValueContainer>(
        _ value: T,
        _: B.Type
    ) throws -> XMLSimpleContainer where B.Unboxed == T {
        guard value.isInfinite || value.isNaN else {
            return B(value)
        }
        guard case let .convertToString(
            positiveInfinity: posInfString,
            negativeInfinity: negInfString,
            nan: nanString
        ) = options.nonConformingFloatEncodingStrategy else {
            throw EncodingError._invalidFloatingPointValue(value, at: codingPath)
        }
        if value == T.infinity {
            return XMLStringContainer(posInfString)
        } else if value == -T.infinity {
            return XMLStringContainer(negInfString)
        } else {
            return XMLStringContainer(nanString)
        }
    }

    func addToXMLContainer(_ value: String) -> XMLSimpleContainer {
        return XMLStringContainer(value)
    }

    func addToXMLContainer(_ value: Date) throws -> XMLContainer {
        switch options.dateEncodingStrategy {
        case .deferredToDate:
            try value.encode(to: self)
            return storage.popContainer()
        case .secondsSince1970:
            return XMLDateContainer(value, format: .secondsSince1970)
        case .millisecondsSince1970:
            return XMLDateContainer(value, format: .millisecondsSince1970)
        case .iso8601:
            return XMLDateContainer(value, format: .iso8601)
        case let .formatted(formatter):
            return XMLDateContainer(value, format: .formatter(formatter))
        case let .custom(closure):
            let depth = storage.count
            try closure(value, self)

            guard storage.count > depth else {
                return XMLKeyBasedContainer()
            }

            return storage.popContainer()
        }
    }

    func addToXMLContainer(_ value: URL) -> XMLSimpleContainer {
        return XMLURLContainer(value)
    }

//    func box(_ value: Data) throws -> XMLContainer {
//        switch options.dataEncodingStrategy {
//        case .deferredToData:
//            try value.encode(to: self)
//            return storage.popContainer()
//        case .base64:
//            return DataBox(value, format: .base64)
//        case let .custom(closure):
//            let depth = storage.count
//            try closure(value, self)
//
//            guard storage.count > depth else {
//                return XMLKeyBasedContainer()
//            }
//
//            return storage.popContainer()
//        }
//    }
//
    func addToXMLContainer(_ value: Decimal) -> XMLSimpleContainer {
        return XMLDecimalContainer(value)
    }

    func addToXMLContainer(_ value: Double) throws -> XMLSimpleContainer {
        return try addToXMLContainer(value, XMLDoubleContainer.self)
    }

    func addToXMLContainer<T: BinaryInteger & UnsignedInteger & Encodable>(_ value: T) -> XMLSimpleContainer {
        return XMLUIntContainer(value)
    }

    func addToXMLContainer<T: Encodable>(_ value: T) throws -> XMLContainer {
        if T.self == Date.self || T.self == NSDate.self,
            let value = value as? Date {
            return try addToXMLContainer(value)
        } else if T.self == Data.self || T.self == NSData.self,
            let value = value as? Data {
            return try addToXMLContainer(value)
        } else if T.self == URL.self || T.self == NSURL.self,
            let value = value as? URL {
            return addToXMLContainer(value)
        } else if T.self == Decimal.self || T.self == NSDecimalNumber.self,
            let value = value as? Decimal {
            return try addToXMLContainer(value)
        }

        let depth = storage.count
        try value.encode(to: self)

        // The top container should be a new container.
        guard storage.count > depth else {
            return XMLKeyBasedContainer()
        }

        let lastContainer = storage.popContainer()

        guard let sharedBox = lastContainer as? TypeErasedSharedBoxProtocol else {
            return lastContainer
        }

        return sharedBox.unbox()
    }
}
