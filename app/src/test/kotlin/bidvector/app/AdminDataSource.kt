package bidvector.app

import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/**
 * 컨테이너 **소유자** 자격으로 여는 비풀링 `DataSource` — **fixture 초기화 전용**(M6/6E-2a P-5).
 *
 * production `DataSource` 빈은 최소 권한 역할(`bidvector_app`)로 연결을 내주고 그 역할에는
 * `TRUNCATE` 권한이 **없다** — 어느 마이그레이션도 주지 않는다. test 사이의 행 격리는 그래서
 * **권한 밖 작업**이고, 조립 빈이 아니라 이 자리를 지난다(adapters 의 `PersistenceTestSupport`
 * 가 admin 연결로 `truncateAllTables()` 를 도는 것과 같은 관례다).
 *
 * **조립이 실제로 하는 일은 그대로 production 빈을 지난다** — HTTP 왕복·러너·조회가 역할의
 * 권한 안에서 성립하는지가 그 test 들이 재는 사실이고, 이 helper 가 그 자리를 대신하지 않는다.
 *
 * 한 자리에 두는 이유: 같은 여섯 줄이 test class 마다 복사되면 중복 탐지 게이트가 그것을 세고,
 * 「왜 admin 인가」의 근거도 그만큼 흩어진다.
 */
fun adminDataSource(container: PostgreSQLContainer): DataSource =
    PGSimpleDataSource().apply {
        setUrl(container.jdbcUrl)
        user = container.username
        password = container.password
    }
