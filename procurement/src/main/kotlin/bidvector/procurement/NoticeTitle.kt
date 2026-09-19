package bidvector.procurement

/**
 * 공고명(D-6F4-1·9, M6/6F-4) — 감시 키워드 매칭 입력의 조각 하나(`bidvector.strategy.
 * KeywordScopeText`로 조립된다, D-6F4-3). 원문 **trim만** 하고 정규화하지 않는다([AgencyName]과
 * 같은 관례 — 표기 흔들림을 그대로 보존한다). 생성은 [of] 하나뿐이고 `@ConsistentCopyVisibility`
 * 로 `copy()`도 그 경계 밖으로 나가지 않는다.
 *
 * trim 뒤 빈 문자열이면 `null`이다(D-6F4-8 — 「없음」은 센티넬이 아니라 타입으로 닫는다,
 * business control flow에 예외를 쓰지 않는다).
 */
@ConsistentCopyVisibility
data class NoticeTitle private constructor(
    val value: String,
) {
    companion object {
        fun of(raw: String): NoticeTitle? {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) null else NoticeTitle(trimmed)
        }
    }
}
