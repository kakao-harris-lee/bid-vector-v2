package bidvector.decision.menu

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.MONEY_AXIS_SCALE_DIGITS
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.RoundingPolicy
import java.math.RoundingMode

/**
 * 경계 등가의 처분(`ShortfallComparison` 관례, D-3 · decision 28) — 투찰율이 하한 bound 와
 * **정확히 같을 때** 허용인가. 율 축에만 걸리는 어휘다. 금액 축의 보증은 DEC-02 무조건
 * acceptance 문면 그대로 「하한 **이상**」이다(`UnroundedBidAmount.roundedWith`).
 */
enum class FloorBoundComparison {
    AtOrAbove,
    StrictlyAbove,
}

/**
 * 게시 낙찰하한율을 **투찰율 하한으로 읽을 것인가** — 운영자 결정이다
 * (`OPEN-DEC05-FLOOR-RATE-READING`).
 *
 * 왜 결정이 필요한가: 게시 하한율은 **예정가격 기준** 율이고 투찰율은 **기초금액 기준**이다
 * (`data-dictionary.md` §1.4.2 · DEC-04 분류 근거). 두 축을 잇는 정직한 다리는
 * 임계 사정률(`투찰율 ÷ 하한율`) 하나뿐이고, 임계가 1 미만이면 그 후보는 실현 사정률이 1
 * 이상인 어떤 추첨에서도 하한 미달이다. 「하한율을 투찰율 하한으로 그대로 읽는다」는 그
 * 사실을 하드 제약으로 승격하는 해석이며, 승격 여부와 경계 등가의 처분은 **값이 아니라
 * 도메인 판단**이라 이 타입이 정하지 않는다.
 *
 * [Undecided] 에서 커널은 **표식형**이다 — 하한 제약을 적용한 「척」하지 않고
 * [BindingConstraint.FloorRateReadingUndecided] 로 미결을 결과에 **싣지만, 결과를 막지는
 * 않는다.** 「fail-closed」라고 부르지 않는다(라운드 2 M-1) — 제약 없이 계산된 메뉴가 그대로
 * 나가므로 닫히는 것은 **주장**이지 **출력**이 아니다. 그 표식을 읽고 처분하는 것은 표면의
 * 의무이고, 7C② 계약 조건으로 넘긴다: 「이 binding 이 붙은 옵션을 추천으로 띄우지 않는다」.
 */
sealed interface FloorRateReading {
    data object Undecided : FloorRateReading

    data class AsBidRateLowerBound(
        val comparison: FloorBoundComparison,
    ) : FloorRateReading
}

/**
 * 투찰가 메뉴 정책(DEC-05) — **값을 이 타입이 지어내지 않는다**(`FloorShortfallPolicyData`
 * 관례). legacy 의 매직 셋(`_BASE_ADJUSTMENT`·`_DISPERSION_REFERENCE`·`_MAX_ADJUSTMENT`)에
 * 대응하는 슬롯이 **없다** — 메뉴는 태도별 간격을 스스로 만들지 않고 받은 후보 셋
 * ([StanceRates])을 태도에 대응시킬 뿐이라 그 수치가 필요 없다(DEC-05 「legacy 형태 처리」
 * 폐기 지시의 집행).
 */
data class BidMenuPolicyData(
    val floorRateReading: FloorRateReading,
    val amountRounding: RoundingPolicy,
)

/**
 * 출하 정본. **[FloorRateReading.Undecided] 가 오늘의 정직한 값이다** — 운영자가 아직
 * 결정하지 않았고(`OPEN-DEC05-FLOOR-RATE-READING`), 미정을 임의 해석으로 메우지 않는다.
 *
 * `amountRounding` 의 모드는 **새 값이 아니다** — 같은 축(기초금액×투찰율 → 투찰가)의 출하
 * 값이 `OPPORTUNITY_POLICY.recommendedAmountRounding` 에 이미 선언돼 있어 그것과 같은 값을
 * 쓴다. 두 자리가 갈리지 않게 하려는 것이고, 모드 자체의 미결은 여전히 `OPEN-DIC-10` 이
 * 소유한다. `scaleDigits` 는 금액 축 정의상 0 이다(`data-dictionary.md` §1.1 정의 ①).
 */
val BID_MENU_POLICY: EffectiveDatedPolicy<BidMenuPolicyData> =
    EffectiveDatedPolicy(
        source = "M6/G1-DEC05 — floorRateReading 은 OPEN-DEC05-FLOOR-RATE-READING 미결, 반올림 모드는 OPEN-DIC-10",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    BidMenuPolicyData(
                        floorRateReading = FloorRateReading.Undecided,
                        amountRounding = RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, RoundingMode.HALF_UP),
                    ),
            ),
    )

/**
 * 허용 집합의 **최소 원소** — 하한에 걸린 후보를 어디로 올릴 것인가.
 * [FloorBoundComparison.StrictlyAbove] 에는 최소 원소가 없다(열린 구간) — `null` 을 내고
 * **엡실론을 지어내지 않는다**. 그 경우 하한 아래 후보는 구제되지 않고 사유 있는 실패가 된다.
 */
internal fun clampTargetOf(
    bound: Rate,
    comparison: FloorBoundComparison,
): Rate? =
    when (comparison) {
        FloorBoundComparison.AtOrAbove -> bound
        FloorBoundComparison.StrictlyAbove -> null
    }

/** 율 축 허용 판정 — 경계 등가의 처분만 정책이 가른다. */
internal fun admits(
    rate: Rate,
    bound: Rate,
    comparison: FloorBoundComparison,
): Boolean =
    when (comparison) {
        FloorBoundComparison.AtOrAbove -> rate >= bound
        FloorBoundComparison.StrictlyAbove -> rate > bound
    }
