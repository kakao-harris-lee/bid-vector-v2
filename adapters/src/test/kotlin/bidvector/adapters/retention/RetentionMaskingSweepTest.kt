package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.sharedkernel.Resolution
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionOutcome
import bidvector.workflow.retention.RetentionPolicyData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * P-4 — 마스킹의 실 SQL 축. 「마스킹은 한 방향이고 되돌릴 수 없다」의 **거동** 쪽이다
 * (트리거가 되돌림을 거부한다는 쪽은 `RawObservationMaskingGuardTest`).
 */
class RetentionMaskingSweepTest : PersistenceTestSupport() {
    private lateinit var fixture: RetentionFixture

    @BeforeEach
    fun seedBothSidesOfTheBoundary() {
        fixture = RetentionFixture(dataSource())
        fixture.seedAll("old", JUST_OLDER)
        fixture.seedAll("new", JUST_NEWER)
    }

    @Test
    fun `기한 넘은 원문에서 담당자 키가 사라지고 표지가 찍힌다`() {
        val report = sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report

        val (payload, masked) = fixture.payloadOf("ret-old")
        payload shouldNotContain "Ofcl"
        payload shouldNotContain "FIXTURE"
        masked shouldBe true
        report.maskedTotal shouldBe 1
    }

    @Test
    fun `남길 키는 마스킹 뒤에도 그대로다 — 6G 백테스트 표본이 남는다`() {
        sweepAt(NOW)

        fixture.payloadOf("ret-old").first shouldBe """{"bidNtceNo":"RET-old","bidNtceOrd":"000"}"""
    }

    @Test
    fun `기한 안쪽 원문은 손대지 않는다 — 표지도 찍히지 않는다`() {
        sweepAt(NOW)

        val (payload, masked) = fixture.payloadOf("ret-new")
        payload.contains("Ofcl") shouldBe true
        masked shouldBe false
    }

    @Test
    fun `두 번째 run 은 이미 마스킹된 행을 건너뛴다 — 계수가 0 이다`() {
        sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report.maskedTotal shouldBe 1

        val second = sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report

        second.maskedTotal shouldBe 0
        second.maskFailClosedTotal shouldBe 0
    }

    @Test
    fun `판독할 수 없는 원문은 값 전체를 버리고 따로 센다 — 원문을 그대로 두지 않는다`() {
        fixture.seedUnparsablePayload("1", JUST_OLDER)

        val report = sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report

        report.maskFailClosedTotal shouldBe 1
        report.maskedTotal shouldBe 1
        fixture.payloadOf("ret-bad-1").first shouldBe PAYLOAD_MASK_FAIL_CLOSED
    }

    @Test
    fun `임대를 쥔 채 둘째 run 을 돌리면 아무것도 하지 않는다`() {
        val holder = JdbcRetentionStore(dataSource())

        val secondOutcome =
            holder.withExclusiveLease {
                PurgeExpiredData(JdbcRetentionStore(dataSource()), { NOW }, productionPolicy()).run()
            }

        secondOutcome
            .shouldBeInstanceOf<LeaseAttempt.Held<RetentionOutcome>>()
            .result shouldBe RetentionOutcome.LeaseBusy
        // 둘째가 아무것도 하지 않았으므로 기한 넘은 원문은 아직 마스킹되지 않았다.
        fixture.payloadOf("ret-old").second shouldBe false
    }

    @Test
    fun `임대가 풀린 뒤에는 다시 쥘 수 있다 — 잠금이 세션 종료로 새지 않는다`() {
        JdbcRetentionStore(dataSource()).withExclusiveLease { }

        sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report.maskedTotal shouldBe 1
    }

    private fun sweepAt(at: Instant): RetentionOutcome =
        PurgeExpiredData(JdbcRetentionStore(dataSource()), { at }, productionPolicy()).run()

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value
}
