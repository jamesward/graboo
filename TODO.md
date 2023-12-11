# Graboo TODO

## Infra
- Currently all plugins are on the classpath and this requires the google repo to be configured for pluginManagement
  - The settings plugin can't have deps on plugins in non-default repos
  - It'd be better if the classpath were more granular and the pluginManagement could be handled by the settings plugin
  - So maybe go back to project plugins for each type of model
- Deploy on Maven Central too?
- This build to gradleboot
- end-to-end test: graboo (shell script) -> graboo (native) -> graboo (jvm)
  - Use `graboo new` instead of examples
- Maybe some way for the publish-executables job to get the latest from git since it is updated in publish-plugin

## DX
- Project initialization
  - `graboo new`
  - graboo.dev
- Gradle Usage
  - Auto-main plugin
    - Discover main
    - Multi-main like sbt
    - Multi-target: ie `run` with just `jvm` target works; `run` with multiple targets provides selector
  - Build shell plugin
    - Continuous build, reload on build changes, etc
  - `graboo` native launcher
    - How much do we put in the launcher? Wrapper is built-in. Settings plugin? Can we put gradle.properties defaults in?
    - https://github.com/jart/cosmopolitan for αcτµαlly pδrταblε εxεcµταblε
      - Option 1) Thin APE Wrapper that downloads JDK & Graboo
      - Option 2) Kotlin/Native APE Binary (may be too large for git repo use / not sure about upx support)
- two-way tooling
  - Compile kts and load the class instead of parse?
  - Add dependency
    - `./graboo dep ktor-client`
    - KMP aware
    - Optional sample code?
    - Generalized or not?
    - Version catalogs
      - Assuming GradleBoot has built-in Universe Version Catalogs, the deps should use them, ie `universe.ktor.client` as the dep
    - Test vs Main?
    - Multiple modules?
      - `./graboo dep androidApp:ktor-client` or `./graboo dep androidApp:shared`
  - Add module
    - `./graboo mod androidApp`
    - If one module, refactor to multi-module
  - Add target
    - `./graboo target wasm`
- Other plugins
  - What happens? kotlinx.serialization, ktlint, ksp, etc
- Multi-module
  - Automatic submodules `*/build.gradle.kts`
  - No top-level `build.gradle.kts` ?

## Model
- Plugin internal deps externalized versions
  - Can we use version catalogs?
  - Is bumping internal versions only via bumping the gradleboot plugin?
- `boot.kotlinApp {` -> `kotlinApp`
  - `kotlinApp {` uses the generated accessor `Project.kotlinApp(Action<KotlinAppExtension>)`
  - Which doesn't get configured until it is too late to hook in
  - Tried `KotlinAppExtension.invoke` but the compiler picks the `Action` accessor over the `get`
  - Investigated using reflection to remove the `Action` accessor
- slf4j with ktor ?
- `springApp`
  - `native = true`
    - Needs better toolchain support: https://github.com/graalvm/native-build-tools/issues/357
    - Needs Kotlin 1.9 GraalVM support: https://youtrack.jetbrains.com/issue/KT-60211
- `kotlinLib`
- `androidApp`
  - JVM Compose testing as a default instead of device testing?
- `androidLib`
- `javaApp`
- `javaLib`
- Dependencies:
  - `dependencies` & `testDependencies` instead of `dependencies { implementation() }` ? Or some other default scope?
- Note: `*Lib` need signing, publishing, docs, etc

## Archetypes, Platforms, 