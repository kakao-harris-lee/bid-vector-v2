package bidvector.bidding

import java.time.Instant

/**
 * 입찰 건 — 한 공고를 후보에서 결과까지 따라가는 **하나의 건**(`BID-01`).
 *
 * 계정은 싣지 않는다 — 소유는 application 층의 포트 서명이 지고(`workflow.bidding`), 저장
 * 층에서는 `account_id` 열과 질의 필터가 진다. 도메인 값에 계정을 넣으면 domain 이
 * `workflow.access.AccountId` 를 참조하게 되어 의존 방향이 뒤집힌다(ADR 0006 D-3).
 *
 * [outcomeNoticeExpectedAt] 만 건이 들고 [nextCheckpoint] 에 넘긴다 — 나머지 일정 축(리마인드 ·
 * 개찰일시 · 서류 기한)은 **그 상태가 싣는다**(그래야 「없을 수 없는 값」이 없을 수 없다).
 */
data class BidCase(
    val id: BidCaseId,
    val notice: NoticeKey,
    val kind: BidKind,
    val state: BidState,
    val caseVersion: Int,
    val outcomeNoticeExpectedAt: Instant?,
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
