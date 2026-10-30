package bidvector.app.http

import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.app.wiring.BidPriceRequestFactory
import bidvector.app.wiring.BidPriceRequestOutcome
import bidvector.app.wiring.BidPriceRequestRun
import bidvector.decision.MlUnavailableReason
import bidvector.decision.PUBLISHED_FLOOR_TRUST_POLICY
import bidvector.procurement.Notice
import bidvector.qualification.LicenseName
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.LmtGrpNo
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.RequirementSourceField
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.MlAnalysisOutcome
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 응답 조립의 순수 성질 — **값이 서지 않는 자리는 상태와 사유로만 말한다**, **게시값이 아닌
 * 하한율은 근거가 되지 않는다**, **그룹 간 OR 을 합집합으로 접지 않는다**, **고지는 전수다**.
 *
 * Spring 을 띄우지 않는다(조립 함수가 순수하다) — HTTP 층의 상태 코드·키 집합은
 * `BidPriceRequestEndpointTest` 와 `OpenApiBidPriceContractTest` 가 잰다.
 */
class BidPriceRequestResponseTest {
    private val clock = FixedClock(Instant.parse("2026-10-23T00:00:00Z"))

    private fun responseFor(
        notice: Notice,
        judgement: bidvector.qualification.LicenseJudgement = licenseJudgementFixture(),
        ml: MlAnalysisOutcome = MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided),
        currentActiveBids: Int = 0,
    ): BidPriceRequestResponse {
        val run = runFor(notice, judgement, ml, currentActiveBids)
        val evaluation = runBlocking { run.evaluate() }
        return bidPriceRequestResponseOf(run, evaluation, currentActiveBids)
    }

    private fun runFor(
        notice: Notice,
        judgement: bidvector.qualification.LicenseJudgement,
        ml: MlAnalysisOutcome,
        currentActiveBids: Int,
    ): BidPriceRequestRun {
        val outcome =
            BidPriceRequestFactory(
                notices = FakeNoticeByIdPort(mapOf(notice.id to notice)),
                strategyRepository = TestStrategyRepository(bidPriceStrategy()),
                licenseJudgements = FakeLicenseJudgementPort(judgement),
                watchSubjects = NoticeWatchSubjectPort(),
                mlAnalysis = CountingMlAnalysisPort(ml),
                correlationIds = SequentialCorrelationIdFactory(),
                clock = clock,
            ).forRequest(notice.id, currentActiveBids)
        return (outcome as BidPriceRequestOutcome.Prepared).run
    }

    @Test
    fun `가격 세 축은 값이 아니라 상태와 사유로 답한다 — 값 필드가 없다`() {
        val response = responseFor(bidPriceNotice())

        response.priceAxes.bidPriceMenuState shouldBe "NOT_COMPUTED"
        response.priceAxes.bidPriceMenuNotComputedReason shouldBe "MENU_PATH_NOT_WIRED"
        response.priceAxes.floorShortfallState shouldBe "NOT_COMPUTED"
        response.priceAxes.floorShortfallNotComputedReason shouldBe "SHORTFALL_POLICY_NOT_PUBLISHED"
        response.priceAxes.confidenceState shouldBe "NOT_AVAILABLE"
    }

    /**
     * 「없음」이 「0」으로 읽히는 문을 닫는다 — 세 축의 **숫자 칸이 응답 타입에 존재하지
     * 않는다**. 필드 이름 전수로 재므로 누가 `floorShortfallFrequency...` 를 더하면 붉어진다.
     */
    @Test
    fun `산출하지 않는 축의 숫자 칸은 응답 타입에 없다`() {
        val forbidden =
            bidPriceResponseFieldNames().filter { name ->
                name.startsWith("floorShortfallFrequency") ||
                    name.startsWith("priceFitness") ||
                    name.startsWith("bidPriceMenuOption") ||
                    name.startsWith("safe") ||
                    name.startsWith("recommended") ||
                    name.startsWith("aggressive")
            }

        forbidden shouldBe emptyList()
    }

    /** ML-03 — 응답 어디에도 「확률」 어휘가 없다. 신뢰도가 서는 날 그것은 가격 적합도다. */
    @Test
    fun `응답 필드 이름에 확률 어휘가 없다`() {
        val offenders =
            bidPriceResponseFieldNames().filter { name ->
                name.contains("probability", ignoreCase = true) || name.contains("확률")
            }

        offenders shouldBe emptyList()
    }

    @Test
    fun `고지는 전수다 — 하나를 빼면 붉어진다`() {
        responseFor(bidPriceNotice()).disclosures shouldBe
            listOf(
                "YEGA_DRAWN_PRIVATELY",
                "NOT_AUTO_SUBMITTED",
                "STATUTORY_FLOOR_RATE_NOT_INTERPRETED",
                "PRICE_FITNESS_IS_NOT_PROBABILITY",
            )
    }

    @Test
    fun `미판정 자격 축 셋을 그대로 싣는다 — 자격 카드가 면허만 보면 끝으로 읽히지 않게`() {
        responseFor(bidPriceNotice()).qualification.unjudgedAxes shouldBe
            listOf("QUAL_08_REGION", "QUAL_10_PERFORMANCE", "QUAL_11_CONSTRUCTION_CAPACITY")
    }

    @Test
    fun `게시 하한율은 근거가 되고 값과 출처를 함께 싣는다`() {
        val response = responseFor(bidPriceNotice(floorRate = publishedFloorRate("0.87745")))

        response.floorRate.floorRateGroundsState shouldBe "USABLE"
        response.floorRate.publishedFloorRateFraction shouldBe "0.87745"
        response.floorRate.publishedFloorRateOrigin shouldBe "NOTICE_VALUE"
        response.floorRate.publishedFloorRateNoticeRound shouldBe "000"
    }

    /**
     * **게시값만 근거다**(운영자 결정 2026-10-15) — 법정 하한율 표에서 온 값은 근거 칸에 싣지
     * 않는다. 이 분기를 지우면(값을 그대로 싣게 바꾸면) 이 단언이 붉어진다.
     */
    @Test
    fun `법정 하한율 출처의 값은 근거 칸에 싣지 않는다`() {
        val response = responseFor(bidPriceNotice(floorRate = statutoryFloorRate()))

        response.floorRate.floorRateGroundsState shouldBe "ORIGIN_NOT_PUBLISHED"
        withClue("출처가 게시값이 아니면 율 자체를 싣지 않는다") {
            response.floorRate.publishedFloorRateFraction shouldBe null
        }
        withClue("출처는 숨기지 않는다 — 왜 근거가 못 되는지 보여야 한다") {
            response.floorRate.publishedFloorRateOrigin shouldBe "STATUTORY_TABLE"
        }
    }

    @Test
    fun `하한율이 없으면 ABSENT 다 — 「게시값이 아니다」와 다른 값`() {
        val response = responseFor(bidPriceNotice(floorRate = null as FloorRate?))

        response.floorRate.floorRateGroundsState shouldBe "ABSENT"
        response.floorRate.publishedFloorRateFraction shouldBe null
        response.floorRate.publishedFloorRateOrigin shouldBe null
    }

    /**
     * **출처가 게시값이라는 것만으로 근거가 되지 않는다**(DEC-10 게이트, D-7C2B-1·2). 하한율
     * `1.00000` 은 라이브에 실재하고(열린 공고 32건) 「예정가 전액 이상 투찰」이라 하한이
     * 성립하지 않는다 — 그 값이 근거 칸에 숫자로 실리면 화면이 없는 하한을 있다고 말한다.
     */
    @Test
    fun `개연 불가 게시 하한율은 IMPLAUSIBLE 이고 근거 칸을 비운다`() {
        val response = responseFor(bidPriceNotice(floorRate = publishedFloorRate("1.00000")))

        response.floorRate.floorRateGroundsState shouldBe "IMPLAUSIBLE"
        withClue("근거로 설 수 없는 값은 근거 칸에 싣지 않고 상태가 말한다") {
            response.floorRate.publishedFloorRateFraction shouldBe null
        }
        withClue("출처와 차수는 원문 사실이라 남는다 — 왜 근거가 못 되는지 보여야 한다") {
            response.floorRate.publishedFloorRateOrigin shouldBe "NOTICE_VALUE"
            response.floorRate.publishedFloorRateNoticeRound shouldBe "000"
        }
    }

    /** 밴드는 **양 끝을 포함한다**(D-DEC10-1) — 경계값이 근거에서 빠지면 공고가 조용히 샌다. */
    @Test
    fun `개연 밴드 양 끝은 근거가 된다`() {
        listOf("0.30" to "0.3", "0.995" to "0.995").forEach { (published, emitted) ->
            val response = responseFor(bidPriceNotice(floorRate = publishedFloorRate(published)))

            withClue(published) {
                response.floorRate.floorRateGroundsState shouldBe "USABLE"
                response.floorRate.publishedFloorRateFraction shouldBe emitted
            }
        }
    }

    /**
     * 밴드 **바로 밖**은 근거가 아니다. 표시가 평가와 다른 밴드를 보면(리터럴을 따로 적으면)
     * 이 두 자리가 먼저 붉어진다 — 그것이 이 경계 표본의 목적이다.
     */
    @Test
    fun `개연 밴드 바로 밖은 근거가 아니다 — 표시가 평가와 같은 밴드를 본다`() {
        listOf("0.29999", "0.99501").forEach { published ->
            val response = responseFor(bidPriceNotice(floorRate = publishedFloorRate(published)))

            withClue(published) {
                response.floorRate.floorRateGroundsState shouldBe "IMPLAUSIBLE"
                response.floorRate.publishedFloorRateFraction shouldBe null
            }
        }
    }

    /**
     * **다섯째 상태를 만들지 않는 전제**(D-7C2B-1) — 표시 어휘는 게이트 판정 네 갈래뿐이고
     * 「정책을 해소하지 못했다」는 자리가 없다. 그것이 성립하는 이유는 신뢰 정책 표의 유일한
     * 항목이 `EffectiveFrom.Initial` 이라 **모든 기준일에 적용**되기 때문이다. 항목을 날짜로
     * 바꾸는 날 이 단언이 먼저 붉어진다 — 표시가 조용히 500 을 내는 대신.
     */
    @Test
    fun `표시가 쓰는 신뢰 정책은 어느 기준일에도 해소된다`() {
        listOf(LocalDate.MIN, LocalDate.EPOCH, LocalDate.parse("2026-10-23"), LocalDate.MAX)
            .forEach { referenceDate ->
                withClue(referenceDate.toString()) {
                    PUBLISHED_FLOOR_TRUST_POLICY
                        .resolve(referenceDate)
                        .shouldBeInstanceOf<Resolution.Resolved<*>>()
                }
            }
    }

    /**
     * 위 단언은 **상수만** 잰다(CR r1 LOW) — 표시 조립이 그 상수를 쓴다는 사실은 따로 잠근다.
     * 요청 묶음에 실린 정책이 **출하 상수의 해소 결과와 값·version 까지 같다**: 표시가 다른 상수나
     * 위조한 밴드를 쓰면 이 단언이 붉어진다.
     *
     * **잠기지 않는 것**: 기준일 계산이다. 표의 유일 항목이 `Initial` 인 동안은 어느 기준일로 바꿔도
     * 같은 값이 나오므로 이 단언으로 구별되지 않는다(표가 날짜를 갖는 날 위 전제 test 가 먼저 붉다).
     */
    @Test
    fun `표시 조립에 실리는 정책은 출하 상수의 해소 결과다 — 배선까지`() {
        val run =
            runFor(
                bidPriceNotice(),
                licenseJudgementFixture(),
                MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided),
                currentActiveBids = 0,
            )

        run.floorTrustPolicy shouldBe PUBLISHED_FLOOR_TRUST_POLICY.resolve(LocalDate.parse("2026-10-23"))
    }

    @Test
    fun `기초금액은 값과 basis·과세·provenance·해석 갈래를 함께 싣는다`() {
        val response = responseFor(bidPriceNotice(baseAmountWon = 1_500L))

        response.baseAmount.baseAmountWon shouldBe 1_500L
        response.baseAmount.baseAmountBasis shouldBe "BASE_AMOUNT"
        response.baseAmount.baseAmountCurrency shouldBe "KRW"
        response.baseAmount.baseAmountVatTreatment shouldBe "INCLUSIVE"
        response.baseAmount.baseAmountProvenance shouldBe "PUBLISHED"
        response.baseAmount.baseAmountResolution shouldBe "DIRECT"
    }

    @Test
    fun `자격 근거는 출처 필드·요건 면허명·정책 version·유효기간 미검증을 싣는다`() {
        val response =
            responseFor(
                bidPriceNotice(),
                judgement =
                    licenseJudgementFixture(
                        requiredLicenses = listOf(LicenseName("토목공사업")),
                        sourceFields = setOf(RequirementSourceField.LcnsLmtNm),
                        unparsableRowCount = 2,
                    ),
            )

        response.qualification.licenseVerdict shouldBe "ELIGIBLE"
        response.qualification.licenseRequiredLicenses shouldBe listOf("토목공사업")
        response.qualification.licenseRequirementSourceFields shouldBe listOf("LcnsLmtNm")
        response.qualification.licenseUnparsableRowCount shouldBe 2
        response.qualification.licenseValidity shouldBe "NOT_VERIFIED"
        response.qualification.licensePolicySource shouldBe "test-license-policy"
        response.qualification.licensePolicyEffectiveFrom shouldBe "2026-08-26"
    }

    /** `Uncertain` 은 `Ineligible` 이 아니다(U-5) — 접히지 않고 사유를 함께 낸다. */
    @Test
    fun `판정 불가는 미달과 다른 값이고 사유를 싣는다`() {
        val response =
            responseFor(
                bidPriceNotice(),
                judgement =
                    licenseJudgementFixture(
                        verdict = LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared),
                        requiredLicenses = null,
                    ),
            )

        response.qualification.licenseVerdict shouldBe "UNCERTAIN"
        response.qualification.licenseUncertainReason shouldBe "OPERATOR_LICENSES_NOT_DECLARED"
        response.qualification.licenseRequiredLicenses shouldBe null
    }

    @Test
    fun `미충족 그룹이 하나면 미보유 면허를 싣는다`() {
        val group = RequirementGroupId.Numbered(LmtGrpNo("1"))
        val response =
            responseFor(
                bidPriceNotice(),
                judgement =
                    licenseJudgementFixture(
                        verdict = LicenseVerdict.Ineligible(mapOf(group to setOf(LicenseName("전기공사업")))),
                        missingByGroup = mapOf(group to setOf(LicenseName("전기공사업"))),
                    ),
            )

        response.qualification.licenseMissingDetailState shouldBe "SINGLE_GROUP"
        response.qualification.licenseMissingLicenses shouldBe listOf("전기공사업")
        response.qualification.licenseGroupsMissing shouldBe listOf("G1")
    }

    /**
     * **그룹 간 OR 을 합집합으로 접지 않는다** — 둘 이상이면 「이 면허들이 다 필요하다」로
     * 읽히므로 목록을 비우고 상태로 말한다. 합집합을 싣도록 바꾸면 이 단언이 붉어진다.
     */
    @Test
    fun `미충족 그룹이 둘 이상이면 미보유 면허를 접지 않고 상태로 말한다`() {
        val first = RequirementGroupId.Numbered(LmtGrpNo("1"))
        val second = RequirementGroupId.Numbered(LmtGrpNo("2"))
        val missing: Map<RequirementGroupId, Set<LicenseName>> =
            mapOf(
                first to setOf(LicenseName("전기공사업")),
                second to setOf(LicenseName("토목공사업")),
            )
        val response =
            responseFor(
                bidPriceNotice(),
                judgement =
                    licenseJudgementFixture(
                        verdict = LicenseVerdict.Ineligible(missing),
                        missingByGroup = missing,
                    ),
            )

        response.qualification.licenseMissingDetailState shouldBe "MULTIPLE_GROUPS_NOT_FLATTENED"
        response.qualification.licenseMissingLicenses shouldBe emptyList()
        response.qualification.licenseGroupsMissing shouldBe listOf("G1", "G2")
    }

    /** ML 단계에 **이르지 못한 것**과 **갔으나 미가용**은 다른 사실이다. */
    @Test
    fun `ML 단계에 이르지 못하면 NOT_REACHED 이고 신뢰도 사유도 그것을 말한다`() {
        val response = responseFor(bidPriceNotice(deadlineAt = Instant.parse("2026-01-01T00:00:00Z")))

        response.evaluation.outcome shouldBe "NOT_REACHED"
        response.priceAxes.mlAnalysisState shouldBe "NOT_REACHED"
        response.priceAxes.mlUnavailableReason shouldBe null
        response.priceAxes.confidenceNotAvailableReason shouldBe "EVALUATION_NOT_REACHED_ML_STAGE"
    }

    @Test
    fun `ML 이 미가용이면 사유를 싣고 신뢰도 사유가 그것을 따른다`() {
        val response = responseFor(bidPriceNotice())

        response.priceAxes.mlAnalysisState shouldBe "UNAVAILABLE"
        response.priceAxes.mlUnavailableReason shouldBe "SCORE_NOT_PROVIDED"
        response.priceAxes.confidenceNotAvailableReason shouldBe "ML_UNAVAILABLE"
    }

    @Test
    fun `알림함 기록 공백을 상태로 드러낸다 — 가짜 포트를 두지 않는다`() {
        val response = responseFor(bidPriceNotice())

        response.notification.inboxRecordState shouldBe "NOT_RECORDED_YET"
        response.notification.inboxRecordNotRecordedReason shouldBe "INBOX_WRITE_PORT_NOT_MERGED"
    }
}
