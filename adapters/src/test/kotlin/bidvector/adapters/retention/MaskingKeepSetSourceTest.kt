package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.sharedkernel.Resolution
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionPolicyData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 적재자가 **투영 열에 심은** 담당자 키 — 계약 미등재라 남길 집합에 들어올 수 없어야 한다. */
private const val PINNED_OFFICER_KEY = "ntceInsttOfclNm"
private const val PINNED_OFFICER_VALUE = "PINNED-OFFICER-FIXTURE"

/** 계약에 등재되지 않은 임의 키 — 상류가 새로 보내는 키의 자리를 흉내낸다. */
private const val PINNED_UNKNOWN_KEY = "brandNewUpstreamKey"

/** 중첩 값을 심은 행의 키 — 적재자(앱 역할)만 만들 수 있는 모양이다. */
private const val NESTED_KEY = "ret-nested"

/** **계약 키 이름 아래** 중첩된 상호 — 키 축 allow-list 로는 걸러지지 않는다. */
private const val NESTED_COMPANY_NAME = "NESTED-CORP-FIXTURE"

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

    /**
     * **vr R3-F1 · mig M-1 · priv F-10** — 투영 열의 **값 형태**까지 `payload` 와 같은 규칙으로 걸러진다.
     *
     * 앞 판은 투영을 **키 축**으로만 줄였다(`WHERE e.key = ANY (?)`). 그래서 적재자가 **계약 키 이름
     * 아래 중첩 객체**를 심으면 그 값이 `payload` 에서는 사라지고(원문 판독이 비스칼라를 버린다)
     * `payload_fields` 에는 **남았다** — 표지가 찍혀 다시 마스킹할 수 없으니 영구다. 두 열이 「같은
     * allow-list 로 줄어든다」는 이 slice 의 주장이 한 축(값 모양)에서 성립하지 않던 자리다.
     */
    @Test
    fun `남길 키 아래 중첩된 값은 투영 열에서도 사라진다 — 두 열의 술어가 값 축에서도 같다`() {
        seedNestedUnderKeptKey()

        sweepFarInTheFuture()

        val projection = columnOf("payload_fields::text", NESTED_KEY)
        projection shouldNotContain NESTED_COMPANY_NAME
        projection shouldNotContain "prcbdrNm"
    }

    @Test
    fun `같은 행의 원문에서도 사라진다 — 음성 대조가 아니라 대칭의 확인이다`() {
        seedNestedUnderKeptKey()

        sweepFarInTheFuture()

        columnOf("payload", NESTED_KEY) shouldNotContain NESTED_COMPANY_NAME
    }

    /** 공허 대조 — 심은 중첩 값이 두 열에 실제로 들어간다(마스킹 전). */
    @Test
    fun `공허 대조 — 중첩 값이 두 열에 실린다`() {
        seedNestedUnderKeptKey()

        columnOf("payload", NESTED_KEY) shouldContain NESTED_COMPANY_NAME
        columnOf("payload_fields::text", NESTED_KEY) shouldContain NESTED_COMPANY_NAME
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

    private fun columnOf(
        expression: String,
        key: String = "ret-pin",
    ): String =
        dataSource().connection.use { connection ->
            val sql = "SELECT $expression AS value FROM raw_observation WHERE observation_key = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "행이 없다: $key" }
                    rs.getString("value")
                }
            }
        }

    /**
     * **앱 역할이 심는다** — 출하 수집 경로는 평평한 문자열·`null` 만 쓰므로(`ObservationPayloadCodec`)
     * 이 모양은 적재자의 직접 INSERT 로만 생긴다. 적재 시각은 트리거가 DB 시계로 덮으므로(이 slice 의
     * ③) 기한을 넘기는 쪽은 **시계**다 — 그래서 아래 sweep 이 미래를 본다.
     */
    private fun seedNestedUnderKeptKey() =
        appConnection().use { connection ->
            val nested = """{"bidNtceNo":{"prcbdrNm":"$NESTED_COMPANY_NAME"},"bidNtceOrd":"000"}"""
            connection
                .prepareStatement(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                        "observed_at, release_sha) VALUES (?, 'NOTICE_LIST', ?, ?::jsonb, now(), 'ret-test')",
                ).use { statement ->
                    statement.setString(1, NESTED_KEY)
                    statement.setString(2, nested)
                    statement.setString(3, nested)
                    statement.executeUpdate()
                }
            connection.commit()
        }

    private fun sweepFarInTheFuture() =
        PurgeExpiredData(
            retentionStore(),
            { Instant.now() + RETENTION_ANCHOR + Duration.ofSeconds(1) },
            productionPolicy(),
        ).run()

    private fun retentionStore() = JdbcRetentionStore(dataSource())
}
