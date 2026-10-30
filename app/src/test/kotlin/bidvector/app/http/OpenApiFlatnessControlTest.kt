package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 평탄 술어([isFlatPropertyDefinition]) **자신의 대조** — 계약 문서를 읽지 않는다.
 *
 * `OpenApiContractTest` 에서 갈라 나온 경계는 크기가 아니라 **대상**이다: 저쪽은 **문서와 응답**이
 * 평탄한지를 재고(그래서 Spring 컨텍스트를 띄운다), 이쪽은 **술어가 무엇을 통과시키는가**를
 * 값으로만 잰다. 두 축이 한 파일에 있으면 「문서가 바뀌었다」와 「술어가 바뀌었다」가 같은 실패로
 * 보이고, 저 파일은 500 줄 한도에 붙어 다음 라운드의 한 줄이 곧 게이트 위반이 된다(D-7C4C-6
 * 확장 직후 실측: 500/500).
 *
 * 술어 선언은 **옮기지 않았다** — 공유 게이트의 술어를 slice 중간에 다른 파일로 옮기면 변이·리뷰가
 * 좌표를 다시 찾아야 한다. 가시성만 `internal` 로 열었다(같은 패키지의 test 둘이 같은 술어를 본다).
 */
class OpenApiFlatnessControlTest {
    /**
     * **양성 대조 — D-7C4C-6 의 깊이 1 object 확장이 열지 않는 모양.**
     *
     * 확장의 위험은 「한 겹을 열면 두 겹도 열리는가」다. 깊이를 **세는 인자**를 두지 않고 호출
     * 그래프로 닫았으므로(object 층 술어가 스칼라 층 술어만 부르고 자기를 다시 부르지 않는다)
     * 깊이 2 를 표현할 함수가 없다 — 이 test 는 그 구성이 모양마다 실제로 그렇게 판정하는지 잰다.
     *
     * `$ref`(속성 · `items`) · 배열의 배열 · `type: object` 단독은 **`OpenApiContractTest` 의
     * 다섯 우회 test 가 이미 고정**한다 — 같은 단언을 두 벌 두지 않는다. 여기서는 확장이 새로
     * 건드리는 축만 센다.
     */
    @Test
    fun `깊이 1 object 확장은 깊이 2 · 자유 맵 · 합성 · 빈 properties · nullable · 미지 키를 거부한다`() {
        val scalar = mapOf("type" to "string")
        val depthOne = mapOf("type" to "object", "properties" to mapOf("a" to scalar))

        // 깊이 2 — 세 자리(속성 안 object · 아이템 안 object · 아이템 안 object 배열).
        isFlatPropertyDefinition(objectWithProperty(depthOne)) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(objectWithProperty(depthOne))) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(objectWithProperty(arrayWithItems(depthOne)))) shouldBe false
        // 자유 맵 — 아이템이 자유 맵인 꼴(속성 자리는 저쪽 test 가 고정한다).
        isFlatPropertyDefinition(arrayWithItems(depthOne + mapOf("additionalProperties" to scalar))) shouldBe false
        // 합성 — object 를 감싸 깊이를 숨기는 세 어휘(속성 · 아이템 둘 다).
        isFlatPropertyDefinition(mapOf("allOf" to listOf(depthOne))) shouldBe false
        isFlatPropertyDefinition(mapOf("oneOf" to listOf(scalar, depthOne))) shouldBe false
        isFlatPropertyDefinition(mapOf("anyOf" to listOf(scalar, depthOne))) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(mapOf("allOf" to listOf(depthOne)))) shouldBe false
        // 계약이 없는 object — 빈 `properties` 와 `properties` 자체가 없는 꼴.
        isFlatPropertyDefinition(mapOf("type" to "object", "properties" to emptyMap<String, Any?>())) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(mapOf("type" to "object"))) shouldBe false
        // 두 모양을 받는 속성 — nullable object. 키 허용 목록이 닫는다(스칼라의 nullable 은 그대로 열려 있다).
        isFlatPropertyDefinition(depthOne + mapOf("nullable" to true)) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(depthOne + mapOf("nullable" to true))) shouldBe false
        // 미지의 키 — 차단 목록을 늘리는 대신 키 허용 목록이 기본 거부로 잡는다.
        val pattern = mapOf("patternProperties" to mapOf("^x" to scalar))
        isFlatPropertyDefinition(depthOne + pattern) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(depthOne + pattern)) shouldBe false
    }

    /**
     * **음성 대조 — D-7C4C-1 이 여는 유일한 모양(`awaiting` 짝 배열)이 통과한다.**
     *
     * 이 단언이 붉은 동안은 체크리스트 operation 을 계약에 적을 수 없다(`OPEN-7C4-AWAITING-CHECKLIST`).
     * 스칼라 · 스칼라 배열이 그대로 통과하는 것은 `OpenApiContractTest` 가 고정한다.
     */
    @Test
    fun `awaiting 짝 배열과 깊이 1 object 속성은 통과한다 — 음성 대조`() {
        val itemName = mapOf("type" to "string", "enum" to listOf("TRADE_NAME", "LICENSES"))
        val pair =
            mapOf(
                "type" to "object",
                "required" to listOf("item", "prerequisite"),
                "properties" to mapOf("item" to itemName, "prerequisite" to itemName),
            )

        isFlatPropertyDefinition(arrayWithItems(pair)) shouldBe true
        // 서술 키 하나는 계약 문서가 실제로 쓰는 자리라 허용 목록에 든다.
        isFlatPropertyDefinition(arrayWithItems(pair + mapOf("description" to "항목과 선행 항목의 짝"))) shouldBe true
        // 자리로 가르지 않는다 — 같은 모양이 속성 자리에 와도 같은 판정이다(위치 술어는 종점이 없다).
        isFlatPropertyDefinition(pair) shouldBe true
    }
}

/** 대조용 조립 — 한 겹 object 로 감싼다(한 표현식으로 쓰면 줄 길이 한도를 넘는다). */
private fun objectWithProperty(property: Map<String, Any?>): Map<String, Any?> =
    mapOf("type" to "object", "properties" to mapOf("a" to property))

/** 대조용 조립 — 배열로 감싼다. */
private fun arrayWithItems(items: Map<String, Any?>): Map<String, Any?> = mapOf("type" to "array", "items" to items)
