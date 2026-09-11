package bidvector.decision

import java.math.BigDecimal

/**
 * 게시 하한율의 개연 밴드(scope.md ⑥, 조사 §6.2 — `PUBLISHED_FLOOR_MIN_PLAUSIBLE`·
 * `PUBLISHED_FLOOR_MAX_PLAUSIBLE`). 값은 이 타입이 지어내지 않는다 — 호출부가 준다.
 * 경계는 포함이다.
 */
data class PlausibilityBand(
    val min: BigDecimal,
    val max: BigDecimal,
) {
    init {
        require(min <= max) { "min은 max 이하여야 한다: min=$min max=$max" }
    }

    fun contains(rate: BigDecimal): Boolean = rate >= min && rate <= max
}

/**
 * 운영자 하한 override 검증 결과(`OPEN-DEC-04`) — **버리지도(silently drop) 통과시키지도
 * (silently accept) 않고 사유와 함께 거부**한다. `Accepted`만 `internal constructor`다
 * (설계 검토 관례 — 「이 override 는 밴드 안이라 검증을 통과했다」는 주장은 이 함수를
 * 거쳐야만 낼 수 있다). `Rejected`는 실패 정보라 공개다(1D `FloorShortfall.Unmeasurable`
 * 관례 — 사유 있는 실패는 어디서든 만들 수 있다).
 */
sealed interface FloorOverrideOutcome {
    val rate: BigDecimal

    @ConsistentCopyVisibility
    data class Accepted internal constructor(
        override val rate: BigDecimal,
    ) : FloorOverrideOutcome

    data class Rejected(
        override val rate: BigDecimal,
        val band: PlausibilityBand,
    ) : FloorOverrideOutcome
}

/**
 * `OPEN-DEC-04` 검증 커널(조사 §6.2 실측 — legacy 는 `ge=0.0`만 있고 상한이 없어
 * override 가 검증 없이 그대로 guardrail floor 에 도달했다). **거부가 관측 가능한 결과
 * 타입에 실린다** — 예외를 던지지 않고 조용히 버리지도 않는다.
 */
object FloorOverrideValidation {
    fun validate(
        rate: BigDecimal,
        band: PlausibilityBand,
    ): FloorOverrideOutcome =
        if (band.contains(rate)) {
            FloorOverrideOutcome.Accepted(rate)
        } else {
            FloorOverrideOutcome.Rejected(rate, band)
        }
}
