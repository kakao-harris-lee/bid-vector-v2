package bidvector.workflow.evaluation

import bidvector.decision.ProvenanceJudgement
import bidvector.procurement.NoticeId

/**
 * DEC-08 판정 기록(M9) — **분류에 쓴 정책 version 이 결과와 함께 남는다.** 커널
 * ([bidvector.decision.ProvenanceRules])은 원본·판정·근거·정책 version 을 한 봉투로 내는데
 * (`ProvenanceJudgement`), 지금까지 호출부가 `classification` 하나만 취하고 나머지를 버렸다 —
 * 그래서 「어느 정책이 이 라벨을 냈는가」를 나중에 답할 수 없었다(6e1 추적 DEC-08).
 *
 * 봉투를 **그대로** 받는다(라벨만 뽑아 받지 않는다) — port 경계에서 근거를 버리면 저장 층이
 * 무엇을 적을지 고를 수 없다.
 */
fun interface BaseAmountClassificationRecordPort {
    /**
     * 같은 공고를 같은 정책 version 으로 다시 판정하면 **같은 행**이다(멱등) — 구현은
     * 덮어쓰지 않고 넘긴다. 정책 version 이 바뀌면 **새 행**이고 과거 판정은 남는다
     * (원본을 조용히 교정하지 않는다, capability-map DEC-08 「재사용 후보(규율)」).
     */
    fun record(
        noticeId: NoticeId,
        judgement: ProvenanceJudgement,
    )
}

/**
 * 기록하지 않는 구현 — **부작용이 없어야 하는 경로**(dry-run · 조회)가 끼운다. `null` port 를
 * 허용하거나 기본값을 두면 「아무도 안 끼운 조립」과 「일부러 안 쓰는 조립」이 같은 모양이 된다.
 */
object DiscardingBaseAmountClassificationRecordPort : BaseAmountClassificationRecordPort {
    override fun record(
        noticeId: NoticeId,
        judgement: ProvenanceJudgement,
    ) = Unit
}
