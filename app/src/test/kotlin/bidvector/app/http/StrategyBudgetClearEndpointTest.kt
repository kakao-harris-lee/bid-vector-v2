package bidvector.app.http

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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

private const val EDIT_SESSIONS = "/api/strategy/edit-sessions"

/** 세션 id·상태 — 응답 본문에서 한 자리로 읽는다(이 test 가 JSON 해석기를 들이지 않는다). */
private val SESSION_ID = Regex("\"sessionId\"\\s*:\\s*\"([^\"]+)\"")
private val SESSION_STATE = Regex("\"state\"\\s*:\\s*\"([^\"]+)\"")

/** 값 하나를 보낸 결과 — 상태 코드 · 본문 · **그 뒤 세션의 상태**. */
private data class ValueOutcome(
    val status: Int,
    val body: String,
    val sessionState: String?,
)

/**
 * **금액대를 비우는 길**(M9 D-1·D-2) — 예산 한계 둘만 `amountWon: null` 을 「명시적 비움」으로
 * 받는다. 그 전에는 비우는 operation 이 계약에 없어(`amountWon` 생략 · `null` · `terms:[]` 모두
 * 400, `OPEN-7E2B-BUDGET-CLEAR`) 운영자가 한 번 세운 금액대를 지우는 길이 제품에 없었다.
 *
 * 비움 하나가 아니라 **그 비움의 귀결**을 잰다(D-2) — 「200 을 받았다」는 값이 wire 에 닿았다는
 * 뜻일 뿐이고, 비움이 뜻을 가지는 자리는 전략 조회 · 제안 게이트 · 온보딩 체크리스트 · 값
 * 불변식이다. 셋을 **한 번 비운 뒤 함께 읽는 것**이 요점이다: 세 읽기가 같은 전략을 보므로 하나만
 * 보면 「비웠는데 다른 읽기가 낡은 값으로 답한다」가 보이지 않는다.
 *
 * 비움이 **아닌 것**도 같은 자리에서 잠근다 — 칸 생략과 비움을 받지 않는 필드의 `null` 은 400 이다.
 * 그 둘이 비움으로 접히면 「값을 지어내지 않는다」(D-6A2b-1)가 뒤집힌다: 운영자가 칸을 빠뜨린
 * 요청이 조용히 설정을 지운다.
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
class StrategyBudgetClearEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var transaction: InMemoryStrategyEditTransaction

    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @Autowired
    private lateinit var store: InMemoryBusinessProfileStore

    @Autowired
    private lateinit var operatorProfile: InMemoryOperatorProfile

    @Autowired
    private lateinit var candidates: FakeSuggestionCandidates

    @Autowired
    private lateinit var licenseGate: FakeSuggestionLicenseGate

    private lateinit var session: SessionTestClient

    /**
     * 출발 전략은 **감시 조건이 없다** — 금액대를 세우면 그것이 유일한 감시 규칙이고, 비우면 다시
     * 「감시 미설정」이 된다. `freshStrategy()`(관심 업종 하나를 가진 fixture)로 출발하면 금액대를
     * 비워도 다른 규칙이 남아 제안 게이트가 움직이지 않는다 — 그러면 이 slice 의 귀결이 보이지 않는다.
     *
     * 후보 하나를 둔다: 후보가 0 이면 제안 상태가 `NO_OPEN_NOTICES` 로 먼저 접혀 감시 축이 가려진다.
     */
    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = emptyWatchStrategy()
        strategyRepository.loadFailure = null
        transaction.reset()
        access.reset()
        store.reset()
        operatorProfile.reset()
        candidates.notices = listOf(bidPriceNotice())
        licenseGate.verdictFor = { LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared) }
        session = SessionTestClient(restTemplate, ::url).signIn()
    }

    private fun emptyWatchStrategy(): OperatorStrategy {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val validation = validate(StrategyDraft(), StrategyRevision(1), policy)
        return (validation as StrategyValidation.Valid).strategy
    }

    /** 값 하나를 **편집 세션 전체**를 지나 적용한다 — 전략을 직접 쓰는 operation 이 계약에 없다. */
    private fun applyValue(
        field: String,
        slot: String,
        tag: String,
    ) {
        val sessionId = beginFor(field)
        session
            .post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"$tag-value","field":"$field",$slot}""")
            .statusCode
            .value() shouldBe 200
        val seen = strategyRepository.strategy.revision.value
        session
            .post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"$tag-confirm","seenRevision":$seen}""")
            .statusCode
            .value() shouldBe 200
    }

    private fun beginFor(field: String): String {
        val begin = session.post(EDIT_SESSIONS, """{"field":"$field"}""")
        begin.statusCode.value() shouldBe 201
        return requireNotNull(SESSION_ID.find(begin.body.orEmpty())?.groupValues?.get(1)) {
            "편집 세션 응답에 sessionId 가 없다: ${begin.body}"
        }
    }

    /**
     * 세션을 열어 값 하나를 보낸 뒤 **응답과 그 세션의 상태**를 본다 — 거절을 재는 자리다.
     *
     * 상태를 함께 읽는 이유: 값 단계의 거절에서 「아무것도 쓰지 않았다」를 **전략 값으로는 잴 수
     * 없다**. 쓰기는 `confirm` 에서만 일어나고 이 함수는 확인하지 않으므로, 전략 값 단언은 비움
     * 코드를 어떻게 망가뜨려도 통과한다. 거절이 실제로 남기는 흔적은 **세션이 전진했는가**다 —
     * 전진하면 뒤따르는 `confirm` 하나가 운영자가 요청하지 않은 비움을 적용한다(통제군 실측:
     * 검사 순서를 바꾸면 이 상태가 `WAITING_FOR_CONFIRMATION` 으로 간다).
     */
    private fun valueResponse(
        field: String,
        body: String,
    ): ValueOutcome {
        val sessionId = beginFor(field)
        val response = session.post("$EDIT_SESSIONS/$sessionId/value", body)
        val state = SESSION_STATE.find(session.get("$EDIT_SESSIONS/$sessionId").body.orEmpty())?.groupValues?.get(1)
        // 다음 `begin` 이 `SESSION_ALREADY_ACTIVE` 로 막히지 않게 세션을 닫는다.
        session.post("$EDIT_SESSIONS/$sessionId/cancel", """{"commandId":"close-$field"}""")
        return ValueOutcome(response.statusCode.value(), response.body.orEmpty(), state)
    }

    private fun strategyBody(): String = session.get("/api/strategy").body.orEmpty()

    private fun suggestionState(): String? = session.get("/api/suggestions").headers.getFirst(RESULT_STATE_HEADER)

    @Test
    fun `예산 한계 둘은 명시적 null 로 비워지고 제안 게이트가 감시 미설정으로 돌아간다`() {
        applyValue("MIN_BUDGET", """"amountWon":100000000""", "min-set")
        applyValue("MAX_BUDGET", """"amountWon":900000000""", "max-set")
        strategyBody() shouldContain "\"minBudgetWon\":100000000"
        // **통제군** — 금액대가 서 있는 동안은 「감시 미설정」이 아니다(비운 뒤의 단언이 「처음부터
        // 그랬다」와 구분된다).
        suggestionState() shouldBe "NO_MATCH"

        applyValue("MIN_BUDGET", """"amountWon":null""", "min-clear")
        applyValue("MAX_BUDGET", """"amountWon":null""", "max-clear")

        val cleared = strategyBody()
        cleared shouldContain "\"minBudgetWon\":null"
        cleared shouldContain "\"maxBudgetWon\":null"
        // 금액이 없으면 그 값에 딸린 축도 없다 — 비움이 「0 원 · 출처 있음」으로 남지 않는다.
        cleared shouldContain "\"minBudgetProvenance\":null"
        suggestionState() shouldBe "WATCH_NOT_CONFIGURED"
    }

    /**
     * 비움이 적용된다는 것은 `confirm` 이 200 이라는 뜻이다 — 값 불변식이
     * `BudgetLimitNotComparable` 을 냈으면 그 자리가 400 `STRATEGY_VALUE_INVALID` 였다(비움은
     * 「통화·과세·출처를 모르는 값」을 만드는 길이 아니다). 그 사실을 **개정이 실제로 올랐는가**로
     * 함께 센다 — 「조용히 아무것도 안 바꾸고 200」과 구분되는 자리다.
     */
    @Test
    fun `비움도 개정을 올린다 — 값 불변식을 어기지 않는다`() {
        applyValue("MIN_BUDGET", """"amountWon":100000000""", "min-set")
        val beforeClear = strategyRepository.strategy.revision.value

        applyValue("MIN_BUDGET", """"amountWon":null""", "min-clear")

        strategyRepository.strategy.revision.value shouldBe beforeClear + 1
        strategyRepository.strategy.watchRules.budget.min shouldBe null
    }

    @Test
    fun `비움은 온보딩 체크리스트의 금액대를 미완으로 되돌린다`() {
        applyValue("MIN_BUDGET", """"amountWon":100000000""", "min-set")
        session.get(AccountScopedRoutes.ONBOARDING_CHECKLIST).body.orEmpty() shouldContain
            "\"completed\":[\"BUDGET_RANGE\"]"

        applyValue("MIN_BUDGET", """"amountWon":null""", "min-clear")

        val checklist = session.get(AccountScopedRoutes.ONBOARDING_CHECKLIST).body.orEmpty()
        checklist shouldContain "\"completed\":[]"
        // 미완 목록 안에 있는지만 본다 — 그 목록의 **순서**는 커널 선언 순서이고 다른 test 의 축이다.
        checklist.substringAfter("\"pending\":[").substringBefore("]") shouldContain "\"BUDGET_RANGE\""
    }

    /**
     * **칸 생략은 비움이 아니다.** 비움은 운영자가 그 칸에 `null` 을 **적어 보낸 것**이고, 칸이
     * 없는 요청은 값을 말하지 않은 것이다 — 둘을 같은 뜻으로 접으면 오탈자 하나가 설정을 지운다.
     */
    @Test
    fun `값 칸 생략은 여전히 400 이다 — 비움은 명시적 null 뿐이다`() {
        val outcome = valueResponse("MIN_BUDGET", """{"commandId":"c1","field":"MIN_BUDGET"}""")

        outcome.status shouldBe 400
        outcome.body shouldContain ErrorCode.INVALID_REQUEST
        outcome.sessionState shouldBe "WAITING_FOR_VALUE"
    }

    /**
     * 비움을 받는 집합은 **예산 한계 둘**이다. 나머지 열하나는 초안에 「없음」 상태가 없다 — 어휘는
     * 빈 목록이 곧 없음이고, 임계·상한은 값이 반드시 있다. 그 칸의 `null` 은 400 이다.
     *
     * 넷은 **칸 네 종류의 대표**다(어휘 · 소수 · 건수 둘). 열하나 전수는 `withFieldValue` 층에서
     * `EditableFieldVocabularyGateTest` 가 태우는데 **두 층은 다른 것을 잰다**(파서는 400, 패치표는
     * 터짐) — HTTP 경계의 전수화는 후속으로 등재했다(cr L-4).
     */
    @Test
    fun `비움을 받지 않는 필드의 null 은 400 이다`() {
        val probes =
            listOf(
                "FOCUS_CATEGORY" to """"terms":null""",
                "BID_NOW_THRESHOLD" to """"number":null""",
                "CANDIDATE_LIMIT" to """"count":null""",
                "MAX_ACTIVE_BIDS" to """"count":null""",
            )

        val statuses =
            probes.map { (field, slot) ->
                valueResponse(field, """{"commandId":"c1","field":"$field",$slot}""").status
            }

        statuses shouldBe listOf(400, 400, 400, 400)
    }

    /**
     * **비움도 「그 칸 하나만」을 지킨다**(v M-1) — `amountWon: null` 에 **다른 값 칸**이나 **알려지지
     * 않은 키**가 함께 오면 400 이다.
     *
     * 이 단언이 없으면 그 약속은 **코드의 검사 순서로만** 지켜진다: 비움 판정을 칸 배정·알려진 키
     * 검사보다 앞으로 옮기는 리팩터가 `{"field":"MIN_BUDGET","amountWon":null,"terms":["x"]}` 를
     * 200(비움)으로 바꾸는데, 그 변이에서 이 slice 의 다른 test 와 `app.http` 전건이 전부 초록이었다
     * (검증 레인 실측). 그래서 순서가 아니라 **거동**을 잠근다.
     *
     * 「다른 칸에 담긴 `null`」(`terms: null` 류)은 칸 배정 진단이 먼저 받는다 — 그 자리는 위 test 다.
     *
     * **먼저 금액대를 세우고 잰다**: 거절이 「아무것도 쓰지 않았다」는 **지울 것이 있을 때만** 뜻이
     * 있다. 빈 전략에서 「지금도 비어 있다」를 단언한 앞 판은 거절이 실제로 비웠어도 통과했다
     * (v r2 L-b — 공허한 단언).
     */
    @Test
    fun `비움 요청에 다른 칸이나 미지 키가 함께 오면 400 이고 세운 값이 그대로다`() {
        applyValue("MIN_BUDGET", """"amountWon":100000000""", "min-set")
        val before = strategyRepository.strategy.watchRules.budget.min
        val beforeRevision = strategyRepository.strategy.revision
        before shouldNotBe null

        val probes =
            listOf(
                """{"commandId":"c1","field":"MIN_BUDGET","amountWon":null,"terms":["x"]}""",
                """{"commandId":"c1","field":"MIN_BUDGET","amountWon":null,"count":1}""",
                """{"commandId":"c1","field":"MIN_BUDGET","amountWon":null,"number":0.5}""",
                """{"commandId":"c1","field":"MIN_BUDGET","amountWon":null,"note":"왜"}""",
                """{"commandId":"c1","field":"MIN_BUDGET","amountWon":null,"amountwon":1}""",
            )

        val outcomes = probes.map { body -> valueResponse("MIN_BUDGET", body) }

        outcomes.map { it.status } shouldBe listOf(400, 400, 400, 400, 400)
        outcomes.forEach { outcome -> outcome.body shouldContain ErrorCode.INVALID_REQUEST }
        // **거절이 세션을 전진시키지 않았다** — 이것이 falsifiable 한 자리다(위 헬퍼 KDoc).
        // 전진했으면 뒤따르는 `confirm` 하나가 요청되지 않은 비움을 적용한다.
        outcomes.map { it.sessionState } shouldBe List(outcomes.size) { "WAITING_FOR_VALUE" }
        // 그래서 세운 값·개정·wire 값이 그대로다.
        strategyRepository.strategy.watchRules.budget.min shouldBe before
        strategyRepository.strategy.revision shouldBe beforeRevision
        strategyBody() shouldContain "\"minBudgetWon\":100000000"
    }
}
