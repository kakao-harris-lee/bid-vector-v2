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
import java.io.InputStream
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipEntry

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

        val policy =
            JarContentPolicy(
                owned,
                verifiedSourceNames(verifiedSources.files),
                classRoots = values.requireList(CLASS_ROOTS),
                externalClassRoots = values.requireList(EXTERNAL_CLASS_ROOTS),
            )
        val verdict = policy.judge(entries, verified)
        val runtimeJarNames = runtimeClasspath.files.map(File::getName).toSet()
        val expectation = bootArchiveExpectation(values)
        val read = archiveFiles.map { readNestedArchive(it, expectation.indexLocations) }
        val nested = nestedVerdict(module, values, read, runtimeJarNames)
        val deployment =
            BootArchivePolicy(
                expectation,
                exemptNames = values.requireList(EXTERNAL_NESTED_JARS),
                ownerModule = values.requireValue(BOOT_ARCHIVE_MODULE),
            ).judge(module, read)

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
                "nestedOutside=${nested.outsideCount}",
                "bootArchives=${deployment.bootArchiveCount}",
                "runtimeJars=${runtimeJarNames.size}",
            )
        val found = verdict.violations + nested.violations + deployment.violations
        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText((header + found).joinToString(separator = "\n", postfix = "\n"))

        archiveEmptinessFailure(module, archiveFiles.size, verdict.judged)?.let { throw GradleException(it) }
        failOnNestedJars(module, nested.violations + deployment.violations)
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
     * 중첩 아카이브 축(verifier M-1) — 최상위 class 엔트리만 보던 술어가 `BOOT-INF/lib` 에 실린 jar 를
     * 놓쳤다. 판정은 [NestedArchivePolicy] 가 하고 이 자리는 **읽기와 정책 값 전달**만 맡는다.
     *
     * 기준 집합은 **해석된 runtime classpath 의 파일 이름 전수**다. 확장자를 묻지 않는 것이 판정 쪽과
     * 대칭이다(리뷰 LOW-1) — 선언된 의존에 `.jar` 아닌 파일이 있고 Boot 가 그것을 뿌리에 넣으면,
     * 묻는 쪽만 확장자를 보면 **선언된 것이 위반으로** 지목된다.
     */
    private fun nestedVerdict(
        module: String,
        values: Map<String, String>,
        archives: List<NestedArchive>,
        runtimeJarNames: Set<String>,
    ): NestedArchiveVerdict =
        NestedArchivePolicy(
            roots = values.requireNonEmptyList(NESTED_LIB_ROOTS),
            runtimeJarNames = runtimeJarNames,
            exemptNames = values.requireList(EXTERNAL_NESTED_JARS),
        ).judge(module, archives)

    /**
     * 배포 경로가 읽는 자리의 기대 값([BootArchivePolicy]). 적재 자리 둘은 **다른 키를 복사하지 않고**
     * 중첩 축·클래스 뿌리 목록에서 가져온다 — 같은 값을 두 자리에 적으면 그 둘이 갈라지는 날 게이트가
     * 자기 전제를 잃는다. 목록이 둘 이상이면 어느 쪽이 배포 자리인지가 정해지지 않으므로 거부한다.
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

/**
 * **판독기 층** — 아카이브를 [NestedArchive] 로 읽는다. 판정은 [NestedArchivePolicy] 와
 * [BootArchivePolicy] 가 한다.
 *
 * 최상위 함수인 것은 그래야 **test 가 진짜 아카이브를 하나 만들어 이 층을 잴 수 있기** 때문이다
 * (리뷰 M-1): 판정만 순수 test 로 잠그면 판독이 경로를 버리는 변이가 아무 test 도 깨뜨리지 않는다 —
 * 실제로 그렇게 한 라운드가 열렸다.
 *
 * 읽는 것은 셋이다 — ⓐ 엔트리 전수의 경로와 **zip 으로 읽히는가** ⓑ 배포 경로의 도구가 읽는 manifest
 * 속성 ⓒ 그 속성이 가리킨 자리와 [indexLocations] 가 가리킨 자리의 index 줄. ⓑⓒ 가 없으면 「무엇이
 * 실리는가」를 엔트리 목록만으로 짐작하게 된다(verifier H-1).
 */
internal fun readNestedArchive(
    archive: File,
    indexLocations: Set<String>,
): NestedArchive =
    JarFile(archive).use { jar ->
        val attributes =
            BOOT_ARCHIVE_ATTRIBUTES
                .mapNotNull { name -> jar.manifest?.mainAttributes?.getValue(name)?.let { name to it } }
                .toMap()
        NestedArchive(
            path = archive.path,
            displayName = archive.name,
            entries =
                jar
                    .entries()
                    .asSequence()
                    .filterNot { it.isDirectory }
                    .map { ArchiveEntry(it.name, jar.holdsArchiveDirectory(it)) }
                    .toList(),
            descriptor =
                BootArchiveDescriptor(
                    attributes = attributes,
                    indexes = jar.readIndexes(indexLocations + attributes.indexLocations()),
                ),
        )
    }

/** 판독기가 읽는 manifest 속성 — [BootArchiveExpectation] 이 단언하는 것과 **같은 집합**이다. */
private val BOOT_ARCHIVE_ATTRIBUTES =
    listOf(
        MAIN_CLASS_ATTRIBUTE,
        START_CLASS_ATTRIBUTE,
        CLASSES_LOCATION_ATTRIBUTE,
        LIB_LOCATION_ATTRIBUTE,
        CLASSPATH_INDEX_ATTRIBUTE,
        LAYERS_INDEX_ATTRIBUTE,
    )

private fun Map<String, String>.indexLocations(): Set<String> =
    setOfNotNull(this[CLASSPATH_INDEX_ATTRIBUTE], this[LAYERS_INDEX_ATTRIBUTE])

/**
 * index 파일 한 줄의 모양은 `- "<엔트리 경로>"` 다 — 추출 도구가 그 자리에서 따옴표 안쪽만 떼어
 * 쓴다. 떼어지지 않는 줄은 **그대로 둔다**: 그 줄은 기대 집합에 없으므로 판정이 「뿌리 밑 엔트리가
 * 아닌 줄」로 지목하고, 판독기가 조용히 버리면 그 지목이 사라진다.
 */
private fun JarFile.readIndexes(locations: Set<String>): Map<String, List<String>> =
    locations
        .mapNotNull { location -> getEntry(location)?.let { location to readIndexLines(it) } }
        .toMap()

private fun JarFile.readIndexLines(entry: ZipEntry): List<String> =
    getInputStream(entry)
        .use { it.readBytes() }
        .decodeToString()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line -> line.removePrefix("- ").removeSurrounding("\"") }
        .toList()

/**
 * zip 판독기가 아카이브를 찾는 자리 — **끝의 중앙 디렉터리 끝 레코드(EOCD)** 다.
 *
 * 앞 판은 앞 네 바이트의 서명을 보았는데, 그러면 **앞에 아무 바이트나 붙인 jar** 가 빠져나갔다
 * (앞 판 제한 20 ⓐ). zip 은 앞에서가 아니라 뒤에서 읽히므로 그 포장은 적재를 막지 않는다. 서명 네
 * 바이트만으로는 우연한 일치가 많아 **주석 길이가 남은 바이트와 맞는가**와 **중앙 디렉터리가 그 앞에
 * 놓이는가**를 함께 본다(실측: 이 저장소의 배포물에서 뿌리 밖 오탐 0).
 */
private fun JarFile.holdsArchiveDirectory(entry: JarEntry): Boolean =
    getInputStream(entry).use { it.readTail(EOCD_SCAN_WINDOW) }.holdsCentralDirectoryEnd()

/** 엔트리의 **끝 [size] 바이트**와 전체 길이. 큰 중첩 jar 를 통째로 들고 있지 않기 위해서다. */
private class ArchiveTail(
    val bytes: ByteArray,
    val total: Long,
)

private fun InputStream.readTail(size: Int): ArchiveTail {
    val window = ByteArray(size)
    var filled = 0
    var total = 0L
    val chunk = ByteArray(READ_CHUNK)
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        total += read
        val take = minOf(read, size)
        val keep = minOf(filled, size - take)
        if (keep > 0) System.arraycopy(window, filled - keep, window, 0, keep)
        System.arraycopy(chunk, read - take, window, keep, take)
        filled = keep + take
    }
    return ArchiveTail(window.copyOf(filled), total)
}

private fun ArchiveTail.holdsCentralDirectoryEnd(): Boolean {
    val base = total - bytes.size
    for (index in bytes.size - EOCD_LENGTH downTo 0) {
        if (bytes.readIntLe(index) == EOCD_SIGNATURE && isConsistentEnd(index, base)) return true
    }
    return false
}

/**
 * 서명 네 바이트는 우연히도 나온다 — 그래서 **주석 길이가 남은 바이트와 정확히 같은가**와 **중앙
 * 디렉터리가 이 레코드 앞에 놓이는가**를 함께 본다.
 *
 * 뒤의 확인은 zip64 에서 성립하지 않는다 — 그 형식은 두 자리에 `0xFFFFFFFF` 표지를 넣고 실제 값을
 * 별도 레코드에 둔다. 표지가 보이면 **자리 확인을 하지 않는다**: 하면 zip64 로 포장한 아카이브가
 * 「아카이브가 아니다」로 빠져나간다(앞 판 서명 술어가 열어 두던 것과 같은 종류의 구멍이다).
 */
private fun ArchiveTail.isConsistentEnd(
    index: Int,
    base: Long,
): Boolean {
    val commentLength = bytes.readShortLe(index + EOCD_COMMENT_OFFSET)
    if (commentLength != bytes.size - (index + EOCD_LENGTH)) return false
    val directorySize = bytes.readIntLe(index + EOCD_SIZE_OFFSET).toUInt().toLong()
    val directoryOffset = bytes.readIntLe(index + EOCD_OFFSET_OFFSET).toUInt().toLong()
    val zip64 = directorySize == ZIP64_MARKER || directoryOffset == ZIP64_MARKER
    return zip64 || directoryOffset + directorySize <= base + index
}

private fun ByteArray.readIntLe(at: Int): Int =
    (this[at].toInt() and 0xFF) or
        ((this[at + 1].toInt() and 0xFF) shl 8) or
        ((this[at + 2].toInt() and 0xFF) shl 16) or
        ((this[at + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.readShortLe(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private const val EOCD_SIGNATURE = 0x06054B50
private const val EOCD_LENGTH = 22
private const val EOCD_SIZE_OFFSET = 12
private const val EOCD_OFFSET_OFFSET = 16
private const val EOCD_COMMENT_OFFSET = 20
private const val ZIP64_MARKER = 0xFFFFFFFFL

/** EOCD 는 주석 길이(최대 `0xFFFF`)만큼만 끝에서 밀린다 — 그래서 끝의 이만큼만 보면 전수다. */
private const val EOCD_SCAN_WINDOW = EOCD_LENGTH + 0xFFFF
private const val READ_CHUNK = 8192

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
