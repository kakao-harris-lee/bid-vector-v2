package bidvector.app.http

import bidvector.workflow.access.AccountId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C③ — 인증 판정의 산출물에서 **계정 주체**를 꺼내는 자리. 값만 쓰는 순수 test 다.
 *
 * 가장 중요한 단언은 **왕복**이다: 라벨을 짓는 자리(세션 인증기)와 읽는 자리([SessionAccount])
 * 가 갈리면 알림함이 조용히 「계정 주체 없음」으로 떨어져 **전부 401** 이 된다. 형식을 두 곳에
 * 적는 대신 왕복 등식으로 잠그면 7D 의 파일을 이 slice 가 고치지 않고도 그 결합이 닫힌다 —
 * 그쪽이 형식을 바꾸는 날 이 test 가 붉다.
 */
class SessionAccountTest {
    @Test
    fun `세션 인증기가 지은 주체 라벨을 그대로 되읽는다 — 왕복`() {
        listOf(1L, 2L, 42L, Long.MAX_VALUE).forEach { id ->
            val label = BrowserSessionAuthenticator.subjectOf(AccountId(id))

            SessionAccount.parse(label)?.value shouldBe id
        }
    }

    /**
     * 운영자 자격 헤더 경로의 주체는 계정이 아니다 — 그 라벨로는 계정이 나오지 않아야 한다.
     * 이것이 「알림함은 세션 전용」을 코드에서 성립시키는 자리다.
     */
    @Test
    fun `자격 헤더 경로와 미인증 라벨에서는 계정이 나오지 않는다`() {
        SessionAccount.parse("operator").shouldBeNull()
        SessionAccount.parse(RequestAuthenticationFilter.UNAUTHENTICATED_SUBJECT).shouldBeNull()
    }

    /**
     * `account.id` 는 IDENTITY 라 **양의 정수**다. `toLongOrNull` 은 `+1`·`-1` 을 받아들이므로
     * 조용히 넓어지지 않도록 형태를 못박는다(7C① 이 질의 정수 칸에서 겪은 것과 같은 축).
     */
    @Test
    fun `양의 십진 정수만 계정이 된다`() {
        listOf("account:0", "account:-1", "account:+1", "account: 1", "account:1 ", "account:1x", "account:")
            .forEach { label -> SessionAccount.parse(label).shouldBeNull() }
    }

    @Test
    fun `접두가 다르면 계정이 아니다 — 접두만 닮은 라벨도 거부한다`() {
        listOf("accounts:1", "account1", "ACCOUNT:1", "xaccount:1", "1", "")
            .forEach { label -> SessionAccount.parse(label).shouldBeNull() }
    }

    /**
     * **거부의 사유를 나누지 않는다** — 넷 전부 `null` 하나다(7D 가 인증 거부에 세운 관례와
     * 같은 방향). 호출자에게는 「이 요청에는 계정 주체가 없다」 하나뿐이다.
     */
    @Test
    fun `계정이 아닌 라벨은 전부 같은 값으로 떨어진다`() {
        val labels = listOf("operator", "unauthenticated", "account:0", "accounts:1")

        labels.map { SessionAccount.parse(it) }.distinct() shouldBe listOf(null)
    }
}
