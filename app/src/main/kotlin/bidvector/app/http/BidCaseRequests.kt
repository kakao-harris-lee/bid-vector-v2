package bidvector.app.http

import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidMemo
import bidvector.bidding.BidOutcome
import bidvector.bidding.BidStateTag
import bidvector.bidding.BidSubmission
import bidvector.bidding.EvidenceRef
import bidvector.bidding.NoticeKey
import bidvector.bidding.SkipReason
import bidvector.bidding.SubmittedBidAmount
import bidvector.bidding.TransitionReason
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import jakarta.servlet.http.HttpServletRequest
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * 요청 → command 변환. **알려진 키 밖은 400** 이다(D-6A2b-25 와 같은 규율).
 *
 * **(2b) 값 획득 축 — 이 파일이 밖에 허락하지 **않는** 것.**
 *  · `provenance` · `chosenOptionRef` — **자리가 없다.** 초안·기록의 금액 출처는
 *    [BidAmountOrigin.OperatorEntered] 로 서버가 싣는다. 운영자 입력을 투찰가 메뉴 산출로
 *    보이게 하는 요청은 **표현할 수 없다**(계약 산출물 6 「메뉴 값 위장 금지」).
 *  · `retroactive` — 자리가 없다. 소급은 출발 상태가 정한다(Q-11).
 *  · `evidenceRetainUntil` — 자리가 없다. 보관 기한은 정책이 정한다(R-5).
 *  · `accountId` — 자리가 없다. 주체는 인증 필터가 적은 값에서만 온다.
 *  · `caseVersion` — 자리가 없다. 낙관적 잠금의 기준은 서버가 방금 읽은 판이다.
 */
internal object BidCaseRequests {
    private val DRAFT_KEYS = setOf("amountWon", "vatTreatment", "presentedAt", "snapshotTakenAt")
    private val RECORD_KEYS = setOf("amountWon", "vatTreatment", "submittedAt", "drawNumbers", "evidenceRef", "memo")

    fun openCase(body: JsonNode): Pair<NoticeKey, BidKind> {
        val payload = body.requireObject().requireKnownKeys(setOf("noticeNumber", "noticeRound", "bidKind"))
        return NoticeKey(
            payload.requiredNonBlankText("noticeNumber"),
            NoticeRound.of(payload.requiredNonBlankText("noticeRound")),
        ) to
            payload.enumOf("bidKind", BidKind.entries)
    }

    fun remindAt(body: JsonNode): Instant =
        body.requireObject().requireKnownKeys(setOf("remindAt")).requiredInstant("remindAt")

    fun openingAt(body: JsonNode): Instant =
        body.requireObject().requireKnownKeys(setOf("openingAt")).requiredInstant("openingAt")

    fun documentDueAt(body: JsonNode): Instant =
        body.requireObject().requireKnownKeys(setOf("documentDueAt")).requiredInstant("documentDueAt")

    fun skipReason(body: JsonNode): SkipReason =
        SkipReason(body.requireObject().requireKnownKeys(setOf("reason")).requiredNonBlankText("reason"))

    /**
     * 개찰 결과 — 1위인가 아닌가. **순위 숫자를 받지 않는다**: 운영자 요구가 「몇 위보다 낙찰
     * 여부」이고(§6.1 :175-176), 순위를 받으면 쓰지도 않는 값을 보관하게 된다.
     */
    fun openingFirst(body: JsonNode): Boolean {
        val payload = body.requireObject().requireKnownKeys(setOf("provisionalFirst"))
        val node = payload.requiredNode("provisionalFirst")
        if (!node.isBoolean) throw InvalidEditRequestException("provisionalFirst 는 불리언이어야 한다")
        return node.booleanValue()
    }

    fun outcome(body: JsonNode): BidOutcome =
        body
            .requireObject()
            .requireKnownKeys(setOf("outcome"))
            .enumOf("outcome", BidOutcome.entries)

    fun correction(body: JsonNode): Pair<BidOutcome, TransitionReason> {
        val payload = body.requireObject().requireKnownKeys(setOf("outcome", "reason"))
        return payload.enumOf("outcome", BidOutcome.entries) to
            TransitionReason(payload.requiredNonBlankText("reason"))
    }

    /** 초안 — provenance 자리가 **없다**(위 KDoc). 서버가 `OperatorEntered` 를 싣는다. */
    fun draft(body: JsonNode): DraftInput {
        val payload = body.requireObject().requireKnownKeys(DRAFT_KEYS)
        return DraftInput(
            amount =
                SubmittedBidAmount(
                    won = payload.requiredLong("amountWon", minimum = 0L),
                    currency = Currency.KRW,
                    vatTreatment = payload.enumOf("vatTreatment", VatTreatment.entries),
                    origin = BidAmountOrigin.OperatorEntered,
                ),
            presentedAt = payload.requiredInstant("presentedAt"),
            snapshotTakenAt = payload.requiredInstant("snapshotTakenAt"),
        )
    }

    /** 「제출함」 — retroactive · evidenceRetainUntil 자리가 **없다**(위 KDoc). */
    fun submission(body: JsonNode): BidSubmission {
        val payload = body.requireObject().requireKnownKeys(RECORD_KEYS)
        return BidSubmission(
            amount =
                SubmittedBidAmount(
                    won = payload.requiredLong("amountWon", minimum = 0L),
                    currency = Currency.KRW,
                    vatTreatment = payload.enumOf("vatTreatment", VatTreatment.entries),
                    origin = BidAmountOrigin.OperatorEntered,
                ),
            submittedAt = payload.requiredInstant("submittedAt"),
            drawNumbers = payload.optionalIntArray("drawNumbers"),
            evidence = payload.optionalNonBlankText("evidenceRef")?.let(::EvidenceRef),
            memo = payload.optionalNonBlankText("memo")?.let(::BidMemo),
        )
    }
}

/**
 * 경로 · 질의 · **주체** 읽기 — 본문 읽기(`BidCaseRequests`)와 가른다.
 *
 * 가른 이유는 detekt 의 object 함수 상한(11)이지만, 경계 자체는 의미가 있다: 이쪽은
 * **요청 봉투**(path · query · 인증 필터가 적은 속성)를 읽고 저쪽은 **본문**을 읽는다.
 * 주체가 본문 읽기와 같은 자리에 있으면 「본문에서 주체를 받는」 변경이 눈에 덜 띈다.
 */
internal object BidCaseRouting {
    fun bidKind(raw: String?): BidKind =
        BidKind.entries.firstOrNull { it.name == raw }
            ?: throw InvalidEditRequestException("bidKind 는 ${BidKind.entries.joinToString("·") { it.name }} 중 하나다")

    fun stateTag(raw: String?): BidStateTag? {
        if (raw == null) return null
        return BidStateTag.entries.firstOrNull { it.name == raw }
            ?: throw InvalidEditRequestException("state 가 알려진 상태 어휘가 아니다")
    }

    fun caseId(raw: String): BidCaseId {
        val value = raw.toLongOrNull() ?: throw InvalidEditRequestException("caseId 가 숫자가 아니다")
        if (value <= 0L) throw InvalidEditRequestException("caseId 는 양수여야 한다")
        return BidCaseId(value)
    }

    /**
     * 요청의 주체 — **필터가 적은 값**에서만 온다(요청 본문·헤더에서 받지 않는다).
     *
     * 입찰함은 **계정 경로 전용**이다: 기존 운영자 자격 헤더(`operator`)에는 계정이 없어
     * 소유 경계를 세울 수 없다. 그 요청은 거부하고 **사유를 나누지 않는다**(D-6A1-6) —
     * 「인증은 됐는데 계정이 아니다」를 따로 말하면 그 자체가 신호가 된다.
     */
    fun accountOf(request: HttpServletRequest): AccountId? {
        val subject = request.authenticatedSubject()
        val raw = subject.removePrefix(ACCOUNT_SUBJECT_PREFIX)
        if (raw == subject) return null
        return raw.toLongOrNull()?.takeIf { it > 0L }?.let(::AccountId)
    }

    private const val ACCOUNT_SUBJECT_PREFIX = "account:"
}

/** 초안 요청이 실어 오는 것 — 공고 스냅샷은 **서버가 뜨므로** 여기 없다(위협 ③). */
internal data class DraftInput(
    val amount: SubmittedBidAmount,
    val presentedAt: Instant,
    val snapshotTakenAt: Instant,
)

/**
 * 알려진 어휘 하나 — 값이 enum 밖이면 400 이다. 「모르는 값을 기본값으로 접는」 경로를 두지
 * 않는다(그것이 곧 어휘를 지어내는 길이다).
 */
private fun <T : Enum<T>> JsonNode.enumOf(
    key: String,
    values: List<T>,
): T {
    val raw = requiredNonBlankText(key)
    return values.firstOrNull { it.name == raw }
        ?: throw InvalidEditRequestException("$key 는 ${values.joinToString("·") { it.name }} 중 하나다")
}
