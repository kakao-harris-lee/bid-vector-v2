package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile

/**
 * **아카이브에 들어간 클래스가 게이트를 거친 그 바이트인지 본다.**
 *
 * 소스 검사와 산출물 검사는 둘 다 「모듈이 컴파일한 것」을 본다. `jar { from("prebuilt") }` 는
 * 그 둘을 모두 비껴간다 — 소스가 없고 `output.classesDirs` 도 아니다. 그런데 **배포되는 것은
 * jar** 이므로 그 경로가 열려 있으면 앞의 두 검사가 지키는 것이 배포물이 아니게 된다.
 *
 * **패키지 접두만 보면 부족하다.** 접두는 넣는 쪽이 맞출 수 있는 값이라
 * `bidvector/<module>/Sneak.class` 로 이름만 지으면 통과했다. 그래서 판정을 열거가 아니라
 * **포함 관계**로 바꾼다: 아카이브의 class 엔트리 집합은 **게이트를 통과한 class output**
 * (경로 + 내용 해시)의 부분집합이어야 한다. 게이트를 거치지 않은 바이트는 이름을 어떻게 짓든
 * 그 집합에 없다.
 *
 * 엔트리마다 **원산지도 함께 본다** — class 의 `SourceFile` 이 **게이트를 통과한 소스 이름
 * 집합**(컴파일러가 실제로 먹은 파일)에 드는가다. 검증 집합에 심어 포함 관계를 통과시켜도 그
 * 층이 잡는다(실측). 판정과 그 근거는 [JarContentPolicy] 가 갖는다.
 *
 * 대상은 **class 엔트리뿐**이다. `.properties` 같은 resource 는 컴파일 산출물이 아니고
 * 소스·크기·경계 게이트의 대상도 아니므로 여기서 판정하지 않는다.
 */
abstract class JarContentGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archives: ConfigurableFileCollection

    /** 게이트를 통과한 산출물. 아카이브가 이 집합 밖의 바이트를 담으면 실패한다. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    /**
     * 모듈이 **선언한** runtime 의존이 해석된 결과. 중첩 jar 축의 기준 집합이고 이름으로만 쓴다
     * ([nestedArchiveViolations]). 해석 자체는 Gradle 이 실행 시점에 하므로 설정 단계에서 강제하지 않는다.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeClasspath: ConfigurableFileCollection

    /** 형식·크기·언어 도구가 검사한 소스. 그 **이름 집합**이 원산지 판정의 기준이다. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val verifiedSources: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val values = readPolicy(policyFile.get().asFile)
        val module = moduleName.get()
        val owned = PackageOwnershipPolicy(values.requireValue("package.root")).ownedPackage(module).replace('.', '/')
        val verified = verifiedClasses()
        val archiveFiles = archives.files.filter(File::isFile)
        val entries = archiveFiles.flatMap(::classEntries)

        val policy =
            JarContentPolicy(
                owned,
                verifiedSourceNames(verifiedSources.files),
                classRoots = values.requireList(CLASS_ROOTS),
                externalClassRoots = values.requireList(EXTERNAL_CLASS_ROOTS),
            )
        val verdict = policy.judge(entries, verified)
        val nested = nestedVerdict(module, values, archiveFiles)

        val header =
            listOf(
                "module=$module",
                "owned=$owned/",
                "verified=${verified.size}",
                "archives=${archiveFiles.size}",
                "entries=${entries.size}",
                "judged=${verdict.judged}",
                "exempt=${verdict.exempt}",
                "nestedJars=${nested.nestedCount}",
                "runtimeJars=${nested.runtimeCount}",
            )
        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText((header + verdict.violations + nested.violations).joinToString(separator = "\n", postfix = "\n"))

        archiveEmptinessFailure(module, archiveFiles.size, verdict.judged)?.let { throw GradleException(it) }
        failOnNestedJars(module, nested.violations)
        if (verdict.violations.isNotEmpty()) {
            throw GradleException(
                verdict.violations.joinToString(
                    prefix = "아카이브 내용이 게이트를 통과한 산출물이 아니다 (모듈 '$module'):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    /** 게이트를 통과한 class output: 클래스 디렉터리 기준 상대 경로 → 내용 해시. */
    private fun verifiedClasses(): Map<String, String> =
        classDirectories.files
            .filter(File::isDirectory)
            .flatMap { root ->
                root
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .map { it.relativeTo(root).invariantPath() to it.readBytes().digest() }
                    .toList()
            }.toMap()

    /**
     * 중첩 jar 축(verifier M-1) — 최상위 class 엔트리만 보던 술어가 `BOOT-INF/lib` 에 실린 jar 를 놓쳤다.
     * 경로가 아니라 **파일 이름**으로 견주므로 어느 자리에 넣어도 같은 판정을 받는다.
     */
    private fun nestedVerdict(
        module: String,
        values: Map<String, String>,
        archiveFiles: List<File>,
    ): NestedJarVerdict {
        val nested = archiveFiles.associate { it.name to nestedJarNames(it) }
        val runtimeJarNames =
            runtimeClasspath.files
                .filter { it.extension == "jar" }
                .map(File::getName)
                .toSet()
        val exempt = values.requireList(EXTERNAL_NESTED_JARS)
        return NestedJarVerdict(
            violations = nestedArchiveViolations(module, nested, runtimeJarNames, exempt),
            nestedCount = nested.values.sumOf { it.size },
            runtimeCount = runtimeJarNames.size,
        )
    }

    private fun failOnNestedJars(
        module: String,
        violations: List<String>,
    ) {
        if (violations.isEmpty()) return
        throw GradleException(
            violations.joinToString(
                prefix = "아카이브가 선언되지 않은 중첩 jar 를 품는다 (모듈 '$module'):\n  ",
                separator = "\n  ",
            ),
        )
    }

    /** 아카이브가 품는 중첩 jar 의 **파일 이름**. 경로는 보지 않는다 — 자리를 옮기는 것이 우회가 되지 않게. */
    private fun nestedJarNames(archive: File): List<String> =
        JarFile(archive).use { jar ->
            jar
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.endsWith(".jar") }
                .map { it.substringAfterLast('/') }
                .toList()
        }

    private fun classEntries(archive: File): List<JarClassEntry> =
        JarFile(archive).use { jar ->
            jar
                .entries()
                .asSequence()
                .filter { it.name.endsWith(".class") }
                .map { entry ->
                    val bytes = jar.getInputStream(entry).use { stream -> stream.readBytes() }
                    JarClassEntry(entry.name, bytes.digest(), bytes.sourceFileName())
                }.toList()
        }
}

/** 중첩 jar 축의 판정과 그 축이 **실제로 센 수** — 수가 0 으로 내려가면 리포트에서 보인다. */
private class NestedJarVerdict(
    val violations: List<String>,
    val nestedCount: Int,
    val runtimeCount: Int,
)

private const val CLASS_ROOTS = "jar.class-roots"
private const val EXTERNAL_CLASS_ROOTS = "jar.external-class-roots"
private const val EXTERNAL_NESTED_JARS = "jar.external-nested-jars"

/** jar 엔트리는 항상 `/` 구분자다 — 플랫폼 구분자로 만든 상대 경로를 그 표기에 맞춘다. */
private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

private fun ByteArray.digest(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { "%02x".format(it) }
