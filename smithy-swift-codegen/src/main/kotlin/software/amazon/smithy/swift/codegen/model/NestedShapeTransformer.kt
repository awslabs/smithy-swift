/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.model

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.swift.codegen.customtraits.NestedTrait

object NestedShapeTransformer {
    fun transform(model: Model, service: ServiceShape): Model {
        val next = transformInner(model, service)
        if (next == model) {
            throw CodegenException("model $model is equal to $next, loop detected")
        }
        return if (next == null) {
            model
        } else {
            transform(next, service)
        }
    }

    private fun transformInner(model: Model, service: ServiceShape): Model? {
        // find all the shapes in this models shapes that have are nested shapes (not a top level input or output)
        val allShapesNeedingNested = model.getNestedShapes(service).filter { !it.hasTrait<NestedTrait>() && it.id.namespace != "smithy.api" }

        if (allShapesNeedingNested.isEmpty()) {
            return null
        }

        return ModelTransformer.create().mapShapes(model) { shape ->
            if (allShapesNeedingNested.contains(shape)) {
                when (shape.type) {
                    ShapeType.STRUCTURE -> shape.asStructureShape().get().toBuilder().addTrait(NestedTrait()).build()
                    ShapeType.UNION -> shape.asUnionShape().get().toBuilder().addTrait(NestedTrait()).build()
                    ShapeType.STRING -> shape.asStringShape().get().toBuilder().addTrait(NestedTrait()).build()
                    ShapeType.ENUM -> shape.asEnumShape().get().toBuilder().addTrait(NestedTrait()).build()
                    else -> shape
                }
            } else {
                shape
            }
        }
    }
}
