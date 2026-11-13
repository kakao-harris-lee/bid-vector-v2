package bidvector.adapters.ml

import bidvector.adapters.architecture.disallowedCoordinates
import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * scope.md 위협 모델 (j), 설계 검토 「닫히지 않는 자리」 — `adapters` 는 domain 층이 아니라
 * `domainSourceReferenceGate` 의 domain 허용 목록 축이 걸리지 않는다. 이 test 가 `ml` 패키지의
 * 좌표 allow-list(3C `ExtractionAdapterDependencyTest` 관례)와 resilience4j `retry` 패키지 부재
 * (우회 (8), D-4D-3 「재시도 계층 하나」의 실행 증거)를 함께 잰다.
 *
 * **앞 판보다 주장이 넓다 — 좌표 축의 술어가 소스 텍스트에서 상수 풀로 옮겨졌다**(M11/11C).
 * 앞 판의 `^import (bidvector\.…)` 는 **import 문 없이 전체 한정 좌표로 직접 부르는 우회**를
 * 통과시켰다(`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 이 persistence 에서 닫은 그 사각). 허용 목록은
 * 앞 판과 같은 주장이고 자기 패키지 하나가 더해졌다 — 바이트코드 층은 같은 패키지 안의 참조도 상수
 * 풀에서 본다.
 *
 * **이 축이 좌측 경계를 고정한 정규식을 쓰는 이유**(`SELF_COORDINATE`): 이 패키지는 생성 proto
 * 타입을 쓰고 그 JVM 패키지가 `contract.bidvector.ml.v1` 이다. 형제 열 축의 고정 없는 정규식은 그
 * 내부 이름의 **가운데 토막**을 자기 좌표로 읽어 **오탐 193**을 낸다(실측). 형제 열 축의 같은 잠복은
 * 오늘 그 꼴을 만나는 축이 0 이라 조용하고, `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 가 받는다.
 *
 * **retry 축은 소스 텍스트로 남는다.** 그 축이 재는 것은 자기 저장소 좌표가 아니라 **외부
 * 라이브러리의 계층**이고, 이 slice 가 받은 계약(②)은 `bidvector` 좌표 술어의 이식이다. 같은 사각
 * (import 없는 전체 한정 사용)이 그 축에 남아 있다 — `checklist.md` 알려진 제한.
 *
 * `ALLOWED_ROOTS` 가 `internal` 인 것은 **두 층 등식**(`ScopedAllowedRootsParityTest`)이 이 집합을
 * 이름 층 정책 `source.scoped-allowed-roots.bidvector.adapters.ml` 과 집합 등식으로 묶기 때문이다.
 */
internal val ALLOWED_ROOTS =
    setOf(
        "bidvector.procurement",
        "bidvector.decision",
        "bidvector.sharedkernel",
        "bidvector.workflow",
        "bidvector.adapters.ml",
    )

private val RESILIENCE4J_RETRY_IMPORT = Regex("""^import io\.github\.resilience4j\.retry""")

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

class MlAdapterDependencyTest {
    @Test
    fun `ml 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val violations =
            disallowedCoordinates(File("build/classes/kotlin/main/bidvector/adapters/ml"), ALLOWED_ROOTS)

        violations shouldBe emptyList()
    }

    @Test
    fun `ml 패키지는 resilience4j retry 패키지를 import 하지 않는다(재시도 계층 하나, 우회 8)`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/ml")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines() }
                .filter { line -> RESILIENCE4J_RETRY_IMPORT.containsMatchIn(line) }
                .toList()

        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. 앞 판이 들던 셋을 그대로 든다. */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.settlement") shouldBe true
        isDisallowed("bidvector.adapters.persistence") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.decision") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.workflow") shouldBe false
        isDisallowed("bidvector.workflow.prediction") shouldBe false
        isDisallowed("bidvector.adapters.ml") shouldBe false
    }

    /**
     * 접두 일치가 **세그먼트 경계**에서만 성립함을 고정한다 — `startsWith(뿌리)` 만 쓰면
     * `bidvector.workflowX` 같은 이웃 좌표가 허용으로 새고, 그 새는 이름은 지어낼 수 있다.
     */
    @Test
    fun `허용 루트의 이름 접두를 공유하는 다른 패키지는 허용되지 않는다`() {
        isDisallowed("bidvector.workflowlegacy") shouldBe true
        isDisallowed("bidvector.adapters.mlx") shouldBe true
    }

    @Test
    fun `resilience4j retry import 를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        RESILIENCE4J_RETRY_IMPORT.containsMatchIn("import io.github.resilience4j.retry.Retry") shouldBe true
        val allowedImport = "import io.github.resilience4j.circuitbreaker.CircuitBreaker"
        RESILIENCE4J_RETRY_IMPORT.containsMatchIn(allowedImport) shouldBe false
    }
}
