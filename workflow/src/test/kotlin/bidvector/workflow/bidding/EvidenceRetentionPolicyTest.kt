package bidvector.workflow.bidding

import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Period

/**
 * **출하되는 정책 값 자체**를 잰다 — R-5(운영자 결정 2026-10-21, 제출확인서 참조 보관 **3년**).
 *
 * 이게 왜 따로 필요한가: `BidCaseWorkflowTest` 는 자기 정책을 **주입해서** 날짜 산술을 재므로,
 * 출하 상수를 1년으로 바꿔도 그 test 는 전부 초록이다(변이 ⑥ 실측). 운영자가 정한 값이 코드에
 * 실제로 들어 있는지는 그 상수를 직접 읽는 자리가 있어야 잡힌다.
 */
class EvidenceRetentionPolicyTest {
    @Test
    fun `출하 정책은 3년이다 — R-5 운영자 결정`() {
        val resolved = EVIDENCE_RETENTION_POLICY.resolve(LocalDate.of(2026, 10, 21))
        resolved.shouldBeInstanceOf<Resolution.Resolved<EvidenceRetentionPolicyData>>()
        resolved.value.retainFor shouldBe Period.ofYears(3)
    }

    @Test
    fun `정본은 코드가 아니라 문서다 — source 가 그 문서를 가리킨다`() {
        EVIDENCE_RETENTION_POLICY.source shouldContain "milestone-7.md"
        EVIDENCE_RETENTION_POLICY.source shouldContain "R-5"
    }

    @Test
    fun `보관 기간은 양수여야 한다 — 0이나 음수 정책은 만들 수 없다`() {
        runCatching { EvidenceRetentionPolicyData(Period.ZERO) }.isFailure shouldBe true
        runCatching { EvidenceRetentionPolicyData(Period.ofYears(-1)) }.isFailure shouldBe true
    }

    @Test
    fun `보관 기한을 세는 구역은 KST 다 — 운영자의 하루와 날짜 경계가 같다`() {
        EVIDENCE_RETENTION_ZONE.id shouldBe "Asia/Seoul"
    }
}
