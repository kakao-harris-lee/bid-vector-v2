package bidvector.workflow.collection

import bidvector.workflow.collection.NoticeKeyHash

/**
 * 「이번 실행의 표본은 무엇인가」 하나만 답한다(D-6G-39) — 뽑기([StratifiedSampler])와 확정 원장
 * ([SampleListLedger]) 사이의 자리다. 수집 use case 는 표본을 **묻기만** 하고, 다시 뽑을지 말지를
 * 판단하지 않는다.
 */
internal class SampleResolution(
    private val sampler: StratifiedSampler,
    private val ledger: SampleListLedger,
) {
    /**
     * 확정된 목록이 있으면 그것을, 없으면 뽑아서 확정한다. **뽑은 실행에서는 뽑은 결과를 그대로**
     * 쓴다 — 층별 결과(모자란 층 여부)는 뽑는 순간에만 있는 사실이라 교집합으로 재구성할 수 없다.
     * 확정에서 졌으면(동시 실행) 이긴 쪽의 목록으로 물러선다.
     */
    fun resolve(candidates: List<Candidate>): FramedSample {
        ledger.confirmed()?.let { return FramedSample(narrow(candidates, it.keys), it.keys.size) }
        val fresh = draw(candidates)
        val stored = ledger.confirm(fresh)
        val sample = if (stored.keys == fresh.selected.toSet()) fresh else narrow(candidates, stored.keys)
        return FramedSample(sample, stored.keys.size)
    }

    /** 계획용 — **확정하지 않는다.** 시험 삼아 돌린 계획이 표본을 못 박으면 안 된다. */
    fun preview(candidates: List<Candidate>): SampleOutcome =
        ledger.confirmed()?.let { narrow(candidates, it.keys) } ?: draw(candidates)

    private fun draw(candidates: List<Candidate>): SampleOutcome = sampler.select(candidates.map { it.candidate })

    /** 확정 표본 ∩ 이번 표본틀 — 표본인데 이번에 못 본 공고는 부르지 않고 센다(지어내지 않는다). */
    private fun narrow(
        candidates: List<Candidate>,
        confirmed: Set<NoticeKeyHash>,
    ): SampleOutcome {
        val present =
            candidates
                .map { it.candidate.key }
                .filter { it in confirmed }
                .distinct()
        return SampleOutcome(present.sortedWith(NOTICE_KEY_ORDER), emptyMap())
    }
}

/** 이번 표본틀에 놓인 확정 표본 — [unseen] 은 확정됐으나 이번에 보이지 않은 공고 수다(D-6G-39). */
internal class FramedSample(
    val sample: SampleOutcome,
    private val confirmedSize: Int,
) {
    val unseen: Int get() = confirmedSize - sample.selected.size
}
