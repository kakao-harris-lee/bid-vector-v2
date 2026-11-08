package bidvector.adapters.notification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * **v N-1 = cr M-2** — base URL 축의 오분류를 **기동 시점에** 막는다.
 *
 * 왜 이것이 필요한가: 요청 URI 를 짓는 자리가 보간하는 값은 **둘**(base URL · 자격)이고, 앞 라운드는
 * 자격 축만 막았다. `^https?://[^\s@?#]+$` 같은 **문자열 술어**는 `java.net.URI` 가 거부하는 문자
 * (`| \ " { } ^ < > ` ` 와 16진 두 자리가 따르지 않는 `%`)를 통과시킨다. 그 주소를 받으면 기동은
 * 초록이고, **첫 발송에서** `URI.create` 가 던지고, 그 예외가 발송 경로의 포괄 `catch` 에 걸려
 * **`Unknown`(모호)** 으로 기록된다 — 집은 행이 전부 `ISOLATED` 로 탄다(at-most-once 라 재시도도 0).
 *
 * 그래서 술어를 **문자열이 아니라 URI 자신에게** 묻는다(구조로 닫는다). 정규식을 늘리는 쪽은 같은
 * 함정을 한 칸 뒤로 미루는 것뿐이다 — URI 가 받는지는 URI 만 안다.
 */
class TelegramSenderSettingsTest {
    /**
     * `URI` 왕복만으로는 **둘이 빠져나간다** — 그래서 host 와 포트 범위를 함께 본다.
     *
     * - `…:abc`: `URI` 가 **받아들이지만** authority 가 registry 기반이 되어 `host == null` 이다.
     * - `…:99999`: `URI` 는 받고 포트도 그 수를 그대로 낸다(16비트 검사가 없다).
     *
     * 둘 다 발송 때 요청 조립·연결에서 깨지므로 같은 `ISOLATED` 처분으로 흘러간다.
     */
    @Test
    fun `URI 가 거부하는 base URL 은 기동에서 거부된다`() {
        listOf(
            "https://api.telegram.org|x",
            "https://api.telegram.org/%zz",
            "https://[bad",
            """https://api.telegram.org\x""",
            """https://a"b""",
            "https://api.telegram.org/{a}",
            "https://api.telegram.org/a^b",
            "https://api.telegram.org:abc",
            "http://127.0.0.1:99999",
        ).forEach { malformed ->
            withClue("'$malformed' 는 기동에서 거부돼야 한다") {
                shouldThrow<IllegalArgumentException> { settingsWith(malformed) }
            }
        }
    }

    /**
     * **양성 대조** — 실제로 쓰는 모양은 통과한다. 이 칸이 없으면 「전부 거부하는」 술어도 위 test 를
     * 통과하고, 그 회귀는 **운영 기동 실패**로만 드러난다.
     */
    @Test
    fun `실제로 쓰는 base URL 은 통과한다 — 양성 대조`() {
        listOf(
            "https://api.telegram.org",
            "http://127.0.0.1:8080",
            "http://127.0.0.1",
            "https://api.telegram.org:443",
        ).forEach { valid ->
            withClue("'$valid' 는 통과해야 한다") { settingsWith(valid).baseUrl shouldBe valid }
        }
    }

    /**
     * **v N-6** — 거부 메시지는 원문도 **길이도** 담지 않는다. 설정 `toString` 의 KDoc 이 「길이도
     * 비밀 값의 단서다」라고 적는데 거부 메시지가 길이를 싣고 있었다 — 두 자리의 문면을 맞춘다.
     * base URL 은 비밀이 아니지만, 같은 메시지 관례를 쓰는 자격 쪽이 비밀이므로 관례를 하나로 둔다.
     */
    @Test
    fun `base URL 거부 메시지는 원문도 길이도 담지 않는다`() {
        // 경로 조각 이름을 **비밀값 스캔 어휘 밖**에서 고른다 — 첫 작성에서 고른 이름이 그 어휘에
        // 걸려 diff 축 판독에 거짓 양성을 하나 더했다(이 slice 가 자격 값 타입 이름에서 이미 겪은 일).
        val malformed = "https://api.telegram.org|opaque-path"

        val failure = shouldThrow<IllegalArgumentException> { settingsWith(malformed) }
        val message = failure.message.orEmpty()

        message shouldNotContain malformed
        message shouldNotContain "opaque-path"
        message shouldNotContain malformed.length.toString()
        message shouldNotContain "length"
    }

    /** 앞 판의 얕은 층도 그대로 선다 — `/` 로 끝나는 주소는 경로를 두 번 잇는다. */
    @Test
    fun `슬래시로 끝나는 base URL 은 여전히 거부된다`() {
        shouldThrow<IllegalArgumentException> { settingsWith("https://api.telegram.org/") }
    }

    private fun settingsWith(baseUrl: String) =
        TelegramSenderSettings(
            baseUrl = baseUrl,
            botCredential = TelegramBotCredential.of(FAKE_BOT_CREDENTIAL),
            routeKey = FAKE_ROUTE,
            chatId = TelegramChatId.of(FAKE_CHAT_ID),
            connectTimeout = Duration.ofSeconds(1),
            responseTimeout = Duration.ofSeconds(1),
        )
}
