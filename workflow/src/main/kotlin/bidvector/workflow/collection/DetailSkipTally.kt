package bidvector.workflow.collection

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.DetailFetchSkipReason
import bidvector.procurement.SourceEndpoint
import java.time.Instant

/**
 * 조회 가치 술어가 **부르지 않기로 한** 축의 사유별 수(COL-03 둘째 bullet, M6/G1-C03) — 수집 보고가
 * 나른다.
 *
 * 회계 열은 하나(`backoff_skipped`)이고 거기에는 속도 보호의 허가 거부와 **같은 칸에** 실린다. 그 칸은
 * 「이번 스윕이 부르지 않은 호출이 몇인가」에 답하고, 「왜」에 답하는 것은 이 값이다 — 한 칸에 섞되 사유를
 * 잃지 않는다는 것이 이것이고, 열을 늘리지 않으므로 마이그레이션이 없다.
 */
data class DetailSkipCounts(
    val ageGate: Int,
    val recheckGate: Int,
) {
    val total: Int get() = ageGate + recheckGate
}

/**
 * 한 실행이 건너뛴 축의 누적 — 사유별 합(보고)과 **축별 합**(회계 줄)을 함께 센다.
 *
 * **이미 받은 축은 세지 않는다.** 그것은 백오프가 아니라 이어 돌기의 정상 결론이고, 세면 그 신호가 매
 * 스윕 표본 크기만큼 울려 「수집이 줄었다」를 가리키지 못한다. 소진 `when` 이라 술어에 사유가 하나 늘면
 * 컴파일이 이 자리를 가리킨다.
 */
internal class DetailSkipTally {
    private val byAxis = mutableMapOf<SourceEndpoint, Int>()
    private var ageGate = 0
    private var recheckGate = 0

    fun record(
        endpoint: SourceEndpoint,
        reason: DetailFetchSkipReason,
    ) {
        when (reason) {
            DetailFetchSkipReason.AlreadyHeld -> return

            is DetailFetchSkipReason.AgeGateNotPassed -> ageGate++

            is DetailFetchSkipReason.RecheckGateNotPassed -> recheckGate++
        }
        byAxis[endpoint] = (byAxis[endpoint] ?: 0) + 1
    }

    fun counts(): DetailSkipCounts = DetailSkipCounts(ageGate, recheckGate)

    /**
     * 축마다 한 줄 — 건너뜀은 **호출이 아니므로** 받은 항목도 걸은 쪽도 0 이다. 줄 자체가 0 이면 적지
     * 않는다(아무 일도 없던 축에 줄을 남기면 「건너뜀이 있었다」가 희석된다).
     */
    fun recordInto(
        runs: CollectionRunStore,
        referenceDate: CollectionReferenceDate,
        startedAt: Instant,
        finishedAt: Instant,
    ) {
        byAxis.forEach { (endpoint, skipped) ->
            runs.record(
                skipAccounting(skipped),
                CollectionRunMeta(referenceDate, endpoint, startedAt, finishedAt),
            )
        }
    }
}

/** 건너뜀만 실은 회계 — COL-06 항등식은 0 = 0 + 0 + 0 으로 선다. */
private fun skipAccounting(skipped: Int): CollectionAccounting =
    CollectionAccounting(
        received = 0,
        normalized = 0,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = null,
        pagesFetched = 0,
        truncated = false,
        unknownFields = 0,
        backoffSkipped = skipped,
    )
