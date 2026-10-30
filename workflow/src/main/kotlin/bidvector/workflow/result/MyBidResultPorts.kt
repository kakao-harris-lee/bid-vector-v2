package bidvector.workflow.result

import bidvector.bidding.RealBidCase
import bidvector.bidding.SubmittedBidAmount
import bidvector.procurement.NoticeId
import bidvector.settlement.AwardWinnerTradeName
import bidvector.workflow.access.AccountId
import bidvector.workflow.opening.NoticeOpeningFacts
import java.time.Instant

/**
 * 투찰 기록이 있는 실투찰 건 하나 — 결과 조회가 읽는 **전부**다.
 *
 * **메모 · 증빙 참조 · 추첨번호를 담을 자리가 없다**(D-7C5B-5). 응답 DTO 에 칸이 없는 것만으로는
 * 질의가 그 값을 쥐는 것을 막지 못하므로 포트가 내는 타입에서 먼저 막는다 — 읽어 온 값을 담을
 * 곳이 없으면 그 열을 `SELECT` 할 이유도 사라진다(열 목록 등식 test 가 그 부재를 잠근다).
 *
 * [case] 가 [RealBidCase] 인 것이 「페이퍼가 섞이지 않는다」의 구조다 — 그 타입은 `bidding`
 * 모듈 밖에서 만들 수 없고 `asReal()` 이 페이퍼에 `null` 을 준다(§6.1).
 */
data class RecordedRealBid(
    val case: RealBidCase,
    val amount: SubmittedBidAmount,
    val submittedAt: Instant,
    val confirmedAt: Instant,
)

/**
 * 계정의 투찰 기록 읽기 — **첫 인자가 주체다**(7B R-2 관례). 그 자리가 서명에 있으므로 「계정
 * 없이 부르는 조회」가 존재할 수 없다.
 *
 * **읽기 하나뿐이라 이 포트를 쥔 코드가 무엇도 바꿀 수 없다.** 기존 `BidCaseStorePort` 를 쓰지
 * 않는 이유가 이것이다(D-7C5B-8): 그 포트의 읽기는 `BidCaseWorkflow` 를 지나며 마감 정산이
 * 타임라인에 **쓴다**. 결과 조회는 읽기 전용이라 그 경로를 빌릴 수 없다.
 *
 * [BidKind][bidvector.bidding.BidKind] 를 인자로 받지 않는다 — 돌려주는 타입이 실투찰로 이미
 * 좁혀져 있어 「페이퍼를 달라」고 **물을 수가 없다**. 목록 포트가 종류를 필수 인자로 받던
 * 7B 의 규율(§6.1 「같은 목록에 섞이지 않는다」)을 더 좁은 쪽으로 지킨다.
 *
 * 순서는 **이 포트가 정한다**(확인 시각 내림차순) — 조회는 받은 순서를 바꾸지 않는다.
 */
fun interface RecordedBidReadPort {
    fun recordedBids(account: AccountId): List<RecordedRealBid>
}

/**
 * 공고 여럿의 개찰 사실을 **한 번에** — 7C⑤-a 의 단건 포트와 같은 사실 타입을 낸다.
 *
 * 단건 포트를 건마다 부르지 않는 이유는 비용이다: canonical 행이 없을 때 「승격 전 ↔ 수집 전」을
 * 가르는 질의가 개찰 축 원문을 훑는데(7C⑤-a 의 알려진 제한), 오늘 production 의 답은 전건
 * 「승격 전」이라 그 훑기가 **건마다** 돈다. 묶어 물으면 요청당 한 번이다.
 *
 * 판정은 여기 없다 — 사실만 나르고 상태는 순수 함수가 정한다
 * ([resolveOpeningResultView][bidvector.workflow.opening.resolveOpeningResultView]). 7C⑤-a 와
 * **같은 함수**를 쓰므로 두 조회가 같은 입력에 다른 상태를 낼 수 없다.
 *
 * 결과는 요청한 공고마다 항목을 갖는다 — 공고 행이 없으면 [NoticeOpeningFacts.NoticeAbsent] 다.
 * 키가 아예 없는 것도 같은 뜻이고 조회가 그렇게 읽는다.
 */
fun interface BatchOpeningFactsPort {
    fun lookupAll(notices: Set<NoticeId>): Map<NoticeId, NoticeOpeningFacts>
}

/**
 * 확정 낙찰자 상호 읽기 — 그 값을 나르는 **유일한 포트**이고 산출 타입은
 * [AwardWinnerTradeName] 하나다(D-7C5B-9).
 *
 * 7C⑤-a 의 개찰 조회에 상호 열을 더해 재사용하지 않는 이유: 그 질의의 열 목록이
 * **상호 둘이 없다**는 것을 등식 test 로 잠그고 있고(D-7C5-3), 거기 열을 더하면 그 구조가
 * 무너진다. 그래서 상호는 **별 질의**로 읽고, 읽은 값은 이 타입에 실려 커널로 들어가
 * [ReconciliationVerdict] 로만 나온다 — 그 타입에 이름 자리가 없으므로 상호가 응답 조립기에
 * 닿을 **타입 경로가 없다**.
 *
 * [AwardWinnerTradeName.Published] 는 `toString()` 이 가려져 있어 로그·예외 문면으로도 새지
 * 않는다. 항목이 없는 공고는 [AwardWinnerTradeName.NotPublished] 로 읽는다 — 「낙찰자가
 * 아니다」가 아니라 「상호를 모른다」다.
 */
fun interface AwardWinnerNamePort {
    fun winnerNames(notices: Set<NoticeId>): Map<NoticeId, AwardWinnerTradeName>
}
