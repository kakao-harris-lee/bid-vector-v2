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
 * **패키지 접두만 보면 부족하다**(Codex 7차). 접두는 넣는 쪽이 맞출 수 있는 값이라
 * `bidvector/<module>/Sneak.class` 로 이름만 지으면 통과했다. 그래서 판정을 열거가 아니라
 * **포함 관계**로 바꾼다: 아카이브의 class 엔트리 집합은 **게이트를 통과한 class output**
 * (경로 + 내용 해시)의 부분집합이어야 한다. 게이트를 거치지 않은 바이트는 이름을 어떻게 짓든
 * 그 집합에 없다.
 *
 * 엔트리마다 **원산지도 함께 본다**(`kotlin.Metadata` 보유) — 검증 집합에 심어 포함 관계를
 * 통과시켜도 그 층이 잡는다(실측). 판정과 그 근거는 [JarContentPolicy] 가 갖는다.
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

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val packageRoot = readPolicy(policyFile.get().asFile).requireValue("package.root")
        val module = moduleName.get()
        val owned = PackageOwnershipPolicy(packageRoot).ownedPackage(module).replace('.', '/')
        val verified = verifiedClasses()
        val entries = archives.files.filter(File::isFile).flatMap(::classEntries)

        val violations = JarContentPolicy(owned).violations(entries, verified)

        val header =
            listOf(
                "module=$module",
                "owned=$owned/",
                "verified=${verified.size}",
                "entries=${entries.size}",
            )
        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText((header + violations).joinToString(separator = "\n", postfix = "\n"))

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
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

    private fun classEntries(archive: File): List<JarClassEntry> =
        JarFile(archive).use { jar ->
            jar
                .entries()
                .asSequence()
                .filter { it.name.endsWith(".class") }
                .map { entry ->
                    val bytes = jar.getInputStream(entry).use { stream -> stream.readBytes() }
                    JarClassEntry(entry.name, bytes.digest(), bytes.hasKotlinMetadata())
                }.toList()
        }
}

/** jar 엔트리는 항상 `/` 구분자다 — 플랫폼 구분자로 만든 상대 경로를 그 표기에 맞춘다. */
private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

private fun ByteArray.digest(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { "%02x".format(it) }
