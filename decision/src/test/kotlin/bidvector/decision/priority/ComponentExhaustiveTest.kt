package bidvector.decision.priority

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * [Component] 어휘 고정(scope.md 위협 모델 (c) — 확률 축의 몰래 유입 차단). `when`
 * 소진은 컴파일이 잡지만(`PriorityInputs.factFor`) 「다섯이고 이 이름들이다」는 값으로
 * 재는 이 test 가 잰다 — 값을 늘리거나 이름을 바꾸면 이 test 가 값으로 드러낸다.
 */
class ComponentExhaustiveTest {
    @Test
    fun `Component 는 정확히 다섯이고 확률 축이 없다`() {
        Component.entries.toSet() shouldBe
            setOf(
                Component.Match,
                Component.Urgency,
                Component.Competitiveness,
                Component.BudgetCapture,
                Component.ExpectedMargin,
            )
    }

    @Test
    fun `PRIORITY_POLICY 의 weights 키는 Component 전 값과 정확히 같다(전사상)`() {
        TEST_PRIORITY_POLICY.weights.keys shouldBe Component.entries.toSet()
    }
}
