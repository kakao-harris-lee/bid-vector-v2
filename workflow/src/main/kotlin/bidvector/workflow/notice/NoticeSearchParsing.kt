package bidvector.workflow.notice

import java.time.Instant

// 원문 질의 값의 파싱 원시 함수들 — `NoticeSearchCriteria.kt` 의 거부 규칙표와 조건 조립이
// 공유한다(파일당 함수 수 한도로 갈랐을 뿐 같은 관심사다: 「문자열 하나를 값으로 읽거나
// 못 읽었다고 답한다」).
//
// 전부 조용한 강제 변환을 하지 않는다 — 읽을 수 없으면 `null` 이고, 그 `null` 을 거부로
// 옮기는 것은 규칙표의 일이다. `internal` 인 이유는 같은 패키지의 두 파일이 나눠 쓰기
// 때문이고, 모듈 밖으로는 나가지 않는다.

/** 공백만이면 조건이 아니다 — 「아무것도 안 맞는다」가 조용히 성립하지 않게 한다. */
internal fun String?.searchTextOrNull(): SearchText? = this?.trim()?.takeIf { it.isNotEmpty() }?.let(::SearchText)

internal fun String?.tooLong(): Boolean = this != null && length > NoticeSearchLimits.MAX_TEXT_LENGTH

internal fun String?.blankOrNull(): Boolean = this == null || isBlank()

internal fun String?.parsedWon(): Long? = this?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

internal fun String?.notInteger(): Boolean = !blankOrNull() && parsedWon() == null

internal fun String?.negative(): Boolean = (parsedWon() ?: 0L) < 0L

internal fun String?.parsedInt(): Int? = this?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

internal fun String?.notInteger32(): Boolean = !blankOrNull() && parsedInt() == null

/**
 * ISO-8601 순간만 받는다. `Instant.parse` 의 실패를 `runCatching` 으로 접는 이유는 결과
 * 타입으로 되돌리기 위함이다 — 이 모듈 밖으로 예외가 나가지 않는다.
 */
internal fun String?.parsedInstant(): Instant? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }

internal fun String?.notInstant(): Boolean = !blankOrNull() && parsedInstant() == null
