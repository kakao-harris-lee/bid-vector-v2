package bidvector.procurement

/**
 * scale/basis/range 계약 위반의 축 — `ContractViolation(scale/basis/range)`(D-3A-6).
 * `RANGE`는 v2-defect 수정(3A 잔여 일괄 verifier r3 전, koneps-collection-002)이 더했다 —
 * [RangeBand]가 참조하는 `expectedRange`(§5.3 규율 2 단일 출처) 위반을 나른다.
 */
enum class ContractViolationAxis {
    SCALE,
    BASIS,
    RANGE,
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
 * 걷기가 `truncated` 로 끝난 실제 사유(M3/3B 좁은 확장, 운영자 결정 2026-09-07 verifier r1
 * H-3) — 3B 의 page-walk 가 백스톱·재시도 소진으로 종료될 때 「왜 빠졌는가」를 회계가
 * 구별하게 한다(COL-06 사용자 가치). 3B(어댑터)만 이 값을 만든다 — procurement 는 형태만
 * 소유한다.
 */
sealed interface TruncationCause {
    /** 정책 `maxPages` 백스톱. */
    data object MaxPages : TruncationCause

    /** 동일 페이지 내용 반복 감지. */
    data object RepeatedPage : TruncationCause

    /** HTTP 429 또는 `resultCode 22` — quota 신호(두 표면, D-3B-7)가 재시도를 소진시켰다. */
    data object QuotaExhausted : TruncationCause

    /** 전송 timeout 이 재시도를 소진시켰다. */
    data object Timeout : TruncationCause

    /** timeout 이 아닌 전송 실패(연결 거부 등)가 재시도를 소진시켰다. */
    data object TransportFailure : TruncationCause

    /** `resultCode` 가 3A 표의 재시도 가능 범주(01/02/04/05)인 채로 재시도를 소진시켰다. */
    data object ServerError : TruncationCause

    /** `resultCode` 가 3A 표의 비재시도 범주(12/20/30/31/32). */
    data object NotRetryable : TruncationCause

    /** `resultCode` 가 3A 표의 입력 오류 범주(06/07/08/10/11), 또는 cursor 토큰 자체가 무효(M-5). */
    data object InputError : TruncationCause

    /** `resultCode` 가 부재이거나 3A 표에 없다(fail-safe 비재시도). */
    data object Unclassified : TruncationCause

    /** envelope JSON 구조 자체가 무너짐(파싱 실패·필수 형태 위반). */
    data object StructureFailure : TruncationCause

    /** rate limiter 자체 거부(허가 대기 시간 초과)가 재시도를 소진시켰다 — 호출조차 못 나갔다. */
    data object SelfThrottled : TruncationCause
}

/**
 * 수집 회계(⑦, COL-06) — `received = normalized + duplicate + dropped` 항등식은 생성자
 * 불변식이다(위반 시 생성 실패, `copy()`도 이 생성자를 다시 지나므로 재검사된다). legacy의
 * `setdefault` 채움·뺄셈 역산(`cap_skipped_count`)은 채택하지 않는다 — 값은 전부 호출부가
 * 직접 센 수만 받는다.
 *
 * **`truncationCause`·`quotaExceeded`·`backoffSkipped`는 M3/3B 좁은 확장이다**(운영자 결정
 * 2026-09-07, verifier r1 H-3) — 기본값이 있어 기존 생성자 호출처(3A corpus 실행자·
 * `AccountingTest`)는 그대로 컴파일된다. `truncated`↔`truncationCause` 결합 불변식만
 * 새로 추가한다 — 다른 기존 불변식은 손대지 않는다.
 *
 * **`maskingFailures`는 M3/3B-2 좁은 확장이다**(운영자 결정 2026-09-08, verifier r1 F-3·F-8) —
 * 개찰 축이 `opengCorpInfo` 성분 배치 불일치로 값 전체를 폐기한 건수를 `unknownFields`와
 * **분리**해 낸다. 이전 판은 이 사유를 `unknownFields`(이름 그대로면 「계약 밖 키 수」)에
 * 얹어 그 축의 원래 의미(§5.3 규율 1의 미지 필드 리포트)를 비웠다 — 이번 확장은 그 자리를
 * 되돌리고(`unknownFields`는 다시 계약 밖 키만 센다) masking 실패를 별도 축으로 세운다.
 * **`dropReasons`/`dropped` 항등식 밖에 둔다** — 필드 단위 실패는 항목 단위 drop 과 다른
 * 축이라(같은 항목 안의 일부 필드만 폐기되고 항목 자체는 살아남는다) 그 항등식에 강제로
 * 넣으면 「항목이 몇 개 왔는가」와 「항목 안에서 무엇이 빠졌는가」가 뒤섞인다. 기본값이
 * 있어 기존 호출처(3A corpus 실행자·`AccountingTest`·3B `mapRawItem` 경로)는 그대로
 * 컴파일된다.
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
    val truncationCause: TruncationCause? = null,
    val quotaExceeded: Int = 0,
    val backoffSkipped: Int = 0,
    val maskingFailures: Int = 0,
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
        require(truncated == (truncationCause != null)) {
            "truncated 는 truncationCause 존재와 같아야 한다(H-3) — truncated=$truncated truncationCause=$truncationCause"
        }
        require(quotaExceeded >= 0) { "quotaExceeded는 음수일 수 없다" }
        require(backoffSkipped >= 0) { "backoffSkipped는 음수일 수 없다" }
        require(maskingFailures >= 0) { "maskingFailures는 음수일 수 없다" }
    }
}
