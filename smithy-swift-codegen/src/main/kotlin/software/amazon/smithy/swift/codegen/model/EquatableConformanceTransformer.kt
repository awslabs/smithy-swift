package software.amazon.smithy.swift.codegen.model

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.PaginatedIndex
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.swift.codegen.customtraits.EquatableConformanceTrait

/*
 * This transformer is used to add @equatableConformance custom trait to
 * Smithy structure shapes and Smithy union shapes only when necessary, which atm is for pagination tokens.
 *
 * E.g., if an operation uses pagination, then it has a corresponding @paginated trait with
 * inputToken and outputToken fields that specify names of member shapes. Because the SDK uses equivalence checking
 * on those tokens during pagination, all structure and union shapes nested under that member shape must have
 * Equatable conformance in codegen output. Swift counterparts of Smithy simple types conform to Equatable by default.
 *
 * Minimizing protocol conformance has significant benefits for compile time and binary size of the SDK.
 */
object EquatableConformanceTransformer {
    fun transform(model: Model, service: ServiceShape): Model {
        val paginationTokenMembers = mutableSetOf<MemberShape>()
        val paginatedIndex = PaginatedIndex.of(model)
        // Collect all member shapes used as inputToken or outputToken for paginated operations.
        for (op in service.allOperations) {
            val paginationInfo = paginatedIndex.getPaginationInfo(service.id, op.toShapeId())
            paginationInfo.ifPresent {
                paginationTokenMembers.add(it.inputTokenMember)
                paginationTokenMembers.addAll(it.outputTokenMemberPath)
            }
        }
        // Get all shapes nested within member shapes used as pagination tokens.
        val shapesToAddEquatableConformanceTraitTo = mutableSetOf<Shape>()
        for (tokenMemberShape in paginationTokenMembers) {
            val nestedShapes = model.getNestedShapes(tokenMemberShape).filter { it.id.namespace != "smithy.api" }
            shapesToAddEquatableConformanceTraitTo.addAll(nestedShapes)
        }
        // Add @equatableConformance custom trait to all structure and union shapes
        // ...nested under the token member shapes.
        return ModelTransformer.create().mapShapes(model) { shape ->
            if (shapesToAddEquatableConformanceTraitTo.contains(shape)) {
                when (shape.type) {
                    ShapeType.STRUCTURE -> shape.asStructureShape().get().toBuilder().addTrait(EquatableConformanceTrait()).build()
                    ShapeType.UNION -> shape.asUnionShape().get().toBuilder().addTrait(EquatableConformanceTrait()).build()
                    else -> shape
                }
            } else {
                shape
            }
        }
    }
}
