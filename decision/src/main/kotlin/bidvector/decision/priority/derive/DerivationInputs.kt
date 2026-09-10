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
 * 상태」로 닫는다.
 *
 * **판정층 짝은 축마다 다르다(verifier r2 G-1 정정 — r1 의 「상류에 있다」는 한 축에만
 * 맞았다)**:
 * - `recommendedRate`·`predictedRate` — 상류에 **이미 있는 관문**의 마지막 안전판이다.
 *   2B `D-2B-8`(*"`Rate.fraction` 이 `1` 초과면 계약 위반(`INVALID_REQUEST`)"*)이 계약을
 *   걸고, `bidvector.adapters.ml.ParsedSuccessFields.toRateOrNull()` 이 `> 1` 이면 `null`
 *   을 내 `BidPredictionOutcome.Unavailable(ContractViolation)` 으로 접는다(`PredictionContractTest`
 *   가 `1.0000` 통과·`1.0001` 거부를 고정).
 * - `floorRate` — **이 `init` 이 유일한 관문이다.** 상류는 4D-1 gRPC 가 아니라
 *   `Notice.floorRate`(`FloorRate`)이고, `FloorRate`(`init` 없음)·`Canonicalize.kt`
 *   (`Rate.ofPercent` 변환만, 상한 검사 없음)·`NoticeReconstruction.kt`(DB 열을
 *   `Rate.ofFraction` 으로 그대로 감쌈) 어디에도 `≤ 1` 이 없다(shared-kernel·procurement·
 *   adapters 전수 grep 0건). **4B-6 인계**(checklist 「알려진 제한」) — `MarginInputs`
 *   생성 전에 `Notice.floorRate > 1` 을 걸러 [DerivationAbsence.FloorRateOutOfRange] 로
 *   내야 한다. 이 `init` 은 그 관문이 배선되기 전까지의 마지막 방어선이다.
 *
 * legacy 의 `max(0, min(1, ·))` 클램프는 **재현하지 않는다**(D-4B5-1 방향 — 값을 조용히
 * 자르는 대신 거부한다, `policy-values.md` §3).
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
