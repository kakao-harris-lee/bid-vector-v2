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

    @Test
    fun `낙찰 확정의 낙찰 여부가 detail 두 값으로 갈린다`() {
        NotificationFact.AwardDecided(awarded = true).detail shouldBe NotificationDetail.AWARDED
        NotificationFact.AwardDecided(awarded = false).detail shouldBe NotificationDetail.NOT_AWARDED
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
                NotificationFact.AwardDecided(awarded = true),
            )

        private val DETAIL_BEARING_VARIANTS: List<NotificationFact> =
            listOf(
                NotificationFact.OpeningCompleted(provisionallyFirst = false),
                NotificationFact.AwardDecided(awarded = false),
            )
    }
}
