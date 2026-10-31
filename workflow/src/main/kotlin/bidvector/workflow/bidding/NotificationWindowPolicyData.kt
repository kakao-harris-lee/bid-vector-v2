package bidvector.workflow.bidding

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.ZoneId

/**
 * 「D-며칠 전」 창 하나 — **양 끝을 포함하고 KST 달력일 차이로 센다**(0 = 당일).
 *
 * 경과 시간(시간 단위)이 아니라 달력일인 이유: 운영자가 쓰는 말이 「내일 마감」이고, 이 저장소의
 * 다른 날짜 축(증빙 보관 기한 · 체크리스트 인증서 만료 · 개찰일)이 전부 KST 달력일을 센다. 시간
 * 단위로 두면 23시 마감과 01시 마감이 같은 「내일」인데 다른 알림을 받는다.
 */
data class DaysBeforeWindow(
    val minDaysBefore: Long,
    val maxDaysBefore: Long,
) {
    init {
        require(minDaysBefore >= 0) { "창의 하한은 음수일 수 없다(지난 날짜는 창 밖이다): $minDaysBefore" }
        require(maxDaysBefore >= minDaysBefore) { "창의 상한이 하한보다 작다: $minDaysBefore..$maxDaysBefore" }
    }

    fun contains(daysBefore: Long): Boolean = daysBefore in minDaysBefore..maxDaysBefore

    internal fun overlaps(other: DaysBeforeWindow): Boolean =
        minDaysBefore <= other.maxDaysBefore && other.minDaysBefore <= maxDaysBefore
}

/**
 * 시간 기반 알림의 창 셋(M7/7C③-b · D-7C3B-8) — 훑기가 「오늘 무엇을 알릴 것인가」를 묻는 표다.
 *
 * **마감 두 창은 겹칠 수 없다**(`init`). 겹치면 같은 날 한 건이 「마감 임박」과 「마감 당일」 **두
 * 항목**을 받는다 — 종류가 둘이라 중복 억제 키가 다르고, 알림함이 같은 사실을 두 번 말한다.
 * 그 둘을 한 종류로 접지 않은 것은 7C③ 의 결정이고(「어제 D-1 을 봤다」가 사라지지 않게), 그
 * 결정이 값을 가지려면 두 창이 서로 다른 날이어야 한다.
 *
 * **서류 기한 창만 폭이 있다**(0..2). 마감 두 창은 그날 하루씩이라 훑기가 하루 쉬면 그날 몫을
 * 잃지만, 서류 기한은 종류가 하나라 폭을 주면 훑기가 하루 빠져도 **같은 항목이 다음 날 선다**
 * (중복 억제 키가 같아 항목은 하나다). 마감 쪽에 같은 폭을 줄 수 없는 이유가 위 `init` 이다.
 */
data class NotificationWindowPolicyData(
    val bidDeadlineImminent: DaysBeforeWindow,
    val bidDeadlineToday: DaysBeforeWindow,
    val documentDeadline: DaysBeforeWindow,
) {
    init {
        require(!bidDeadlineImminent.overlaps(bidDeadlineToday)) {
            "마감 임박과 마감 당일의 창이 겹치면 한 건이 같은 날 두 항목을 받는다: " +
                "$bidDeadlineImminent / $bidDeadlineToday"
        }
    }

    /** 훑기가 저장소에 물을 때 쓰는 **상한** — 세 창 가운데 가장 먼 날이다. */
    val horizonDays: Long
        get() =
            maxOf(
                bidDeadlineImminent.maxDaysBefore,
                bidDeadlineToday.maxDaysBefore,
                documentDeadline.maxDaysBefore,
            )
}

/**
 * 알림 창을 세는 달력 구역 — 날짜 경계가 운영자의 하루와 같아야 한다(KST).
 *
 * **public 이다** — 창 **정책을 해소하는 조립**(`NotificationSweepWiring`)이 같은 구역으로 기준일을
 * 골라야 하기 때문이다(v L-3). UTC 로 고르면 KST 00~09 시에 **어제 날짜로 정책을 해소**하고, 그러면
 * 효력일이 바뀌는 날 판정 달력과 정책 달력이 하루 어긋난다 — 체크리스트가 같은 함정을 자기 구역
 * 상수로 막은 것과 같은 자리다.
 *
 * `EVIDENCE_RETENTION_ZONE` 을 재사용하지 않는다: 같은 값이지만 **다른 축**이다(보관 기한은 R-5,
 * 알림 창은 NOTI-10). 한 상수를 둘이 쓰면 한쪽 축을 옮기는 변경이 다른 축을 말없이 옮긴다 —
 * 체크리스트가 자기 구역 상수를 따로 든 것과 같은 판단이다.
 */
val NOTIFICATION_CALENDAR_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * **정본은 코드가 아니라 문서다**(`EVIDENCE_RETENTION_POLICY` 관례) — 값을 바꾸려면 `source` 의
 * 문서를 먼저 갱신한다. 창을 호출 자리의 리터럴로 두지 않는 것이 §5 「매직 넘버 금지」다.
 */
val NOTIFICATION_WINDOW_POLICY: EffectiveDatedPolicy<NotificationWindowPolicyData> =
    EffectiveDatedPolicy(
        source =
            "docs/discovery/m7-screen-data-requirements.md 「알림함」 — 마감 D-1/D-0 · 1순위 서류 제출 기한 D-2",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    NotificationWindowPolicyData(
                        bidDeadlineImminent = DaysBeforeWindow(1, 1),
                        bidDeadlineToday = DaysBeforeWindow(0, 0),
                        documentDeadline = DaysBeforeWindow(0, 2),
                    ),
            ),
    )
