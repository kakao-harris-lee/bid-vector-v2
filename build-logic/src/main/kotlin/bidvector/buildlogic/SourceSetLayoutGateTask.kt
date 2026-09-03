package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * **소스의 자리를 고정한다.** 형식·크기·언어 도구는 전부 source set 을 입력으로 삼으므로,
 * 소스가 관례 자리에 있고 컴파일 대상이 그 자리에 갇혀 있으면 도구 전부가 같은 집합을 본다.
 * 도구마다 입력을 넓히는 방식은 **도구 수만큼 반복해야 하고 하나를 빠뜨리면 그대로 구멍**이다.
 *
 * 다섯을 단언한다 — 관례 디렉터리(java 는 비어 있어야 한다) · resources 와 겹치지 않음 ·
 * 컴파일 입력이 source set 파일과 **양방향으로 같음**(`exclude` 도 걸린다) · source set 과
 * 컴파일 task 의 집합 · resources 안에 컴파일 가능한 소스 없음.
 *
 * 입력은 전부 configuration 시점 Provider 이고 판정은 task action 에서 한다 — configuration
 * 콜백에 두면 configuration cache 적중 회차에 조용히 돌지 않는다.
 *
 * 산출물 쪽은 이 게이트가 덮지 않는다. 컴파일을 거치지 않고 산출 디렉터리에 놓인 class 는
 * 원산지(`SourceFile`) 층이 든다 — 기대는 것이 서로 달라(구성 대 바이트) 둘을 함께 둔다.
 */
abstract class SourceSetLayoutGateTask : DefaultTask() {
    /** `<set>.<축>` → 경로 목록. 축은 `kotlin`·`java`·`resources`·`expectedKotlin`·`expectedResources`. */
    @get:Input
    abstract val sourceDirs: MapProperty<String, String>

    @get:Input
    abstract val expectedSourceSets: SetProperty<String>

    @get:Input
    abstract val actualSourceSets: SetProperty<String>

    @get:Input
    abstract val expectedCompileTasks: SetProperty<String>

    @get:Input
    abstract val actualCompileTasks: SetProperty<String>

    /**
     * 컴파일 task 이름 → `excludes` 를 이어 붙인 값. 비어 있어야 한다.
     *
     * `includes` 는 단언하지 않는다 — KGP 가 Kotlin 확장자 패턴을 기본값으로 채워 두므로
     * 「비어 있음」이 성립하지 않는다. `include(...)` 로 **좁히는** 경로는 그래서 이 층이
     * 잡지 못하고 알려진 제한이 든다.
     */
    @get:Input
    abstract val compileFilters: MapProperty<String, String>

    /**
     * 관례 디렉터리 그 자체. **기준 집합은 이 디렉터리를 직접 걸어서 만든다** — Gradle 의
     * `SourceDirectorySet` 에서 파생시키면 그 객체에 `exclude(...)` 한 줄로 기준과 비교 대상이
     * **동시에** 사라져 자기 비교가 된다. 파일 시스템은 그 필터를 모른다.
     *
     * 입력으로는 디렉터리를 선언해 configuration cache 와 up-to-date 가 성립하게 하고,
     * 걷는 것은 action 에서 한다. `walkTopDown` 은 심볼릭 링크를 따라가며 컴파일러·ktlint 와
     * 같은 집합을 본다(실측).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSourceRoot: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSourceRoot: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSourceSetFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainCompilerFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSourceSetFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testCompilerFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val violations =
            directoryViolations() +
                setViolations() +
                setViolationsFor("main", mainSourceRoot, mainSourceSetFiles, mainCompilerFiles) +
                setViolationsFor("test", testSourceRoot, testSourceSetFiles, testCompilerFiles) +
                filterViolations() +
                resourceSourceViolations()

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("sourceSets=${actualSourceSets.get().sorted()}") + violations).joinToString("\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.sorted().joinToString(
                    prefix = "소스 레이아웃이 관례를 벗어났다 — 형식·크기·언어 도구가 그 소스를 놓친다:\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun directoryViolations(): List<String> =
        actualSourceSets.get().flatMap { set ->
            val kotlin = sourceDirs.paths("$set.kotlin")
            val resources = sourceDirs.paths("$set.resources")
            listOfNotNull(
                "source set '$set' 의 kotlin 디렉터리가 관례와 다르다: $kotlin"
                    .takeIf { kotlin != sourceDirs.paths("$set.expectedKotlin") },
                "source set '$set' 에 java 디렉터리가 등록돼 있다: ${sourceDirs.paths("$set.java")}"
                    .takeIf { sourceDirs.paths("$set.java").isNotEmpty() },
                "source set '$set' 의 resources 디렉터리가 관례와 다르다: $resources"
                    .takeIf { resources != sourceDirs.paths("$set.expectedResources") },
                "source set '$set' 의 kotlin 과 resources 디렉터리가 겹친다: ${kotlin intersect resources}"
                    .takeIf { (kotlin intersect resources).isNotEmpty() },
            )
        }

    private fun setViolations(): List<String> =
        listOfNotNull(
            "source set 집합이 다르다 — 기대 ${expectedSourceSets.get().sorted()}, 실제 ${actualSourceSets.get().sorted()}"
                .takeIf { actualSourceSets.get() != expectedSourceSets.get() },
            "Kotlin 컴파일 task 집합이 다르다 — 기대 ${expectedCompileTasks.get().sorted()}, 실제 ${actualCompileTasks.get().sorted()}"
                .takeIf { actualCompileTasks.get() != expectedCompileTasks.get() },
        )

    /**
     * 기준은 **파일 시스템**이고 등식이 둘이다 — 기준 대 source set, 기준 대 컴파일 입력.
     * 어느 쪽이 어긋났는지 사유가 구분한다. 둘을 서로 비교하면 같은 객체에서 파생돼 자기
     * 비교가 되므로, 양쪽 모두 **디스크의 관례 디렉터리**와 대조한다.
     */
    private fun setViolationsFor(
        set: String,
        root: ConfigurableFileCollection,
        declared: ConfigurableFileCollection,
        compiled: ConfigurableFileCollection,
    ): List<String> {
        val onDisk =
            root.files
                .filter(File::isDirectory)
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
                .compilablePaths()
        return mismatch(onDisk, declared.files.compilablePaths(), set, "source set") +
            mismatch(onDisk, compiled.files.compilablePaths(), set, "컴파일 입력")
    }

    private fun mismatch(
        onDisk: Set<String>,
        observed: Set<String>,
        set: String,
        what: String,
    ): List<String> =
        (onDisk - observed).sorted().map { "'$set' 관례 파일이 $what 에서 빠졌다 — $it" } +
            (observed - onDisk).sorted().map { "'$set' $what 이 관례 디렉터리 밖에 있다 — $it" }

    private fun filterViolations(): List<String> =
        compileFilters
            .get()
            .filterValues(String::isNotEmpty)
            .map { (task, patterns) -> "컴파일 필터가 걸려 있다 — '$task' 의 exclude: $patterns" }
            .sorted()

    private fun resourceSourceViolations(): List<String> =
        resourceFiles.files
            .filter { it.extension in COMPILABLE }
            .map { "resources 아래에 컴파일 가능한 소스가 있다 — ${it.path}" }
            .sorted()

    private companion object {
        val COMPILABLE = setOf("kt", "kts", "java")

        fun Iterable<File>.compilablePaths(): Set<String> =
            filter { it.extension == "kt" }
                .map(File::getCanonicalPath)
                .toSet()

        fun MapProperty<String, String>.paths(key: String): Set<String> =
            get()[key]
                .orEmpty()
                .split(File.pathSeparatorChar)
                .filter(String::isNotEmpty)
                .toSet()
    }
}

/**
 * source set 하나의 레이아웃 사실을 게이트 입력 형태로 낸다. 빌드 스크립트는 배선만 하고
 * 계산은 여기서 한다.
 */
internal fun sourceSetLayoutFacts(
    name: String,
    kotlinDirs: Set<File>,
    javaDirs: Set<File>,
    resourceDirs: Set<File>,
    moduleRoot: File,
): Map<String, String> =
    mapOf(
        "$name.kotlin" to kotlinDirs.joinPaths(),
        "$name.java" to javaDirs.joinPaths(),
        "$name.resources" to resourceDirs.joinPaths(),
        "$name.expectedKotlin" to setOf(File(moduleRoot, "src/$name/kotlin")).joinPaths(),
        "$name.expectedResources" to setOf(File(moduleRoot, "src/$name/resources")).joinPaths(),
    )

private fun Set<File>.joinPaths(): String = joinToString(File.pathSeparator) { it.path }
