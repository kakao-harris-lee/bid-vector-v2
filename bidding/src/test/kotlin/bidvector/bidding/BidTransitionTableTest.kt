package bidvector.bidding

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 전이표 **전수** — 14 상태 × 13 명령 = 182 쌍 전부를 돌려 「표에 있는 것만 허용되고 나머지는
 * 전부 거부」를 잰다. 금지 목록을 따로 들지 않는 설계(`BID_TRANSITIONS` KDoc)는 이 전수가
 * 없으면 주장일 뿐이다.
 *
 * 모집단 자체도 먼저 단언한다 — 대표값 목록([ALL_STATES]·[ALL_COMMANDS])이 꼬리표 enum 과
 * 같은 집합이 아니면 전수가 조용히 줄어든다.
 */
class BidTransitionTableTest {
    private val outcomes =
        setOf(BidStateTag.WON, BidStateTag.LOST, BidStateTag.FAILED_OR_REBID)

    /**
     * **표와 독립인 골든 목록** — `ux-journey-research.md` §6.1 의 상태 기계와 계약 r2 의
     * 전이표를 손으로 옮긴 것이다. `BID_TRANSITIONS` 에서 **도출하지 않는다.**
     *
     * 이게 없으면 아래 전수 test 둘이 **순환**이다: 둘 다 `BID_TRANSITIONS` 를 읽어
     * 「표에 있으면 허용, 없으면 거부」를 재므로, 표에 금지 전이 한 줄을 더해도 두 test 가
     * 그 줄을 따라가며 초록을 유지한다(변이 ③ 실측 — 이 목록을 넣기 전에는 `WON → SKIP` 을
     * 더해도 전건 초록이었다). 정본을 코드 밖에 한 벌 더 두어야 표의 **변화 자체**가 잡힌다.
     */
    private val goldenTransitions: Set<Triple<BidStateTag, BidCommandTag, Set<BidStateTag>>> =
        setOf(
            Triple(BidStateTag.WATCHING, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED)),
            Triple(BidStateTag.PRICE_RECEIVED, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED)),
            Triple(BidStateTag.DEFERRED, BidCommandTag.RESUME, setOf(BidStateTag.WATCHING)),
            Triple(BidStateTag.WATCHING, BidCommandTag.RECEIVE_PRICE_MENU, setOf(BidStateTag.PRICE_RECEIVED)),
            Triple(BidStateTag.DEFERRED, BidCommandTag.RECEIVE_PRICE_MENU, setOf(BidStateTag.PRICE_RECEIVED)),
            Triple(BidStateTag.WATCHING, BidCommandTag.DRAFT_BID, setOf(BidStateTag.BID_PLANNED)),
            Triple(BidStateTag.DEFERRED, BidCommandTag.DRAFT_BID, setOf(BidStateTag.BID_PLANNED)),
            Triple(BidStateTag.PRICE_RECEIVED, BidCommandTag.DRAFT_BID, setOf(BidStateTag.BID_PLANNED)),
            Triple(BidStateTag.BID_PLANNED, BidCommandTag.DRAFT_BID, setOf(BidStateTag.BID_PLANNED)),
            Triple(BidStateTag.BID_PLANNED, BidCommandTag.RECORD_BID, setOf(BidStateTag.BID_RECORDED)),
            Triple(BidStateTag.BID_RECORDED, BidCommandTag.RECORD_BID, setOf(BidStateTag.BID_RECORDED)),
            Triple(
                BidStateTag.DEADLINE_PASSED_UNRECORDED,
                BidCommandTag.RECORD_BID,
                setOf(BidStateTag.BID_RECORDED),
            ),
            Triple(BidStateTag.BID_RECORDED, BidCommandTag.AWAIT_OPENING, setOf(BidStateTag.AWAITING_OPENING)),
            Triple(
                BidStateTag.AWAITING_OPENING,
                BidCommandTag.RECORD_OPENING_FIRST,
                setOf(BidStateTag.OPENED_PROVISIONAL_FIRST),
            ),
            Triple(
                BidStateTag.AWAITING_OPENING,
                BidCommandTag.RECORD_OPENING_OUT_OF_RANK,
                setOf(BidStateTag.OPENED_OUT_OF_RANK),
            ),
            Triple(
                BidStateTag.OPENED_PROVISIONAL_FIRST,
                BidCommandTag.ENTER_QUALIFICATION_REVIEW,
                setOf(BidStateTag.UNDER_QUALIFICATION_REVIEW),
            ),
            Triple(BidStateTag.OPENED_PROVISIONAL_FIRST, BidCommandTag.RECORD_OUTCOME, outcomes),
            Triple(BidStateTag.OPENED_OUT_OF_RANK, BidCommandTag.RECORD_OUTCOME, outcomes),
            Triple(BidStateTag.UNDER_QUALIFICATION_REVIEW, BidCommandTag.RECORD_OUTCOME, outcomes),
            Triple(BidStateTag.WON, BidCommandTag.CORRECT_OUTCOME, outcomes),
            Triple(BidStateTag.LOST, BidCommandTag.CORRECT_OUTCOME, outcomes),
            Triple(BidStateTag.FAILED_OR_REBID, BidCommandTag.CORRECT_OUTCOME, outcomes),
            Triple(BidStateTag.WATCHING, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED)),
            Triple(BidStateTag.DEFERRED, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED)),
            Triple(BidStateTag.PRICE_RECEIVED, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED)),
            Triple(BidStateTag.BID_PLANNED, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED)),
            Triple(
                BidStateTag.WATCHING,
                BidCommandTag.MARK_DEADLINE_PASSED,
                setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED),
            ),
            Triple(
                BidStateTag.DEFERRED,
                BidCommandTag.MARK_DEADLINE_PASSED,
                setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED),
            ),
            Triple(
                BidStateTag.PRICE_RECEIVED,
                BidCommandTag.MARK_DEADLINE_PASSED,
                setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED),
            ),
            Triple(
                BidStateTag.BID_PLANNED,
                BidCommandTag.MARK_DEADLINE_PASSED,
                setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED),
            ),
        )

    @Test
    fun `전이표가 §6_1 골든 목록과 정확히 같다 — 행을 더하거나 빼면 여기서 잡힌다`() {
        val actual =
            BID_TRANSITIONS.entries
                .map { (key, rule) -> Triple(key.from, key.command, rule.allowedTargets) }
                .toSet()
        actual shouldContainExactlyInAnyOrder goldenTransitions
    }

    @Test
    fun `표 밖 주체 조합도 골든과 같다 — 시스템 전용 하나와 사용자 전용 집합`() {
        val systemOnly =
            BID_TRANSITIONS.entries
                .filter { it.value.actors == setOf(ActorKind.SYSTEM) }
                .map { it.key.command }
                .toSet()
        systemOnly shouldContainExactlyInAnyOrder listOf(BidCommandTag.RECEIVE_PRICE_MENU)

        val userAndSystem =
            BID_TRANSITIONS.entries
                .filter { it.value.actors == setOf(ActorKind.USER, ActorKind.SYSTEM) }
                .map { it.key.command }
                .toSet()
        userAndSystem shouldContainExactlyInAnyOrder
            listOf(
                BidCommandTag.RESUME,
                BidCommandTag.AWAIT_OPENING,
                BidCommandTag.RECORD_OPENING_FIRST,
                BidCommandTag.RECORD_OPENING_OUT_OF_RANK,
                BidCommandTag.RECORD_OUTCOME,
                BidCommandTag.MARK_DEADLINE_PASSED,
            )
    }

    @Test
    fun `모집단 — 대표값 목록이 꼬리표 전수와 같다`() {
        ALL_STATES.map { it.tag } shouldContainExactlyInAnyOrder BidStateTag.entries
        ALL_COMMANDS.map { it.tag } shouldContainExactlyInAnyOrder BidCommandTag.entries
    }

    @Test
    fun `표에 없는 쌍은 전부 InvalidTransition 이다`() {
        val rejected = mutableListOf<TransitionKey>()
        for (state in ALL_STATES) {
            for (command in ALL_COMMANDS) {
                val key = TransitionKey(state.tag, command.tag)
                if (BID_TRANSITIONS.containsKey(key)) continue
                val outcome = applyCommand(state, command, ActorKind.USER, T0)
                outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
                outcome.reason shouldBe RejectionReason.InvalidTransition
                rejected += key
            }
        }
        // 182 쌍 중 표가 든 것을 뺀 나머지가 전부 거부로 관측됐는지 — 수를 못 박아 둔다.
        rejected.size shouldBe (BidStateTag.entries.size * BidCommandTag.entries.size) - BID_TRANSITIONS.size
    }

    @Test
    fun `표에 있는 쌍은 사용자 또는 시스템 주체로 허용된다`() {
        for (state in ALL_STATES) {
            for (command in ALL_COMMANDS) {
                val rule = BID_TRANSITIONS[TransitionKey(state.tag, command.tag)] ?: continue
                for (actor in rule.actors) {
                    val outcome = applyCommand(state, command, actor, T0)
                    outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
                    rule.allowedTargets.contains(outcome.next.tag) shouldBe true
                }
            }
        }
    }

    @Test
    fun `허락되지 않은 주체 종류는 ActorNotPermitted 다`() {
        for (state in ALL_STATES) {
            for (command in ALL_COMMANDS) {
                val rule = BID_TRANSITIONS[TransitionKey(state.tag, command.tag)] ?: continue
                for (actor in ActorKind.entries - rule.actors) {
                    val outcome = applyCommand(state, command, actor, T0)
                    outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
                    outcome.reason shouldBe RejectionReason.ActorNotPermitted
                }
            }
        }
    }

    @Test
    fun `투찰가 메뉴 수령은 사용자가 일으킬 수 없다 — 받았다를 지어내는 경로가 없다`() {
        val outcome =
            applyCommand(
                BidState.Watching,
                BidCommand.ReceivePriceMenu(PriceMenuRef("menu-1")),
                ActorKind.USER,
                T0,
            )
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.ActorNotPermitted
    }

    @Test
    fun `보류 상태는 어휘에 없다`() {
        BidStateTag.entries.map { it.name }.none { it.contains("HOLD") } shouldBe true
    }

    @Test
    fun `종단은 건너뜀 하나뿐이고 마감 지남은 종단이 아니다`() {
        BidStateTag.entries.filter { isTerminal(it) } shouldContainExactlyInAnyOrder
            listOf(BidStateTag.SKIPPED)
        isTerminal(BidStateTag.DEADLINE_PASSED_UNRECORDED) shouldBe false
    }

    @Test
    fun `마감 지남에서 나가는 간선은 소급 기록 하나뿐이다`() {
        BID_TRANSITIONS.keys
            .filter { it.from == BidStateTag.DEADLINE_PASSED_UNRECORDED }
            .map { it.command } shouldContainExactlyInAnyOrder listOf(BidCommandTag.RECORD_BID)
    }

    @Test
    fun `결과 셋은 정정으로 서로 오간다`() {
        val outcomes = listOf(BidState.Won, BidState.Lost, BidState.FailedOrRebid)
        for (from in outcomes) {
            for (target in BidOutcome.entries) {
                val outcome =
                    applyCommand(
                        from,
                        BidCommand.CorrectOutcome(target, TransitionReason("개찰 결과 정정")),
                        ActorKind.USER,
                        T0,
                    )
                outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
                outcome.next shouldBe target.toState()
                outcome.reason shouldBe TransitionReason("개찰 결과 정정")
            }
        }
    }

    @Test
    fun `건너뜀과 정정만 타임라인 사유를 남긴다`() {
        for (state in ALL_STATES) {
            for (command in ALL_COMMANDS) {
                val rule = BID_TRANSITIONS[TransitionKey(state.tag, command.tag)] ?: continue
                val outcome = applyCommand(state, command, rule.actors.first(), T0)
                outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
                val expectsReason =
                    command.tag == BidCommandTag.SKIP || command.tag == BidCommandTag.CORRECT_OUTCOME
                (outcome.reason != null) shouldBe expectsReason
            }
        }
    }
}
