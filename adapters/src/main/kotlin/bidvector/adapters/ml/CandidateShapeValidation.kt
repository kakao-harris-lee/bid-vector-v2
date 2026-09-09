package bidvector.adapters.ml

import contract.bidvector.ml.v1.Success

/**
 * verifier r2 G-1(high) — `BidRateCandidates.init`(conservative≤base≤aggressive, legacy
 * `scenario_spec.py`의 `CANDIDATE_SCENARIOS` sign 순서에서 온 방향)이 구성 시점에 던지기
 * 전에, 구조 검증층(`ParsedSuccessFields.isAcceptableSuccessShape`)이 같은 조건을 먼저
 * 잰다 — F-2 의 `hasNonBlankRelease`와 동형 패턴이다. 이 검사가 없으면 정직하지 않은
 * 순서의 응답이 `IllegalArgumentException`으로 `predict` 밖까지 샌다(scope.md ④ 위반).
 * `ParsedSuccessFields.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions`).
 */
internal fun hasOrderedCandidateRates(success: Success): Boolean {
    val rates = success.candidatesList.mapNotNull { it.bidRate.fraction.toRateOrNull() }
    // 파싱 실패(정규형 위반 등)는 이 검사의 책임이 아니다 — parsedSuccessFields 의
    // allNotNull 이벤트가 그 자리를 잡는다(중복 판정 방지, 이 검사는 순서만 잰다).
    if (rates.size != EXPECTED_CANDIDATE_COUNT) return true
    return rates[0] <= rates[1] && rates[1] <= rates[2]
}

/** 2B ③ — 후보는 항상 conservative·base·aggressive 셋(라벨 순서는 `hasExactlyThreeOrderedCandidates`가 잰다). */
private const val EXPECTED_CANDIDATE_COUNT = 3
