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
 * **`sealed`다(verifier r1 H-2 시정)** — 이전 판은 평범한 `public interface`였는데, 그러면
 * `app`(또는 다른 어느 모듈이든)이 「경계 아닌 커넥션 공급자」를 직접 구현해
 * `JdbcOutboxPort`에 넘길 수 있었다(실행 재현: `PROBE-1`, 트랜잭션 없이 outbox 1행 커밋).
 * 봉투 위조가 아니라 **커넥션 공급 경로 자체의 위조**였다 — `EventEnvelope`·`OutboxTransition`
 * 의 폐쇄(4C-1)와 같은 형태의 구멍이 이 슬롯에 남아 있었다. `sealed`는 이 허가된 구현 집합을
 * **컴파일 층에서** `TransactionBoundary`·[DataSourceConnectionSource] 둘로 고정한다 —
 * Kotlin sealed 규약상 새 구현(익명 object 포함)은 어느 모듈에서도 추가할 수 없다.
 *
 * `withConnection`이 메서드 자신의 타입 매개변수 `T`를 갖는다 — Kotlin의 SAM 변환(트레일링
 * 람다)은 인터페이스 자신이 타입 매개변수를 갖는 함수형 인터페이스만 지원하고 메서드 자체의
 * 제네릭은 지원하지 않는다. 그래서 `fun interface`가 아니라 평범한(이제 `sealed`) `interface`
 * 로 두고, 두 구현은 전부 이름 있는 클래스([DataSourceConnectionSource],
 * [TransactionBoundary])로 쓴다 — sealed 인터페이스는 애초에 익명 object 구현 자체를
 * 허용하지 않으므로(모든 모듈에서), SAM 변환 미지원과 무관하게 이 형태가 유일한 선택이다.
 *
 * **잔여(알려진 제한)** — `sealed`는 **타입**을 두 구현으로 고정하지만, `adapters` 모듈
 * **안**에서는 여전히 `JdbcOutboxPort(DataSourceConnectionSource(dataSource))`처럼 비경계
 * 값을 손으로 조립해 넘길 수 있다(타입 검사만으로는 「어느 인스턴스인지」를 못 막는다).
 * 이것은 새 권한이 아니다 — `DataSource`를 쥔 같은 모듈의 코드는 이미 원시 SQL로 outbox에
 * 직접 INSERT할 수 있어(verifier PROBE-3), 그 권한 이하다. 위협 모델이 막는 것은 **다른
 * 모듈**의 구조적 우회이고, `sealed`가 정확히 그것을 닫는다(교차 모듈 컴파일 거부 양성
 * 대조, `reports/evidence/m4/4c2/commands.md`).
 */
sealed interface ConnectionSource {
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
