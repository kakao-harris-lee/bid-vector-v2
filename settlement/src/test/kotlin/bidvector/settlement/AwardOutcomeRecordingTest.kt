package bidvector.settlement

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * *"판정은 한 번만 기록되고 덮이지 않는다"*(capability map SET-01 분류 근거, legacy
 * `award_notifications.py:186-191`).
 */
class AwardOutcomeRecordingTest {
    @Test
    fun `기록이 없으면 확정 판정이 기록된다`() {
        recordAwardOutcome(RecordedAwardOutcome.Absent, SettledAwardOutcome.Won()) shouldBe
            AwardOutcomeRecording.Recorded(SettledAwardOutcome.Won())
        recordAwardOutcome(RecordedAwardOutcome.Absent, SettledAwardOutcome.Lost()) shouldBe
            AwardOutcomeRecording.Recorded(SettledAwardOutcome.Lost())
    }

    /** 확정이 아닌 결말은 기록되지 않는다 — 다음 관측에서 다시 판정된다. */
    @Test
    fun `미기록과 판정 안 함은 기록되지 않는다`() {
        val notRecorded = AwardOutcome.NotRecorded(WinnerNameAbsentReason.NotPublished)
        val notJudged = AwardOutcome.NotJudged(OperatorNameAbsentReason.NotDeclared)
        recordAwardOutcome(RecordedAwardOutcome.Absent, notRecorded) shouldBe
            AwardOutcomeRecording.NotSettled(notRecorded)
        recordAwardOutcome(RecordedAwardOutcome.Absent, notJudged) shouldBe
            AwardOutcomeRecording.NotSettled(notJudged)
    }

    /** 뒤집힌 판정이 와도 기존 확정을 **그대로** 돌려준다(반대 방향 둘 다 잰다). */
    @Test
    fun `이미 확정된 판정은 반대 판정으로 덮이지 않는다`() {
        recordAwardOutcome(
            RecordedAwardOutcome.Settled(SettledAwardOutcome.Won()),
            SettledAwardOutcome.Lost(),
        ) shouldBe AwardOutcomeRecording.AlreadySettled(SettledAwardOutcome.Won())

        recordAwardOutcome(
            RecordedAwardOutcome.Settled(SettledAwardOutcome.Lost()),
            SettledAwardOutcome.Won(),
        ) shouldBe AwardOutcomeRecording.AlreadySettled(SettledAwardOutcome.Lost())
    }

    /** 같은 판정이 다시 와도 「새로 기록」이 아니다 — 기록 시점이 둘이 되지 않는다. */
    @Test
    fun `같은 판정이 다시 와도 새 기록이 아니다`() {
        recordAwardOutcome(
            RecordedAwardOutcome.Settled(SettledAwardOutcome.Won()),
            SettledAwardOutcome.Won(),
        ) shouldBe AwardOutcomeRecording.AlreadySettled(SettledAwardOutcome.Won())
    }

    /** 확정 뒤에 「미기록」 관측이 와도 기록이 지워지지 않는다(되돌림도 덮어쓰기다). */
    @Test
    fun `확정 뒤 미기록 관측은 기록을 지우지 않는다`() {
        recordAwardOutcome(
            RecordedAwardOutcome.Settled(SettledAwardOutcome.Won()),
            AwardOutcome.NotRecorded(WinnerNameAbsentReason.NotPublished),
        ) shouldBe AwardOutcomeRecording.AlreadySettled(SettledAwardOutcome.Won())
    }
}
