package bidvector.adapters.extraction

import bidvector.procurement.ExtractedRequirementItem
import bidvector.procurement.ExtractedSourceField
import bidvector.qualification.LicenseName
import bidvector.qualification.LmtGrpNo
import bidvector.qualification.LmtSno
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField

/**
 * 3C 추출 결과 → 1C `RequirementCollection` 변환(D-3C-3, 착수 시 계약 고정 ③) —
 * adapters 한 함수. `procurement`·`qualification`이 서로를 참조하지 않으므로(ADR 0006
 * D-4) 이 변환은 두 도메인을 모두 보는 이 층에만 설 수 있다.
 *
 * `Extracted`(요건 있음) → `Collected(rows)`. `Uncertain`(사유 무관 — 취득 실패든 schema
 * 위반이든 timeout·breaker open·예산 초과든)은 전부 `CollectionFailed`로 접는다 — 세부
 * 사유는 1C 가 구분할 개념이 아니라 이 함수 밖 [ExtractionFailure]와 provenance·회계에만
 * 남는다(design review §(3) 최종 결정 — "그 밖 전부 CollectionFailed", scope D-3C-3 의
 * 「나머지 → RequirementUnparsable」은 실제 `RequirementRow.Unparsable` 행이 있어야
 * 나오는 값이라 행 단위 사유에만 쓴다 — 문서 전체 실패에 그 행을 지어내지 않는다).
 *
 * **판단이 갈린 지점(verifier r1 판단 셋 (3), 근거 정정 L-5)**: 모델이 "요건 없음"이라고
 * 명시한 [ExtractedRequirements.assertedAbsent]도 `CollectionFailed`로 접는다(scope.md
 * ⑦의 "RequirementUnparsable" 문면과 다르다). **정정**: `Collected(emptyList())`를
 * 그대로 뒀어도 1C `LicenseEligibility.judgeCollected`가 내는 `RequirementDataAbsent`는
 * `LicenseVerdict.Uncertain`의 한 **사유**이지 자격 「없음」의 **확정**이 아니다 — 그러니
 * "확정된 없음이 되어 버린다"는 이전 서술은 부정확했다. 실제 이유는 둘이다: (a)
 * `RequirementUnparsable`에 닿으려면 실재하지 않는 자리표시 `LmtSno`로 가짜
 * `Unparsable` 행을 지어내야 한다(§(3)이 경계한 안티패턴) (b) `RequirementDataAbsent`와
 * `CollectionFailed`(→ `Uncertain(CollectionFailed)`) 둘 다 이미 `Uncertain`이라 자격
 * 통과·확정 없음 어느 쪽도 생기지 않는다 — 두 선택 모두 안전하고, `CollectionFailed`를
 * 고른 것은 "취득이 불완전했다"는 사유가 "값이 없다고 확인됐다"는 사유보다 이 경우(모델의
 * 자기 주장, 사람 확인 전)에 더 정확하다는 판단이다.
 */
fun toRequirementCollection(attempt: ExtractionAttempt): RequirementCollection =
    when (attempt) {
        is ExtractionAttempt.Extracted -> {
            if (attempt.requirements.assertedAbsent) {
                RequirementCollection.CollectionFailed
            } else {
                RequirementCollection.Collected(attempt.requirements.items.map(::toRequirementRow))
            }
        }

        is ExtractionAttempt.Uncertain -> {
            RequirementCollection.CollectionFailed
        }
    }

private fun toRequirementRow(item: ExtractedRequirementItem): RequirementRow =
    RequirementRow.Parsed(
        groupNo = item.groupNo?.let { LmtGrpNo(it.value) },
        serialNo = LmtSno(item.serialNo.value),
        sourceField = toRequirementSourceField(item.sourceField),
        licenseNames = item.licenseNames.map(::LicenseName),
    )

private fun toRequirementSourceField(field: ExtractedSourceField): RequirementSourceField =
    when (field) {
        ExtractedSourceField.LicenseLimitName -> RequirementSourceField.LcnsLmtNm
        ExtractedSourceField.PermittedIndustryList -> RequirementSourceField.PermsnIndstrytyList
    }
