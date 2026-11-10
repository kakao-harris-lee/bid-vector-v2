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
     * 모듈이 **선언한** runtime 의존이 해석된 결과. 중첩 아카이브 축의 기준 집합이고 이름으로만 쓴다
     * ([nestedArchiveViolations]). 해석 자체는 Gradle 이 실행 시점에 하므로 설정 단계에서 강제하지 않는다.
     *
     * **`@Classpath` 가 아니다**(수정 라운드 5, 리뷰 M-5). 그 정규화는 **파일 이름을 무시한다** — 같은
     * 내용이면 이름이 달라도 up-to-date 다. 그런데 이 축의 판정 근거가 바로 그 이름이라, 의존의 이름만
     * 바뀌는 변이에서 게이트가 재판 없이 앞의 초록을 재사용할 수 있었다. `NAME_ONLY` 는 **판정이 읽는
     * 것**과 **선언 입력**을 같은 축으로 맞춘다(「게이트 판정 근거는 선언 입력으로」).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
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

        // 적재 자리 둘(`BOOT-INF/classes/`·`BOOT-INF/lib/`)은 **여기서 한 번만 읽는다**(리뷰 r5 M-3).
        // 앞 판은 같은 키를 목록(`requireList`)이자 단일값(`requireSingle`)으로 읽어, 뿌리를 하나 더
        // 더하면 한 자리는 받아들이고 다른 자리는 `error()` 를 냈다 — 「이 키는 몇 개인가」의 답이 읽는
        // 자리마다 달랐다. 자리가 하나인 것은 배포물의 성질이므로 엄밀한 쪽으로 접는다.
        val expectation = bootArchiveExpectation(values)
        val policy =
            JarContentPolicy(
                owned,
                verifiedSourceNames(verifiedSources.files),
                classRoots = listOf(expectation.classesLocation),
                externalClassRoots = values.requireList(EXTERNAL_CLASS_ROOTS),
            )
        val verdict = policy.judge(entries, verified)
        val runtimeJarNames = runtimeClasspath.files.map(File::getName).toSet()
        val read = archiveFiles.map { readNestedArchive(it, expectation.indexLocations) }
        val nested = nestedVerdict(module, values, expectation.libLocation, read, runtimeJarNames)
        val deployment =
            BootArchivePolicy(
                expectation,
                exemptNames = values.requireList(EXTERNAL_NESTED_JARS),
                ownerModule = values.requireValue(BOOT_ARCHIVE_MODULE),
            ).judge(module, read)

        writeReport(
            counts =
                listOf(
                    "module=$module",
                    "owned=$owned/",
                    "verified=${verified.size}",
                    "archives=${archiveFiles.size}",
                    "entries=${entries.size}",
                    "judged=${verdict.judged}",
                    "exempt=${verdict.exempt}",
                    "nestedJars=${nested.nestedCount}",
                    "nestedOutside=${nested.outsideCount}",
                    "bootArchives=${deployment.bootArchiveCount}",
                    "runtimeJars=${runtimeJarNames.size}",
                ),
            violations = verdict.violations + nested.violations + deployment.violations,
        )

        archiveEmptinessFailure(module, archiveFiles.size, verdict.judged)?.let { throw GradleException(it) }
        failOnNestedJars(module, nested.violations + deployment.violations)
        failOnContent(module, verdict.violations)
    }

    /** **리포트를 먼저 쓰고 그 다음에 던진다** — 실패한 판정의 수도 남아야 다음 라운드가 대조한다. */
    private fun writeReport(
        counts: List<String>,
        violations: List<String>,
    ) {
        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText((counts + violations).joinToString(separator = "\n", postfix = "\n"))
    }

    private fun failOnContent(
        module: String,
        violations: List<String>,
    ) {
        if (violations.isEmpty()) return
        throw GradleException(
            violations.joinToString(
                prefix = "아카이브 내용이 게이트를 통과한 산출물이 아니다 (모듈 '$module'):\n  ",
                separator = "\n  ",
            ),
        )
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
     * 중첩 아카이브 축(verifier M-1) — 최상위 class 엔트리만 보던 술어가 `BOOT-INF/lib` 에 실린 jar 를
     * 놓쳤다. 판정은 [NestedArchivePolicy] 가 하고 이 자리는 **읽기와 정책 값 전달**만 맡는다.
     *
     * 기준 집합은 **해석된 runtime classpath 의 파일 이름 전수**다. 확장자를 묻지 않는 것이 판정 쪽과
     * 대칭이다(리뷰 LOW-1) — 선언된 의존에 `.jar` 아닌 파일이 있고 Boot 가 그것을 뿌리에 넣으면,
     * 묻는 쪽만 확장자를 보면 **선언된 것이 위반으로** 지목된다.
     *
     * 뿌리는 [libRoot] 로 **받는다** — 이 자리가 정책을 다시 읽으면 같은 키의 답이 둘이 된다(리뷰 r5 M-3).
     */
    private fun nestedVerdict(
        module: String,
        values: Map<String, String>,
        libRoot: String,
        archives: List<NestedArchive>,
        runtimeJarNames: Set<String>,
    ): NestedArchiveVerdict =
        NestedArchivePolicy(
            roots = listOf(libRoot),
            runtimeJarNames = runtimeJarNames,
            exemptNames = values.requireList(EXTERNAL_NESTED_JARS),
        ).judge(module, archives)

    /**
     * 배포 경로가 읽는 자리의 기대 값([BootArchivePolicy]). 적재 자리 둘은 **다른 키를 복사하지 않고**
     * 중첩 축·클래스 뿌리 목록에서 가져온다 — 같은 값을 두 자리에 적으면 그 둘이 갈라지는 날 게이트가
     * 자기 전제를 잃는다. 목록이 둘 이상이면 어느 쪽이 배포 자리인지가 정해지지 않으므로 거부한다.
     *
     * **그 두 키를 읽는 자리는 여기 하나뿐이다**(리뷰 r5 M-3) — 중첩 축과 class 축은 여기서 나온 값을
     * 받아 쓴다. 읽는 자리가 둘이면 엄밀함도 둘이 되어, 뿌리를 더한 날 한쪽만 거부한다.
     */
    private fun bootArchiveExpectation(values: Map<String, String>): BootArchiveExpectation =
        BootArchiveExpectation(
            launcherMainClass = values.requireValue(BOOT_LAUNCHER_MAIN_CLASS),
            startClass = values.requireValue(BOOT_START_CLASS),
            classesLocation = values.requireSingle(CLASS_ROOTS),
            libLocation = values.requireSingle(NESTED_LIB_ROOTS),
            classpathIndex = values.requireValue(BOOT_CLASSPATH_INDEX),
            layersIndex = values.requireValue(BOOT_LAYERS_INDEX),
        )

    private fun failOnNestedJars(
        module: String,
        violations: List<String>,
    ) {
        if (violations.isEmpty()) return
        throw GradleException(
            violations.joinToString(
                prefix = "배포물이 품은 아카이브의 축이 붉다 (모듈 '$module'):\n  ",
                separator = "\n  ",
            ),
        )
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

private const val CLASS_ROOTS = "jar.class-roots"
private const val EXTERNAL_CLASS_ROOTS = "jar.external-class-roots"
private const val EXTERNAL_NESTED_JARS = "jar.external-nested-jars"
private const val NESTED_LIB_ROOTS = "jar.nested-lib-roots"
private const val BOOT_LAUNCHER_MAIN_CLASS = "jar.boot-launcher-main-class"
private const val BOOT_START_CLASS = "jar.boot-start-class"
private const val BOOT_CLASSPATH_INDEX = "jar.boot-classpath-index"
private const val BOOT_LAYERS_INDEX = "jar.boot-layers-index"
private const val BOOT_ARCHIVE_MODULE = "jar.boot-archive-module"

/** jar 엔트리는 항상 `/` 구분자다 — 플랫폼 구분자로 만든 상대 경로를 그 표기에 맞춘다. */
private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

private fun ByteArray.digest(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { "%02x".format(it) }
