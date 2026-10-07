package bidvector.workflow.evaluation

import bidvector.decision.Verdict
import bidvector.sharedkernel.EffectiveFrom
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * D-6F10-4·D-6F10-19 — `reach` 가 알림 요청의 **결과를 값으로 돌려주고**, payload 가 **판정에
 * 쓴 정책 버전·전략 개정과 같은 값**을 나르는지.
 *
 * 앞 판은 `notifications.request(...)` 의 반환을 버렸다. 오늘 평가에는 판정 기록 표가 없어
 * (D-6F7-2) outbox 행이 판정의 **유일한 영속 흔적**이므로, 그 반환을 버리는 것은 판정이
 * 흔적 없이 사라진 채 run 이 성공으로 끝나는 길이었다.
 */
class NotificationDispositionTest {
    @Test
    fun `승격이면 요청 결과가 Reached 에 실린다 — Requested`() {
        val reached = evaluateSingle(FakeNotificationRequestPort())

        reached.verdict.shouldBeInstanceOf<Verdict.BidNow>()
        reached.disposition shouldBe
            NotificationDisposition.Requested(NotificationRequestOutcome.Requested)
    }

    /** **버려지지 않는다** — 이 단언이 없으면 outbox 쓰기 실패가 run 결과에서 사라진다. */
    @Test
    fun `outbox 쓰기가 실패하면 Failed 가 값으로 올라온다`() {
        val reached = evaluateSingle(FakeNotificationRequestPort(NotificationRequestOutcome.Failed))

        reached.disposition shouldBe
            NotificationDisposition.Requested(NotificationRequestOutcome.Failed)
    }

    /**
     * 승격이 아니면 **요청 자체가 없다** — 실패가 아니라 해당 없음이다. `null` 이었다면
     * 소비자가 그 둘을 한 분기로 접었을 자리다(v2-지침서 §5 「불법 상태를 타입으로」).
     */
    @Test
    fun `승격이 아니면 NotApplicable 이고 요청은 0 건이다`() {
        val notifications = FakeNotificationRequestPort()
        val notice = testNotice()
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = FakeMlAnalysisPort { skipAnalysis() },
                notifications = notifications,
            )

        val reached =
            runBlocking { useCase.evaluate() }
                .single()
                .shouldBeInstanceOf<CandidateEvaluation.Reached>()

        // 이 test 의 주제는 사다리 결과가 아니라 **처분**이다 — 필요한 전제는 「승격이
        // 아니다」 하나다. 어느 비-승격 갈래인지(Skip·Review)는 fixture 의 점수 조합이
        // 정하고, 그것을 여기서 고정하면 사다리 정책이 바뀔 때 이 test 가 엉뚱하게 붉어진다.
        reached.verdict.shouldNotBeInstanceOf<Verdict.BidNow>()
        reached.disposition shouldBe NotificationDisposition.NotApplicable
        notifications.requested.shouldBeEmpty()
    }

    /**
     * 값 두 축 ②(D-6F10-8) — **전략 개정만 바꾸면 payload 가 달라진다.** 그리고 사다리 정책
     * 버전은 판정에 쓴 것과 **같은 인스턴스**다(두 자리에 적으면 갈린다).
     */
    @Test
    fun `payload 는 판정에 쓴 정책 버전과 전략 개정을 나른다`() {
        val notifications = FakeNotificationRequestPort()
        evaluateSingle(notifications, strategyRevision = 7)

        val request = notifications.requested.single()
        request.strategyRevision.value shouldBe 7
        request.ladderPolicyVersion shouldBe EVALUATION_LADDER_POLICY_VERSION
        request.ladderPolicyVersion.effectiveFrom shouldBe EffectiveFrom.Initial
    }

    @Test
    fun `전략 개정이 다르면 같은 입력의 payload 가 달라진다 — 거동 축`() {
        val first = FakeNotificationRequestPort()
        val second = FakeNotificationRequestPort()

        evaluateSingle(first, strategyRevision = 1)
        evaluateSingle(second, strategyRevision = 2)

        first.requested.single().strategyRevision shouldNotBe second.requested.single().strategyRevision
    }
}

private fun evaluateSingle(
    notifications: FakeNotificationRequestPort,
    strategyRevision: Int = 1,
): CandidateEvaluation.Reached {
    val notice = testNotice()
    val useCase =
        useCase(
            strategyRepository = FakeStrategyRepository(testStrategy(revision = strategyRevision)),
            candidateSource = FakeCandidateSource(listOf(notice)),
            mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            notifications = notifications,
        )
    return runBlocking { useCase.evaluate() }
        .single()
        .shouldBeInstanceOf<CandidateEvaluation.Reached>()
}
