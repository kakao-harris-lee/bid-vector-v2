package bidvector.adapters.audit

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * 요청 감사 행 하나(M6/6A-1 D-6A1-7) — 요청 본문·자격증명 값을 담지 않는다. `subject`는
 * 인증 판정 결과의 라벨이지 자격증명 값 자체가 아니다(`OperatorCredentialFilter`가 정한다).
 */
data class ApiAuditRow(
    val occurredAt: Instant,
    val subject: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMillis: Long,
    val correlationId: String,
)

/**
 * `api_request_audit` 표의 유일한 쓰기 경로((2b) 「닫는다」) — **추가 전용**이다. 읽기·삭제
 * 메서드를 두지 않는다 — 보존·파기 정책(6B-3)이 아직 없는 상태에서 이 클래스가 읽기
 * 경로를 열면 그 정책 없이 감사 행이 다른 목적으로 조회될 여지가 생긴다(D-6A1-7).
 *
 * 커넥션 풀 없이 `DataSource`를 직접 받는다(D-6A1-18, `OPEN-6A1-CONNECTION-POOL`) —
 * `JdbcStrategyRepository`와 같은 관례(`dataSource.connection.use { }`).
 */
class ApiAuditStore(
    private val dataSource: DataSource,
) {
    fun append(row: ApiAuditRow) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(ApiAuditSql.INSERT_API_REQUEST_AUDIT).use { statement ->
                // detekt MagicNumber — 바인딩 순서를 리터럴 색인이 아니라 증가하는 index로
                // 짠다(`bidvector.adapters.strategy.StrategyRow.bindStrategyRow`와 같은 관례).
                var index = 1
                statement.setTimestamp(index++, Timestamp.from(row.occurredAt))
                statement.setString(index++, row.subject)
                statement.setString(index++, row.method)
                statement.setString(index++, row.path)
                statement.setInt(index++, row.statusCode)
                statement.setLong(index++, row.durationMillis)
                statement.setString(index, row.correlationId)
                statement.executeUpdate()
            }
        }
    }
}
