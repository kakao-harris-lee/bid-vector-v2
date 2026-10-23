package bidvector.procurement

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 10, 23)

/** **실제 정책**으로 잰다 — 사상이 계약 경유라는 것은 실제 계약 행에서만 참이 된다. */
private val POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("KONEPS_COLLECTION_POLICY 가 해석되지 않는다: ${resolution.reason}")
    }

private val NOTICE_ID = NoticeId(NoticeNumber.of("OPENG-20261001-001"), NoticeRound.of("000"))

private val WALK_A: Instant = Instant.parse("2026-10-20T01:00:00Z")
private val WALK_B: Instant = Instant.parse("2026-10-21T02:00:00Z")

private var keySeed = 0

private fun observation(
    endpoint: SourceEndpoint,
    observedAt: Instant,
    fields: Map<String, String>,
    key: String? = null,
): OpeningAxisObservation =
    OpeningAxisObservation(
        RawNoticeObservation.of(
            fields = fields.mapKeys { (name, _) -> RawKey(name) } + identityFields(),
            sourceEndpoint = endpoint,
            observedAt = observedAt,
            sourceDivision = null,
        ),
        ObservationKey(key ?: "key-${keySeed++}"),
    )

private fun identityFields(): Map<RawKey, String> =
    mapOf(
        NOTICE_NUMBER_RAW_KEY to NOTICE_ID.number.value,
        NOTICE_ROUND_RAW_KEY to NOTICE_ID.round.value,
    )

private fun groupOf(vararg rows: OpeningAxisObservation): OpeningAxisGroup =
    OpeningAxisGroup(NOTICE_ID, rows.toList())

private fun succeeded(walk: Instant): AxisConclusion = AxisConclusion(AttemptOutcome.Succeeded, walk)

private fun promote(
    group: OpeningAxisGroup,
    conclusions: Map<SourceEndpoint, AxisConclusion> = emptyMap(),
): OpeningPromotionOutcome = promoteOpeningResult(group, conclusions, POLICY)

private fun promotedResult(
    group: OpeningAxisGroup,
    conclusions: Map<SourceEndpoint, AxisConclusion> = emptyMap(),
): OpeningResult = promote(group, conclusions).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>().result

/**
 * M7/7C⑤-0 — 이미 적재된 개찰 축 원문 → canonical `opening_result` 사상.
 *
 * 승인 결정은 `reports/evidence/m7/7c5p/scope.md` 와 팀장 결정 `D-7C5P-1`~`7` 이다. 걷기 선택의
 * 정본은 D-6G-68·D-6G-80 이고, 추첨번호 축을 승격하지 않는 근거는 D-7C5P-2(D-3F-4 (a) ↔ D-6G-38
 * 충돌)다.
 */
class OpeningPromotionTest {
    // =========================================================================
    // 사상 — 키별 값
    // =========================================================================
    @Test
    fun `예비가격 상세 원문이 공고 층 금액 슬롯과 자식 행을 채운다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf(
                            "plnprc" to "1,234,000",
                            "bssamt" to "1,200,000",
                            "rlOpengDt" to "2026-10-19 14:00:00",
                            "compnoRsrvtnPrceSno" to "3",
                            "bsisPlnprc" to "1,210,000",
                            "drwtYn" to "Y",
                        ),
                    ),
                ),
            )

        result.plannedPrice?.export()?.won shouldBe 1_234_000L
        result.baseAmount?.export()?.won shouldBe 1_200_000L
        result.actualOpeningAt shouldBe Instant.parse("2026-10-19T05:00:00Z")
        result.reservePrices shouldContainExactly
            listOf(
                OpeningReservePriceRow(
                    sequenceNumber = "3",
                    baseReservePrice = ReservePriceCandidateAmount(1_210_000L, Currency.KRW),
                    isDrawn = true,
                    observedAt = WALK_A,
                    drawCount = null,
                ),
            )
    }

    @Test
    fun `통화와 과세는 계약에서 온다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("bssamt" to "1000")),
                ),
            )
        val contract = POLICY.fieldContracts.contractFor(RawKey("bssamt"))

        result.baseAmount?.currency shouldBe Currency.KRW
        result.baseAmount?.vatTreatment shouldBe contract?.vatTreatment
    }

    @Test
    fun `provenance 는 계약 템플릿이 정한다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("plnprc" to "500", "bssamt" to "400"),
                    ),
                ),
            )

        // `bssamt` 는 PUBLISHED 템플릿 — 차수를 실은 게시값이다.
        result.baseAmount?.provenance shouldBe Provenance.Published(NOTICE_ID.round)
        // `plnprc` 는 NOT_APPLICABLE — 출처를 지어내지 않고 「선언되지 않았다」로 나른다.
        result.plannedPrice?.provenance shouldBe Provenance.Undeclared
    }

    @Test
    fun `개찰결과 목록 원문이 진행구분과 참가업체수를 채운다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        mapOf("progrsDivCdNm" to "개찰완료", "prtcptCnum" to "7"),
                    ),
                ),
            )

        result.progressDivision shouldBe "개찰완료"
        result.participantCount shouldBe 7
    }

    @Test
    fun `진행구분은 개찰완료 축의 다른 키로도 온다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("opengRsltDivNm" to "유찰", "prcbdrNm" to "표본업체", "opengRank" to "1"),
                    ),
                ),
            )

        result.progressDivision shouldBe "유찰"
    }

    @Test
    fun `같은 개념을 두 축이 나르면 늦게 관측된 축이 이긴다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    observation(SourceEndpoint.OPENING_AWARD_LIST, WALK_B, mapOf("prtcptCnum" to "9")),
                ),
            )

        result.participantCount shouldBe 9
    }

    // =========================================================================
    // 부재 · 해석 불가 — 0·빈 문자열로 적재하지 않는다
    // =========================================================================
    @Test
    fun `키가 없으면 슬롯이 부재다`() {
        val result = promotedResult(groupOf(observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, emptyMap())))

        result.participantCount.shouldBeNull()
        result.progressDivision.shouldBeNull()
        result.plannedPrice.shouldBeNull()
        result.baseAmount.shouldBeNull()
        result.actualOpeningAt.shouldBeNull()
        result.reservePrices.shouldContainExactly(emptyList())
    }

    @Test
    fun `공백 값은 부재다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        mapOf("progrsDivCdNm" to "   ", "prtcptCnum" to ""),
                    ),
                ),
            )

        result.progressDivision.shouldBeNull()
        result.participantCount.shouldBeNull()
    }

    @Test
    fun `해석되지 않는 값은 부재이고 0 이 아니다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("plnprc" to "미정", "rlOpengDt" to "어제"),
                    ),
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "일곱")),
                ),
            )

        result.participantCount.shouldBeNull()
        result.plannedPrice.shouldBeNull()
        result.actualOpeningAt.shouldBeNull()
    }

    @Test
    fun `음수 셈은 부재다`() {
        val result =
            promotedResult(
                groupOf(observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "-1"))),
            )

        result.participantCount.shouldBeNull()
    }

    @Test
    fun `소수부가 섞인 금액은 깎지 않고 부재다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("plnprc" to "1234.5", "bssamt" to "1000.00"),
                    ),
                ),
            )

        result.plannedPrice.shouldBeNull()
        // 소수부가 0 이면 원 단위 정수다 — 값이 사라지지 않는다.
        result.baseAmount?.export()?.won shouldBe 1_000L
    }

    @Test
    fun `해석되지 않는 최신 값이 더 오래된 축의 값에 가려지지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    observation(SourceEndpoint.OPENING_AWARD_LIST, WALK_B, mapOf("prtcptCnum" to "셋")),
                ),
            )

        result.participantCount.shouldBeNull()
    }

    // =========================================================================
    // 구조적 부재 — 계약에 없는 축
    // =========================================================================
    @Test
    fun `총예가건수와 추첨횟수는 계약에 없어 어떤 원문으로도 채워지지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf(
                            "compnoRsrvtnPrceSno" to "1",
                            "totRsrvtnPrceNum" to "15",
                            "drwtNum" to "4",
                        ),
                    ),
                ),
            )

        result.totalReservePriceCandidateCount.shouldBeNull()
        result.reservePrices.single().drawCount.shouldBeNull()
        POLICY.fieldContracts.contractFor(RawKey("totRsrvtnPrceNum")).shouldBeNull()
        POLICY.fieldContracts.contractFor(RawKey("drwtNum")).shouldBeNull()
    }

    @Test
    fun `사업자등록번호와 대표자명은 어느 계약에도 등재되지 않았다`() {
        POLICY.fieldContracts.contractFor(RawKey("bidwinnrBizno")).shouldBeNull()
        POLICY.fieldContracts.contracts.none { it.rawName.name.contains("Bizno") } shouldBe true
        POLICY.fieldContracts.contracts.none { it.rawName.name.contains("Ceo") } shouldBe true
    }

    @Test
    fun `추첨번호 축은 원문에 번호가 있어도 승격되지 않는다`() {
        // D-7C5P-2 — 개찰완료의 추첨번호 키는 **투찰자가 고른 번호**이지 추첨된 번호가 아니다
        // (D-6G-38). 승인 결정 둘이 어긋나 그 축을 비운다.
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to "표본업체", "opengRank" to "1", "drwtNo1" to "3", "drwtNo2" to "7"),
                    ),
                ),
            )

        result.drawNumbers shouldBe DrawNumberObservation.NotObserved
    }

    @Test
    fun `역산 파생 기초금액은 승격이 짓지 않는다`() {
        val result =
            promotedResult(groupOf(observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "900"))))

        result.derivedBaseAmount.shouldBeNull()
    }

    // =========================================================================
    // 확정 낙찰 축 — 오늘은 원천이 없고, 원천이 생기면 코드 변경 없이 따라온다(D-7C5P-1)
    // =========================================================================
    @Test
    fun `낙찰 목록 원문이 없으면 확정 축이 비어 있다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        mapOf("progrsDivCdNm" to "개찰완료", "sucsfbidAmt" to "9999", "bidwinnrNm" to "엉뚱업체"),
                    ),
                ),
            )

        // 같은 키가 **다른 축**의 원문에 섞여 와도 읽지 않는다 — 계약의 `presentIn` 이 축을 가른다.
        result.finalAwardAmount.shouldBeNull()
        result.finalAwardCompanyName.shouldBeNull()
        result.winningRate.shouldBeNull()
    }

    @Test
    fun `낙찰 목록 축의 원문이 들어오면 확정 축이 코드 변경 없이 채워진다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_AWARD_LIST,
                        WALK_A,
                        mapOf(
                            "sucsfbidAmt" to "1,000,000",
                            "bidwinnrNm" to "낙찰업체",
                            "sucsfbidRate" to "87.995",
                        ),
                    ),
                ),
            )

        result.finalAwardAmount?.export()?.won shouldBe 1_000_000L
        result.finalAwardCompanyName shouldBe "낙찰업체"
        result.winningRate shouldBe Rate.ofPercent(BigDecimal("87.995"))
    }

    // =========================================================================
    // 걷기 선택 — 원장이 정한다(D-6G-68)
    // =========================================================================
    @Test
    fun `원장 결말이 가리킨 걷기의 행만 쓴다`() {
        val group =
            groupOf(
                observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B, mapOf("plnprc" to "200")),
            )

        val older = promotedResult(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeeded(WALK_A)))
        val newer = promotedResult(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeeded(WALK_B)))

        older.plannedPrice?.export()?.won shouldBe 100L
        newer.plannedPrice?.export()?.won shouldBe 200L
    }

    @Test
    fun `두 걷기의 자식 행은 합쳐지지 않는다`() {
        val group =
            groupOf(
                observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("compnoRsrvtnPrceSno" to "1")),
                observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("compnoRsrvtnPrceSno" to "2")),
                observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B, mapOf("compnoRsrvtnPrceSno" to "9")),
            )

        val result = promotedResult(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeeded(WALK_B)))

        result.reservePrices.map { it.sequenceNumber } shouldContainExactly listOf("9")
    }

    @Test
    fun `원장 줄이 없는 축은 가장 늦은 관측으로 물러선다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "9")),
                ),
            )

        result.participantCount shouldBe 9
    }

    @Test
    fun `빈 응답으로 끝난 축은 0 행이다`() {
        val group = groupOf(observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))

        val outcome =
            promote(
                group,
                mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to AxisConclusion(AttemptOutcome.Empty, WALK_A)),
            )

        outcome shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `실패로 끝난 축의 행은 쓰지 않는다`() {
        val group = groupOf(observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))

        val outcome =
            promote(
                group,
                mapOf(
                    SourceEndpoint.RESERVE_PRICE_DETAIL to
                        AxisConclusion(AttemptOutcome.Failed("SHORT_WALK"), WALK_A),
                ),
            )

        outcome shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `미완 축이 있어도 다른 축은 승격된다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "4")),
                ),
                mapOf(
                    SourceEndpoint.RESERVE_PRICE_DETAIL to
                        AxisConclusion(AttemptOutcome.Failed("SHORT_WALK"), WALK_A),
                ),
            )

        result.plannedPrice.shouldBeNull()
        result.participantCount shouldBe 4
    }

    @Test
    fun `걷기 선택은 네 갈래다`() {
        axisWalkOf(null, listOf(WALK_A, WALK_B)) shouldBe AxisWalk.Use(WALK_B)
        axisWalkOf(null, emptyList()) shouldBe AxisWalk.NoRows
        axisWalkOf(AxisConclusion(AttemptOutcome.Empty, WALK_B), listOf(WALK_A)) shouldBe AxisWalk.NoRows
        axisWalkOf(AxisConclusion(AttemptOutcome.Failed("X"), WALK_B), listOf(WALK_B)) shouldBe AxisWalk.Unusable
        axisWalkOf(succeeded(WALK_A), listOf(WALK_A, WALK_B)) shouldBe AxisWalk.Use(WALK_A)
    }

    // =========================================================================
    // 개찰 1위 축 — 기존 순수 커널에 맡긴다
    // =========================================================================
    @Test
    fun `순위 1 이 하나면 결정이다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf(
                            "prcbdrNm" to "일위업체",
                            "opengRank" to "1",
                            "bidprcAmt" to "900,000",
                            "bidprcrt" to "90.0",
                        ),
                    ),
                    observation(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to "이위업체", "opengRank" to "2"),
                    ),
                ),
            )

        val determined = result.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
        determined.bid.bidderName shouldBe "일위업체"
        determined.bid.bidAmount shouldBe ObservedBidAmount(900_000L, Currency.KRW)
        determined.bid.bidRate shouldBe Rate.ofPercent(BigDecimal("90.0"))
        determined.observedAt shouldBe WALK_A
    }

    @Test
    fun `순위 1 이 둘이면 동값을 깨지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "가", "opengRank" to "1")),
                    observation(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "나", "opengRank" to "1")),
                ),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.RankDuplicated(2, WALK_A)
    }

    @Test
    fun `순위 1 이 없으면 순위 부재다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "가", "opengRank" to "2")),
                ),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.RankMissing(WALK_A)
    }

    @Test
    fun `개찰완료 축이 없으면 1위 축은 미관측이다`() {
        val result = promotedResult(groupOf(observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, emptyMap())))

        result.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
    }

    @Test
    fun `상호를 얻지 못한 투찰 행이 있으면 1위 축을 비운다`() {
        // 그 행을 버리면 「순위 1이 유일한가」의 모수가 조용히 줄고, 승인된 타입에는 「순위 1을
        // 특정했으나 상호가 없다」를 표현할 자리가 없다(알려진 제한).
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "가", "opengRank" to "1")),
                    observation(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("opengRank" to "2")),
                ),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
    }

    @Test
    fun `투찰금액의 소수부는 1위 축에서도 깎이지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to "가", "opengRank" to "1", "bidprcAmt" to "900000.5"),
                    ),
                ),
            )

        result.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>().bid.bidAmount.shouldBeNull()
    }

    // =========================================================================
    // 자식 행의 정체성 — 순번
    // =========================================================================
    @Test
    fun `순번이 공백인 자식 행은 승격하지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "  ", "bsisPlnprc" to "1000"),
                    ),
                ),
            )

        result.reservePrices.shouldContainExactly(emptyList())
    }

    @Test
    fun `같은 순번이 두 번 오면 첫 행만 쓴다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "1", "bsisPlnprc" to "1000"),
                    ),
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "1", "bsisPlnprc" to "2000"),
                    ),
                ),
            )

        result.reservePrices.single().baseReservePrice shouldBe ReservePriceCandidateAmount(1_000L, Currency.KRW)
    }

    @Test
    fun `추첨 표시는 Y·N 만 참·거짓이다`() {
        fun drawnFor(flag: String): Boolean? =
            promotedResult(
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "1", "drwtYn" to flag),
                    ),
                ),
            ).reservePrices.single().isDrawn

        drawnFor("Y") shouldBe true
        drawnFor("N") shouldBe false
        drawnFor("예").shouldBeNull()
    }

    // =========================================================================
    // 관측 시각과 저장 키
    // =========================================================================
    @Test
    fun `부모 관측 시각은 기여한 걷기 가운데 가장 늦은 시각이다`() {
        val result =
            promotedResult(
                groupOf(
                    observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "4")),
                ),
            )

        result.observedAt shouldBe WALK_B
    }

    @Test
    fun `저장 키는 부모 관측 시각을 낸 걷기에서 고른다`() {
        // 가드가 시각과 키를 함께 보므로 둘은 같은 행에서 온다 — 더 작은 키라도 더 이른 걷기의
        // 것이면 고르지 않는다.
        val outcome =
            promote(
                groupOf(
                    observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100"), key = "k-1"),
                    observation(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "4"), key = "k-9"),
                    observation(SourceEndpoint.OPENING_COMPLETE, WALK_B, mapOf("prcbdrNm" to "가"), key = "k-5"),
                ),
            )

        val promotable = outcome.shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        promotable.result.observedAt shouldBe WALK_B
        promotable.observationKey shouldBe ObservationKey("k-5")
    }

    @Test
    fun `승격은 저장된 관측보다 늦을 때만 쓴다`() {
        val older = OpeningResult(NOTICE_ID, null, null, WALK_A)
        val newer = OpeningResult(NOTICE_ID, null, null, WALK_B)

        promotionIsNew(null, older) shouldBe true
        promotionIsNew(older, newer) shouldBe true
        // 같은 입력 두 번 — 관측 시각이 같아 쓰지 않는다(행 불변).
        promotionIsNew(older, older) shouldBe false
        promotionIsNew(newer, older) shouldBe false
    }

    @Test
    fun `쓸 걷기가 있는 축이 하나도 없으면 행을 만들지 않는다`() {
        promote(groupOf()) shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    // =========================================================================
    // 축 선언의 건전성 · 던지지 않는다
    // =========================================================================
    @Test
    fun `선언한 축은 그 개념의 계약 presentIn 안이다`() {
        OPENING_PROMOTION_SLOT_AXES.forEach { (concept, axes) ->
            val declared = POLICY.fieldContracts.contractsFor(concept).flatMap { it.presentIn }.toSet()
            val unreachable = axes - declared
            unreachable shouldBe emptySet()
        }
    }

    @Test
    fun `원문 값이 무엇이든 던지지 않는다`() {
        val garbage =
            listOf("", " ", "-", "+", "NaN", "1e999999", "99999999999999999999999999", "0x10", "1,,2", "\u0000")
        garbage.forEach { value ->
            val group =
                groupOf(
                    observation(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf(
                            "plnprc" to value,
                            "bssamt" to value,
                            "bsisPlnprc" to value,
                            "compnoRsrvtnPrceSno" to value,
                            "drwtYn" to value,
                            "rlOpengDt" to value,
                            "prtcptCnum" to value,
                        ),
                    ),
                    observation(
                        SourceEndpoint.OPENING_AWARD_LIST,
                        WALK_A,
                        mapOf("sucsfbidAmt" to value, "sucsfbidRate" to value, "bidwinnrNm" to value),
                    ),
                    observation(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to value, "opengRank" to value, "bidprcAmt" to value, "bidprcrt" to value),
                    ),
                )

            promote(group).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        }
    }

    @Test
    fun `과세 어휘를 리터럴로 짓지 않는다`() {
        // 계약의 과세 값이 무엇이든 그 값이 그대로 실린다 — UNKNOWN 리터럴 회귀를 잡는다.
        val contract = requireNotNull(POLICY.fieldContracts.contractFor(RawKey("bssamt")))
        val result =
            promotedResult(groupOf(observation(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("bssamt" to "1"))))

        result.baseAmount?.vatTreatment shouldBe contract.vatTreatment
        contract.vatTreatment shouldBe VatTreatment.UNKNOWN
    }
}
