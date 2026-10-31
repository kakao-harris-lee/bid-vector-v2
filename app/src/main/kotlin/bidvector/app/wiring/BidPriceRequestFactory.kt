package bidvector.app.wiring

import bidvector.adapters.evaluation.InvalidEvaluationRequestException
import bidvector.adapters.evaluation.PinnedStrategyRepository
import bidvector.adapters.evaluation.RecordingMlAnalysis
import bidvector.adapters.evaluation.RecordingNotificationRequestPort
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.adapters.evaluation.SingleNoticeCandidateSource
import bidvector.adapters.qualification.PinnedLicenseGate
import bidvector.decision.PUBLISHED_FLOOR_TRUST_POLICY
import bidvector.decision.PublishedFloorTrustPolicyData
import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.qualification.LicenseJudgement
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.LicenseJudgementPort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.NoticeByIdPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 공고 단건·운영자 요청 평가의 요청 스코프 묶음(M7/7C ②, `EvaluationDryRunRun` 관례) —
 * 컨트롤러는 [evaluate] 하나만 부르고 판정·조립을 복제하지 않는다.
 *
 * **어댑터가 아니라 값을 낸다**(D-6A2b-8) — [notifications]·[mlAnalysis] 는 어댑터 구현이라
 * `app.http` 의존 게이트가 그 참조를 막는다. 컨트롤러가 필요한 것은 「알림이 갈 뻔했는가」와
 * 「커널이 받은 ML 결과」라서 그 값만 낸다.
 *
 * [licenseJudgement] 는 **커널에 pin 된 판정과 같은 값**이다 — [BidPriceRequestFactory] 가 한 번
 * 판정해 그 `verdict` 를 [PinnedLicenseGate] 로 커널에 넘겼다. 응답의 근거와 판정이 갈릴 자리가
 * 없다는 사실이 이 필드다.
 */
class BidPriceRequestRun(
    private val useCase: EvaluateCandidatesUseCase,
    private val notifications: RecordingNotificationRequestPort,
    private val mlAnalysis: RecordingMlAnalysis,
    val strategy: OperatorStrategy,
    val notice: Notice,
    val licenseJudgement: LicenseJudgement,
    /**
     * 게시 하한율 신뢰 밴드(DEC-10) — 표시 조립이 **원값 대신 게이트 판정**을 읽는 데 쓴다
     * (7C②-b, D-7C2B-1). 값을 묶음에 담는 이유는 응답 조립이 순수 함수라 `Clock` 도 정책 표도
     * 읽지 않기 때문이다 — 해소는 [BidPriceRequestFactory] 한 자리에서 요청당 한 번이다.
     */
    val floorTrustPolicy: Resolution.Resolved<PublishedFloorTrustPolicyData>,
) {
    /**
     * 평가 한 번 — 커널이 후보 **하나**를 돌려주므로 결과도 하나다. 그 불변식을 여기서
     * 단언한다(후보 소스가 1원소라는 사실이 깨지면 조용히 첫 원소를 쓰지 않는다).
     */
    suspend fun evaluate(): CandidateEvaluation {
        val results = useCase.evaluate()
        require(results.size == 1) {
            "단건 평가는 결과가 정확히 하나여야 한다(후보 소스가 1원소다): ${results.size}"
        }
        return results.single()
    }

    /** 이 판정이 알림 요청을 낳았는가 — dry-run 과 같이 **요청까지**이고 배달·기록은 없다. */
    fun wouldNotify(): Boolean = notifications.requested().isNotEmpty()

    /** 커널이 받은 ML 결과. 커널이 ML 단계에 이르지 못했으면 `null`(「미가용」과 다른 사실). */
    fun mlOutcome(): MlAnalysisOutcome? = mlAnalysis.recordedOutcome()
}

/** 요청한 공고가 없다 — 표면이 404 로 옮긴다. 평가를 시작하지 않았다는 뜻이다. */
class NoticeNotFoundException : RuntimeException("요청한 공고를 찾을 수 없다")

/**
 * 공고 단건 평가 요청의 결과 갈래(M7/7C ②) — **예외가 아니라 값이다.** 「공고가 없다」는
 * 정상적인 요청의 정상적인 답 가운데 하나이고, 그것을 예외로 흘리면 호출부가 `catch` 로
 * 분기하게 된다. HTTP 코드로 옮기는 것은 표면의 일이다(오류 **형태**를 만드는 자리는
 * `app.http.ErrorBody` 하나라는 규율은 그대로다 — 표면이 그 자리에 닿는 예외를 던진다).
 */
sealed interface BidPriceRequestOutcome {
    data object NoticeNotFound : BidPriceRequestOutcome

    data class Prepared(
        val run: BidPriceRequestRun,
    ) : BidPriceRequestOutcome
}

/**
 * 요청마다 단건 평가 묶음을 짓는다(M7/7C ②, [EvaluationDryRunFactory] 관례) — 컨트롤러는
 * [forRequest] 하나만 부른다.
 *
 * **기존 평가 커널을 무편집으로 재사용한다.** `EvaluateCandidatesUseCase` 는 「후보 목록을 받아
 * 각각 판정」이고, 목록 길이 1 은 특별한 경우가 아니다 — 그래서 단건 평가에 두 번째 판정 경로가
 * 없다. 그 사실이 차분 test(같은 입력에서 전건 dry-run 과 같은 결과)가 재는 성질이다.
 *
 * **자격은 요청당 정확히 한 번 판정한다.** [licenseJudgements] 로 봉투를 얻고 그 `verdict` 를
 * [PinnedLicenseGate] 로 커널에 넘긴다 — 커널의 `licenseGate.verdictFor(...)` 호출은 이 값을
 * 돌려받을 뿐 요건 저장소를 다시 두드리지 않는다([PinnedStrategyRepository] 와 같은 형태).
 *
 * **분석 예산을 적용하지 않는다(`D-7C2-1` Q2).** `EvaluateCandidatesUseCase` 의 `analysisBudget`
 * 은 **스캔 비용 통제**(전건 후보 순회에서 뒤쪽 후보를 평가하지 않는다)이고, 운영자가 지목한
 * 공고 하나의 허가 조건이 아니다. 전략의 후보 상한이 1 이어도 요청한 공고는 평가된다 — 전건
 * dry-run 과 **의도적으로 어긋나는 유일한 축**이고 test 가 그 차이를 고정한다.
 *
 * **`ladderPolicySlot` 을 받지 않는다** — 사다리 정책을 밖에서 바꿀 인자를 이 경로에 열지 않는다
 * (use case 기본값 그대로).
 */
@Suppress("LongParameterList")
class BidPriceRequestFactory(
    private val notices: NoticeByIdPort,
    private val strategyRepository: StrategyReader,
    private val licenseJudgements: LicenseJudgementPort,
    private val watchSubjects: WatchSubjectPort,
    private val mlAnalysis: MlAnalysisPort,
    private val correlationIds: CorrelationIdFactory,
    private val clock: Clock,
    private val notificationInbox: NotificationInboxPort,
) {
    /**
     * 이 요청을 운영자의 알림함에 한 항목으로 적는다(M7/7C③-b · R-4 의 종착점).
     *
     * **주체를 인자로 받는다** — 이 클래스가 세션·요청에서 계정을 꺼낼 길이 없다. 계정이 없는
     * 인증 경로(운영자 자격 헤더)에서는 표면이 아예 **부르지 않고**, 그래서 「계정 없이 알림을
     * 적는다」가 호출 자리의 `if` 가 아니라 **시그니처**로 막힌다.
     *
     * **사실은 하나로 고정이다**(`BidPriceRequestRecorded`). 요청이 종류를 고를 자리가 없으므로
     * 이 표면으로는 다른 종류의 알림을 위조할 수 없다 — 쓰기 포트 자체는 controller 가 받을 수
     * 없고(`app.http.denied-types`) 이 좁은 메서드만 지난다.
     */
    fun recordInbox(
        account: AccountId,
        notice: NoticeId,
    ): NotificationInboxOutcome =
        notificationInbox.record(
            NotificationInboxRecord(account, notice, NotificationFact.BidPriceRequestRecorded, clock.now()),
        )

    fun forRequest(
        id: NoticeId,
        currentActiveBids: Int,
    ): BidPriceRequestOutcome {
        // 요청 형태(400)를 저장소 상태보다 먼저 거부한다 — `EvaluationDryRunFactory` 와 같은 순서.
        if (currentActiveBids < 0) {
            throw InvalidEvaluationRequestException("currentActiveBids는 음수일 수 없다: $currentActiveBids")
        }
        val notice = notices.find(id) ?: return BidPriceRequestOutcome.NoticeNotFound
        val strategy = strategyRepository.load()
        val maxActiveBids = strategy.maxActiveBids?.value ?: throw MaxActiveBidsNotConfiguredException()
        val judgement = licenseJudgements.judgementFor(notice)
        val notifications = RecordingNotificationRequestPort()
        val recordingMl = RecordingMlAnalysis(mlAnalysis)
        val useCase =
            EvaluateCandidatesUseCase(
                strategies = PinnedStrategyRepository(strategy),
                candidateSource = SingleNoticeCandidateSource(notice),
                watchSubjects = watchSubjects,
                licenseGate = PinnedLicenseGate(judgement.verdict),
                mlAnalysis = recordingMl,
                capacity = RequestCapacityPort(currentActiveBids, maxActiveBids),
                notifications = notifications,
                correlationIds = correlationIds,
                clock = clock,
                analysisBudget = null,
            )
        return BidPriceRequestOutcome.Prepared(
            BidPriceRequestRun(
                useCase,
                notifications,
                recordingMl,
                strategy,
                notice,
                judgement,
                resolvedFloorTrustPolicy(),
            ),
        )
    }

    /**
     * 표시 경로가 쓰는 DEC-10 신뢰 정책 — **평가 경로와 같은 상수**(`PUBLISHED_FLOOR_TRUST_POLICY`)
     * 를 같은 방식(UTC 기준일)으로 해소한다. 정책 값을 이 파일에 다시 적지 않는다(D-7C2B-1).
     *
     * `NotApplicable` 은 **구조적으로 불가능하다** — 이 표의 유일한 항목이 `EffectiveFrom.Initial`
     * 이고 그것은 모든 기준일에 적용된다(`Policy.resolve`). 그래서 표시 어휘에 「정책을 해소하지
     * 못했다」는 다섯째 상태를 만들지 않았다. 항목이 날짜로 바뀌는 날 화면이 조용히 틀린 상태를
     * 내는 대신 이 자리가 멈추고, 그 전제 자체는 `BidPriceRequestResponseTest` 가 잠근다.
     */
    private fun resolvedFloorTrustPolicy(): Resolution.Resolved<PublishedFloorTrustPolicyData> {
        val referenceDate = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC)
        return when (val resolution = PUBLISHED_FLOOR_TRUST_POLICY.resolve(referenceDate)) {
            is Resolution.Resolved -> {
                resolution
            }

            is Resolution.NotApplicable -> {
                error("신뢰 정책 표는 Initial 항목을 가져 모든 기준일에 해소된다: ${resolution.reason}")
            }
        }
    }
}
