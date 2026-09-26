package bidvector.workflow.evaluation

import bidvector.procurement.BusinessCategory
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CanonicalizationOutcome
import bidvector.procurement.FieldConcept
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeCollected
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.canonicalize
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private const val REFERENCE_DAY = "2026-09-24"
private const val SERVICE_CLASS_NUMBER = "81111500"
private const val SERVICE_CLASS_NAME = "정보시스템 개발 서비스"
private const val CONSTRUCTION_TYPE_NAME = "전기공사업"

/**
 * M6/6F-9 D-6F9-6 — 업무구분 수집이 **기회 분석의 거동**에 만드는 변화를 잠근다: 용역 공고에 공공조달분류 번호가
 * 생기면서 `competitionSampleSupplyFor` 의 조기 반환이 처음으로 통과해 표본 조회가 **실제로 돈다**. 공사 공고는
 * 코드가 없어 조회를 부르지 않는다. 이 slice 는 ML·표본 조회 쪽 코드를 바꾸지 않는다 — 여기 있는 것은 잠금뿐이다.
 *
 * 4B-7 의 인접 test 와 다른 점은 업종 코드를 손으로 짓지 않는 것이다 — 운영 필드 계약 → `canonicalize` 를 지나온
 * 값으로 판정하므로 canonicalize 가 분류 번호를 코드 칸에 넣기를 멈추면 4B-7 은 초록인 채 이 test 가 RED 다.
 */
class OpportunitySampleSupplyDivisionTest {
    private val policy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.parse(REFERENCE_DAY)) as Resolution.Resolved).value

    /** 원시 키는 코드에 박지 않고 그 개념의 운영 계약 행에서 읽는다(공고 목록 오퍼레이션 행). */
    private fun rawKeyOf(concept: FieldConcept): RawKey =
        RawKey(
            policy.fieldContracts
                .contractsFor(concept)
                .first { SourceEndpoint.NOTICE_LIST in it.presentIn }
                .rawName.name,
        )

    private fun collected(
        division: BusinessDivision,
        fields: Map<FieldConcept, String>,
    ): NoticeCollected {
        val identity =
            mapOf(
                rawKeyOf(FieldConcept.NOTICE_NUMBER) to "20260101001",
                rawKeyOf(FieldConcept.NOTICE_ROUND) to "000",
            )
        val observation =
            RawNoticeObservation.of(
                identity + fields.mapKeys { rawKeyOf(it.key) },
                SourceEndpoint.NOTICE_LIST,
                Instant.EPOCH,
                sourceDivision = division,
            )
        return canonicalize(observation, policy)
            .shouldBeInstanceOf<CanonicalizationOutcome.Normalized>()
            .command
    }

    private fun serviceCategory(): BusinessCategory =
        requireNotNull(
            collected(
                BusinessDivision.SERVICE,
                mapOf(
                    FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE to SERVICE_CLASS_NUMBER,
                    FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME to SERVICE_CLASS_NAME,
                ),
            ).businessCategory,
        ) { "용역 공고의 업무구분 코드가 비어 있다" }

    @Test
    fun `용역 공고의 공공조달분류 번호가 표본 조회를 발동한다 — 조기 반환이 처음으로 통과한다`() {
        val category = serviceCategory()
        val samples = FakeCompetitionSamplePort()
        val notice = testNoticeWithMoney(number = "20260101021", businessCategory = category)

        analyzeNotice(analysis(samples = samples), notice).shouldBeAnalyzed()

        val query = samples.queriesSeen.single()
        query.categoryCode shouldBe category.code
        query.categoryCode.value shouldBe SERVICE_CLASS_NUMBER
        query.excludeNoticeId shouldBe notice.id
    }

    @Test
    fun `공사 공고는 업무구분 코드가 없어 표본 조회를 부르지 않는다 — 주공종은 코드가 아니다`() {
        val construction =
            collected(
                BusinessDivision.CONSTRUCTION,
                mapOf(FieldConcept.MAIN_CONSTRUCTION_TYPE to CONSTRUCTION_TYPE_NAME),
            )
        construction.businessCategory shouldBe null
        construction.mainConstructionType shouldNotBe null
        val samples = FakeCompetitionSamplePort()
        val prediction = FakeBidPredictionPort { predicted() }
        val notice = testNoticeWithMoney(number = "20260101022", businessCategory = construction.businessCategory)

        analyzeNotice(analysis(prediction = prediction, samples = samples), notice).shouldBeAnalyzed()

        samples.queriesSeen shouldBe emptyList()
        prediction.requestsSeen.single().competitionSamples shouldBe emptyList()
        requestedCategory(prediction) shouldBe null
    }

    /** 예측 요청까지 같은 값이 흐른다 — 용역은 값, 공사는 부재(wire 잠금은 `adapters/ml` 쪽 test). */
    @Test
    fun `예측 요청의 업무구분은 용역이면 분류 번호고 공사면 부재다`() {
        val category = serviceCategory()
        val prediction = FakeBidPredictionPort { predicted() }
        val notice = testNoticeWithMoney(number = "20260101023", businessCategory = category)

        analyzeNotice(analysis(prediction = prediction, samples = FakeCompetitionSamplePort()), notice)
            .shouldBeAnalyzed()

        requestedCategory(prediction)?.code shouldBe category.code
    }

    private fun requestedCategory(prediction: FakeBidPredictionPort): BusinessCategory? =
        prediction.requestsSeen.single().businessCategory
}
