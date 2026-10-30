package bidvector.app.http

import bidvector.workflow.result.ReconciliationBlocker
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

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
     * 판정 불가 사유의 **어휘가 커널과 같다** — 계약이 사유를 열거하지는 않지만(enum 을 두면 네
     * 자리에 사본이 생긴다) 설명이 드는 토큰은 실재해야 한다. 오늘 production 의 답인
     * `WINNER_NAME_NOT_PUBLISHED` 가 그 하나다.
     */
    @Test
    fun `설명이 드는 판정 불가 토큰이 커널 어휘에 실재한다`() {
        val names = ReconciliationBlocker.entries.map { it.name }.toSet()
        names shouldBe
            setOf(
                "NOTICE_ABSENT",
                "NOT_OPENED_YET",
                "NOT_COLLECTED",
                "OBSERVED_NOT_PROMOTED",
                "PROGRESS_UNRECOGNIZED",
                "PROGRESS_ABSENT",
                "OPERATOR_NAME_NOT_DECLARED",
                "OPERATOR_NAME_NORMALIZES_TO_EMPTY",
                "WINNER_NAME_NOT_PUBLISHED",
                "WINNER_NAME_NORMALIZES_TO_EMPTY",
            )
    }

    /** 읽기 전용 — 이 경로에 GET 말고 다른 메서드가 선언돼 있지 않다. */
    @Test
    fun `이 경로에 쓰기 메서드가 없다`() {
        val paths = spec["paths"] as Map<String, Any?>
        (paths[BID_RESULTS_PATH] as Map<String, Any?>).keys shouldBe setOf("get")
    }
}
