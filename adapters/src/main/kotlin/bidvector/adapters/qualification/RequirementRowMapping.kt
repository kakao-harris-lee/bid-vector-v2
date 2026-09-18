package bidvector.adapters.qualification

import bidvector.qualification.LicenseName
import bidvector.qualification.LmtGrpNo
import bidvector.qualification.LmtSno
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField

/** `notice_requirement.status` 저장값 — `RequirementCollection.CollectionFailed`·`Collected`에만 대응한다(D-6F5-4). */
internal enum class RequirementCollectionStatus {
    FAILED,
    COLLECTED,
}

/** `notice_requirement_row.kind` 저장값 — `RequirementRow`(Parsed·Unparsable)와 1:1이다. */
internal enum class RequirementRowKind {
    PARSED,
    UNPARSABLE,
}

/**
 * `notice_requirement_row` 한 행의 raw 컬럼값 — JDBC 왕복 전용 중간 표현(`internal`, D-3
 * 관례). PARSED만 `sourceField`·`licenseNames`를 갖는다(UNPARSABLE은 도메인 타입 자체에 그
 * 필드가 없다 — 값을 지어내지 않는다).
 */
internal data class RequirementRowRecord(
    val serialNo: String,
    val kind: RequirementRowKind,
    val groupNo: String?,
    val sourceField: RequirementSourceField?,
    val licenseNames: List<String>?,
)

/**
 * 저장 형태 — 헤더 상태 + 행 목록. `null`은 `DataAbsent`(헤더 행 자체를 두지 않는다,
 * D-6F5-2 「저장된 요건이 없으면 DataAbsent를 그대로 커널에 넘긴다」의 반대 방향).
 */
internal fun RequirementCollection.toStoredForm(): Pair<RequirementCollectionStatus, List<RequirementRowRecord>>? =
    when (this) {
        RequirementCollection.DataAbsent -> null
        RequirementCollection.CollectionFailed -> RequirementCollectionStatus.FAILED to emptyList()
        is RequirementCollection.Collected -> RequirementCollectionStatus.COLLECTED to rows.map(::toRecord)
    }

private fun toRecord(row: RequirementRow): RequirementRowRecord =
    when (row) {
        is RequirementRow.Parsed ->
            RequirementRowRecord(
                serialNo = row.serialNo.value,
                kind = RequirementRowKind.PARSED,
                groupNo = row.groupNo?.value,
                sourceField = row.sourceField,
                licenseNames = row.licenseNames.map { it.value },
            )

        is RequirementRow.Unparsable ->
            RequirementRowRecord(
                serialNo = row.serialNo.value,
                kind = RequirementRowKind.UNPARSABLE,
                groupNo = null,
                sourceField = null,
                licenseNames = null,
            )
    }

/**
 * 저장값 → `RequirementCollection`(읽기 경로, D-6F5-2). `status`가 `null`이면(헤더 행 없음)
 * `DataAbsent`를 지어내지 않고 그대로 낸다 — 이 함수가 게이트가 소비하는 유일한 복원
 * 경로다.
 */
internal fun toRequirementCollection(
    status: RequirementCollectionStatus?,
    records: List<RequirementRowRecord>,
): RequirementCollection =
    when (status) {
        null -> RequirementCollection.DataAbsent
        RequirementCollectionStatus.FAILED -> RequirementCollection.CollectionFailed
        RequirementCollectionStatus.COLLECTED -> RequirementCollection.Collected(records.map(::toRow))
    }

private fun toRow(record: RequirementRowRecord): RequirementRow =
    when (record.kind) {
        RequirementRowKind.PARSED ->
            RequirementRow.Parsed(
                groupNo = record.groupNo?.let(::LmtGrpNo),
                serialNo = LmtSno(record.serialNo),
                sourceField = requireNotNull(record.sourceField) { "PARSED 행의 sourceField가 없다: ${record.serialNo}" },
                licenseNames =
                    requireNotNull(record.licenseNames) { "PARSED 행의 licenseNames가 없다: ${record.serialNo}" }
                        .map(::LicenseName),
            )

        RequirementRowKind.UNPARSABLE -> RequirementRow.Unparsable(serialNo = LmtSno(record.serialNo))
    }
