package bidvector.app.http

import bidvector.workflow.profile.OnboardingItem
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/** 체크리스트 operation 의 좌표 — 문서와 구현이 같은 문자열을 쓴다. */
private const val CHECKLIST_PATH = "/api/onboarding-checklist"

/**
 * 체크리스트 operation 의 **계약 대조**(M7/7C④-3) — 키 집합 · 상태 코드 · 보안 선언 · 어휘 등식.
 *
 * `OpenApiContractTest` 와 가른 경계는 **대상**이다(`OpenApiDryRunContractTest` 선례): 저쪽은 계약
 * 문서 전체의 성질(평탄 · 중복 키)을 재고 이쪽은 이 operation 하나를 잰다.
 *
 * 키 집합은 **Kotlin DTO 의 필드**와 맞댄다(`OpenApiNotificationInboxContractTest` 와 같은 방식) —
 * 계정 전용 경로라 200 을 받으려면 세션이 필요한데, 그 왕복은 `OnboardingChecklistEndpointTest` 가
 * 이미 wire 에서 잰다. 여기서 필요한 것은 **문서와 타입이 같은 키를 말하는가**이고, DTO 쪽이 더
 * 엄격하다(한 번의 응답이 드러내지 않는 여분 필드도 잡는다). `kotlin-reflect` 를 들이지 않고 순수
 * Java reflection 을 쓰며, `companion object` 가 만드는 **정적 필드**를 걷어낸다(걷지 않으면 「문서에
 * `Companion` 키가 없다」는 엉뚱한 이유로 붉는다).
 *
 * **어휘 등식이 이 파일의 핵심**이다. 평탄 술어가 `$ref` 를 거부하므로(그것이 깊이를 숨기는 길이다)
 * 항목 enum 이 문서에 **네 번 인라인**된다 — `completed` · `pending` · `awaiting[].item` ·
 * `awaiting[].prerequisite`. 네 벌이 갈리지 않는 근거는 손이 아니라 이 등식이다: 네 자리를 문서에서
 * **모아서** 세고(자리를 손으로 적으면 하나를 빠뜨린 채 초록이 된다) 각각이 Kotlin [OnboardingItem]
 * 과 **양방향** 일치해야 한다.
 *
 * **Spring 컨텍스트를 띄우지 않는다** — 여기서 보는 것은 문서와 타입뿐이다. 401 본문의 키 집합처럼
 * 실제 왕복이 필요한 단언은 컨텍스트를 띄우는 `OnboardingChecklistEndpointTest` 에 둔다(앞 판은 이
 * 파일에 두었다가 `@SpringBootTest` 없이 `url()` 이 포트 0 을 내 I/O 오류로 붉었다 — 실측).
 */
@Suppress("UNCHECKED_CAST")
class OpenApiOnboardingChecklistContractTest {
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    @Test
    fun `응답 키 집합이 Kotlin DTO 와 완전히 일치하고 전부 required 다`() {
        val checklistKeys = declaredFieldNames(OnboardingChecklistResponse::class.java)

        checklistKeys shouldBe setOf("completed", "pending", "awaiting")
        spec.propertyKeys("OnboardingChecklist") shouldBe checklistKeys
        // 빈 목록도 키 자체는 남는다 — 그래서 required 가 전수다.
        spec.requiredKeys("OnboardingChecklist") shouldBe checklistKeys
    }

    /**
     * 짝의 모양 — 두 칸이 **한 object 안**에 있고 둘 다 `required` 다. 선행을 응답의 다른 칸에 두면
     * 두 자리가 어긋나 선행 없는 대기가 표현된다(D-7C4C-1).
     */
    @Test
    fun `awaiting 아이템은 항목과 선행을 한 object 에 담고 둘 다 required 다`() {
        val pairKeys = declaredFieldNames(AwaitingItemResponse::class.java)
        val item = awaitingItemSchema()

        pairKeys shouldBe setOf("item", "prerequisite")
        (item["properties"] as Map<String, Any?>).keys shouldBe pairKeys
        (item["required"] as List<String>).toSet() shouldBe pairKeys
    }

    @Test
    fun `항목 어휘 네 자리가 Kotlin enum 과 양방향으로 같다`() {
        val expected = OnboardingItem.entries.map { it.name }
        val vocabularies = checklistVocabularies()

        vocabularies.keys shouldBe setOf("completed", "pending", "awaiting.item", "awaiting.prerequisite")
        vocabularies.forEach { (coordinate, values) ->
            withClue(coordinate) { values shouldBe expected }
        }
    }

    @Test
    fun `계약이 선언한 상태 코드가 셋이다 — 200 · 401 · 500`() {
        spec.responseStatusCodes(CHECKLIST_PATH, "get") shouldBe setOf("200", "401", "500")
    }

    /** 문서가 **세션만** 요구한다 — 계정 전용 뿌리이므로 자격 헤더 대안을 적지 않는다(D-7C4C-7). */
    @Test
    fun `보안 선언은 세션 하나뿐이다`() {
        val security = operation()["security"] as List<Map<String, Any?>>

        security.map { it.keys } shouldBe listOf(setOf("browserSession"))
    }

    @Test
    fun `operationId 가 계약에 있다 — 생성 타입의 이름 자리`() {
        operation()["operationId"] shouldBe "readOnboardingChecklist"
    }

    private fun declaredFieldNames(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

    private fun operation(): Map<String, Any?> {
        val paths = spec["paths"] as Map<String, Any?>
        val pathItem = paths[CHECKLIST_PATH] as Map<String, Any?>
        return pathItem["get"] as Map<String, Any?>
    }

    private fun checklistProperties(): Map<String, Any?> =
        spec.schema("OnboardingChecklist")["properties"] as Map<String, Any?>

    private fun awaitingItemSchema(): Map<String, Any?> {
        val awaiting = checklistProperties()["awaiting"] as Map<String, Any?>
        return awaiting["items"] as Map<String, Any?>
    }

    /** 항목 어휘가 적힌 자리 전부 — 좌표 → enum 목록. 자리를 손으로 적지 않고 문서에서 모은다. */
    private fun checklistVocabularies(): Map<String, List<String>> {
        val properties = checklistProperties()
        val scalarLists =
            listOf("completed", "pending").associateWith { name ->
                val items = (properties[name] as Map<String, Any?>)["items"] as Map<String, Any?>
                items["enum"] as List<String>
            }
        val pair = awaitingItemSchema()["properties"] as Map<String, Any?>
        val pairEntries =
            pair
                .mapValues { (_, definition) -> (definition as Map<String, Any?>)["enum"] as List<String> }
                .mapKeys { (key, _) -> "awaiting.$key" }
        return scalarLists + pairEntries
    }
}
