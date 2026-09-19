package bidvector.adapters.audit

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6A1-22, `ProfileAdapterDependencyTest`·
 * `EvaluationAdapterDependencyTest`와 같은 형태·같은 이유). `ApiAuditStore`·`ApiAuditSql`은
 * `java.sql`·`java.time`·`javax.sql`만 쓴다 — domain·workflow 타입을 하나도 참조하지 않는다
 * (D-6A1-7, audit 행이 도메인 값이 아니라 원시 값만 나른다). 그래서 허용 루트는 **자기
 * 패키지 하나**다 — 다른 형제(`evaluation`·`qualification`·`profile`)와 달리 이 패키지는
 * `adapters.persistence`조차 참조하지 않는다(SQL 문 자신을 `ApiAuditSql`이 직접 갖는다,
 * 팀장 지시 — 6F-4와 `Sql.kt`를 동시에 늘리지 않기 위해서였다).
 */
private val ALLOWED_ROOTS = setOf("bidvector.adapters.audit")

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다(형제 dependency test들과 같은
 * 정규식·같은 근거).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * D-6A1-22 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회를 소스 텍스트 정규식 형태는 못 본다
 * (6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다.
 */
class AuditAdapterDependencyTest {
    @Test
    fun `audit 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/audit")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.

        val violations = classFiles.flatMap(::disallowedBytecodeReferences).distinct()
        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(문자열 판정 자체는 이 값으로 잰다). */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.procurement") shouldBe true
        isDisallowed("bidvector.workflow.strategy") shouldBe true
        isDisallowed("bidvector.workflow.event") shouldBe true
        isDisallowed("bidvector.adapters.persistence") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.adapters.audit") shouldBe false
    }
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 **실행 중인 JVM의 `java.home`**에서 해석한다
 * (형제 dependency test들과 같은 이유 — CI의 JDK 21과 다른 `javap`를 부를 여지를 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

private fun disallowedBytecodeReferences(classFile: File): List<String> {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }

    return BIDVECTOR_INTERNAL_NAME
        .findAll(output)
        .map { it.value.replace('/', '.') }
        .filter(::isDisallowed)
        .toList()
}
