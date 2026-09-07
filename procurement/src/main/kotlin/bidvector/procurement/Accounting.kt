package bidvector.procurement

/** scale/basis 계약 위반의 축 — `ContractViolation(scale/basis)`(D-3A-6). */
enum class ContractViolationAxis {
    SCALE,
    BASIS,
}

/** 파싱 실패의 종류 — `ParseFailure(kind)`(D-3A-6). */
enum class ParseFailureKind {
    NUMERIC,
    DATE_TIME,
    IDENTIFIER,
}

/**
 * 수집 회계가 관측한 항목 탈락 사유(D-3A-6) — 3A 소유 sealed. `Collection` 접두는 OPS-09
 * (실행 실패 분류, 아직 미착수)의 어휘와 겹치지 않기 위해서다. **소스 중립**이다(조사
 * G-5 — legacy는 공고 경로 `parse_rejected`·개찰 경로 `missing_notice_number`로 어휘가
 * 갈렸으나, V2는 어느 소스에서 왔든 같은 사유 어휘를 쓴다). `Duplicate`가 없다 — COL-06의
 * 항등식은 `duplicate`와 `dropped`가 서로소인 셈이라(회계 `init`, [CollectionAccounting]),
 * 중복은 이 사유 어휘의 대상이 아니다.
 */
sealed interface CollectionDropReason {
    data object CollectionMissingNoticeNumber : CollectionDropReason

    data object CollectionUnknownField : CollectionDropReason

    data class CollectionContractViolation(
        val axis: ContractViolationAxis,
    ) : CollectionDropReason

    data class CollectionParseFailure(
        val kind: ParseFailureKind,
    ) : CollectionDropReason
}

/**
 * 수집 회계(⑦, COL-06) — `received = normalized + duplicate + dropped` 항등식은 생성자
 * 불변식이다(위반 시 생성 실패, `copy()`도 이 생성자를 다시 지나므로 재검사된다). legacy의
 * `setdefault` 채움·뺄셈 역산(`cap_skipped_count`)은 채택하지 않는다 — 값은 전부 호출부가
 * 직접 센 수만 받는다.
 */
data class CollectionAccounting(
    val received: Int,
    val normalized: Int,
    val duplicate: Int,
    val dropped: Int,
    val dropReasons: Map<CollectionDropReason, Int>,
    val sourceTotal: Int?,
    val pagesFetched: Int,
    val truncated: Boolean,
    val unknownFields: Int,
) {
    init {
        require(received >= 0 && normalized >= 0 && duplicate >= 0 && dropped >= 0) {
            "수집 회계 값은 음수일 수 없다: received=$received normalized=$normalized duplicate=$duplicate dropped=$dropped"
        }
        require(received == normalized + duplicate + dropped) {
            "COL-06 항등식 위반 — received=$received != normalized($normalized)+duplicate($duplicate)+dropped($dropped)"
        }
        require(dropReasons.values.all { it >= 0 }) { "dropReasons 값은 음수일 수 없다" }
        require(dropReasons.values.sum() == dropped) {
            "dropReasons 합(${dropReasons.values.sum()})은 dropped($dropped)와 같아야 한다"
        }
        require(pagesFetched >= 0) { "pagesFetched는 음수일 수 없다" }
        require(unknownFields >= 0) { "unknownFields는 음수일 수 없다" }
        if (sourceTotal != null) require(sourceTotal >= 0) { "sourceTotal은 음수일 수 없다" }
    }
}
