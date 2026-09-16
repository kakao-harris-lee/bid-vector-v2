package bidvector.procurement

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-3H-3, M3/3H-1 — 발주기관 역할별 조립(② `canonicalize`). `CanonicalizeTest.kt`에서
 * 분리한 파일이다(sizeGate 500줄, v2-지침서 §5 — `KonepsOpeningCompleteFieldContracts.kt`
 * 분리와 같은 전례). `TEST_REGISTRY`·`TEST_POLICY`·`observationOf`는 `CanonicalizeTest.kt`
 * 소유(`internal`)를 그대로 재사용한다 — 두 번째 정본을 만들지 않는다.
 */
class AgencyCanonicalizeTest {
    @Test
    fun `수요기관·공고기관 코드_이름이 모두 있으면 두 fact 를 각자 채운다 — D-3H-3`() {
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    "dminsttCd" to "1234567",
                    "dminsttNm" to "수요기관",
                    "ntceInsttCd" to "7654321",
                    "ntceInsttNm" to "공고기관",
                ),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.demandAgency shouldBe Agency(AgencyCode.of("1234567"), AgencyName.of("수요기관"))
        outcome.command.noticeAgency shouldBe Agency(AgencyCode.of("7654321"), AgencyName.of("공고기관"))
    }

    @Test
    fun `수요기관 키가 결측이고 공고기관 키만 있으면 demandAgency 는 null 이다 — 우회 (3), 폴백 없음`() {
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    "ntceInsttCd" to "7654321",
                    "ntceInsttNm" to "공고기관",
                ),
            )

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.demandAgency shouldBe null
        outcome.command.noticeAgency shouldBe Agency(AgencyCode.of("7654321"), AgencyName.of("공고기관"))
    }

    @Test
    fun `발주기관 키가 아예 결측이면 두 fact 모두 null 이다 — 지어내지 않는다`() {
        val observation = observationOf(mapOf("bidNtceNo" to "20260101001", "bidNtceOrd" to "000"))

        val outcome = canonicalize(observation, TEST_POLICY) as CanonicalizationOutcome.Normalized

        outcome.command.demandAgency shouldBe null
        outcome.command.noticeAgency shouldBe null
    }

    @Test
    fun `등재 없는 레지스트리로는 발주기관 키가 원문에 있어도 fact 가 null 이다 — 우회 (1)`() {
        val registryWithoutAgency =
            KonepsFieldContractRegistry.of(TEST_REGISTRY.contracts.filterNot { it.rawName == RawKey("dminsttCd") })
        val policyWithoutDemandCode = TEST_POLICY.copy(fieldContracts = registryWithoutAgency)
        val observation =
            observationOf(
                mapOf(
                    "bidNtceNo" to "20260101001",
                    "bidNtceOrd" to "000",
                    "dminsttCd" to "1234567",
                    "dminsttNm" to "수요기관",
                ),
            )

        val outcome = canonicalize(observation, policyWithoutDemandCode) as CanonicalizationOutcome.Normalized

        outcome.command.demandAgency shouldBe Agency(code = null, name = AgencyName.of("수요기관"))
    }
}
