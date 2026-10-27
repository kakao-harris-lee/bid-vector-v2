package bidvector.workflow.opening

import bidvector.procurement.ObservedBidAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Rate
import java.time.Instant

/**
 * 공고 하나의 개찰 결과 조회 결과(M7/7C⑤-a — SET-02 의 표시 절반, COL-02 표면).
 *
 * **「데이터가 없다」·「결과가 없다」·「아직 개찰 전이다」를 한 값으로 접지 않는다**(COL-04 의
 * 「요건 없음 ↔ 수집 실패」와 같은 원칙). 갈래마다 가용한 값이 다르다는 사실을 타입이 지므로,
 * 값이 없는 갈래에 값을 실을 자리가 애초에 없다.
 *
 * [Observed] 는 canonical 행이 있다는 뜻이고 진행구분 축은 그 안의 [OpeningProgress] 다 —
 * 그래서 「개찰완료·유찰·재입찰·등재 밖 라벨·라벨 부재」가 상태 다섯이 아니라 한 갈래의 축
 * 하나로 선다(같은 값 묶음을 네 번 복사하지 않는다).
 */
sealed interface OpeningResultView {
    /** 그 공고 자체가 수집되지 않았다 — 개찰 결과의 부재와 **다른 사실**이라 고유 상태다(`D-7C5-9`). */
    data object NoticeAbsent : OpeningResultView

    /**
     * 아직 개찰 전이다 — canonical 행이 없고 그 공고의 **투찰 마감이 지금보다 뒤**다. 개찰은
     * 마감 뒤라는 도메인 사실에서만 유도한다(개찰예정일시는 canonical 에 없다) — 마감을 모르면
     * 이 갈래가 아니다.
     */
    data object NotOpenedYet : OpeningResultView

    /** 개찰 축 원문도 canonical 행도 없다. 「수집 실패」는 공고 단위로 DB 에 없어 이 갈래에 접힌다(알려진 제한). */
    data object NotCollected : OpeningResultView

    /**
     * 개찰 축 **원문 관측은 있는데** canonical 승격이 없다 — M7/7C⑤ 착수 시점의 production 답이다
     * (승격 경로는 7C⑤-0 이 세운다). 「원문이 있다」가 「결과가 없다」로 보이지 않게 가르는 갈래다.
     */
    data object ObservedNotPromoted : OpeningResultView

    /** canonical 행이 있다 — 진행구분 축과 관측값을 함께 나른다. */
    data class Observed(
        val progress: OpeningProgress,
        val figures: OpeningResultFigures,
    ) : OpeningResultView
}

/**
 * 진행구분 축 — 등재 라벨 셋에 **부재**와 **등재 밖**을 더한 다섯이다.
 *
 * [UNRECOGNIZED] 와 [ABSENT] 는 다른 사실이다: 전자는 값이 있는데 정책 열거에 없는 것이고
 * 후자는 열 자체가 비어 있는 것이다(개찰완료 축만 승격된 행이 그 꼴일 수 있다 — 그 응답에는
 * 진행구분이 없고 잠정 1위는 있다). 둘을 접으면 「상류 어휘가 늘었다」를 아무도 보지 못한다.
 */
enum class OpeningProgress {
    OPENED,
    FAILED_BID,
    REBID,
    UNRECOGNIZED,
    ABSENT,
}

/**
 * canonical 행이 나르는 관측값 — **잠정 축과 확정 축이 서로 다른 칸**이다(SET-02 acceptance
 * 「잠정 1위 수집이 확정 낙찰 필드를 변경하지 않는다」를 응답 계약에서도 지킨다).
 *
 * 금액은 값만 싣지 않는다 — [confirmedAward] 는 `AwardAmount` 그대로라 basis·통화·과세·
 * provenance 가 함께 간다(R-BASIS-06).
 *
 * 관측 시각이 둘인 이유: [observedAt] 은 관측 전체의 시각이고 잠정 축은 **자기 관측 시각**을
 * 따로 나른다. 소비자가 둘을 비교해 잠정 값의 낡음을 스스로 판정한다(파생 플래그를 만들지
 * 않는다 — V5 migration 이 그 비교를 요구한다).
 */
data class OpeningResultFigures(
    val participantCount: Int?,
    val actualOpeningAt: Instant?,
    val confirmedAward: AwardAmount?,
    val provisionalRankOne: ProvisionalRankOne,
    val observedAt: Instant,
) {
    init {
        require(participantCount == null || participantCount >= 0) {
            "참가업체수는 음수일 수 없다: $participantCount"
        }
    }
}

/**
 * 잠정 1위 축 — **상호를 담을 자리가 없다**(D-7C5-3).
 *
 * 도메인의 `OpeningRankOneOutcome` 을 재사용하지 않는 이유가 그 부재 자체다: 그 타입의
 * 1위 행은 빈 문자열이 아닌 상호를 **생성자에서 요구**하므로, 재사용하면 조회 질의가 상호 열을
 * 읽어야 성립한다. 이 slice 는 그 열을 읽지 않는 것으로 노출을 구조로 막으니(§0.4 ⓑ) 재사용이
 * 곧 그 구조를 허무는 일이 된다. 같은 판단의 선례가 ML 스냅숏의 투찰자 행이다(D-6G-10 —
 * 「상호·사업자번호·대표자명이 없다」, 타입에 자리가 없음).
 *
 * 순위를 투찰금액으로 재계산하지 않는다 — 부재·중복은 정상 관측이고 그 자체가 결과다
 * (동값이 실재하는 도메인, `OPEN-3F-OPENG-RANK-SEMANTICS`).
 */
sealed interface ProvisionalRankOne {
    /** 이 축이 마지막으로 관측된 시각 — [NotObserved] 만 `null` 이다. */
    val observedAt: Instant?

    /** 투찰 행 자체가 관측되지 않았다. */
    data object NotObserved : ProvisionalRankOne {
        override val observedAt: Instant? = null
    }

    /** 어느 행도 순위 1이 아니다. */
    data class RankMissing(
        override val observedAt: Instant,
    ) : ProvisionalRankOne

    /** 순위 1이 둘 이상이다 — 동값을 임의로 깨지 않는다. */
    data class RankDuplicated(
        val count: Int,
        override val observedAt: Instant,
    ) : ProvisionalRankOne {
        init {
            require(count >= 2) { "순위 중복은 2건 이상일 때만 성립한다: $count" }
        }
    }

    /**
     * 순위 1이 정확히 하나다 — 그 투찰금액·투찰율만 나른다(상호는 없다). 협상 계약에서는
     * 금액·율의 부재가 정상이다(3F 실측).
     */
    data class Determined(
        val bidAmount: ObservedBidAmount?,
        val bidRate: Rate?,
        override val observedAt: Instant,
    ) : ProvisionalRankOne
}
