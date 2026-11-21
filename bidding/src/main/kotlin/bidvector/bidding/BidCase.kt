package bidvector.bidding

import java.time.Instant

/**
 * 입찰 건 — 한 공고를 후보에서 결과까지 따라가는 **하나의 건**(`BID-01`).
 *
 * 계정은 싣지 않는다 — 소유는 application 층의 포트 서명이 지고(`workflow.bidding`), 저장
 * 층에서는 `account_id` 열과 질의 필터가 진다.
 *
 * **이 선택의 사유는 M15/15A 이후 「불가능」이 아니라 「설계」다.** 앞 판은 「도메인이
 * `workflow.access.AccountId` 를 참조하면 의존 방향이 뒤집힌다」고 적었는데, 15A 가 그 식별자를
 * `bidvector.sharedkernel` 로 옮겨 **도메인도 볼 수 있게** 됐다(패키지 순환을 끊느라). 그래도 넣지
 * 않는 이유는 그대로다: 소유를 값에 넣으면 같은 건을 **소유자 없이** 다루는 자리(판정·정산)까지
 * 계정을 들고 다니게 되고, 소유 검사가 값 비교로 흩어진다. 경계는 포트 서명 하나로 둔다.
 *
 * [outcomeNoticeExpectedAt] 만 건이 들고 [nextCheckpoint] 에 넘긴다 — 나머지 일정 축(리마인드 ·
 * 개찰일시 · 서류 기한)은 **그 상태가 싣는다**(그래야 「없을 수 없는 값」이 없을 수 없다).
 *
 * [deadlineAt] 은 건을 담을 때 적은 **공고 마감의 사본**이다(계약 D-7B-3 결정 ①) — 공고가 파기된
 * 뒤에도 「마감이 지났는가」를 판정하는 근거다. 담을 때 공고가 없었으면 `null` 이다.
 */
data class BidCase(
    val id: BidCaseId,
    val notice: NoticeKey,
    val kind: BidKind,
    val state: BidState,
    val caseVersion: Int,
    val outcomeNoticeExpectedAt: Instant?,
    val deadlineAt: Instant?,
) {
    init {
        require(caseVersion >= 0) { "caseVersion 은 음수일 수 없다: $caseVersion" }
    }
}

/**
 * 실투찰 건임이 **타입으로** 보증된 값 — 페이퍼 건을 실투찰 자리에 넘길 수 없게 한다.
 *
 * `internal constructor` 라 이 모듈 밖에서는 [asReal] 로만 얻는다. 그 함수는 페이퍼 건에
 * `null` 을 돌려주므로, 좁히기를 건너뛴 코드는 **컴파일되지 않는다**(「같은 목록에 섞이지
 * 않는다」를 구조가 진다 — §6.1).
 */
@ConsistentCopyVisibility
data class RealBidCase internal constructor(
    val case: BidCase,
)

/** 실투찰 건으로 좁힌다 — 페이퍼면 `null`. */
fun BidCase.asReal(): RealBidCase? = if (kind == BidKind.REAL) RealBidCase(this) else null
