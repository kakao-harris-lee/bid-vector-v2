package bidvector.adapters.extraction

import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트. extraction 이 보는 domain 은
 * procurement·qualification·strategy·shared-kernel 이고(3B `KonepsAdapterDependencyTest` 와 같은
 * 관례, D-4) 형제 adapters 패키지는 보지 않는다 — `bidvector.adapters.extraction` 은 domain 이
 * 아니라 **이 패키지 자신**이다.
 *
 * **앞 판보다 주장이 넓다 — 두 가지가 바뀌었다**(M11/11C).
 *
 * ① **금지 목록에서 허용 목록으로.** 앞 판은 층 밖 뿌리 일곱을 열거해 **거기에 없는 좌표를 전부
 * 허용**했다 — 새 domain 모듈이 생기면 그 목록이 조용히 낡는다. 허용 목록은 열거되지 않은 좌표를
 * 전부 거부하므로 그 낡음이 열거가 아니라 구성으로 닫힌다(`PersistenceAdapterDependencyTest` 가
 * 같은 전환을 먼저 했다).
 *
 * ② **소스 텍스트에서 상수 풀로.** 앞 판은 `^import (bidvector\.…)` 정규식이라 **import 문 없이
 * 전체 한정 좌표로 직접 부르는 우회**를 통과시켰다(`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 이
 * persistence 에서 닫은 그 사각 — 이 축에서 음성 대조로 재현했다: 층 밖 타입을 import 없이 부르는
 * 표본이 앞 판에서 초록이었다). 이제 우회하려면 게이트 자체를 바꿔야 한다(CLAUDE.md 「게이트 술어는
 * 문자열이 아니라 구조로」).
 *
 * `ALLOWED_ROOTS` 가 `internal` 인 것은 **두 층 등식**(`ScopedAllowedRootsParityTest`)이 이 집합을
 * 이름 층 정책 `source.scoped-allowed-roots.bidvector.adapters.extraction` 과 집합 등식으로 묶기
 * 때문이다. 그 등식의 바이트코드 항은 **이 축이 실제로 판정에 쓰는 그 집합 자체**다.
 *
 * **재지 않는 것**: 다른 패키지·모듈(이 test 의 모집단이 아니다) · 컴파일 시 인라인돼 상수 풀에 흔적을
 * 남기지 않는 `const val`(형제들과 공유하는 등재된 사각, `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` —
 * 그 자리를 이름 층이 덮는다) · 값이 흐르는 경로 일반.
 */
internal val ALLOWED_ROOTS: Set<String> =
    java.util.Set.copyOf(
        setOf(
            "bidvector.procurement",
            "bidvector.qualification",
            "bidvector.strategy",
            "bidvector.sharedkernel",
            "bidvector.adapters.extraction",
        ),
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

class ExtractionAdapterDependencyTest {
    /** 음성 대조 — 허용 목록이 조용히 좁아지면(뿌리를 지우면) 이 자리가 붉는다. */
    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.procurement.Notice") shouldBe false
        isDisallowed("bidvector.qualification") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.extraction") shouldBe false
    }
}
