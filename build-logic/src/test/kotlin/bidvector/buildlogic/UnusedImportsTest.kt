package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` 의 **순수 코어**. 각 단언은 「쓰였다」의 한 형태를 고정한다 —
 * 오탐이 미탐보다 나쁜 쪽이 이 축이다(쓰이는 import 를 지우라고 하면 컴파일이 깨진다).
 */
class UnusedImportsTest {
    @Test
    fun `호출로 쓰이면 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run() = Thing()\n"))
    }

    @Test
    fun `아무 데서도 쓰이지 않으면 잡는다`() {
        assertEquals(listOf("a.b.Thing"), unused("import a.b.Thing\nfun run() = 1\n"))
    }

    @Test
    fun `타입 표면에 쓰이면 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run(): Thing? = null\n"))
    }

    @Test
    fun `타입 인자에만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run(): List<Thing> = emptyList()\n"))
    }

    @Test
    fun `애노테이션으로만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Marked\n@Marked\nclass Holder\n"))
    }

    @Test
    fun `문자열 템플릿 안에서만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run() = \"\${Thing.NAME}\"\n"))
    }

    @Test
    fun `KDoc 링크에만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\n\n/** [Thing] 을 설명한다. */\nclass Holder\n"))
    }

    @Test
    fun `별칭 import 는 별칭이 도입 이름이다 — 원 이름을 써도 쓰인 것이 아니다`() {
        assertEquals(listOf("a.b.Thing"), unused("import a.b.Thing as Alias\nfun run() = Thing\n"))
    }

    @Test
    fun `별칭을 쓰면 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing as Alias\nfun run() = Alias()\n"))
    }

    @Test
    fun `import 목록 자신은 쓰임으로 세지 않는다 — 세면 게이트가 공허해진다`() {
        assertEquals(listOf("a.b.Thing", "c.d.Other"), unused("import a.b.Thing\nimport c.d.Other\nfun run() = 1\n"))
    }

    @Test
    fun `package 선언의 세그먼트도 쓰임으로 세지 않는다`() {
        assertEquals(listOf("a.b.Thing"), unused("package a.b\n\nimport a.b.Thing\nfun run() = 1\n"))
    }

    @Test
    fun `문법이 쓰는 import 는 면제한다 — 기호로 불려 본문에 이름이 없다`() {
        val source = "import bidvector.sharedkernel.times\nfun run() = 1\n"

        assertEquals(listOf("bidvector.sharedkernel.times"), unused(source))
        assertEquals(emptyList(), unused(source, syntaxFqns = setOf("bidvector.sharedkernel.times")))
    }

    @Test
    fun `면제는 FQN 이라 같은 짧은 이름의 다른 import 를 덮지 않는다`() {
        // 앞 판은 짧은 이름 면제라 `times` 를 면제하면 **어느 패키지의** `times` 든 영원히 판정 밖이었다
        // (리뷰 M-3). FQN 이면 면제가 그 import 하나에만 걸린다.
        val other = "import com.example.other.times\nfun run() = 1\n"

        assertEquals(
            listOf("com.example.other.times"),
            unused(other, syntaxFqns = setOf("bidvector.sharedkernel.times")),
        )
    }

    @Test
    fun `정적 멤버 import 는 면제 집합에 없으면 그대로 잡힌다`() {
        // 앞 판에서 `java.util.Objects.equals` 는 짧은 이름 `equals` 가 면제 집합에 있어 **영원히**
        // 판정되지 않았다(verifier L-1). FQN 면제는 그 자리를 닫는다.
        assertEquals(
            listOf("java.util.Objects.equals"),
            unused("import java.util.Objects.equals\nfun run() = 1\n", syntaxFqns = SYNTAX_FQNS_IN_POLICY),
        )
    }

    @Test
    fun `대상이 없는 면제는 검출된다 — 죽은 면제는 범위를 조용히 넓힌다`() {
        val dead = UnusedImports.deadSyntaxFqns(setOf("bidvector.sharedkernel.times"), setOf("a.b.ghost"))

        assertEquals(listOf("a.b.ghost"), dead)
    }

    @Test
    fun `도입된 면제는 죽지 않았다`() {
        val live = setOf("bidvector.sharedkernel.times")

        assertEquals(emptyList<String>(), UnusedImports.deadSyntaxFqns(live, live))
    }

    @Test
    fun `정책의 면제 FQN 은 저장소에서 전부 실제로 도입된다 — 죽은 면제 0`() {
        // 모듈마다 도입 집합이 달라 모듈 task 로는 이 단언을 걸 수 없다(어느 모듈에선 늘 죽어 보인다).
        // 저장소 전수를 읽는 자리는 이 test 다 — `MigrationOrderTest` 의 뒤 두 test 와 같은 꼴.
        // 건너뛰기 목록이 비면 `build/` 밑 생성 소스까지 모집단에 들어온다 — 선언을 못 받은 것이고
        // 그 상태의 초록은 다른 집합을 잰 초록이다.
        assertTrue(SKIPPED_DIRECTORIES.isNotEmpty(), "건너뛰기 목록을 선언 쪽에서 받지 못했다")
        val introduced = introducedFqnsInRepository()
        assertTrue(introduced.isNotEmpty(), "도입 FQN 이 0 이다 — 비-공허 단언")

        assertEquals(emptyList<String>(), UnusedImports.deadSyntaxFqns(introduced, SYNTAX_FQNS_IN_POLICY))
    }

    @Test
    fun `수식 참조의 같은 짧은 이름은 쓰임으로 보인다 — 알려진 미탐`() {
        // `import java.time.Duration` 이 쓰이지 않아도 본문의 `kotlin.time.Duration.ZERO` 가 같은 식별자
        // 토큰을 내므로 쓰인 것으로 보인다(verifier L-1). 식별자 토큰으로 세는 술어의 느슨한 쪽이다.
        val source = "import java.time.Duration\nfun run() = kotlin.time.Duration.ZERO\n"

        assertEquals(emptyList(), unused(source))
    }

    @Test
    fun `wildcard import 는 대상이 아니다 — 도입 이름이 열거되지 않는다`() {
        assertEquals(emptyList(), unused("import a.b.*\nfun run() = 1\n"))
    }

    @Test
    fun `같은 짧은 이름을 그 파일이 선언하면 쓰인 것으로 본다 — 알려진 미탐`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nclass Thing\n"))
    }

    private fun unused(
        source: String,
        syntaxFqns: Set<String> = emptySet(),
    ): List<String> = UnusedImports.detect("Probe.kt", source, syntaxFqns).map { it.fqn }

    private companion object {
        val SYNTAX_FQNS_IN_POLICY: Set<String> =
            readPolicy(architecturePolicyFile()).requireList("source.import.syntax-fqns").toSet()

        /**
         * 저장소 전수의 도입 FQN. 게이트가 걸는 세 트리(모듈·included build·느슨한 스크립트)와 같은
         * 모집단이고, 판독은 게이트와 **같은 PSI 술어**다(텍스트 grep 이 아니다). 파서 환경은
         * [parseKotlinFiles] 가 한 번만 만든다.
         */
        fun introducedFqnsInRepository(): Set<String> {
            val sources =
                repositoryRoot()
                    .walkTopDown()
                    .onEnter { it.name !in SKIPPED_DIRECTORIES }
                    .filter { it.isFile && it.extension in setOf("kt", "kts") }
                    .toList()
            return parseKotlinFiles(sources) { ktFile, _ -> UnusedImports.introducedFqns(ktFile).toList() }.toSet()
        }

        /**
         * 건너뛰는 디렉터리는 **선언 쪽**(`build-logic/build.gradle.kts` 의 입력 제외)이 단일 출처이고
         * 여기는 그것을 받는다(리뷰 LOW-4) — 두 자리에 적으면 모집단과 선언 입력이 갈린다.
         */
        val SKIPPED_DIRECTORIES: Set<String> =
            System
                .getProperty("bidvector.scan.skipped")
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()

        fun architecturePolicyFile(): File = File(System.getProperty("bidvector.architecture.policy"))

        /** 저장소 뿌리는 주입된 정책 경로에서 유도한다 — 작업 디렉터리에 기대지 않는다. */
        fun repositoryRoot(): File = architecturePolicyFile().parentFile.parentFile.parentFile
    }
}
