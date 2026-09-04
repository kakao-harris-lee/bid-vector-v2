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
 */
internal fun candidateForms(segments: List<String>): List<String> {
    val typeIndex = segments.indexOfFirst { it.startsWithUpper() }
    val isSimpleNameOrValueChain = segments.isEmpty() || segments.first().startsWithUpper() || typeIndex < 0
    if (isSimpleNameOrValueChain) return emptyList()

    val prefix = segments.subList(0, typeIndex).joinToString(".")
    val typeSegments = segments.subList(typeIndex, segments.size).takeWhile { it.startsWithUpper() }
    return typeSegments.indices.map { i -> "$prefix." + typeSegments.subList(0, i + 1).joinToString("$") }
}

private fun String.startsWithUpper(): Boolean = isNotEmpty() && first().isUpperCase()
