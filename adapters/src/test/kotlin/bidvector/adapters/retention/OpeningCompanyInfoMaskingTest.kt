package bidvector.adapters.retention

import bidvector.adapters.koneps.JsonValue
import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.adapters.koneps.RawItemOutcome
import bidvector.adapters.koneps.mapMaskedOpeningItem
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.KONEPS_COLLECTION_POLICY
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 개찰결과 목록의 합성 문자열 — 수집 시점 마스킹이 사업자등록번호·대표자명을 버리고 셋을 남긴다. */
private const val OPENING_CORP_RAW = "PROBECORP^1234567890^PROBECEO^1000000^99.5"

/** 그 수집 마스킹을 지난 모양 — **상호가 성분 0 으로 남는다**(그래서 보존 마스킹의 대상이다). */
private const val OPENING_CORP_COLLECTED = "PROBECORP^1000000^99.5"

/** 상호 자리값 — 마스킹 뒤 두 열 어디에도 남아서는 안 된다. */
private const val OPENING_CORP_NAME = "PROBECORP"

/**
 * **R2-F1 · privacy F-6** — 업체명을 나르는 셋째 등재 개념(`opengCorpInfo`)이 마스킹 뒤 사라지는지
 * **출하 수집 경로 위에서** 잰다.
 *
 * 왜 이 경로인가: 앞 판의 마스킹 test 는 전부 선언된 두 개념에서 키를 끌어왔고 fixture 어디에도 이
 * 키가 없었다. 그래서 「선언이 모자랐다」를 아무 test 도 볼 수 없었다 — 이 파일은 **수집이 실제로
 * 만드는 행**(`mapMaskedOpeningItem` → `JdbcRawObservationStore`)으로 그 사각을 메운다.
 *
 * 이 누락은 **사후에 고칠 수 없다**: 한 번 마스킹된 행은 표지가 찍혀 V18 관문이 둘째 마스킹을
 * 거부한다. 그래서 「지울 집합에 들어 있다」가 아니라 「그 행에서 실제로 사라진다」를 재야 한다.
 */
class OpeningCompanyInfoMaskingTest : PersistenceTestSupport() {
    @Test
    fun `공허 대조 — 수집 경로가 상호를 담은 행을 만든다`() {
        val key = appendOpeningObservation()

        columnOf("payload", key) shouldContainCollected true
        columnOf("payload_fields::text", key) shouldContainCollected true
    }

    @Test
    fun `기한 넘은 개찰 행의 업체명 키는 원문에서 사라진다`() {
        val key = appendOpeningObservation()

        sweepFarInTheFuture().shouldBeInstanceOf<RetentionOutcome.Completed>().report.maskedTotal shouldBe 1

        val payload = columnOf("payload", key)
        payload shouldNotContain OPENING_CORP_NAME
        payload shouldNotContain "opengCorpInfo"
    }

    @Test
    fun `같은 행의 투영 열에서도 사라진다`() {
        val key = appendOpeningObservation()

        sweepFarInTheFuture()

        val projection = columnOf("payload_fields::text", key)
        projection shouldNotContain OPENING_CORP_NAME
        projection shouldNotContain "opengCorpInfo"
    }

    /** 음성 대조 — 기한 안쪽 행은 손대지 않는다(마스킹이 시각 술어를 지난다는 것까지 재야 한다). */
    @Test
    fun `기한 안쪽 개찰 행의 업체명은 두 열에 남는다`() {
        val key = appendOpeningObservation()

        PurgeExpiredData(retentionStore(), { Instant.now() }, productionPolicy()).run()

        columnOf("payload", key) shouldContainCollected true
        columnOf("payload_fields::text", key) shouldContainCollected true
    }

    private infix fun String.shouldContainCollected(expected: Boolean) {
        contains(OPENING_CORP_COLLECTED) shouldBe expected
    }

    /**
     * 적재 시각은 `DEFAULT now()` 가 찍는다(출하 경로의 계약이고 V18 의 INSERT 관문이 비소유자에게
     * 그것을 강제한다) — 그 값을 고칠 수 없으므로 **시계를 미래로** 둬서 기한을 넘긴다.
     */
    private fun sweepFarInTheFuture(): RetentionOutcome {
        val future = Instant.now() + RETENTION_ANCHOR + Duration.ofSeconds(1)
        return PurgeExpiredData(retentionStore(), { future }, productionPolicy()).run()
    }

    /** 출하 수집 경로 — 개찰결과 목록 항목 하나를 매핑해 원문 저장 어댑터로 적재한다. */
    private fun appendOpeningObservation(): String {
        val item =
            KonepsJsonParser.parse(
                """{"bidNtceNo":"20260101001","bidNtceOrd":"000","opengCorpInfo":"$OPENING_CORP_RAW"}""",
                PAYLOAD_MASK_MAX_DEPTH,
            ) as JsonValue.JsonObject
        val outcome =
            mapMaskedOpeningItem(
                item = item,
                policy = collectionPolicy(),
                sourceEndpoint = SourceEndpoint.OPENING_RESULT_LIST,
                observedAt = Instant.parse("2026-01-01T00:00:00Z"),
                rowIdentifierRawKeys = emptyList(),
            )
        return appendRawObservation(outcome.shouldBeInstanceOf<RawItemOutcome.Mapped>().observation).value
    }

    private fun collectionPolicy() =
        (KONEPS_COLLECTION_POLICY.resolve(RETENTION_REFERENCE_DATE) as Resolution.Resolved).value

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

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

    private fun retentionStore() = JdbcRetentionStore(dataSource())
}
