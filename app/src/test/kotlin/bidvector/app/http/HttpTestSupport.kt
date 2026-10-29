package bidvector.app.http

import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.procurement.Notice
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.notice.NoticeSearchCriteria
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchPort
import bidvector.workflow.notice.NoticeSearchState
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.Socket
import java.time.Instant
import java.time.LocalDate
import java.util.Collections

/**
 * 매핑 패턴의 경로 변수(`{sessionId}` 등)를 구체 값으로 바꿀 때 쓰는 정규식 — 기계 전수
 * test 들이 공유한다(같은 값을 파일마다 베끼지 않는다).
 */
val PATH_VARIABLE_PATTERN: Regex = Regex("\\{[^/}]+}")

/** 실 자격증명과 다른, test 고정값 — 운영 값과 헷갈리지 않게 문구를 담는다(비밀값이 아니다). */
const val TEST_CREDENTIAL = "http-layer-test-fixture-credential"

/**
 * `BidVectorApplication.main()`이 굳히는 두 속성을 `HttpTestApplication` 기반 test에도
 * 그대로 준다 — `@SpringBootTest`는 `main()`을 부르지 않고, annotation 속성은 컴파일
 * 시간 상수만 받아 `main()`의 [bidvector.app.PRODUCTION_DISPATCH_PROPERTIES]를 직접
 * 참조할 수 없어 이 두 `const val`로 값만 복제한다.
 *
 * **D-6A1-27 — 이 복제는 드리프트를 스스로 드러내지 않는다.**
 * `main()`의 `PRODUCTION_DISPATCH_PROPERTIES`에서 두 속성을 지워도 이 test들은 **자기
 * 사본**을 그대로 쓰므로 영향받지 않고 exit 0이다. 실제 drift 감지는
 * `ProductionAssemblyAuthAuditTest`(production 조립을 직접 부팅하고
 * `PRODUCTION_DISPATCH_PROPERTIES`를 참조로 공유한다)가 진다 — 이 두 `const val`은
 * `HttpTestApplication` 계열 test의 **재현 사본**일 뿐, drift 게이트가 아니다.
 */
const val PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND = "spring.mvc.throw-exception-if-no-handler-found=true"
const val PROP_NO_STATIC_RESOURCE_MAPPINGS = "spring.web.resources.add-mappings=false"

/**
 * **이 test 전용 조립에는 관리 서버가 없다.** `-1` 은 Boot 의 `ManagementPortType
 * .DISABLED` 로, actuator 의 web endpoint 배선 자체가 올라오지 않는다.
 *
 * 왜 필요한가(실측): actuator 좌표가 들어오자 이 조립에 `RequestMappingHandlerMapping`
 * **빈이 둘**(우리 것 + actuator 의 controller endpoint 매핑)이 되어 `OperatorAuthenticationTest`
 * 의 타입 주입이 `NoUniqueBeanDefinitionException` 으로 깨졌다. 이름으로 한정하는 대신 조립에서
 * 빼는 쪽을 고른다 — 이 test 들이 재는 것은 **API 포트의 필터 체인**이고, 그 조립에 actuator 가
 * 있으면 「등록된 모든 endpoint」 기계 전수의 모집단이 우리 것이 아닌 경로로 오염된다.
 *
 * 출하 조립의 actuator 표면은 `ManagementHealthSurfaceTest`(별도 관리 포트)와 CI 스모크가 잰다 —
 * 그쪽은 포트가 갈려 actuator 매핑이 child context 에 살고 이 애매성이 애초에 없다.
 */
const val PROP_NO_MANAGEMENT_SERVER = "management.server.port=-1"

/**
 * `TestRestTemplate`을 **직접 만든다** — Boot 4.1의 `@AutoConfigureTestRestTemplate`
 * 자동 배선이 `@ConditionalOnMissingBean`의 타입 추론에서 예외를 던지는 것을 실측했다
 * (`org.springframework.boot.resttestclient.autoconfigure.TestRestTemplateTestAutoConfiguration
 * .testRestTemplate` — 이 Boot 버전 자체의 결함으로 보인다). 이 slice는 그
 * 자동 배선에 기대지 않고 `TestRestTemplate()`(빈 생성자, HttpClientOption vararg 0개)와
 * `@LocalServerPort`만으로 직접 URL을 조립한다 — 표준 API 조합이라 그 결함을 우회한다.
 */
abstract class HttpIntegrationTestBase {
    @LocalServerPort
    protected var port: Int = 0

    protected val restTemplate: TestRestTemplate = TestRestTemplate()

    protected fun url(path: String): String = "http://localhost:$port$path"
}

/** [rawGet] 이 돌려주는 것 — 상태 줄의 코드와 본문 전문이다(헤더는 이 자리가 재는 축이 아니다). */
data class RawHttpResponse(
    val status: Int,
    val body: String,
)

/**
 * 질의 **원문 그대로** 를 보내는 GET — `?q=%G1` 처럼 퍼센트 이스케이프 **문법**이 틀린 꼴은
 * `URI.create` 가 먼저 `URISyntaxException` 으로 거부해 서버까지 가지 못한다(실측). 재려는 것은
 * 「서버가 그 바이트열을 받았을 때 무슨 상태 코드를 내는가」라서 `RestTemplate`·`URI` 를 끼우지
 * 않고 소켓에 직접 쓴다. 바이트는 ISO-8859-1 로 옮긴다 — 요청 줄은 모두 ASCII 이고, 이 함수가
 * 보내려는 것은 **이미 퍼센트 인코딩된 문자열의 바이트 그대로** 이기 때문이다.
 *
 * [credential] 이 `null` 이면 자격증명 헤더를 **아예 붙이지 않는다**(개방 경로 측정용).
 */
fun rawGet(
    port: Int,
    target: String,
    credential: String?,
): RawHttpResponse =
    Socket("localhost", port).use { socket ->
        val credentialLine =
            // main 통합: 7D 가 그 상수를 인증기 쪽으로 옮겼다(필터 → 인증기 이름 변경). 값은 같다.
            credential?.let { "${OperatorCredentialAuthenticator.CREDENTIAL_HEADER}: $it\r\n" } ?: ""
        val request = "GET $target HTTP/1.1\r\nHost: localhost\r\n${credentialLine}Connection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.ISO_8859_1))
            flush()
        }
        val text = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        RawHttpResponse(
            status = text.substringAfter(' ').substringBefore(' ').toInt(),
            body = text.substringAfter("\r\n\r\n"),
        )
    }

/** [bidvector.strategy.validate]를 지나 만든 유효한 fixture — `OperatorStrategy`를 직접 짓지 않는다. */
fun freshStrategy(revision: Int = 1): OperatorStrategy {
    val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
    val draft = StrategyDraft(focusCategories = listOf("CAT-1"))
    return when (val result = validate(draft, StrategyRevision(revision), policy)) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture가 유효하지 않다: ${result.violations}")
    }
}

/**
 * `load()`를 제어할 수 있는 fake. **`save()`가 실제로 저장한다**: 편집 endpoint test 가 「confirm 뒤 `GET /api/strategy`
 * 가 새 값을 낸다」를 같은 저장소에서 확인한다.
 */
class TestStrategyRepository(
    @Volatile var strategy: OperatorStrategy = freshStrategy(),
) : StrategyRepository {
    @Volatile var loadFailure: (() -> Throwable)? = null

    override fun load(): OperatorStrategy = loadFailure?.let { throw it() } ?: strategy

    override fun save(applied: AppliedStrategy) {
        strategy = applied.strategy
    }
}

/**
 * M7/7C ① — `NoticeSearchController`(같은 패키지 main)가 조회기를 요구해 `HttpTestApplication`
 * 기반 test 가 DB 없이 검색 경로를 돈다. 실 질의 거동(이스케이프·basis·정렬·건수)은 이 자리가
 * 아니라 `bidvector.adapters.notice.JdbcNoticeSearchTest`(실 PostgreSQL)가 잰다 —
 * 여기서 재는 것은 **HTTP 층이 조건을 어떻게 만들고 결과를 어떤 응답으로 옮기는가**다.
 */
class FakeNoticeSearchPort : NoticeSearchPort {
    @Volatile var page: NoticeSearchPage = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NOTHING_COLLECTED)

    @Volatile var lastCriteria: NoticeSearchCriteria? = null

    override fun search(criteria: NoticeSearchCriteria): NoticeSearchPage {
        lastCriteria = criteria
        return page
    }
}

/**
 * M7/7C①b — 제안 목록의 이중체 원천·면허 게이트. `SuggestionController`(같은 패키지 main)가 조회기를
 * 요구해 `HttpTestApplication` 기반 test 가 DB 없이 제안 경로를 돈다. 감시 게이트는 **실 구현**
 * (`NoticeWatchSubjectPort`, 순수)이다 — 전략의 감시 조건이 실제로 거른다. 실 DB 거동은
 * `SuggestionProductionE2ETest` 가 잰다.
 */
class FakeSuggestionCandidates : CandidateSourcePort {
    @Volatile var notices: List<Notice> = emptyList()

    override fun openCandidates(): List<Notice> = notices
}

class FakeSuggestionLicenseGate : LicenseGatePort {
    @Volatile var verdictFor: (Notice) -> LicenseVerdict = {
        LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared)
    }

    override fun verdictFor(notice: Notice): LicenseVerdict = verdictFor.invoke(notice)
}

/** audit 쓰기 하나를 실패시킬 수 있는 recording sink — D-6A1-17 fail-closed 대조에 쓴다. */
class RecordingAuditSink {
    val records: MutableList<ApiAuditRecord> = Collections.synchronizedList(mutableListOf())

    @Volatile var failNext: Boolean = false

    fun record(record: ApiAuditRecord) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("의도된 audit 쓰기 실패(test)")
        }
        records += record
    }
}
