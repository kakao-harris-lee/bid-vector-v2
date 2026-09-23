package bidvector.app.http

import bidvector.adapters.evaluation.InvalidEvaluationRequestException
import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.app.wiring.EvaluationDryRunRun
import bidvector.decision.Verdict
import bidvector.procurement.NoticeId
import bidvector.workflow.evaluation.CandidateEvaluation
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

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
 *
 * **`@RequestBody`가 [EvaluationDryRunRequest]가 아니라 [JsonNode]다(D-6A3-19, 검토 라운드
 * 1 contract-keeper V1·verifier MEDIUM 시정).** `currentActiveBids: Int`(non-null primitive)
 * 로 직접 바인딩하면 Jackson 3의 기본 강제 변환(`ALLOW_COERCION_OF_SCALARS`·
 * `ACCEPT_FLOAT_AS_INT`)이 `"3"`(문자열)·`1.7`(소수)을 **조용히** 200으로 통과시킨다(실측
 * — 값을 지어내는 것에 가깝다, D-6F1-5 위반). 원시 트리로 받아 [parseCurrentActiveBids]가
 * **명시** 검증하면, 이 필드 하나만 엄격해지고 전역 `ObjectMapper` 설정은 그대로다 — 이
 * endpoint 가 이 앱에서 사실상 처음으로 요청 본문을 받는 자리라 다른 endpoint 거동에
 * 영향이 없다(오늘은 `/api/strategy` GET 하나뿐, 본문이 없다).
 */
@RestController
class EvaluationDryRunController(
    private val factory: EvaluationDryRunFactory,
) {
    @PostMapping("/api/evaluation-dry-runs")
    fun evaluate(
        @RequestBody body: JsonNode,
    ): EvaluationDryRunResponse {
        val request = EvaluationDryRunRequest(parseCurrentActiveBids(body))
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
 * D-6A3-19 — 요청 본문의 **명시** 형식 검증. `0..Int.MAX_VALUE` 범위의 JSON 정수만 받는다 —
 * 누락·`null`·문자열·소수·정수 범위 초과는 전부 [InvalidEvaluationRequestException](400
 * `INVALID_REQUEST`, 예외 메시지는 응답에 싣지 않는다 — `ErrorMapping` 이 고정 문구로만
 * 옮긴다)다. 음수 자체는 형식상 유효한 JSON 정수라 이 함수를 통과한다 — 그 거부는
 * `RequestCapacityPort`(값 수준 검증, 단일 자리 유지)가 진다.
 */
internal fun parseCurrentActiveBids(body: JsonNode): Int {
    if (!body.isObject) {
        throw InvalidEvaluationRequestException("요청 본문이 JSON object 가 아니다")
    }
    val field = body.get("currentActiveBids")
    val isValidJsonInt = field != null && !field.isNull && field.isIntegralNumber && field.canConvertToInt()
    if (!isValidJsonInt) {
        throw InvalidEvaluationRequestException("currentActiveBids 는 0 이상의 JSON 정수(Int 범위)여야 한다")
    }
    return checkNotNull(field).intValue()
}

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
