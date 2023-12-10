# Graboo

> Like Spring Boot, but for Gradle; i.e. Gradle Boot

A collection of experiments to reduce friction with Gradle:

- [x] JVM-less / Native Gradle Wrapper that is SCM-friendly
- [ ] Project initializer (CLI and Web)
- [x] Out-of-the box typed dependency references via [Universe Version Catalog](https://github.com/jamesward/kotlin-universe-catalog)
- [x] Declarative-like, Developer-oriented Build Definitions (the common things shouldn't need to be specified)
    Example:
    ```kotlin
    boot.springApp {
        jvmVersion = 17
        kotlin = true

        dependencies {
            implementation(universe.spring.boot.starter.webflux)
        }
    }
    ```
- [ ] CLI-based common tasks like adding dependencies, refactoring from single-module to multi-module projects
    Example:
    ```bash
    ./graboo dep ktor-client
    ```
- [ ] Interactive build shell
- [ ] Auto main discovery

## Get Started

### New Project

TODO

### Existing Project

TODO

