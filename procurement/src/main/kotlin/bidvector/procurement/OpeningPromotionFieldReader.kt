package bidvector.procurement

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import java.math.BigDecimal
import java.time.Instant

/**
 * 금액 축 한 칸의 재료 — 값과 **계약이 말하는 나머지 셋**이다(통화·과세·provenance). 금액 값 객체
 * 셋(`AwardAmount`·`YegaAmount`·`BaseAmount`)이 생성자 모양만 다르고 재료가 같아, 세 벌의 거의 같은
 * 조립 함수를 두는 대신 재료를 한 번 읽고 호출부가 자기 타입을 짓는다.
 */
internal class FieldMoney(
    val won: Long,
    val currency: Currency,
    val vatTreatment: VatTreatment,
    val provenance: Provenance,
)

/**
 * 고른 걷기의 행들을 **개념으로** 읽는 자리(M7/7C⑤-0 계약 판독 층). 한 개념을 여러 축이 나르면
 * **늦게 관측된 축의 값이 이긴다**(「최신 관측 우선」 — 이 표의 권위 계층이 그것 하나다). 같은
 * 시각이면 축 열거 순서로 결정적으로 고른다.
 *
 * 값을 얻으면 **그 값으로 끝낸다** — 해석되지 않는 최신 값을 건너뛰고 더 오래된 축의 값을 집으면,
 * 깨진 새 관측이 낡은 값에 가려진다. 빈 문자열은 값이 아니라 부재다(상류가 옵션을 그렇게 낸다).
 */
internal class FieldReader(
    private val selected: Map<SourceEndpoint, List<OpeningAxisObservation>>,
    private val policy: KonepsCollectionPolicyData,
    private val round: NoticeRound,
) {
    fun textOf(concept: FieldConcept): String? = readingsOf(concept).firstOrNull()?.text

    fun countOf(concept: FieldConcept): Int? =
        readingsOf(concept)
            .firstOrNull()
            ?.text
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }

    /** 원문 percent → canonical fraction. 값 객체 불변식 위반과 표현 불가 지수는 부재로 접는다. */
    fun rateOf(concept: FieldConcept): Rate? {
        val numeric =
            readingsOf(concept)
                .firstOrNull { it.contract.scale == FieldScale.PERCENT }
                ?.text
                ?.trim()
                ?.removePrefix("+")
                ?.toBigDecimalOrNull() ?: return null
        return try {
            Rate.ofPercent(numeric)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * 금액 축 — 원 단위 정수만 받고, 통화·과세·provenance 는 **계약에서** 온다(리터럴로 짓지 않는다).
     * 소수부는 깎지 않고 부재다(깎으면 원이 조용히 사라진다).
     */
    fun moneyOf(concept: FieldConcept): FieldMoney? =
        readingsOf(concept)
            .firstOrNull { it.contract.scale == FieldScale.WON_INTEGER }
            ?.let { reading ->
                wonOf(reading.text)?.let { won ->
                    FieldMoney(
                        won = won,
                        currency = currencyForWonUnit(reading.contract.unit),
                        vatTreatment = reading.contract.vatTreatment,
                        provenance = provenanceOf(reading.contract),
                    )
                }
            }

    /** 일시는 계약의 `sourceZone` 규칙으로만 해석한다 — 기존 진입점을 그대로 부른다. */
    fun instantOf(concept: FieldConcept): Instant? =
        observationsIn(concept)
            .asSequence()
            .map { observation -> instantFrom(observation, policy, concept) }
            // 값이 있는데 해석되지 않았다면 **더 오래된 축으로 물러서지 않는다** — 첫 비부재가 답이다.
            .firstOrNull { it != InstantResolutionOutcome.Absent }
            ?.let { (it as? InstantResolutionOutcome.Resolved)?.instant }

    private fun provenanceOf(contract: KonepsFieldContract): Provenance =
        when (contract.provenanceTemplate) {
            FieldProvenanceTemplate.PUBLISHED -> Provenance.Published(round)

            FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY -> Provenance.FilledFromBudgetKey(contract.rawName.name)

            // 계약이 출처를 선언하지 않은 값 — 지어내지 않고 「선언되지 않았다」로 나른다.
            FieldProvenanceTemplate.NOT_APPLICABLE -> Provenance.Undeclared
        }

    /**
     * 그 개념의 값을 낸 (값, 계약) 쌍들 — **늦은 관측이 앞**이다. 계약은 그 관측이 온 축을
     * `presentIn` 에 가진 것만 쓴다: 한 개념을 여러 축이 **다른 키**로 나르므로(진행구분이 그렇다)
     * 첫 계약만 쓰면 다른 축의 값이 조용히 사라진다.
     */
    private fun readingsOf(concept: FieldConcept): List<FieldReading> {
        val contracts = policy.fieldContracts.contractsFor(concept)
        return observationsIn(concept).flatMap { observation ->
            contracts
                .filter { observation.sourceEndpoint in it.presentIn }
                .mapNotNull { contract ->
                    observation.valueOf(contract)?.takeUnless(String::isBlank)?.let { FieldReading(it, contract) }
                }
        }
    }

    /**
     * 선언되지 않은 개념을 물으면 **큰 소리로 멈춘다**(`getValue`) — 그것은 원문 값 유래 실패가 아니라
     * 선언 누락이고, 조용히 빈 목록을 내면 그 슬롯이 영원히 비는 것을 아무도 보지 못한다.
     */
    private fun observationsIn(concept: FieldConcept): List<RawNoticeObservation> {
        val axes = OPENING_PROMOTION_SLOT_AXES.getValue(concept)
        val ordered =
            selected.entries
                .filter { it.key in axes }
                .flatMap { (endpoint, rows) -> rows.map { endpoint to it.observation } }
        return ordered.sortedWith(LATEST_OBSERVATION_FIRST).map { it.second }
    }

    private class FieldReading(
        val text: String,
        val contract: KonepsFieldContract,
    )
}

/**
 * 원 단위 정수만 받는다 — **소수부는 깎지 않고 부재**다(깎으면 원이 조용히 사라진다). 음수도
 * 부재다(금액 값 객체의 불변식을 예외로 만나지 않는다).
 */
private fun wonOf(raw: String): Long? =
    runCatching { BigDecimal(raw.trim().replace(",", "")) }
        .getOrNull()
        ?.stripTrailingZeros()
        ?.takeIf { it.scale() <= 0 }
        ?.let { whole -> runCatching { whole.longValueExact() }.getOrNull() }
        ?.takeIf { it >= 0L }

/**
 * **늦은 관측이 앞, 같은 시각이면 축 열거 순서**. `compareByDescending`·`thenBy` 를 쓰지 않는다 —
 * 그 둘은 inline 이라 비교자 클래스가 stdlib 소스로 찍히고, 그러면 산출물의 모든 클래스가 게이트를
 * 지난 소스에서 나와야 한다는 `jarContentGate` 를 깬다(실측).
 */
private val LATEST_OBSERVATION_FIRST =
    Comparator<Pair<SourceEndpoint, RawNoticeObservation>> { left, right ->
        val byObservedAt = right.second.observedAt.compareTo(left.second.observedAt)
        if (byObservedAt != 0) byObservedAt else left.first.ordinal.compareTo(right.first.ordinal)
    }

/** 통화는 계약의 unit 에서 온다 — 리터럴로 짓지 않는다(공고 축 `canonicalize` 와 같은 자리). */
private fun currencyForWonUnit(unit: FieldUnit): Currency =
    when (unit) {
        FieldUnit.WON -> Currency.KRW
        else -> error("금액 축 계약의 unit은 WON이어야 한다: $unit")
    }
