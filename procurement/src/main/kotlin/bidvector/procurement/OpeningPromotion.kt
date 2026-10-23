package bidvector.procurement

import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.YegaAmount
import java.math.BigDecimal
import java.time.Instant

/**
 * 이미 적재된 개찰 축 원문 하나와 그 행의 저장 키(M7/7C⑤-0) — 값 열람은 **이 모듈 안**이다
 * (`internal val`). 적재 어댑터는 짓기만 하고, 승격 use case 는 이 타입의 원문을 열 수 없다 —
 * 키를 고르는 일은 계약을 소유한 이 모듈의 몫이라는 규율을 타입이 진다.
 */
class OpeningAxisObservation(
    internal val observation: RawNoticeObservation,
    internal val observationKey: ObservationKey,
)

/**
 * 공고 하나의 개찰 축 원문 묶음 — [promoteOpeningResult] 의 입력. 묶는 일(식별자 판독)은 읽기
 * 어댑터가 하고, 어느 걷기를 쓸지와 무엇이 canonical 이 되는지는 이 모듈이 정한다.
 */
class OpeningAxisGroup(
    val noticeId: NoticeId,
    internal val rows: List<OpeningAxisObservation>,
)

/**
 * 개찰 축 원문 읽기 port(D-7C5P-6) — **읽기 메서드 하나뿐이다.** 쓰기 통로가 없고, 승격이
 * 원문을 고쳐 쓰는 경로가 구조적으로 서지 않는다.
 */
interface OpeningRawAxisSource {
    /**
     * [collectedSince] **이후에 적재된**, 마스킹 표지가 없는 개찰 축 원문을 공고 단위로 묶어 낸다.
     *
     * 시각 하한은 보존 창이다(D-7C5P-3) — 마스킹을 눈앞에 둔 원문을 승격하면 업체 식별 값의
     * canonical 사본이 최초 수집 기준 지평을 두 배까지 넘길 수 있다. 하한을 두면 승격 지연이 보존
     * 창 이내로 묶이고, 상호의 잔존 상한이 「최초 수집 + 보존 창 + 승격 지연」 이하가 된다.
     */
    fun readOpeningAxes(collectedSince: Instant): OpeningRawAxisRead
}

/**
 * 한 번 읽은 결과 — 묶인 공고들과 **버린 행의 사유별 계수**. 버림을 조용히 지나가면 「왜 승격이
 * 비었나」를 물을 자리가 없다(6G 의 판독 불가 행 계수와 같은 자리).
 */
data class OpeningRawAxisRead(
    val groups: List<OpeningAxisGroup>,
    /** 공고 식별자나 축 어휘가 서지 않아 키를 갖지 못한 행. */
    val unkeyedRows: Int = 0,
    /** 마스킹 표지가 찍혀 제외한 행 — 승격하면 반쪽 canonical 이 된다. */
    val maskedRows: Int = 0,
    /** 보존 창보다 오래돼 제외한 행. */
    val expiredRows: Int = 0,
)

/**
 * (공고, 축)에서 **쓸 걷기**(D-6G-68 · D-6G-80) — 원장의 마지막 AXIS 결말 줄이 가리킨다.
 * 「선택하지 못했다」가 조용한 통과가 아니라 값이다.
 */
internal sealed interface AxisWalk {
    /** 이 걷기의 행만 쓴다 — 다른 걷기의 행은 버린다(합치지 않는다). */
    data class Use(
        val walk: Instant,
    ) : AxisWalk

    /** 결말이 빈 응답이다 — **0 행**이다(앞선 잘린 걷기의 행을 쓰지 않는다, D-6G2d-4 ⓑ). */
    data object NoRows : AxisWalk

    /** 실패·미완으로 끝났다 — 그 걷기의 행으로 canonical 을 쓸 수 없다. */
    data object Unusable : AxisWalk
}

/**
 * 걷기 선택의 **유일한 자리**. 원장 줄이 있으면 그 줄이 가리킨 걷기이고, 줄이 **없는** 축
 * (목록 축)만 가장 늦은 관측 시각으로 물러선다(D-6G-58 r4-d). 행 존재로도, 적재 순서로도
 * 고르지 않는다 — 전자는 잘린 걷기를 완료로 읽고, 후자는 backfill 이 뒤집는다.
 *
 * 목록 축의 물러섬은 `OPEN-6G-LIST-AXIS-WALK-SELECTION` 을 **상속한다**(이 slice 가 닫지 않는다).
 */
internal fun axisWalkOf(
    conclusion: AxisConclusion?,
    observedAtValues: List<Instant>,
): AxisWalk =
    when {
        conclusion == null -> observedAtValues.maxOrNull()?.let(AxisWalk::Use) ?: AxisWalk.NoRows
        conclusion.outcome == AttemptOutcome.Empty -> AxisWalk.NoRows
        !conclusion.usesRows -> AxisWalk.Unusable
        else -> AxisWalk.Use(conclusion.walk)
    }

/** 승격 한 건의 결과 — 소진 `when` 으로 소비한다(버림을 조용히 삼키지 않는다). */
sealed interface OpeningPromotionOutcome {
    /** canonical 로 쓸 수 있다 — 저장 키는 기여한 걷기들의 관측 키에서 결정적으로 고른다. */
    data class Promotable(
        /** 저장 왕복 전에 저장소를 물을 때 쓴다 — use case 가 canonical fact 를 열지 않아도 된다. */
        val noticeId: NoticeId,
        val result: OpeningResult,
        val observationKey: ObservationKey,
    ) : OpeningPromotionOutcome

    /** 쓸 걷기가 있는 축이 하나도 없다 — 행을 만들지 않는다(반쪽 원문으로 쓰지 않는다). */
    data object NoUsableAxis : OpeningPromotionOutcome
}

/**
 * 원문 → canonical 개찰 fact 사상(M7/7C⑤-0) — **유일한 변환 지점**이고 순수하다.
 *
 * 값은 **개념 경유**로만 읽는다(이 파일에 raw 키 리터럴이 없다). 그래서 상류가 어느 개념에 키를
 * 더하거나, 아직 걷지 않는 축(낙찰 목록)의 원문이 뒤에 들어와도 **코드 변경 없이** 따라온다
 * (D-7C5P-1) — 슬롯마다 어느 오퍼레이션의 관측인지만 [OPENING_PROMOTION_SLOT_AXES] 가 선언한다.
 *
 * **원문 값이 무엇이든 던지지 않는다.** 값 유래 실패는 슬롯 부재이고, 0·빈 문자열로 적재하지
 * 않는다. 소수부가 섞인 금액은 깎지 않고 부재다.
 */
fun promoteOpeningResult(
    group: OpeningAxisGroup,
    conclusions: Map<SourceEndpoint, AxisConclusion>,
    policy: KonepsCollectionPolicyData,
): OpeningPromotionOutcome {
    val selected = selectedRowsOf(group, conclusions)
    if (selected.isEmpty()) return OpeningPromotionOutcome.NoUsableAxis
    val reader = FieldReader(selected, policy, group.noticeId.round)
    val contributing = selected.values.flatten()
    return OpeningPromotionOutcome.Promotable(
        noticeId = group.noticeId,
        result =
            OpeningResult(
                noticeId = group.noticeId,
                winningRate = reader.rateOf(FieldConcept.WINNING_RATE),
                // 역산 파생은 3D 소유다 — 승격이 짓지 않는다.
                derivedBaseAmount = null,
                observedAt = contributing.maxOf { it.observation.observedAt },
                finalAwardAmount = awardAmountOf(reader),
                finalAwardCompanyName = reader.textOf(FieldConcept.AWARD_COMPANY_NAME),
                participantCount = reader.countOf(FieldConcept.PARTICIPANT_COUNT),
                progressDivision = reader.textOf(FieldConcept.PROGRESS_DIVISION),
                plannedPrice = plannedPriceOf(reader),
                baseAmount = openingBaseAmountOf(reader),
                // 총예가건수(계약 미등재)는 투영에 없어 **구조적으로 부재**다.
                totalReservePriceCandidateCount = null,
                actualOpeningAt = reader.instantOf(FieldConcept.ACTUAL_OPENING_AT),
                reservePrices = reservePriceRowsOf(selected, policy, group.noticeId.round),
                openingRankOne = rankOneOf(selected, policy, group.noticeId.round),
                // D-7C5P-2 — 승인 결정 둘이 어긋난다(D-3F-4 (a) ↔ D-6G-38). 이 축은 승격하지 않는다.
                drawNumbers = DrawNumberObservation.NotObserved,
            ),
        // 감사 축·신선도 표지 — **부모 관측 시각을 낸 걷기**의 행에서 고른다(같은 걷기 안에서는
        // 관측 키의 최소값). DB 가드가 이 둘을 **함께** 보므로(축이 바뀌면 새 키를 요구한다) 둘을
        // 같은 행에서 가져온다 — 따로 고르면 시각은 움직이는데 키는 그대로인 조합이 생긴다.
        observationKey = latestObservationKeyOf(contributing),
    )
}

/** 가장 늦은 관측의 행 가운데 관측 키가 가장 작은 것 — 결정적이고 부모 관측 시각과 같은 행이다. */
private fun latestObservationKeyOf(contributing: List<OpeningAxisObservation>): ObservationKey {
    val latest = contributing.maxOf { it.observation.observedAt }
    return ObservationKey(
        contributing.filter { it.observation.observedAt == latest }.minOf { it.observationKey.value },
    )
}

/**
 * 이 승격을 **쓸 것인가**(멱등, D-7C5P-4) — 저장된 행의 관측 시각보다 늦을 때만이다.
 *
 * 술어가 upsert 의 `WHERE EXCLUDED.observed_at >= …` 와 **같은 축**이라 두 정의가 어긋날 수 없다
 * (COALESCE 병합 규칙을 Kotlin 에 다시 쓰면 그 사본이 SQL 과 조용히 갈린다). 같은 입력을 두 번
 * 넣으면 관측 시각이 같아 쓰지 않으므로 `revision`·`updated_at` 이 움직이지 않는다.
 *
 * 축 하나가 늦게 완료되는 경우도 이 술어가 통과시킨다: 그 축이 완료로 바뀌는 길은 **새 걷기** 하나뿐
 * 이고(실패로 끝난 축은 다시 불려야 결말이 바뀐다), 새 걷기는 앞 실행이 본 어느 걷기보다 늦다.
 *
 * **보지 못하는 것**: 관측 시각이 그대로인데 사상 결과가 달라지는 경우 — 계약에 키가 더해지는 배포가
 * 그 꼴이다. 그때는 이 술어가 승격을 건너뛴다(알려진 제한).
 */
fun promotionIsNew(
    stored: OpeningResult?,
    promoted: OpeningResult,
): Boolean = stored == null || promoted.observedAt.isAfter(stored.observedAt)

/**
 * 축마다 쓸 걷기의 행만 남긴다. 결말이 빈 응답·실패인 축은 **항목 자체가 없다** — 비운 채
 * 지나가는 것과 「0 행인 축」을 같은 값으로 두지 않는다.
 */
private fun selectedRowsOf(
    group: OpeningAxisGroup,
    conclusions: Map<SourceEndpoint, AxisConclusion>,
): Map<SourceEndpoint, List<OpeningAxisObservation>> =
    group.rows
        .groupBy { it.observation.sourceEndpoint }
        .mapNotNull { (endpoint, rows) ->
            when (val walk = axisWalkOf(conclusions[endpoint], rows.map { it.observation.observedAt })) {
                is AxisWalk.Use -> endpoint to rows.filter { it.observation.observedAt == walk.walk }
                AxisWalk.NoRows, AxisWalk.Unusable -> null
            }
        }.toMap()

private val AWARD_LIST_AXES = setOf(SourceEndpoint.OPENING_AWARD_LIST)

private val OPENING_COMPLETE_AXES = setOf(SourceEndpoint.OPENING_COMPLETE)

/** 예정가격·개찰 기초금액·예비가격 자식 행은 **예비가격 상세 자신의 관측**이다(각 슬롯 KDoc). */
private val RESERVE_PRICE_DETAIL_AXES = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL)

/** 참가업체수는 목록 축 둘에만 실려 온다 — 예비가격 상세는 그 키를 계약으로 선언하지 않는다. */
private val PARTICIPANT_COUNT_AXES = setOf(SourceEndpoint.OPENING_RESULT_LIST, SourceEndpoint.OPENING_AWARD_LIST)

private val PROGRESS_DIVISION_AXES = setOf(SourceEndpoint.OPENING_RESULT_LIST, SourceEndpoint.OPENING_COMPLETE)

private val ACTUAL_OPENING_AT_AXES = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL, SourceEndpoint.OPENING_AWARD_LIST)

/**
 * 슬롯마다 **그 슬롯이 뜻하는 관측의 축**(승격 축 선언 전수).
 *
 * 축이 개념에서 유도되지 않는 이유: 같은 개념이 여러 오퍼레이션에 실려 오고(기초금액은 공고 목록·
 * 예비가격 상세·기초금액 조회 셋), 슬롯마다 어느 오퍼레이션의 관측인지가 승인 명세로 정해져 있다.
 * 선언한 축이 그 개념의 계약 `presentIn` 합집합 안인지는 test 가 잰다 — 오타 난 축은 조용히 0 을
 * 내지 않는다.
 */
internal val OPENING_PROMOTION_SLOT_AXES: Map<FieldConcept, Set<SourceEndpoint>> =
    mapOf(
        FieldConcept.WINNING_RATE to AWARD_LIST_AXES,
        FieldConcept.AWARD_AMOUNT to AWARD_LIST_AXES,
        FieldConcept.AWARD_COMPANY_NAME to AWARD_LIST_AXES,
        FieldConcept.PARTICIPANT_COUNT to PARTICIPANT_COUNT_AXES,
        FieldConcept.PROGRESS_DIVISION to PROGRESS_DIVISION_AXES,
        FieldConcept.RESERVE_PRICE to RESERVE_PRICE_DETAIL_AXES,
        FieldConcept.BASE_AMOUNT to RESERVE_PRICE_DETAIL_AXES,
        FieldConcept.ACTUAL_OPENING_AT to ACTUAL_OPENING_AT_AXES,
        FieldConcept.RESERVE_PRICE_SEQUENCE to RESERVE_PRICE_DETAIL_AXES,
        FieldConcept.RESERVE_PRICE_PRELIMINARY to RESERVE_PRICE_DETAIL_AXES,
        FieldConcept.DRAW_FLAG to RESERVE_PRICE_DETAIL_AXES,
        FieldConcept.OPENING_RANK to OPENING_COMPLETE_AXES,
        FieldConcept.BID_COMPANY_NAME to OPENING_COMPLETE_AXES,
        FieldConcept.BID_AMOUNT to OPENING_COMPLETE_AXES,
        FieldConcept.BID_RATE to OPENING_COMPLETE_AXES,
    )

/**
 * 승격이 읽는 축 전수 — 위 슬롯 선언의 **합집합**이다. 읽기 어댑터가 질의 조건으로 쓴다: 축 목록을
 * 두 자리에 적으면 한쪽만 늘어나 조용히 읽히지 않는 축이 생긴다.
 */
val OPENING_PROMOTION_AXES: Set<SourceEndpoint> =
    OPENING_PROMOTION_SLOT_AXES.values.flatMapTo(mutableSetOf()) { it }

private fun awardAmountOf(reader: FieldReader): AwardAmount? =
    reader.wonReadingOf(FieldConcept.AWARD_AMOUNT)?.let { (won, contract) ->
        AwardAmount(won, currencyForWonUnit(contract.unit), reader.provenanceOf(contract))
    }

private fun plannedPriceOf(reader: FieldReader): YegaAmount? =
    reader.wonReadingOf(FieldConcept.RESERVE_PRICE)?.let { (won, contract) ->
        YegaAmount(won, currencyForWonUnit(contract.unit), reader.provenanceOf(contract))
    }

private fun openingBaseAmountOf(reader: FieldReader): BaseAmount? =
    reader.wonReadingOf(FieldConcept.BASE_AMOUNT)?.let { (won, contract) ->
        BaseAmount(won, currencyForWonUnit(contract.unit), contract.vatTreatment, reader.provenanceOf(contract))
    }

/**
 * 복수예비가격 자식 행 — **순번이 행의 정체성**이다(D-3E-1b (a)). 순번이 부재·공백인 행은 승격하지
 * 않고, 한 걷기 안에 같은 순번이 둘이면 첫 행만 쓴다(위치로 정체성을 지어내지 않는다).
 */
private fun reservePriceRowsOf(
    selected: Map<SourceEndpoint, List<OpeningAxisObservation>>,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): List<OpeningReservePriceRow> =
    selected[SourceEndpoint.RESERVE_PRICE_DETAIL]
        .orEmpty()
        .mapNotNull { row -> reservePriceRowOf(row, policy, round) }
        .distinctBy { it.sequenceNumber }

private fun reservePriceRowOf(
    row: OpeningAxisObservation,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): OpeningReservePriceRow? {
    val one = readerOf(SourceEndpoint.RESERVE_PRICE_DETAIL, row, policy, round)
    val sequence = one.textOf(FieldConcept.RESERVE_PRICE_SEQUENCE)?.trim()
    if (sequence.isNullOrEmpty()) return null
    return OpeningReservePriceRow(
        sequenceNumber = sequence,
        baseReservePrice =
            one.wonReadingOf(FieldConcept.RESERVE_PRICE_PRELIMINARY)?.let { (won, contract) ->
                ReservePriceCandidateAmount(won, currencyForWonUnit(contract.unit))
            },
        isDrawn = one.predicateOf(FieldConcept.DRAW_FLAG),
        // 행은 자기 관측 시각을 스스로 나른다(부모의 시각으로 대체하지 않는다).
        observedAt = row.observation.observedAt,
        // 추첨횟수(계약 미등재)는 구조적으로 부재다.
        drawCount = null,
    )
}

/**
 * 개찰 1위 축 — 결정은 기존 순수 커널이 한다(투찰금액으로 순위를 재계산하지 않는다).
 *
 * **전부 아니면 무**다: 고른 걷기의 투찰 행 가운데 상호를 얻지 못한 행이 하나라도 있으면 이 축을
 * 비운다. 그 행을 버리면 「순위 1이 유일한가」의 모수가 조용히 줄고, 승인된 결과 타입에는 「순위 1을
 * 특정했으나 상호가 없다」를 표현할 자리가 없다(상호는 공백일 수 없다) — 거짓 모양을 단언하는 대신
 * 관측하지 않은 것으로 둔다(알려진 제한).
 */
private fun rankOneOf(
    selected: Map<SourceEndpoint, List<OpeningAxisObservation>>,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): OpeningRankOneOutcome {
    val rows = selected[SourceEndpoint.OPENING_COMPLETE].orEmpty()
    if (rows.isEmpty()) return OpeningRankOneOutcome.NotObserved
    val candidates = rows.map { row -> rankOneCandidateOf(row, policy, round) }
    if (candidates.any { it == null }) return OpeningRankOneOutcome.NotObserved
    return OpeningRankOneOutcome.resolve(candidates.filterNotNull(), rows.maxOf { it.observation.observedAt })
}

private fun rankOneCandidateOf(
    row: OpeningAxisObservation,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): Pair<Int?, OpeningRankOneBid>? {
    val one = readerOf(SourceEndpoint.OPENING_COMPLETE, row, policy, round)
    val name = one.textOf(FieldConcept.BID_COMPANY_NAME)?.trim()
    if (name.isNullOrEmpty()) return null
    val bid =
        OpeningRankOneBid(
            bidderName = name,
            bidAmount =
                one.wonReadingOf(FieldConcept.BID_AMOUNT)?.let { (won, contract) ->
                    ObservedBidAmount(won, currencyForWonUnit(contract.unit))
                },
            bidRate = one.rateOf(FieldConcept.BID_RATE),
        )
    return one.countOf(FieldConcept.OPENING_RANK) to bid
}

private fun readerOf(
    endpoint: SourceEndpoint,
    row: OpeningAxisObservation,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): FieldReader = FieldReader(mapOf(endpoint to listOf(row)), policy, round)

/**
 * 고른 걷기의 행들을 **개념으로** 읽는 자리. 한 개념을 여러 축이 나르면 **늦게 관측된 축의 값이
 * 이긴다**(「최신 관측 우선」 — 이 표의 권위 계층이 그것 하나다). 같은 시각이면 축 열거 순서로
 * 결정적으로 고른다.
 *
 * 값을 얻으면 **그 값으로 끝낸다** — 해석되지 않는 최신 값을 건너뛰고 더 오래된 축의 값을 집으면,
 * 깨진 새 관측이 낡은 값에 가려진다. 빈 문자열은 값이 아니라 부재다(상류가 옵션을 그렇게 낸다).
 */
private class FieldReader(
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

    fun rateOf(concept: FieldConcept): Rate? =
        readingsOf(concept)
            .firstOrNull { it.contract.scale == FieldScale.PERCENT }
            ?.let { reading -> percentRateOf(reading.text) }

    /** 일시는 계약의 `sourceZone` 규칙으로만 해석한다 — 기존 진입점을 그대로 부른다. */
    fun instantOf(concept: FieldConcept): Instant? {
        for (observation in observationsIn(concept)) {
            when (val resolution = instantFrom(observation, policy, concept)) {
                is InstantResolutionOutcome.Resolved -> return resolution.instant
                // 값이 있는데 해석되지 않았다 — 더 오래된 축으로 물러서지 않는다.
                InstantResolutionOutcome.ParseFailed -> return null
                InstantResolutionOutcome.Absent -> Unit
            }
        }
        return null
    }

    /**
     * `Y`/`N` 만 참·거짓이다 — 그 밖의 값과 부재는 판정 불가(`null`)다. 지어내지 않는다.
     *
     * 대소문자를 **열거로** 받는다 — `uppercase()` 는 로케일을 끌어오고(domain 허용 목록 밖),
     * 프로토콜 플래그 두 글자에 로케일 의존 대소문자 접기를 쓸 이유도 없다.
     */
    fun predicateOf(concept: FieldConcept): Boolean? =
        when (textOf(concept)?.trim()) {
            "Y", "y" -> true
            "N", "n" -> false
            else -> null
        }

    /** 원 단위 정수 축의 값 + 그 값을 낸 계약(통화·과세·provenance 가 계약에서 온다). */
    fun wonReadingOf(concept: FieldConcept): Pair<Long, KonepsFieldContract>? =
        readingsOf(concept)
            .firstOrNull { it.contract.scale == FieldScale.WON_INTEGER }
            ?.let { reading -> wonOf(reading.text)?.let { it to reading.contract } }

    fun provenanceOf(contract: KonepsFieldContract): Provenance =
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
        return ordered
            .sortedWith(
                compareByDescending<Pair<SourceEndpoint, RawNoticeObservation>> { it.second.observedAt }
                    .thenBy { it.first.ordinal },
            ).map { it.second }
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
private fun wonOf(raw: String): Long? {
    val decimal = runCatching { BigDecimal(raw.trim().replace(",", "")) }.getOrNull() ?: return null
    val whole = decimal.stripTrailingZeros()
    if (whole.scale() > 0) return null
    return runCatching { whole.longValueExact() }.getOrNull()?.takeIf { it >= 0L }
}

/** 원문 percent → canonical fraction. 값 객체 불변식 위반과 표현 불가 지수는 부재로 접는다. */
private fun percentRateOf(raw: String): Rate? {
    val numeric = raw.trim().removePrefix("+").toBigDecimalOrNull() ?: return null
    return try {
        Rate.ofPercent(numeric)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/** 통화는 계약의 unit 에서 온다 — 리터럴로 짓지 않는다(공고 축 `canonicalize` 와 같은 자리). */
private fun currencyForWonUnit(unit: FieldUnit): Currency =
    when (unit) {
        FieldUnit.WON -> Currency.KRW
        else -> error("금액 축 계약의 unit은 WON이어야 한다: $unit")
    }
