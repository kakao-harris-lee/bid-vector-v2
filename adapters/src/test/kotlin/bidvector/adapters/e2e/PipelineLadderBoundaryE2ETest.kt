package bidvector.adapters.e2e

import bidvector.decision.BidNowReason
import bidvector.decision.Verdict
import bidvector.workflow.evaluation.CandidateEvaluation
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * 6D-1 축 ① 보강(verifier r1 F-3 / review G-4) — 승격 임계를 **두 방향으로** 잠근다.
 *
 * 앞 판은 위쪽만 잠겨 있었다: 측정 priority 가 임계 아래로 내려가면 outbox 가 비어 모든 E2E 가
 * 붉어지지만, **임계를 0 으로 내리는 변이는 아무 데서도 잡히지 않았다**(실측 12/12 GREEN).
 * 사다리가 무엇을 가르는지가 어느 단언에도 없었다는 뜻이다.
 *
 * 그래서 같은 run 에 **임계를 사이에 둔 후보 둘**을 넣는다. 한쪽은 공고 텍스트와 프로필 텍스트의
 * 임베딩이 같아 match 가 높고, 다른 쪽은 **직교 벡터**라 match 가 바닥이다. 임계를 0 으로 바꾸면
 * 둘 다 승격돼 이 test 가 붉어진다.
 *
 * 임계 값 자체는 리터럴로 남는다 — 사다리 임계의 운영 값은 아직 승인 전이다
 * (`OPEN-4B1-LADDER-THRESHOLDS`). 이 test 가 잠그는 것은 값이 아니라 **그 값이 후보를 가른다는
 * 사실**이다.
 */
internal class PipelineLadderBoundaryE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    @Test
    fun `임계 위 후보만 승격되고 임계 아래 후보는 outbox 에 닿지 않는다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(HIGH_MATCH_NOTICE), e2eNoticeItem(LOW_MATCH_NOTICE)))

        val evaluations = runBlocking { lowMatchAssembly().evaluate() }
        val reached = evaluations.filterIsInstance<CandidateEvaluation.Reached>()

        reached shouldHaveSize TWO_CANDIDATES
        promotedNoticeNumbers(reached) shouldContainExactly listOf(HIGH_MATCH_NOTICE)
        promotionReason(reached).threshold shouldBe BigDecimal(E2E_BID_NOW_THRESHOLD)
        promotionReason(reached).priority shouldBeGreaterThanOrEqualTo BigDecimal(E2E_BID_NOW_THRESHOLD)
        outboxIdempotencyKeys() shouldHaveSize 1
        outboxIdempotencyKeys().single() shouldBe idempotencyKeyFor(HIGH_MATCH_NOTICE)
    }

    private fun promotedNoticeNumbers(reached: List<CandidateEvaluation.Reached>): List<String> =
        reached.filter { it.verdict is Verdict.BidNow }.map { it.noticeId.number.value }

    private fun promotionReason(reached: List<CandidateEvaluation.Reached>): BidNowReason.PriorityAboveBidNowThreshold {
        val verdict = reached.map { it.verdict }.filterIsInstance<Verdict.BidNow>().single()
        return verdict.reasons.filterIsInstance<BidNowReason.PriorityAboveBidNowThreshold>().single()
    }

    /** 직교 벡터는 [LOW_MATCH_NOTICE] 번호가 든 텍스트에만 간다 — 다른 후보는 평소 배선 그대로다. */
    private fun lowMatchAssembly(): PipelineAssembly {
        val release = e2eRelease()
        val script = successfulMlScript(release = release)
        val server =
            MlFakeServer.start(
                script = script,
                embeddingResponder = lowMatchEmbeddingResponder(LOW_MATCH_NOTICE, release, script.embeddingResponse),
            )
        servers += server
        return PipelineAssembly(
            dataSource = dataSource(),
            mlChannel = server.channel,
            at = E2E_NOW,
            correlationPrefix = "ladder",
            mlPolicy = e2eMlCallPolicy(),
        )
    }

    private companion object {
        const val HIGH_MATCH_NOTICE = "E2E-LADDER-HIGH"
        const val LOW_MATCH_NOTICE = "E2E-LADDER-LOW"
        const val TWO_CANDIDATES = 2
    }
}
