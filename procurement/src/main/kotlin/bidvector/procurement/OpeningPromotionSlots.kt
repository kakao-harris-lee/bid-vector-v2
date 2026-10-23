package bidvector.procurement

import bidvector.sharedkernel.NoticeRound

/**
 * 승격의 **슬롯 층**(M7/7C⑤-0) — 「슬롯마다 어느 축에서 어떻게 조립하는가」. 진입점은
 * `OpeningPromotion.kt` 이고 계약 판독 primitive 는 `OpeningPromotionFieldReader.kt` 다.
 */
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
 * **공고 단위로 걷는 축**(= 상세 축, D-7C5P-11). 이 축들만 공고 키를 단 AXIS 결말 줄을 원장에 남기고,
 * 목록 축의 줄은 공고 키가 없어(`CollectionAttempt.noticeKey` KDoc 「목록 축은 `null`」) 공고 단위로는
 * 줄이 아예 없다. 그래서 결말이 없을 때 **목록 축만** 행 시각으로 물러설 수 있다.
 *
 * **정본은 수집 팬아웃의 `DetailAxis`** 다 — 이 집합은 그 열거의 거울이고, 둘이 같은지는 등식 test 가
 * 잰다(`PromoteOpeningAxisClassTest`). 거울을 둔 이유: 그 열거는 application 층(workflow)에 있고 사상은
 * 그 아래 domain 층이라 직접 볼 수 없다. 하나로 합치는 길(예: `SourceEndpoint` 쪽 속성)은 **있지만**
 * 수집·추출 production 을 건드려 **이 slice 의 범위 밖**이라 합치지 않았다(알려진 제한 20, 후속 slice).
 * 등식이 깨지면 승격이 상세 축을 목록 축으로 오인해 결말 없는 원문을 올리므로, 그 test 가 이 거울의
 * 유일한 존재 근거다.
 */
val OPENING_PER_NOTICE_AXES: Set<SourceEndpoint> =
    setOf(
        SourceEndpoint.RESERVE_PRICE_DETAIL,
        SourceEndpoint.OPENING_COMPLETE,
        SourceEndpoint.BASE_AMOUNT_DETAIL,
        SourceEndpoint.BID_PRICE_FORMULA_A,
    )

/**
 * 승격이 읽는 축 전수 — 위 슬롯 선언의 **합집합**이다. 읽기 어댑터가 질의 조건으로 쓴다: 축 목록을
 * 두 자리에 적으면 한쪽만 늘어나 조용히 읽히지 않는 축이 생긴다.
 */
val OPENING_PROMOTION_AXES: Set<SourceEndpoint> =
    OPENING_PROMOTION_SLOT_AXES.values.flatMapTo(mutableSetOf()) { it }

/**
 * 복수예비가격 자식 행 — **순번이 행의 정체성**이다(D-3E-1b (a)). 순번이 부재·공백인 행은 승격하지
 * 않고, 한 걷기 안에 같은 순번이 둘이면 첫 행만 쓴다(위치로 정체성을 지어내지 않는다).
 */
internal fun reservePriceRowsOf(
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
    val sequence = one.textOf(FieldConcept.RESERVE_PRICE_SEQUENCE)
    if (sequence.isNullOrEmpty()) return null
    return OpeningReservePriceRow(
        sequenceNumber = sequence,
        baseReservePrice =
            one.moneyOf(FieldConcept.RESERVE_PRICE_PRELIMINARY)?.let {
                ReservePriceCandidateAmount(it.won, it.currency)
            },
        isDrawn = one.drawFlagOf(),
        // 행은 자기 관측 시각을 스스로 나른다(부모의 시각으로 대체하지 않는다).
        observedAt = row.observation.observedAt,
        // 추첨횟수(계약 미등재)는 구조적으로 부재다.
        drawCount = null,
    )
}

/**
 * `Y`/`N` 만 참·거짓이다 — 그 밖의 값과 부재는 판정 불가(`null`)다. 지어내지 않는다.
 *
 * 대소문자를 **열거로** 받는다 — `uppercase()` 는 로케일을 끌어오고(domain 허용 목록 밖), 프로토콜
 * 플래그 두 글자에 로케일 의존 대소문자 접기를 쓸 이유도 없다.
 */
private fun FieldReader.drawFlagOf(): Boolean? =
    when (textOf(FieldConcept.DRAW_FLAG)) {
        "Y", "y" -> true
        "N", "n" -> false
        else -> null
    }

/**
 * 개찰 1위 축 — 결정은 기존 순수 커널이 한다(투찰금액으로 순위를 재계산하지 않는다).
 *
 * **전부 아니면 무**다: 고른 걷기의 투찰 행 가운데 상호를 얻지 못한 행이 하나라도 있으면 이 축을
 * 비운다. 그 행을 버리면 「순위 1이 유일한가」의 모수가 조용히 줄고, 승인된 결과 타입에는 「순위 1을
 * 특정했으나 상호가 없다」를 표현할 자리가 없다(상호는 공백일 수 없다) — 거짓 모양을 단언하는 대신
 * 관측하지 않은 것으로 둔다(알려진 제한).
 */
internal fun rankOneOf(
    selected: Map<SourceEndpoint, List<OpeningAxisObservation>>,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): OpeningRankOneOutcome {
    val rows = selected[SourceEndpoint.OPENING_COMPLETE].orEmpty()
    val candidates = rows.map { row -> rankOneCandidateOf(row, policy, round) }
    return if (rows.isEmpty() || candidates.any { it == null }) {
        OpeningRankOneOutcome.NotObserved
    } else {
        OpeningRankOneOutcome.resolve(candidates.filterNotNull(), rows.maxOf { it.observation.observedAt })
    }
}

private fun rankOneCandidateOf(
    row: OpeningAxisObservation,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): Pair<Int?, OpeningRankOneBid>? {
    val one = readerOf(SourceEndpoint.OPENING_COMPLETE, row, policy, round)
    val name = one.textOf(FieldConcept.BID_COMPANY_NAME)
    return if (name.isNullOrEmpty()) {
        null
    } else {
        one.countOf(FieldConcept.OPENING_RANK) to
            OpeningRankOneBid(
                bidderName = name,
                bidAmount = one.moneyOf(FieldConcept.BID_AMOUNT)?.let { ObservedBidAmount(it.won, it.currency) },
                bidRate = one.rateOf(FieldConcept.BID_RATE),
            )
    }
}

/** 행 하나만 보는 판독기 — 자식 행·투찰 행은 **행 단위**로 조립된다(행끼리 값이 섞이지 않는다). */
private fun readerOf(
    endpoint: SourceEndpoint,
    row: OpeningAxisObservation,
    policy: KonepsCollectionPolicyData,
    round: NoticeRound,
): FieldReader = FieldReader(mapOf(endpoint to listOf(row)), policy, round)
