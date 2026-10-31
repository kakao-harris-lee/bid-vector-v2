package bidvector.decision

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val TEST_BAND = PlausibilityBand(BigDecimal("0.30"), BigDecimal("0.995"))

private val TEST_POLICY =
    Resolution.Resolved(
        PublishedFloorTrustPolicyData(TEST_BAND),
        PolicyVersion(EffectiveFrom.Initial, "test-published-floor-trust-policy"),
    )

private fun noticeValue(fraction: String): FloorRate =
    FloorRate(Rate.ofFraction(BigDecimal(fraction)), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

private fun statutory(fraction: String): FloorRate =
    FloorRate(Rate.ofFraction(BigDecimal(fraction)), FloorRateOrigin.StatutoryTable(EffectiveFrom.Initial))

/**
 * DEC-10 게이트 — 게시 하한율의 개연 밴드 판정. **고치지 않고** 「하한 미보고」와 같은 자리로
 * 떨어뜨리되 사유를 남긴다(capability map DEC-10 분류 근거 · R-FLOOR-03).
 */
class PublishedFloorTrustTest {
    @Test
    fun `밴드 안 게시값은 통과한다`() {
        val trust = trustPublishedFloorRate(noticeValue("0.88"), TEST_POLICY)

        trust.shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
        trust.rate.rate shouldBe Rate.ofFraction(BigDecimal("0.88"))
    }

    @Test
    fun `밴드 경계는 양 끝 포함이다 (운영자 결정 2026-10-24)`() {
        trustPublishedFloorRate(noticeValue("0.30"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
        trustPublishedFloorRate(noticeValue("0.995"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
    }

    @Test
    fun `밴드 경계의 척도 변형도 같은 판정이다 — 0,3000 은 0,30 과 같은 수다`() {
        trustPublishedFloorRate(noticeValue("0.3000"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
        trustPublishedFloorRate(noticeValue("0.99500"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
    }

    @Test
    fun `밴드 바로 아래는 사유와 함께 개연 불가다`() {
        val trust = trustPublishedFloorRate(noticeValue("0.2999"), TEST_POLICY)

        trust.shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
        trust.rate shouldBe noticeValue("0.2999")
        trust.band shouldBe TEST_BAND
    }

    @Test
    fun `밴드 바로 위도 개연 불가다`() {
        trustPublishedFloorRate(noticeValue("0.9951"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
    }

    /**
     * R-FLOOR-03 — 하한율 `1.00000` 이 라이브 47건 실재하고 「예정가 전액 이상 투찰」이라
     * 하한이 성립하지 않는다. 오늘 평가 margin 경로는 `fraction > 1` 만 걸러 이 값을 통과
     * 시키고 `floorHeadroom` 의 `floor == 1` 가지로 **조용히** 접었다.
     */
    @Test
    fun `성립 불가한 1,00000 은 개연 불가로 떨어지고 값은 고쳐지지 않는다`() {
        val trust = trustPublishedFloorRate(noticeValue("1.00000"), TEST_POLICY)

        trust.shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
        trust.rate.rate shouldBe Rate.ofFraction(BigDecimal("1"))
    }

    @Test
    fun `밴드 안의 낮은 값(실재하는 것)은 통과한다 — 0,5 미만 3건 근거`() {
        trustPublishedFloorRate(noticeValue("0.31"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
    }

    @Test
    fun `게시값이 없으면 부재다 — 실패가 아니라 출처 부재`() {
        trustPublishedFloorRate(null, TEST_POLICY) shouldBe PublishedFloorTrust.Absent
    }

    /**
     * 설계 검토 우회 7 — 밴드 밖 값에 법정표 origin 을 달아 게이트를 비켜 가는 길. 이 게이트는
     * `NoticeValue` 에만 통과를 내준다(data dictionary §1.4.3 B6 — 게이트가 그 variant 에만
     * 걸린다). 법정 하한율 해석은 DEC-03 몫이고 오늘 그 값의 생성 자리가 0 이다.
     */
    @Test
    fun `법정표 origin 은 통과도 개연 불가도 아닌 별 가지다 — 밴드 안 값이어도 통과가 아니다`() {
        val inBand = trustPublishedFloorRate(statutory("0.88"), TEST_POLICY)
        inBand.shouldBeInstanceOf<PublishedFloorTrust.OriginNotJudged>()
        inBand.rate shouldBe statutory("0.88")

        trustPublishedFloorRate(statutory("1.00000"), TEST_POLICY)
            .shouldBeInstanceOf<PublishedFloorTrust.OriginNotJudged>()
    }

    @Test
    fun `출하 정책의 밴드는 0,30 에서 0,995 다 (D-DEC10-1)`() {
        val resolved = PUBLISHED_FLOOR_TRUST_POLICY.resolve(java.time.LocalDate.of(2026, 10, 28))

        resolved.shouldBeInstanceOf<Resolution.Resolved<PublishedFloorTrustPolicyData>>()
        val band = resolved.value.plausibilityBand
        band.min.compareTo(BigDecimal("0.30")) shouldBe 0
        band.max.compareTo(BigDecimal("0.995")) shouldBe 0
    }

    @Test
    fun `출하 정책에서도 1,00000 은 개연 불가다 — test 밴드 위에서만 도는 판정이 아니다`() {
        val resolved = PUBLISHED_FLOOR_TRUST_POLICY.resolve(java.time.LocalDate.of(2026, 10, 28))
        resolved.shouldBeInstanceOf<Resolution.Resolved<PublishedFloorTrustPolicyData>>()

        trustPublishedFloorRate(noticeValue("1.00000"), resolved)
            .shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
        trustPublishedFloorRate(noticeValue("0.88"), resolved)
            .shouldBeInstanceOf<PublishedFloorTrust.Trusted>()
    }

    /**
     * 계수 어휘 — 가격 경로가 제외한 사유가 **값으로** 남는다(침묵 스킵 금지, acceptance 셋째).
     * 부재는 제외가 아니므로 사유가 없다.
     */
    @Test
    fun `제외 사유는 결과에서 값으로 읽힌다 — 부재는 제외가 아니다`() {
        trustPublishedFloorRate(noticeValue("0.88"), TEST_POLICY).exclusion() shouldBe null
        trustPublishedFloorRate(null, TEST_POLICY).exclusion() shouldBe null
        trustPublishedFloorRate(noticeValue("1.00000"), TEST_POLICY).exclusion() shouldBe
            PublishedFloorExclusion.IMPLAUSIBLE
        trustPublishedFloorRate(statutory("0.88"), TEST_POLICY).exclusion() shouldBe
            PublishedFloorExclusion.ORIGIN_NOT_JUDGED
    }
}
