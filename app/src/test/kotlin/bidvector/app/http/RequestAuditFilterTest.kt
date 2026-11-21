package bidvector.app.http

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

/**
 * 우회 (2)(audit이 성공 경로에만 있으면 안 된다) · D-6A1-17(fail-closed) · D-6A1-21(디스패치
 * 실측)을 잰다.
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
class RequestAuditFilterTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var auditSink: RecordingAuditSink

    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @BeforeEach
    fun resetFixtures() {
        auditSink.failNext = false
        strategyRepository.loadFailure = null
        strategyRepository.strategy = freshStrategy()
        // **M15/15A — 로그인을 계수 초기화 **앞**에 세운다.** `POST /api/session` 도 요청이라
        // audit 행을 남긴다. 세션을 test 본문에서 지연 초기화하면 그 행이 「요청 하나 = 행 하나」
        // 계수에 섞이고(실측 2), audit 쓰기를 깨뜨리는 test 에서는 로그인 자체가 500 이 된다.
        session.headers()
        auditSink.records.clear()
    }

    @AfterEach
    fun resetAfter() {
        auditSink.failNext = false
        strategyRepository.loadFailure = null
    }

    /**
     * **M15/15A — 이 경로는 계정 전용이다.** V28 이 전략을 계정 소유로 옮기면서 자격 헤더 경로
     * (계정을 내지 않는다)는 401 이고, 그래서 이 test 는 세션으로 두드린다. 재는 축은 그대로다.
     */
    private val session by lazy { SessionTestClient(restTemplate, ::url).signIn() }

    private fun authorizedHeaders(): HttpHeaders = session.headers()

    @Test
    fun `성공 요청은 audit 행 하나를 남긴다`() {
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                String::class.java,
            )

        response.statusCode.value() shouldBe 200
        auditSink.records.size shouldBe 1
        auditSink.records.first().status shouldBe 200
        // M15/15A — 주체는 세션 계정 라벨이다(이 test 의 축은 audit 행이지 주체 어휘가 아니다).
        auditSink.records.first().subject shouldStartWith "account:"
    }

    @Test
    fun `인증 실패 요청도 audit 행을 남긴다 — 우회 (2)`() {
        restTemplate.getForEntity(url("/api/strategy"), String::class.java)

        auditSink.records.size shouldBe 1
        auditSink.records.first().status shouldBe 401
        auditSink.records.first().subject shouldBe "unauthenticated"
    }

    @Test
    fun `컨트롤러 예외도 audit 행을 남기고 예외 메시지를 노출하지 않는다 — 우회 (2)(3)`() {
        strategyRepository.loadFailure = { RuntimeException("절대-응답에-노출되면-안되는-내부-문구") }

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 500
        auditSink.records.size shouldBe 1
        auditSink.records.first().status shouldBe 500
        (response.body?.get("message") as? String ?: "").contains("절대-응답에-노출되면-안되는-내부-문구") shouldBe false
        response.body?.get("code") shouldBe ErrorCode.INTERNAL_ERROR
    }

    @Test
    fun `D-6A1-17 — audit 쓰기 실패는 fail-closed다, 성공한 조회 결과가 새지 않는다`() {
        auditSink.failNext = true

        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 500
        response.body?.get("code") shouldBe ErrorCode.INTERNAL_ERROR
        response.body?.containsKey("focusCategories") shouldBe false
        response.body?.containsKey("revision") shouldBe false
    }

    @Test
    fun `D-6A1-21 — 미매핑 경로도 같은 REQUEST 디스패치에서 끝나고 audit 행이 정확히 하나다`() {
        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                url("/does-not-exist"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 404
        response.body?.get("code") shouldBe ErrorCode.NOT_FOUND
        // Boot 기본 BasicErrorController/whitelabel 응답의 표식(timestamp·trace)이 없다는
        // 것은 컨테이너의 별도 ERROR 재디스패치가 아니라 우리 GlobalErrorHandler가 같은
        // REQUEST 디스패치 안에서 처리했다는 실측이다(D-6A1-21).
        response.body?.containsKey("timestamp") shouldBe false
        response.body?.containsKey("trace") shouldBe false
        auditSink.records.size shouldBe 1
        auditSink.records.first().status shouldBe 404
    }
}
