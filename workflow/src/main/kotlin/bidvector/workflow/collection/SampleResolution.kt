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
    fun resolve(
        framing: Framing,
        scope: SampleScope,
    ): FramedSample {
        ledger.confirmed()?.let { return FramedSample(restored(framing.candidates, it, scope), it.keys.size) }
        return confirmFirst(framing, scope)
    }

    /**
     * 첫 확정의 **전제 둘**(D-6G-50). 표본틀의 모든 슬롯이 끝까지 읽혔어야 하고, 뽑힌 것이 있어야
     * 한다. 반쪽 표본틀에서 뽑으면 실패한 슬롯의 공고가 영영 뽑히지 않고, 빈 표본을 확정하면 그
     * 파일이 이후 모든 실행을 막는다 — 둘 다 확정이 **되돌릴 수 없기** 때문에 생기는 사고다.
     */
    private fun confirmFirst(
        framing: Framing,
        scope: SampleScope,
    ): FramedSample {
        require(framing.truncatedSlots == 0) {
            "표본틀이 온전하지 않다(절단 슬롯 ${framing.truncatedSlots}) — 반쪽 표본틀에서 표본을 굳히지 않는다"
        }
        val fresh = draw(framing.candidates)
        require(fresh.selected.isNotEmpty()) { "빈 표본은 확정하지 않는다 — 그 파일이 이후 실행을 막는다" }
        val stored = ledger.confirm(SampleConfirmation(fresh, scope))
        val sample =
            if (stored.keys == fresh.selected.toSet()) fresh else restored(framing.candidates, stored, scope)
        return FramedSample(sample, stored.keys.size)
    }

    /** 계획용 — **확정하지 않는다.** 시험 삼아 돌린 계획이 표본을 못 박으면 안 된다. */
    fun preview(candidates: List<Candidate>): SampleOutcome =
        ledger.confirmed()?.let { narrow(candidates, it) } ?: draw(candidates)

    private fun draw(candidates: List<Candidate>): SampleOutcome = sampler.select(candidates.map { it.candidate })

    /**
     * 확정 파일에서 층·목표를 **되살린다**(D-6G-50). 범위가 지금 설정과 다르면 거부한다 — 그 표본은
     * 지금 설정이 말하는 모집단에서 뽑힌 것이 아니다.
     */
    private fun restored(
        candidates: List<Candidate>,
        confirmed: SampleList,
        scope: SampleScope,
    ): SampleOutcome {
        require(confirmed.scope == scope) {
            "확정된 표본의 표본틀 범위가 지금 설정과 다르다 — 같은 모집단이 아니다"
        }
        return narrow(candidates, confirmed)
    }

    /** 확정 표본 ∩ 이번 표본틀 — 표본인데 이번에 못 본 공고는 부르지 않고 센다(지어내지 않는다). */
    private fun narrow(
        candidates: List<Candidate>,
        confirmed: SampleList,
    ): SampleOutcome {
        val present =
            candidates
                .map { it.candidate.key }
                .filter { it in confirmed.keys }
                .distinct()
        return SampleOutcome(
            present.sortedWith(NOTICE_KEY_ORDER),
            confirmed.strata,
            confirmed.strataByKey.filterKeys { it in present },
            confirmed.requested,
        )
    }
}

/** 이번 표본틀에 놓인 확정 표본 — [unseen] 은 확정됐으나 이번에 보이지 않은 공고 수다(D-6G-39). */
internal class FramedSample(
    val sample: SampleOutcome,
    private val confirmedSize: Int,
) {
    val unseen: Int get() = confirmedSize - sample.selected.size
}
