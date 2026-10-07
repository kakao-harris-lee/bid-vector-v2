package bidvector.app.evaluation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `Failed` 가 **종료 코드로 보이는지**(D-6F10-4) — 앞 판의 `reach` 는 알림 요청 결과를
 * 버렸고, 오늘 평가에 판정 기록 표가 없어 outbox 행이 판정의 유일한 영속 흔적이므로
 * 그것은 판정이 흔적 없이 사라진 채 run 이 성공으로 끝나는 길이었다.
 */
class EvaluationCommitExitCodeTest {
    @Test
    fun `요청이 전부 접수되면 COMPLETE 0 이다`() {
        val tally = tally(bidNow = 2, requested = 2)

        exitCodeOf(tally) shouldBe EvaluationCommitExitCode.COMPLETE
        EvaluationCommitExitCode.COMPLETE.value shouldBe 0
    }

    @Test
    fun `요청 하나라도 실패하면 INCOMPLETE 2 이고 0 이 아니다`() {
        val tally = tally(bidNow = 2, requested = 1, requestFailed = 1)

        exitCodeOf(tally) shouldBe EvaluationCommitExitCode.INCOMPLETE
        EvaluationCommitExitCode.INCOMPLETE.value shouldBe 2
    }

    /** 승격이 0 인 run 은 실패가 아니다 — 요청할 것이 없었다는 사실과 실패는 다르다. */
    @Test
    fun `승격이 없으면 COMPLETE 다`() {
        exitCodeOf(tally(candidates = 5, reached = 5)) shouldBe EvaluationCommitExitCode.COMPLETE
    }

    @Test
    fun `종료 코드 값은 서로 겹치지 않는다`() {
        val values = EvaluationCommitExitCode.entries.map { it.value }

        values.toSet().size shouldBe values.size
    }

    @Test
    fun `완료 로그 줄은 계수 여섯을 싣는다 — 공고 ID 는 싣지 않는다`() {
        val tally = tally(candidates = 4, reached = 3, notReached = 1, bidNow = 2, requested = 1, requestFailed = 1)

        evaluationCommitFinishLine(tally, exitCodeOf(tally)) shouldBe
            "evaluation-commit finished candidates=4 reached=3 notReached=1 bidNow=2 " +
            "requested=1 requestFailed=1 exit=2"
    }
}

@Suppress("LongParameterList")
private fun tally(
    candidates: Int = 0,
    reached: Int = 0,
    notReached: Int = 0,
    bidNow: Int = 0,
    requested: Int = 0,
    requestFailed: Int = 0,
) = EvaluationCommitTally(
    candidates = candidates,
    reached = reached,
    notReached = notReached,
    bidNow = bidNow,
    requested = requested,
    requestFailed = requestFailed,
)
