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

/**
 * D-6B3-1 ④ — 「마스킹이 PII 아닌 필드를 지워 표본을 깨지 않는다」를 **production 쓰기 경로
 * 위에서** 잰다. 손으로 심은 fixture 가 아니라 `JdbcRawObservationStore` 가 실제 필드 계약
 * 레지스트리로 쓴 행을 쓴다 — 등식의 양쪽이 모두 실제 계약에서 나온다.
 *
 * 등식의 기준이 **「업체명을 뺀 남김 집합」**으로 다시 세워졌다(운영자 결정 2026-10-14,
 * D-6B3-3 ①): 업체명 둘은 계약에 등재되어 투영에 실리지만 남김 집합 밖이고, 그래서 마스킹은
 * `payload` 와 `payload_fields` **둘 다** 에서 그 키를 지운다. 소비자 포함 술어는 그 기준으로
 * 잰다 — 6G 백테스트·스냅숏 추출·재정규화의 읽기 키 집합에 업체명이 없음을 기계 수집으로
 * 확인했다(`RawPayloadProductionReaderTest` 와 개념 축 수집, evidence).
 */
class MaskingKeepsProjectedKeysTest : PersistenceTestSupport() {
    @Test
    fun `마스킹 뒤 남은 키 집합은 투영 키에서 업체명을 뺀 집합과 같다 — 읽는 키를 지우지 않는다`() {
        val key = appendThroughProductionPath()
        val projected = projectedKeysOf(key.value)

        sweepFarInTheFuture().shouldBeInstanceOf<RetentionOutcome.Completed>().report.maskedTotal shouldBe 1

        keysOf(payloadOf(key.value)) shouldBe projected - COMPANY_NAME_FIXTURE_KEYS.toSet()
    }

    @Test
    fun `업체명은 투영에 실렸다 — 그 전제가 없으면 위 등식이 공허하다`() {
        val key = appendThroughProductionPath()

        projectedKeysOf(key.value) shouldBe
            (setOf("bidNtceNo", "bidNtceOrd") + COMPANY_NAME_FIXTURE_KEYS)
    }

    @Test
    fun `같은 행에서 담당자 키는 사라진다 — 계약에 등재되지 않아 투영에 없다`() {
        val key = appendThroughProductionPath()

        sweepFarInTheFuture()

        val masked = payloadOf(key.value)
        OFFICER_FIXTURE_KEYS.keys.forEach { masked shouldNotContain it }
        OFFICER_FIXTURE_KEYS.values.forEach { masked shouldNotContain it }
    }

    @Test
    fun `투영은 업체명만 잃는다 — 다른 키는 production 경로 위에서도 그대로다`() {
        val key = appendThroughProductionPath()
        val before = projectedKeysOf(key.value)

        sweepFarInTheFuture()

        projectedKeysOf(key.value) shouldBe before - COMPANY_NAME_FIXTURE_KEYS.toSet()
    }

    /**
     * 적재 시각은 `DEFAULT now()` 가 찍는다(production 경로의 계약) — 그 값을 고칠 수 없으므로
     * (append-only) **시계를 미래로** 둬서 기한을 넘긴다. 90일은 `RETENTION_ANCHOR`(test 앵커)다.
     */
    private fun sweepFarInTheFuture(): RetentionOutcome {
        val future = Instant.now() + RETENTION_ANCHOR + java.time.Duration.ofSeconds(1)
        return PurgeExpiredData(retentionStore(), { future }, productionPolicy()).run()
    }

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

    /**
     * 계약 등재 키(공고 식별자 둘 + **업체명 둘**) + 담당자 키 넷을 담은 원문으로 **production
     * 경로**를 지난다. `payload` 는 `sourceText` 그대로 들어가고 `payload_fields` 는 계약 투영이
     * 된다 — 업체명은 계약에 있으므로 그 투영에 실린다.
     */
    private fun appendThroughProductionPath(): bidvector.procurement.ObservationKey {
        val registered =
            mapOf(RawKey("bidNtceNo") to "20260101001", RawKey("bidNtceOrd") to "000") +
                COMPANY_NAME_FIXTURE_KEYS.associate { RawKey(it) to COMPANY_NAME_FIXTURE_VALUE }
        val sourceText =
            (
                registered.entries.map { (raw, value) -> """"${raw.name}":"$value"""" } +
                    OFFICER_FIXTURE_KEYS.entries.map { (name, value) -> """"$name":"$value"""" }
            ).joinToString(",", "{", "}")
        val observation =
            RawNoticeObservation.of(
                fields = registered,
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

    /** 보존 저장소 — 지울 키의 해소를 어댑터가 하므로 **기준일만** 넘긴다(D-6F8-6 게이트). */
    private fun retentionStore() = JdbcRetentionStore(dataSource(), RETENTION_REFERENCE_DATE)
}
