package bidvector.bidding

import java.time.Instant
import java.time.LocalDate

/**
 * 제출확인서 등 증빙의 **참조**(식별자)다 — 파일도, 파일 경로도, 본문도 아니다.
 * PDF 업로드 · 저장소 · 파기 실행은 이 slice 밖이고(계약 산출물 3), 그래서 이 값이 가리키는
 * 실체는 오늘 저장소 안에 없다(알려진 제한 ③).
 *
 * 그 상태에서 자유 텍스트를 그대로 받으면 파일명으로 개인 · 사업자 정보가 흘러든다. 그래서
 * **형태를 제한한다**: 공백 불가 · 길이 상한. 그리고 로그 · audit 어디에도 싣지 않는다
 * ([BidRecordEntry.memo] 와 같은 취급).
 */
data class EvidenceRef(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "증빙 참조는 빈 값일 수 없다" }
        require(value.length <= MAX_LENGTH) { "증빙 참조는 $MAX_LENGTH 자를 넘을 수 없다: ${value.length}" }
    }

    /** `toString()` 을 가린다 — 예외 문면 · 로그에 값이 실리지 않게(`ClearPassphrase` 관례). */
    override fun toString(): String = "EvidenceRef(***)"

    private companion object {
        const val MAX_LENGTH = 128
    }
}

/**
 * 운영자 메모 — 자유 텍스트다. **로그 · audit 노출 0**(계약 산출물 8): audit 는 본문을 남기지
 * 않고(7D), 이 타입은 `toString()` 을 가려 예외 문면으로도 새지 않는다.
 */
data class BidMemo(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "메모는 빈 값일 수 없다 — 없으면 null 이다" }
        require(value.length <= MAX_LENGTH) { "메모는 $MAX_LENGTH 자를 넘을 수 없다: ${value.length}" }
    }

    override fun toString(): String = "BidMemo(***)"

    private companion object {
        const val MAX_LENGTH = 2000
    }
}

/**
 * 「제출함」 확인이 **들고 오는 것** — 운영자(또는 SET)가 주장하는 사실의 전부다.
 *
 * **여기에 「소급인가」가 없다.** 그 자리를 두지 않는 것이 Q-11 의 「소급 표시」를 표시로
 * 유지하는 방법이다 — 호출부가 보내는 값이면 소급 건을 평범한 기록으로 위장할 수 있다.
 * 판정은 [BidTransitions.apply] 가 **출발 상태**로 한다.
 */
data class BidSubmission(
    val amount: SubmittedBidAmount,
    val submittedAt: Instant,
    val drawNumbers: List<Int>,
    val evidence: EvidenceRef?,
    val memo: BidMemo?,
) {
    init {
        require(drawNumbers.all { it > 0 }) { "추첨번호는 양수여야 한다" }
    }
}

/**
 * 투찰 기록(§6.1 「투찰 기록됨(금액·시각·추첨번호·증빙)」) — [BidSubmission] 에 **서버가
 * 정하는 둘**([confirmedAt] · [retroactive])과 정책이 계산하는 [evidenceRetainUntil] 을
 * 더한 값이다.
 *
 * **[submittedAt] 과 [confirmedAt] 은 다른 축이다** — 앞은 운영자가 나라장터에 낸 시각(진술),
 * 뒤는 그 사실을 이 시스템에 확인한 시각(서버의 시계). §6.1 이 *"확인 시각과 초안 시각이
 * 따로 남는다"* 라 적는다. 소급 확인에서 둘은 크게 벌어진다.
 *
 * 이 타입을 손으로 조립할 수는 있어도 **저장에 넣을 수는 없다** — 저장 포트가 받는 것은
 * `workflow.bidding.AppliedTransition`(그 모듈의 `internal constructor`)뿐이고, 그 값은
 * [BidTransitions.apply] 의 결과로만 만들어진다.
 */
data class BidRecordEntry(
    val submission: BidSubmission,
    val confirmedAt: Instant,
    val retroactive: Boolean,
    val evidenceRetainUntil: LocalDate?,
) {
    init {
        require((submission.evidence == null) == (evidenceRetainUntil == null)) {
            "증빙 참조와 보관 기한은 함께 있거나 함께 없다"
        }
    }
}
