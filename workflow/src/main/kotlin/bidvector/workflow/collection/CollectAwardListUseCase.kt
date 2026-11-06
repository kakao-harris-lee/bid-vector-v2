package bidvector.workflow.collection

import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetLimit
import bidvector.procurement.CollectionAccounting
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
import bidvector.procurement.attemptOutcomeOf
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
 * 없다) 걸치는 조각은 같은 호출을 두 번 쓴다.
 *
 * 조각 상한은 `to - from` 의 **일 수**라 양 끝을 포함하면 최대 61일이고, 오퍼레이션 둘이면 바닥 호출이
 * **122**(업무당 최소 한 쪽) + 쪽 수만큼 더다 — 제안 일 상한 200 호출 안이다(scope.md D-4).
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
 * **원문으로 올라오지 못한 항목이 있다**는 뜻이고, 그 차이는 등식으로 닫힌다 —
 * `received − stored = duplicates + dropped`. 이 갈래에서 드롭 사유는 **공고번호·차수 부재** 하나다
 * (cr r3 M-2). 계약 필수 키(낙찰자 상호)의 부재는 **원문 적재를 막지 않는다** — 그 요구는 승격 축의
 * 것이다. 치환 실패도 막지 않는다(그쪽은 따로 세어진다).
 *
 * 그래서 [duplicates]·[dropped] 를 **따로 싣는다**(cr r3 M-1): 차이를 한 칸으로 접으면 조용한 결측이
 * 되고, 그 결측은 **재실행으로 풀리지 않는다**(원천이 같은 응답을 준다). 그래서 종료 코드는 이 둘을
 * 보지 않고 [unfinishedSlots] 만 본다 — 대신 마침 줄이 계수를 들어 운영자를 원천 응답으로 보낸다
 * (팀장 결정 2026-11-06: 드롭·중복을 미완으로 세지 않는다).
 */
data class AwardCollectionReport(
    val slots: Int,
    val received: Int,
    /**
     * 원문 적재를 **시도한** 항목 수 — 적재는 멱등이라(`ON CONFLICT DO NOTHING`) 같은 조각을 다시
     * 돌리면 새 행이 0 이어도 이 수는 꽉 찬다(cr r3 L-3). 실제 행 수를 재는 자리는 DB 질의다.
     */
    val stored: Int,
    /** 같은 식별자가 두 번 와서 원문을 쓰지 않은 항목 수 — 쪽 밀림의 신호다. */
    val duplicates: Int,
    /** 공고번호·차수가 비어 담는 단계에서 떨어진 항목 수 — 재실행으로 풀리지 않는다. */
    val dropped: Int,
    /**
     * **끝내지 못한** 슬롯 수 — 「잘린」이 아니다(cr r2 H-2). 절단 사유를 남긴 슬롯과 **사유 없이 짧게
     * 끝난** 슬롯(원천이 총수를 말했는데 그만큼 받지 못했다)을 함께 센다. 둘을 가르는 것은 사유가
     * 로그에 있는지뿐이고, 운영자의 다음 행동(그 조각을 다시 돌린다)과 이 갈래의 피해(그 개찰일의
     * 원문이 영영 빠진다)는 같다.
     */
    val unfinishedSlots: Int,
    val halted: AwardCollectionHalt?,
)

/**
 * 낙찰 목록 수집(M9/award-ingest D-1) — 개찰일 × 업무마다 목록을 끝까지 걷고 **원문만** 적재한다.
 *
 * canonical 승격을 하지 않는다. `opening_result` 의 낙찰 칸(낙찰자 상호·낙찰금액·낙찰률)은 기존 승격
 * 러너가 계약 행이 선언한 슬롯을 읽어 채운다 — 이 갈래가 그 경로를 건드리지 않는 것이 「원문이 들어오면
 * 승격은 코드 변경 0 으로 따라온다」의 실물이다.
 *
 * 멈춤은 둘뿐이다(예산·쿼터). 그 밖에 끝내지 못한 슬롯은 그 슬롯만 접고 다음 슬롯으로 간다 — 한 날의
 * 전송 실패로 창 전체를 버리면 다시 걷는 호출이 그만큼 더 나간다. 끝내지 못한 슬롯이 하나라도 있으면
 * 실행은 **미완**으로 끝난다(종료 코드는 러너가 [AwardCollectionReport] 에서 읽는다).
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
        val totals = AwardTotals()
        for ((index, slot) in plan.withIndex()) {
            val (date, source) = slot
            val halt = totals.add(sweepSlot(date, source)) ?: continue
            return totals.reportOf(index + 1, halt.copy(notAttemptedSlots = plan.size - index - 1))
        }
        return totals.reportOf(plan.size, halted = null)
    }

    /**
     * 슬롯 하나를 끝까지 걷는다 — **예산·쿼터·절단으로 그만둔 슬롯도 회계를 남긴다**(「어디까지
     * 읽었는가」가 남아야 다음 실행의 상한 seed 가 그 호출을 본다).
     *
     * **「다 받았는가」는 [attemptOutcomeOf] 하나가 답한다**(cr r2 H-2) — 이 저장소에서 그 물음의 단일
     * 출처이고, 절단 사유뿐 아니라 **짧은 걷기**(총수를 말한 원천에서 그만큼 받지 못한 채 끝났다)까지
     * 센다. 앞 판은 `truncationCause != null` 만 봤고, 그래서 빈 쪽·`NoData` 로 「곱게」 멈춘 걷기가
     * 사유를 남기지 않아 **완료로 적혔다**(D-6G-58 ⓒ 가 적은 그 함정이다). 이 갈래에는 슬롯 단위 이어
     * 돌기가 없으므로 그 침묵의 끝은 「그 개찰일의 낙찰 원문이 영영 들어오지 않는다」로 같다.
     *
     * 끝난 것으로 보는 결말은 **둘**이다 — [AttemptOutcome.Succeeded] 와 [AttemptOutcome.Empty].
     * 빈 응답은 오류가 아니다(그 개찰일에 낙찰이 없었다). 나머지(일시 실패·확정 실패·관문 거부)는
     * 전부 미완이고, 그중 예산·쿼터만 실행을 멈춘다([haltOf]).
     *
     * **인프라 예외는 전파하고 그 슬롯의 회계는 남지 않는다**(형제 갈래와 같은 거동) — 포트나 원문
     * 저장이 던지면 `runs.record` 에 닿지 못한 채 `collect` 밖으로 나가고 러너가 사유 토큰으로 정제한다.
     */
    private fun sweepSlot(
        date: LocalDate,
        source: AwardCollectionSource,
    ): SlotSweep {
        val referenceDate = CollectionReferenceDate(date)
        val startedAt = clock.now()
        val tally = SourceAccountingTally()
        var stored = 0
        var claimedTotal: Int? = null
        var cursor: PageCursor? = null
        var halt: AwardCollectionHalt?
        do {
            val batch = source.port.fetchNotices(referenceDate, cursor)
            tally.absorb(batch.accounting)
            claimedTotal = widerTotalOf(claimedTotal, batch.accounting.sourceTotal)
            batch.items.forEach { rawObservations.append(it) }
            stored += batch.items.size
            halt = haltOf(batch.accounting.truncationCause)
            cursor = resumeCursorOf(batch, cursor)
        } while (halt == null && cursor != null)
        val accounting = tally.toSourceAccounting().copy(sourceTotal = claimedTotal)
        runs.record(
            accounting,
            CollectionRunMeta(referenceDate, SourceEndpoint.OPENING_AWARD_LIST, startedAt, clock.now()),
        )
        return SlotSweep(accounting, stored, finishedWith(accounting), halt)
    }
}

/** 슬롯 하나의 끝 — 그 슬롯의 소스 회계와 적재 시도 수, 「다 받았는가」, 그리고 멈춰야 하면 사유. */
private class SlotSweep(
    val accounting: CollectionAccounting,
    val stored: Int,
    val finished: Boolean,
    val halt: AwardCollectionHalt?,
)

/** 실행 전체의 누적 — 슬롯마다 더하고 끝에 보고를 낸다(계수가 늘어도 호출부의 지역 변수가 늘지 않는다). */
private class AwardTotals {
    private var received = 0
    private var stored = 0
    private var duplicates = 0
    private var dropped = 0
    private var unfinished = 0

    /** 그 슬롯을 더하고, 실행을 멈춰야 하면 사유를 돌려준다. */
    fun add(sweep: SlotSweep): AwardCollectionHalt? {
        received += sweep.accounting.received
        stored += sweep.stored
        duplicates += sweep.accounting.duplicate
        dropped += sweep.accounting.dropped
        if (!sweep.finished) unfinished++
        return sweep.halt
    }

    fun reportOf(
        slots: Int,
        halted: AwardCollectionHalt?,
    ): AwardCollectionReport = AwardCollectionReport(slots, received, stored, duplicates, dropped, unfinished, halted)
}

/**
 * **원천이 한 번 말한 총수는 뒤 쪽이 지우지 못한다**(v r3 L-1) — 쪽 상한으로 이어 돌 때 공유 누산기는
 * 마지막 배치의 총수를 쓰는데(`source.sourceTotal ?: sourceTotal`), 어댑터는 배치마다 새 누산기라
 * 재개 첫 쪽이 `NoData` 면 총수 **0** 을 낸다. 그러면 「받은 수 ≥ 총수」가 되어 덜 받은 슬롯이 완료로
 * 적힌다. 최댓값을 쥐면 그 자리도, 원천이 뒤 쪽에서 총수를 **줄이는** 판도 함께 닫힌다.
 *
 * 공유 누산기를 고치지 않는 이유는 범위다 — 그 타입은 형제 공고 목록 갈래도 쓰고 이 slice 의
 * in_scope 밖이다(그 갈래의 거동을 바꾸지 않는다).
 */
private fun widerTotalOf(
    seen: Int?,
    batch: Int?,
): Int? =
    when {
        seen == null -> batch
        batch == null -> seen
        else -> maxOf(seen, batch)
    }

/** 그 슬롯이 다 받았는가 — 저장소 단일 출처([attemptOutcomeOf])의 정착 결말 둘만 참이다. */
private fun finishedWith(accounting: CollectionAccounting): Boolean =
    when (attemptOutcomeOf(accounting)) {
        AttemptOutcome.Succeeded, AttemptOutcome.Empty -> true
        else -> false
    }

/**
 * 멈출 사유인가 — 오늘은 예산·쿼터뿐이다. 나머지 절단(전송 실패·구조 오류·반복 쪽)은 그 슬롯의 회계에
 * 원인으로 남고 실행은 다음 슬롯으로 간다. **짧은 걷기는 여기 오지 않는다** — 사유가 없으므로
 * 멈춤도 아니고, 미완 계수만 올린다([AwardCollectionReport.unfinishedSlots]).
 *
 * **`else` 를 두지 않는다**(ops-code 레인 교차 지적). `else -> null` 은 「모르는 사유는 계속 걷는다」를
 * 뜻하고, 그 기본값은 틀린 쪽으로 안전하다: 계정·키 차단처럼 **남은 슬롯을 계속 부르면 사태를 키우는**
 * 사유가 새로 생기면 조용히 그 길로 간다. 소진 `when` 이면 새 [TruncationCause] 가 생기는 순간 컴파일이
 * 이 자리를 가리켜 결정을 강제한다 — 저장소의 `truncationCodeOf`·`failureOf` 와 같은 형태·같은 이유다.
 *
 * **다음에 이 자리를 깨우는 사유의 답은 정해져 있다**: 403 계열 차단(ops-code 가 들여올 `AccessBlocked`)은
 * **멈춤**이다 — 쿼터와 같은 자리(`AwardCollectionHalt(null, cause)`)로 보낸다. 차단된 키로 남은 슬롯을
 * 계속 부르면 승인 호출만 태우고 차단을 키운다. 오늘 이 브랜치에는 그 사유가 없어 그대로 컴파일된다.
 */
private fun haltOf(cause: TruncationCause?): AwardCollectionHalt? =
    when (cause) {
        null -> null

        is TruncationCause.BudgetExhausted -> AwardCollectionHalt(cause.limit, null)

        TruncationCause.QuotaExhausted -> AwardCollectionHalt(null, cause)

        TruncationCause.MaxPages,
        TruncationCause.RepeatedPage,
        TruncationCause.Timeout,
        TruncationCause.TransportFailure,
        TruncationCause.ServerError,
        TruncationCause.NotRetryable,
        TruncationCause.InputError,
        TruncationCause.Unclassified,
        TruncationCause.StructureFailure,
        TruncationCause.SelfThrottled,
        -> null
    }

/** 이어 읽을 쪽 — 쪽 상한으로 잘렸고 진전이 있을 때만(같은 커서를 다시 부르지 않는다). */
private fun resumeCursorOf(
    batch: SourceBatch<RawNoticeObservation>,
    previous: PageCursor?,
): PageCursor? = batch.next?.takeIf { batch.accounting.truncationCause == TruncationCause.MaxPages && it != previous }
