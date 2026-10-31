package bidvector.adapters.opening

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(`NoticeAdapterDependencyTest` 와 같은
 * 형태·같은 이유 — 어댑터 패키지마다 자기 게이트를 갖는 관례).
 *
 * 개찰 결과 조회 어댑터가 봐야 하는 것: [bidvector.workflow.opening](읽기 포트·사실 타입·결과
 * 축) · [bidvector.procurement](`NoticeId`·관측 투찰금액) · `shared-kernel`(금액·율 값 타입) ·
 * **같은 모듈의 다른 adapter 패키지 셋** — `persistence`(provenance·개찰 1위 kind 어휘 codec),
 * `snapshot`(원문 키를 SQL 자리에 놓기 전 형태를 요구하는 함수), `notice`(읽기 전용 트랜잭션을
 * 여는 함수). 그 셋을 **새로 짓지 않고 재사용**하는 것이 이 slice 의 결정이고, 그래서 이 루트
 * 목록이 그 사실을 진술한다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.opening",
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.snapshot",
        "bidvector.adapters.notice",
        "bidvector.adapters.opening",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/** 바이트코드 내부 이름(`a/b/C`)과 점 표기 좌표(`a.b.C`) 양쪽에서 `bidvector...` 부분을 뽑는다. */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v` 로 훑는다** — import 문 없이
 * 전체 한정 좌표로 직접 참조하는 우회를 소스 정규식은 보지 못한다(6F-1 실측).
 */
class OpeningAdapterDependencyTest {
    @Test
    fun `개찰 조회 어댑터의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/opening")
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
        isDisallowed("bidvector.workflow.collection") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        ALLOWED_ROOTS.forEach { root -> isDisallowed(root) shouldBe false }
    }

    /**
     * **보조 그물** — 조회 어댑터 class 하나의 상수 풀에 **개찰 결과 저장소 타입 이름이 없다**는
     * 것만 잰다. 쓰기 부재를 실제로 지는 것은 `JdbcOpeningResultViewTest` 가 실 DB 로 재는 읽기
     * 전용 트랜잭션과 세 표 지문이다(7C① 이 같은 자리에서 이 술어의 한계를 적었다: `const val`
     * 인라인·원시 DML 문자열·같은 패키지의 다른 class 는 이 그물 밖이다).
     */
    @Test
    fun `조회 어댑터의 상수 풀에 개찰 결과 저장소 타입 이름이 없다 — 보조 그물`() {
        javapOf("JdbcOpeningResultView.class").contains("JdbcOpeningResultRepository") shouldBe false
    }

    /**
     * **읽기 전용 트랜잭션을 실제로 여는가**(배선 축) — 거동은 실 DB test 가 재고, 이 test 는
     * 어댑터가 7C① 의 그 함수를 **부른다**는 사실을 상수 풀로 잰다(사본을 만들지 않았다는 것이
     * 곧 이 참조다). 호출을 지우면 이름이 사라져 RED 다.
     */
    @Test
    fun `조회 어댑터는 7C① 의 읽기 전용 트랜잭션 함수를 참조한다`() {
        javapOf("JdbcOpeningResultView.class").contains("beginReadOnlySearchTransaction") shouldBe true
    }

    private fun javapOf(name: String): String {
        val classFile = File("build/classes/kotlin/main/bidvector/adapters/opening/$name")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        return javapOutput(classFile)
    }
}

/** `javap` 를 OS `PATH` 가 아니라 **실행 중인 JVM 의 `java.home`** 에서 해석한다. */
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
