package bidvector.adapters.e2e

import bidvector.adapters.ml.GrpcBidPredictionGateway
import bidvector.adapters.ml.testBidPredictionRequest
import bidvector.adapters.ml.testMlCallEffectivePolicy
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.ModelReleaseSelector
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneOffset

/**
 * 6D-1 축 ②④ — 계약 위반 두 갈래와 release rollback. 두 갈래의 응답 바이트는 **계약 파일의
 * 골든**에서 온다(`contracts/testdata/prediction`) — test 안 리터럴이 아니라 파일이라, 파일이
 * 사라지거나 바뀌면 로딩이 던져 RED 다(설계 검토 (2) 우회 3).
 *
 * **두 갈래는 서로 반대 결과를 내야 한다** — 정의 밖 필드는 **보존**되고(proto3 전방 호환,
 * 평가가 그대로 진행), schema 거부는 **예측 없음**으로 접힌다. 한쪽만 재면 「무엇이 와도 같은
 * 결과」인 배선과 구별되지 않는다.
 */
internal class PipelineContractRejectionE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    @Test
    fun `정의 밖 필드가 든 성공 응답은 거부되지 않고 근거가 그대로 남는다`() {
        val release = e2eRelease()
        val response = predictionResponseWithUnknownField(release)
        unknownFieldCountOf(response) shouldBe 1

        val payload = evaluateWith(successfulMlScript(release = release, predictionResponse = response))

        payload shouldContain "DIAGNOSED"
        payload shouldContain E2E_RELEASE_ID
        payload shouldNotContain "NOT_PREDICTED"
    }

    @Test
    fun `지원하지 않는 schema 거부 골든은 예측 없음으로 접히고 사유가 payload 에 남는다`() {
        val payload = evaluateWith(successfulMlScript(predictionResponse = predictionUnsupportedSchemaResponse()))

        payload shouldContain "NOT_PREDICTED"
        payload shouldContain "UnsupportedSchema"
        payload shouldNotContain "DIAGNOSED"
    }

    /**
     * model rollback 의 정의(운영자 결정 C-3 (a)) — **릴리스 선택자 `EXACT` 로 직전 release 를
     * 지정**하는 것이다(demote 기제를 새로 만들지 않는다). 같은 서버·같은 gateway 에서 선택자만
     * 바꿔 부르고, 돌아온 release 가 서로 다름을 단언한다.
     *
     * 이 축은 **gateway 수준에서** 잰다 — 파이프라인이 쓰는 선택자는 `OPPORTUNITY_POLICY` 에
     * `LatestPromoted` 로 고정돼 있고 그 정책을 바꾸는 것은 production 변경이라 이 slice 밖이다
     * (알려진 제한).
     */
    @Test
    fun `EXACT 선택자는 직전 release 를 돌려주고 LATEST 와 다른 releaseId 를 낸다`() {
        val latest = e2eRelease(E2E_RELEASE_ID)
        val rolledBack = e2eRelease(E2E_ROLLBACK_RELEASE_ID)
        val script = successfulMlScript(release = latest)
        val server =
            MlFakeServer.start(
                script = script,
                responder =
                    PredictionResponder { request ->
                        if (request.envelope.modelReleaseSelector.hasExactRelease()) {
                            predictionSuccess(rolledBack)
                        } else {
                            predictionSuccess(latest)
                        }
                    },
            )
        servers += server
        val gateway =
            GrpcBidPredictionGateway(
                server.channel,
                testMlCallEffectivePolicy(e2eMlCallPolicy()),
                java.time.Clock.fixed(E2E_NOW, ZoneOffset.UTC),
            )

        val outcomes =
            runBlocking {
                val promoted = gateway.predict(testBidPredictionRequest(ModelReleaseSelector.LatestPromoted), budget())
                val exact =
                    gateway.predict(
                        testBidPredictionRequest(
                            ModelReleaseSelector.Exact(E2E_ROLLBACK_RELEASE_ID, E2E_ARTIFACT_CHECKSUM),
                        ),
                        budget(),
                    )
                promoted to exact
            }

        val promoted = outcomes.first.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        val exact = outcomes.second.shouldBeInstanceOf<BidPredictionOutcome.Predicted>()
        promoted.release.releaseId shouldBe E2E_RELEASE_ID
        exact.release.releaseId shouldBe E2E_ROLLBACK_RELEASE_ID
        exact.release.releaseId shouldNotBe promoted.release.releaseId
    }

    private fun evaluateWith(script: MlFakeScript): String {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
        val server = MlFakeServer.start(script)
        servers += server
        val assembly =
            PipelineAssembly(
                dataSource = dataSource(),
                mlChannel = server.channel,
                at = E2E_NOW,
                correlationPrefix = "contract",
                mlPolicy = e2eMlCallPolicy(),
            )
        runBlocking { assembly.evaluate() }
        return outboxPayloads().single()
    }

    private fun budget(): CallBudget = CallBudget(CALL_BUDGET)

    private companion object {
        const val NOTICE = "E2E-CONTRACT-0001"
        val CALL_BUDGET: Duration = Duration.ofSeconds(5)
    }
}
