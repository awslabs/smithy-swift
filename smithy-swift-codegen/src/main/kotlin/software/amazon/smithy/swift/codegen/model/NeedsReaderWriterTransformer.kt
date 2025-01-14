/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.model

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.swift.codegen.customtraits.NeedsReaderTrait
import software.amazon.smithy.swift.codegen.customtraits.NeedsWriterTrait

object NeedsReaderWriterTransformer {
    fun transform(model: Model, service: ServiceShape): Model {
        var modelToReturn = model

        // Get all the input shapes, and tag their descendents with the NeedsWriterTrait
        val inputShapes = model.getOperations(service).map { model.expectShape<StructureShape>(it.inputShape) }
        inputShapes.forEach { modelToReturn = transform(modelToReturn, it, NeedsWriterTrait()) }

        // Get all the output & error shapes, and tag their descendents with the NeedsReaderTrait
        val outputShapes = model.getOperations(service).map { model.expectShape<StructureShape>(it.outputShape) }
        val errorShapes = model.getErrorShapes(service)
        (outputShapes + errorShapes).forEach { modelToReturn = transform(modelToReturn, it, NeedsReaderTrait()) }

        return modelToReturn
    }

    private fun transform(model: Model, structure: StructureShape, trait: Trait): Model {
        // Get all shapes nested under the passed trait
        var allShapesNeedingTrait = model.getNestedShapes(structure)
            .filter { it.id.namespace != "smithy.api" }
            .filter { !it.hasTrait(trait.toShapeId()) }
        if (allShapesNeedingTrait.isEmpty()) { return model }

        // If it's a struct or union, tag it with the trait.  Else leave it unchanged.
        return ModelTransformer.create().mapShapes(model) { shape ->
            if (allShapesNeedingTrait.contains(shape)) {
                when (shape.type) {
                    ShapeType.STRUCTURE -> shape.asStructureShape().get().toBuilder().addTrait(trait).build()
                    ShapeType.UNION -> shape.asUnionShape().get().toBuilder().addTrait(trait).build()
                    else -> shape
                }
            } else {
                shape
            }
        }
    }
}
