package bidvector.workflow.opening

import bidvector.procurement.NoticeId
import bidvector.workflow.strategy.Clock
import java.time.Instant

/**
 * 조회가 DB 에서 읽은 **사실**(M7/7C⑤-a) — 상태 판정은 여기 없다.
 *
 * 판정을 어댑터에서 하지 않는 이유: 판정은 시계와 정책이 들어가는 순수 함수라
 * ([resolveOpeningResultView]) DB 없이 전수로 잴 수 있다. 어댑터는 「무엇을 읽었는가」만 나르고,
 * 그 경계 덕에 상태 아홉의 test 가 Testcontainers 없이 돈다.
 */
sealed interface NoticeOpeningFacts {
    /** 공고 행 자체가 없다. */
    data object NoticeAbsent : NoticeOpeningFacts

    /**
     * 공고 행은 있다. [canonical] 이 `null` 이면 개찰 결과 canonical 행이 없다는 뜻이고,
     * [openingAxisObserved] 는 그 공고의 **개찰 결과 축 원문**이 적재돼 있는가다(예비가격·
     * 기초금액·A값 축은 개찰 **결과**가 아니라 입력이므로 이 술어에 들지 않는다).
     */
    data class Present(
        val deadlineAt: Instant?,
        val canonical: CanonicalOpeningFacts?,
        val openingAxisObserved: Boolean,
    ) : NoticeOpeningFacts
}

/**
 * canonical 행에서 읽은 것 — 진행구분은 **라벨 원문**으로 받고 정책이 상태로 옮긴다
 * ([OpeningProgressPolicyData]). 라벨을 어댑터에서 상태로 바꾸면 그 사상이 DB 층에 숨는다.
 */
data class CanonicalOpeningFacts(
    val progressLabel: String?,
    val figures: OpeningResultFigures,
)

/** 개찰 결과 조회 포트 — 읽기 **하나뿐**이라 이 포트를 쥔 코드가 무엇도 바꿀 수 없다. */
fun interface OpeningResultFactsPort {
    fun lookup(id: NoticeId): NoticeOpeningFacts
}

/**
 * 사실 → 상태(순수). **관측이 시계 추론보다 강한 근거다** — 개찰 축 원문이 있으면 마감이
 * 아직 뒤라도 「승격 전」이다(그 관측이 개찰이 있었다는 증거이고, 마감 비교는 관측이 없을 때
 * 쓰는 유도다).
 *
 * 마감 경계는 **마감 시각에 이르면 미개찰이 아니다**(`now < deadline` 만 미개찰).
 */
fun resolveOpeningResultView(
    facts: NoticeOpeningFacts,
    now: Instant,
    policy: OpeningProgressPolicyData,
): OpeningResultView =
    when (facts) {
        NoticeOpeningFacts.NoticeAbsent -> OpeningResultView.NoticeAbsent
        is NoticeOpeningFacts.Present ->
            facts.canonical?.let { canonical ->
                OpeningResultView.Observed(policy.progressOf(canonical.progressLabel), canonical.figures)
            } ?: facts.viewWithoutCanonical(now)
    }

private fun NoticeOpeningFacts.Present.viewWithoutCanonical(now: Instant): OpeningResultView =
    when {
        openingAxisObserved -> OpeningResultView.ObservedNotPromoted
        deadlineAt != null && now < deadlineAt -> OpeningResultView.NotOpenedYet
        else -> OpeningResultView.NotCollected
    }

/**
 * 개찰 결과 조회 하나 — 포트의 사실에 시계와 정책을 더해 상태를 낸다.
 *
 * 조립을 HTTP 층이 아니라 이 자리에 두는 이유: 그쪽에 두면 호출 순서(시계를 먼저 읽는가,
 * 정책을 어느 값으로 주는가)가 전송 층의 자유가 된다.
 */
class ReadOpeningResult(
    private val facts: OpeningResultFactsPort,
    private val clock: Clock,
    private val policy: OpeningProgressPolicyData,
) {
    fun read(id: NoticeId): OpeningResultView = resolveOpeningResultView(facts.lookup(id), clock.now(), policy)
}
