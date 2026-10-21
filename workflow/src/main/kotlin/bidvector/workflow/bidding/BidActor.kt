package bidvector.workflow.bidding

import bidvector.bidding.ActorKind
import bidvector.workflow.access.AccountId

/**
 * 전이를 일으킨 주체(M7/7B) — `workflow.strategy.Actor` 와 **같은 축의 다른 타입**이다.
 * 그쪽은 `OperatorId`(TEXT, 단일 회사 전제)를 들고 이쪽은 [AccountId](7D 의 계정 행)를 든다.
 * 둘을 한 타입으로 합치려면 「`OperatorId` 가 `account.id` 인가」를 먼저 답해야 하고, 그
 * 결정은 R-2 남은 절반의 별 slice 몫이다(계약 r2 D-7B-1).
 *
 * 도메인(`bidding`)은 이 타입을 보지 않고 [ActorKind] 만 본다 — domain 이 application 의
 * 타입을 참조하면 의존 방향이 뒤집힌다(ADR 0006 D-3).
 */
sealed interface BidActor {
    val kind: ActorKind

    /** 운영자 — 이 요청의 주체이고, 건의 소유자와 **같아야** 한다(포트가 그 등식을 진다). */
    data class User(
        val account: AccountId,
    ) : BidActor {
        override val kind: ActorKind get() = ActorKind.USER
    }

    /**
     * 시스템 — SET-01 대사 · SET-02 개찰 · 리마인드 도달이 올 자리다.
     * **오늘 이 값을 만드는 호출자가 없다**(알려진 제한 ②). [detail] 은 타임라인의
     * `actor_detail` 로 남는다(사람의 이름이 아니라 경로의 이름이다).
     */
    data class System(
        val detail: String,
    ) : BidActor {
        override val kind: ActorKind get() = ActorKind.SYSTEM

        init {
            require(detail.isNotBlank()) { "시스템 주체는 경로 이름을 가져야 한다" }
        }
    }
}
