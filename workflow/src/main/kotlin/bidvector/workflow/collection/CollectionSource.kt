package bidvector.workflow.collection

import bidvector.procurement.NoticeSourcePort
import java.time.LocalDate

/**
 * 업종(수집 채널) 이름 — 회계·로그의 식별자다. 소문자 영문으로 시작하는 짧은 토큰만 허용해 어떤 값도
 * 로그 한 줄에 그대로 실려도 안전하다(공백·개행·`=` 없음). 유효하지 않은 원문은 `null` 이다.
 */
@ConsistentCopyVisibility
data class CollectionSourceName private constructor(
    val value: String,
) {
    companion object {
        private val SHAPE = Regex("[a-z][a-z0-9-]{0,31}")

        fun of(raw: String): CollectionSourceName? = if (SHAPE.matches(raw)) CollectionSourceName(raw) else null
    }
}

/** 업종 하나의 공고 목록 소스 — 어느 오퍼레이션인지는 [port] 구현이 안다(이 타입은 이름만 붙인다). */
class CollectionSource(
    val name: CollectionSourceName,
    val port: NoticeSourcePort,
)

/** 수집의 최소 단위 — 조회일 하나 × 업종 하나. 회계 한 줄이 이 단위로 남는다(D-6F8-1). */
data class CollectionSlot(
    val referenceDate: LocalDate,
    val source: CollectionSourceName,
)
