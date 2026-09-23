package bidvector.app.http

import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.app.wiring.EvaluationDryRunRun
import bidvector.decision.Verdict
import bidvector.procurement.NoticeId
import bidvector.workflow.evaluation.CandidateEvaluation
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 평가 dry-run endpoint(D-6A3-1·6·8, 운영자 결정 2 — dry-run 전용) — 외부 effect 를
 * 만들지 않는다(outbox 미접촉, 위협 모델 ①). **컨트롤러는 `factory.forRequest(...).
 * evaluate()`만 부른다**(6A 완료 조건, D-6A3-8) — 판정·조립을 복제하거나 포트를 직접
 * 부르지 않는다(우회 5 폐쇄 대상).
 *
 * `evaluate()`가 `suspend`(M4/4B-3 ADR 0010 D-2, 실 ML 취소 전파 대비)라 `runBlocking`
 * 으로 동기 Spring MVC 경계 하나만 다리 놓는다 — 이 slice가 그 포트를 처음 production
 * 에서 돈다(app/build.gradle.kts에 `kotlinx-coroutines-core`를 처음 컴파일 의존으로
 * 더한 이유).
 */
@RestController
class EvaluationDryRunController(
    private val factory: EvaluationDryRunFactory,
) {
    @PostMapping("/api/evaluation-dry-runs")
    fun evaluate(
        @RequestBody request: EvaluationDryRunRequest,
    ): EvaluationDryRunResponse {
        val run = factory.forRequest(request.currentActiveBids)
        val results = runBlocking { run.useCase.evaluate() }
        return EvaluationDryRunResponse.from(request.currentActiveBids, run, results)
    }
}

/** 요청 본문(D-6A3-5) — `currentActiveBids` 하나뿐이다. 음수는 [RequestCapacityPort]가 거부한다. */
data class EvaluationDryRunRequest(
    val currentActiveBids: Int,
)

/**
 * 응답(D-6A3-6) — **평탄하다**(D-6A1-38 게이트를 넓히지 않는다). 결과별 공고 ID 배열 +
 * 건수로만 답한다 — 후보별 사유 상세는 `OPEN-6A3-EVALUATION-DETAIL`. 공고 ID 만 싣는다
 * (공고명·기관명 원문은 응답에 없다, privacy).
 */
data class EvaluationDryRunResponse(
    val strategyRevision: Int,
    val candidateCount: Int,
    val currentActiveBids: Int,
    val maxActiveBids: Int,
    val bidNowNoticeIds: List<String>,
    val reviewNoticeIds: List<String>,
    val skipNoticeIds: List<String>,
    val notReachedNoticeIds: List<String>,
    /** [RecordingNotificationRequestPort]가 모은 요청의 공고 ID — 불변식: 이 집합 == [bidNowNoticeIds] 집합. */
    val wouldNotifyNoticeIds: List<String>,
) {
    companion object {
        fun from(
            currentActiveBids: Int,
            run: EvaluationDryRunRun,
            results: List<CandidateEvaluation>,
        ): EvaluationDryRunResponse {
            val buckets = ResultBuckets.of(results)
            return EvaluationDryRunResponse(
                strategyRevision = run.strategy.revision.value,
                candidateCount = results.size,
                currentActiveBids = currentActiveBids,
                maxActiveBids =
                    requireNotNull(run.strategy.maxActiveBids) {
                        "factory 가 이미 상한 존재를 보장한다(MaxActiveBidsNotConfiguredException)"
                    }.value,
                bidNowNoticeIds = buckets.bidNow,
                reviewNoticeIds = buckets.review,
                skipNoticeIds = buckets.skip,
                notReachedNoticeIds = buckets.notReached,
                wouldNotifyNoticeIds = run.notifications.requested().map { it.noticeId.label() },
            )
        }
    }
}

/** [CandidateEvaluation] 목록을 네 서로소 갈래로 가른다(D-6A3-6 불변식 — 길이 합 == candidateCount). */
private data class ResultBuckets(
    val bidNow: List<String>,
    val review: List<String>,
    val skip: List<String>,
    val notReached: List<String>,
) {
    companion object {
        fun of(results: List<CandidateEvaluation>): ResultBuckets {
            val bidNow = mutableListOf<String>()
            val review = mutableListOf<String>()
            val skip = mutableListOf<String>()
            val notReached = mutableListOf<String>()
            results.forEach { evaluation -> evaluation.classify(bidNow, review, skip, notReached) }
            return ResultBuckets(bidNow, review, skip, notReached)
        }

        private fun CandidateEvaluation.classify(
            bidNow: MutableList<String>,
            review: MutableList<String>,
            skip: MutableList<String>,
            notReached: MutableList<String>,
        ) {
            when (this) {
                is CandidateEvaluation.Reached -> {
                    when (verdict) {
                        is Verdict.BidNow -> bidNow += noticeId.label()
                        is Verdict.Review -> review += noticeId.label()
                        is Verdict.Skip -> skip += noticeId.label()
                    }
                }

                is CandidateEvaluation.NotReached -> {
                    notReached += noticeId.label()
                }
            }
        }
    }
}

/**
 * 응답에 싣는 공고 식별 라벨(D-6A3-6 「공고 ID 만 싣는다」) — 공고번호·차수 쌍을 하나의
 * 문자열로 합친다. 공고명·기관명 원문은 어디에도 참조하지 않는다(privacy).
 */
private fun NoticeId.label(): String = "${number.value}:${round.value}"
