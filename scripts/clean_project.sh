#!/bin/bash

# Deletes known build products, caches, and temporary storage from the project.
# Useful for reclaiming storage space or before archiving.
#
# Run this script from the root of the project.

rm -rf .gradle
rm -rf .build
rm -rf build
rm -rf smithy-swift-codegen/build
