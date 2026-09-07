package bidvector.procurement

import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** ③ — 식별자 값 객체가 정규화 규칙을 소유한다(COL-05). */
class NoticeIdTest {
    @Test
    fun `NoticeNumber 는 trim 한 자릿수·구분자 원문을 그대로 보존한다`() {
        NoticeNumber.of("  20260101001-00  ") shouldBe NoticeNumber("20260101001-00")
    }

    // v2-defect 023 수정(3A 잔여 일괄 verifier r3 전) — 대소문자·내부 공백 표기 차이를
    // 같은 식별자로 정규화한다(팀리드 결정 「구분자 처리는 case 기대값대로」).
    @Test
    fun `NoticeNumber 는 대소문자·내부 공백 표기가 달라도 같은 정규형으로 모인다`() {
        val fromLowercase = NoticeNumber.of(" syn-ntc-2301 ")
        val fromUppercase = NoticeNumber.of("SYN-NTC-2301")
        val fromSpaceDelimited = NoticeNumber.of("SYN NTC 2301")

        fromLowercase shouldBe NoticeNumber("SYN-NTC-2301")
        fromUppercase shouldBe NoticeNumber("SYN-NTC-2301")
        fromSpaceDelimited shouldBe NoticeNumber("SYN-NTC-2301")
        (fromLowercase == fromUppercase) shouldBe true
        (fromLowercase == fromSpaceDelimited) shouldBe true
    }

    @Test
    fun `NoticeNumber 정규화는 멱등이다`() {
        val once = NoticeNumber.of("SYN NTC 2301")
        val twice = NoticeNumber.of(once.value)

        twice shouldBe once
    }

    @Test
    fun `NoticeNumber 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { NoticeNumber.of("   ") }
    }

    @Test
    fun `NoticeRound 는 제로패딩 3자리 문자열을 원문 그대로 보존한다 — R-QUAL-05`() {
        val round = NoticeRound.of("000")

        round.value shouldBe "000"
    }

    @Test
    fun `NoticeRound 는 원문 문자열 그대로 왕복한다 — 우회 (3) 등가성은 원문 문자열`() {
        NoticeRound.of("000") shouldBe NoticeRound.of("000")
        NoticeRound.of("001") shouldBe NoticeRound.of("001")
        (NoticeRound.of("000") == NoticeRound.of("001")) shouldBe false
    }

    @Test
    fun `NoticeRound 는 형식 위반(비3자리)을 생성 실패로 낸다 — 정규화가 아니라 거부`() {
        shouldThrow<IllegalArgumentException> { NoticeRound.of("0") }
        shouldThrow<IllegalArgumentException> { NoticeRound.of("1") }
        shouldThrow<IllegalArgumentException> { NoticeRound.of("0001") }
    }

    @Test
    fun `NoticeId 는 번호와 차수의 쌍이다 — 같은 번호라도 차수가 다르면 다른 식별자`() {
        val first = NoticeId(NoticeNumber.of("20260101001"), NoticeRound.of("000"))
        val renoticed = NoticeId(NoticeNumber.of("20260101001"), NoticeRound.of("001"))

        (first == renoticed) shouldBe false
    }
}
