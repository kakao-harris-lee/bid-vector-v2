package bidvector.decision.menu

import bidvector.decision.FloorShortfallJudgement
import bidvector.sharedkernel.BidAmount
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Derived
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.ReasonCode

/**
 * 최종 추천가를 **무엇이 정했는가**(`capability-map.md` DEC-02 무조건 acceptance 첫째 —
 * 「최종 추천가가 어느 제약에 binding 됐는지가 결과에 실린다」).
 *
 * 값마다 **다른 제약**이라 「제약 하나를 지워도 결과가 같다」가 성립할 수 없다 — 그것이 이
 * 타입이 지키는 성질이고 변이 test 가 그 성질을 잰다.
 *
 * DEC-02 의 **잠정** bullet(예산 상한 · 계약 역전 미채택, `OPEN-DEC-03` 재결정 대상)에
 * 해당하는 값은 **두지 않는다** — 생산자 없는 variant 는 소비자 없는 필드와 같은 결함이다
 * (`OPEN-4B5-COMPETITIVENESS` 가 같은 이유로 `DerivationPolicyData` 에 필드를 두지 않았다).
 */
sealed interface BindingConstraint {
    /** 후보 비율 자신이 값을 정했다 — 걸린 제약이 없다. */
    data object CandidateRate : BindingConstraint

    /** 게시 하한율에서 읽은 투찰율 하한이 값을 정했다(후보가 그 하한에 걸렸거나 정확히 그 위다). */
    data object PublishedFloorBound : BindingConstraint

    /**
     * **잠정** — 하한율의 읽기가 미정이라(`OPEN-DEC05-FLOOR-RATE-READING`) 하한 제약을
     * 적용하지 않았다. 「제약이 없었다」([CandidateRate])와 **다른 사실**이다 — 제약을 재지
     * 못한 것이지 재서 안 걸린 것이 아니다. 둘을 한 값으로 접으면 미결이 「제약 없음」으로
     * 위장된다.
     */
    data object FloorRateReadingUndecided : BindingConstraint
}

/**
 * 공통 고지 두 문장의 **자리**(DEC-05 분류 근거 — 「예정가격은 개찰 전 비공개 추첨으로
 * 결정되어 정확한 낙찰하한을 보장할 수 없다」·「자동 제출·확정 낙찰이 아니다」).
 *
 * **한국어 완성 문장을 도메인이 들지 않는다** — 문장 생성·영속은 DEC-12 A1 이 폐기한 형태다
 * (legacy 는 계산이 바뀐 뒤에도 영속된 문구가 옛 분모를 말했고 저장된 감사 기록이 틀린 채
 * 남았다). 이 enum 은 **무엇을 고지해야 하는가**만 나르고 문장은 표면이 짓는다.
 */
enum class BidMenuDisclosure {
    YegaDrawnPrivately,
    NotAutoSubmitted,
}

/**
 * 옵션의 **근거**(DEC-05 무조건 acceptance 첫째 — 「값만 있고 근거 없는 옵션을 만들 수 없다」).
 *
 * 위조가 구조로 막힌다 — [BidRate] 는 `shared-kernel` 의 `internal` 생성자 뒤에 있고 공개
 * 경로는 출처를 이름에 담은 factory 뿐이다. 임계 사정률을 여기 두지 않는다 — 그 값은
 * [BidMenuOption.risk] 의 `FloorShortfallJudgement.critical` 이 이미 `Derived` 로 나른다
 * (같은 사실을 두 자리에 두지 않는다).
 */
data class BidMenuGrounds(
    val bidRate: BidRate,
    val publishedFloorRate: FloorRate,
    val baseProvenance: Provenance,
    val policyVersion: PolicyVersion,
)

/**
 * 메뉴 옵션 하나 — **(태도, 리스크 노트, 근거)가 없으면 만들 수 없다**(DEC-05 무조건
 * acceptance 첫째). 다섯 인자 전부 필수이고 기본값이 없으며, 생성자는 `internal` 이라 유일한
 * 생성 경로가 [composeBidMenu] 다(`CandidateEvaluation`·`NotificationRequest` 관례 —
 * 「밖에서 지으면 '판정했다'를 위조」).
 *
 * [risk] 가 리스크 노트다 — DEC-04 커널의 판정 봉투를 **그대로** 싣는다. 새 축을 만들지
 * 않으며, 그래서 「확률이라고 부르지 않는다」가 이 타입에서도 자동으로 지켜진다
 * (낙찰 확률을 실을 칸이 없다).
 */
@ConsistentCopyVisibility
data class BidMenuOption internal constructor(
    val stance: BidStance,
    val amount: Derived<BidAmount>,
    val binding: BindingConstraint,
    val risk: FloorShortfallJudgement,
    val grounds: BidMenuGrounds,
)

/** 메뉴를 낼 수 없는 사유 — 어느 태도에서 무엇이 막혔는지를 함께 나른다. */
sealed interface MenuUnmeasurableReason {
    /** 금액 파생이 막혔다(하한 미달·overflow·출처 미선언 등 — `ReasonCode` 가 사유다). */
    data class AmountUnmeasurable(
        val stance: BidStance,
        val reason: ReasonCode,
    ) : MenuUnmeasurableReason

    /** 임계 사정률을 못 냈다(하한율 0 등) — 리스크 노트를 지어내지 않는다. */
    data class CriticalRateUnmeasurable(
        val stance: BidStance,
        val reason: ReasonCode,
    ) : MenuUnmeasurableReason

    /**
     * 후보가 하한 아래인데 [FloorBoundComparison.StrictlyAbove] 라 올릴 자리가 없다 —
     * 허용 구간이 열려 있어 **최소 원소가 없고**, 엡실론을 지어내지 않는다.
     */
    data class FloorBoundNotRepresentable(
        val stance: BidStance,
    ) : MenuUnmeasurableReason
}

/** 메뉴의 입력 자체가 없다 — 「재지 못했다」가 아니라 「잴 것이 없었다」다. */
sealed interface MenuSourceAbsence {
    data object PublishedFloorRateAbsent : MenuSourceAbsence
}

/**
 * 투찰가 메뉴의 결과(DEC-05) — **네 갈래 sealed 이고 `when` 이 전수다**(`else` 금지).
 * 밴드 붕괴가 「정상 3안」으로 위장될 수 없는 것은 그것이 **다른 타입**이기 때문이다.
 */
sealed interface BidMenuOutcome {
    /**
     * 정상 3안. 태도 셋이 각자의 자리에 있고(필드 이름이 곧 태도), 세 옵션의 [BidMenuOption.stance]
     * 가 그 자리와 일치함을 `init` 이 강제한다 — 뒤섞인 메뉴가 「정상」으로 나가지 않는다.
     *
     * [disclosures] 는 **전수여야 한다** — 고지 하나를 뺀 메뉴를 표현 불가능하게 한다.
     * 필드가 늘 같은 집합이라는 것이 요점이 아니라, 그 의무가 **값과 함께 표면까지 이동**
     * 한다는 것이 요점이다(DEC-01 의 「금액과 provenance 가 한 값으로 묶여 표시 경계까지
     * 함께 이동한다」와 같은 축).
     */
    @ConsistentCopyVisibility
    data class Offered internal constructor(
        val safe: BidMenuOption,
        val recommended: BidMenuOption,
        val aggressive: BidMenuOption,
        val disclosures: Set<BidMenuDisclosure>,
        val policyVersion: PolicyVersion,
    ) : BidMenuOutcome {
        init {
            require(safe.stance == BidStance.Safe) { "safe 자리의 태도가 어긋났다: ${safe.stance}" }
            require(recommended.stance == BidStance.Recommended) { "recommended 자리의 태도가 어긋났다: ${recommended.stance}" }
            require(aggressive.stance == BidStance.Aggressive) { "aggressive 자리의 태도가 어긋났다: ${aggressive.stance}" }
            require(disclosures == BidMenuDisclosure.entries.toSet()) { "공통 고지는 전수여야 한다: $disclosures" }
        }
    }

    /**
     * 밴드 붕괴 — 허용 상한이 하한 이하다(DEC-05 무조건 acceptance 둘째). 후보 셋의 꼭대기
     * ([upperBound])조차 하한 bound 를 만족하지 못하는 상태라, 셋을 내면 전부 같은 한 점으로
     * 접혀 「선택지」가 아니게 된다.
     */
    data class BandCollapsed(
        val lowerBound: Rate,
        val upperBound: Rate,
    ) : BidMenuOutcome

    data class Unmeasurable(
        val reason: MenuUnmeasurableReason,
    ) : BidMenuOutcome

    data class SourceAbsent(
        val reason: MenuSourceAbsence,
    ) : BidMenuOutcome
}
