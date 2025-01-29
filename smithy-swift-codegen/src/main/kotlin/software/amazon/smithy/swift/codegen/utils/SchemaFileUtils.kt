package software.amazon.smithy.swift.codegen.utils

import software.amazon.smithy.swift.codegen.SwiftSettings

class SchemaFileUtils {

    companion object {
        fun filename(settings: SwiftSettings, filename: String): String {
            return if (settings.mergeModels) {
                "Sources/${settings.moduleName}/Schemas.swift"
            } else {
                "Sources/${settings.moduleName}/schemas/$filename.swift"
            }
        }
    }
}
