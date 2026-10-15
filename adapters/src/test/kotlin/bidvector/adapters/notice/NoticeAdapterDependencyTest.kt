package bidvector.adapters.notice

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(`EvaluationAdapterDependencyTest` 와
 * 같은 형태·같은 이유). 공고 검색 어댑터는 [bidvector.workflow.notice](조회 포트와 조건·결과
 * 타입)·[bidvector.procurement](`Notice`·`NoticeId`·provenance 어휘)·`shared-kernel`(금액 값
 * 타입)·`bidvector.adapters.persistence`(열 목록 상수와 `Notice` 복원 경로 — domain 이 아니라
 * **같은 모듈의 다른 adapter 패키지**다)를 봐야 한다.
 *
 * **이 패키지가 `adapters.persistence` 안에 살 수 없는 이유**가 이 게이트의 쌍둥이다 —
 * `PersistenceAdapterDependencyTest` 는 그 패키지에서 `bidvector.workflow` 참조를 금지하고,
 * 그 금지를 넓히는 대신 축마다 자기 패키지를 갖는 관례(`evaluation`·`strategy`·`qualification`
 * …)를 따랐다. 두 게이트는 서로를 받치고 어느 쪽도 느슨해지지 않았다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.notice",
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.notice",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 점 표기 좌표(`a.b.C`) 양쪽에서 `bidvector...` 부분을 뽑는다
 * (`EvaluationAdapterDependencyTest` 와 같은 정규식·같은 근거).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v` 로 훑는다** — import 문
 * 없이 전체 한정 좌표로 직접 참조하는 우회를 소스 정규식은 보지 못한다(6F-1 실측). 신설
 * 게이트는 구조로 닫는 쪽만 쓴다.
 */
class NoticeAdapterDependencyTest {
    @Test
    fun `notice 어댑터의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/notice")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.

        val violations = classFiles.flatMap(::disallowedBytecodeReferences).distinct()

        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `허용 밖 좌표는 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.workflow.strategy") shouldBe true
        isDisallowed("bidvector.workflow.event") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.notice") shouldBe false
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.notice") shouldBe false
    }

    /**
     * 쓰기 표면 부재의 **구조** 단언 — 검색 어댑터의 상수 풀에 `notice` 표 쓰기 경로
     * (`JdbcNoticeRepository`·`Sql.INSERT_NOTICE`·`Sql.UPDATE_NOTICE`)의 이름이 **없다**.
     * SQL 문면을 grep 하는 것이 아니라 컴파일러가 실제로 만든 참조를 본다 — 이름을 바꿔도
     * 그 타입을 쓰는 순간 좌표가 상수 풀에 나타난다.
     */
    @Test
    fun `검색 어댑터의 상수 풀에 공고 쓰기 경로 좌표가 없다`() {
        val classFile = File("build/classes/kotlin/main/bidvector/adapters/notice/JdbcNoticeSearch.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
        }

        val output = javapOutput(classFile)

        output.contains("JdbcNoticeRepository") shouldBe false
        output.contains("INSERT_NOTICE") shouldBe false
        output.contains("UPDATE_NOTICE") shouldBe false
    }
}

/**
 * `javap` 를 OS `PATH` 가 아니라 **실행 중인 JVM 의 `java.home`** 에서 해석한다
 * (`EvaluationAdapterDependencyTest` 와 같은 이유 — 다른 `javap` 를 부를 여지를 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

private fun javapOutput(classFile: File): String {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}

private fun disallowedBytecodeReferences(classFile: File): List<String> =
    BIDVECTOR_INTERNAL_NAME
        .findAll(javapOutput(classFile))
        .map { it.value.replace('/', '.') }
        .filter(::isDisallowed)
        .toList()
