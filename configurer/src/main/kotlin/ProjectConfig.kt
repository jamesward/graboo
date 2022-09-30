import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.appendText
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

object ProjectConfig {

    // note: using manual parsing because Properties is a Map<String, Any>
    private val importmap: Map<String, String> = javaClass.getResourceAsStream("importmap.properties")!!.bufferedReader().use { reader ->
        reader.readLines().fold(emptyMap()) { existing, line ->
            try {
                val (k, v) = line.split('=')
                existing + (k to v)
            }
            catch (_: Exception) {
                existing
            }
        }
    }

    @JvmInline
    value class ProjectDir(val path: Path)

    @JvmInline
    value class BuildFile(val path: Path)
    fun ProjectDir.buildFile(): BuildFile = BuildFile(path / "build.gradle.kts")

    fun hasPlugin(buildFile: BuildFile, plugin: String): Boolean = run {
        buildFile.path.readLines().any {
            // todo: check in plugins block
            it.contains(plugin)
        }
    }

    enum class SourceLanguage(val dirName: String) {
        JAVA("java"), KOTLIN("kotlin"), RESOURCES("resources")
    }

    enum class SourceType(val dirName: String) {
        MAIN("main"), TEST("test")
    }

    data class SourceSet(val sourceLanguage: SourceLanguage?, val sourceType: SourceType?, val path: Path)

    data class SourceMain(val sourceSet: SourceSet, val className: String)
    data class SourceImport(val sourceSet: SourceSet, val packageName: String)

    data class SourceConfig(val sourceSets: Set<SourceSet>) {
        val languages: Set<SourceLanguage> = run {
            sourceSets.fold(emptySet()) { languages, sourceSet ->
                if (sourceSet.sourceLanguage != null)
                    languages + sourceSet.sourceLanguage
                else
                    languages
            }
        }

        val hasTest: Boolean = sourceSets.any { it.sourceType == SourceType.TEST }

        val mains: Set<SourceMain> = run {
            sourceSets.flatMap { sourceSet ->
                findMains(sourceSet)
            }.toSet()
        }
    }

    fun findImports(sourceSet: SourceSet): Set<SourceImport> = run {
        if (sourceSet.sourceLanguage == SourceLanguage.JAVA || sourceSet.sourceLanguage == SourceLanguage.KOTLIN) {
            sourceSet.path.toFile().walkBottomUp()
                .filter { it.extension == "java" || it.extension == "kt" }
                .fold(emptySet()) { imports, file ->
                    val newImports = file.readLines().filter { it.startsWith("import ") }.map { line ->
                        val importLine = line.removePrefix("import ").removePrefix("static ").removeSuffix(";")
                        SourceImport(sourceSet, importLine)
                    }.toSet()

                    imports + newImports
                }
        }
        else {
            emptySet()
        }
    }

    // todo: might be better to run the compile first, then find the mains after a successful compile
    fun findMains(sourceSet: SourceSet): Set<SourceMain> = run {
        if (sourceSet.sourceLanguage == SourceLanguage.JAVA || sourceSet.sourceLanguage == SourceLanguage.KOTLIN) {
            sourceSet.path.toFile().walkBottomUp().fold(emptySet()) { mains, file ->
                val maybeMain: SourceMain? = when(file.extension) {
                    "kt" -> {
                        val hasMain = file.readLines().any {
                            it.contains("fun main(")
                        }
                        if (hasMain) {
                            val packageName =
                                file.readLines().find { it.startsWith("package ") }?.removePrefix("package ")
                                    ?.removeSuffix(";")
                            // todo: does not work on nested objects
                            val className = file.name + "Kt"
                            val fullName = if (packageName != null) "$packageName.$className" else className
                            SourceMain(sourceSet, fullName)
                        }
                        else {
                            null
                        }
                    }

                    "java" -> {
                        val hasMain = file.readLines().any {
                            it.contains("public static void main(")
                        }
                        if (hasMain) {
                            val packageName =
                                file.readLines().find { it.startsWith("package ") }?.removePrefix("package ")
                                    ?.removeSuffix(";")
                            val classRegex = """class (\w+) \{""".toRegex()
                            val className = file.readLines().firstNotNullOf { classRegex.find(it)?.groupValues?.getOrNull(1) } // todo: handle null
                            val fullName = if (packageName != null) "$packageName.$className" else className
                            SourceMain(sourceSet, fullName)
                        }
                        else {
                            null
                        }
                    }

                    else -> null
                }

                if (maybeMain == null) {
                    mains
                } else {
                    mains + maybeMain
                }
            }
        }
        else {
            emptySet()
        }
    }

    fun sourceConfig(projectDir: ProjectDir): SourceConfig = run {
        val sourceSets = if ((projectDir.path / "src").exists()) {
            (projectDir.path / "src").listDirectoryEntries().flatMap { sourceTypeDir ->
                sourceTypeDir.listDirectoryEntries().map { sourceLanguageDir ->
                    val sourceType = SourceType.values().find { it.dirName == sourceTypeDir.name }
                    val sourceLanguage = SourceLanguage.values().find { it.dirName == sourceLanguageDir.name }
                    SourceSet(sourceLanguage, sourceType, sourceLanguageDir)
                }
            }.toSet()
        } else {
            emptySet()
        }

        SourceConfig(sourceSets)
    }


    fun createBuild(buildFile: BuildFile): Unit = run {
        buildFile.path.writeText("""

            repositories {
                mavenCentral()
            }
            
        """.trimIndent())
    }

    fun injectPlugin(buildFile: BuildFile, s: String): Unit = run {
        if (!buildFile.path.exists()) {
            createBuild(buildFile)
        }

        val lines = buildFile.path.readLines()

        val pluginsLine = lines.indexOf("plugins {")
        val newLines = if (pluginsLine >= 0) {
            val before = lines.take(pluginsLine + 1)
            val after = lines.drop(pluginsLine + 1)
            before + "    $s" + after
        }
        else {
            listOf("plugins {", "    $s", "}", "") + lines
        }

        buildFile.path.writeLines(newLines)
    }


    fun setupGradle(dir: ProjectDir): Unit = run {
        if (!dir.buildFile().path.exists()) {
            createBuild(dir.buildFile())
        }

        if (!(dir.path / "settings.gradle.kts").exists()) {
            (dir.path / "settings.gradle.kts").writeText("""
                rootProject.name = "${dir.path.absolute().name}"
                
            """.trimIndent())
        }

        if (!(dir.path / "gradle/wrapper/gradle-wrapper.properties").exists()) {
            Gradler.wrapper(dir)
        }
    }

    fun setupSourceSets(dir: ProjectDir, sourceConfig: SourceConfig): Unit = run {

        fun setupJavaToolchain() {
            val alreadyConfigured = dir.buildFile().path.readLines().any {
                it.contains("java {")
            }

            if (!alreadyConfigured) {
                val toolchainConfig = """

                    java {
                        toolchain {
                            languageVersion.set(JavaLanguageVersion.of(17))
                        }
                    }
                    
                """.trimIndent()

                dir.buildFile().path.appendText(toolchainConfig)
            }
        }

        if (sourceConfig.languages.contains(SourceLanguage.KOTLIN)) {
            if (!hasPlugin(dir.buildFile(), """kotlin("jvm")""")) {
                injectPlugin(dir.buildFile(), """kotlin("jvm") version "1.7.20"""")
            }

            setupJavaToolchain()
        }
        else if (sourceConfig.languages.contains(SourceLanguage.JAVA)) {
            if (sourceConfig.mains.isEmpty()) {
                if (!hasPlugin(dir.buildFile(), """`java-library`""")) {
                    injectPlugin(dir.buildFile(), """`java-library`""")
                }
            }
            else {
                if (!hasPlugin(dir.buildFile(), """java""")) {
                    injectPlugin(dir.buildFile(), """java""")
                }
            }

            setupJavaToolchain()
        }


        if (sourceConfig.mains.isNotEmpty()) {
            if (!hasPlugin(dir.buildFile(), """application""")) {
                injectPlugin(dir.buildFile(), """application""")
            }

            if (sourceConfig.mains.size == 1) {
                val alreadyConfigured = dir.buildFile().path.readLines().any {
                    it.contains("application {")
                }

                if (!alreadyConfigured) {
                    val applicationConfig = """
                    
                        application {
                            mainClass.set("${sourceConfig.mains.first().className}")
                        }
    
                    """.trimIndent()

                    dir.buildFile().path.appendText(applicationConfig)
                }
            }
            else {
                // todo: externalize mainClass
            }
        }

        // todo: test framework / which deps?
        if (sourceConfig.hasTest) {
            val alreadyConfigured = dir.buildFile().path.readLines().any {
                it.contains("tasks.withType<Test> {")
            }

            if (!alreadyConfigured) {
                val testConfig = """
                    
                        tasks.withType<Test> {
                            useJUnitPlatform()
                        
                            testLogging {
                                showStandardStreams = true
                                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                                events(org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED, org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED, org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED, org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
                            }
                        }
    
                    """.trimIndent()

                dir.buildFile().path.appendText(testConfig)
            }
        }

        val deps = sourceConfig.sourceSets.flatMap { findImports(it) }.toSet().flatMap { sourceImport ->
            importmap.filterKeys {
                sourceImport.packageName.startsWith(it)
            }.map {
                Gradler.Dependency.parse(it.value) to sourceImport.sourceSet.sourceType
            }.toSet()
        }.toSet()

        deps.forEach { (maybeDependency, maybeSourceType) ->
            maybeDependency?.let { dependency ->
                maybeSourceType?.let { sourceType ->
                    if (!Gradler.hasDependency(dir.buildFile(), dependency)) {
                        Gradler.addDependency(dir.buildFile(), dependency, sourceType)
                    }
                }
            }
        }

    }

    // todo
    //   - text parsing replacement
    //   - adding dependencies
    //   - java toolchain
    //   - main runner
    //   - background compiler
    //   - background build updater
    //   - testing support
    //   - java bootstrapper
    //   - kotter tui
    /*
    fun run(dir: ProjectDir): Unit = run {


        val kotlinImports = (dir.path / "src/main/kotlin").toFile().walkBottomUp().fold(emptySet<String>()) { imports, file ->
            val theseImports = if (file.extension == "kt") {
                file.readLines()
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ") }
                    .toSet()
            }
            else {
                emptySet()
            }

            imports + theseImports
        }

        val neededDeps = kotlinImports.fold(emptySet<String>()) { knownNeededDeps, import ->
            val deps = importmap.filterKeys {
                import.startsWith(it.toString())
            }
            if (deps.isEmpty()) {
                knownNeededDeps
            }
            else {
                knownNeededDeps + deps.values.map { it.toString() }
            }
        }

        neededDeps.forEach { dep ->
            Gradler.Dependency.parse(dep)?.let { dependency ->
                if (!Gradler.hasDependency(dir.buildFile(), dependency)) {
                    Gradler.addDependency(dir.buildFile(), dependency)
                }
            }
        }
    }

     */

}