package bidvector.adapters.access

import bidvector.adapters.persistence.ConnectionSource
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration

/**
 * 로그인 scope 의 커넥션 공급 — **빌린 커넥션마다 대기 상한을 먼저 씌운다**(code-review r4 M-1).
 *
 * 앞 판은 상한이 확정 문장에만 있었다. 그런데 같은 계정 행을 잠그는 문장이 로그인 경로에 하나 더
 * 있다 — 실패 계수 갱신, 곧 **틀린 비밀 문구 경로**다. 그쪽은 **자격 없이 누구나 칠 수 있는데**
 * 무한정 기다렸다. 상한이 필요했던 이유(기다리는 쪽이 커넥션을 쥔 채 서 있어 풀이 마른다)가
 * 더 싼 입구로 그대로 남아 있었고, 보호의 순서가 뒤집혀 있었다.
 *
 * 그래서 상한을 **문장이 아니라 scope** 에 건다. 이 타입을 통해 빌린 커넥션은 어느 문장을 쏘든
 * 같은 상한 아래 선다 — 「이 scope 의 대기 상한」이 비로소 사실이다.
 *
 * **`ConnectionSource` 를 구현하지 않는다.** 그 인터페이스는 `sealed` 이고 Kotlin 의 sealed 는
 * **같은 패키지**의 구현만 허용한다 — 래퍼를 그 타입으로 만들려면 `adapters.persistence` 에
 * 새 클래스를 두어야 하고, 그것은 이 slice 의 in_scope 밖이다. 대신 같은 모양의 좁은 타입을
 * 여기 두고 접근 어댑터 둘이 이것을 받는다(효과는 같고 범위는 이 패키지 안이다).
 */
internal class SignInConnections(
    private val connections: ConnectionSource,
    private val lockWait: Duration,
) {
    /**
     * 상한을 씌운 커넥션으로 [block] 을 돌린다. 대기가 상한을 넘으면 **`null`** 이다 —
     * 호출부가 그것을 자기 어휘의 「못 했다」로 옮긴다.
     *
     * **접는 것은 `55P03` 하나**다(code-review r4 L-1 의 절반). 그 밖의 DB 오류는 그대로 올린다 —
     * 삼키면 「로그인이 안 된다」가 원인 없이 반복된다. 판정은 SQLState 로 하고 예외 문면을
     * 읽지 않는다(문면에는 값이 실릴 수 있고, 판정이 문구에 매이면 드라이버 판에 따라 흔들린다).
     *
     * **경합의 사유는 여기서 사라지지 않는다** — `null` 이 그 사실을 담아 호출부로 나가고,
     * 확정 경로는 그것을 이름 있는 결과([SignInWrite.Contended])로 올린다. 이 층은 로그를 쓸 수
     * 없다(`adapters` 의 바깥 참조 허용 집합에 로깅 패키지가 없다) — 관측은 그 이름을 받는
     * 위층의 일이고, 아직 그 소비자가 없다는 사실은 알려진 제한으로 등재돼 있다.
     */
    fun <T : Any> withBoundedConnection(block: (Connection) -> T): T? =
        try {
            connections.withConnection { connection ->
                applyLockWait(connection)
                block(connection)
            }
        } catch (failure: SQLException) {
            if (failure.sqlState != LOCK_NOT_AVAILABLE) throw failure
            null
        }

    /**
     * 상한은 **씌우되 접지 않는다** — 행 잠금을 잡지 않는 읽기 문장이 쓴다. 계정 조회는 평범한
     * SELECT 라 MVCC 아래에서 행 잠금에 막히지 않으므로 경합이 성립하지 않는다. 그래도 같은
     * 공급원을 지나게 두는 이유는 「이 scope 의 모든 문장이 같은 상한 아래 선다」를 **예외 없이**
     * 참으로 만들기 위해서다(예외를 하나 두면 다음 문장이 그 예외로 들어간다).
     */
    fun <T> withConnection(block: (Connection) -> T): T =
        connections.withConnection { connection ->
            applyLockWait(connection)
            block(connection)
        }

    /**
     * 이 **트랜잭션 안에서만** 사는 상한이다 — 셋째 인자 `true` 가 `LOCAL` 이다. 평문
     * `SET lock_timeout` 이었다면 풀로 돌아간 커넥션에 값이 눌어붙어 다음 차용자 전부에게
     * 조용히 적용됐을 것이다.
     */
    private fun applyLockWait(connection: Connection) {
        connection.prepareStatement(AccessSql.SET_LOCAL_LOCK_WAIT).use { statement ->
            statement.setString(1, "${lockWait.toMillis()}ms")
            statement.executeQuery().use { rs -> rs.next() }
        }
    }

    private companion object {
        /** PostgreSQL 이 `lock_timeout` 초과에 내는 SQLState — 「지금은 못 잡는다」의 표준 신호다. */
        const val LOCK_NOT_AVAILABLE = "55P03"
    }
}
