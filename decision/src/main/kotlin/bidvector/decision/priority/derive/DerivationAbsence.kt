package bidvector.decision.priority.derive

import bidvector.sharedkernel.ReasonCode

/**
 * 성분 파생 함수(4B-5)의 fact 부재 사유(D-4B5-1) — [bidvector.decision.MlUnavailableReason]
 * 과 다른 축이다: ML 이 답을 못 낸 것이 아니라 그 이전 단계에서 fact 자체가 없다. 4B-6 이
 * `PriorityInputs` 조립 시 이 사유를 `MlUnavailableReason` 으로 옮긴다(이 slice 밖 — 이
 * slice 는 그 사유 축을 소유하지 않는다).
 */
sealed interface DerivationAbsence {
    /** legacy 「base 없으면 중립 0.5」sentinel 의 뒤집기(scope.md ②, 우회 (1)). */
    data object BaseAmountMissing : DerivationAbsence

    /** 추천 투찰가 자체가 없다(scope.md ②). */
    data object RecommendationMissing : DerivationAbsence

    /** workload 집계 port 는 4B-6 — 이 slice 는 상수로 낸다(scope.md ⑦). */
    data object WorkloadNotCollected : DerivationAbsence

    /**
     * 시장 평균 fact 자체가 V2 에 없다(계약 갱신 2026-09-10 #1, `OPEN-4B5-COMPETITIVENESS`)
     * — 산술의 부재가 아니라 fact 의 부재라 `deriveCompetitiveness` 는 이 slice 밖이고
     * [competitivenessNotCollected] 가 상수로 낸다(⑦ `WorkloadNotCollected` 와 같은 축).
     */
    data object MarketAverageMissing : DerivationAbsence

    /**
     * [bidvector.sharedkernel.bidRateAgainst] 가 [bidvector.sharedkernel.Measurement.Unmeasurable]
     * 로 낸 잔여 사유(VAT 불일치·미선언 출처 등, scope.md ②) — `base`·`recommended` 존재
     * 검사를 통과한 뒤에만 도달한다(계약이 이름 붙인 `BaseAmountMissing`·
     * `RecommendationMissing` 두 사유 밖의 나머지를 흡수).
     */
    data class MoneyArithmeticUnmeasurable(
        val reason: ReasonCode,
    ) : DerivationAbsence

    /**
     * `Notice.floorRate` 가 `1` 을 넘는다(verifier r2 G-1, 사용자 승인 2026-09-10 4B-6
     * 인계) — `recommendedRate`·`predictedRate` 와 달리 `floorRate` 는 4D-1 계약(`D-2B-8`)의
     * 보호를 받지 않는다(`FloorRate`·`Canonicalize`·`NoticeReconstruction` 어디에도 `≤ 1`
     * 불변식이 없다, 전수 grep 0건). `MarginInputs.init` 이 이 값으로는 애초에 구성을
     * 거부하므로(`IllegalArgumentException`), **4B-6 조합기가 `MarginInputs` 생성 전에**
     * `Notice.floorRate > 1` 을 이 사유로 걸러야 한다 — 이 slice 는 사유 어휘만 낸다(값
     * 획득·필터링은 4B-6, D-4B5-1 과 같은 규율로 예외 대신 `Absent`).
     */
    data object FloorRateOutOfRange : DerivationAbsence
}

/**
 * 성분 파생 함수의 결과 — [bidvector.decision.priority.ScoreFact] 와 형태는 같지만
 * (Present/Absent) 부재 사유 어휘가 [DerivationAbsence] 다(D-4B5-1). `ScoreFact` 를 재사용
 * 하지 않는 것은 그 사유 축(`MlUnavailableReason`)이 이 slice 소유가 아니기 때문이다 —
 * 4B-4 파일도 편집하지 않는다(scope.md out_of_scope).
 */
sealed interface DerivationOutcome<out T> {
    data class Present<T>(
        val value: T,
    ) : DerivationOutcome<T>

    data class Absent(
        val reason: DerivationAbsence,
    ) : DerivationOutcome<Nothing>
}
