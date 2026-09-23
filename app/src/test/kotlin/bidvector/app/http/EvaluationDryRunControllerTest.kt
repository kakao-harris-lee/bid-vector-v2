package bidvector.app.http

import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate

/**
 * `EvaluationDryRunController`(같은 패키지 main) — 401 전수는 `OperatorAuthenticationTest`
 * 가 기계 전수로 이미 잰다(D-6A1-21, 새 매핑도 자동 포함, 이 slice에서 재확인). 이 test는
 * 이 endpoint 고유의 400·409·평탄 200 형태만 잰다 — 실 후보·판정 거동(공고 적재·outbox
 * 불변식)은 production 조립 E2E(acceptance ①~⑦)가 진다.
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND, PROP_NO_STATIC_RESOURCE_MAPPINGS],
)
class EvaluationDryRunControllerTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = freshStrategy()
        strategyRepository.loadFailure = null
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun post(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/evaluation-dry-runs"),
            HttpMethod.POST,
            HttpEntity(body, authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    /** D-6A3-19 — 원시 문자열 본문(비JSON·빈 본문 포함)을 그대로 보낸다. `Content-Type` 은 고정. */
    private fun postRaw(rawBody: String): ResponseEntity<Map<String, Any?>> {
        val headers = authorizedHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/evaluation-dry-runs"),
            HttpMethod.POST,
            HttpEntity(rawBody, headers),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    private fun strategyWithCap(maxActiveBids: Int): bidvector.strategy.OperatorStrategy {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val draft = StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = maxActiveBids)
        return when (val result = validate(draft, StrategyRevision(1), policy)) {
            is StrategyValidation.Valid -> result.strategy
            is StrategyValidation.Invalid -> error("test fixture가 유효하지 않다: ${result.violations}")
        }
    }

    @Test
    fun `전략에 maxActiveBids 가 없으면 409 를 낸다 — fail-closed`() {
        // freshStrategy() 는 maxActiveBids 를 설정하지 않는다(StrategyDraft 기본값).
        val response = post(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 409
        response.body?.get("code") shouldBe ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED
    }

    @Test
    fun `currentActiveBids 가 음수면 400 을 낸다 — maxActiveBids 미설정보다 우선한다`() {
        // freshStrategy() 도 maxActiveBids 가 없다 — 그래도 입력 형태(400) 가 저장소
        // 상태(409) 보다 먼저 걸러진다(EvaluationDryRunFactory.forRequest 순서 고정).
        val response = post(mapOf("currentActiveBids" to -1))

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `후보가 없으면 200 과 빈 배열·candidateCount 0 을 낸다`() {
        strategyRepository.strategy = strategyWithCap(maxActiveBids = 10)

        val response = post(mapOf("currentActiveBids" to 3))

        response.statusCode.value() shouldBe 200
        response.body?.get("candidateCount") shouldBe 0
        response.body?.get("currentActiveBids") shouldBe 3
        response.body?.get("maxActiveBids") shouldBe 10
        response.body?.get("bidNowNoticeIds") shouldBe emptyList<String>()
        response.body?.get("reviewNoticeIds") shouldBe emptyList<String>()
        response.body?.get("skipNoticeIds") shouldBe emptyList<String>()
        response.body?.get("notReachedNoticeIds") shouldBe emptyList<String>()
        response.body?.get("wouldNotifyNoticeIds") shouldBe emptyList<String>()
    }

    @Test
    fun `currentActiveBids 0 은 유효한 JSON 정수다 — 200`() {
        strategyRepository.strategy = strategyWithCap(maxActiveBids = 10)

        val response = post(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 200
        response.body?.get("currentActiveBids") shouldBe 0
    }

    /**
     * D-6A3-19(검토 라운드 1 contract-keeper V1 · verifier MEDIUM) — 요청 본문 입력 표.
     * 조용한 강제 변환(`"3"`→3, `1.7`→1)도, 파싱 실패의 500 낙하(비JSON·빈 본문)도 없다 —
     * 전부 400 `INVALID_REQUEST`(예외 메시지 미포함)로 통일된다. `maxActiveBids` 미설정
     * 전략(`freshStrategy()`)에서도 이 판정이 먼저다(형식 검증이 `factory.forRequest()`
     * 진입보다 앞선다 — `parseCurrentActiveBids`가 컨트롤러에서 저장소 호출 전에 돈다).
     */
    @ParameterizedTest(name = "{1} — {0}")
    @MethodSource("invalidRequestBodies")
    fun `요청 본문 형식 오류는 전부 400 INVALID_REQUEST 다`(
        rawBody: String,
        description: String,
    ) {
        withClue(description) {
            val response = postRaw(rawBody)

            response.statusCode.value() shouldBe 400
            response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
            // D-6A1-7 불변식 — 예외 메시지(원시 파싱 오류 문구 등)를 응답에 싣지 않는다.
            // 고정 문구 둘(파싱 실패/값 검증 실패) 중 하나여야 한다.
            val fixedMessages = setOf("요청 값이 유효하지 않다", "요청 본문을 읽을 수 없다")
            (response.body?.get("message") in fixedMessages) shouldBe true
        }
    }

    companion object {
        @JvmStatic
        fun invalidRequestBodies(): List<Arguments> =
            listOf(
                Arguments.of("{}", "누락"),
                Arguments.of("""{"currentActiveBids": null}""", "null"),
                Arguments.of("""{"currentActiveBids": "3"}""", "문자열"),
                Arguments.of("""{"currentActiveBids": 1.7}""", "소수"),
                Arguments.of("""{"currentActiveBids": 2147483648}""", "Int 범위 초과"),
                Arguments.of("""["currentActiveBids"]""", "object 아님"),
                Arguments.of("not valid json {", "비JSON"),
                Arguments.of("", "빈 본문"),
            )
    }
}
