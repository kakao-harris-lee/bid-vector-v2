package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Rate
import java.time.Duration

/**
 * ③ 기대 마진 입력(scope.md) — 다섯 축 전부 non-null(D-4B5-6). `capacity` 부재가 이 값
 * 자체를 못 만들게 하는 사유(`Absent(PredictionMissing)`)는 4B-6 이 [deriveExpectedMargin]
 * 을 부르지 않는 것으로 낸다 — 이 타입은 「이미 확보된 다섯」만 나른다.
 */
data class MarginInputs(
    val recommendedRate: Rate,
    val floorRate: Rate?,
    val predictedRate: Rate,
    val priceFitness: UnitScore,
    val capacity: UnitScore,
)

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
