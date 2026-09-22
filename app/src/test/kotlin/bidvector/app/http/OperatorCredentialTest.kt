package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * [OperatorCredential]의 (2b) 값 획득 축 실측(D-6A1-43) — 이 타입이 새 public 표면이므로
 * 밖에 무엇을 허락하는지를 코드가 아니라 **test로** 잠근다. 세 가지를 잰다: ① `matches`가
 * 기능적으로 여전히 옳은가(감싸는 것만으로 인증이 깨지지 않는가) ② `toString`이 원문을
 * 흘리지 않는가(로그에 실수로 찍혀도 안전한가) ③ `equals`/`==`가 **의미 없는가**(내용이
 * 같아도 항상 거짓 — 빠른 비교로 오용될 수 없다).
 */
class OperatorCredentialTest {
    @Test
    fun `같은 원문을 감싼 두 인스턴스는 matches 로 참이다`() {
        val a = OperatorCredential.wrap("operator-secret-value")
        val b = OperatorCredential.wrap("operator-secret-value")

        a.matches(b) shouldBe true
    }

    @Test
    fun `다른 원문을 감싼 두 인스턴스는 matches 로 거짓이다`() {
        val a = OperatorCredential.wrap("operator-secret-value")
        val b = OperatorCredential.wrap("different-value")

        a.matches(b) shouldBe false
    }

    @Test
    fun `길이가 다른 원문도 matches 로 거짓이다`() {
        val a = OperatorCredential.wrap("short")
        val b = OperatorCredential.wrap("a-much-longer-value-than-short")

        a.matches(b) shouldBe false
    }

    /**
     * (2b) 값 획득 축 — `toString()`이 원문을 흘리지 않는다. 재정의하지 않아 `Any.toString()`
     * (`클래스명@해시코드`)을 그대로 쓴다 — 값을 포함하는 형태라면 이 test가 실측으로 잡는다.
     */
    @Test
    fun `toString 은 원문 값을 담지 않는다`() {
        val secret = "operator-secret-value-that-must-not-leak"
        val credential = OperatorCredential.wrap(secret)

        credential.toString().contains(secret) shouldBe false
        credential.toString().contains("bidvector.app.http.OperatorCredential") shouldBe true
    }

    /**
     * (2b) 값 획득 축 — `equals`/`==`는 **빠른 비교를 제공하지 않는다**. 내용이 같은 두
     * [wrap] 결과도 참조가 다르면 항상 거짓이다 — `matches`를 `==`로 잘못 바꾸는 시도는
     * 시간이 새는 방향이 아니라 **인증이 항상 거부되는 방향**으로만 실패한다(가장 무거운
     * 우회 형태를 타입 차원에서 기능 고장으로 강등한다).
     */
    @Test
    fun `equals 는 내용이 같아도 항상 거짓이다 — 빠른 비교로 오용될 수 없다`() {
        val a = OperatorCredential.wrap("operator-secret-value")
        val b = OperatorCredential.wrap("operator-secret-value")

        (a == b) shouldBe false
        a.equals(b) shouldBe false
        (a == a) shouldBe true
    }
}
