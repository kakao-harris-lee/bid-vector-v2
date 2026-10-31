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
    private val DRAFT_KEYS = setOf("amountWon", "vatTreatment", "presentedAt")
    private val RECORD_KEYS = setOf("amountWon", "vatTreatment", "submittedAt", "drawNumbers", "evidenceRef", "memo")

    fun openCase(body: JsonNode): Pair<NoticeKey, BidKind> {
        val payload = body.requireObject().requireKnownKeys(setOf("noticeNumber", "noticeRound", "bidKind"))
        val noticeNumber = payload.requiredNonBlankText("noticeNumber")
        val round = payload.requiredNonBlankText("noticeRound")
        // 감싸는 범위는 형식 규칙을 가진 생성자 하나다(검토 C-L1) — 진단 라벨이 엉뚱한 키를 가리키지 않는다.
        val notice = NoticeKey(noticeNumber, requestValue("noticeRound") { NoticeRound.of(round) })
        return notice to payload.enumOf("bidKind", BidKind.entries)
    }

    fun remindAt(body: JsonNode): Instant =
        body.requireObject().requireKnownKeys(setOf("remindAt")).requiredInstant("remindAt")

    fun openingAt(body: JsonNode): Instant =
        body.requireObject().requireKnownKeys(setOf("openingAt")).requiredInstant("openingAt")

    fun documentDueAt(body: JsonNode): Instant =
        body.requireObject().requireKnownKeys(setOf("documentDueAt")).requiredInstant("documentDueAt")

    fun skipReason(body: JsonNode): SkipReason {
        val raw = body.requireObject().requireKnownKeys(setOf("reason")).requiredNonBlankText("reason")
        // 길이 상한(`SkipReason.MAX_LENGTH`)을 어기면 400 이다 — 500 으로 새지 않는다.
        return requestValue("reason") { SkipReason(raw) }
    }

    /**
     * 개찰 결과 — 1위인가 아닌가. **순위 숫자를 받지 않는다**: 운영자 요구가 「몇 위보다 낙찰
     * 여부」이고(§6.1 「몇 위였나」 문장), 순위를 받으면 쓰지도 않는 값을 보관하게 된다.
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
        val raw = payload.requiredNonBlankText("reason")
        return payload.enumOf("outcome", BidOutcome.entries) to requestValue("reason") { TransitionReason(raw) }
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
        )
    }

    /** 「제출함」 — retroactive · evidenceRetainUntil 자리가 **없다**(위 KDoc). */
    fun submission(body: JsonNode): BidSubmission {
        val payload = body.requireObject().requireKnownKeys(RECORD_KEYS)
        val won = payload.requiredLong("amountWon", minimum = 0L)
        val vat = payload.enumOf("vatTreatment", VatTreatment.entries)
        // 금액 값 규칙은 생성자가 진다 — 판독기의 minimum 이 오늘 먼저 막지만, 규칙이 갈라지면 500 이 아니라 400 이다(C3-L7).
        val amount =
            requestValue("amountWon") {
                SubmittedBidAmount(
                    won = won,
                    currency = Currency.KRW,
                    vatTreatment = vat,
                    origin = BidAmountOrigin.OperatorEntered,
                )
            }
        val submittedAt = payload.requiredInstant("submittedAt")
        val drawNumbers = payload.optionalIntArray("drawNumbers")
        // 값 규칙(길이 상한 · 부호)을 가진 생성자만 그 키의 라벨로 감싼다(검토 r2 C2-N11 — 진단이 엉뚱한 키를 가리키지 않게).
        val evidence =
            payload.optionalNonBlankText("evidenceRef")?.let { requestValue("evidenceRef") { EvidenceRef(it) } }
        val memo = payload.optionalNonBlankText("memo")?.let { requestValue("memo") { BidMemo(it) } }
        return requestValue("drawNumbers") { BidSubmission(amount, submittedAt, drawNumbers, evidence, memo) }
    }
}

/**
 * 경로 · 질의 읽기 — 본문 읽기(`BidCaseRequests`)와 가른다.
 *
 * 가른 이유는 detekt 의 object 함수 상한(11)이지만, 경계 자체는 의미가 있다: 이쪽은
 * **요청 봉투**(path · query)를 읽고 저쪽은 **본문**을 읽는다. **주체는 둘 다 읽지 않는다** —
 * 계정은 인증 필터가 심은 typed 속성 하나(`request.authenticatedAccount()`, 계약 D-7B-2)에서만
 * 오고, audit 주체 라벨을 파싱해 얻지 않는다.
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
}

/**
 * 초안 요청이 실어 오는 것 — 공고 스냅샷은 **서버가 뜨므로** 여기 없다(위협 ③). 「화면이 공고를 본
 * 시각」(`snapshotTakenAt`)도 받지 않는다(계약 D-7B-3 결정 ③) — 받고서 쓰지 않는 입력은 계약 거짓말이고,
 * 화면 신선도 검사가 필요하면 별 slice 다.
 */
internal data class DraftInput(
    val amount: SubmittedBidAmount,
    val presentedAt: Instant,
)
