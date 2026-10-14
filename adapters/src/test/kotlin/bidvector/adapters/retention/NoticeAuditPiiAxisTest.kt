package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

private val SEED_AT = Instant.parse("2026-01-01T00:00:00Z")

/**
 * **D-6B3-1 ① 의 술어 셋**(vr F-2 · privacy F-2) — 「`notice_audit` 은 마스킹 대상 0」을 산문이
 * 아니라 등식으로 잠근다. 그 표는 파기도 마스킹도 받지 않고(A-4 제외) append-only 트리거가
 * UPDATE 를 전부 거부하므로, PII 가 한 번 들어오면 **사후 마스킹이 구조적으로 불가능**하다.
 *
 * 셋째 술어(「담당자 키는 계약 레지스트리에 등재되지 않는다」)는 `FieldContractTest` 가 이미
 * 지므로 여기서 되풀이하지 않는다 — 이 파일이 세우는 것은 앞의 둘이다.
 */
class NoticeAuditPiiAxisTest : PersistenceTestSupport() {
    @BeforeEach
    fun seed() {
        RetentionFixture(dataSource()).seedAll("audit", SEED_AT)
    }

    /**
     * ⓐ **카탈로그 실측** — 실제 감사 행의 `previous_row` 키 집합이 `notice` 의 열 집합이다.
     * 트리거가 `to_jsonb(OLD)` 로 싣기 때문인데, 그 사실을 **감사 행을 실제로 만들어** 센다
     * (정의를 읽어 짐작하지 않는다).
     */
    @Test
    fun `previous_row 의 키 집합은 notice 의 열 집합이다 — 트리거가 행 전체를 싣는다`() {
        updateNoticeToFireAudit()

        previousRowKeys().shouldNotBeEmpty()
        previousRowKeys() shouldContainExactlyInAnyOrder noticeColumns()
    }

    /**
     * ⓑ **등식** — `notice` 의 열 집합 == 보존 분류가 아는 열 집합. 새 열은 이 목록에 들어오지
     * 않고는 초록이 되지 않고, 들어오려면 「이 열은 담당자 PII 가 아니다」를 사람이 판단해야 한다.
     * 열 **이름의 꼴**을 보는 문자열 술어가 아니다 — `Ofcl` 을 담지 않은 담당자 키가 그 술어를
     * 지난다(privacy F-2).
     */
    @Test
    fun `notice 의 열 집합은 보존 분류가 아는 열 집합과 같다`() {
        noticeColumns() shouldContainExactlyInAnyOrder NOTICE_SNAPSHOT_CLASSIFIED_COLUMNS
    }

    // 「술어가 실제로 센다」의 음성 대조는 이 파일에 두지 않는다(cr L-1). 앞 판의
    // `noticeColumns() != 분류 − 한 원소` 는 **집합이 비어 있지 않기만 하면 참**이라 아무 새 성질도
    // 잠그지 않았다 — 위 등식이 이미 그 비교를 하고 있다. 실제로 재야 하는 것은 **열을 더한 스키마에서
    // 등식이 RED 가 되는가**이고, 그것은 변이(V-3: `notice` 에 담당자 꼴 열 + 열 장부 한 줄)가 진다.

    /** 그 표가 **제외**라는 사실도 함께 센다 — 제외가 아니면 위 등식이 지키는 것이 바뀐다. */
    @Test
    fun `notice_audit 은 제외 분류이고 그 사유는 PII 부재다`() {
        val rule = RETENTION_TABLE_RULES.single { it.table == NOTICE_AUDIT_SNAPSHOT_TABLE }

        (rule as RetentionRule.Exempt).reason shouldBe ExemptReason.AUDIT_WITHOUT_PII
    }

    /** 그 스냅숏이 담기는 표에 FK 가 없다는 사실(cr M-3 의 보존 축 사유) — 파기와 무관하게 남는다. */
    @Test
    fun `notice_audit 은 notice 로 가는 FK 가 없다 — 부모 파기가 이 표를 건드리지 않는다`() {
        val edges = dataSource().connection.use(::readForeignKeyEdges)

        edges
            .filter { it.child == NOTICE_AUDIT_SNAPSHOT_TABLE && it.parent == NOTICE_SNAPSHOT_SOURCE_TABLE }
            .shouldBeEmpty()
    }

    /**
     * 가드가 없는 열 하나를 바꿔 `AFTER UPDATE` 감사 트리거를 태운다 — 금액·하한율·상태 축은
     * 신선도 가드가 새 `observation_key` 를 요구한다(V2). `deadline_at` 에는 가드가 없다.
     */
    private fun updateNoticeToFireAudit() =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("UPDATE notice SET deadline_at = now() WHERE notice_number = 'RET-audit'")
            }
        }

    private fun previousRowKeys(): List<String> =
        queryColumn(
            "SELECT jsonb_object_keys(previous_row) FROM $NOTICE_AUDIT_SNAPSHOT_TABLE " +
                "WHERE reason = 'REVISION_UPDATE'",
        )

    private fun noticeColumns(): List<String> =
        queryColumn(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = '$NOTICE_SNAPSHOT_SOURCE_TABLE'",
        )

    private fun queryColumn(sql: String): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    val values = mutableListOf<String>()
                    while (rs.next()) values += rs.getString(1)
                    values.distinct()
                }
            }
        }
}
