package bidvector.workflow.notificationinbox

import bidvector.procurement.NoticeId
import bidvector.workflow.access.AccountId
import java.time.Instant

// 왜 `workflow.notification` 이 아니라 별 패키지인가(M7/7C③ — 실측으로 갈린 자리)
//
// `workflow.notification` 은 **채널 발송**의 패키지다(R-4 가 「알림함 기록까지」로 그은 경계의
// 바깥쪽 — `DeliveryPlan`·`Channel`·`MaskedTarget`·`RelayOutboxNotifications`). 둘이 그 패키지를
// 쓰지 못하게 만든다:
//
//  ① 그 패키지에는 **참조 경계 test** 가 걸려 있고 허용 루트에 `procurement` 가 없다. 알림함은
//     `NoticeId` 로 주제를 가리키므로 거기 두면 그 경계가 붉어진다 — 발송 층이 공고를 모르게
//     유지하는 것이 그 경계의 뜻이고, 이 slice 가 그것을 넓힐 이유가 없다.
//  ② 관심사가 다르다. 그쪽은 「렌더된 내용을 어느 채널로」(`ContentRef` + `Channel`)이고 이쪽은
//     「어떤 사실이 누구의 알림함에 있는가」다. 한 패키지에 두면 R-4 의 경계가 코드에서 사라진다.
//
// **그 과정에서 발견한 틈**(알려진 제한에 등재): 발송 쪽은 주체를 `OperatorId`(`workflow.strategy`)
// 로 들고 이쪽은 `AccountId`(`workflow.access`)로 든다. 알림함 항목을 나중에 채널로 복제하려면
// 그 둘을 잇는 **식별자 모델 결정**이 필요하다 — R-2 의 남은 절반이고(7B 가 같은 틈을 자격 헤더
// 쪽에서 만났다) `OPEN-STR-12` 와 함께 풀 일이다. 이 slice 는 그 결정을 흉내 내지 않는다.

/**
 * 알림함 항목의 **종류**(M7/7C③ · NOTI-10) — 중복 억제의 한 축이다. 「한 계정 · 한 공고 ·
 * 한 종류」에 한 항목이고, 그 셋이 저장 층의 PRIMARY KEY 다.
 *
 * **여덟 전부를 지금 둔다**(D-7C3-1 ③) — 오늘 이 어휘를 부르는 원천이 없는 값도 있다. 나중에
 * CHECK 어휘를 늘리는 둘째 마이그레이션을 피하는 것이 목적이고, 7B 가 `PRICE_RECEIVED` 전이
 * 어휘에 쓴 것과 같은 선례다. 어휘가 이 enum 과 DB CHECK 양쪽에 **같이** 있다는 것은
 * 어휘 등식 test 가 잠근다(한쪽만 늘리면 붉다).
 *
 * **마감 D-1 과 D-0 을 한 종류 + detail 로 접지 않는다.** 접으면 D-1 항목이 D-0 으로 갱신되며
 * 하나로 수렴해 「어제 D-1 을 봤다」는 사실이 사라진다. 두 종류면 두 항목이고 각각 읽음
 * 상태를 갖는다.
 */
enum class NotificationKind {
    /** 새 후보(자격 통과). 원천은 STR-08 감시 이력 — 오늘 저장소에 없다. */
    NEW_CANDIDATE,

    /** 투찰 마감 D-1 · 투찰 미기록. 원천은 BID-01(7B). */
    BID_DEADLINE_IMMINENT,

    /** 투찰 마감 D-0 · 투찰 미기록. 원천은 BID-01(7B). */
    BID_DEADLINE_TODAY,

    /** 1순위 서류 제출 기한 D-2. 원천은 BID-01(7B). */
    DOCUMENT_DEADLINE_IMMINENT,

    /** 개찰 완료. 잠정 1위 여부를 detail 로 싣는다 — 그 값은 **갱신되어야 한다**. */
    OPENING_COMPLETED,

    /** 낙찰·패찰·유찰 확정. 결과 셋([AwardResult])을 detail 로 싣는다. */
    AWARD_DECIDED,

    /** 공고 변경(정정공고). 원천 판정은 관련성이 서는 slice 몫이다(D-7C3-1 ②). */
    NOTICE_REVISED,

    /** 투찰가 요청 결과 기록 — R-4 의 「알림함 기록까지」가 끝나는 자리(7C②). */
    BID_PRICE_REQUEST_RECORDED,
}

/**
 * detail 어휘 — **닫힌 다섯**이다. 자유 문자열이 아니므로 이 축으로 PII 가 들어올 수 없다.
 * 어느 종류가 어느 값을 갖는지는 [NotificationFact] 의 구성이 정하고, 저장 층의 짝 CHECK 가
 * 같은 규칙을 한 겹 더 진다(심층 방어).
 */
enum class NotificationDetail {
    PROVISIONAL_FIRST,
    NOT_PROVISIONAL_FIRST,
    AWARDED,
    NOT_AWARDED,
    FAILED_OR_REBID,
}

/**
 * 낙찰 확정의 **결과** — 닫힌 셋이고 각자 자기 detail 을 **생성자로** 든다(D-M9-1).
 *
 * **이름이 `AwardOutcome` 이 아닌 이유**: 그 이름은 `bidvector.settlement.AwardOutcome`(개찰
 * 대사의 4-state 결말 — 미기록·판정 안 함·낙찰·패찰)이 쓰고 있고, 이 모듈은 그 타입을 실제로
 * 들고 있다(`workflow.result.ReconciliationVerdict`). 같은 이름 둘이 한 모듈에서 import 로만
 * 갈리면 읽는 사람이 어느 쪽인지 알 수 없다.
 *
 * **기존 어휘를 재활용하지 않은 이유**(조사 뒤 채택): `bidvector.bidding.BidOutcome` 이
 * `WON`·`LOST`·`FAILED_OR_REBID` 로 **값이 같다**. 그래도 쓰지 않는다 — 알림함의 detail 어휘는
 * 저장 층 CHECK 와 **집합 등식**을 이루는 이 패키지의 닫힌 어휘이고, 그것을 `bidding` 의 enum 에
 * 묶으면 알림함이 입찰 모듈 없이는 서지 않는다(7C③ 이 이 패키지의 의존을 `procurement.NoticeId` ·
 * `workflow.access.AccountId` 둘로 묶어 둔 사유와 같은 축). 그리고 둘은 앞으로 갈린다: 자동 대사가
 * 들어오면 같은 종류의 원천이 `settlement` 쪽 4-state 가 되는데 그 집합에는 유찰이 **없다**. 즉
 * 어느 기존 타입도 알림함의 어휘가 아니다. 두 벌이 갈리는 것은 `BidCaseNotifications` 의 상태
 * 사상 등식이 막는다(상태 전수 ↔ 사실 전수).
 *
 * **`Boolean` 이 아닌 이유**: 결과는 셋이다. 유찰(아무도 못 땄다 · 재입찰)은 패찰(내가 졌다)과
 * 다른 사실이고, 운영자의 다음 할 일도 다르다 — 유찰은 재입찰 공고를 기다리는 일이다. 앞 판은
 * 어휘가 둘(`awarded: Boolean`)이어서 유찰이 **알림을 아예 남기지 못했다**(7C③-b 의 알려진 제한).
 * `NOT_AWARDED` 로 접는 길은 거부됐다 — 「내가 졌다」와 「아무도 못 땄다」가 한 값이 된다.
 *
 * **짝이 생성자 인자인 이유**: 투영과 복원이 **한 표**를 읽는다. 양쪽에 `when` 을 두면 표가
 * 둘이 되고 갈릴 자리가 생긴다. 겹침(두 결과가 한 detail 을 가리키는 접기)은 이 꼴에서
 * 표현되기는 하지만 복원이 [notificationFactOf] 의 `single` 에서 어긋나 붉다.
 */
enum class AwardResult(
    val detail: NotificationDetail,
) {
    AWARDED(NotificationDetail.AWARDED),
    NOT_AWARDED(NotificationDetail.NOT_AWARDED),
    FAILED_OR_REBID(NotificationDetail.FAILED_OR_REBID),
}

/**
 * 같은 키의 **재기록이 읽음을 해제하는가** — 종류별 정책이다(D-M9-3).
 *
 * 왜 종류로 가르는가: 7C③ 은 한 규칙이었다(「detail 이 바뀔 때만 해제」). detail 을 **갖지 않는**
 * 종류는 그 규칙 아래에서 재기록이 영영 읽음을 유지하고, `NOTICE_REVISED` 에서는 그것이 틀렸다 —
 * 이미 본 공고가 **또** 바뀐 것은 새 소식이다(7C③-b 의 알려진 제한 13).
 *
 * 이 어휘는 **SQL 로 bind 하나로** 내려간다. 종류 문자열을 SQL 에 늘어놓으면 종류를 더할 때
 * 컴파일이 막아 주지 않고, 그 부재를 `NotificationInboxSqlPolicyTest` 가 종류 전수로 잰다.
 */
enum class ReadResetPolicy {
    /**
     * detail 이 바뀔 때만 해제한다(D-7C3-1 ④) — 같은 사실의 재기록은 읽음을 유지한다. 그러지
     * 않으면 운영자가 같은 것을 반복해 읽는다.
     */
    ON_DETAIL_CHANGE,

    /**
     * 사실의 시각이 **전진한** 재기록도 해제한다 — 그 종류에서는 전진이 「새 소식」과 동치다.
     *
     * **매 재기록이 아니다**: 같은 사실을 다시 적는 것만으로 해제하면, 그 사실을 주기로 다시
     * 적는 원천 위에서 읽음 처리가 **영영 유지되지 않는다**(공고 개정 훑기가 그런 원천이다 —
     * 조건이 유지되는 동안 매 run 마다 같은 개정을 다시 적는다). 전진을 보면 그 재기록은
     * 지나가고 **새 개정만** 배지를 세운다.
     */
    ON_NEWER_FACT,
}

/**
 * 종류 **전수 표**(`else` 없는 `when`) — 종류를 더하면 이 정책을 정하지 않고는 컴파일되지
 * 않는다. 어느 종류가 어느 정책인지는 `NotificationFactTest` 가 파티션 등식으로 고정한다
 * (그 등식이 없으면 정책을 뒤집어도 거동 test 둘이 각자 자기 파티션을 돌아 **둘 다** 초록이다).
 *
 * `NOTICE_REVISED` 만 [ReadResetPolicy.ON_NEWER_FACT] 인 이유: 그 종류의 사실 시각은 개정 시각
 * (`notice.updated_at`)이고, V2 의 트리거가 `revision` 과 그 열을 **함께** 올리므로(그리고 내용이
 * 같으면 UPDATE 자체가 돌지 않는다) 시각의 전진은 **새 개정과 동치**다. 시간 기반 종류들은
 * 사실 시각이 달력일 시작이라 전진이 「같은 판정의 다음 날」을 뜻하고, 그것은 새 소식이 아니다.
 */
val NotificationKind.readResetPolicy: ReadResetPolicy
    get() =
        when (this) {
            NotificationKind.NOTICE_REVISED -> ReadResetPolicy.ON_NEWER_FACT

            NotificationKind.NEW_CANDIDATE,
            NotificationKind.BID_DEADLINE_IMMINENT,
            NotificationKind.BID_DEADLINE_TODAY,
            NotificationKind.DOCUMENT_DEADLINE_IMMINENT,
            NotificationKind.OPENING_COMPLETED,
            NotificationKind.AWARD_DECIDED,
            NotificationKind.BID_PRICE_REQUEST_RECORDED,
            -> ReadResetPolicy.ON_DETAIL_CHANGE
        }

/**
 * 알림 하나가 나르는 **사실**. 자유 문자열 칸이 없다 — 표시 문구(공고명·기관·마감)는 저장하지
 * 않고 읽을 때 `notice` 에서 합친다(D-7C3-1 ⑦). 그래서 두 성질이 규율이 아니라 **구조**다:
 *
 * · 「제목 문구가 바뀌어도 중복 억제 키가 흔들리지 않는다」 — 흔들릴 값이 애초에 없다.
 * · 「항목에 PII 가 담기지 않는다」 — 담을 칸이 없다.
 *
 * **불법 쌍이 표현되지 않는다**: detail 을 갖는 종류만 그 자리를 갖는다. 열거 + nullable
 * 필드로 두면 `NewCandidate(detail = AWARDED)` 같은 상태가 표현되고, 그것을 막는 일이
 * 런타임 검증으로 내려간다.
 */
sealed interface NotificationFact {
    val kind: NotificationKind

    /** detail 이 없는 종류 — 사실은 「종류 + 공고 쌍」이 전부다. */
    sealed interface WithoutDetail : NotificationFact

    data object NewCandidate : WithoutDetail {
        override val kind: NotificationKind get() = NotificationKind.NEW_CANDIDATE
    }

    data object BidDeadlineImminent : WithoutDetail {
        override val kind: NotificationKind get() = NotificationKind.BID_DEADLINE_IMMINENT
    }

    data object BidDeadlineToday : WithoutDetail {
        override val kind: NotificationKind get() = NotificationKind.BID_DEADLINE_TODAY
    }

    data object DocumentDeadlineImminent : WithoutDetail {
        override val kind: NotificationKind get() = NotificationKind.DOCUMENT_DEADLINE_IMMINENT
    }

    data object NoticeRevised : WithoutDetail {
        override val kind: NotificationKind get() = NotificationKind.NOTICE_REVISED
    }

    data object BidPriceRequestRecorded : WithoutDetail {
        override val kind: NotificationKind get() = NotificationKind.BID_PRICE_REQUEST_RECORDED
    }

    /** 개찰 완료 — 잠정 1위 여부. 바뀌면 그 항목이 안읽음으로 돌아간다(D-7C3-1 ④). */
    data class OpeningCompleted(
        val provisionallyFirst: Boolean,
    ) : NotificationFact {
        override val kind: NotificationKind get() = NotificationKind.OPENING_COMPLETED
    }

    /** 낙찰·패찰·유찰 확정 — 결과를 [AwardResult] 으로 싣는다. 바뀌면 안읽음으로 돌아간다. */
    data class AwardDecided(
        val outcome: AwardResult,
    ) : NotificationFact {
        override val kind: NotificationKind get() = NotificationKind.AWARD_DECIDED
    }

    /**
     * 이 사실이 싣는 detail — 없는 종류는 `null` 이다. 소진 `when`(else 없음)이라 종류를
     * 더하면 이 함수가 컴파일되지 않는다.
     */
    val detail: NotificationDetail?
        get() =
            when (this) {
                is WithoutDetail -> {
                    null
                }

                is OpeningCompleted -> {
                    if (provisionallyFirst) {
                        NotificationDetail.PROVISIONAL_FIRST
                    } else {
                        NotificationDetail.NOT_PROVISIONAL_FIRST
                    }
                }

                is AwardDecided -> {
                    outcome.detail
                }
            }
}

/**
 * 저장된 `(종류, detail)` 짝에서 사실을 복원한다 — [NotificationFact.detail] 의 **역**이다.
 * 둘이 한 자리에 있어야 왕복 등식 하나가 두 방향을 같이 잠근다(`NotificationFactTest`).
 *
 * 짝이 어긋난 조합은 **복원되지 않는다**. 그 상태는 저장 층의 짝 CHECK 가 막으므로, 그래도
 * 들어왔다면 스키마와 코드가 갈렸다는 뜻이다 — 업무 흐름의 분기가 아니라 **불가능한 상태**라
 * 조용히 기본값으로 접지 않는다(어댑터가 enum 을 `valueOf` 로 복원하는 기존 관례와 같은 결).
 */
fun notificationFactOf(
    kind: NotificationKind,
    detail: NotificationDetail?,
): NotificationFact =
    when (kind) {
        NotificationKind.NEW_CANDIDATE -> NotificationFact.NewCandidate.withoutDetail(detail)
        NotificationKind.BID_DEADLINE_IMMINENT -> NotificationFact.BidDeadlineImminent.withoutDetail(detail)
        NotificationKind.BID_DEADLINE_TODAY -> NotificationFact.BidDeadlineToday.withoutDetail(detail)
        NotificationKind.DOCUMENT_DEADLINE_IMMINENT -> NotificationFact.DocumentDeadlineImminent.withoutDetail(detail)
        NotificationKind.NOTICE_REVISED -> NotificationFact.NoticeRevised.withoutDetail(detail)
        NotificationKind.BID_PRICE_REQUEST_RECORDED -> NotificationFact.BidPriceRequestRecorded.withoutDetail(detail)
        NotificationKind.OPENING_COMPLETED -> NotificationFact.OpeningCompleted(detail.isProvisionalFirst(kind))
        NotificationKind.AWARD_DECIDED -> NotificationFact.AwardDecided(detail.asAwardResult(kind))
    }

// detail 을 싣는 종류 **각각이 자기 어휘를 읽는 함수**를 갖는다. 어휘 쌍을 호출 자리에 펴 두면
// 갈래 하나가 여러 줄이 되고, 그러면 위 `when` 이 함수 크기 한도를 넘는다(실측). 더 중요한
// 것은 **소진 `when` 을 지키는 것**이다 — `else` 로 바꾸면 종류를 더할 때 컴파일이 막아 주지
// 않고 런타임까지 간다. 그래서 줄이는 쪽을 갈래가 아니라 **갈래의 본문**에서 했다.
//
// 두 본문의 꼴이 **다른 이유**: 개찰 축은 `Boolean` 이라 어휘 쌍이 여기 적히고, 낙찰 축은
// [AwardResult] 이 짝을 이미 들고 있어 그 표를 **되읽는다**. 그쪽에 `when` 을 또 적으면 같은
// 짝이 두 자리에 살고 갈릴 자리가 생긴다.

private fun NotificationDetail?.isProvisionalFirst(kind: NotificationKind): Boolean =
    when (this) {
        NotificationDetail.PROVISIONAL_FIRST -> true
        NotificationDetail.NOT_PROVISIONAL_FIRST -> false
        else -> throw IllegalArgumentException("$kind 의 detail 이 아니다: $this")
    }

/**
 * 낙찰 확정의 detail → 결과. [AwardResult] 의 짝을 **역으로** 읽는다.
 *
 * `single` 이 아니라 `singleOrNull` + 던지기인 것은 「둘이 걸렸다」와 「없다」를 **같은 거부**로
 * 접기 위해서다. 둘이 걸리는 경우는 두 결과가 한 detail 을 가리키는 접기이고, 그 상태는 복원할
 * 수 있는 짝이 아니다 — 하나를 골라 주면 그 접기가 조용히 작동한다.
 */
private fun NotificationDetail?.asAwardResult(kind: NotificationKind): AwardResult =
    AwardResult.entries.singleOrNull { it.detail == this }
        ?: throw IllegalArgumentException("$kind 의 detail 이 아니다: $this")

/** detail 을 갖지 않는 종류에 detail 이 실려 오면 그 짝은 복원 대상이 아니다. */
private fun NotificationFact.WithoutDetail.withoutDetail(detail: NotificationDetail?): NotificationFact {
    require(detail == null) { "$kind 는 detail 을 싣지 않는다: $detail" }
    return this
}

/**
 * 쓰기 포트가 받는 한 건. **첫 필드가 주체**다 — 주체 없이 알림을 기록하는 호출이 시그니처상
 * 존재하지 않는다(7B 가 세운 「주체 없이 부르는 포트 0」 규율).
 *
 * [occurredAt] 은 **사실의 시각**이고 호출자의 시계가 정한다. 저장 층의 `created_at`(최초
 * 적재, 보존 cutoff 축)과 `updated_at`(재기록 증거)은 이 값과 다른 축이고 **둘 다 DB 시계가
 * 찍는다** — 저장 층이 자기 시계로 「언제 이 행을 만졌나」를 남기고, 호출자의 시계는 「사실이
 * 언제 일어났나」만 말한다.
 */
data class NotificationInboxRecord(
    val account: AccountId,
    val notice: NoticeId,
    val fact: NotificationFact,
    val occurredAt: Instant,
)

/**
 * 기록의 결과. **예외를 업무 흐름에 쓰지 않는다** — 저장 실패도 값이다
 * (`OutboxNotificationRequestPort` 가 `SQLException` 을 `Failed` 로 접은 것과 같은 축).
 *
 * **알려진 제한을 그 선례에서 그대로 잇는다**: 호출부가 [Failed] 를 무시하고 자기 트랜잭션을
 * 커밋하면 도메인 write 는 남고 알림은 없는 상태가 된다. 호출부는 원천 slice 들이다.
 */
sealed interface NotificationInboxOutcome {
    /** 그 키의 첫 항목이 생겼다. */
    data object Created : NotificationInboxOutcome

    /** 이미 있던 항목을 갱신했다 — 중복 억제가 작동한 자리다(행이 늘지 않았다). */
    data object Updated : NotificationInboxOutcome

    data object Failed : NotificationInboxOutcome
}

/**
 * 항목 하나의 신원 — 읽음 처리(단건)가 받는 키다. **합성 id 가 없다**: 신원이 「종류 + 공고
 * 쌍」이고 그 셋은 호출자가 자기 계정의 목록에서 이미 본 값이다. 열거할 불투명 id 가 없으므로
 * 남의 항목을 더듬어 찾을 표면이 그만큼 작다(계정 축은 포트 인자가 따로 진다).
 */
data class NotificationItemKey(
    val kind: NotificationKind,
    val notice: NoticeId,
)
