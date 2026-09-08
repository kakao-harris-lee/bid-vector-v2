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
    ) : DetailFetchDecision

    data class Skip(
        val reason: DetailFetchSkipReason,
    ) : DetailFetchDecision
}

/** age-gate·recheck-gate 시간 — 값(legacy 기본 24h/48h)은 정책 데이터다(매직넘버 금지). */
data class DetailFetchGates(
    val ageGateHours: Long,
    val recheckGateHours: Long,
) {
    init {
        require(ageGateHours >= 0) { "ageGateHours는 음수일 수 없다: $ageGateHours" }
        require(recheckGateHours >= 0) { "recheckGateHours는 음수일 수 없다: $recheckGateHours" }
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
            DetailFetchDecision.Fetch(noticeId)
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
