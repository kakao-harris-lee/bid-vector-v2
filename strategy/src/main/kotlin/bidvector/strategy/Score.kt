package bidvector.strategy

import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.ReasonCode
import java.math.BigDecimal

private val SCORE_MIN: BigDecimal = BigDecimal.ZERO
private val SCORE_MAX: BigDecimal = BigDecimal.ONE

/**
 * 점수 값 허용 범위(D-1, D-13) — 정책 데이터. `min`·`max` 자체는 `[0,1]`을 벗어날 수
 * 없다(점수 축의 구조적 정의) — 그 범위 **안에서** 더 좁은 실제 값은 main 이 지어내지
 * 않는다(`STRATEGY_POLICY`가 자리표시자로 전체 범위를 둔다).
 */
data class ScoreRange(
    val min: BigDecimal,
    val max: BigDecimal,
) {
    init {
        require(min >= SCORE_MIN) { "min은 0 이상이어야 한다: $min" }
        require(max <= SCORE_MAX) { "max는 1 이하여야 한다: $max" }
        require(min <= max) { "min은 max 이하여야 한다: min=$min max=$max" }
    }

    fun contains(value: BigDecimal): Boolean = value >= min && value <= max
}

/**
 * 무차원 점수 값(D-1 (a), 스카우트 §7.3) — `Rate`와 다른 축이다. `Rate`가 단위 크기 추측
 * 금지(`ADR 0002` D-4)로 상한을 두지 않는 것과 달리, 점수는 정의상 `[0,1]` 구조적 경계를
 * 갖는다(백분율·소수 표기의 단위 모호성이 없다 — ML이 내는 값은 항상 fraction이다).
 * 생성자는 `internal`이고 유일한 생성 경로는 [of]다 — `of`도 `internal`이라(verifier r1
 * F-1) 이 타입은 `strategy` 모듈 밖에서 만들 수 없다. `MatchScore`·`ProbabilityScore`·
 * `PriorityScore`가 이미 만들어진 값을 옮겨 담는 것은 여전히 공개다(값 읽기는 막지 않는다).
 */
@ConsistentCopyVisibility
data class Score internal constructor(
    val value: BigDecimal,
) {
    init {
        require(value >= SCORE_MIN && value <= SCORE_MAX) { "Score는 [0,1] 범위여야 한다: $value" }
    }

    companion object {
        /**
         * 정책이 정하는 축별 허용 범위([ScoreRange], D-13 「`[0,1]` 범위는 정책 데이터」)
         * 위의 범위 검사 factory(D-1). `Fact.Absent`의 사유는 `ReasonCode.POLICY_NOT_APPLICABLE`을
         * 재사용한다 — **판단이 갈린 지점**: D-16은 `StrategyViolation`·`WatchUndeterminableReason`
         * 두 사유 어휘에 `ReasonCode`를 더하지 않는다는 것이지, `Fact<Score>`가 요구하는
         * `ReasonCode` 필드(shared-kernel 타입 자체의 구조)를 우회할 방법을 주지 않는다.
         * 이 `Fact`는 [bidvector.strategy.validate] 안에서만 소비되고 그 결과는 항상
         * [StrategyViolation.ScoreOutOfRange]로 번역돼 밖으로 나간다 — 재사용한 `ReasonCode`
         * 값 자체는 호출부에 노출되지 않는다.
         *
         * `internal`이다(verifier r1 F-1) — 「밖으로 새지 않는다」가 KDoc 관례가 아니라
         * 가시성이 되게 한다. 저장소 전체 유일 호출자는 `StrategyValidation.kt`의 `validate`뿐
         * (`grep -rn "Score\.of("` 1건). 모듈 밖 호출은 컴파일 자체가 막는다
         * ([bidvector.strategy.CompileFailureHarnessTest] fixture 3).
         */
        internal fun of(
            value: BigDecimal,
            range: ScoreRange,
        ): Fact<Score> =
            if (range.contains(value)) {
                Fact.Known(Score(value))
            } else {
                Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE)
            }
    }
}

/** 매칭 점수 축(`data-dictionary.md` §1.4.2 점수 판, D-13) — 분석 결과의 카테고리·키워드 부합도. */
data class MatchScore(
    val score: Score,
)

/** 확률 점수 축(D-13) — 낙찰 확률 추정. */
data class ProbabilityScore(
    val score: Score,
)

/** 우선순위 점수 축(D-13) — `bidNowThreshold`·`reviewThreshold`가 비교하는 축. */
data class PriorityScore(
    val score: Score,
)
