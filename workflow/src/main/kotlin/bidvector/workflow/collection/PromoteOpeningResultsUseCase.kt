package bidvector.workflow.collection

import bidvector.procurement.AttemptLedger
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.OpeningPromotionOutcome
import bidvector.procurement.OpeningRawAxisRead
import bidvector.procurement.OpeningRawAxisSource
import bidvector.procurement.OpeningResultRepository
import bidvector.procurement.PersistOutcome
import bidvector.procurement.promoteOpeningResult
import bidvector.procurement.promotionIsNew
import bidvector.workflow.retention.RetentionPolicyData
import bidvector.workflow.strategy.Clock

/**
 * 승격 한 실행의 계수 — **닫힌 항등식**이다: 읽은 공고 묶음 하나하나가 승격됐거나, 되지 못한 사유로
 * 계수된다. 어느 쪽도 아닌 묶음은 없다. 행 단위 제외(키 없음·마스킹·보존 창)는 묶음이 되기 전에
 * 걸러진 것이라 **항등식 밖**이다.
 */
data class OpeningPromotionReport(
    val groupsRead: Int,
    val promoted: Int,
    /** 저장된 관측이 이미 이 승격만큼 새롭다 — 쓰지 않았다(멱등). */
    val skippedUnchanged: Int,
    /** 확정 표본 밖의 공고 — 승격하지 않는다(D-7C5P-6). */
    val outsideSample: Int,
    /** 쓸 걷기가 있는 축이 하나도 없다. */
    val noUsableAxis: Int,
    /** 저장 가드가 거부했다 — 조용히 삼키지 않는다. */
    val rejected: Int,
    val unkeyedRows: Int,
    val maskedRows: Int,
    val expiredRows: Int,
) {
    init {
        val accounted = promoted + skippedUnchanged + outsideSample + noUsableAxis + rejected
        require(groupsRead == accounted) {
            "읽은 묶음 수와 사유별 계수의 합이 같아야 한다: groupsRead=$groupsRead accounted=$accounted"
        }
    }
}

/** 한 실행의 결말 — 표본이 없는 것은 오류가 아니라 값이다(수집이 먼저다). */
sealed interface OpeningPromotionRun {
    data class Completed(
        val report: OpeningPromotionReport,
    ) : OpeningPromotionRun

    /** 확정 표본 목록이 없다 — 승격 범위를 알 수 없으므로 아무것도 쓰지 않는다. */
    data object SampleNotConfirmed : OpeningPromotionRun
}

/**
 * 이미 적재된 개찰 축 원문을 canonical `opening_result` 로 승격한다(M7/7C⑤-0).
 *
 * **수집 경로를 건드리지 않는다.** 외부 호출 0 · 원문 쓰기 0 · 시도 원장 쓰기 0 — 원장은 **읽기만**
 * 하고, 그 읽기는 어느 걷기의 행을 쓸지 정하기 위해서다(D-6G-68).
 *
 * 범위는 **확정 표본**이다(D-7C5P-6 · D-6G-39) — 실행 상태 디렉터리의 확정 표본 목록(`sample-list.tsv`
 * 가 담고 [SampleListLedger.confirmed] 가 내는 집합)에 든 공고만 승격한다. 「DB 의 개찰 원문 전부」로
 * 넓히지 않는다: 그러면 표본 밖 공고가 canonical 에 들어오고 그 집합을 말하는 문서가 없다.
 *
 * 멱등하다 — 같은 입력을 두 번 넣으면 둘째 실행은 아무것도 쓰지 않는다([promotionIsNew]).
 */
class PromoteOpeningResultsUseCase(
    private val rawAxes: OpeningRawAxisSource,
    private val results: OpeningResultRepository,
    private val attempts: AttemptLedger,
    private val sampleList: SampleListLedger,
    private val policyFor: (CollectionReferenceDate) -> KonepsCollectionPolicyData,
    /** 승격 대상 원문의 적재 시각 하한을 정한다(D-7C5P-3) — 보존 정책이 그 창의 정본이다. */
    private val retention: RetentionPolicyData,
    /** 계약 개정 해석의 기준일 — 배선이 정한다(수집 갈래가 같은 자리에서 같은 결정을 한다). */
    private val referenceDate: CollectionReferenceDate,
    private val clock: Clock,
) {
    fun promote(): OpeningPromotionRun {
        val sample = sampleList.confirmed() ?: return OpeningPromotionRun.SampleNotConfirmed
        val read = rawAxes.readOpeningAxes(retention.cutoffFrom(clock.now()).instant)
        // 걷기 권위는 원장이다(D-6G-68) — raw 행의 존재도, 그 행의 시각도 아니다. 결말이 없는 축은
        // 상세 축이면 쓰지 않는다: 결말의 부재는 「원장 이전 원문」·「다른 실행 상태 디렉터리의 원문」·
        // 「적재와 결말 사이에서 죽은 걷기」를 가리지 못하고, 셋 다 반쪽 원문일 수 있다(D-7C5P-11).
        val conclusions = attempts.read().axisConclusions()
        val policy = policyFor(referenceDate)
        val tally = PromotionTally()
        for (group in read.groups) {
            val hash = NoticeKeyHash.of(group.noticeId.number.value, group.noticeId.round.value)
            if (hash !in sample.keys) {
                tally.outsideSample++
                continue
            }
            when (val outcome = promoteOpeningResult(group, conclusions[hash.value].orEmpty(), policy)) {
                OpeningPromotionOutcome.NoUsableAxis -> tally.noUsableAxis++
                is OpeningPromotionOutcome.Promotable -> store(outcome, tally)
            }
        }
        return OpeningPromotionRun.Completed(tally.reportOf(read))
    }

    private fun store(
        outcome: OpeningPromotionOutcome.Promotable,
        tally: PromotionTally,
    ) {
        if (!promotionIsNew(results.find(outcome.noticeId), outcome.result)) {
            tally.skippedUnchanged++
            return
        }
        when (results.persist(outcome.result, outcome.observationKey)) {
            PersistOutcome.Inserted, is PersistOutcome.Updated -> tally.promoted++

            // 저장이 더 늦은 관측을 이미 들고 있었다 — 읽기와 쓰기 사이에 다른 실행이 지났다.
            PersistOutcome.Unchanged -> tally.skippedUnchanged++

            is PersistOutcome.Rejected -> tally.rejected++
        }
    }
}

private class PromotionTally {
    var promoted: Int = 0
    var skippedUnchanged: Int = 0
    var outsideSample: Int = 0
    var noUsableAxis: Int = 0
    var rejected: Int = 0

    fun reportOf(read: OpeningRawAxisRead): OpeningPromotionReport =
        OpeningPromotionReport(
            groupsRead = read.groups.size,
            promoted = promoted,
            skippedUnchanged = skippedUnchanged,
            outsideSample = outsideSample,
            noUsableAxis = noUsableAxis,
            rejected = rejected,
            unkeyedRows = read.unkeyedRows,
            maskedRows = read.maskedRows,
            expiredRows = read.expiredRows,
        )
}
