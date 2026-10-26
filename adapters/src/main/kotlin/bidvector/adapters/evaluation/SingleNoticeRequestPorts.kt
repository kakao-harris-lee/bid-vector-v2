package bidvector.adapters.evaluation

import bidvector.procurement.Notice
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.event.CorrelationId

/**
 * 운영자가 지목한 공고 **하나**만 후보로 내는 [CandidateSourcePort](M7/7C ②) — 요청 스코프다.
 *
 * 이것이 7C ② 가 평가 커널을 **무편집으로** 재사용하는 방법이다. `EvaluateCandidatesUseCase`
 * 는 「후보 목록을 받아 각각을 판정한다」이고, 목록의 길이가 1 인 것은 그 커널에게 특별한
 * 경우가 아니다 — 그래서 단건 평가에 새 판정 경로를 만들 필요가 없다(판정 복제 금지).
 *
 * **후보 전건 스캔([JdbcCandidateSource])을 거치지 않는다.** 지목된 공고를 찾기 위해 열린 공고
 * 전부를 읽으면 요청당 비용이 공고 수에 비례하고(반복 요청으로 비싼 계산 남용), 게다가 마감이
 * 지난 공고는 그 스캔에 없어서 **없는 공고와 구분되지 않는다**. 식별자로 한 행을 읽으면 마감이
 * 지난 공고도 커널의 수명 단계 드롭(`NoticeNotBiddable`)으로 정직하게 내려온다.
 */
class SingleNoticeCandidateSource(
    private val notice: Notice,
) : CandidateSourcePort {
    override fun openCandidates(): List<Notice> = listOf(notice)
}

/**
 * [MlAnalysisPort] 를 감싸 **결과를 보관**하는 데코레이터(M7/7C ②,
 * [RecordingNotificationRequestPort] 관례) — 요청 스코프다.
 *
 * 커널은 `MlAnalysisOutcome` 을 소비하고 결과에는 판정만 남긴다(`Analyzed` 의 점수·사유는
 * `Verdict` 로 접힌다). 화면 C 의 「신뢰도 · 확률 아님」 행은 그 접히기 전 사실(분석됐는가,
 * 안 됐다면 왜)을 요구하므로 **커널이 실제로 받은 값**을 그대로 보관한다 — 응답 조립이 같은
 * 질문을 ML 에 다시 묻지 않는다(호출 계수 상한, 그리고 두 호출이 다른 답을 낼 여지 제거).
 *
 * [delegate] 의 답을 **바꾸지 않는다** — 결과를 만들지 않고 지나가는 값을 적을 뿐이다.
 */
class RecordingMlAnalysis(
    private val delegate: MlAnalysisPort,
) : MlAnalysisPort {
    private val recorded = mutableListOf<MlAnalysisOutcome>()

    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome {
        val outcome = delegate.analyze(notice, correlationId)
        recorded += outcome
        return outcome
    }

    /**
     * 이 요청에서 커널이 받은 결과 — 커널이 ML 단계에 **이르지 못하면** `null` 이다(수명 단계·
     * 감시·자격·임계 미설정 드롭이 ML 앞에 있다). 그 `null` 은 「미가용」이 아니라 「거기까지 가지
     * 않았다」라서, 응답이 두 사실을 다른 값으로 낸다.
     */
    fun recordedOutcome(): MlAnalysisOutcome? = recorded.lastOrNull()
}
