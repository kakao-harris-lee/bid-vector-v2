package bidvector.adapters.extraction

import bidvector.procurement.ExtractedRequirements

/**
 * 예산 초과 축 — 어느 상한을 넘었는지 회계·provenance 에 남긴다. **`TokenCount`는 아직
 * main 에 강제 지점이 없다**(verifier r1 F-4 관측) — `maxTokensPerCall`은 LLM 요청 본문의
 * `max_tokens`로 나갈 뿐 지역에서 실제 토큰 수를 세지 않는다. 어휘만 선언해 두고 값을
 * 지어내지 않는다 — 강제는 실측(응답 토큰 사용량 관측) 뒤 추가한다.
 */
sealed interface BudgetExceededKind {
    data object ChunkCount : BudgetExceededKind

    data object CallCount : BudgetExceededKind

    data object TokenCount : BudgetExceededKind
}

/**
 * 추출 실패 세부 사유(D-3C-3 (a)) — **adapters 소유**다. 1C `UncertainReason`에 1:1로
 * 없으므로 여기 두고, 1C 변환(`ExtractionToQualification`)이 「취득 실패 → CollectionFailed,
 * 나머지 전부 → CollectionFailed」로 접는다(design review §(3) 결정 — procurement 는
 * 이 세부를 모른다, 세부는 이 타입과 provenance·회계에만 남는다).
 */
sealed interface ExtractionFailure {
    data object FetchFailed : ExtractionFailure

    data class UnsupportedFormat(
        val mediaType: String,
    ) : ExtractionFailure

    data class SchemaViolation(
        val schemaVersion: String,
        val messages: List<String>,
    ) : ExtractionFailure

    data object Timeout : ExtractionFailure

    data object BreakerOpen : ExtractionFailure

    data class BudgetExceeded(
        val kind: BudgetExceededKind,
    ) : ExtractionFailure

    data object EmptyResponse : ExtractionFailure

    data class TransportFailed(
        val detail: String,
    ) : ExtractionFailure
}

/**
 * 이 slice 의 실제 추출 결과(adapters 소유, `RequirementExtractionPort.ExtractionOutcome`
 * 보다 풍부하다) — `WatchGatedExtractor`·`ExtractionToQualification`이 이 타입을 쓴다.
 * `Extracted`는 요건 행이 비어 있을 수 없다(procurement `ExtractedRequirements` 자체
 * 불변식이 이미 보장 — 이 타입은 그 값을 감쌀 뿐 재검사하지 않는다).
 */
sealed interface ExtractionAttempt {
    data class Extracted(
        val requirements: ExtractedRequirements,
        val provenance: List<ExtractionProvenance>,
    ) : ExtractionAttempt

    data class Uncertain(
        val reason: ExtractionFailure,
    ) : ExtractionAttempt
}
