package bidvector.adapters.notification

import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ContentRef
import bidvector.workflow.notification.ContentRenderer
import bidvector.workflow.notification.RenderedContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 공고 하나의 한국어 평문 알림(D-6). `parse_mode` 를 쓰지 않으므로 **이스케이프 규칙이 없다** —
 * 공고명에 어떤 문자가 와도 문면이 깨지거나 다른 뜻이 되지 않는다.
 *
 * **렌더 실패를 발송 결과 어휘로 옮기지 않는다**(설계 검토 D-6 결정). 갈래가 셋이고 각자 **다른
 * 모양**의 본문이다:
 *
 *  1. 공고 행이 있다 — 칸 다섯을 적는다. 빈 칸은 `값 없음` 이고 `0원`·`미정` 을 지어내지 않는다.
 *  2. 공고 행이 없다(보존 파기·미수집) — 금액·마감 칸 **자체를 쓰지 않고** 그 사실을 말한다.
 *     던지지 않는 이유: 던지면 relay run 이 멈춰 그 행과 **남은 행 전부**가 좌초한다. `Rejected` 로
 *     옮기지 않는 이유: 그 어휘는 「Telegram 이 확정 거부했다」는 뜻이고 그 명제가 거짓이 된다.
 *  3. 내용 참조를 해독하지 못했다 — 링크를 지어낼 수 없으므로 링크를 적지 않는다.
 *
 * 표기 규칙은 웹 화면과 같은 축이다(`web/src/display/noticeDisplay.ts`): 금액은 원 단위 콤마,
 * 통화가 원이 아니면 코드를 함께 적는다. 시각은 운영자의 달력(KST)이다.
 */
class NoticeMessageRenderer(
    private val facts: NoticeNotificationFactsPort,
    private val settings: NoticeMessageSettings,
) : ContentRenderer {
    override fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent = RenderedContent(channel, bodyFor(contentRef.value))

    private fun bodyFor(raw: String): String {
        val reference = NoticeReference.parse(raw) ?: return undecodableBody(raw)
        return facts.factsFor(reference)?.let { noticeBody(reference, it) } ?: missingNoticeBody(reference)
    }

    private fun noticeBody(
        reference: NoticeReference,
        notice: NoticeNotificationFacts,
    ): String =
        listOf(
            HEADLINE,
            "공고명 ${notice.title.orAbsent()}",
            "공고번호 ${reference.display()}",
            "수요기관 ${notice.demandAgencyName.orAbsent()}",
            "마감 ${deadlineText(notice.deadlineAt)}",
            "기초금액 ${amountText(notice)}",
            linkTo(reference),
        ).joinToString(separator = "\n")

    private fun missingNoticeBody(reference: NoticeReference): String =
        listOf(
            HEADLINE,
            "공고번호 ${reference.display()}",
            MISSING_NOTICE_LINE,
            linkTo(reference),
        ).joinToString(separator = "\n")

    private fun undecodableBody(raw: String): String =
        listOf(
            HEADLINE,
            "공고 식별자를 해독하지 못했습니다 — 내용 참조 '$raw'",
            INBOX_FALLBACK_LINE,
        ).joinToString(separator = "\n")

    private fun linkTo(reference: NoticeReference): String =
        "${settings.siteBaseUrl}/notices/${reference.number}/${reference.round}"

    private fun deadlineText(at: Instant?): String = at?.let { "${KST_FORMAT.format(it)} KST" } ?: ABSENT

    private fun amountText(notice: NoticeNotificationFacts): String {
        val won = notice.baseAmountWon ?: return ABSENT
        val formatted = String.format(Locale.KOREA, "%,d", won.toBigInteger())
        val currency = notice.baseAmountCurrency?.takeIf { it.isNotBlank() }
        return if (currency == null || currency == KRW) "${formatted}원" else "$formatted $currency"
    }

    private companion object {
        const val HEADLINE = "[입찰 추천]"
        const val ABSENT = "값 없음"
        const val KRW = "KRW"
        const val MISSING_NOTICE_LINE = "저장된 공고 정보가 없습니다 — 보존 파기 또는 미수집입니다."
        const val INBOX_FALLBACK_LINE = "알림함에서 확인하십시오."

        /**
         * 알림 본문이 시각을 적는 달력 구역 — **운영자의 하루와 같아야 한다**(KST). 다른 날짜 축
         * (증빙 보관 기한·알림 창·개찰일)도 각자 같은 이유로 자기 상수를 든다 — 한 상수를 공유하면
         * 「왜 이 축이 KST 인가」가 한 자리에만 적히고 다른 축의 근거가 사라진다.
         */
        val NOTICE_MESSAGE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        val KST_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA).withZone(NOTICE_MESSAGE_ZONE)

        fun String?.orAbsent(): String = this?.takeIf { it.isNotBlank() } ?: ABSENT
    }
}
