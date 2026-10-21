package bidvector.workflow.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommandTag
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.TransitionEffect
import bidvector.bidding.TransitionReason
import bidvector.workflow.access.AccountId
import java.time.Instant

/**
 * 전이가 **저장에 넣을 수 있는 형태** — `internal constructor` 다(`AppliedStrategy` 선례).
 *
 * 왜 닫는가: [BidCaseStorePort.applyTransition] 이 이 타입만 받으므로, `bidding.applyCommand`
 * 를 밖에서 직접 몰아 얻은 `TransitionOutcome.Accepted` 로는 **저장에 닿을 수 없다.** 커널
 * 자체는 domain 모듈의 public 함수라(그것이 `bidding` 모듈의 존재 이유다) `internal` 로 닫을
 * 수 없고, 대신 **저장 경계**를 닫는다. `app` 쪽은 한 겹 더 막혀 있다 — controller 는 포트가
 * 아니라 use case 를 받는다(`StrategyReadController` 의 「포트가 아니라 조회기를 받는다」).
 *
 * [expectedVersion] 은 낙관적 잠금 축이다 — 같은 건에 두 전이가 겹치면 뒤에 선 쪽이
 * [TransitionWrite.VersionConflict] 로 떨어진다(타임라인의 `UNIQUE (case_id, case_version)` 이
 * 같은 불변식의 둘째 겹).
 */
@ConsistentCopyVisibility
data class AppliedTransition internal constructor(
    val caseId: BidCaseId,
    val expectedVersion: Int,
    val command: BidCommandTag,
    val fromState: BidState,
    val next: BidState,
    val effect: TransitionEffect,
    val retroactive: Boolean,
    val reason: TransitionReason?,
    val actorKind: ActorKind,
    val actorDetail: String?,
    val occurredAt: Instant,
)

/** 전이 저장의 결과. */
sealed interface TransitionWrite {
    data object Applied : TransitionWrite

    /** 건이 없거나 **이 계정의 것이 아니다** — 둘을 구별하지 않는다(존재 여부가 새지 않게). */
    data object CaseNotFound : TransitionWrite

    /** 그 사이에 다른 전이가 끼었다. */
    data object VersionConflict : TransitionWrite
}

/** 건을 여는 결과 — 같은 (계정, 공고, 종류)에 이미 건이 있으면 **그 건을 돌려준다**(중복 생성 금지). */
sealed interface OpenCaseResult {
    data class Opened(
        val case: BidCase,
    ) : OpenCaseResult

    data class AlreadyOpen(
        val case: BidCase,
    ) : OpenCaseResult
}

/** 타임라인 한 줄 — 본문(메모 · 증빙)은 **싣지 않는다**(계약 산출물 8). */
data class TimelineEntry(
    val caseVersion: Int,
    val fromState: BidStateTag?,
    val toState: BidStateTag,
    val command: String,
    val actorKind: ActorKind,
    val actorDetail: String?,
    val reason: String?,
    val retroactive: Boolean,
    val occurredAt: Instant,
)

/**
 * 입찰 건 저장 포트 — **모든 메서드의 첫 인자가 주체([AccountId])다**(R-2, 계약 산출물 4).
 *
 * 그 자리가 서명에 있으므로 「계정 없이 부르는 조회」가 **존재할 수 없고**, 구현이 필터를
 * 빠뜨리면 소유 경계 test 가 실 DB 위에서 잡는다. 전역 · 스레드 로컬에서 주체를 꺼내는
 * 경로를 두지 않는다(§4.7 주입).
 *
 * 목록은 [BidKind] 를 **필수**로 받는다 — 페이퍼와 실투찰을 함께 내는 operation 이 서명에
 * 없다(§6.1 「같은 목록에 섞이지 않는다」).
 */
interface BidCaseStorePort {
    fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
    ): OpenCaseResult

    /** 남의 건이면 `null` — 존재 여부를 흘리지 않는다. */
    fun load(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase?

    fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCase>

    fun timeline(
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry>

    fun applyTransition(
        account: AccountId,
        applied: AppliedTransition,
    ): TransitionWrite
}

/**
 * 공고의 마감 시각 — **계정 축이 없다.** 공고는 사용자 소유 데이터가 아니라 공공 사실이라
 * 이 포트만 주체를 받지 않는다(그 예외를 여기 적어 둔다 — 적지 않으면 「빠뜨린 필터」와
 * 구별되지 않는다).
 *
 * 건이 공고를 FK 로 참조하지 않으므로(`V21` 머리말 — 6B-3 파기 충돌) 공고가 파기된 뒤에는
 * 답이 없을 수 있다. 그때는 키가 결과 map 에 **없고**, 다음 확인 시점은 「없음」이 된다.
 */
fun interface NoticeDeadlineQueryPort {
    fun deadlines(notices: Set<NoticeKey>): Map<NoticeKey, Instant>
}

/**
 * 초안이 얼릴 **공고 스냅샷을 서버가 뜬다** — 요청이 실어 오지 않는다.
 *
 * 이것이 위협 ③(초안 스냅샷 변조)의 차단면이다: 스냅샷을 요청 본문으로 받으면 운영자가
 * 「그때 하한율이 이랬다」를 **나중에 지어낼 수 있고**, 그러면 얼린 값이 증거이기를 그친다.
 * 요청 DTO 에 스냅샷 자리가 없고(`BidCaseRequests`) 이 포트만이 값을 낸다.
 *
 * 공고가 없거나 이미 파기됐으면 `null` 이고, 그때는 초안을 만들지 않는다 — 스냅샷 없는
 * 초안은 §6.1 의 acceptance 를 만족하지 못한다.
 */
fun interface NoticeSnapshotQueryPort {
    fun snapshot(
        notice: bidvector.bidding.NoticeKey,
        takenAt: Instant,
    ): bidvector.bidding.NoticeSnapshot?
}
