package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.sharedkernel.Resolution
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionPolicyData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** 적재자가 **투영 열에 심은** 담당자 키 — 계약 미등재라 남길 집합에 들어올 수 없어야 한다. */
private const val PINNED_OFFICER_KEY = "ntceInsttOfclNm"
private const val PINNED_OFFICER_VALUE = "PINNED-OFFICER-FIXTURE"

/** 계약에 등재되지 않은 임의 키 — 상류가 새로 보내는 키의 자리를 흉내낸다. */
private const val PINNED_UNKNOWN_KEY = "brandNewUpstreamKey"

/**
 * **vr R2-F2(a)** — 남김 집합의 **출처**를 잰다. 그 집합이 행 자신의 투영 열에서 오면 **적재자가
 * 마스킹 결과를 정한다**: 투영에 담당자 키를 실은 행은 마스킹 뒤에도 두 열에 그 키를 갖고, 표지가
 * 찍혀 다시 마스킹되지 않는다(영구). 앱 역할은 그런 행을 INSERT 할 수 있다.
 *
 * 집합의 출처가 **계약 레지스트리**면 그 경로가 닫힌다 — 심은 키는 계약 밖이므로 남길 집합에 없고,
 * 두 열 모두 allow-list 로 줄어든다. 이 파일은 그 성질을 **심은 행**으로 재는 자리다.
 */
class MaskingKeepSetSourceTest : PersistenceTestSupport() {
    @Test
    fun `공허 대조 — 심은 키가 두 열에 실제로 들어간다`() {
        seedPinnedRow()

        columnOf("payload") shouldContainPinned true
        columnOf("payload_fields::text") shouldContainPinned true
    }

    @Test
    fun `투영에 심은 담당자 키는 마스킹 뒤 원문에서 사라진다 — 남김 집합이 행에서 오지 않는다`() {
        seedPinnedRow()

        sweepAtCutoff()

        val payload = columnOf("payload")
        payload shouldNotContain PINNED_OFFICER_KEY
        payload shouldNotContain PINNED_OFFICER_VALUE
    }

    @Test
    fun `투영 열에서도 사라진다 — 두 열이 같은 allow-list 로 줄어든다`() {
        seedPinnedRow()

        sweepAtCutoff()

        val projection = columnOf("payload_fields::text")
        projection shouldNotContain PINNED_OFFICER_KEY
        projection shouldNotContain PINNED_OFFICER_VALUE
    }

    @Test
    fun `계약 밖 미지 키도 사라진다 — 이름을 몰라도 빠진다(deny-list 가 아니다)`() {
        seedPinnedRow()

        sweepAtCutoff()

        columnOf("payload") shouldNotContain PINNED_UNKNOWN_KEY
        columnOf("payload_fields::text") shouldNotContain PINNED_UNKNOWN_KEY
    }

    /** 통제군 — 계약 등재 키(공고 식별자)는 두 열에 그대로 남는다(마스킹이 전부를 지우지 않는다). */
    @Test
    fun `계약 등재 키는 두 열에 남는다`() {
        seedPinnedRow()

        sweepAtCutoff()

        columnOf("payload").contains("bidNtceNo") shouldBe true
        columnOf("payload_fields::text").contains("bidNtceNo") shouldBe true
    }

    private infix fun String.shouldContainPinned(expected: Boolean) {
        contains(PINNED_OFFICER_KEY) shouldBe expected
    }

    /**
     * 적재자가 쓴 행 — 두 열 모두에 계약 밖 키가 들어 있다. 적재 시각은 기한을 넘겨 심는다(소유자
     * 연결이라 쓸 수 있다 — 비소유자 적재에는 DB 시계가 강제된다).
     */
    private fun seedPinnedRow() =
        dataSource().connection.use { connection ->
            val payload =
                """{"bidNtceNo":"RET-pin","bidNtceOrd":"000",""" +
                    """"$PINNED_OFFICER_KEY":"$PINNED_OFFICER_VALUE","$PINNED_UNKNOWN_KEY":"X"}"""
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                        "observed_at, release_sha, inserted_at) " +
                        "VALUES ('ret-pin', 'NOTICE_LIST', '$payload', '$payload'::jsonb, " +
                        "'$JUST_OLDER', 'ret-test', '$JUST_OLDER')",
                )
            }
        }

    private fun sweepAtCutoff() = PurgeExpiredData(retentionStore(), { NOW }, productionPolicy()).run()

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

    private fun columnOf(expression: String): String =
        dataSource().connection.use { connection ->
            val sql = "SELECT $expression AS value FROM raw_observation WHERE observation_key = 'ret-pin'"
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    check(rs.next()) { "행이 없다" }
                    rs.getString("value")
                }
            }
        }

    private fun retentionStore() = JdbcRetentionStore(dataSource(), RETENTION_REFERENCE_DATE)
}
