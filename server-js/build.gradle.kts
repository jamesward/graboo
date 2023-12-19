plugins {
    alias(universe.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
        applyBinaryen()
    }
}
