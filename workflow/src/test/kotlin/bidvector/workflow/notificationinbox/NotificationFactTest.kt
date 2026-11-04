package bidvector.workflow.notificationinbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C③ — 사실 어휘의 **구성**을 잰다(계약 설계 검토 (1)). mock 없이 값만 쓴다.
 *
 * 이 class 가 지키는 것은 「문서가 적은 종류 ↔ detail 짝이 코드의 구성과 같다」다. 열거 +
 * nullable 필드로 두면 `NewCandidate(detail = AWARDED)` 같은 상태가 표현되고 그것을 막는 일이
 * 런타임 검증으로 내려간다 — 그 상태가 **표현되지 않는다**는 것이 여기서 재는 성질이다.
 *
 * **읽음 해제 정책의 파티션도 여기서 잰다**(D-M9-3). 같은 종류 전수 표가 정본이고(같은 파일의
 * `readResetPolicy`), 그 표의 **거동**은 실 DB 쪽이 진다 — 여기서 재는 것은 「어느 종류가 어느
 * 정책인가」뿐이다. 그 등식이 없으면 정책을 뒤집어도 거동 test 둘이 각자 자기 파티션을 돌아
 * **둘 다** 초록이다(실측으로 세운 자리가 아니라, 분할 test 의 구조가 그렇다).
 */
class NotificationFactTest {
    /**
     * 종류 전수에 사실이 **하나씩** 대응한다 — 어휘를 늘리고 사실을 안 만들면(또는 그 반대)
     * 붉다. 이 등식이 없으면 enum 에만 존재하는 「부를 수 없는 종류」가 조용히 생긴다.
     */
    @Test
    fun `사실 전수의 종류 집합은 종류 어휘 전수와 같다`() {
        ALL_FACTS.map { it.kind } shouldContainExactlyInAnyOrder NotificationKind.entries
    }

    @Test
    fun `detail 을 갖지 않는 종류는 detail 이 null 이다`() {
        ALL_FACTS.filterIsInstance<NotificationFact.WithoutDetail>().forEach { fact ->
            fact.detail.shouldBeNull()
        }
    }

    /**
     * detail 을 **갖는** 종류는 둘뿐이고, 그 둘만이 `WithoutDetail` 밖에 있다. 이 등식이
     * 「detail 자리를 가진 종류」의 정본이다.
     */
    @Test
    fun `detail 을 싣는 종류는 개찰 완료와 낙찰 확정 둘이다`() {
        val withDetail = ALL_FACTS.filterNot { it is NotificationFact.WithoutDetail }.map { it.kind }

        withDetail shouldContainExactlyInAnyOrder
            listOf(NotificationKind.OPENING_COMPLETED, NotificationKind.AWARD_DECIDED)
    }

    @Test
    fun `개찰 완료의 잠정 1위 여부가 detail 두 값으로 갈린다`() {
        NotificationFact.OpeningCompleted(provisionallyFirst = true).detail shouldBe
            NotificationDetail.PROVISIONAL_FIRST
        NotificationFact.OpeningCompleted(provisionallyFirst = false).detail shouldBe
            NotificationDetail.NOT_PROVISIONAL_FIRST
    }

    /**
     * **결과 셋이 detail 셋으로 갈린다**(D-M9-1). 앞 판은 `Boolean` 이라 어휘가 둘뿐이었고, 그래서
     * 유찰은 「패찰로 접기」나 「알림 없음」 중 하나여야 했다.
     */
    @Test
    fun `낙찰 확정의 결과 셋이 detail 셋으로 갈린다`() {
        NotificationFact.AwardDecided(AwardOutcome.AWARDED).detail shouldBe NotificationDetail.AWARDED
        NotificationFact.AwardDecided(AwardOutcome.NOT_AWARDED).detail shouldBe NotificationDetail.NOT_AWARDED
        NotificationFact.AwardDecided(AwardOutcome.FAILED_OR_REBID).detail shouldBe
            NotificationDetail.FAILED_OR_REBID
    }

    /**
     * **결과 전수가 서로 다른 detail 을 가리킨다**(D-M9-1). 어휘 짝이 생성자 인자라 겹침이
     * 표현되기는 하고(열거가 아니라 구성인 대가), 겹치면 아래 왕복 등식의 복원이 `single` 에서
     * 어긋나 붉다 — 다만 **무엇이 접혔는지**를 말해 주는 것은 이 등식이다.
     *
     * 「내가 졌다」와 「아무도 못 땄다」가 한 값이 되는 접기가 7C③-b 가 거부한 바로 그것이다.
     */
    @Test
    fun `결과 전수가 서로 다른 detail 을 가리킨다 — 유찰이 패찰로 접히지 않는다`() {
        val details = AwardOutcome.entries.map { it.detail }

        details.distinct() shouldContainExactlyInAnyOrder details
    }

    /**
     * **detail 어휘에 고아가 없다** — 어느 사실도 내지 않는 detail 값이 있으면 그것은 DB CHECK
     * 에만 사는 어휘이고, 그런 값은 저장될 수 없으면서 짝 CHECK 를 넓힌다.
     */
    @Test
    fun `detail 어휘 전수가 어떤 사실에서든 실제로 나온다`() {
        val produced =
            (ALL_FACTS + DETAIL_BEARING_VARIANTS).mapNotNull { it.detail }.distinct()

        produced shouldContainExactlyInAnyOrder NotificationDetail.entries
    }

    /**
     * **왕복 등식 하나가 두 방향을 잠근다** — `detail` 투영과 [notificationFactOf] 복원이 서로의
     * 역이다. 한쪽만 고치면 붉다(어댑터가 저장한 짝을 자기가 다시 못 읽는 상태를 막는다).
     */
    @Test
    fun `모든 사실이 종류와 detail 로 접혔다가 그대로 복원된다`() {
        (ALL_FACTS + DETAIL_BEARING_VARIANTS).forEach { fact ->
            notificationFactOf(fact.kind, fact.detail) shouldBe fact
        }
    }

    /**
     * 짝이 어긋난 조합은 **복원되지 않는다**. 저장 층의 짝 CHECK 가 막는 상태이지만, 어댑터의
     * codec 이 어긋나게 쓰면 그것을 여기서도 잡는다(두 겹).
     */
    @Test
    fun `detail 을 싣지 않는 종류에 detail 이 오면 복원하지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            notificationFactOf(NotificationKind.NEW_CANDIDATE, NotificationDetail.AWARDED)
        }
    }

    @Test
    fun `detail 을 싣는 종류에 detail 이 없거나 남의 어휘면 복원하지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            notificationFactOf(NotificationKind.OPENING_COMPLETED, null)
        }
        shouldThrow<IllegalArgumentException> {
            // 개찰 완료에 낙찰 어휘를 실은 짝 — DB 짝 CHECK 가 막는 바로 그 조합이다.
            notificationFactOf(NotificationKind.OPENING_COMPLETED, NotificationDetail.AWARDED)
        }
        shouldThrow<IllegalArgumentException> {
            notificationFactOf(NotificationKind.AWARD_DECIDED, NotificationDetail.PROVISIONAL_FIRST)
        }
    }

    // --- 읽음 해제 정책의 파티션 (D-M9-3) ---------------------------------------

    /**
     * **정책 표 자체를 고정한다.** 「개정의 둘째 소식이 배지에 선다」는 성질은 이 파티션에
     * 의존하고, 뒤집히면 실 DB 거동 test 들이 **각자 다른 파티션으로 옮겨가** 둘 다 통과한다.
     */
    @Test
    fun `사실 시각 전진으로 읽음을 해제하는 종류는 공고 변경 하나다`() {
        val onNewerFact = NotificationKind.entries.filter { it.readResetPolicy == ReadResetPolicy.ON_NEWER_FACT }

        onNewerFact shouldContainExactlyInAnyOrder listOf(NotificationKind.NOTICE_REVISED)
    }

    /**
     * 음성 대조 — 나머지 전부가 **지금 규칙**이다. 위 등식만 있으면 「전부 `ON_NEWER_FACT`」로
     * 가는 변이가 그 등식에서만 붉고, 어느 쪽이 기본인지는 아무 단언도 묻지 않는다.
     */
    @Test
    fun `나머지 종류 전부는 detail 변화로만 읽음을 해제한다`() {
        val byDetailChange = NotificationKind.entries.filter { it.readResetPolicy == ReadResetPolicy.ON_DETAIL_CHANGE }

        byDetailChange shouldContainExactlyInAnyOrder
            NotificationKind.entries.filterNot { it == NotificationKind.NOTICE_REVISED }
    }

    companion object {
        /**
         * 사실 전수 — detail 을 갖는 둘은 한쪽 값만 둔다(종류 등식용). 반대쪽 값은
         * [DETAIL_BEARING_VARIANTS] 가 든다.
         *
         * **손 목록이다**(그리고 그것이 요점이다): 종류를 더하고 여기에 안 더하면 첫 test 가
         * 붉다. sealed 하위 타입을 반사로 훑으면 그 신호가 사라진다 — 목록이 자동으로 자라므로.
         */
        private val ALL_FACTS: List<NotificationFact> =
            listOf(
                NotificationFact.NewCandidate,
                NotificationFact.BidDeadlineImminent,
                NotificationFact.BidDeadlineToday,
                NotificationFact.DocumentDeadlineImminent,
                NotificationFact.NoticeRevised,
                NotificationFact.BidPriceRequestRecorded,
                NotificationFact.OpeningCompleted(provisionallyFirst = true),
                NotificationFact.AwardDecided(AwardOutcome.AWARDED),
            )

        /**
         * detail 을 갖는 둘의 **나머지 값 전부**. 낙찰 확정 쪽은 결과 전수에서 짓는다 — 손으로
         * 적으면 결과를 더하고 여기 빠뜨린 변형이 아래 왕복 등식의 모집단 밖에 남는다.
         */
        private val DETAIL_BEARING_VARIANTS: List<NotificationFact> =
            listOf(NotificationFact.OpeningCompleted(provisionallyFirst = false)) +
                AwardOutcome.entries
                    .filterNot { it == AwardOutcome.AWARDED }
                    .map { NotificationFact.AwardDecided(it) }
    }
}
