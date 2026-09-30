package bidvector.procurement

import java.time.Duration
import java.time.Instant

/** [DetailFetchDecision.Skip]의 사유 — 백오프 상태를 데이터 컬럼에 얹지 않는다(COL-03 legacy 형태 처리). */
sealed interface DetailFetchSkipReason {
    data object AlreadyHeld : DetailFetchSkipReason

    data class AgeGateNotPassed(
        val until: Instant,
    ) : DetailFetchSkipReason

    data class RecheckGateNotPassed(
        val until: Instant,
    ) : DetailFetchSkipReason
}

/**
 * 「무엇을 언제 조회할 가치가 있는가」라는 도메인 판단(⑪, COL-03). [Fetch]는 `internal
 * constructor`다 — 유일한 생성 경로는 [decideDetailFetch]이고, 상세 조회 port 서명이 이
 * 값을 인자로 요구해(위협 모델 방어 (h)) 술어를 거치지 않은 호출이 컴파일되지 않는다.
 */
sealed interface DetailFetchDecision {
    @ConsistentCopyVisibility
    data class Fetch internal constructor(
        val noticeId: NoticeId,
        /**
         * 공고 키 해시 hex — **호출부가 이미 지은 값**이다. 어댑터가 호출 원장에 줄을 쓸 때
         * 필요하고, 도메인은 sha256 을 계산하지 않는다(architecture 게이트). 해시를 짓는 자리는
         * workflow 하나이므로 규칙이 두 벌이 되지 않는다.
         */
        val noticeKeyHash: String,
    ) : DetailFetchDecision {
        init {
            // 형태를 **값이 들어오는 자리**에서 닫는다 — 원장까지 흘러간 뒤에 잡으면 그 사이의
            // 어댑터는 이미 그 문자열을 URI·로그에 실었을 수 있다.
            require(NOTICE_KEY_HEX.matches(noticeKeyHash)) { NOTICE_KEY_HEX_MESSAGE }
        }
    }

    data class Skip(
        val reason: DetailFetchSkipReason,
    ) : DetailFetchDecision
}

/**
 * 「무엇을 언제 다시 조회할 가치가 있는가」의 값들 — age-gate·recheck-gate 시간(legacy 기본 24h/48h)과
 * 축 재호출 상한. 전부 정책 데이터다(매직넘버 금지).
 */
data class DetailFetchGates(
    val ageGateHours: Long,
    val recheckGateHours: Long,
    /**
     * 한 (공고, 축)을 **일시 실패로 다시 부를 횟수 상한**(D-6G2d-8 ⓒ). 앞 판에는 상한이 없어, 구조적
     * 으로 실패하는 축이 매 실행 승인 호출을 다시 태우고도 같은 답을 받았다 — 실제로 나간 호출 수와
     * 상한이 어긋나는 D-6G-65 의 항목이다. 상한에 닿은 축은 확정으로 접고, 추출이 그 공고를
     * `incomplete_axis` 로 공시한다.
     *
     * **기본값이 없다**: 상한을 잊은 배선이 「상한 없음」으로 조용히 돌지 않는다. 이 값이 여기 있는
     * 이유는 이것이 조회 가치 술어의 값이고, 수집 use case 가 **읽을 수 있는** 유일한 정책 자리이기
     * 때문이다 — `KonepsCollectionPolicyData` 는 그 패키지에서 통과 전용이다(구조 게이트가 멤버 접근을
     * 막는다, D-6F8-1 우회 1). 그 금지는 옳다: 정책의 원문 키를 use case 가 들여다볼 자리를 주지 않는다.
     */
    val axisRetryLimit: Int,
) {
    init {
        require(ageGateHours >= 0) { "ageGateHours는 음수일 수 없다: $ageGateHours" }
        require(recheckGateHours >= 0) { "recheckGateHours는 음수일 수 없다: $recheckGateHours" }
        require(axisRetryLimit >= 1) {
            "axisRetryLimit은 1 이상이다 — 0 이면 한 번의 일시 실패가 그 축을 영구히 버린다: $axisRetryLimit"
        }
    }
}

/**
 * 조회 가치 술어(⑪) — 순수 함수. 이미 저장돼 있으면 [DetailFetchSkipReason.AlreadyHeld],
 * 개찰 후 age-gate 미만이면 [DetailFetchSkipReason.AgeGateNotPassed], recheck-gate 미만이면
 * [DetailFetchSkipReason.RecheckGateNotPassed], 그 외에는 [DetailFetchDecision.Fetch]다
 * (gate를 넘긴 뒤 정확히 1회 — recheck-gate가 다음 창을 정한다, COL-03 acceptance).
 */
fun decideDetailFetch(
    noticeId: NoticeId,
    noticeKeyHash: String,
    alreadyHeld: Boolean,
    openingObservedAt: Instant?,
    lastCheckedAt: Instant?,
    now: Instant,
    gates: DetailFetchGates,
): DetailFetchDecision {
    val ageGateUntil = openingObservedAt?.plus(Duration.ofHours(gates.ageGateHours))
    val recheckUntil = lastCheckedAt?.plus(Duration.ofHours(gates.recheckGateHours))
    return when {
        alreadyHeld -> {
            DetailFetchDecision.Skip(DetailFetchSkipReason.AlreadyHeld)
        }

        ageGateUntil != null && now.isBefore(ageGateUntil) -> {
            DetailFetchDecision.Skip(DetailFetchSkipReason.AgeGateNotPassed(ageGateUntil))
        }

        recheckUntil != null && now.isBefore(recheckUntil) -> {
            DetailFetchDecision.Skip(DetailFetchSkipReason.RecheckGateNotPassed(recheckUntil))
        }

        else -> {
            DetailFetchDecision.Fetch(noticeId, noticeKeyHash)
        }
    }
}

/** [QualificationFetchDecision.Skip]의 사유(D-3B2-5 (a), COL-04). */
sealed interface QualificationFetchSkipReason {
    /** 업종제한 플래그(`indstrytyLmtYn`)가 `N` — 서브콜 자체가 쿼터 낭비다(COL-04 acceptance 첫째). */
    data object NoRestriction : QualificationFetchSkipReason
}

/**
 * 「자격 원문을 조회할 가치가 있는가」라는 도메인 판단(D-3B2-5 (a) 승인, 3B-2 scope.md ④) —
 * [DetailFetchDecision]과 같은 형태(3A ⑪). [Fetch]는 `internal constructor`다 — 유일한 생성
 * 경로는 [decideQualificationFetch]이고, `DocumentSourcePort.fetchQualificationText`의 서명이
 * 이 값을 인자로 요구해 술어를 거치지 않은 호출이 컴파일되지 않는다(위협 모델 방어 (a),
 * 우회 후보 (4)).
 */
sealed interface QualificationFetchDecision {
    @ConsistentCopyVisibility
    data class Fetch internal constructor(
        val noticeId: NoticeId,
    ) : QualificationFetchDecision

    data class Skip(
        val reason: QualificationFetchSkipReason,
    ) : QualificationFetchDecision
}

/**
 * 조회 가치 술어(D-3B2-5 (a)) — 순수 함수. `industryRestricted`는 공고 목록 관측에 이미 실려
 * 오는 `indstrytyLmtYn`(업종제한여부, §1.9.5)의 해석값이다 — 이 함수는 그 해석을 하지 않고
 * 불리언만 받는다(해석은 호출부, M4 workflow 의 몫). 제한이 없으면(`false`) 서브콜 0회
 * (`Skip(NoRestriction)`) — 「제한 없음」을 조회 실패가 아니라 조회 자체를 생략하는 이유로
 * 다룬다(COL-04 「제한 없음」과 「수집 실패」의 구분과는 다른 축 — 이쪽은 호출 전 판단).
 */
fun decideQualificationFetch(
    noticeId: NoticeId,
    industryRestricted: Boolean,
): QualificationFetchDecision =
    if (!industryRestricted) {
        QualificationFetchDecision.Skip(QualificationFetchSkipReason.NoRestriction)
    } else {
        QualificationFetchDecision.Fetch(noticeId)
    }
