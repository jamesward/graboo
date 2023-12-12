plugins {
    alias(universe.plugins.kotlin.multiplatform) apply false
    alias(universe.plugins.kotlin.plugin.serialization) apply false
    alias(universe.plugins.qoomon.git.versioning)
}

gitVersioning.apply {
    refs {
        tag("v(?<version>.*)") {
            version = "\${ref.version}"
        }
    }

    rev {
        version = "\${commit}"
    }
}