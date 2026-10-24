package bidvector.guardprobe

import bidvector.app.http.AccountBindingGuard
import bidvector.app.http.GlobalErrorHandler
import bidvector.workflow.access.AccountId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import tools.jackson.databind.JsonNode
import java.util.concurrent.atomic.AtomicInteger

/**
 * **요청 바인딩은 `AccountId` 를 짓지 않는다 — 모양과 무관하게**(M7/7B 계약 D-7B-7 ① · 검토 r5 V5-N1).
 *
 * 검토 r5 는 두 겹 중첩 본문 DTO(H1) · setter 운반(H2) · `@ModelAttribute` 메서드(H5)에서 Spring · Jackson 이 `AccountId(9)` 를
 * 짓는 것을 실측했다(상위 클래스 필드 H3 · 제네릭 `T` H4 · 핸들러 매개변수 G11 도 같은 부류). 출하 가드
 * [AccountBindingGuard] 를 **실제 MVC · Jackson 자동 구성** 위에 올리고, 그 모양을 그대로 가진 핸들러를 실제 요청으로 부른다:
 * 전부 4xx 이고 **핸들러에 닿지 않는다**(도달 계수 0 — `AccountId` 가 지어졌다면 핸들러가 불렸다). 양성 대조: 본문
 * `JsonNode` 핸들러는 그대로 닿는다(가드가 모든 요청을 막아 공허하게 초록인 것이 아니다).
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

    private val restTemplate = TestRestTemplate()

    @BeforeEach
    fun reset() {
        Reached.count.set(0)
    }

    @Test
    fun `AccountId 를 싣는 모양은 전부 4xx 이고 핸들러에 닿지 않는다`() {
        val shapes =
            mapOf(
                "H1 두 겹 중첩 본문" to call(HttpMethod.POST, "/probe/h1", """{"inner":{"account":9}}"""),
                "H2 setter 운반" to call(HttpMethod.POST, "/probe/h2", """{"account":9}"""),
                "H3 상위 클래스 필드" to call(HttpMethod.POST, "/probe/h3", """{"account":9}"""),
                "H4 제네릭 T" to call(HttpMethod.POST, "/probe/h4", "9"),
                "H5 @ModelAttribute 메서드" to call(HttpMethod.GET, "/probe/h5?value=9", null),
                "G11 핸들러 매개변수" to call(HttpMethod.GET, "/probe/g11?value=9", null),
            )

        shapes.filterValues { it !in CLIENT_ERRORS } shouldBe emptyMap()
        Reached.count.get() shouldBe 0
    }

    /**
     * 단순 변환(`@RequestParam` `String → AccountId`) — 이 조립은 **위협을 흉내 내** `String → AccountId` 변환기를 등록한다
     * ([ProbeConversion]). 가드의 formatter 가 변환 서비스보다 먼저 묻혀 지어지지 않는다: 핸들러에 닿지 않고 2xx 가 아니다.
     * 응답은 500 이다(formatter 의 예외를 Spring 이 형 불일치로 감싸고, 공유 오류표가 그것을 500 으로 낸다 — 알려진 제한).
     */
    @Test
    fun `등록된 변환기가 있어도 요청 매개변수로 AccountId 를 짓지 못한다`() {
        val status = call(HttpMethod.GET, "/probe/rp?account=9", null)

        (status in SUCCESS) shouldBe false
        Reached.count.get() shouldBe 0
    }

    /** 양성 대조 — 출하 핸들러의 모양(본문 `JsonNode`)은 가드를 지나 핸들러에 닿는다. */
    @Test
    fun `본문 JsonNode 핸들러는 가드를 지나 닿는다 — 양성 대조`() {
        call(HttpMethod.POST, "/probe/ok", """{"account":9}""") shouldBe OK
        Reached.count.get() shouldBe 1
    }

    private fun call(
        method: HttpMethod,
        path: String,
        body: String?,
    ): Int {
        val headers = HttpHeaders().apply { if (body != null) contentType = MediaType.APPLICATION_JSON }
        return restTemplate
            .exchange("http://localhost:$port$path", method, HttpEntity(body, headers), String::class.java)
            .statusCode
            .value()
    }

    private companion object {
        const val OK = 200
        val CLIENT_ERRORS = 400..499
        val SUCCESS = 200..299
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
