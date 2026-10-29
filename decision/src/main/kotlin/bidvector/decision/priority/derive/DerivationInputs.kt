package bidvector.decision.priority.derive

import bidvector.decision.PlausibleFloorRate
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
 * **`recommendedRate`·`floorRate`·`predictedRate` 는 `≤ 1` 이어야 한다** —
 * `Rate` 자신은 상한을 두지 않는다(shared-kernel `Rate` KDoc, ADR 0002 D-4 「밴드 상한을
 * 여기 두지 않는다」). 1 을 넘는 율이 이 타입을 통과하면 `floorHeadroomOf`(분모 `1 − floor`)
 * 가 부호를 두 번 뒤집어 legacy 보다 **낙관적인**(높은) 마진으로 새는 결함이 있었다
 * (실측: `rec=0.9, floor=1.2` → V2 `0.715` vs legacy `0.515`). 이 `init` 이 그 값을 「불가능한
 * 상태」로 닫는다.
 *
 * **판정층 짝은 축마다 다르다**:
 * - `recommendedRate`·`predictedRate` — 상류에 **이미 있는 관문**의 마지막 안전판이다.
 *   2B `D-2B-8`(*"`Rate.fraction` 이 `1` 초과면 계약 위반(`INVALID_REQUEST`)"*)이 계약을
 *   걸고, `bidvector.adapters.ml.ParsedSuccessFields.toRateOrNull()` 이 `> 1` 이면 `null`
 *   을 내 `BidPredictionOutcome.Unavailable(ContractViolation)` 으로 접는다(`PredictionContractTest`
 *   가 `1.0000` 통과·`1.0001` 거부를 고정).
 * - `floorRate` — **관문은 DEC-10 신뢰 게이트이고 이 `init` 은 최후 방어선이다**(D-DEC10-9).
 *   원값 쪽에는 상한이 없다 — `FloorRate`(`init` 없음)·`Canonicalize.kt`(`Rate.ofPercent`
 *   변환만)·`NoticeReconstruction.kt`(DB 열을 `Rate.ofFraction` 으로 그대로 감쌈) 어디에도
 *   `≤ 1` 이 없고, 그것이 **의도**다(수집·영속은 공고를 버리지 않는다). 가격 경로로 들어오는
 *   값은 `bidvector.decision.trustPublishedFloorRate` 를 거치고, 통과 값의 율은 개연 밴드
 *   안이므로 `≤ 1` 이다 — 그래서 이 `require` 는 가격 경로에서 도달 불가다. 그래도 지운다는
 *   것은 「호출부가 X 만 하므로 안전」을 타입에서 빼는 것이라(R-FLOOR-04 의 형태) 남겨 둔다.
 *
 * legacy 의 `max(0, min(1, ·))` 클램프는 **재현하지 않는다**(D-4B5-1 방향 — 값을 조용히
 * 자르는 대신 거부한다, `policy-values.md` §3).
 */
@ConsistentCopyVisibility
data class MarginInputs internal constructor(
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

/**
 * **가격 경로 ③ 의 유일한 공개 진입**(운영자 결정 2026-10-28 — D-DEC10-22). 하한율 자리가
 * [bidvector.decision.PlausibleFloorRate] 이므로 **게이트를 거친 값만** 이 경로에 들어온다.
 *
 * 왜 타입이어야 하는가 — 앞 판은 이 자리가 날 `Rate?` 였고, 그래서 원값을 **다른 형태로
 * 세탁**해 넣는 길이 census 의 어느 축에도 보이지 않았다(verifier r4 실측: `toString()` 정규식
 * 파싱 · 영속 행의 `BigDecimal`). census 는 **타입을 따라가므로** 값의 표현이 바뀌면 못 본다 —
 * 열거를 늘리는 것은 다음 우회를 만들 뿐이고, 닫는 것은 **구성**이다.
 *
 * `MarginInputs` 의 생성자와 `copy` 는 `internal` 이다 — 모듈 밖에서 날 `Rate?` 로 입력을 지을
 * 길이 없다. 같은 모듈 안에서는 4B-5 의 승인된 test 가 그 생성자로 경계값(`0.0`·`1.0`·`1.2`)을
 * 계속 재고(그 거동을 이 slice 가 지우지 않는다), production 의 생성 자리는 이 함수 하나임을
 * 아키텍처 등식이 잠근다.
 */
fun marginInputsFrom(
    recommendedRate: Rate,
    floorRate: PlausibleFloorRate?,
    predictedRate: Rate,
    priceFitness: UnitScore,
    capacity: UnitScore,
): MarginInputs =
    MarginInputs(
        recommendedRate = recommendedRate,
        floorRate = floorRate?.rate,
        predictedRate = predictedRate,
        priceFitness = priceFitness,
        capacity = capacity,
    )

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
