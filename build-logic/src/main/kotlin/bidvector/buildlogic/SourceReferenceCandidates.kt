package bidvector.buildlogic

// [SourceReferences] 의 후보 조립 — `SourceReferences.kt` 와 같은 detekt `TooManyFunctions`
// 예산을 나눠 쓴다(v2-지침서.md §5 「크기와 결합도」, `PublicApiTypes`/`PublicApiTypeSurface`
// 분리와 같은 이유).

/**
 * 소문자 세그먼트를 접두로 모으고 첫 대문자 세그먼트까지가 후보다(설계 검토 §4 단계 2·3).
 * 대문자 세그먼트가 이어지면 `$` 로 이어 붙인 형태도 함께 낸다 — **전부** 허용이어야
 * 한다(`java.util.Map`·`java.util.Map$Entry` 를 둘 다 재는 이유, 단계 4).
 *
 * 첫 세그먼트가 대문자면 단순 이름 참조라 건너뛴다 — import 나 같은 파일 선언이 이미 푼다.
 * 대문자 세그먼트가 아예 없으면 값 체인이라 건너뛴다.
 *
 * **타입 축의 첫 세그먼트(뿌리) 다음은 SCREAMING_CASE 면 잇지 않는다**(verifier r18 F-3).
 * `java.lang.Integer.MAX_VALUE` 처럼 상수 멤버가 대문자로 시작하면 존재하지 않는 중첩
 * 클래스 `Integer$MAX_VALUE` 후보가 생겨 허용된 `Integer` 접근까지 오탐으로 잡힌다 —
 * 뿌리 세그먼트 자신은 이 규칙의 대상이 아니다(클래스 이름이 우연히 전부 대문자여도 후보는
 * 낸다).
 */
internal fun candidateForms(segments: List<String>): List<String> {
    val typeIndex = segments.indexOfFirst { it.startsWithUpper() }
    val isSimpleNameOrValueChain = segments.isEmpty() || segments.first().startsWithUpper() || typeIndex < 0
    if (isSimpleNameOrValueChain) return emptyList()

    val prefix = segments.subList(0, typeIndex).joinToString(".")
    val root = segments[typeIndex]
    val nested =
        segments.subList(typeIndex + 1, segments.size).takeWhile {
            it.startsWithUpper() &&
                !it.isScreamingCase()
        }
    val typeSegments = listOf(root) + nested
    return typeSegments.indices.map { i -> "$prefix." + typeSegments.subList(0, i + 1).joinToString("$") }
}

private fun String.startsWithUpper(): Boolean = isNotEmpty() && first().isUpperCase()

/** 대문자·숫자·`_` 만으로 된 두 글자 이상 — 관례상 상수 멤버 이름(`MAX_VALUE`·`HTTP_OK`). */
private fun String.isScreamingCase(): Boolean = length >= 2 && all { it.isUpperCase() || it.isDigit() || it == '_' }
