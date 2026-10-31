package bidvector.app.http

import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidStateTag
import bidvector.bidding.PriceMenuRef
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Rate
import bidvector.workflow.result.AwardGap
import bidvector.workflow.result.AwardRateAbsence
import bidvector.workflow.result.AwardRateState
import bidvector.workflow.result.OutcomeAgreement
import bidvector.workflow.result.ReconciliationBlocker
import bidvector.workflow.result.ReconciliationVerdict
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.math.BigDecimal
import org.junit.jupiter.api.Test

/** operation 의 좌표 — **구현 상수에서 받는다**(리터럴을 쓰면 등식을 세우지 않고 말만 한다). */
private val BID_RESULTS_PATH = AccountScopedRoutes.BID_RESULTS

/**
 * 내 입찰 결과 operation 의 **계약 대조**(M7/7C⑤-b) — 키 집합 · 보안 선언 · 상태 코드 · 상호
 * 칸의 부재.
 *
 * `OpenApiContractTest` 가 계약 문서 **전체**의 성질(평탄 · 중복 키)을 재고, 이 class 는 이
 * operation 하나를 잰다(`OpenApiOnboardingChecklistContractTest` 선례).
 *
 * 키 집합은 Kotlin DTO 의 필드와 맞댄다 — DTO 쪽이 더 엄격하다(한 번의 응답이 드러내지 않는
 * 여분 필드도 잡는다). Spring 컨텍스트를 띄우지 않는다: 여기서 보는 것은 문서와 타입뿐이고,
 * 실제 왕복은 `MyBidResultEndpointTest` 가 잰다.
 */
@Suppress("UNCHECKED_CAST")
class OpenApiMyBidResultContractTest {
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    /** `companion object` 가 만드는 정적 필드를 걷는다 — 걷지 않으면 「문서에 Companion 키가 없다」로 붉는다. */
    private fun declaredFieldNames(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

    private fun operation(): Map<String, Any?> {
        val paths = spec["paths"] as Map<String, Any?>
        val path = paths[BID_RESULTS_PATH] as? Map<String, Any?>
        return requireNotNull(path) { "$BID_RESULTS_PATH operation 이 계약에 없다" }["get"] as Map<String, Any?>
    }

    private fun resultsSchema(): Map<String, Any?> =
        ((spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>)["MyBidResults"]
            as Map<String, Any?>

    private fun propertiesOf(schema: Map<String, Any?>): Map<String, Any?> = schema["properties"] as Map<String, Any?>

    private fun caseItemSchema(): Map<String, Any?> =
        (propertiesOf(resultsSchema())["cases"] as Map<String, Any?>)["items"] as Map<String, Any?>

    private fun aggregateSchema(): Map<String, Any?> = propertiesOf(resultsSchema())["aggregate"] as Map<String, Any?>

    private fun enumOf(
        properties: Map<String, Any?>,
        key: String,
    ): Set<String> {
        val definition = requireNotNull(properties[key] as? Map<String, Any?>) { "계약에 $key 가 없다" }
        val values = requireNotNull(definition["enum"] as? List<String>) { "$key 가 enum 이 아니다" }
        return values.toSet()
    }

    /** 스칼라 **배열** 칸의 어휘는 `items` 가 든다. */
    private fun itemEnumOf(
        properties: Map<String, Any?>,
        key: String,
    ): Set<String> {
        val definition = requireNotNull(properties[key] as? Map<String, Any?>) { "계약에 $key 가 없다" }
        val items = requireNotNull(definition["items"] as? Map<String, Any?>) { "$key 가 배열이 아니다" }
        val values = requireNotNull(items["enum"] as? List<String>) { "$key 의 아이템이 enum 이 아니다" }
        return values.toSet()
    }

    private fun openingResultSchema(): Map<String, Any?> =
        ((spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>)["OpeningResultView"]
            as Map<String, Any?>

    @Test
    fun `최상위 키가 Kotlin DTO 와 같고 전부 required 다`() {
        val keys = declaredFieldNames(MyBidResultsResponse::class.java)
        keys shouldBe setOf("cases", "aggregate")
        propertiesOf(resultsSchema()).keys shouldBe keys
        (resultsSchema()["required"] as List<String>).toSet() shouldBe keys
    }

    /** 건 하나의 키 — **23 칸이 문서와 타입에서 같다**. 빠진 칸도 여분 칸도 붉는다. */
    @Test
    fun `건 키 집합이 Kotlin DTO 와 완전히 일치하고 전부 required 다`() {
        val keys = declaredFieldNames(MyBidCaseResponse::class.java)
        propertiesOf(caseItemSchema()).keys shouldBe keys
        (caseItemSchema()["required"] as List<String>).toSet() shouldBe keys
    }

    @Test
    fun `집계 키 집합이 Kotlin DTO 와 완전히 일치하고 전부 required 다`() {
        val keys = declaredFieldNames(MyBidAggregateResponse::class.java)
        propertiesOf(aggregateSchema()).keys shouldBe keys
        (aggregateSchema()["required"] as List<String>).toSet() shouldBe keys
    }

    /**
     * **다른 업체 식별 칸이 없다**(D-7C5B-5) — 칸 **이름**으로 잰다. 값 축의 단언은
     * `MyBidResultEndpointTest` 가 표지 글자로 따로 잰다(이름을 바꿔 숨기는 길을 그쪽이 막는다).
     */
    @Test
    fun `응답 계약에 다른 업체 상호와 사업자번호 칸이 없다`() {
        val forbidden = listOf("company", "bidder", "bizno", "businessNumber", "ceo", "representative", "winnerName")
        val allKeys = propertiesOf(caseItemSchema()).keys + propertiesOf(aggregateSchema()).keys
        allKeys.forEach { key ->
            withClue("업체 식별로 읽히는 칸이 생겼다: $key") {
                forbidden.none { key.contains(it, ignoreCase = true) } shouldBe true
            }
        }
    }

    /** 메모·증빙 참조 칸도 없다 — 화면 E 요구표 밖이고 포트 값에 자리가 없다. */
    @Test
    fun `응답 계약에 메모와 증빙 참조 칸이 없다`() {
        val forbidden = listOf("memo", "evidence", "drawNumber", "skipReason")
        val allKeys = propertiesOf(caseItemSchema()).keys + propertiesOf(aggregateSchema()).keys
        allKeys.forEach { key ->
            withClue("자유 텍스트·증빙 칸이 생겼다: $key") {
                forbidden.none { key.contains(it, ignoreCase = true) } shouldBe true
            }
        }
    }

    /** 계정 경로 전용이라 세션 보안이 선언돼 있고, 401·406·500 갈래가 문서에 있다. */
    @Test
    fun `세션 보안과 상태 코드 넷을 선언한다`() {
        val security = operation()["security"] as List<Map<String, Any?>>
        security.single().keys shouldBe setOf("browserSession")
        (operation()["responses"] as Map<String, Any?>).keys.sorted() shouldContainExactly
            listOf("200", "401", "406", "500")
    }

    /**
     * **토큰 칸 아홉이 enum 이고 그 집합이 응답을 만드는 함수의 산출과 같다**(r2 v M-1 · CR L-1).
     *
     * 앞 판은 넷을 **test 안에 손으로 쓴 집합**과 맞댔다 — 생산자의 글자를 바꾸면 spec 도 test 도
     * 그대로인 채 wire 만 달라졌다(변이 F 가 244 test 를 지나갔다). 지금은 봉인 계층의 값을
     * **전수로 만들어** 토큰 함수에 통과시키고 그 산출을 계약과 맞댄다 — 소진 `when` 이 입력
     * 전수를 지키고, 글자를 바꾸면 이 등식이 먼저 깨진다.
     *
     * 선례: `OpenApiOpeningResultContractTest` 의 `implementationStates()`.
     */
    @Test
    fun `토큰 칸 아홉이 enum 이고 집합이 응답 생산자의 산출과 같다`() {
        val case = propertiesOf(caseItemSchema())
        val aggregate = propertiesOf(aggregateSchema())

        enumOf(case, "recordedState") shouldBe BidStateTag.entries.map { it.name }.toSet()
        enumOf(case, "reconciliationBlocker") shouldBe ReconciliationBlocker.entries.map { it.name }.toSet()
        itemEnumOf(aggregate, "reconciliationBlockers") shouldBe ReconciliationBlocker.entries.map { it.name }.toSet()
        enumOf(case, "outcomeAgreement") shouldBe OutcomeAgreement.entries.map { it.name }.toSet()

        enumOf(case, "reconciledOutcome") shouldBe allVerdicts().map(::verdictTokenOf).toSet()
        enumOf(case, "gapState") shouldBe allGaps().map(::gapStateTokenOf).toSet()
        enumOf(case, "submittedAmountOrigin") shouldBe allOrigins().map(::originTokenOf).toSet()
        val rateStates = allRateStates().map(::rateStateTokenOf).toSet()
        enumOf(aggregate, "recordedRateState") shouldBe rateStates
        enumOf(aggregate, "reconciledRateState") shouldBe rateStates
        enumOf(case, "openingState") shouldBe enumOf(propertiesOf(openingResultSchema()), "state")
    }

    /** 봉인 계층의 값 **전수** — 갈래가 늘면 소진 `when` 이 먼저 컴파일을 막는다. */
    private fun allVerdicts(): List<ReconciliationVerdict> =
        listOf(
            ReconciliationVerdict.Won,
            ReconciliationVerdict.Lost,
            ReconciliationVerdict.FailedBid,
            ReconciliationVerdict.Rebid,
        ) + ReconciliationBlocker.entries.map(ReconciliationVerdict::Undecidable)

    private fun allGaps(): List<AwardGap> = listOf(AwardGap.AwardAbsent, AwardGap.Computed(0L, Currency.KRW))

    private fun allOrigins(): List<BidAmountOrigin> =
        listOf(BidAmountOrigin.OperatorEntered, BidAmountOrigin.PriceMenu(PriceMenuRef("menu-1")))

    private fun allRateStates(): List<AwardRateState> =
        listOf(AwardRateState.Measured(Rate.ofFraction(BigDecimal.ONE))) +
            AwardRateAbsence.entries.map(AwardRateState::Absent)

    /** 판정 불가 사유는 **열**이다 — 커널 어휘의 수를 계약 쪽에서도 센다(D-7C5B-11). */
    @Test
    fun `판정 불가 사유가 열이다`() {
        ReconciliationBlocker.entries.size shouldBe 10
        enumOf(propertiesOf(caseItemSchema()), "reconciliationBlocker").size shouldBe 10
    }

    /** 읽기 전용 — 이 경로에 GET 말고 다른 메서드가 선언돼 있지 않다. */
    @Test
    fun `이 경로에 쓰기 메서드가 없다`() {
        val paths = spec["paths"] as Map<String, Any?>
        (paths[BID_RESULTS_PATH] as Map<String, Any?>).keys shouldBe setOf("get")
    }
}
