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

    /**
     * 운영자 결정 2026-10-14(D-6B3-3 ①) — 업체명은 마스킹 대상이다. 담당자 키와 달리 업체명은
     * 필드 계약에 **등재되어** 있어 남김 집합(= 그 행의 투영 키)에 저절로 들어온다. 개념 축으로
     * 빼지 않으면 `payload` 에 그대로 남는다.
     */
    @Test
    fun `업체명은 원문에서 사라진다 — 계약에 등재되어 있어도 남김 집합 밖이다`() {
        sweepAt(NOW)

        val payload = fixture.payloadOf("ret-old").first
        COMPANY_NAME_FIXTURE_KEYS.forEach { payload shouldNotContain it }
        payload shouldNotContain COMPANY_NAME_FIXTURE_VALUE
    }

    /**
     * 같은 결정의 **나머지 절반** — 업체명은 투영 열에도 실린다. 그 열을 마스킹 열로 두지 않으면
     * 트리거 관문이 그 열을 불변으로 만들어 **구조적으로 지울 수 없는** 값이 된다(privacy F-1).
     */
    @Test
    fun `업체명은 투영 열에서도 사라진다 — 그 열도 마스킹 열이다`() {
        sweepAt(NOW)

        val projection = fixture.payloadFieldsOf("ret-old")
        COMPANY_NAME_FIXTURE_KEYS.forEach { projection shouldNotContain it }
        projection shouldNotContain COMPANY_NAME_FIXTURE_VALUE
    }

    @Test
    fun `투영의 나머지 키는 그대로 남는다 — 업체명만 뺀다`() {
        sweepAt(NOW)

        keysOf(fixture.payloadFieldsOf("ret-old")) shouldBe setOf("bidNtceNo", "bidNtceOrd")
    }

    @Test
    fun `기한 안쪽 행의 업체명은 두 열 모두에 남는다 — 음성 대조`() {
        sweepAt(NOW)

        fixture.payloadOf("ret-new").first.contains(COMPANY_NAME_FIXTURE_VALUE) shouldBe true
        fixture.payloadFieldsOf("ret-new").contains(COMPANY_NAME_FIXTURE_VALUE) shouldBe true
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
        val holder = retentionStore()

        val secondOutcome =
            holder.withExclusiveLease {
                PurgeExpiredData(retentionStore(), { NOW }, productionPolicy()).run()
            }

        secondOutcome
            .shouldBeInstanceOf<LeaseAttempt.Held<RetentionOutcome>>()
            .result shouldBe RetentionOutcome.LeaseBusy
        // 둘째가 아무것도 하지 않았으므로 기한 넘은 원문은 아직 마스킹되지 않았다.
        fixture.payloadOf("ret-old").second shouldBe false
    }

    @Test
    fun `임대가 풀린 뒤에는 다시 쥘 수 있다 — 잠금이 세션 종료로 새지 않는다`() {
        retentionStore().withExclusiveLease { }

        sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report.maskedTotal shouldBe 1
    }

    private fun sweepAt(at: Instant): RetentionOutcome =
        PurgeExpiredData(retentionStore(), { at }, productionPolicy()).run()

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

    /** 평평한 JSON 객체의 키 집합 — jsonb 는 공백·순서를 자기 방식으로 내므로 문면 비교를 피한다. */
    private fun keysOf(json: String): Set<String> =
        Regex("\"([^\"]+)\"\\s*:").findAll(json).map { it.groupValues[1] }.toSet()

    /** 보존 저장소 — 마스킹이 지울 키를 **계약**에 물으므로 레지스트리를 함께 받는다(D-6B3-3 ①). */
    private fun retentionStore() = JdbcRetentionStore(dataSource(), testFieldContracts())
}
