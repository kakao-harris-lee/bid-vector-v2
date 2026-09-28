package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import java.time.LocalDate

/**
 * 표본을 뽑은 **표본틀의 범위**(D-6G-50) — 업무 집합과 공고일 창.
 *
 * 확정 파일에 이것을 싣고 이후 실행의 설정과 대조한다. 범위가 달라지면 그 표본은 지금 설정이 말하는
 * 모집단에서 뽑힌 것이 아니다 — 창을 넓혀 다시 돌리면 늘어난 공고는 영영 뽑히지 않고, 좁혀서 돌리면
 * 표본의 일부가 표본틀 밖이 된다. 어느 쪽이든 조용히 지나가면 안 되는 어긋남이다.
 */
data class SampleScope(
    val from: LocalDate,
    val to: LocalDate,
    val divisions: Set<BusinessDivision>,
)

/**
 * 확정된 표본(D-6G-39·50) — 키·층·목표·범위.
 *
 * **이 집합 밖은 표본이 아니다**가 수집·추출 양쪽의 기준이고, [strata] 와 [requested] 를 함께 싣는
 * 이유는 확정 뒤의 실행도 「모자란 층」을 보고할 수 있어야 하기 때문이다(층별 결과는 뽑는 순간에만
 * 있는 사실이라 교집합으로는 되살릴 수 없다).
 */
data class SampleList(
    val strataByKey: Map<NoticeKeyHash, SampleStratum>,
    val strata: Map<SampleStratum, StratumOutcome> = emptyMap(),
    val requested: Int = 0,
    /**
     * 이 표본을 뽑은 **모집단의 범위**(D-6G-50) — `null` 일 수 없다(cr r4 M-7 ⑴). 없으면 통과하는
     * 대조는 게이트가 아니다: 판독이 범위를 빠뜨리는 순간 「같은 모집단인가」 검사가 예외 없이,
     * 조용히 사라진다. 타입이 그 자리를 없앤다.
     */
    val scope: SampleScope,
) {
    val keys: Set<NoticeKeyHash> get() = strataByKey.keys
}

/** 확정에 올리는 한 벌 — 뽑은 결과와 그것을 뽑은 범위. 둘은 떨어지면 뜻을 잃는다. */
data class SampleConfirmation(
    val sample: SampleOutcome,
    val scope: SampleScope,
)

/**
 * 표본 목록 원장 — **한 번 확정하고 다시 뽑지 않는다**(D-6G-39).
 *
 * 수집은 3~4일에 걸친다. 실행마다 뽑으면 늦게 개찰된 공고가 창에 들어오거나 한 슬롯이 실패하는
 * 것만으로 표본이 달라져, 「결과를 보기 전에 확정한다」가 실행 단위로만 성립한다. 그래서 확정은
 * 저장소 밖 파일에 떨어지고, 이후 실행은 그것을 읽기만 한다.
 */
interface SampleListLedger {
    /** 아직 확정되지 않았으면 `null`. */
    fun confirmed(): SampleList?

    /**
     * 첫 표본틀이 뽑은 것을 확정한다. **이미 확정돼 있으면 덮어쓰지 않고 그것을 돌려준다** — 동시에
     * 두 실행이 들어와도 표본은 하나다.
     */
    fun confirm(confirmation: SampleConfirmation): SampleList
}
