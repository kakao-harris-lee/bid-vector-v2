package bidvector.adapters.evaluation

import bidvector.adapters.persistence.Sql
import bidvector.adapters.persistence.reconstructNotice
import bidvector.adapters.persistence.setTextArray
import bidvector.adapters.persistence.toNoticeRow
import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeStatus
import bidvector.procurement.isBiddable
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.strategy.Clock
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * SQL에 실릴 "입찰 가능" 상태 집합을 도메인 술어([isBiddable])에서 기계 산출한다(D-6F2-2)
 * — 상태 리터럴을 SQL 문자열에 직접 적지 않는다. `now`·`future`는 판정 자체가 요구하는
 * 임의의 두 시각일 뿐이다(`now`가 `future`보다 앞서기만 하면 된다) — 그 둘을 고정해
 * 마감 축을 항상 "아직 안 지남"으로 묶으면 상태 값만의 효과가 남는다. [NoticeStatus]에
 * 값이 늘거나 [isBiddable]의 정의가 바뀌면 이 집합도 같이 움직인다(`CandidateStatusSetTest`
 * 가 그 동기화를 잰다 — 하한 단언이 아니라 집합 등식으로).
 */
internal fun biddableStatuses(): Set<NoticeStatus> {
    val now = Instant.EPOCH
    val future = now.plusSeconds(1)
    return NoticeStatus.entries.filterTo(mutableSetOf()) { status -> isBiddable(status, now, future) }
}

/**
 * 상한 초과(D-6F2-4) — 조용한 `LIMIT`로 후보를 잘라내지 않는다. 질의가 `cap + 1`행만 읽어
 * "넘었다"는 사실만 알 뿐 정확한 초과 건수는 세지 않는다 — `cap`만 사유에 담는다.
 */
class CandidateCapExceededException(
    val cap: Int,
) : RuntimeException(
        "후보 스캔이 상한(cap=$cap)을 초과했다 — 조용히 자르지 않는다",
    )

/**
 * [CandidateSourcePort] 첫 production 구현(M6/6F-2, D-6F2-1~6). `EvaluateCandidatesUseCase
 * .evaluate()`의 둘째 줄(`candidateSource.openCandidates()`)이 지금까지 test fake뿐이었다
 * — 전략이 있어도 후보가 0이면 파이프라인은 여전히 빈 목록을 돈다. `notice`표(V1)의 기존
 * 행을 다건 스캔 질의로 읽는다(마이그레이션·인덱스 없음, D-6F2-7).
 *
 * **D-6F2-6 — 도메인 타입을 직접 만들지 않는다.** `Notice` 복원은 기존
 * [reconstructNotice](`adapters.persistence`, 같은 모듈의 `internal`)를 그대로 쓴다 — 이
 * 클래스는 `Notice`의 유일한 공개 생성 경로(`collected` + `applyEvent`)를 우회하지 않는다.
 *
 * **`cap`은 생성자 주입이고 기본값이 없다(D-6F2-4).** 값과 초과 시 운영 처분은 조립 축의
 * 결정이다(`OPEN-6F2-CANDIDATE-BOUND`) — 이 클래스는 "조용히 자르지 않는다"만 고정한다.
 * `cap <= 0`은 생성자에서 거부한다(verifier r1 LOW-1) — 잘못된 배선이 도메인 실패가 아니라
 * `LIMIT` 음수 등 DB 오류로 새지 않게 한다.
 */
class JdbcCandidateSource(
    private val dataSource: DataSource,
    private val clock: Clock,
    private val cap: Int,
) : CandidateSourcePort {
    init {
        require(cap > 0) { "cap은 1 이상이어야 한다: $cap" }
    }

    /**
     * 상태 배열 바인딩은 [setTextArray](`adapters.persistence`, 6F-1 이 같은 목적으로 신설,
     * verifier r1 code-reviewer MEDIUM)을 그대로 쓴다 — `connection.createArrayOf("text",
     * ...)`를 여기서 다시 적지 않는다(중복 금지, CLAUDE.md).
     */
    override fun openCandidates(): List<Notice> {
        val now = clock.now()
        val scanned =
            dataSource.connection.use { connection ->
                connection.prepareStatement(Sql.SELECT_OPEN_CANDIDATES).use { statement ->
                    statement.setTextArray(1, biddableStatuses().map { it.name })
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setInt(3, cap + 1)
                    statement.executeQuery().use { rs -> rs.readCandidateRows() }
                }
            }
        if (scanned.size > cap) throw CandidateCapExceededException(cap)
        return scanned.map { (id, row) -> id.reconstructNotice(row) }
    }

    private fun ResultSet.readCandidateRows() =
        generateSequence { if (next()) candidateId() to toNoticeRow() else null }.toList()

    private fun ResultSet.candidateId(): NoticeId =
        NoticeId(NoticeNumber(getString("notice_number")), NoticeRound(getString("notice_round")))
}
