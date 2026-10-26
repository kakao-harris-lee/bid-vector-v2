package bidvector.adapters.opening

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcOpeningRawAxisSource
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.AxisConclusion
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningPromotionOutcome
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.OpeningResult
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.promoteOpeningResult
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.opening.NoticeOpeningFacts
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDate

internal val T1: Instant = Instant.parse("2026-10-22T00:00:00Z")
internal val T2: Instant = Instant.parse("2026-10-22T03:00:00Z")
internal val DEADLINE: Instant = Instant.parse("2026-10-21T05:00:00Z")

/** 표지 상호 — 조회가 내는 사실 어디에도 이 글자가 나오면 안 된다(D-7C5-3, 값 수준 단언). */
internal const val SENTINEL_COMPANY = "SENTINEL-BIDDER-DO-NOT-EXPORT"

/**
 * 개찰 결과 조회 test 둘이 함께 쓰는 **행 심기**(`BidCaseDbFixture` 와 같은 관례).
 *
 * 단언은 하나도 두지 않는다 — 여기 있는 것은 「어떤 상태의 DB 인가」뿐이고, 「그 상태에서 조회가
 * 무엇을 답해야 하는가」는 두 test class 가 각자의 책임으로 잰다:
 * [JdbcOpeningResultViewTest] 는 **결과 투영과 읽기 전용**을, [JdbcOpeningAxisObservationTest] 는
 * **원문 존재와 승격 축**을 잰다. 같은 fixture 를 두 벌 두면 한쪽만 고치는 편집이 조용히 지나간다.
 */
abstract class OpeningViewDbFixture : PersistenceTestSupport() {
    protected val id = NoticeId(NoticeNumber.of("OPENVIEW-20261022-001"), NoticeRound.of("001"))
    protected val other = NoticeId(NoticeNumber.of("OPENVIEW-20261022-999"), NoticeRound.of("001"))

    protected fun view() = JdbcOpeningResultView(dataSource())

    protected fun seedNotice(target: NoticeId = id) {
        val observation = observationOf(SourceEndpoint.NOTICE_LIST, T1, target)
        val command =
            NoticeCollected(
                id = target,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = DEADLINE,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, appendRawObservation(observation)) shouldBe
            PersistOutcome.Inserted
    }

    /** 원문 한 줄 — 공고 식별자 둘은 계약 등재 키라 투영에 실린다(`NOTICE_IDENTIFIER_PRESENT_IN`). */
    protected fun observationOf(
        endpoint: SourceEndpoint,
        observedAt: Instant,
        target: NoticeId = id,
    ): RawNoticeObservation = rawObservationOf(target.number.value, target.round.value, endpoint, observedAt)

    /**
     * 원문 한 줄을 **상류가 보낸 표기 그대로** 만든다 — canonical 값을 심는 [observationOf] 와
     * 달리 원문 ≠ canonical 사례를 만들 수 있다. 정규화는 심는 쪽이 아니라 읽는 쪽의 몫이다.
     */
    protected fun rawObservationOf(
        numberRaw: String,
        roundRaw: String,
        endpoint: SourceEndpoint = SourceEndpoint.OPENING_RESULT_LIST,
        observedAt: Instant = T1,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                NOTICE_NUMBER_RAW_KEY to numberRaw,
                NOTICE_ROUND_RAW_KEY to roundRaw,
            ),
            endpoint,
            observedAt,
        )

    /** 보존 규율이 찍는 마스킹 표지 — 그 행은 승격 경로가 더 이상 읽지 않는다. */
    protected fun markMasked(key: ObservationKey) =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("UPDATE raw_observation SET pii_masked_at = now() WHERE observation_key = ?")
                .use { statement ->
                    statement.setString(1, key.value)
                    statement.executeUpdate() shouldBe 1
                }
        }

    protected fun rawKey(
        endpoint: SourceEndpoint,
        observedAt: Instant,
        target: NoticeId = id,
    ): ObservationKey = appendRawObservation(observationOf(endpoint, observedAt, target))

    protected fun seedCanonical(
        result: OpeningResult,
        observedAt: Instant = T1,
    ) {
        JdbcOpeningResultRepository(dataSource())
            .persist(result, rawKey(SourceEndpoint.OPENING_RESULT_LIST, observedAt))
    }

    protected fun canonicalOf(
        progressDivision: String? = "개찰완료",
        participantCount: Int? = 5,
        finalAwardAmount: AwardAmount? = null,
        openingRankOne: OpeningRankOneOutcome = OpeningRankOneOutcome.NotObserved,
        observedAt: Instant = T1,
    ) = OpeningResult(
        noticeId = id,
        winningRate = null,
        derivedBaseAmount = null,
        observedAt = observedAt,
        finalAwardAmount = finalAwardAmount,
        participantCount = participantCount,
        progressDivision = progressDivision,
        actualOpeningAt = Instant.parse("2026-10-21T06:00:00Z"),
        openingRankOne = openingRankOne,
    )

    protected fun present(facts: NoticeOpeningFacts) = facts.shouldBeInstanceOf<NoticeOpeningFacts.Present>()

    /**
     * 원문 두 축을 심고 **승격 경로가** canonical 을 쓰게 한다 — 이 fixture 가 승격 그 자체다
     * (손으로 짠 canonical 을 쓰면 두 slice 의 맞물림을 재지 못한다).
     *
     * 라벨을 **앞뒤 공백과 함께** 넘길 수 있게 열어 둔다: 승격이 trim 해서 저장하고(그쪽 계약)
     * 이 조회는 리터럴로 비교한다(이쪽 계약) — 호출부가 그 비대칭을 입력으로 준다.
     */
    protected fun promoteViaProductionPath(
        progressLabel: String,
        bidderName: String,
        walk: Instant = T1,
    ) {
        appendRawObservation(
            RawNoticeObservation.of(
                fields =
                    mapOf(
                        RawKey("progrsDivCdNm") to progressLabel,
                        RawKey("prtcptCnum") to "7",
                        NOTICE_NUMBER_RAW_KEY to id.number.value,
                        NOTICE_ROUND_RAW_KEY to id.round.value,
                    ),
                sourceEndpoint = SourceEndpoint.OPENING_RESULT_LIST,
                observedAt = walk,
                sourceDivision = null,
            ),
        )
        appendRawObservation(
            RawNoticeObservation.of(
                fields =
                    mapOf(
                        RawKey("prcbdrNm") to bidderName,
                        RawKey("opengRank") to "1",
                        NOTICE_NUMBER_RAW_KEY to id.number.value,
                        NOTICE_ROUND_RAW_KEY to id.round.value,
                    ),
                sourceEndpoint = SourceEndpoint.OPENING_COMPLETE,
                observedAt = walk,
                sourceDivision = null,
            ),
        )
        val policy = (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 10, 23)) as Resolution.Resolved).value
        val group = JdbcOpeningRawAxisSource(dataSource()).readOpeningAxes(Instant.EPOCH).groups.single()
        val conclusions =
            listOf(SourceEndpoint.OPENING_RESULT_LIST, SourceEndpoint.OPENING_COMPLETE)
                .associateWith { AxisConclusion(AttemptOutcome.Succeeded, walk) }
        val promoted =
            promoteOpeningResult(group, conclusions, policy)
                .shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        JdbcOpeningResultRepository(dataSource()).persist(promoted.result, promoted.observationKey)
    }

    /** 조회가 닿는 세 표의 쓰기 흔적 — 행 집합과 `xmin`·`ctid`(7C① R3-L-1 처방을 표 셋으로 넓혔다). */
    protected fun writeFingerprint(): List<String> =
        listOf("notice", "opening_result", "raw_observation").flatMap { table -> fingerprintOf(table) }

    private fun fingerprintOf(table: String): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery("SELECT xmin::text AS xmin, ctid::text AS ctid FROM $table ORDER BY ctid")
                    .use { rows ->
                        generateSequence {
                            if (rows.next()) "$table@${rows.getString("xmin")}:${rows.getString("ctid")}" else null
                        }.toList()
                    }
            }
        }
}
