package bidvector.workflow.evaluation

import bidvector.qualification.OperatorLicenses
import bidvector.strategy.WatchSubject
import bidvector.workflow.embedding.TextKind

/**
 * 합성 규약 version(scope.md ①) — 4D-2 임베딩 port의 `feature_schema_version` 인자로
 * 그대로 나간다(2E `embedding.proto` 머리말 — 이 RPC에서 그 필드는 텍스트 합성 규약의
 * version이라는 뜻).
 */
data class SynthesisVersion(
    val value: String,
)

/**
 * 합성된 텍스트(scope.md ①) — 유일한 생성 경로는 [synthesizeNoticeText]·
 * [synthesizeProfileText]다(`internal constructor`, 위협 모델 우회 (1) — 문자열을 직접
 * 넣어 조립할 수 없다).
 */
@ConsistentCopyVisibility
data class SynthesizedText internal constructor(
    val value: String,
    val kind: TextKind,
    val version: SynthesisVersion,
)

/**
 * [synthesizeNoticeText]·[synthesizeProfileText]의 결과(설계 검토 (4) 우회 (8)) — 2E는
 * 빈/공백 텍스트를 `INVALID_REQUEST`로 거부하므로, 합성기는 빈 텍스트를 만들지 않고
 * [Empty]를 낸다(예외가 아니다). `Empty`를 `Unavailable`로 옮기는 처분은 4B-6b 소관.
 */
sealed interface SynthesisOutcome {
    data class Synthesized(
        val text: SynthesizedText,
    ) : SynthesisOutcome

    data object Empty : SynthesisOutcome
}

/** 합성 규약 v1 구분자 — 정책이 아니라 규약 상수다(D-4B6A-3, version이 바뀌면 함께 바뀐다). */
private const val SEPARATOR = " "

/**
 * NOTICE 합성(D-4B6A-1, 규약 v1) — [WatchSubject.categories]·[WatchSubject.keywordText]·
 * [WatchSubject.fullText] 세 조각만 순서대로 [SEPARATOR]로 이어 붙인다(빈 조각은 생략).
 * **금액·마감·공고 id는 인자에 아예 없다**(위협 모델 방어 (a)(c)) —
 * [WatchSubject.baseAmount]는 읽지 않는다(legacy `_project_semantic_text`와 달리 의도적
 * 제외, D-4B6A-1). 카테고리는 [bidvector.strategy.CategoryCode.value] 사전순 정렬.
 * 상한 절단은 [truncateToPolicy] — 뒤를 자른다(D-4B6A-3).
 */
fun synthesizeNoticeText(
    subject: WatchSubject,
    policy: OpportunityPolicyData,
): SynthesisOutcome {
    val categoriesPart =
        subject.categories
            .map { it.value }
            .sorted()
            .joinToString(SEPARATOR)
    val parts = listOf(categoriesPart, subject.keywordText.value, subject.fullText.value)
    return synthesize(parts, TextKind.NOTICE, policy)
}

/**
 * PROFILE 합성(D-4B6A-1, 규약 v1) — [ProfileFacts]의 세 필드(`businessTypes`·`licenses`·
 * `regionTerms`)가 합성 fact allow-list 자체다(사업자번호·대표자·연락처는 필드가 없어
 * 구조적으로 못 들어온다, 위협 모델 방어 (a)). 면허 [OperatorLicenses.NotDeclared]는 그
 * 조각을 생략한다 — 「면허 없음」을 지어내지 않는다. 순서: 업종(사전순 정렬) → 면허
 * (`Declared`면 이름 사전순 정렬) → 지역(입력 순서 그대로).
 */
fun synthesizeProfileText(
    profile: ProfileFacts,
    policy: OpportunityPolicyData,
): SynthesisOutcome {
    val businessTypesPart =
        profile.businessTypes
            .map { it.value }
            .sorted()
            .joinToString(SEPARATOR)
    val licensesPart =
        when (val licenses = profile.licenses) {
            is OperatorLicenses.Declared -> {
                licenses.licenseNames
                    .map { it.value }
                    .sorted()
                    .joinToString(SEPARATOR)
            }

            OperatorLicenses.NotDeclared -> {
                ""
            }
        }
    val regionsPart = profile.regionTerms.joinToString(SEPARATOR)
    val parts = listOf(businessTypesPart, licensesPart, regionsPart)
    return synthesize(parts, TextKind.OPERATOR_PROFILE, policy)
}

/**
 * verifier r1 F-1(medium) — 절단은 코드포인트 수만 보고 자르므로, 첫 비공백 조각의 앞
 * `textMaxChars`개가 전부 공백이면(전각 공백·탭 등) 절단 결과가 다시 공백뿐일 수 있다.
 * `SynthesisOutcome` KDoc·설계 검토 (8)의 불변식(「빈 텍스트를 만들지 않는다」)을 지키려면
 * 절단 **뒤**에도 blank 재검사가 필요하다 — 절단 전 검사만으로는 이 경로를 못 막는다.
 */
private fun synthesize(
    parts: List<String>,
    kind: TextKind,
    policy: OpportunityPolicyData,
): SynthesisOutcome {
    val combined = parts.filter { it.isNotBlank() }.joinToString(SEPARATOR)
    val truncated = if (combined.isBlank()) null else truncateToPolicy(combined, policy.textMaxChars)
    return if (truncated.isNullOrBlank()) {
        SynthesisOutcome.Empty
    } else {
        SynthesisOutcome.Synthesized(SynthesizedText(truncated, kind, policy.synthesisVersion))
    }
}

/**
 * 상한 초과 시 뒤를 자른다(D-4B6A-3 — 앞부분이 카테고리·업종이라 더 중요하다는 규약 판단,
 * scope.md ②). 코드포인트 단위로 잘라 서로게이트 쌍이 갈라지지 않는다.
 */
private fun truncateToPolicy(
    text: String,
    maxChars: Int,
): String {
    val codePointCount = text.codePointCount(0, text.length)
    if (codePointCount <= maxChars) return text
    val endIndex = text.offsetByCodePoints(0, maxChars)
    return text.substring(0, endIndex)
}
