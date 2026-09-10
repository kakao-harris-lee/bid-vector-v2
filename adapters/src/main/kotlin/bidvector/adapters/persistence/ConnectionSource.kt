package bidvector.adapters.persistence

import java.sql.Connection

/**
 * 커넥션을 빌려주는 경계(D-4C2-1 갈래 b, scope.md ②) — `DataSource`를 직접 갖지 않는
 * 참여자([bidvector.adapters.event.JdbcOutboxPort]·개조된 [JdbcRawObservationStore])가 이
 * 것만 받는다. [withConnection]이 커넥션을 열고 닫는 책임을 진다 — 참여자는 커넥션을 직접
 * 닫지 않는다: [TransactionBoundary]가 주는 구현은 트랜잭션이 끝날 때(커밋/롤백)까지
 * 커넥션을 살려 두고, [DataSourceConnectionSource]는 매 호출마다 새로 열고 닫는다(3D의
 * 기존 `dataSource.connection.use { ... }` 관례와 동치 — 호출부 무변경, D-4C2-1).
 *
 * `withConnection`이 메서드 자신의 타입 매개변수 `T`를 갖는다 — Kotlin의 SAM 변환(트레일링
 * 람다)은 인터페이스 자신이 타입 매개변수를 갖는 함수형 인터페이스만 지원하고 메서드 자체의
 * 제네릭은 지원하지 않는다. 그래서 `fun interface`가 아니라 평범한 `interface`로 두고
 * 구현은 전부 `object : ConnectionSource { override fun <T> ... }` 형태로 쓴다
 * ([DataSourceConnectionSource], `TransactionBoundary.participantSource`).
 */
interface ConnectionSource {
    fun <T> withConnection(block: (Connection) -> T): T
}

/**
 * `DataSource`를 그대로 감싸는 기본 구현 — [JdbcRawObservationStore]의 기존 `DataSource`
 * 생성자가 위임하는 대상이다. 매 호출이 커넥션을 새로 열고 닫는다(트랜잭션 경계 없음,
 * 개조 전과 같은 거동).
 */
internal class DataSourceConnectionSource(
    private val dataSource: javax.sql.DataSource,
) : ConnectionSource {
    override fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)
}
