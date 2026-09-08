package bidvector.procurement

import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.YegaAmount
import java.time.Instant

/**
 * 공고 fact + 상태(D-3A-1 (a)) — canonical fact 셋(`Notice`·[OpeningResult]·[QualificationText])
 * 중 상태를 보유하는 유일한 자리(`NoticeId`로 묶인다). `status` 변경은 [applyEvent] 하나뿐이다 —
 * `@ConsistentCopyVisibility` + `internal constructor`(1E 관례)가 `copy(status = ...)`로 전이표를
 * 우회하는 경로를 닫는다(우회 (5), verifier r1 F-5가 「대상 부재」로 지적한 자리를 채운다).
 */
@ConsistentCopyVisibility
data class Notice internal constructor(
    val id: NoticeId,
    val status: NoticeStatus,
    val businessCategory: BusinessCategory?,
    val baseAmount: ResolvedBaseAmount?,
    val estimatedAmount: ResolvedEstimatedAmount?,
    val allocatedBudget: AllocatedBudget?,
    val floorRate: FloorRate?,
    val deadlineAt: Instant?,
) {
    /** 유일한 상태 변경 경로 — 표 밖의 전이는 [NoticeTransitionOutcome.Rejected]로 관측된다. */
    fun applyEvent(event: NoticeEvent): NoticeTransitionOutcome =
        when (val result = transition(status, event)) {
            is TransitionResult.Moved -> NoticeTransitionOutcome.Applied(copy(status = result.to))
            is TransitionResult.Rejected -> NoticeTransitionOutcome.Rejected(result)
        }

    companion object {
        /** [NoticeCollected](②)로부터 최초 진입 — 항상 `Open`이다(§2.2.1 「(없음) → NoticeCollected → Open」). */
        fun collected(command: NoticeCollected): Notice =
            Notice(
                id = command.id,
                status = NoticeStatus.Open,
                businessCategory = command.businessCategory,
                baseAmount = command.baseAmount,
                estimatedAmount = command.estimatedAmount,
                allocatedBudget = command.allocatedBudget,
                floorRate = command.floorRate,
                deadlineAt = command.deadlineAt,
            )
    }
}

/** [Notice.applyEvent]의 결과 — 소진 `when`으로만 소비한다(거부를 조용히 삼키지 않는다). */
sealed interface NoticeTransitionOutcome {
    data class Applied(
        val notice: Notice,
    ) : NoticeTransitionOutcome

    data class Rejected(
        val result: TransitionResult.Rejected,
    ) : NoticeTransitionOutcome
}

/**
 * 개찰 결과 fact(D-3A-1 (a)) — `Notice`와 별도 fact다(수집 실패·시점 단위가 다르다, COL-03
 * 「공고 1건당 별도 호출」). 예정가는 **파생 필드 + provenance**만 나른다 — 역산 계산식은
 * 여기 없다(3D 소유, 「만들지 않는 것」).
 */
data class OpeningResult(
    val noticeId: NoticeId,
    val winningRate: Rate?,
    val derivedBaseAmount: ResolvedBaseAmount.DerivedFromOpeningAmount?,
    val observedAt: Instant,
    /**
     * ③ fact 슬롯 확장(추가만, M3/3E, D-3E-4 (a) — 부모 fact + 자식 목록, 한 aggregate) 첫째
     * 슬롯 — 3B-2가 실제로 수집하는 것만 준다(`policy-values.md` §1.7.1·§1.7.5). 추첨번호·
     * 투찰 축(3F)의 자리는 만들지 않는다. 최종낙찰금액(`sucsfbidAmt`) — `AwardAmount`(basis
     * AWARD)가 이미 있다.
     */
    val finalAwardAmount: AwardAmount? = null,
    /** 최종낙찰업체명(`bidwinnrNm`, 상호만 — P-10 (a), 사업자등록번호·대표자명 슬롯은 만들지 않는다). */
    val finalAwardCompanyName: String? = null,
    /** 참가업체수(`prtcptCnum`, §1.7.3 「셈」 축 — 계약 어휘가 아직 없어 원시 정수만 나른다). */
    val participantCount: Int? = null,
    /** 진행구분(`progrsDivCdNm`, §1.7.5 — 유찰/개찰완료/재입찰 3값 열거, 문서 라벨 원문 그대로). */
    val progressDivision: String? = null,
    /**
     * 예정가격(`plnprc`, basis YEGA) — **공고 층 슬롯**(팀리드 실측 정정 2026-09-08, §1.9.7).
     * 예비가격 상세 응답은 행마다 이 값을 반복해 싣지만 한 공고에 하나다 — 자식 행에 두면
     * 단수 예가(총예가건수 1, 순번 공백)일 때 자식 행이 0개가 되며 이 값도 함께 사라진다.
     */
    val plannedPrice: YegaAmount? = null,
    /**
     * 기초금액(`bssamt`) — 공고 층 슬롯, `plannedPrice`와 같은 이유(§1.9.7). Notice의
     * `baseAmount`(공고 목록 축 관측)와 별개다 — 이 슬롯은 예비가격 상세 오퍼레이션 자신의
     * 관측이다(수집 시점·엔드포인트가 다르다, D-3A-1 (a)와 같은 「fact를 섞지 않는다」 원칙).
     */
    val baseAmount: BaseAmount? = null,
    /** 총예가건수(`totRsrvtnPrceNum`) — 실측(§1.9.7)이 `reservePrices.size`와 일치를 확인한 축. */
    val totalReservePriceCandidateCount: Int? = null,
    /** 실개찰일시(`rlOpengDt`) — 공고 층 슬롯(§1.9.7, `sourceZone` 미확정이라 원문 그대로). */
    val actualOpeningAt: Instant? = null,
    /** 복수예비가격 자식 행 목록(D-3E-2 (a)) — 순번 부재 행은 여기 오르지 않는다(D-3E-1b (a)). */
    val reservePrices: List<OpeningReservePriceRow> = emptyList(),
) {
    init {
        require(participantCount == null || participantCount >= 0) {
            "participantCount는 음수일 수 없다: $participantCount"
        }
        require(totalReservePriceCandidateCount == null || totalReservePriceCandidateCount >= 0) {
            "totalReservePriceCandidateCount는 음수일 수 없다: $totalReservePriceCandidateCount"
        }
    }
}

/**
 * 복수예비가격 후보 자식 행(D-3E-2 (a), M3/3E 신설, §1.9.7 실측 정정으로 「후보 층」만 남는다)
 * — `OpeningResult`가 목록으로 안는다. `sequenceNumber`(`compnoRsrvtnPrceSno`)가 부재·공백인
 * 행은 이 타입으로 만들어지지 않는다(D-3E-1b (a), 운영자 승인 2026-09-08 — 정체성 없는 행은
 * canonical 승격을 거절한다). 3B-2 `rowIdentifierIndeterminate` 회계가 그 승격 불가 건수를
 * 이미 센다(같은 부재 판정을 공유한다, `KonepsRawItemMapper.rowDiscriminatorOf`). **실측
 * (§1.9.7, 8건 23행, 2026-09-01~09-07 창)이 부재 조건을 좁혔다** — 순번 공백은 총예가건수가
 * 1(단수 예가)일 때만 관측됐고, 그 경우 행이 하나뿐이라 애초에 정체성 모호가 없다. 15행
 * 건(4건)은 순번이 전부 채워져 있었다 — COL-03이 요구하는 복수예비가격 축은 이 관측 범위에서
 * 온전하다. 표본이 작아 「항상 그렇다」로 승격하지 않는다.
 *
 * **`observedAt`(verifier r1 H-2 뒤 신설)** — D-3E-3 (a)가 확정한 「사라진 행을 지우지 않고
 * 관측 시각으로 구분한다」의 읽기 경로 절반. 15→12 재수집처럼 이번 응답에 없던 행이 저장에
 * 남을 때, 그 행의 `observedAt`이 최신 관측(부모 `OpeningResult.observedAt`)보다 이르면
 * 「낡았다」고 소비자가 스스로 판정할 수 있다 — 낡음 자체를 저장 컬럼(파생 플래그)으로 만들지
 * 않는다(파생은 읽는 쪽이 낸다, 3D 규율). 필수 파라미터다 — raw 관측이 이미
 * `observedAt`을 항상 나르므로(`RawNoticeObservation`) 「모름」이 아니다.
 */
data class OpeningReservePriceRow(
    val sequenceNumber: String,
    /** 기초예정가격(`bsisPlnprc`) — basis가 문서로 미확정이라 `Money` 타입에 태우지 않는다([ReservePriceCandidateAmount]). */
    val baseReservePrice: ReservePriceCandidateAmount?,
    /** 추첨여부(`drwtYn`, 문서 `(Y/N)` 필수 — 이 행이 존재하면 항상 값이 있다고 문서가 선언한다). */
    val isDrawn: Boolean?,
    /** 이 행이 마지막으로 관측된 시각 — 사라진 행 판별의 유일한 근거(위 KDoc). */
    val observedAt: Instant,
    /** 추첨횟수(`drwtNum`) — §1.9.1이 예비가격 상세 신설 후보로 짚은 축, 후보 자신의 값이다. */
    val drawCount: Int? = null,
) {
    init {
        require(sequenceNumber.isNotBlank()) { "sequenceNumber는 빈 문자열일 수 없다" }
        require(drawCount == null || drawCount >= 0) { "drawCount는 음수일 수 없다: $drawCount" }
    }
}

/**
 * 기초예정가격(`bsisPlnprc`) 전용 금액 값 객체 — `policy-values.md` §1.7.1이 이 키의 basis를
 * 「미확정」으로 남겼다(예정가격 `YEGA`로 접으면 §1.1 `bssAmtPurcnstcst`와 같은 「부분을 전체
 * 자리에」가 된다). `shared-kernel Money`는 basis가 있는 여섯 구현으로 닫힌 `sealed
 * interface`라 procurement가 일곱째 구현을 더할 수 없고, 없는 basis를 지어내 기존 여섯 중
 * 하나로 태우지도 않는다(「모름을 지어내지 않는다」 규율) — 그래서 `Money`를 구현하지 않는
 * 평범한 값 객체로 둔다. `vatTreatment`는 `YegaAmount`·`AwardAmount`와 같은 이유로 `UNKNOWN`
 * 고정이다(§1.7.1 문면 「미선언 → UNKNOWN」, 다른 값을 만들 생성 경로가 없다).
 *
 * **`provenance`를 두지 않는다**(verifier 전 자기 발견 정정, Layer B 저장 설계 중) — 이 축은
 * `opening_result`·`opening_reserve_price`와 같은 「최신 관측 우선, 권위 계층 없음」 축이라
 * (D-M3-7 OPEN-DIC-09) `derivedBaseAmount`도 provenance를 고정 상수(`DerivedFromOpening`)로
 * 읽어내지 별도 컬럼에 왕복시키지 않는다. `bsisPlnprc`엔 그런 고정 상수가 없고(파생이 아니라
 * 원문 관측이다), 그렇다고 임의 `Provenance`를 저장·복원하면 왕복 안정성이 없는 필드를 타입에
 * 얹는 것이다 — 만들지 않는다.
 */
data class ReservePriceCandidateAmount(
    val won: Long,
    val currency: Currency,
) {
    val vatTreatment: VatTreatment = VatTreatment.UNKNOWN

    init {
        require(won >= 0L) { "금액은 음수일 수 없다: $won" }
    }
}

/** 자격 원문 fact(D-3A-1 (a)) — 원문 파싱은 3B 소관이고, 여기는 원문 + 관측 시각만 보존한다. */
data class QualificationText(
    val noticeId: NoticeId,
    val rawText: String,
    val observedAt: Instant,
)
