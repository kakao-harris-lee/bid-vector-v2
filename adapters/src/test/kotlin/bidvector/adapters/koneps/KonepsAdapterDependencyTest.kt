package bidvector.adapters.koneps

import bidvector.adapters.architecture.disallowedCoordinates
import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S-3b — 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트. koneps 가 보는 domain 은
 * procurement·shared-kernel 둘이고(scope.md acceptance) `bidvector.adapters.koneps` 는 domain 이
 * 아니라 **이 패키지 자신**이다.
 *
 * **앞 판보다 주장이 넓다 — 두 가지가 바뀌었다**(M11/11C). 형제
 * `ExtractionAdapterDependencyTest` 와 같은 전환이고 같은 근거다.
 *
 * ① **금지 목록에서 허용 목록으로.** 앞 판은 층 밖 뿌리 여섯을 열거해 거기 없는 좌표를 전부
 * 허용했다 — 새 domain 모듈이 생기면 조용히 낡는 열거다.
 *
 * ② **소스 텍스트에서 상수 풀로.** 앞 판의 `^import (bidvector\.…)` 는 import 없이 전체 한정
 * 좌표로 부르는 우회를 통과시켰다(`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 이 persistence 에서
 * 닫은 사각). 앞 판 KDoc 이 「import 문 텍스트 스캔으로 충분한 좁은 단언」이라 적은 판단이 뒤집힌
 * 자리다 — ArchUnit 을 끌어오지 않는다는 쪽(D-3B-2 「측정된 필요 없이 도입하지 않는다」)은 그대로다.
 * 상수 풀을 `javap` 로 읽는 것은 형제 열 축이 이미 쓰는 층이고 **새 의존이 0** 이다.
 *
 * `ALLOWED_ROOTS` 가 `internal` 인 것은 **두 층 등식**(`ScopedAllowedRootsParityTest`)이 이 집합을
 * 이름 층 정책 `source.scoped-allowed-roots.bidvector.adapters.koneps` 와 집합 등식으로 묶기
 * 때문이다.
 *
 * **재지 않는 것**: 다른 패키지·모듈 · 상수 풀에 흔적을 남기지 않는 `const val`
 * (`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` — 이름 층이 덮는다) · 값이 흐르는 경로 일반.
 */
internal val ALLOWED_ROOTS =
    setOf(
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.adapters.koneps",
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

class KonepsAdapterDependencyTest {
    @Test
    fun `koneps 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val violations =
            disallowedCoordinates(File("build/classes/kotlin/main/bidvector/adapters/koneps"), ALLOWED_ROOTS)

        violations shouldBe emptyList()
    }

    /**
     * 양성 대조 — **앞 판에는 없었다**(실측). 앞 판이 들던 층 밖 뿌리 여섯을 그대로 들어, 술어
     * 종류와 의미를 바꾸면서 앞 판이 잡던 것을 잃지 않았음을 같은 자리에서 보인다.
     */
    @Test
    fun `층 밖 domain 루트와 형제 adapter 패키지는 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.settlement") shouldBe true
        isDisallowed("bidvector.workflow") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
        isDisallowed("bidvector.adapters.extraction") shouldBe true
        isDisallowed("bidvector.adapters.persistence") shouldBe true
    }

    /** 음성 대조 — 허용 목록이 조용히 좁아지면 이 자리가 붉는다. */
    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.procurement.Notice") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.koneps") shouldBe false
    }

    /**
     * 접두 일치가 **세그먼트 경계**에서만 성립함을 고정한다 — `startsWith(뿌리)` 만 쓰면
     * `bidvector.procurementX` 같은 이웃 좌표가 허용으로 샌다.
     */
    @Test
    fun `허용 루트의 이름 접두를 공유하는 다른 패키지는 허용되지 않는다`() {
        isDisallowed("bidvector.procurementlegacy") shouldBe true
        isDisallowed("bidvector.adapters.konepsx") shouldBe true
    }
}
