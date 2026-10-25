package bidvector.guardprobe

import bidvector.app.http.AccountBindingGuard
import bidvector.app.http.GlobalErrorHandler
import bidvector.workflow.access.AccountId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.format.FormatterRegistry
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode
import java.util.concurrent.atomic.AtomicInteger

/**
 * 가드 test 와 그 양성 대조(가드 없는 조립 — `bidvector.guardless`)가 함께 쓰는 **모양 표**. 이름 · 메서드 · 경로 · 본문.
 * r5 의 H1~H5 · G11, Jackson 층만 재는 HE, 단일 `AccountId` 요청 매개변수 RP, `Map` 키 MK(검토 r6 V6-L1).
 */
internal data class ProbeShape(
    val name: String,
    val method: HttpMethod,
    val path: String,
    val body: String?,
)

internal val PROBE_SHAPES =
    listOf(
        ProbeShape("H1 두 겹 중첩 본문", HttpMethod.POST, "/probe/h1", """{"inner":{"account":9}}"""),
        ProbeShape("H2 setter 운반", HttpMethod.POST, "/probe/h2", """{"account":9}"""),
        ProbeShape("H3 상위 클래스 필드", HttpMethod.POST, "/probe/h3", """{"account":9}"""),
        ProbeShape("H4 제네릭 T", HttpMethod.POST, "/probe/h4", "9"),
        ProbeShape("HE 본문 HttpEntity(바인더 없음)", HttpMethod.POST, "/probe/entity", """{"inner":{"account":9}}"""),
        ProbeShape("MK 본문 Map 의 키", HttpMethod.POST, "/probe/mapkey", """{"9":"x"}"""),
        ProbeShape("H5 @ModelAttribute 메서드", HttpMethod.GET, "/probe/h5?value=9", null),
        ProbeShape("G11 핸들러 매개변수", HttpMethod.GET, "/probe/g11?value=9", null),
        ProbeShape("RP 단일 AccountId 요청 매개변수", HttpMethod.GET, "/probe/rp?account=9", null),
    )

/** 모양 하나를 실제 요청으로 부른다 — 상태 코드. */
internal fun callProbe(
    restTemplate: TestRestTemplate,
    port: Int,
    shape: ProbeShape,
): Int {
    val headers = HttpHeaders().apply { if (shape.body != null) contentType = MediaType.APPLICATION_JSON }
    return restTemplate
        .exchange(
            "http://localhost:$port${shape.path}",
            shape.method,
            HttpEntity(shape.body, headers),
            String::class.java,
        ).statusCode
        .value()
}

/**
 * **요청 바인딩은 `AccountId` 를 짓지 않는다 — 모양과 무관하게**(M7/7B 계약 D-7B-7 ① · 검토 r5 V5-N1 · r6 R6-3).
 *
 * 출하 가드 [AccountBindingGuard] 를 **실제 MVC · Jackson 자동 구성** 위에 올리고 [PROBE_SHAPES] 를 실제 요청으로 부른다: 모양마다
 * **정확히 400** 이고(404 · 405 를 통과로 세지 않는다) **핸들러에 닿지 않는다**(도달 계수 0). 모양의 경로가 이 조립에 **실제로
 * 매핑돼 있음**을 함께 단언하고, 가드를 뺀 조립(`GuardlessProbeTest`)에서 같은 모양이 핸들러에 닿는다(200 · 도달 1)는 것이 양성
 * 대조다. 본문 DTO 는 두 층이 겹친다 — `@RequestBody` 는 역직렬화 뒤 대상이 DTO 인 바인더를 만들어 `@InitBinder` 층도 거부하므로,
 * 바인더를 만들지 않는 `HttpEntity` 모양(HE · MK)이 Jackson 층만 따로 잰다. RP 는 **단일 `AccountId` 매개변수**다(용기 모양
 * `List` · `Array` · `Optional` 은 이 가드의 범위 밖 — §6). 그리고 본문 `JsonNode` 핸들러는 그대로 닿는다(가드가 모든 요청을 막아
 * 공허하게 초록인 것이 아니다).
 *
 * 이 조립은 **이 test 의 패키지에만** 있다(`bidvector.guardprobe` — 출하 조립 `bidvector.app` · HTTP test 조립
 * `bidvector.app.http` 의 컴포넌트 스캔 밖). 그래서 이 핸들러들이 HTTP 표면 센서스를 오염시키지 않는다(검토 r5 R1 의 측정 오염).
 */
@SpringBootTest(
    classes = [GuardProbeApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class AccountBindingGuardTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    private val restTemplate = TestRestTemplate()

    @BeforeEach
    fun reset() {
        Reached.count.set(0)
    }

    @Test
    fun `AccountId 를 싣는 모양은 전부 정확히 400 이고 핸들러에 닿지 않는다`() {
        val registered =
            handlerMapping.handlerMethods.keys
                .flatMap { info ->
                    info.pathPatternsCondition
                        ?.patterns
                        .orEmpty()
                        .map { it.patternString }
                }.toSet()
        PROBE_SHAPES.map { it.path.substringBefore('?') }.filterNot { it in registered } shouldBe emptyList()

        PROBE_SHAPES.associate { it.name to callProbe(restTemplate, port, it) } shouldBe
            PROBE_SHAPES.associate { it.name to BAD_REQUEST }
        Reached.count.get() shouldBe 0
    }

    /** 양성 대조 — 출하 핸들러의 모양(본문 `JsonNode`)은 가드를 지나 핸들러에 닿는다. */
    @Test
    fun `본문 JsonNode 핸들러는 가드를 지나 닿는다 — 양성 대조`() {
        callProbe(restTemplate, port, ProbeShape("OK", HttpMethod.POST, "/probe/ok", """{"account":9}""")) shouldBe OK
        Reached.count.get() shouldBe 1
    }

    private companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
    }
}

/** 출하 가드와 오류 처리기만 실은 조립 — 나머지는 Boot 자동 구성(MVC · Jackson)이 출하와 같은 길로 올린다. */
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
@Import(AccountBindingGuard::class, GlobalErrorHandler::class)
open class GuardProbeApplication

/** 위협 흉내 — 누군가 `String → AccountId` 변환기를 등록한 조립. 가드가 없으면 `@RequestParam` 이 이것으로 `AccountId` 를 짓는다. */
@Configuration
open class ProbeConversion : WebMvcConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(String::class.java, AccountId::class.java) { AccountId(it.toLong()) }
    }
}

/** 핸들러 도달 계수 — `AccountId` 가 지어졌다면 핸들러가 불려 1 이상이 된다. */
object Reached {
    val count = AtomicInteger()

    fun hit(value: Any?): String {
        count.incrementAndGet()
        return value.toString()
    }
}

class H1Inner {
    var account: AccountId? = null
}

class H1Outer {
    var inner: H1Inner? = null
}

class H2Form {
    var held: Any? = null

    fun setAccount(account: AccountId) {
        held = account
    }
}

open class H3Base {
    var account: AccountId? = null
}

class H3Form : H3Base()

abstract class H4Base<T> {
    @PostMapping("/probe/h4")
    fun h4(
        @RequestBody body: T,
    ): String = Reached.hit(body)
}

@RestController
class H4Controller : H4Base<AccountId>()

@RestController
class GuardProbeController {
    @PostMapping("/probe/h1")
    fun h1(
        @RequestBody body: H1Outer,
    ): String = Reached.hit(body.inner?.account)

    @PostMapping("/probe/h2")
    fun h2(
        @RequestBody body: H2Form,
    ): String = Reached.hit(body.held)

    @PostMapping("/probe/h3")
    fun h3(
        @RequestBody body: H3Form,
    ): String = Reached.hit(body.account)

    @GetMapping("/probe/g11")
    fun g11(account: AccountId): String = Reached.hit(account)

    @GetMapping("/probe/rp")
    fun rp(
        @RequestParam account: AccountId,
    ): String = Reached.hit(account)

    /** 본문을 `HttpEntity` 로 받으면 바인더가 만들어지지 않는다 — `@InitBinder` 층을 지나지 않고 Jackson 거부만 잰다. */
    @PostMapping("/probe/entity")
    fun entity(body: HttpEntity<H1Outer>): String = Reached.hit(body.body?.inner?.account)

    /** `Map` 의 키를 `AccountId` 로 — 키 역직렬화 축(검토 r6 V6-L1). 바인더를 만들지 않는 `HttpEntity` 로 Jackson 층만 잰다. */
    @PostMapping("/probe/mapkey")
    fun mapKey(body: HttpEntity<Map<AccountId, String>>): String = Reached.hit(body.body)

    @PostMapping("/probe/ok")
    fun ok(
        @RequestBody body: JsonNode,
    ): String = Reached.hit(body)
}

/** H5 — 모델 속성 메서드가 `AccountId` 를 받는다(이 controller 의 모든 요청 앞에서 돈다 — 그래서 따로 둔다). */
@RestController
class H5Controller {
    @ModelAttribute("vrAccount")
    fun vrAccount(account: AccountId): AccountId = account

    @GetMapping("/probe/h5")
    fun h5(
        @ModelAttribute("vrAccount") held: Any,
    ): String = Reached.hit(held)
}
