package bidvector.adapters.persistence

import java.sql.Connection
import javax.sql.DataSource

/**
 * 원자적 커밋의 유일한 문(scope.md ②, D-4C2-1 갈래 b) — 도메인 write(`raw_observation`
 * append)와 outbox 등록을 같은 트랜잭션에서 커밋한다는 `OPEN-4C1-TX-CONTRACT-UNVERIFIED`의
 * 선언을 이 클래스가 실제로 강제한다.
 *
 * 이 클래스 자신이 [ConnectionSource]다 — `JdbcOutboxPort`·개조된 [JdbcRawObservationStore]
 * 는 이 인스턴스 하나를 주입받아 생애주기 내내 재사용한다(생성자 주입 DI, 전역 상태 없음).
 * [inTransaction] 밖에서 [withConnection]을 부르면 던진다(설계 검토 (2) 우회 3, (1) 「구성으로
 * 닫는 형태」) — 경계를 거치지 않은 등록은 컴파일은 되지만 **실행 시점에 항상 실패**한다.
 *
 * ambient는 스레드 하나마다 독립이다([ThreadLocal]) — 서로 다른 스레드의 동시 `inTransaction`
 * 호출은 서로의 트랜잭션을 보지 못한다(claim 경합 test가 바로 이 성질에 기댄다). 그 안에서
 * 한 겹 더 — [Ambient]가 **연 스레드를 기록**하고(설계 검토 (4)-⑤), 그 참조가 다른
 * 스레드로 넘어가(코루틴 디스패처 이동 등) [withConnection]이 불리면 「경계 밖」이 아니라
 * **「남의 스레드」**라는 더 정확한 사유로 던진다.
 */
class TransactionBoundary(
    private val dataSource: DataSource,
) : ConnectionSource {
    private val ambient = ThreadLocal<Ambient?>()

    /**
     * 커넥션을 열고 [block]을 실행한다 — [block]이 정상 반환하면 커밋, 예외로 빠져나가면
     * 롤백한다(둘 다 끝나면 커넥션을 닫는다). [block] 안에서 이 경계를 [ConnectionSource]로
     * 받은 참여자를 부르면 이 트랜잭션의 커넥션을 그대로 쓴다.
     *
     * `catch`를 두지 않는다(detekt `TooGenericExceptionCaught` — v2-지침서.md §5 「business
     * control flow에 exception을 쓰지 않는다」, `ResilientPredictionCall.kt`와 같은 관례) —
     * [block]이 어떤 예외로 실패하든 그대로 전파시키고, `finally`가 **커밋 여부를 플래그로
     * 보고** 커밋되지 않았을 때만 롤백한다.
     */
    fun <T> inTransaction(block: () -> T): T {
        check(ambient.get() == null) { "이미 열린 트랜잭션 경계 안에서 중첩 호출은 지원하지 않는다" }
        val connection = dataSource.connection
        connection.autoCommit = false
        val owner = Thread.currentThread()
        ambient.set(Ambient(connection, owner))
        var committed = false
        try {
            val result = block()
            connection.commit()
            committed = true
            return result
        } finally {
            if (!committed) connection.rollback()
            ambient.remove()
            connection.close()
        }
    }

    override fun <T> withConnection(block: (Connection) -> T): T {
        val ambientTransaction = ambient.get() ?: error("트랜잭션 경계 밖에서 커넥션에 접근했다")
        val caller = Thread.currentThread()
        check(caller == ambientTransaction.owner) {
            "트랜잭션 경계는 연 스레드에서만 쓸 수 있다(owner=${ambientTransaction.owner}, caller=$caller)"
        }
        return block(ambientTransaction.connection)
    }

    private class Ambient(
        val connection: Connection,
        val owner: Thread,
    )
}
