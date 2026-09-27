package bidvector.workflow.collection

/**
 * 확정된 표본(D-6G-39) — 키 집합과 층. **이 집합 밖은 표본이 아니다**가 수집·추출 양쪽의 기준이다.
 */
data class SampleList(
    val strataByKey: Map<NoticeKeyHash, SampleStratum>,
) {
    val keys: Set<NoticeKeyHash> get() = strataByKey.keys
}

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
    fun confirm(sample: SampleOutcome): SampleList
}
