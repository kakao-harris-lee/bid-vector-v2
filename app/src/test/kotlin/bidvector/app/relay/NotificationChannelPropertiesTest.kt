package bidvector.app.relay

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

private const val FAKE_CREDENTIAL = "811122233:FAKEpropsZZQ"
private const val FAKE_CHAT_ID = "-1009876543210"
private const val FAKE_SITE = "https://bidvector.example"

/**
 * **v H-1** — 설정 객체의 `toString` 이 원문을 내지 않는다.
 *
 * 왜 이 test 가 따로 있는가: 발송 축의 누출 방어는 `adapters` 의 값 타입(`TelegramBotCredential`
 * 등)이 지는데, **env 에서 들어온 원문 `String` 을 처음 쥐는 것은 이쪽**(`app` 의 설정 객체)이다.
 * 그 둘을 `data class` 로 두면 합성 `toString` 이 자격·chat id 를 한 줄로 찍는다 — Spring 의
 * 바인딩 실패 메시지·배선 로그·디버거가 모두 그 경로다. 그래서 둘을 `data class` 가 아니게 두고
 * `toString` 을 재정의했고, 이 test 가 그것을 잠근다.
 */
class NotificationChannelPropertiesTest {
    private fun filled() =
        NotificationChannelProperties(
            siteBaseUrl = FAKE_SITE,
            telegram = TelegramChannelProperties(botCredential = FAKE_CREDENTIAL, chatId = FAKE_CHAT_ID),
        )

    private fun assertNoRaw(
        where: String,
        observed: String,
    ) {
        withClue("$where 에 자격 원문이 있다") { observed shouldNotContain FAKE_CREDENTIAL }
        withClue("$where 에 chat id 원문이 있다") { observed shouldNotContain FAKE_CHAT_ID }
    }

    @Test
    fun `설정 객체의 toString 은 자격과 chat id 원문을 담지 않는다`() {
        val properties = filled()

        assertNoRaw("바깥 설정 toString", properties.toString())
        assertNoRaw("telegram 설정 toString", properties.telegram.toString())
    }

    /**
     * **중첩이 바깥으로 새지 않는다** — 바깥 `toString` 이 중첩 객체를 보간하므로, 중첩만 가리고
     * 바깥을 `data class` 로 두면 다시 샌다. 바깥 문자열 안에 중첩의 마스킹이 보이는지 함께 본다.
     */
    @Test
    fun `바깥 toString 은 중첩의 마스킹을 그대로 품는다`() {
        val rendered = filled().toString()

        rendered shouldContain "botCredential=<채워짐>"
        rendered shouldContain "chatId=<채워짐>"
    }

    /** 비밀이 아닌 값은 **보인다** — 그것이 오설정을 읽는 데 필요한 것이고 가릴 이유가 없다. */
    @Test
    fun `비밀이 아닌 값은 toString 에 보인다`() {
        val rendered = filled().telegram.toString()

        rendered shouldContain "routeKey=telegram-primary"
        rendered shouldContain "baseUrl=https://api.telegram.org"
    }

    /** 빈 칸과 채워진 칸이 **다른 문자열**이다 — 기동 실패를 읽을 때 그 구분이 필요하다. */
    @Test
    fun `빈 칸과 채워진 칸을 toString 이 가른다`() {
        NotificationChannelProperties().toString() shouldContain "siteBaseUrl=<비어 있음>"
        filled().toString() shouldContain "siteBaseUrl=<채워짐>"
    }

    /**
     * **마스킹된 칸이 원문도 길이도 나르지 않는다** — 길이는 비밀 값의 단서다.
     *
     * **줄 전체에서 「길이 문자열이 없다」를 재면 안 된다(cr L-4).** 앞 판이 그렇게 썼고, 그것은
     * `routeKey=…, baseUrl=…, connectTimeout=PT5S, responseTimeout=PT10S` 까지 담은 줄에
     * `"23"`·`"14"` 가 없다는 뜻이었다 — **fixture 길이와 timeout 기본값이 겹치지 않는 우연**에
     * 기대는 술어다. 기본 응답 timeout 이 14s 가 되면 마스킹과 무관한 이유로 붉어지고, 반대로
     * 구현이 **다른 숫자로** 길이를 새게 해도 잡지 못한다.
     *
     * 그래서 그 두 칸만 뽑아 **등식**으로 본다(부재 단언보다 등식이 세다 — 이 slice 가 `parse_mode`
     * 키 집합에서 쓴 바로 그 패턴이다). 칸 값이 `<채워짐>` 과 같다면 원문도 길이도 들어갈 자리가 없다.
     */
    @Test
    fun `마스킹된 칸은 원문도 길이도 담지 않는다`() {
        val rendered = filled().telegram.toString()

        fieldOf(rendered, "botCredential") shouldBe "<채워짐>"
        fieldOf(rendered, "chatId") shouldBe "<채워짐>"
        fieldOf(NotificationChannelProperties().telegram.toString(), "botCredential") shouldBe "<비어 있음>"
    }

    /**
     * `name=value` 한 칸을 뽑는다 — 다음 `,` 또는 닫는 괄호까지. 칸 자체가 없으면 **실패해야** 하므로
     * `null` 을 조용히 돌려주지 않는다(칸 이름이 바뀌면 통과하지 않는다).
     */
    private fun fieldOf(
        rendered: String,
        name: String,
    ): String {
        val match = Regex("""\b${Regex.escape(name)}=([^,)]*)""").find(rendered)
        withClue("'$name' 칸이 toString 에 없다") { (match != null) shouldBe true }
        return match!!.groupValues[1]
    }

    @Test
    fun `존재 여부 판정은 공백을 비어 있는 것으로 센다`() {
        NotificationChannelProperties(
            siteBaseUrl = " ",
            telegram = TelegramChannelProperties(botCredential = " ", chatId = FAKE_CHAT_ID),
        ).presence() shouldBe ChannelConfigPresence(botCredential = false, chatId = true, siteBaseUrl = false)
    }

    /** 자격 셋 중 하나라도 비면 설정 묶음이 `null` 이다 — 가짜 값을 만들지 않는다. */
    @Test
    fun `자격이 비면 설정 묶음이 null 이다`() {
        NotificationChannelProperties().channelSettings() shouldBe null
        NotificationChannelProperties(
            siteBaseUrl = FAKE_SITE,
            telegram = TelegramChannelProperties(botCredential = FAKE_CREDENTIAL),
        ).channelSettings() shouldBe null
    }

    @Test
    fun `설정이 다 차면 묶음이 선다`() {
        val settings = filled().channelSettings()

        (settings != null) shouldBe true
        settings?.telegram?.routeKey?.value shouldBe "telegram-primary"
    }
}
