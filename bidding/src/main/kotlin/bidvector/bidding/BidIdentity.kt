package bidvector.bidding

import bidvector.sharedkernel.NoticeRound

/**
 * 입찰 건의 식별 축(M7/7B, `BID-01`) — 건은 **(계정, 공고, 종류)** 하나당 하나다
 * (§6.1 「같은 공고에 기록이 여러 번 갱신돼도 건은 하나다」). 그 유일성은 표의
 * `UNIQUE (account_id, notice_number, notice_round, bid_kind)` 가 함께 진다.
 *
 * **계정 식별자를 이 모듈이 선언하지 않는다.** 주체는 application 층이 쥐고
 * (`workflow.bidding.BidActor`), 도메인은 주체의 *종류*만 안다([ActorKind]) — domain 이
 * `workflow.access.AccountId` 를 참조하면 의존 방향이 뒤집힌다(ADR 0006 D-3).
 */
data class BidCaseId(
    val value: Long,
) {
    init {
        require(value > 0L) { "BidCaseId 는 양수여야 한다: $value" }
    }
}

/** 건이 가리키는 공고 — **사본**이다(FK 를 두지 않는 이유는 `V21` 머리말·runbook). */
data class NoticeKey(
    val noticeNumber: String,
    val noticeRound: NoticeRound,
) {
    init {
        require(noticeNumber.isNotBlank()) { "공고번호는 빈 값일 수 없다" }
    }
}

/**
 * 페이퍼(가상) 투찰과 실투찰 — **같은 상태 어휘를 쓰되 타입이 다르다**(§6.1).
 * 섞이지 않는 것은 문장이 아니라 구조가 진다: 목록 포트가 이 값을 **필수 인자**로 받아
 * 「둘을 함께 내는 operation」이 서명에 없고, 개찰 대사 후보 술어는 [RealBidCase] 만 받는다
 * (`ReconciliationCandidate.kt`).
 */
enum class BidKind {
    REAL,
    PAPER,
}

/**
 * 전이를 일으킨 주체의 **종류**. 전이표가 행마다 허용 종류를 들고 있어, 어떤 명령이 어느
 * 주체에게 열려 있는지가 코드 분기가 아니라 표의 값이다.
 *
 * `SYSTEM` 행이 있다고 그 경로가 오늘 도는 것은 아니다 — SET-01·SET-02·DEC 메뉴는 이 slice
 * 밖이라 **시스템 호출자가 없다**(알려진 제한). 어휘를 지금 두는 이유는 나중에 CHECK 어휘를
 * 늘리는 둘째 마이그레이션을 피하기 위해서다.
 */
enum class ActorKind {
    USER,
    SYSTEM,
}

/** 건너뜀의 사유 — 빈 값이 아니다. 상태가 값을 **싣고** 있어 「사유 없는 건너뜀」이 표현되지 않는다. */
data class SkipReason(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "건너뜀은 사유를 가져야 한다" }
    }
}

/** 전이 사유(정정 등) — 타임라인에 남는다. 메모 본문을 싣는 자리가 아니다. */
data class TransitionReason(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "전이 사유는 빈 값일 수 없다" }
    }
}

/**
 * 투찰가 메뉴(DEC-05) 산출에서 고른 안의 참조. **오늘 이 값을 만드는 경로가 없다** —
 * 메뉴는 출하 경로에 없고(ML-01 · 7C② 대기) HTTP 요청 DTO 에 이 자리가 없다. 어휘만 둔다.
 */
data class PriceMenuRef(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "메뉴 참조는 빈 값일 수 없다" }
    }
}
