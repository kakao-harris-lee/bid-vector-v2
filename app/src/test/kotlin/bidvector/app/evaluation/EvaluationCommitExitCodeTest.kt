package bidvector.app.evaluation

import bidvector.decision.PublishedFloorExclusion
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
    fun `완료 로그 줄은 계수 여섯과 하한 제외 사유 전수를 싣는다 — 공고 ID 는 싣지 않는다`() {
        val tally = tally(candidates = 4, reached = 3, notReached = 1, bidNow = 2, requested = 1, requestFailed = 1)

        evaluationCommitFinishLine(tally, exitCodeOf(tally)) shouldBe
            "evaluation-commit finished candidates=4 reached=3 notReached=1 bidNow=2 " +
            "requested=1 requestFailed=1 floorImplausible=0 floorOriginNotJudged=0 exit=2"
    }

    /**
     * DEC-10 acceptance 셋째 — 제외된 건수가 계수되고 **침묵 스킵이 아니다**. 0 건도 줄에
     * 나온다: 「그 사유가 0 이었다」와 「그 사유를 세지 않았다」가 구별되지 않으면 계수가 없는
     * 것과 같다(위 test 가 그 0 을 고정한다).
     */
    @Test
    fun `하한 제외가 있으면 사유별로 줄에 나온다`() {
        val tally =
            tally(
                candidates = 3,
                reached = 3,
                floorExclusions =
                    mapOf(
                        PublishedFloorExclusion.IMPLAUSIBLE to 2,
                        PublishedFloorExclusion.ORIGIN_NOT_JUDGED to 1,
                    ),
            )

        evaluationCommitFinishLine(tally, exitCodeOf(tally)) shouldBe
            "evaluation-commit finished candidates=3 reached=3 notReached=0 bidNow=0 " +
            "requested=0 requestFailed=0 floorImplausible=2 floorOriginNotJudged=1 exit=0"
    }

    /** 계수 이름은 Kotlin 상수 이름에서 파생되지 않는다 — DEC-10 이 `floor_implausible` 을 고정한다. */
    @Test
    fun `제외 사유 전수가 줄에 서로 다른 이름으로 나온다`() {
        val line = evaluationCommitFinishLine(tally(), EvaluationCommitExitCode.COMPLETE)
        val names = PublishedFloorExclusion.entries.map { exclusion -> "$exclusion" }

        names.forEach { name -> line.contains(name) shouldBe false }
        line.contains("floorImplausible=0") shouldBe true
        line.contains("floorOriginNotJudged=0") shouldBe true
    }

    /** 제외는 사다리 판정과 다른 축이다 — 제외가 있어도 종료 코드는 요청 실패만 본다. */
    @Test
    fun `하한 제외는 종료 코드를 바꾸지 않는다`() {
        val tally = tally(reached = 1, floorExclusions = mapOf(PublishedFloorExclusion.IMPLAUSIBLE to 1))

        exitCodeOf(tally) shouldBe EvaluationCommitExitCode.COMPLETE
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
    floorExclusions: Map<PublishedFloorExclusion, Int> = emptyMap(),
) = EvaluationCommitTally(
    candidates = candidates,
    reached = reached,
    notReached = notReached,
    bidNow = bidNow,
    requested = requested,
    requestFailed = requestFailed,
    floorExclusions = floorExclusions,
)
