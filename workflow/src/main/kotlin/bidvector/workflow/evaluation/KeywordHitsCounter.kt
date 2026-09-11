package bidvector.workflow.evaluation

import bidvector.decision.priority.derive.KeywordHits
import bidvector.strategy.FullScopeText

/**
 * 정책 키워드 매칭(4B-5 D-4B5-5 인계, legacy `opportunity_analysis/scoring.py`
 * `_estimate_execution_complexity_score` — `sum(1 for keyword in KEYWORDS if keyword in
 * text)`) — 소문자 부분 문자열 포함 여부로 센다. `in`은 포함 검사이지 출현 횟수가
 * 아니므로, 같은 키워드가 텍스트에 여러 번 나와도 1회만 센다([policy]의 키워드는
 * [OpportunityPolicyData.init]이 이미 소문자로 강제한다). **단어 경계를 보지 않는다**
 * (legacy `keyword in text`와 동일한 규약) — 다른 단어 내부에 나타나도 매치되지만, 글자
 * 사이에 공백이 끼면 매치되지 않는다(verifier r1 F-3 고정 test 참고).
 */
object KeywordHitsCounter {
    fun count(
        fullText: FullScopeText,
        policy: OpportunityPolicyData,
    ): KeywordHits {
        val haystack = fullText.value.lowercase()
        val hits = policy.keywords.count { keyword -> haystack.contains(keyword) }
        return KeywordHits(hits)
    }
}
