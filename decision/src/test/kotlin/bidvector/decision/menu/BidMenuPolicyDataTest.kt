package bidvector.decision.menu

import bidvector.sharedkernel.MONEY_AXIS_SCALE_DIGITS
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * **출하 정본이 오늘 무엇인지**를 고정한다(추적표 술어 ② 「출하 정책 값에서 성립」의 이 slice 쪽 자리).
 *
 * 값이 상수로 코드에 되돌아오는 변이는 여기가 아니라 [BidMenuKernelTest] 가 잡는다 — 커널이
 * 정책을 읽지 않고 리터럴을 쓰면 `reading` 을 바꾼 test 들이 전부 틀어진다. 이 class 는
 * **정책 표 자신의 오늘 값**을 진술한다.
 */
class BidMenuPolicyDataTest {
    @Test
    fun `출하 정본은 하한율 읽기를 미정으로 둔다`() {
        val resolution = BID_MENU_POLICY.resolve(LocalDate.of(2026, 10, 21))

        val resolved = resolution.shouldBeInstanceOf<Resolution.Resolved<BidMenuPolicyData>>()
        resolved.value.floorRateReading shouldBe FloorRateReading.Undecided
    }

    @Test
    fun `출하 정본의 금액 반올림 자리수는 금액 축 정의값이다`() {
        val resolved =
            BID_MENU_POLICY
                .resolve(LocalDate.of(2026, 10, 21))
                .shouldBeInstanceOf<Resolution.Resolved<BidMenuPolicyData>>()

        resolved.value.amountRounding.scaleDigits shouldBe MONEY_AXIS_SCALE_DIGITS
    }

    /**
     * 출하 정본이 [FloorRateReading.Undecided] 라는 것은 **오늘 production 에서 밴드 붕괴·하한
     * binding 이 발화하지 않는다**는 뜻이다. 그 사실을 test 로 적어 둔다 — 추적표의 ⓒ 판정
     * 근거이고, 운영자 결정으로 읽기가 서면 이 test 가 먼저 깨져 갱신을 강제한다.
     */
    @Test
    fun `출하 정본에서는 모든 옵션의 binding 이 읽기 미정이다`() {
        val resolved =
            BID_MENU_POLICY
                .resolve(LocalDate.of(2026, 10, 21))
                .shouldBeInstanceOf<Resolution.Resolved<BidMenuPolicyData>>()

        val outcome = composeBidMenu(testInput(), resolved)

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.binding shouldBe BindingConstraint.FloorRateReadingUndecided
        offered.aggressive.binding shouldBe BindingConstraint.FloorRateReadingUndecided
    }
}
