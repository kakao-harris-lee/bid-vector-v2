package bidvector.app.http

import bidvector.app.architecture.ArchitecturePolicy
import bidvector.workflow.access.AccountId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.Ordered
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.method.ControllerAdviceBean
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper

/**
 * **가드는 컴포넌트 스캔으로 들어와 출하 매퍼에 등록된다**(M7/7B 계약 D-7B-7 ① — 「모듈 등록이 실제로 app 매퍼에 들어갔는지」).
 *
 * 이 조립(`HttpTestApplication`)은 출하 조립과 같은 방식으로 `bidvector.app.http` 를 스캔한다 — [AccountBindingGuard] 를 손으로
 * 등록하지 않는다. 그 컨텍스트가 MVC 에 쓰는 `JsonMapper` 가 `AccountId` 를 읽으려 하면 실패해야 한다(본문 어디서든). 같은
 * 매퍼가 평범한 값은 그대로 읽는다(양성 대조). 그리고 같은 스캔이 가드를 **controller advice** 로 올려 HTTP 표면 계약의 핸들러 전부에
 * 걸고(가장 앞 순서), 그 바인딩 거부(`@InitBinder`) · 변환 거부 응답(`@ExceptionHandler`)이 그 advice 에 있다 — 등록 하나를 지우면
 * 이 test 가 붉다(팀장 조건 (b): RED 가 「출하 조립에 가드가 들어갔다」를 증명한다).
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
class AccountBindingGuardScanTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var mapper: JsonMapper

    @Autowired
    private lateinit var guards: List<AccountBindingGuard>

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `스캔으로 들어온 가드가 HTTP 조립의 매퍼에서 AccountId 를 거부한다`() {
        guards.size shouldBe 1

        shouldThrow<MismatchedInputException> { mapper.readValue("9", AccountId::class.java) }
        mapper.readValue("9", Long::class.java) shouldBe 9L
    }

    @Test
    fun `스캔으로 들어온 가드는 표면 계약의 핸들러 전부에 가장 먼저 걸리는 advice 다`() {
        val advice =
            ControllerAdviceBean.findAnnotatedBeans(context).filter { it.beanType == AccountBindingGuard::class.java }
        advice.size shouldBe 1
        advice.single().order shouldBe Ordered.HIGHEST_PRECEDENCE

        val handlers =
            ArchitecturePolicy
                .load()
                .apiSurfaceHandlers
                .filter { it.startsWith("bidvector.app.") }
                .map { Class.forName(it) }
        handlers.filterNot { advice.single().isApplicableToBeanType(it) }.map { it.name } shouldBe emptyList()

        val methods = AccountBindingGuard::class.java.declaredMethods
        val binders = methods.filter { it.isAnnotationPresent(InitBinder::class.java) }.map { it.name }
        binders shouldBe listOf("refuseAccountBinding")
        val exceptionHandlers = methods.filter { it.isAnnotationPresent(ExceptionHandler::class.java) }.map { it.name }
        exceptionHandlers shouldBe listOf("refusedConversion")
    }

    /**
     * 검토 r6 R6-8 — 가드는 「그 밖의 형 불일치」에 `GlobalErrorHandler().fallback` 을 **손으로 지은 인스턴스**로 부른다. 그 인스턴스가
     * 빈과 같으려면 그 처리기가 상태 없는 무인자 class 여야 한다 — 주입 상태가 생기면(기본값 있는 매개변수면 컴파일도 된다) 이 test 가
     * 먼저 붉고, 가드는 그 처리기를 주입받아 쓰는 쪽으로 바꿔야 한다.
     */
    @Test
    fun `가드가 손으로 짓는 공유 오류 처리기는 상태 없는 무인자 class 다`() {
        GlobalErrorHandler::class.java.declaredConstructors.map { it.parameterCount } shouldBe listOf(0)
        GlobalErrorHandler::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name } shouldBe emptyList()
    }
}
