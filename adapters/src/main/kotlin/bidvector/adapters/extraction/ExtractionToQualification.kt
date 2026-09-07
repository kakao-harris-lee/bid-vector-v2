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
 * **판단이 갈린 지점**: 모델이 "요건 없음"이라고 명시한 [ExtractedRequirements
 * .assertedAbsent]도 `CollectionFailed`로 접는다(scope.md ⑦의 "RequirementUnparsable"
 * 문면과 다르다) — `Collected(emptyList())`는 1C `LicenseEligibility.judgeCollected`가
 * 이미 `RequirementDataAbsent`로 판정하므로(itself out of scope, qualification 미편집)
 * 그대로 두면 scope 가 피하려던 「확정된 없음」이 되어 버리고, `RequirementUnparsable`에
 * 닿으려면 실재하지 않는 자리표시 `LmtSno`로 가짜 `Unparsable` 행을 지어내야 한다(§(3)이
 * 경계한 바로 그 안티패턴). 「모델의 요건 없음 주장은 사람 확인 전까지 신뢰하지 않는다」는
 * 목적은 `CollectionFailed`(→ `Uncertain(CollectionFailed)`)로도 달성된다 — verifier
 * 검토 대상으로 남긴다.
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
