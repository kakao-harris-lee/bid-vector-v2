package bidvector.adapters.event

import bidvector.adapters.architecture.disallowedCoordinates
import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(설계 검토 (4)-①). `event` 패키지는
 * [bidvector.workflow.event] 의 port·봉투([bidvector.workflow.event.EventEnvelope] 등)와
 * [bidvector.workflow.strategy.Actor](봉투가 나르는 actor)·[bidvector.strategy.StrategyEvent]
 * (payload)·`shared-kernel`(정책 값)을 봐야 한다. `bidvector.adapters.persistence` 는 domain 이
 * 아니라 **같은 모듈의 다른 adapter 패키지**다 — 트랜잭션 경계
 * ([bidvector.adapters.persistence.ConnectionSource])가 거기 산다(설계 검토 「트랜잭션 경계
 * 클래스는 도메인 import 가 없으므로 persistence 에 둬도 되고, event 에서 쓴다」).
 * `bidvector.adapters.event` 는 **이 패키지 자신**이다.
 * `PersistenceAdapterDependencyTest` 의 3D allow-list(procurement·shared-kernel)는 넓히지
 * 않는다 — 이 test 는 그 test 와 별개다.
 *
 * **앞 판보다 주장이 넓다 — 술어가 소스 텍스트에서 상수 풀로 옮겨졌다**(M11/11C). 앞 판의
 * `^import (bidvector\.…)` 는 **import 문 없이 전체 한정 좌표로 직접 부르는 우회**를 통과시켰다
 * (`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 이 persistence 에서 닫은 그 사각). 허용 목록 자체는
 * 앞 판과 같은 주장이고 자기 패키지 하나가 더해졌다 — 바이트코드 층은 같은 패키지 안의 참조도
 * 상수 풀에서 보기 때문이다(소스 import 는 같은 패키지를 쓰지 않는다).
 *
 * `ALLOWED_ROOTS` 가 `internal` 인 것은 **두 층 등식**(`ScopedAllowedRootsParityTest`)이 이 집합을
 * 이름 층 정책 `source.scoped-allowed-roots.bidvector.adapters.event` 와 집합 등식으로 묶기 때문이다.
 *
 * **재지 않는 것**: 다른 패키지·모듈 · 상수 풀에 흔적을 남기지 않는 `const val`
 * (`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` — 이름 층이 덮는다) · 값이 흐르는 경로 일반.
 */
internal val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.event",
        "bidvector.workflow.strategy",
        "bidvector.strategy",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.event",
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

class EventAdapterDependencyTest {
    @Test
    fun `event 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val violations =
            disallowedCoordinates(File("build/classes/kotlin/main/bidvector/adapters/event"), ALLOWED_ROOTS)

        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. 앞 판이 들던 다섯을 그대로 든다. */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.procurement") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.workflow.notification") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.event") shouldBe false
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.event") shouldBe false
    }

    /**
     * 접두 일치가 **세그먼트 경계**에서만 성립함을 고정한다 — `startsWith(뿌리)` 만 쓰면
     * `bidvector.strategyX` 같은 이웃 좌표가 허용으로 새고, 그 새는 이름은 지어낼 수 있다.
     */
    @Test
    fun `허용 루트의 이름 접두를 공유하는 다른 패키지는 허용되지 않는다`() {
        isDisallowed("bidvector.strategylegacy") shouldBe true
        isDisallowed("bidvector.adapters.eventx") shouldBe true
    }
}
