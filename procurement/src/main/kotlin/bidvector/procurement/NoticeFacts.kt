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
    /**
     * M3/3F ① 개찰 1위 축(D-3F-4 (a)) — `getOpengResultListInfoOpengCompt`(개찰완료)만 준다.
     * D-3F-3 해소로 투찰자별 canonical 표를 만들지 않는다 — 순위 1 행을 특정할 수 있을 때만
     * [OpeningRankOneOutcome.Determined]이고, 부재·중복이면 이 축을 비우고 사유를 명시적으로
     * 나른다(투찰금액으로 순위를 재계산하지 않는다, scope.md 설계 검토 (2)).
     */
    val openingRankOne: OpeningRankOneOutcome = OpeningRankOneOutcome.NotObserved,
    /**
     * M3/3F ② 관측된 추첨번호 집합(D-3F-4 (a), `drwtNo1`·`drwtNo2`) — 15행의 1-기반 인덱스
     * (§1.9.4). 투찰자별 귀속은 보존하지 않는다(운영자 도메인 결정, D-3F-3) — 실현 사정률
     * 계산(M5)은 이 집합만으로 충분하다. 범위 검사는 [totalReservePriceCandidateCount]가
     * 있어야 성립하는데 이 오퍼레이션 응답에는 그 값이 없다(§1.9.7) — 그래서 「검사 불가」가
     * 조용한 통과가 아니라 그 자체로 하나의 결과다([DrawNumberObservation]).
     */
    val drawNumbers: DrawNumberObservation = DrawNumberObservation.NotObserved,
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

/**
 * 투찰금액(`bidprcAmt`) 관측값 전용 값 객체(M3/3F) — `bidvector.sharedkernel.BidAmount`
 * (Basis.BID)는 **기초금액×투찰율의 파생값**만 나르고 생성자가 `internal`이라 그 생성 경로가
 * `MoneyArithmetic.kt` 하나뿐이다(shared-kernel 「유일한 생성 경로는 반올림 함수」). 이 축은
 * KONEPS 가 준 **관측**이지 파생이 아니다 — 파생 전용 타입을 관측값으로 지어내는 것은
 * `ReservePriceCandidateAmount`가 피한 것과 같은 우회(값은 있고 출처는 지어낸 것)라 별도
 * 평범한 값 객체로 둔다. `vatTreatment`는 다른 개찰 축 금액과 같이 `UNKNOWN` 고정이다
 * (§1.7.1 문면 「미선언 → UNKNOWN」).
 */
data class ObservedBidAmount(
    val won: Long,
    val currency: Currency,
) {
    val vatTreatment: VatTreatment = VatTreatment.UNKNOWN

    init {
        require(won >= 0L) { "금액은 음수일 수 없다: $won" }
    }
}

/**
 * 개찰 1위 행(M3/3F, D-3F-4 (a)) — 상호(`prcbdrNm`, masked)·투찰금액·투찰율. 투찰금액·투찰율은
 * 협상 계약에서 부재가 정상이다(§1.9.7 실측, 3/15).
 *
 * **평가점수 넷(D-3F-5 (a))은 이 슬롯에 없다** — P-13 (a) 승인(2026-09-09, `policy-values.md`
 * §1.11)이 그 넷을 계약 등재에서 **제외**했다(scale 이 3A `FieldScale` 어휘에 없고 문서가
 * 범위·소수 자리를 적지 않는다, 「어휘를 지어내지 않는다」). 계약이 없으면 allow-list
 * 반전(§5.3 규율 1)이 그 값을 masking 경계에서 자동으로 걷어낸다 — `raw_observation` 에도
 * 남지 않는다. D-3F-5 (a)의 「수집·보존」은 그래서 이 slice 에서 **평가점수 축에는 적용되지
 * 않는다**(계약이 열리는 후속 slice 로 미룬다) — 판단이 갈린 지점, checklist.md.
 */
data class OpeningRankOneBid(
    val bidderName: String,
    val bidAmount: ObservedBidAmount? = null,
    val bidRate: Rate? = null,
) {
    init {
        require(bidderName.isNotBlank()) { "bidderName은 빈 문자열일 수 없다" }
    }
}

/**
 * 개찰 1위 축의 결정 결과(M3/3F, D-3F-4 (a)) — [resolve]가 유일한 생성 경로는 아니지만
 * (부모 fact 는 이 슬롯을 직접 받는다), **순위 1을 특정할 수 없으면 그 축을
 * 비우고 사유를 명시적으로 나른다**는 요구를 sealed type 으로 고정한다. `opengRank`가
 * 실측(§1.9.7)에서 전 행 채워지고 유일한 경우가 4/15뿐이라(결측·중복 흔함) [RankMissing]·
 * [RankDuplicated]가 정상 관측이다 — 투찰금액으로 순위를 재계산해 채우지 않는다(동값이
 * 실재하는 도메인, 설계 검토 (4) 「과잉 하나」).
 */
sealed interface OpeningRankOneOutcome {
    /** 투찰자 행 자체가 관측되지 않았다(기본값). */
    data object NotObserved : OpeningRankOneOutcome

    /** 어느 행도 순위 1이 아니다. */
    data object RankMissing : OpeningRankOneOutcome

    /** 순위 1이 둘 이상이다 — 동값을 임의로 깨지 않는다. */
    data class RankDuplicated(
        val count: Int,
    ) : OpeningRankOneOutcome {
        init {
            require(count >= 2) { "RankDuplicated는 2건 이상일 때만 성립한다: $count" }
        }
    }

    /** 순위 1이 정확히 하나다. */
    data class Determined(
        val bid: OpeningRankOneBid,
    ) : OpeningRankOneOutcome

    companion object {
        /**
         * 관측된 (순위, 개찰 1위 후보) 짝 목록에서 개찰 1위 축을 결정한다. 순위 파싱·raw 텍스트
         * 해석은 호출부 몫이다(canonicalize, M4 4B 배선) — 이 함수는 이미 파싱된 `Int?` 순위만
         * 받는다.
         */
        fun resolve(candidates: List<Pair<Int?, OpeningRankOneBid>>): OpeningRankOneOutcome {
            if (candidates.isEmpty()) return NotObserved
            val rankOnes = candidates.filter { it.first == 1 }
            return when (rankOnes.size) {
                0 -> RankMissing
                1 -> Determined(rankOnes.single().second)
                else -> RankDuplicated(rankOnes.size)
            }
        }
    }
}

/**
 * 추첨번호(`drwtNo1`·`drwtNo2`) 관측 결과(M3/3F, D-3F-4 (a)) — 15행의 1-기반 인덱스(§1.9.4).
 * 실현 사정률 계산(M5)은 이 슬라이스 밖이다 — 이 타입은 **범위 검사까지**만 진다. 범위 검사는
 * `OpeningResult.totalReservePriceCandidateCount`(3E 슬롯)를 요구하는데 이 오퍼레이션 응답에는
 * 그 값이 없다(§1.9.7 실측) — 그래서 **검사 불가**가 조용한 통과(`Verified`)가 아니라 그
 * 자체로 하나의 결과다(위협 모델 방어 (d)).
 */
sealed interface DrawNumberObservation {
    /** 관측된 번호가 없다 — 부재가 정상 형태다(협상 계약·단수 예가, §1.9.7 7/15). */
    data object NotObserved : DrawNumberObservation

    /** 1..총예가건수 범위 안 — 검사를 통과했다. */
    data class Verified(
        val numbers: Set<Int>,
    ) : DrawNumberObservation

    /** 범위 밖 번호가 섞여 있다 — 조용히 통과시키지 않는다. */
    data class OutOfRange(
        val numbers: Set<Int>,
        val validRange: IntRange,
    ) : DrawNumberObservation

    /** 번호는 있으나 총예가건수를 몰라 범위를 검사할 수 없다(§1.9.7 — 이 오퍼레이션 응답에 없다). */
    data class RangeCheckUnavailable(
        val numbers: Set<Int>,
    ) : DrawNumberObservation

    companion object {
        fun of(
            numbers: Set<Int>,
            totalReservePriceCandidateCount: Int?,
        ): DrawNumberObservation =
            when {
                numbers.isEmpty() -> {
                    NotObserved
                }

                totalReservePriceCandidateCount == null -> {
                    RangeCheckUnavailable(numbers)
                }

                numbers.any { it < 1 || it > totalReservePriceCandidateCount } -> {
                    OutOfRange(numbers, 1..totalReservePriceCandidateCount)
                }

                else -> {
                    Verified(numbers)
                }
            }
    }
}

/** 자격 원문 fact(D-3A-1 (a)) — 원문 파싱은 3B 소관이고, 여기는 원문 + 관측 시각만 보존한다. */
data class QualificationText(
    val noticeId: NoticeId,
    val rawText: String,
    val observedAt: Instant,
)
