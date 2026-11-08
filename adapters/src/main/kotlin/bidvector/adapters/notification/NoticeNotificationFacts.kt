package bidvector.adapters.notification

import java.math.BigDecimal
import java.time.Instant

/**
 * 알림 본문이 읽는 **공고 표시 사실**(D-6) — 화면 B·C 가 쓰는 칸과 같은 축이다. 전부 nullable 인
 * 이유: 공고 표의 그 열들이 nullable 이고(V1·V7·V14) 「없다」가 정상 형태다. 없는 칸을 `0`·`미정`
 * 으로 메우지 않는다.
 *
 * 판정 축(`baseAmount` 의 provenance·`floorRate`·면허 요건)은 **싣지 않는다** — 발송 축은 판정을
 * 다시 조립하지 않고 표시 칸만 읽는다(그래서 `procurement` 의 값 타입도 쓰지 않는다).
 */
data class NoticeNotificationFacts(
    val title: String?,
    val demandAgencyName: String?,
    val deadlineAt: Instant?,
    val baseAmountWon: BigDecimal?,
    val baseAmountCurrency: String?,
)

/**
 * `ContentRef` 가 나르는 공고 식별자 — 공고번호와 차수의 쌍이다(`NoticeId` 와 같은 축이지만 이
 * 패키지 자신의 값이다: 발송 축은 `procurement` 를 보지 않는다).
 *
 * 차수는 **세 자리 숫자**다(`notice` 표의 `notice_round` CHECK). 공고번호에도 하이픈이 올 수 있으므로
 * 가르는 자리는 **마지막** 하이픈이다.
 */
data class NoticeReference(
    val number: String,
    val round: String,
) {
    init {
        require(number.isNotBlank()) { "공고번호는 빈 문자열일 수 없다" }
        require(ROUND_SHAPE.matches(round)) { "차수는 세 자리 숫자여야 한다: $round" }
    }

    /** `ContentRef` 와 같은 표기(`<번호>-<차수>`) — 본문이 공고번호 칸에 쓴다. */
    fun display(): String = "$number-$round"

    companion object {
        private val ROUND_SHAPE = Regex("^[0-9]{3}$")

        /**
         * 모양이 아니면 `null` 이다 — **던지지 않는다**(D-6 결정: 렌더 실패가 run 을 멈추지 않는다).
         * 호출부가 그 `null` 을 「해독하지 못했다」 본문으로 옮긴다.
         */
        fun parse(raw: String): NoticeReference? {
            val separator = raw.lastIndexOf('-')
            val number = if (separator > 0) raw.substring(0, separator) else ""
            val round = if (separator > 0) raw.substring(separator + 1) else ""
            return if (number.isNotBlank() && ROUND_SHAPE.matches(round)) {
                NoticeReference(number, round)
            } else {
                null
            }
        }
    }
}

/**
 * 공고 표시 사실을 읽는 port — **어댑터 안의 추상화이고 domain port 가 아니다**(`LlmClient` 와 같은
 * 자리). 도메인의 [bidvector.workflow.notification.ContentRenderer] 는 「내용을 만든다」까지만 알고,
 * 그 내용이 어디서 오는지는 이 층의 관심사다.
 *
 * 행이 없으면 `null` 이다 — 「행이 없다」와 「행은 있고 칸이 비었다」는 다른 사실이고(후자는
 * [NoticeNotificationFacts] 의 `null` 칸이다) 본문이 그 둘을 다른 문장으로 가른다.
 */
interface NoticeNotificationFactsPort {
    fun factsFor(reference: NoticeReference): NoticeNotificationFacts?
}
