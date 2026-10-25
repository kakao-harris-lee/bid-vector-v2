package bidvector.bidding

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * **전이표의 불변식을 속성으로** 잰다(계약 산출물 7 「속성 test 포함」 — 검토 r1 C-M10 · 계약 D-7B-3).
 *
 * 전수 열거(`BidTransitionTableTest`, 14 × 14 = 196 쌍)는 표의 **모양**을 골든과 맞댄다. 여기서는 표에 대한
 * 질문이 아니라 **판정 함수(`applyCommand`)의 성질**을 임의 입력(상태 · 명령 · 주체 · 시각)으로 묻는다 —
 * 시각과 주체까지 섞어도 아래 넷이 늘 참이어야 한다.
 *
 * 1. 받아들여진 전이는 **표에 있는 행**이고, 그 행이 허락한 **주체**이며, 도착이 그 행의 **도착 집합 안**이다.
 * 2. 소급은 **마감 지남에서 온 기록**뿐이다(Q-11 — 출발 상태가 정한다).
 * 3. 건너뜀(종단)에서는 어떤 명령도 받아들여지지 않는다.
 * 4. 판정은 결정적이다(같은 입력 → 같은 결과).
 * 5. **반대 방향**: 표에 행이 있고 그 주체가 허락되면 **반드시** 받아들여진다(대표값의 도착은 전부 도착 집합 안이다).
 *
 * 그리고 **공허성 대조**(검토 r2 C2-N16): 받아들여진 판정이 한 번도 없으면 1 · 3 은 공허하게 참이다 — 늘 거부하는 변이가
 * 초록이 되지 않도록 받아들임이 실제로 일어났는지 센다.
 */
class BidTransitionPropertyTest {
    @Test
    fun `받아들여진 전이는 표의 행 · 주체 · 도착 집합 안에 있고 소급은 마감 지남의 기록뿐이다`() {
        var accepted = 0
        runBlocking {
            checkAll(
                Arb.element(ALL_STATES),
                Arb.element(ALL_COMMANDS),
                Arb.enum<ActorKind>(),
                Arb.long(0L, YEAR_SECONDS),
            ) { state, command, actor, offset ->
                val now = T0.plusSeconds(offset)
                val outcome = applyCommand(state, command, actor, now)

                val rule = BID_TRANSITIONS[TransitionKey(state.tag, command.tag)]
                (outcome is TransitionOutcome.Accepted) shouldBe (rule != null && actor in rule.actors)
                if (outcome is TransitionOutcome.Accepted) {
                    accepted++
                    (rule != null && outcome.next.tag in rule.allowedTargets) shouldBe true
                    outcome.retroactive shouldBe
                        (state.tag == BidStateTag.DEADLINE_PASSED_UNRECORDED && command.tag == BidCommandTag.RECORD_BID)
                }
                if (state.tag == BidStateTag.SKIPPED) {
                    (outcome is TransitionOutcome.Rejected) shouldBe true
                }
                applyCommand(state, command, actor, now) shouldBe outcome
            }
        }
        (accepted > 0) shouldBe true
    }

    private companion object {
        const val YEAR_SECONDS = 31_536_000L
    }
}
