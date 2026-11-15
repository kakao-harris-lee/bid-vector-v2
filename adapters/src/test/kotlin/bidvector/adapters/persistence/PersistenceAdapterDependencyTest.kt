package bidvector.adapters.persistence

import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(`ProfileAdapterDependencyTest`·
 * `StrategyAdapterDependencyTest`와 같은 형태·같은 이유). persistence 가 보는 domain 은
 * [bidvector.procurement](3A canonical fact)와 [bidvector.sharedkernel] 둘이다 —
 * 앞 판의 주장도 이 둘이었다. `bidvector.adapters.snapshot`·`bidvector.adapters.persistence`
 * 는 domain 이 아니라 **같은 모듈의 adapter 패키지**다(jsonb 파싱 헬퍼와 이 패키지 자신).
 *
 * **앞 판보다 주장이 넓다.** 금지 목록(층 밖 domain 루트 여섯)은 열거하지 않은 좌표를
 * 전부 허용했다 — 새 domain 모듈이 생기면 그 목록이 조용히 낡는다. 허용 목록은
 * 열거되지 않은 좌표를 전부 거부하므로 그 낡음이 열거가 아니라 구성으로 닫힌다.
 */
internal val ALLOWED_ROOTS: Set<String> =
    java.util.Set.copyOf(
        setOf(
            "bidvector.procurement",
            "bidvector.sharedkernel",
            "bidvector.adapters.persistence",
            "bidvector.adapters.snapshot",
        ),
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

/**
 * **이 축의 판정은 이 class 에 없다** — [bidvector.adapters.architecture.ScopedBytecodeLayerGateTest] 가 표를 순회하며 한다(M12/12A ④).
 * 여기 남은 것은 이 축에만 있는 주장이고, 아래는 그 판정의 술어가 **상수 풀**인 이유다.
 *
 * `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 처방 — **소스 텍스트가 아니라 컴파일된 클래스의
 * 상수 풀을 `javap -p -v`로 훑는다.** 앞 판은 `^import (bidvector\.…)` 정규식이라 import 문
 * 없이 전체 한정 좌표로 직접 참조하는 우회를 통과시켰다(D-6F5-18 이 그 우회를 실측하고
 * 「처방은 범위가 아니라 **술어 종류**」로 이 OPEN 을 세웠다). 형제 여덟과 같은 층으로
 * 옮기면 우회하려면 게이트 자체를 바꿔야 한다(CLAUDE.md 「게이트 술어는 문자열이 아니라
 * 구조로」).
 *
 * **이 test 가 재는 것**: 이 패키지의 class 가 허용 루트 **밖**의 `bidvector` 좌표를
 * 참조하지 않는다. 재지 않는 것: 다른 패키지·모듈(이 test 의 모집단이 아니다) · 컴파일 시
 * 인라인돼 상수 풀에 흔적을 남기지 않는 `const val`(형제 여덟과 공유하는 등재된 사각,
 * `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) · 값이 흐르는 경로 일반.
 */
class PersistenceAdapterDependencyTest {
    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.procurement.Notice") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.sharedkernel.export") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.snapshot") shouldBe false
    }
}
