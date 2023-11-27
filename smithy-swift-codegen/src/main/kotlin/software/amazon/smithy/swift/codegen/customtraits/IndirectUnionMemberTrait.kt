package software.amazon.smithy.swift.codegen.customtraits

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.Trait

class IndirectUnionMemberTrait: Trait {
    val ID = ShapeId.from("software.amazon.smithy.swift.codegen.swift.synthetic#IndirectUnionMember")
    override fun toNode(): Node = Node.objectNode()

    override fun toShapeId(): ShapeId = ID
}