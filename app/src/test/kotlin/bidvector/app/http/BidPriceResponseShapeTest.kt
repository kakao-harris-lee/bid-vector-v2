package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **응답 타입을 쪼개고 평탄하게 펴는 선택(`D-7C2-3` R7)의 전제**를 재는 자리.
 *
 * 쪼갠 이유는 `limit.type.members`(30)이고 평탄을 유지하는 이유는 계약의 평탄 술어다. 두 게이트가
 * 서로 다른 대상(Kotlin 타입 / OpenAPI 스키마)을 보기 때문에 둘 다 설 수 있는데, 그 성립이
 * **직렬화 거동**에 달려 있어 두 축을 나눠 잰다:
 *
 * - 여기(단위): **묶음끼리 키가 겹치지 않는다.** 겹치면 펴면서 한 축의 값이 조용히 덮인다.
 * - `OpenApiBidPriceContractTest`(출하 직렬화): 펴진 **최상위 키 집합이 계약과 같고** 묶음
 *   프로퍼티 이름이 본문에 나타나지 않는다. 그쪽이 HTTP 왕복이라 출하 ObjectMapper 를 실제로 지난다.
 *
 * **멤버 상한 자체는 여기서 재지 않는다** — `sizeGate` 가 main source 를 Kotlin PSI 로 이미 재고,
 * 그 수를 test 에 다시 적으면 같은 임계가 두 자리에 생긴다(`size-policy.properties` 머리말의 규율).
 */
class BidPriceResponseShapeTest {
    /**
     * **키 충돌 0** — 펴지는 묶음들이 같은 이름을 내면 하나가 조용히 덮인다. 접두사 규약
     * (`license…`·`baseAmount…`·`publishedFloorRate…`)이 그것을 막지만 규약은 지켜지지 않을 수
     * 있으므로 기계로 센다. 이름 목록을 손으로 복제하지 않는다(리플렉션으로 모은다) — 복제하면
     * 필드를 더해도 초록이다.
     */
    @Test
    fun `펴지는 묶음들 사이에 같은 키가 없다`() {
        val names = bidPriceResponseFieldNames()

        val duplicated = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        duplicated shouldBe emptySet()
    }

    /**
     * 묶음 프로퍼티 이름이 **wire 이름 집합에 들어오지 않는다** — 그 이름들은 펴지므로 JSON 에
     * 나타나지 않아야 하고, 수집기가 그것을 빼고 있다는 사실을 고정한다(수집기가 틀리면 위
     * 충돌 단언과 계약 키 등식이 둘 다 엉뚱한 집합을 본다).
     */
    @Test
    fun `묶음 프로퍼티 이름은 wire 이름이 아니다`() {
        bidPriceResponseFieldNames().intersect(BID_PRICE_GROUP_PROPERTY_NAMES) shouldBe emptySet()
    }
}
