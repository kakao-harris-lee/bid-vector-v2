package bidvector.adapters.snapshot

import bidvector.procurement.BusinessDivision
import bidvector.procurement.OPENING_PER_NOTICE_AXES
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C⑤-0 — **승격의 상세 축 집합이 추출의 기대 축 집합과 같은가**(D-7C5P-11).
 *
 * 승격은 「결말 없는 상세 축은 쓰지 않는다」로 걷기를 고르고, 그 판정이 추출의 완료 판정과 같다고
 * 말한다(`axisWalkOf` KDoc). 추출은 기대 축마다 쓸 수 있는 결말을 요구하고 못 받은 공고를
 * `incomplete_axis` 로 뺀다. **그 「같다」를 문장이 아니라 등식으로 둔다** — 두 집합이 갈리는 날
 * 승격은 추출이 거른 반쪽 원문을 canonical 로 올린다(verifier r1 F1 · r2 R2-1 이 둘 다 그 자리였다).
 *
 * 공사 축(A값)이 든 쪽으로 잰다 — 추출은 업무 대분류로 기대 축을 좁히고, 승격은 좁히지 않는다.
 * 그래서 등식은 **상한**(공사)에서 성립해야 하고, 공사 아닌 공고에서 추출의 기대 축이 더 작은 것은
 * 승격이 더 엄한 쪽이라 canonical 을 느슨하게 만들지 않는다.
 */
class OpeningPromotionAxisParityTest {
    @Test
    fun `승격의 상세 축 집합은 추출의 공사 기대 축 집합과 같다`() {
        expectedAxesFor(BusinessDivision.CONSTRUCTION.name) shouldBe OPENING_PER_NOTICE_AXES
    }

    @Test
    fun `공사가 아니면 추출의 기대 축이 더 작다 — 승격이 더 엄한 쪽이다`() {
        val serviceAxes = expectedAxesFor(BusinessDivision.SERVICE.name)

        (serviceAxes.size < OPENING_PER_NOTICE_AXES.size) shouldBe true
        OPENING_PER_NOTICE_AXES.containsAll(serviceAxes) shouldBe true
    }
}
