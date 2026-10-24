package bidvector.app.http

import bidvector.workflow.access.AccountId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper

/**
 * **가드는 컴포넌트 스캔으로 들어와 출하 매퍼에 등록된다**(M7/7B 계약 D-7B-7 ① — 「모듈 등록이 실제로 app 매퍼에 들어갔는지」).
 *
 * 이 조립(`HttpTestApplication`)은 출하 조립과 같은 방식으로 `bidvector.app.http` 를 스캔한다 — [AccountBindingGuard] 를 손으로
 * 등록하지 않는다. 그 컨텍스트가 MVC 에 쓰는 `JsonMapper` 가 `AccountId` 를 읽으려 하면 실패해야 한다(본문 어디서든). 같은
 * 매퍼가 평범한 값은 그대로 읽는다(양성 대조).
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

    @Test
    fun `스캔으로 들어온 가드가 HTTP 조립의 매퍼에서 AccountId 를 거부한다`() {
        guards.size shouldBe 1

        shouldThrow<MismatchedInputException> { mapper.readValue("9", AccountId::class.java) }
        mapper.readValue("9", Long::class.java) shouldBe 9L
    }
}
