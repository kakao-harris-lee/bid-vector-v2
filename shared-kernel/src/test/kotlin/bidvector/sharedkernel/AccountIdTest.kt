package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * [AccountId] — M15/15A 가 `bidvector.workflow.access` 에서 옮겨 온 **신원 값**.
 *
 * 왜 이 모듈에 test 가 필요한가: 공유 커널은 변이 래칫 아래에 있다. 옮겨 온 직후 이 타입의
 * 접근자는 **아무 test 도 밟지 않았고**(`NO_COVERAGE`), 「`value` 를 0 으로 바꾸는」 변이가
 * 살아남아 모듈 점수가 90/129(69%)로 래칫 70% 밑으로 떨어졌다. 아래 첫 단언이 그 변이를
 * 죽인다 — 0 이 아닌 값을 넣고 그 값이 그대로 나오는지 본다.
 *
 * 둘째 단언은 **값 비교를 제공하지 않는다**는 설계를 잠근다. 옮기기 전과 같은 선택이고
 * (`equals`/`hashCode` 미정의), 뒤에 이 자리를 보는 사람이 「`data class` 로 바꾸면 편하겠다」고
 * 할 때 그 변경이 **소리 없이** 지나가지 않게 한다 — 저장소의 여러 자리가 두 식별자를
 * [AccountId.value] 로 비교하고 있어, 비교 의미가 바뀌면 그 자리들의 뜻도 함께 바뀐다.
 */
class AccountIdTest {
    /**
     * 본문을 **블록**으로 둔다(모듈의 다른 property test 와 같은 꼴) — 식 본문
     * (`= runBlocking { … }`)으로 쓰면 JUnit 이 이 메서드를 수집에서 조용히 빼고, 그러면
     * 이 단언이 **한 번도 돌지 않은 채** 초록이다(실측: 수집 1/2, 변이는 `SURVIVED` 로 남았다).
     */
    @Test
    fun `발급된 값이 그대로 나온다`() {
        runBlocking {
            checkAll(Arb.long()) { raw ->
                AccountId(raw).value shouldBe raw
            }
        }
    }

    @Test
    fun `값 비교를 제공하지 않는다 — 같은 값이어도 다른 인스턴스다`() {
        val one = AccountId(7)
        val other = AccountId(7)

        one shouldNotBe other
        one shouldBe one
        one.value shouldBe other.value
    }
}
