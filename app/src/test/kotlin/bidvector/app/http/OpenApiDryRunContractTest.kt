package bidvector.app.http

import bidvector.adapters.evaluation.CandidateCapExceededException
import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.StrategyViolation
import bidvector.strategy.validate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import java.time.LocalDate

/**
 * D-6A3-11·D-6A3-20 — 평가 dry-run endpoint(`/api/evaluation-dry-runs`)의 계약 대조 전담.
 * `OpenApiContractTest`(평탄 스키마 정책 + `/api/strategy`)에서 sizeGate(v2-지침서 §5,
 * 파일당 500줄)로 분리했다 — `PredictionContractTest`→`PredictionAdditiveContractTest`
 * 선례와 같은 관심사 경계(기계적 절삭이 아니라 dry-run endpoint 하나의 전 상태 코드).
 * YAML 로딩·스키마 조회 헬퍼는 `OpenApiSpecSupport.kt`를 공유한다(중복 금지).
 *
 * D-6A3-20(검토 라운드 1) 시정 셋: contract-keeper V2(`ErrorCode` 상수 ↔ 문서 `enum` 양방향)·
 * R1(path 상태 코드 선언 집합)·R2(요청 스키마 속성·필수 집합 ↔ DTO), verifier M8
 * (409 `CANDIDATE_CAP_EXCEEDED` HTTP 층 대조), verifier R5(이 path 의 401·500도 `ErrorBody`
 * 키 등식).
 */
@Suppress("UNCHECKED_CAST")
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
class OpenApiDryRunContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var candidateSource: EmptyCandidateSource

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = freshStrategy()
        strategyRepository.loadFailure = null
        candidateSource.failure = null
    }

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun postDryRun(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/evaluation-dry-runs"),
            HttpMethod.POST,
            HttpEntity(body, authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    private fun strategyWithMaxActiveBids(maxActiveBids: Int): bidvector.strategy.OperatorStrategy {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val draft = StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = maxActiveBids)
        return when (val result = validate(draft, StrategyRevision(1), policy)) {
            is StrategyValidation.Valid -> result.strategy
            is StrategyValidation.Invalid -> error("test fixture가 유효하지 않다: ${result.violations}")
        }
    }

    @Test
    fun `평가 dry-run 200 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        // 후보가 없어도(candidateCount 0) 응답 스키마는 항상 아홉 키를 모두 낸다.
        strategyRepository.strategy = strategyWithMaxActiveBids(10)

        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 200
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("EvaluationDryRunResponse")
        response.body?.values?.none(::containsNestedObject) shouldBe true
    }

    @Test
    fun `평가 dry-run 409 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        // freshStrategy()는 maxActiveBids 를 설정하지 않는다 — fail-closed 409.
        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 409
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED
    }

    @Test
    fun `평가 dry-run 400 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = postDryRun(mapOf("currentActiveBids" to -1))

        response.statusCode.value() shouldBe 400
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    /**
     * D-6A3-20(검토 라운드 1 verifier R5) — 이 path 의 401·500 도 `ErrorBody` 키 등식으로
     * 대조한다. `/api/strategy` 는 이미 대조되지만(`OpenApiContractTest`), 이 path 는 여태
     * 200·400·409 만 잰다 — D-6A1-27 전수 test(GET 으로만 때린다) 뒤 실제 메서드(POST)에
     * 대한 증거이기도 하다.
     */
    @Test
    fun `평가 dry-run 401 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                url("/api/evaluation-dry-runs"),
                HttpMethod.POST,
                HttpEntity(mapOf("currentActiveBids" to 0), HttpHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
    }

    @Test
    fun `평가 dry-run 500 응답도 ErrorBody 계약과 일치한다`() {
        strategyRepository.loadFailure = {
            InvalidStoredStrategyException(listOf(StrategyViolation.CandidateLimitNotPositive))
        }

        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 500
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.INVALID_STORED_STRATEGY
    }

    /**
     * D-6A3-20 — D-6A3-7 의 HTTP 쪽 절반(verifier M8: `ErrorMapping` 의 `CandidateCapExceededException`
     * 행을 지우면 `@ExceptionHandler(Throwable)` 폴백으로 떨어져 500이 되는데도 이전까지는
     * 어떤 HTTP test 도 이 경로를 때리지 않아 전건 초록이었다). `EmptyCandidateSource.failure`
     * 로 그 예외를 직접 낸다 — production 배선(`JdbcCandidateSource`)의 SQL 실 상한 도달은
     * adapter 층(`JdbcCandidateSourceTest`)의 몫이다.
     */
    @Test
    fun `평가 dry-run 409 CANDIDATE_CAP_EXCEEDED 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        strategyRepository.strategy = strategyWithMaxActiveBids(10)
        candidateSource.failure = { CandidateCapExceededException(100) }

        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 409
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.CANDIDATE_CAP_EXCEEDED
    }

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper V2) — `ErrorCode` 의 상수 집합과 문서
     * `ErrorBody.code` 의 `enum` 이 **양방향** 등식이다. 한쪽에만 코드를 더하면(리플렉션이
     * `ErrorCode`를 직접 읽으므로 이 목록을 손으로 복제하지 않는다) RED가 된다.
     */
    @Test
    fun `ErrorCode 상수 집합과 문서 enum 이 양방향으로 같다`() {
        val declaredCodes =
            ErrorCode::class.java.fields
                .filter { it.type == String::class.java }
                .map { it.get(null) as String }
                .toSet()

        @Suppress("UNCHECKED_CAST")
        val documentedCodes =
            (spec.schema("ErrorBody")["properties"] as Map<String, Any?>)
                .let { properties -> properties["code"] as Map<String, Any?> }
                .let { codeDefinition -> codeDefinition["enum"] as List<String> }
                .toSet()

        documentedCodes shouldBe declaredCodes
    }

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper R1) — `paths` 의 상태 코드 **선언 집합**이
     * 문서 자신 안에서 기대값과 같다(verifier M7a·M7b·M7d — 409·401 을 지워도 이전에는
     * 어떤 test 도 「지워졌다」를 몰랐다). HTTP 호출 없이 YAML 만 본다.
     */
    @Test
    fun `각 path 의 상태 코드 선언 집합이 기대값과 같다`() {
        spec.responseStatusCodes("/api/strategy", "get") shouldBe setOf("200", "401", "500")
        spec.responseStatusCodes(
            "/api/evaluation-dry-runs",
            "post",
        ) shouldBe setOf("200", "400", "401", "409", "500")
        spec.responseStatusCodes("/{unmatched}", "get") shouldBe setOf("404")
    }

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper R2) — 요청 스키마의 **속성 키 집합**·**필수
     * 집합**이 DTO 필드와 같다(verifier M7c — 요청 스키마에 필드를 더해도 이전에는 어떤
     * test 도 몰랐다). 기존 `/api/strategy`는 요청 본문이 없어 이 축이 없다.
     *
     * **`kotlin-reflect` 없이 순수 Java reflection만 쓴다**(app 모듈에 그 의존이 없다 —
     * 새로 들이지 않는다, 재사용 우선). Kotlin `data class`의 프로퍼티는 생성자 파라미터와
     * 같은 이름의 인스턴스 필드로 컴파일된다 — non-null `Int`는 primitive `int` 필드가
     * 되므로 `isPrimitive`가 곧 「필수」다(이 DTO가 nullable 필드를 가지면 이 휴리스틱을
     * 다시 봐야 한다는 사실도 이 test가 실패로 알린다).
     */
    @Test
    fun `평가 dry-run 요청 스키마는 DTO 필드와 속성·필수 집합이 같다`() {
        val fields = EvaluationDryRunRequest::class.java.declaredFields.filterNot { it.isSynthetic }
        val parameterNames = fields.map { it.name }.toSet()
        val requiredParameterNames = fields.filter { it.type.isPrimitive }.map { it.name }.toSet()

        spec.propertyKeys("EvaluationDryRunRequest") shouldBe parameterNames
        spec.requiredKeys("EvaluationDryRunRequest") shouldBe requiredParameterNames
    }
}
