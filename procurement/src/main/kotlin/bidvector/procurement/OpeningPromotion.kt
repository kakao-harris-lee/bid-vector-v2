package bidvector.procurement

import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.YegaAmount
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
     * 시각 하한은 보존 창이다(D-7C5P-3·8) — 마스킹을 눈앞에 둔 원문을 승격하면 업체 식별 값의
     * canonical 사본이 최초 적재 기준 지평을 두 배까지 넘길 수 있다. 하한을 두면 승격 지연이 보존
     * 창 이내로 묶이고, 상호의 잔존 상한이 「최초 적재 + 보존 창 + 승격 지연」 이하가 된다. 축은
     * **적재 시각**이다 — 마스킹을 거는 시계가 그것이라 관측 시각으로 자르면 창의 양 끝이 어긋난다.
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
 * 한 공고의 원장 상태 — 축별 **결말**과 **결말 없이 끝난 라운드가 있는 축**이다. 둘을 함께 받는
 * 이유: 결말의 부재가 서로 **다른 두 사실**을 뜻하고(원장 이전 원문 ↔ 적재와 결말 사이에서 죽은
 * 끊긴 걷기), 그 둘을 한 값으로 접으면 반쪽 원문이 canonical 로 굳는다(verifier r1 F1).
 */
class NoticeAxisLedger(
    val conclusions: Map<SourceEndpoint, AxisConclusion>,
    /** 마지막 결말 뒤에 의도·호출 줄만 남은 축 — 그 라운드는 결말을 적지 못했다(D-6G2d-42). */
    val interruptedAxes: Set<SourceEndpoint>,
)

/**
 * 걷기 선택의 **유일한 자리**. 원장 줄이 있으면 그 줄이 가리킨 걷기다. 행 존재로도, 적재 순서로도
 * 고르지 않는다 — 전자는 잘린 걷기를 완료로 읽고, 후자는 backfill 이 뒤집는다.
 *
 * **결말이 없을 때가 둘로 갈린다**(verifier r1 F1 — 앞 판은 하나로 접어 끊긴 걷기를 승격했다).
 * - 그 축에 **열린 라운드가 있다**(의도·호출 줄은 있는데 AXIS 결말이 없다): 적재와 결말 사이에서
 *   죽은 **끊긴 걷기**다. 그 행으로 canonical 을 쓰지 않는다 — 추출이 같은 원문을 `incomplete_axis`
 *   로 빼는 것과 **같은 판정**이다(승격이 추출보다 느슨할 이유가 없다).
 * - 줄이 **하나도 없다**: 원장 이전 원문이고(D-6G-58), 목록 축도 여기 떨어진다(목록 걷기의 줄은
 *   공고 키를 달지 않아 공고 단위로는 줄이 없다). **그때만** 가장 늦은 관측 시각으로 물러선다.
 *
 * 그래서 승격이 **목록 축 집합을 따로 들고 있지 않다** — 구별은 원장 자신의 어휘에서 나온다.
 * 그 물러섬은 `OPEN-6G-LIST-AXIS-WALK-SELECTION` 을 **상속한다**(이 slice 가 닫지 않는다).
 */
internal fun axisWalkOf(
    conclusion: AxisConclusion?,
    axisInterrupted: Boolean,
    observedAtValues: List<Instant>,
): AxisWalk =
    when {
        conclusion == null && axisInterrupted -> AxisWalk.Unusable
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
 * 값은 **개념 경유**로만 읽는다(세 파일 어디에도 raw 키 리터럴이 없다). 그래서 상류가 어느 개념에
 * 키를 더하거나, 아직 걷지 않는 축(낙찰 목록)의 원문이 뒤에 들어와도 **코드 변경 없이** 따라온다
 * (D-7C5P-1) — 슬롯마다 어느 오퍼레이션의 관측인지만 [OPENING_PROMOTION_SLOT_AXES] 가 선언한다.
 *
 * **원문 값이 무엇이든 던지지 않는다.** 값 유래 실패는 슬롯 부재이고, 0·빈 문자열로 적재하지
 * 않는다. 소수부가 섞인 금액은 깎지 않고 부재다.
 *
 * 세 파일의 경계는 **책임**이다: 이 파일은 「무엇이 canonical 이 되는가」(진입점·걷기 선택·결과·
 * 포트), `OpeningPromotionSlots.kt` 는 「슬롯마다 어느 축에서 어떻게 조립하는가」,
 * `OpeningPromotionFieldReader.kt` 는 「계약에서 값을 어떻게 읽는가」다.
 */
fun promoteOpeningResult(
    group: OpeningAxisGroup,
    ledger: NoticeAxisLedger,
    policy: KonepsCollectionPolicyData,
): OpeningPromotionOutcome {
    val selected = selectedRowsOf(group, ledger)
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
                finalAwardAmount =
                    reader.moneyOf(FieldConcept.AWARD_AMOUNT)?.let {
                        AwardAmount(it.won, it.currency, it.provenance)
                    },
                finalAwardCompanyName = reader.textOf(FieldConcept.AWARD_COMPANY_NAME),
                participantCount = reader.countOf(FieldConcept.PARTICIPANT_COUNT),
                progressDivision = reader.textOf(FieldConcept.PROGRESS_DIVISION),
                plannedPrice =
                    reader.moneyOf(FieldConcept.RESERVE_PRICE)?.let {
                        YegaAmount(it.won, it.currency, it.provenance)
                    },
                baseAmount =
                    reader.moneyOf(FieldConcept.BASE_AMOUNT)?.let {
                        BaseAmount(it.won, it.currency, it.vatTreatment, it.provenance)
                    },
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
 * 이 승격을 **쓸 것인가**(멱등, D-7C5P-4·8) — 저장된 행의 관측 시각보다 늦을 때만이다.
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
 * 축마다 쓸 걷기의 행만 남긴다. 결말이 빈 응답·실패·끊김인 축은 **항목 자체가 없다** — 비운 채
 * 지나가는 것과 「0 행인 축」을 같은 값으로 두지 않는다.
 *
 * **고른 걷기의 행이 0 이면 그 축도 항목이 없다**(verifier r1 F2). 원장이 가리킨 걷기의 행이 읽기
 * 집합에 없을 수 있다 — 보존 창 밖이거나 마스킹돼 빠진 경우다. 빈 목록을 남기면 그 뒤의 `maxOf`
 * 가 던져 공고 하나가 **실행 전체**를 멈춘다(「원문 값이 무엇이든 던지지 않는다」가 깨진다).
 */
private fun selectedRowsOf(
    group: OpeningAxisGroup,
    ledger: NoticeAxisLedger,
): Map<SourceEndpoint, List<OpeningAxisObservation>> =
    group.rows
        .groupBy { it.observation.sourceEndpoint }
        .mapNotNull { (endpoint, rows) ->
            val walk =
                axisWalkOf(
                    ledger.conclusions[endpoint],
                    endpoint in ledger.interruptedAxes,
                    rows.map { it.observation.observedAt },
                )
            when (walk) {
                is AxisWalk.Use -> rows.filter { it.observation.observedAt == walk.walk }
                AxisWalk.NoRows, AxisWalk.Unusable -> emptyList()
            }.takeIf { it.isNotEmpty() }?.let { endpoint to it }
        }.toMap()
