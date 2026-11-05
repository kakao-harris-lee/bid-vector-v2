package bidvector.workflow.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.NoticeSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.workflow.strategy.Clock
import java.time.LocalDate

/**
 * 업무 하나의 낙찰 목록 소스(M9/award-ingest D-1) — 어느 오퍼레이션인지는 [port] 구현이 안다
 * (이 타입은 이름만 붙인다).
 *
 * 포트는 **도메인이 이미 가진 [NoticeSourcePort]** 다 — 새로 짓지 않는다. 그 계약이 이 갈래가 필요한
 * 전부이고(조회일 하나의 쪽을 걷어 원문과 회계를 낸다) **메서드가 하나뿐**이라는 것이 호출 상한을
 * 지키는 첫 자리다: 공고당 호출(예비가격·개찰완료 등)을 내려면 다른 포트를 쥐어야 하므로 이 배선으로는
 * 그런 경로가 생기지 않는다. 낙찰 목록은 **한 행 = 한 공고**라 포트 이름과도 맞다.
 *
 * 포트를 이 모듈에 새로 선언하지 않는 이유는 규약이다 — 「domain 이 의존하는 port 는 domain 안에
 * 선다」(ADR 0005 D-10.1 · ADR 0006 D-2). 어댑터가 application 타입을 참조하지 않는다는 것은
 * `KonepsAdapterDependencyTest` 가 잰다(그 게이트가 workflow 에 포트를 둔 첫 판을 거부했다).
 *
 * **남는 위험**: 타입이 같으므로 낙찰 목록 소스를 공고 목록 use case 에 꽂는 조합이 타입으로는 막히지
 * 않는다(그쪽은 canonical `notice` 를 쓴다). 두 갈래의 묶음 타입이 다르고([CollectionSource] 대 이
 * 타입) 배선이 각자 자기 설정에서 만들어 출하 경로에는 그 조합이 없다 — 타입이 아니라 **묶음 타입과
 * 배선**이 그 자리를 막는다(알려진 제한에 등재).
 */
class AwardCollectionSource(
    val name: CollectionSourceName,
    val port: NoticeSourcePort,
)

/**
 * 낙찰 목록 갈래의 조각 상한 — **인스턴스가 따로다**(공고 목록 31일·개찰결과 120일과 다른 근거).
 *
 * 이 갈래의 호출 수는 **창 길이 × 오퍼레이션 수**에 비례한다(공고 목록 갈래와 같은 성질이고, 표본
 * 크기에 묶이는 개찰결과 갈래와는 다르다). 한 조각이 **하루 승인 상한 안에서 끝나야** 한다: 일 상한에
 * 걸려 멈춘 스윕은 다음 실행에서 조각의 처음부터 다시 걷으므로(목록 축에는 슬롯 단위 이어 돌기가
 * 없다) 걸치는 조각은 같은 호출을 두 번 쓴다. 제안 일 상한 200 호출에서 60일 × 오퍼레이션 둘 =
 * 120 호출 + 쪽 여유가 그 조건을 만족하는 값이다(scope.md D-4).
 *
 * 긴 기간은 조각을 **여러 번** 돌려 덮는다 — 상한을 없애지 않는 이유는 형제 갈래와 같다: 연도 오타
 * 한 글자가 실 호출 전에 기동 단계에서 잡혀야 한다.
 */
val AWARD_COLLECTION_RANGE_POLICY: EffectiveDatedPolicy<CollectionRangePolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m9/award-ingest/scope.md D-1·D-4 — 개찰일 조각 <= 60일(일 상한 안에서 끝나는 조각)",
        entries = listOf(EffectiveFrom.Initial to CollectionRangePolicyData(maxSpanDays = AWARD_MAX_SPAN_DAYS)),
    )

private const val AWARD_MAX_SPAN_DAYS = 60

/**
 * 스윕이 멈춘 사실 — **예산과 쿼터를 구별한다**(형제 갈래와 같은 축): 예산은 승인 범위를 다 쓴 것이고
 * 쿼터는 KONEPS 가 거절한 것이다. 멈춘 단위가 **슬롯**(개찰일 × 업무)인 것이 이 갈래의 차이다 —
 * 표본이 없으므로 「손대지 않은 표본 수」가 아니라 걷지 못한 슬롯 수가 다음 실행의 입력이다.
 */
data class AwardCollectionHalt(
    val budgetLimit: BudgetLimit?,
    val truncationCause: TruncationCause?,
    val notAttemptedSlots: Int = 0,
) {
    init {
        require((budgetLimit == null) != (truncationCause == null)) {
            "멈춤 사유는 예산이거나 쿼터이지 둘 다이거나 둘 다 아닐 수 없다"
        }
    }
}

/**
 * 한 실행의 계수 — 건수와 열거값뿐이다(공고 식별자·상호를 나르지 않아 로그 줄이 구조적으로 안전하다).
 *
 * [stored] 는 원문으로 적재된 항목 수, [received] 는 소스에서 받은 항목 수다. 둘이 갈리는 것은
 * 식별자 부재로 떨어진 항목이 있다는 뜻이고, 그 차이를 한 칸으로 접으면 조용한 결측이 된다.
 */
data class AwardCollectionReport(
    val slots: Int,
    val received: Int,
    val stored: Int,
    val truncatedSlots: Int,
    val halted: AwardCollectionHalt?,
)

/**
 * 낙찰 목록 수집(M9/award-ingest D-1) — 개찰일 × 업무마다 목록을 끝까지 걷고 **원문만** 적재한다.
 *
 * canonical 승격을 하지 않는다. `opening_result` 의 낙찰 칸(낙찰자 상호·낙찰금액·낙찰률)은 기존 승격
 * 러너가 계약 행이 선언한 슬롯을 읽어 채운다 — 이 갈래가 그 경로를 건드리지 않는 것이 「원문이 들어오면
 * 승격은 코드 변경 0 으로 따라온다」의 실물이다.
 *
 * 멈춤은 둘뿐이다(예산·쿼터). 그 밖의 절단은 그 슬롯만 접고 다음 슬롯으로 간다 — 한 날의 전송 실패로
 * 창 전체를 버리면 다시 걷는 호출이 그만큼 더 나간다.
 */
class CollectAwardListUseCase(
    private val rawObservations: RawObservationStore,
    private val runs: CollectionRunStore,
    private val clock: Clock,
) {
    fun collect(
        range: CollectionRange,
        sources: List<AwardCollectionSource>,
    ): AwardCollectionReport {
        require(sources.isNotEmpty()) { "낙찰 목록 소스가 하나도 없다" }
        require(sources.map { it.name }.toSet().size == sources.size) { "업종 이름은 서로 달라야 한다" }
        val plan = range.dates.flatMap { date -> sources.map { date to it } }
        var received = 0
        var stored = 0
        var truncated = 0
        for ((index, slot) in plan.withIndex()) {
            val (date, source) = slot
            val sweep = sweepSlot(date, source)
            received += sweep.received
            stored += sweep.stored
            if (sweep.truncated) truncated++
            val halt = sweep.halt ?: continue
            val halted = halt.copy(notAttemptedSlots = plan.size - index - 1)
            return AwardCollectionReport(index + 1, received, stored, truncated, halted)
        }
        return AwardCollectionReport(plan.size, received, stored, truncated, halted = null)
    }

    /**
     * 슬롯 하나를 끝까지 걷는다 — **어느 길로 끝나든 회계를 남긴다**(예산이 막아 도중에 그만둔 슬롯도
     * 「어디까지 읽었는가」가 남아야 다음 실행의 상한 seed 가 그 호출을 본다).
     */
    private fun sweepSlot(
        date: LocalDate,
        source: AwardCollectionSource,
    ): SlotSweep {
        val referenceDate = CollectionReferenceDate(date)
        val startedAt = clock.now()
        val tally = SourceAccountingTally()
        var stored = 0
        var cursor: PageCursor? = null
        var halt: AwardCollectionHalt?
        do {
            val batch = source.port.fetchNotices(referenceDate, cursor)
            tally.absorb(batch.accounting)
            batch.items.forEach { rawObservations.append(it) }
            stored += batch.items.size
            halt = haltOf(batch.accounting.truncationCause)
            cursor = resumeCursorOf(batch, cursor)
        } while (halt == null && cursor != null)
        val accounting = tally.toSourceAccounting()
        runs.record(
            accounting,
            CollectionRunMeta(referenceDate, SourceEndpoint.OPENING_AWARD_LIST, startedAt, clock.now()),
        )
        return SlotSweep(accounting.received, stored, accounting.truncationCause != null, halt)
    }
}

/** 슬롯 하나의 끝 — 계수 둘과 「끝까지 읽었는가」, 그리고 멈춰야 하면 사유. */
private class SlotSweep(
    val received: Int,
    val stored: Int,
    val truncated: Boolean,
    val halt: AwardCollectionHalt?,
)

/**
 * 멈출 사유인가 — 예산·쿼터만이다. 나머지 절단(전송 실패·구조 오류·반복 쪽)은 그 슬롯의 회계에
 * 원인으로 남고 실행은 다음 슬롯으로 간다.
 */
private fun haltOf(cause: TruncationCause?): AwardCollectionHalt? =
    when (cause) {
        is TruncationCause.BudgetExhausted -> AwardCollectionHalt(cause.limit, null)
        TruncationCause.QuotaExhausted -> AwardCollectionHalt(null, cause)
        else -> null
    }

/** 이어 읽을 쪽 — 쪽 상한으로 잘렸고 진전이 있을 때만(같은 커서를 다시 부르지 않는다). */
private fun resumeCursorOf(
    batch: SourceBatch<RawNoticeObservation>,
    previous: PageCursor?,
): PageCursor? = batch.next?.takeIf { batch.accounting.truncationCause == TruncationCause.MaxPages && it != previous }
