package bidvector.app.http

import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseJudgement
import bidvector.qualification.LicenseName
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.RequirementSourceField
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.evaluation.LicenseJudgementPort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.NoticeByIdPort
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** 7C ② test 가 공유하는 fixture·이중체. `@Test` 가 없어 게이트 등록부의 대상이 아니다. */
val BID_PRICE_TEST_DEADLINE: Instant = Instant.parse("2026-12-01T00:00:00Z")

fun bidPriceNoticeId(
    number: String = "BP-1",
    round: String = "000",
): NoticeId = NoticeId(NoticeNumber.of(number), NoticeRound.of(round))

/**
 * 공고 fixture — `Notice` 를 직접 짓지 않는다(유일한 공개 생성 경로 `collected` 를 지난다).
 * [floorRateFraction] 과 [floorRateOrigin] 을 따로 받는 이유는 **게시값 출처와 법정 표 출처를
 * 같은 자리에 넣어 볼 수 있어야** 하기 때문이다(근거 칸이 출처를 가르는지 재는 test).
 */
fun bidPriceNotice(
    id: NoticeId = bidPriceNoticeId(),
    baseAmountWon: Long? = 1_500L,
    floorRate: FloorRate? = null,
    deadlineAt: Instant? = BID_PRICE_TEST_DEADLINE,
): Notice =
    Notice.collected(
        NoticeCollected(
            id = id,
            businessCategory = null,
            baseAmount =
                baseAmountWon?.let {
                    ResolvedBaseAmount.Direct.of(
                        it,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.Published(id.round),
                    )
                },
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = floorRate,
            deadlineAt = deadlineAt,
            openingScheduledAt = null,
            raw =
                RawNoticeObservation.of(
                    mapOf(
                        RawKey("bidNtceNo") to id.number.value,
                        RawKey("bidNtceOrd") to id.round.value,
                    ),
                    SourceEndpoint.NOTICE_LIST,
                    Instant.parse("2026-10-23T00:00:00Z"),
                ),
        ),
    )

fun publishedFloorRate(
    fraction: String = "0.87745",
    round: NoticeRound = NoticeRound.of("000"),
): FloorRate = FloorRate(Rate.ofFraction(BigDecimal(fraction)), FloorRateOrigin.NoticeValue(round))

fun statutoryFloorRate(fraction: String = "0.87745"): FloorRate =
    FloorRate(
        Rate.ofFraction(BigDecimal(fraction)),
        FloorRateOrigin.StatutoryTable(EffectiveFrom.On(LocalDate.parse("2026-01-01"))),
    )

val BID_PRICE_TEST_POLICY_VERSION: PolicyVersion =
    PolicyVersion(EffectiveFrom.On(LocalDate.parse("2026-08-26")), "test-license-policy")

/**
 * 자격 판정 봉투 fixture. `missingByGroup` 을 직접 받는 이유는 **그룹 둘 이상일 때 미보유 면허를
 * 접지 않는다**는 성질을 재야 하기 때문이다.
 */
fun licenseJudgementFixture(
    verdict: LicenseVerdict = LicenseVerdict.Eligible(setOf(RequirementGroupId.Ungrouped)),
    requiredLicenses: List<LicenseName>? = listOf(LicenseName("토목공사업")),
    missingByGroup: Map<RequirementGroupId, Set<LicenseName>> = emptyMap(),
    unparsableRowCount: Int = 0,
    sourceFields: Set<RequirementSourceField> = setOf(RequirementSourceField.LcnsLmtNm),
): LicenseJudgement =
    LicenseJudgement(
        verdict = verdict,
        requiredLicenses = requiredLicenses,
        missingByGroup = missingByGroup,
        unparsableRowCount = unparsableRowCount,
        requirementSourceFields = sourceFields,
        policyVersion = BID_PRICE_TEST_POLICY_VERSION,
    )

/**
 * 전략 fixture — `OperatorStrategy` 를 직접 짓지 않고 [validate] 를 지난다.
 * [maxActiveBids] 가 `null` 이면 평가가 fail-closed 로 거부되는 전략이고,
 * [candidateLimit] 은 전건 스캔의 분석 예산이다(단건 경로에는 적용되지 않는다).
 */
fun bidPriceStrategy(
    maxActiveBids: Int? = 10,
    candidateLimit: Int? = null,
): OperatorStrategy {
    val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
    val draft =
        StrategyDraft(
            focusCategories = listOf("CAT-1"),
            maxActiveBids = maxActiveBids,
            candidateLimit = candidateLimit,
        )
    return when (val result = validate(draft, StrategyRevision(1), policy)) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture 가 유효하지 않다: ${result.violations}")
    }
}

/** 공고를 식별자로 내는 이중체 — 호출 계수를 센다(요청당 상한 측정). */
class FakeNoticeByIdPort(
    private val notices: Map<NoticeId, Notice> = emptyMap(),
) : NoticeByIdPort {
    var callCount: Int = 0
        private set

    override fun find(id: NoticeId): Notice? {
        callCount += 1
        return notices[id]
    }
}

/**
 * Spring 조립용 이중체 — test 가 공고를 밀어 넣는다(`TestStrategyRepository` 관례).
 * 기본값이 `null` 이라 아무것도 설정하지 않은 test(401 전수·계약 순회 등)에서는 404 다.
 */
class MutableNoticeByIdPort : NoticeByIdPort {
    @Volatile
    var notice: Notice? = null

    override fun find(id: NoticeId): Notice? = notice?.takeIf { it.id == id }
}

/** 같은 목적의 자격 판정 이중체 — 기본값은 통과 판정이다. */
class MutableLicenseJudgementPort : LicenseJudgementPort {
    @Volatile
    var judgement: LicenseJudgement = licenseJudgementFixture()

    override fun judgementFor(notice: Notice): LicenseJudgement = judgement
}

/** 자격 판정 봉투를 내는 이중체 — **요청당 한 번만 불려야 한다**는 성질을 이 계수가 잰다. */
class FakeLicenseJudgementPort(
    private val judgement: LicenseJudgement = licenseJudgementFixture(),
) : LicenseJudgementPort {
    var callCount: Int = 0
        private set

    override fun judgementFor(notice: Notice): LicenseJudgement {
        callCount += 1
        return judgement
    }
}

/** 감시 입력 조립 이중체 — 실 production 매퍼를 감싸 계수만 더한다(거동을 바꾸지 않는다). */
class CountingWatchSubjectPort(
    private val delegate: WatchSubjectPort,
) : WatchSubjectPort {
    var callCount: Int = 0
        private set

    override fun subjectFor(notice: Notice): WatchSubjectOutcome {
        callCount += 1
        return delegate.subjectFor(notice)
    }
}

/** ML 이중체 — 결과를 고정하고 계수를 센다. */
class CountingMlAnalysisPort(
    private val outcome: MlAnalysisOutcome,
) : MlAnalysisPort {
    var callCount: Int = 0
        private set

    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome {
        callCount += 1
        return outcome
    }
}

/**
 * 응답이 **wire 에 내는 필드 이름 전부** — 최상위 + 펴지는 묶음 여섯의 선언 필드를 모은다.
 * 중복은 지우지 않는다(「키 충돌 0」 단언이 그 중복을 본다).
 *
 * 리플렉션으로 모으는 이유: 이름 목록을 test 가 손으로 복제하면 필드를 더해도 초록이다.
 */
fun bidPriceResponseFieldNames(): List<String> {
    val groups =
        listOf(
            EvaluationDetailFields::class.java,
            NotificationFields::class.java,
            QualificationFields::class.java,
            BaseAmountFields::class.java,
            FloorRateFields::class.java,
            PriceAxesFields::class.java,
        )
    val unwrapped = groups.flatMap { type -> type.declaredFields.map { it.name } }
    val topLevel =
        BidPriceRequestResponse::class.java.declaredFields
            .map { it.name }
            .filterNot { it in BID_PRICE_GROUP_PROPERTY_NAMES }
    return topLevel + unwrapped
}

/** 펴지는 묶음 프로퍼티의 이름 — 이 이름들은 **wire 에 나타나지 않는다**(펴진다). */
val BID_PRICE_GROUP_PROPERTY_NAMES: Set<String> =
    setOf("evaluation", "notification", "qualification", "baseAmount", "floorRate", "priceAxes")

