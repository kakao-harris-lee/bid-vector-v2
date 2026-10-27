package bidvector.decision.menu

import bidvector.decision.FloorShortfallJudgement
import bidvector.decision.PlausibleFloorRate
import bidvector.sharedkernel.BidAmount
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Derived
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.ReasonCode
import java.util.Collections

/**
 * 최종 추천가를 **무엇이 정했는가**(`capability-map.md` DEC-02 무조건 acceptance 첫째 —
 * 「최종 추천가가 어느 제약에 binding 됐는지가 결과에 실린다」).
 *
 * 값마다 **다른 제약**이라 두 제약이 한 값으로 접히지 않는다 — 그것이 이 타입이 지키는
 * 성질이고 변이 test 가 그 성질을 잰다.
 *
 * 「제약을 지우면 결과가 바뀐다」는 binding 의 **충분조건이지 필요조건이 아니다**(r4 V4-L2).
 * 경계 등가(후보 율 == 하한율)는 하한을 지워도 값이 그대로지만 [PublishedFloorBound] 다 —
 * 「값이 제약 **위에 올라앉아** 있다」를 「제약이 안 걸렸다」와 구별하기로 한 정의다.
 *
 * DEC-02 의 **잠정** bullet(예산 상한 · 계약 역전 미채택, `OPEN-DEC-03` 재결정 대상)에
 * 해당하는 값은 **두지 않는다** — 생산자 없는 variant 는 소비자 없는 필드와 같은 결함이다
 * (`OPEN-4B5-COMPETITIVENESS` 가 같은 이유로 `DerivationPolicyData` 에 필드를 두지 않았다).
 */
sealed interface BindingConstraint {
    /** 후보 비율 자신이 값을 정했다 — 걸린 제약이 없다. */
    data object CandidateRate : BindingConstraint

    /**
     * 게시 하한율에서 읽은 **투찰율 하한**이 값을 정했다 — **율 축**이다(후보가 그 하한에
     * 걸렸거나 정확히 그 위다).
     *
     * **clamp 뒤 금액 올림이 겹쳐 발화해도 이 값을 유지한다**(D-G1DEC05-7 — 두 축이 함께
     * 걸리면 율 축이 이긴다). 율이 이미 하한에 걸린 옵션에 [PublishedFloorRounding] 을 달면
     * 「율은 여유가 있었다」로 읽혀 거짓이 된다.
     */
    data object PublishedFloorBound : BindingConstraint

    /**
     * 게시 하한이 **금액 축**에서 값을 정했다(r3 N-1) — 후보 율은 하한 **위**였는데, 원 단위
     * 자연 반올림이 적용 하한을 밑돌아 **제약을 만족하는 최소 정수로 올라간** 경우다.
     *
     * [CandidateRate] 로 적으면 거짓이다. 「걸린 제약이 없다」가 [CandidateRate] 의 뜻인데,
     * 이 경로는 **하한을 빼면 결과가 달라진다**(실측: 기초금액 1,234,567 · 하한율 0.87995 ·
     * 후보 0.8799501 에서 하한이 있으면 1,086,358 원, 없으면 1,086,357 원).
     *
     * 「제약을 지웠을 때 결과가 바뀐다」는 binding 의 **충분조건이지 필요조건이 아니다**
     * (r4 V4-L2). **경계 등가**가 그 예외다 — 후보 율이 하한율과 정확히 같으면 하한을 지워도
     * 율도 금액도 그대로지만 [PublishedFloorBound] 다. 「값이 제약 **위에 올라앉아** 있다」를
     * 「제약이 안 걸렸다」와 구별하기로 한 r2 의 정의이고, 변이 `M3P` 가 그 정의를 잠근다.
     *
     * [PublishedFloorBound] 와 **다른 값**인 이유는 **어느 축에서 걸렸는가**가 다르기 때문이다 —
     * 저쪽은 율이 하한에 걸렸고, 이쪽은 율은 후보가 정했는데 **금액이** 하한으로 올라갔다.
     * 둘을 한 값으로 접으면 「율이 하한 미만이었다」와 「율은 여유가 있었다」가 구별되지 않는다.
     */
    data object PublishedFloorRounding : BindingConstraint

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
 *
 * **[bidRate] 의 `origin` 은 읽지 말 것**(라운드 2 MEDIUM-6). 메뉴는 `BidRate.recommended(…)`
 * 로 값을 만드는데, 그 `BidRateOrigin.Recommended` 는 **산술 진입에 필요한 자리채움**이지 이
 * 값의 실제 출처가 아니다 — 하한에 걸려 올라간 값도, 읽기 미정이라 제약 없이 지나온 값도 같은
 * `origin` 을 단다. **이 옵션의 값이 무엇 때문에 그 값인지는 [BidMenuOption.binding] 이
 * 유일한 출처다.** `shared-kernel` 의 `BidRateOrigin` 에 variant 를 더하지 않는다(그 타입은
 * 관측값/추천값을 가르는 다른 축이고, 이 slice 가 그 어휘를 넓힐 근거가 없다) — 알려진 제한.
 */
data class BidMenuGrounds(
    val bidRate: BidRate,
    /**
     * **게이트 통과 값이다**(DEC-10, 계약 갱신 in_scope 추가) — 원값 `FloorRate` 가 아니다.
     * 이 칸은 「이 가격이 어느 하한율에서 나왔는가」를 말하므로, 가격에 실제로 쓴 값과 **같은
     * 타입**이어야 근거가 정직하다. 착수 census 가 이 자리를 찾았다: 가격 경로가 만든 출력이
     * 원값을 그대로 내보내고 있었고, 그러면 소비자(표시·감사)가 게이트를 거치지 않은 값을
     * 손에 쥘 수 있다.
     */
    val publishedFloorRate: PlausibleFloorRate,
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

    /**
     * **다른 태도와 제출 금액이 같아** 선택지가 되지 못했다(r2 M-1 · M-3).
     *
     * 운영자가 보는 것은 율이 아니라 **반올림 뒤 금액**이므로 같음의 판정도 금액 축이다 —
     * 율이 1/기초금액 미만으로 다르면 두 옵션은 **같은 원화 금액**이 되고, 그것은 선택지가
     * 아니라 같은 값 둘이다. 겹친 태도만 이 사유로 보류되고 **겹치지 않은 유효한 옵션은
     * 그대로 제시된다**(한 태도를 못 낸다고 나머지를 버리지 않는다는 [BidMenuOutcome.PartiallyOffered]
     * 의 원칙을 붕괴 축에도 적용한다).
     */
    @ConsistentCopyVisibility
    data class CollapsedWithOtherStances private constructor(
        val stance: BidStance,
        val amountWon: Long,
        /** **이미 가둔 사본이 들어온다** — 생성 경로가 [invoke] 하나뿐이라 그것이 보장된다. */
        val otherStances: Set<BidStance>,
    ) : MenuUnmeasurableReason {
        companion object {
            /**
             * 유일한 생성 경로 — 결과 그래프가 싣는 컬렉션은 **전부** 가둔 사본이어야 한다
             * (r3 N-2). 이 하나가 `frozen()` 밖에 있어서, `withheld` 맵이 `unmodifiableMap`
             * 인데도 그 **값** 안의 집합은 캐스팅 한 줄로 비울 수 있었다 — 아래 `init` 불변식
             * 둘이 사후에 무너지고 담은 결과의 `hashCode` 가 움직인다.
             */
            operator fun invoke(
                stance: BidStance,
                amountWon: Long,
                otherStances: Set<BidStance>,
            ): CollapsedWithOtherStances = CollapsedWithOtherStances(stance, amountWon, frozen(otherStances))
        }

        init {
            require(otherStances.isNotEmpty()) { "겹친 상대가 없으면 붕괴가 아니다: $stance" }
            require(stance !in otherStances) { "자기 자신과 겹칠 수 없다: $stance" }
        }
    }
}

/**
 * 메뉴의 입력 자체가 없다 — 「재지 못했다」가 아니라 「잴 것이 없었다」다.
 *
 * DEC-10 이 사유 둘을 더한다(D-DEC10-3) — 개연 불가와 심판 아님은 **부재와 같은 가지로
 * 떨어지되 같은 사유가 아니다**. 셋을 한 사유로 합치면 「원문에 하한이 없었다」와 「게이트가
 * 걸러냈다」가 구별되지 않고, 그것이 DEC-10 acceptance 셋째가 금지하는 침묵 스킵이다.
 */
sealed interface MenuSourceAbsence {
    data object PublishedFloorRateAbsent : MenuSourceAbsence

    data object PublishedFloorRateImplausible : MenuSourceAbsence

    data object PublishedFloorRateOriginNotJudged : MenuSourceAbsence
}

/**
 * 밴드가 무너진 이유(DEC-05 무조건 acceptance 둘째) — **두 출처를 가른다.**
 *
 * 라운드 1 은 [FloorAboveBand] 하나만 쟀고, 그래서 **후보 밴드 자신이 무너진 경우**(ML 이
 * 같은 값을 둘 이상 냈거나 clamp 가 둘 이상을 하한으로 모은 경우)가 「정상 3안」으로 나갈 수
 * 있었다(라운드 2 HIGH-2). 값이 같은 두 옵션은 **선택지가 아니다.**
 *
 * 이 타입은 **태도 전부가 한 금액으로 접힌 경우**만 싣는다 — 일부만 겹치면 겹친 태도가
 * [MenuUnmeasurableReason.CollapsedWithOtherStances] 로 보류되고 나머지는 제시된다(r2 M-1).
 */
sealed interface CollapseCause {
    /** 후보 셋의 꼭대기([StanceRates.safe])조차 하한 bound 를 만족하지 못한다. */
    data class FloorAboveBand(
        val lowerBound: Rate,
        val upperBound: Rate,
    ) : CollapseCause

    /**
     * **제출 금액이 겹친다** — 입력 후보가 이미 같았거나, 하한 clamp 가 둘 이상을 같은 값으로
     * 모았거나, 율이 달라도 **반올림 뒤 원화가 같아진** 경우다.
     *
     * 판정 축이 율이 아니라 금액인 이유(r2 M-3): 운영자가 보고 제출하는 것은 금액이고, 율
     * 차이가 1/기초금액 미만이면 두 옵션은 **같은 금액**이다. 율 축으로만 재면 그 입력이
     * 「정상 3안」으로 나간다. 또 하한 올림이 발화하면 결과가 언제나 `ceil(적용 하한)` 이라
     * 그 경로의 겹침은 **율 축에서는 보이지 않는다**.
     */
    data class DuplicateEffectiveAmounts(
        val amountWon: Long,
    ) : CollapseCause
}

/**
 * 투찰가 메뉴의 결과(DEC-05) — **다섯 갈래 sealed 이고 `when` 이 전수다**(`else` 금지).
 * 밴드 붕괴도, 일부만 제시한 메뉴도 「정상 3안」으로 위장될 수 없는 것은 그것들이 각각
 * **다른 타입**이기 때문이다 — [Offered] 는 셋 전부가 선 경우에만 지어진다.
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
    data class Offered private constructor(
        val safe: BidMenuOption,
        val recommended: BidMenuOption,
        val aggressive: BidMenuOption,
        /** **이미 가둔 사본이 들어온다** — 생성 경로가 [invoke] 하나뿐이라 그것이 보장된다. */
        val disclosures: Set<BidMenuDisclosure>,
        val policyVersion: PolicyVersion,
    ) : BidMenuOutcome {
        internal companion object {
            /**
             * 유일한 생성 경로 — **primary 값 자체를 가둔 사본으로 만든다**(r2 M-4 · ③′).
             *
             * 가둔 뷰를 따로 파생하는 것만으로는 부족했다: data class 의 `equals`·`hashCode` ·
             * `toString` 은 **primary 생성자의 값**을 읽으므로, 그 자리에 호출자의 원본 참조가
             * 남아 있으면 생성 뒤 원본이 바뀔 때 공개 뷰는 그대로인데 `equals`·`hashCode` 가
             * 따라 움직인다(해시 컬렉션이 길을 잃는다). 사본을 primary 자리에 넣어 그 표류를
             * 없앤다. 생성자가 `private` 이라 `@ConsistentCopyVisibility` 가 `copy` 도 닫는다.
             */
            internal operator fun invoke(
                safe: BidMenuOption,
                recommended: BidMenuOption,
                aggressive: BidMenuOption,
                disclosures: Set<BidMenuDisclosure>,
                policyVersion: PolicyVersion,
            ): Offered = Offered(safe, recommended, aggressive, frozen(disclosures), policyVersion)
        }

        init {
            require(safe.stance == BidStance.Safe) { "safe 자리의 태도가 어긋났다: ${safe.stance}" }
            require(recommended.stance == BidStance.Recommended) { "recommended 자리의 태도가 어긋났다: ${recommended.stance}" }
            require(aggressive.stance == BidStance.Aggressive) { "aggressive 자리의 태도가 어긋났다: ${aggressive.stance}" }
            require(disclosures == BidMenuDisclosure.entries.toSet()) { "공통 고지는 전수여야 한다: $disclosures" }
        }
    }

    /**
     * 태도 **일부만** 제시한다(라운드 2 HIGH-3) — 한 태도를 낼 수 없다고 메뉴 전체를 죽이지
     * 않는다. 어느 태도가 **왜** 빠졌는지가 [withheld] 에 실린다.
     *
     * [Offered] 와 **다른 타입**인 것이 요점이다 — 「정상 3안」은 셋 전부일 때만이고, 둘짜리
     * 메뉴가 그 이름으로 나가지 않는다(DEC-05 무조건 acceptance 둘째의 정신).
     */
    @ConsistentCopyVisibility
    data class PartiallyOffered private constructor(
        val offered: Map<BidStance, BidMenuOption>,
        val withheld: Map<BidStance, MenuUnmeasurableReason>,
        val disclosures: Set<BidMenuDisclosure>,
        val policyVersion: PolicyVersion,
    ) : BidMenuOutcome {
        internal companion object {
            /** 유일한 생성 경로 — 셋 다 가둔 사본으로 받는다([Offered.invoke] 와 같은 이유). */
            internal operator fun invoke(
                offered: Map<BidStance, BidMenuOption>,
                withheld: Map<BidStance, MenuUnmeasurableReason>,
                disclosures: Set<BidMenuDisclosure>,
                policyVersion: PolicyVersion,
            ): PartiallyOffered =
                PartiallyOffered(frozen(offered), frozen(withheld), frozen(disclosures), policyVersion)
        }

        init {
            require(offered.isNotEmpty()) { "PartiallyOffered 는 제시 옵션이 하나 이상이어야 한다(0 이면 Unmeasurable)" }
            require(withheld.isNotEmpty()) { "PartiallyOffered 는 보류가 하나 이상이어야 한다(0 이면 Offered)" }
            require(offered.keys.intersect(withheld.keys).isEmpty()) { "한 태도가 제시·보류 양쪽에 있을 수 없다" }
            require(offered.keys + withheld.keys == BidStance.entries.toSet()) { "태도 전수를 덮어야 한다" }
            require(offered.all { (stance, option) -> option.stance == stance }) { "제시 옵션의 태도가 키와 어긋났다" }
            require(disclosures == BidMenuDisclosure.entries.toSet()) { "공통 고지는 전수여야 한다: $disclosures" }
        }
    }

    /**
     * 밴드 붕괴 — 선택지가 서지 않는다(DEC-05 무조건 acceptance 둘째). [cause] 가 출처를
     * 가르고, [collapsedStances] 는 같은 값으로 접힌 태도들이다(하한 유래면 전수).
     */
    @ConsistentCopyVisibility
    data class BandCollapsed private constructor(
        val cause: CollapseCause,
        val collapsedStances: Set<BidStance>,
    ) : BidMenuOutcome {
        companion object {
            /** 생성 표면은 **public 그대로**다(실패 셋은 어디서든 지을 수 있다) — 사본만 가둔다. */
            operator fun invoke(
                cause: CollapseCause,
                collapsedStances: Set<BidStance>,
            ): BandCollapsed = BandCollapsed(cause, frozen(collapsedStances))
        }

        init {
            require(collapsedStances.isNotEmpty()) { "붕괴한 태도 집합이 비어 있을 수 없다" }
        }
    }

    /**
     * 태도 **전부**를 낼 수 없다 — 사유를 태도별로 싣는다(라운드 2 HIGH-3). 하나로 뭉치면
     * 「어느 태도가 왜」가 사라진다.
     */
    @ConsistentCopyVisibility
    data class Unmeasurable private constructor(
        val withheld: Map<BidStance, MenuUnmeasurableReason>,
    ) : BidMenuOutcome {
        companion object {
            /** 생성 표면은 **public 그대로**다 — 사본만 가둔다. */
            operator fun invoke(withheld: Map<BidStance, MenuUnmeasurableReason>): Unmeasurable =
                Unmeasurable(frozen(withheld))
        }

        init {
            require(withheld.keys == BidStance.entries.toSet()) { "전부 보류일 때만 Unmeasurable 이다: ${withheld.keys}" }
        }
    }

    data class SourceAbsent(
        val reason: MenuSourceAbsence,
    ) : BidMenuOutcome
}

/**
 * 결과 타입이 싣는 컬렉션을 **사본으로 바꿔 가둔다**(r2 M-4 · ③′).
 *
 * `toSet()`·`toMap()` 만으로는 부족하다 — 돌아오는 것이 `LinkedHashSet`/`LinkedHashMap` 이라
 * 여전히 `as MutableSet` 로 캐스팅해 비울 수 있다. 사본을 뜨고(호출자의 원본과 끊고) 읽기
 * 전용으로 감싸는 두 가지를 함께 해야 닫힌다.
 */
private fun <T> frozen(values: Set<T>): Set<T> = Collections.unmodifiableSet(values.toSet())

private fun <K, V> frozen(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(values.toMap())
