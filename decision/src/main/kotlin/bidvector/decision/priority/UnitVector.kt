package bidvector.decision.priority

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * L2 정규화 임베딩 벡터(scope.md ④, 2E `embedding.proto` — L2 벡터·dimension). 좌표는
 * [BigDecimal] 이다 — public domain API 에 원시 부동소수를 두지 않는다
 * (`api-type-policy.properties`, D-4B4-3 실측). `DoubleArray` 주 생성자는 클래스가
 * public 이면 `DomainApiTypeGateTask`가 주 생성자 자신의 가시성 표기를 보지 않고
 * **클래스 가시성으로** 판정한다(`PublicApiTypes.classOrObjectTargets` — 주 생성자
 * Target 은 `ownVisibilityOverride = PUBLIC` 고정, `internal`/`private constructor`
 * 로도 숨겨지지 않는다) — 그래서 좌표 타입 자체를 `List<BigDecimal>`로 바꿨다(scope.md
 * D-4B4-3 대안). 코사인 계산은 `Double`(1B 금액 규율과 축이 다르다 — 계약이 float 이고
 * 정밀도 요구가 없다)로 [doubles] 에서만 하고, 그 프로퍼티는 본문의 `internal` 선언이라
 * (주 생성자가 아니다) 실제 가시성 그대로 게이트 표면 밖이다.
 *
 * 생성 불변식(D-4B4-3, 「require 는 값 타입 init 에만」) — 차원 > 0, norm 1±[normEpsilon]
 * (정책 값을 호출부가 그대로 넘긴다 — 리터럴이 아니다). **두 벡터의 차원이 서로 다른
 * 것**은 이 타입 혼자 판정할 수 없다(개별로는 둘 다 유효) — [SemanticMatch.of] 가
 * [MatchOutcome.DimensionMismatch] 로 낸다(4D-1 G-1, `require` 가 아니다).
 */
data class UnitVector(
    val values: List<BigDecimal>,
    val normEpsilon: BigDecimal,
) {
    internal val doubles: DoubleArray = DoubleArray(values.size) { values[it].toDouble() }

    init {
        require(values.isNotEmpty()) { "UnitVector 는 차원이 1 이상이어야 한다" }
        require(normEpsilon > BigDecimal.ZERO) { "normEpsilon 은 0보다 커야 한다: $normEpsilon" }
        val norm = sqrt(doubles.fold(0.0) { acc, v -> acc + v * v })
        require(abs(norm - 1.0) <= normEpsilon.toDouble()) {
            "UnitVector 의 norm 은 1±$normEpsilon 범위여야 한다: norm=$norm"
        }
    }
}
