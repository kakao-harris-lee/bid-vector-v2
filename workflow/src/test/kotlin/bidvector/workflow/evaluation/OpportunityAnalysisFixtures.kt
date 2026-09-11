package bidvector.workflow.evaluation

import bidvector.decision.UnitScore
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.decision.priority.derive.DerivationOutcome
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.CategoryCode
import bidvector.workflow.embedding.EmbedTextPort
import bidvector.workflow.embedding.EmbedTextRequest
import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingVector
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionPort
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.BidRateCandidates
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.prediction.IntervalSource
import bidvector.workflow.prediction.ModelReleaseRef
import bidvector.workflow.prediction.PriceFitness
import bidvector.workflow.prediction.Uncertainty
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.sqrt

/**
 * `OpportunityAnalysisTest`·`EmbeddingBridgeTest`가 함께 쓰는 fake port·조립 헬퍼 —
 * `internal`(같은 모듈 test 소스셋). [testNotice]·[FixedClock]·[FakeWatchSubjectPort]·
 * [FakeCapacityPort]·[MATCHING_SUBJECT]·[NOW]·[FUTURE_DEADLINE]은
 * `EvaluationTestFixtures.kt`(4B-3)를 재사용한다 — 중복 금지.
 */
internal val TEST_RELEASE =
    ModelReleaseRef(
        releaseId = "release-1",
        artifactChecksum = "checksum-1",
        featureSchemaVersion = "schema-1",
        codeVersion = "code-1",
        datasetId = "dataset-1",
    )

internal val OTHER_RELEASE =
    ModelReleaseRef(
        releaseId = "release-2",
        artifactChecksum = "checksum-2",
        featureSchemaVersion = "schema-2",
        codeVersion = "code-2",
        datasetId = "dataset-2",
    )

/** norm≈1 로 정규화한 [EmbeddingVector] — [EmbeddingVector.init]의 거친 ε(0.01) 안에 들어온다. */
internal fun normalizedEmbeddingVector(vararg raw: Float): EmbeddingVector {
    val norm = sqrt(raw.sumOf { component -> component.toDouble() * component.toDouble() })
    return EmbeddingVector(raw.map { component -> (component / norm).toFloat() })
}

internal fun embedded(
    vector: EmbeddingVector = normalizedEmbeddingVector(1.0f),
    release: ModelReleaseRef = TEST_RELEASE,
): EmbeddingOutcome.Embedded = EmbeddingOutcome.Embedded(vector, release)

internal fun predicted(
    base: Rate = Rate.ofFraction(BigDecimal("0.85")),
    conservative: Rate = Rate.ofFraction(BigDecimal("0.80")),
    aggressive: Rate = Rate.ofFraction(BigDecimal("0.90")),
    fitness: BigDecimal = BigDecimal("0.7"),
    release: ModelReleaseRef = TEST_RELEASE,
): BidPredictionOutcome.Predicted =
    BidPredictionOutcome.Predicted(
        candidates = BidRateCandidates(conservative, base, aggressive),
        fitness = PriceFitness(fitness),
        uncertainty =
            Uncertainty(
                sampleSize = 10,
                dispersion = BigDecimal("0.05"),
                estimateMargin = BigDecimal("0.02"),
                intervalSource = IntervalSource.CrossValidationResidual,
            ),
        release = release,
    )

internal class FakeEmbedTextPort(
    val outcomeFor: (EmbedTextRequest) -> EmbeddingOutcome,
) : EmbedTextPort {
    val requestsSeen = mutableListOf<EmbedTextRequest>()
    val budgetsSeen = mutableListOf<CallBudget>()

    override suspend fun embed(
        request: EmbedTextRequest,
        budget: CallBudget,
    ): EmbeddingOutcome {
        requestsSeen += request
        budgetsSeen += budget
        return outcomeFor(request)
    }
}

internal class FakeBidPredictionPort(
    val outcomeFor: (BidPredictionRequest) -> BidPredictionOutcome,
) : BidPredictionPort {
    val requestsSeen = mutableListOf<BidPredictionRequest>()
    val budgetsSeen = mutableListOf<CallBudget>()
    var callCount = 0

    override suspend fun predict(
        request: BidPredictionRequest,
        budget: CallBudget,
    ): BidPredictionOutcome {
        callCount += 1
        requestsSeen += request
        budgetsSeen += budget
        return outcomeFor(request)
    }
}

/**
 * `ProfilePortsTest.kt`(4B-6a)의 `private class FakeOperatorProfilePort`와 이름이 겹쳐
 * (파일 private 도 JVM class 이름은 패키지 단위라 redeclaration) `Configurable` 접두로
 * 가른다 — 이 fake 는 `var`로 test 도중 프로필을 바꿀 수 있다는 점도 다르다.
 */
internal class ConfigurableProfilePort(
    var profile: ProfileFacts?,
) : OperatorProfilePort {
    override fun current(): ProfileFacts? = profile
}

internal class FakeWorkloadPort(
    var outcome: DerivationOutcome<UnitScore> = DerivationOutcome.Absent(DerivationAbsence.WorkloadNotCollected),
) : WorkloadPort {
    override fun current(): DerivationOutcome<UnitScore> = outcome
}

internal fun testProfile(
    businessTypes: Set<CategoryCode> = setOf(CategoryCode(DEFAULT_FOCUS_CATEGORY)),
    licenses: OperatorLicenses = OperatorLicenses.NotDeclared,
    regionTerms: List<String> = listOf("서울"),
): ProfileFacts = ProfileFacts(businessTypes, licenses, regionTerms)

/**
 * `testNotice`(4B-3)와 달리 금액·낙찰하한율을 실을 수 있는 공고 — 4B-6b가 예측·마진
 * 경로를 재려면 `baseAmount`가 있어야 한다(4B-3 fixture는 항상 null).
 */
internal fun testNoticeWithMoney(
    number: String = "20260101002",
    won: Long = 1_000_000_000L,
    floorRate: FloorRate? = null,
    deadlineAt: Instant? = FUTURE_DEADLINE,
): Notice {
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            NOW,
        )
    val resolvedBaseAmount =
        ResolvedBaseAmount.Direct.of(
            won,
            Currency.KRW,
            VatTreatment.EXCLUSIVE,
            Provenance.Published(NoticeRound.of("000")),
        )
    return Notice.collected(
        NoticeCollected(
            id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000")),
            businessCategory = null,
            baseAmount = resolvedBaseAmount,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = floorRate,
            deadlineAt = deadlineAt,
            openingScheduledAt = null,
            raw = observation,
        ),
    )
}
