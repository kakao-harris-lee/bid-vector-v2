package bidvector.procurement

import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDate

/**
 * M7/7C⑤-0 승격 test 의 공통 대역 — **실제 정책**과 같은 원문 조립을 세 test 가 나눠 쓴다(사본을
 * 두면 한쪽만 고쳐져 조용히 갈린다). 책임이 셋이라 test 도 셋이고 그 경계는 production 세 파일과
 * 같다: 사상 표(`OpeningPromotionTest`) · 걷기와 멱등(`OpeningPromotionWalkTest`) · 1위 축과 자식
 * 행(`OpeningPromotionRankOneTest`).
 *
 * 승인 결정은 `reports/evidence/m7/7c5p/scope.md` 와 팀장 결정 `D-7C5P-1`~`8` 이다.
 */
private val PROMOTION_REFERENCE_DATE: LocalDate = LocalDate.of(2026, 10, 23)

/** **실제 정책**으로 잰다 — 사상이 계약 경유라는 것은 실제 계약 행에서만 참이 된다. */
internal val PROMOTION_POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(PROMOTION_REFERENCE_DATE)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("KONEPS_COLLECTION_POLICY 가 해석되지 않는다: ${resolution.reason}")
    }

internal val NOTICE_UNDER_TEST = NoticeId(NoticeNumber.of("OPENG-20261001-001"), NoticeRound.of("000"))

internal val WALK_A: Instant = Instant.parse("2026-10-20T01:00:00Z")

internal val WALK_B: Instant = Instant.parse("2026-10-21T02:00:00Z")

private var keySeed = 0

internal fun axisRow(
    endpoint: SourceEndpoint,
    observedAt: Instant,
    fields: Map<String, String>,
    key: String? = null,
): OpeningAxisObservation =
    OpeningAxisObservation(
        RawNoticeObservation.of(
            fields = fields.mapKeys { (name, _) -> RawKey(name) } + identityFields(),
            sourceEndpoint = endpoint,
            observedAt = observedAt,
            sourceDivision = null,
        ),
        ObservationKey(key ?: "key-${keySeed++}"),
    )

private fun identityFields(): Map<RawKey, String> =
    mapOf(
        NOTICE_NUMBER_RAW_KEY to NOTICE_UNDER_TEST.number.value,
        NOTICE_ROUND_RAW_KEY to NOTICE_UNDER_TEST.round.value,
    )

internal fun groupOf(vararg rows: OpeningAxisObservation): OpeningAxisGroup =
    OpeningAxisGroup(NOTICE_UNDER_TEST, rows.toList())

internal fun succeededAt(walk: Instant): AxisConclusion = AxisConclusion(AttemptOutcome.Succeeded, walk)

internal fun promote(
    group: OpeningAxisGroup,
    conclusions: Map<SourceEndpoint, AxisConclusion> = emptyMap(),
    interruptedAxes: Set<SourceEndpoint> = emptySet(),
): OpeningPromotionOutcome =
    promoteOpeningResult(group, NoticeAxisLedger(conclusions, interruptedAxes), PROMOTION_POLICY)

internal fun promotedResult(
    group: OpeningAxisGroup,
    conclusions: Map<SourceEndpoint, AxisConclusion> = emptyMap(),
    interruptedAxes: Set<SourceEndpoint> = emptySet(),
): OpeningResult =
    promote(group, conclusions, interruptedAxes)
        .shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        .result
