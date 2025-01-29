package software.amazon.smithy.swift.codegen.model

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait
import software.amazon.smithy.swift.codegen.customtraits.TestEquatableConformanceTrait

object TestEquatableConformanceTransformer {

    fun transform(model: Model, service: ServiceShape): Model {
        // Both operation shapes and error shapes may have the HttpResponseTestsTrait.
        // Find both types of shape with that trait for this service.
        val errorsWithResponseTests = model.getNestedErrors(service)
            .filter { it.hasTrait<HttpResponseTestsTrait>() }
        val outputsWithResponseTests = service.allOperations
            .map { model.expectShape(it) as OperationShape }
            .filter { it.hasTrait<HttpResponseTestsTrait>() }
            .map { model.expectShape(it.outputShape) as StructureShape }

        // Get all the shapes that need to have Equatable conformance added for testing,
        // and add them to the list
        var needsTestEquatableConformance = mutableSetOf<ShapeId>()
        (errorsWithResponseTests + outputsWithResponseTests).forEach {
            addToList(model, it, needsTestEquatableConformance)
        }

        // Transform the model by adding the TestEquatableConformanceTrait to shapes that need it
        // In a later SwiftIntegration, the conformance will be code-generated
        return ModelTransformer.create().mapShapes(model) { shape ->
            if (needsTestEquatableConformance.contains(shape.id) && shape.id.namespace != "smithy.api") {
                // If the shape is a structure or union, add the TestEquatableConformanceTrait to it
                // All other shape types don't need to have Equatable generated for them
                when (shape.type) {
                    ShapeType.STRUCTURE -> shape.asStructureShape().get().toBuilder().addTrait(
                        TestEquatableConformanceTrait()
                    ).build()
                    ShapeType.UNION -> shape.asUnionShape().get().toBuilder().addTrait(TestEquatableConformanceTrait())
                        .build()
                    else -> shape
                }
            } else {
                // If the shape doesn't need test equatable conformance, leave it unchanged
                shape
            }
        }
    }

    // Adds the ShapeIds for the passed structure and all of its nested structures or unions
    // to the passed list.
    private fun addToList(model: Model, shape: StructureShape, list: MutableSet<ShapeId>) {
        list.add(shape.id)
        model.getNestedShapes(shape).forEach { nested ->
            if (nested.isStructureShape || nested.isUnionShape) {
                list.add(nested.id)
            }
        }
    }
}
