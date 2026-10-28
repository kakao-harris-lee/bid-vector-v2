package bidvector.app.http

import bidvector.workflow.evaluation.UnjudgedQualificationAxis
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.ProfileView
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 프로필 응답 변환 — **값이 wire 에 닿는가**를 잠근다(M7/7C④ 2b r2, v F7 · CR M-1).
 *
 * 앞 판은 `ProfileResponse.from` 이 use case 가 낸 [ProfileView.unjudgedAxes] 를 버리고 선언 표를 다시
 * 셌다. 오늘은 두 값이 같아 **거동 차이가 0** 이고, 그래서 그 결함을 어떤 test 도 갈라내지 못했다 —
 * 판정이 생긴 축을 use case 가 걸러도 응답은 전부를 실었을 것이다.
 *
 * 여기서는 선언 표와 **다른** 목록을 담은 view 를 넘겨 응답이 그 목록을 그대로 싣는지 본다. 선언 표를
 * 다시 세는 구현에서는 이 단언이 붉다.
 */
class ProfileResponseTest {
    @Test
    fun `응답의 판정 대기 축은 use case 가 낸 목록이다 — 선언 표를 다시 세지 않는다`() {
        val onlyOne = listOf(UnjudgedQualificationAxis.QUAL_10_PERFORMANCE)
        val view =
            ProfileView(
                qualification = null,
                details = BusinessDetails.EMPTY,
                capacities = emptyList(),
                unjudgedAxes = onlyOne,
            )

        ProfileResponse.from(view).unjudgedAxes shouldBe listOf("QUAL_10_PERFORMANCE")
    }

    /** 빈 목록도 **값**이다 — 「판정 대기 축이 없다」를 응답이 말할 수 있어야 한다. */
    @Test
    fun `판정 대기 축이 빈 목록이면 응답도 빈 배열이다`() {
        val view =
            ProfileView(
                qualification = null,
                details = BusinessDetails.EMPTY,
                capacities = emptyList(),
                unjudgedAxes = emptyList(),
            )

        ProfileResponse.from(view).unjudgedAxes shouldBe emptyList()
    }
}
