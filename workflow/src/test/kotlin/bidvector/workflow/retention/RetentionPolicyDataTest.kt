package bidvector.workflow.retention

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * P-5 — 보존 정책 값. 이 test 는 **값이 승인된 값인가**만 본다(90일·일괄 하한). 「그 값이
 * 실제 DELETE 술어에 닿는가」는 DB 위에서 잰다
 * (`bidvector.adapters.retention.RetentionBoundaryTest` — 거기서는 경계 ±1초를 **test 자신의
 * 90일 앵커**로 세우므로 정책 값을 89·91 로 바꾸면 RED 가 된다).
 */
class RetentionPolicyDataTest {
    @Test
    fun `승인된 값은 90일이다`() {
        val resolution = RETENTION_POLICY.resolve(LocalDate.of(2026, 10, 13))

        (resolution as Resolution.Resolved).value.retentionDays shouldBe 90
    }

    @Test
    fun `보존 기간 0 은 받지 않는다 — cutoff 가 현재가 되어 전부 파기가 된다`() {
        shouldThrow<IllegalArgumentException> { RetentionPolicyData(retentionDays = 0, batchSize = 10) }
    }

    @Test
    fun `보존 기간 음수는 받지 않는다 — cutoff 가 미래가 되어 기한 안쪽 행도 지운다`() {
        shouldThrow<IllegalArgumentException> { RetentionPolicyData(retentionDays = -1, batchSize = 10) }
    }

    @Test
    fun `일괄 크기 0 은 받지 않는다 — 한 번도 줄지 않는 루프다`() {
        shouldThrow<IllegalArgumentException> { RetentionPolicyData(retentionDays = 90, batchSize = 0) }
    }

    @Test
    fun `cutoff 는 주어진 시각에서 보존 기간을 뺀 값이고 언제나 그 시각보다 과거다`() {
        val now = Instant.parse("2026-10-13T00:00:00Z")

        val cutoff = RetentionPolicyData(retentionDays = 90, batchSize = 10).cutoffFrom(now)

        cutoff.instant shouldBe Instant.parse("2026-07-15T00:00:00Z")
    }
}
