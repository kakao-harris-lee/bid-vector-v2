package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Resolution
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionOutcome
import bidvector.workflow.retention.RetentionPolicyData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 담당자 키 — 이 키들은 필드 계약에 **등재되지 않는다**(D-3H-7). 자리값은 형태를 흉내내지 않는다. */
private val OFFICER_KEYS =
    mapOf(
        "ntceInsttOfclNm" to "OFFICER-NAME-FIXTURE",
        "ntceInsttOfclTelNo" to "OFFICER-TEL-FIXTURE",
        "ntceInsttOfclEmailAdrs" to "OFFICER-MAIL-FIXTURE",
        "dminsttOfclNm" to "DEMAND-OFFICER-FIXTURE",
    )

/**
 * D-6B3-1 ④ — 「마스킹이 PII 아닌 필드를 지워 표본을 깨지 않는다」를 **production 쓰기 경로
 * 위에서** 잰다. 손으로 심은 fixture 가 아니라 `JdbcRawObservationStore` 가 실제 필드 계약
 * 레지스트리로 쓴 행을 쓴다 — 남김 집합의 출처가 그 행의 `payload_fields` 이므로, 등식의
 * 양쪽이 모두 실제 계약에서 나온다.
 *
 * 포함의 **다른 절반**은 트리거가 진다: 마스킹 UPDATE 는 `payload_fields` 를 바꿀 수 없다
 * (마스킹 열을 뺀 행 전체 비교 — `RawObservationMaskingGuardTest`). 그래서 그 투영을 읽는
 * 소비자(스냅숏 추출·수집 축 조회)는 마스킹에 **영향을 받을 수 없다**.
 */
class MaskingKeepsProjectedKeysTest : PersistenceTestSupport() {
    @Test
    fun `마스킹 뒤 남은 키 집합은 그 행의 투영 키 집합과 같다 — 읽는 키를 지우지 않는다`() {
        val key = appendThroughProductionPath()
        val projected = projectedKeysOf(key.value)

        sweepFarInTheFuture().shouldBeInstanceOf<RetentionOutcome.Completed>().report.maskedTotal shouldBe 1

        keysOf(payloadOf(key.value)) shouldBe projected
    }

    @Test
    fun `같은 행에서 담당자 키는 사라진다 — 계약에 등재되지 않아 투영에 없다`() {
        val key = appendThroughProductionPath()

        sweepFarInTheFuture()

        val masked = payloadOf(key.value)
        OFFICER_KEYS.keys.forEach { masked shouldNotContain it }
        OFFICER_KEYS.values.forEach { masked shouldNotContain it }
    }

    @Test
    fun `투영은 마스킹 뒤에도 바뀌지 않는다 — 트리거가 그 열을 불변으로 둔다`() {
        val key = appendThroughProductionPath()
        val before = projectedKeysOf(key.value)

        sweepFarInTheFuture()

        projectedKeysOf(key.value) shouldBe before
    }

    /**
     * 적재 시각은 `DEFAULT now()` 가 찍는다(production 경로의 계약) — 그 값을 고칠 수 없으므로
     * (append-only) **시계를 미래로** 둬서 기한을 넘긴다. 90일은 `RETENTION_ANCHOR`(test 앵커)다.
     */
    private fun sweepFarInTheFuture(): RetentionOutcome {
        val future = Instant.now() + RETENTION_ANCHOR + java.time.Duration.ofSeconds(1)
        return PurgeExpiredData(JdbcRetentionStore(dataSource()), { future }, productionPolicy()).run()
    }

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

    /**
     * 계약 등재 키 둘 + 담당자 키 넷을 담은 원문으로 **production 경로**를 지난다. `payload` 는
     * `sourceText` 그대로 들어가고 `payload_fields` 는 계약 투영이 된다.
     */
    private fun appendThroughProductionPath(): bidvector.procurement.ObservationKey {
        val identity = mapOf(RawKey("bidNtceNo") to "20260101001", RawKey("bidNtceOrd") to "000")
        val sourceText =
            (
                identity.entries.map { (raw, value) -> """"${raw.name}":"$value"""" } +
                    OFFICER_KEYS.entries.map { (name, value) -> """"$name":"$value"""" }
            ).joinToString(",", "{", "}")
        val observation =
            RawNoticeObservation.of(
                fields = identity,
                sourceEndpoint = SourceEndpoint.NOTICE_LIST,
                observedAt = Instant.parse("2026-01-01T00:00:00Z"),
                sourceText = sourceText,
            )
        return appendRawObservation(observation)
    }

    private fun payloadOf(observationKey: String): String = columnOf("payload", observationKey)

    private fun projectedKeysOf(observationKey: String): Set<String> =
        keysOf(columnOf("payload_fields::text", observationKey))

    private fun columnOf(
        expression: String,
        observationKey: String,
    ): String =
        dataSource().connection.use { connection ->
            val sql = "SELECT $expression AS value FROM raw_observation WHERE observation_key = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, observationKey)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "행이 없다: $observationKey" }
                    rs.getString("value")
                }
            }
        }

    /** 평평한 JSON 객체의 키 집합 — 판독은 test 가 직접 한다(마스킹 함수를 거치지 않는다). */
    private fun keysOf(json: String): Set<String> =
        Regex("\"([^\"]+)\"\\s*:").findAll(json).map { it.groupValues[1] }.toSet()
}
