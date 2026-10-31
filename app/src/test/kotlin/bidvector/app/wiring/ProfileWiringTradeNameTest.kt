package bidvector.app.wiring

import bidvector.app.http.InMemoryBusinessProfileStore
import bidvector.settlement.OperatorTradeName
import bidvector.workflow.access.AccountId
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.TradeName
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * **출하 빈의 몸통을 지나는 test**(M7/7C⑤-b r2 v L-1 · CR L-7).
 *
 * v M-1 의 고침은 사상을 이름 붙은 함수로 꺼내 출하와 test 조립이 **같은 함수**를 쓰게 한 것이고,
 * 그 함수는 wire test 넷이 잰다. 다만 `ProfileWiring` 의 **빈 메서드 자체**(위임 한 줄)는 어느
 * test 도 부르지 않아, 그 줄을 다른 것으로 바꿔치우는 변이가 지나갔다.
 *
 * 위험은 작다 — 한 줄 위임이다. 그래도 **싸게 닫힌다**: 빈을 직접 불러 그 산출이 프로필에 넣은
 * 값을 그대로 따르는지 본다. Spring 컨텍스트를 띄우지 않는다(조립 객체 하나와 이중체 하나뿐).
 *
 * 여기서 잠기는 것은 **대사 사유를 가르는 줄**이다 — 커널이 운영자 축을 먼저 보므로, 상호가
 * 미설정이면 `OPERATOR_NAME_NOT_DECLARED` 가 나가고 설정되면 그 축을 지나 결과 축 사유로 간다.
 */
class ProfileWiringTradeNameTest {
    private val account = AccountId(7L)
    private val at = Instant.parse("2026-10-31T00:00:00Z")

    private fun port(tradeName: TradeName?) =
        ProfileWiring().operatorTradeNamePort(
            InMemoryBusinessProfileStore().apply {
                replaceDetails(account, BusinessDetails(tradeName, null, NaraReadiness.Unanswered), at)
            },
        )

    @Test
    fun `상호가 설정돼 있으면 원문 그대로 낸다 — 정규화는 커널이 한 번만 한다`() {
        port(TradeName("  (주)가상해양기술  ")).tradeNameOf(account) shouldBe
            OperatorTradeName.Declared("  (주)가상해양기술  ")
    }

    @Test
    fun `상호가 미설정이면 「없음」이 값이다 — null 이 아니다`() {
        port(null).tradeNameOf(account) shouldBe OperatorTradeName.NotDeclared
    }

    /** 다른 계정의 상호를 내지 않는다 — 포트가 주체를 받는다는 사실이 빈을 지나서도 산다. */
    @Test
    fun `다른 계정에는 그 계정의 답을 낸다`() {
        port(TradeName("(주)가상해양기술")).tradeNameOf(AccountId(8L)) shouldBe OperatorTradeName.NotDeclared
    }
}
