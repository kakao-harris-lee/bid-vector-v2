package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Rate
import java.math.BigDecimal
import java.time.Duration

/**
 * ③ 기대 마진 입력(scope.md) — 다섯 축 전부 non-null(D-4B5-6). `capacity` 부재가 이 값
 * 자체를 못 만들게 하는 사유(`Absent(PredictionMissing)`)는 4B-6 이 [deriveExpectedMargin]
 * 을 부르지 않는 것으로 낸다 — 이 타입은 「이미 확보된 다섯」만 나른다.
 *
 * **`recommendedRate`·`floorRate`·`predictedRate` 는 `≤ 1` 이어야 한다**(verifier r1 F-1) —
 * `Rate` 자신은 상한을 두지 않는다(shared-kernel `Rate` KDoc, ADR 0002 D-4 「밴드 상한을
 * 여기 두지 않는다」). 1 을 넘는 율이 이 타입을 통과하면 `floorHeadroomOf`(분모 `1 − floor`)
 * 가 부호를 두 번 뒤집어 legacy 보다 **낙관적인**(높은) 마진으로 새는 결함이 있었다
 * (실측: `rec=0.9, floor=1.2` → V2 `0.715` vs legacy `0.515`). 이 `init` 이 그 값을 「불가능한
 * 상태」로 닫는다 — **판정층 짝은 상류(4D-1)에 있다**: `Rate` 가 `≤ 1` 을 보장하는 계약
 * 경계는 2B `D-2B-8`(*"`Rate.fraction` 이 `1` 초과면 계약 위반(`INVALID_REQUEST`)"*)이고,
 * gRPC 응답이 그 경계를 어기면 4D-1 이 `BidPredictionOutcome.Unavailable(ContractViolation)`
 * 로 접는다 — 이 `init` 은 그 계약이 이미 강제하는 값의 **마지막 안전판**이지 새 정책이
 * 아니다. legacy 의 `max(0, min(1, ·))` 클램프는 **재현하지 않는다**(D-4B5-1 방향 — 값을
 * 조용히 자르는 대신 거부한다, `policy-values.md` §3).
 */
data class MarginInputs(
    val recommendedRate: Rate,
    val floorRate: Rate?,
    val predictedRate: Rate,
    val priceFitness: UnitScore,
    val capacity: UnitScore,
) {
    init {
        requireRateAtMostOne("recommendedRate", recommendedRate)
        floorRate?.let { requireRateAtMostOne("floorRate", it) }
        requireRateAtMostOne("predictedRate", predictedRate)
    }
}

private fun requireRateAtMostOne(
    label: String,
    rate: Rate,
) {
    require(rate.fraction <= BigDecimal.ONE) { "$label 는 1 이하여야 한다: ${rate.fraction}" }
}

/**
 * ④ 실행 복잡도 입력(scope.md) — `budget`·`match`·`capacity` 만 nullable(부분 부재를
 * 재정규화로 흡수, D-4B5-6). `keywordHits`·`remaining`·`loadRatio` 는 구조상 항상 값을
 * 갖는다(위협 모델 우회 (8) — 전 항 부재가 불가능하다).
 */
data class ComplexityInputs(
    val budget: BaseAmount?,
    val keywordHits: KeywordHits,
    val remaining: Duration?,
    val loadRatio: UnitScore,
    val match: UnitScore?,
    val capacity: UnitScore?,
)

/**
 * 키워드 매칭 카운트(D-4B5-5) — 문자열 → 매칭은 4B-6 소유(정책 키워드 14개도 거기 있다),
 * 이 slice 는 이미 센 값만 받는다(우회 (5) 음수 차단).
 */
data class KeywordHits(
    val count: Int,
) {
    init {
        require(count >= 0) { "KeywordHits.count 는 음수일 수 없다: $count" }
    }
}

/**
 * ④ 결과(scope.md) — 구조상 항상 [DerivationOutcome.Present] 다(우회 (8)). 어느 신호가
 * 재정규화에 실제로 쓰였는지 [usedSignals] 로 남긴다(설계 검토 (3) 「미달로 채택」).
 */
data class ComplexityOutcome(
    val score: UnitScore,
    val usedSignals: Set<ComplexitySignal>,
)
