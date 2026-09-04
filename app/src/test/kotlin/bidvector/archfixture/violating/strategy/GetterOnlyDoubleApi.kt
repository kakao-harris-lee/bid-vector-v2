package bidvector.archfixture.violating.strategy

import java.math.BigDecimal

/**
 * 접근자 본문만 있고 타입을 명시하지 않은 public 프로퍼티 — 초기화식도 위임도 없어
 * 이전 조건(`hasInitializer() || hasDelegate()`)으로는 타입 미명시로 잡히지 않았다.
 * `val rate = 0.5` 는 막히는데 `val rate get() = 0.5` 는 열리는 것이 넓힘 ④ 가 막으려던
 * 우회로다.
 */
class GetterOnlyDoubleApi {
    private val fraction = BigDecimal.ONE

    val rate get() = fraction.toDouble()
}
