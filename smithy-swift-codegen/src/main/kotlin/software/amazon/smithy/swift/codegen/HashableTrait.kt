package software.amazon.smithy.swift.codegen

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.Trait

class HashableTrait : Trait {
    val ID = ShapeId.from("software.amazon.smithy.swift.codegen.swift.synthetic#hashable")
    override fun toNode(): Node = Node.objectNode()

    override fun toShapeId(): ShapeId = ID
}