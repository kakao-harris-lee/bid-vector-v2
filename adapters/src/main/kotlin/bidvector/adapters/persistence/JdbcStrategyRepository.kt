package bidvector.adapters.persistence

import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.StrategyViolation
import bidvector.strategy.validate
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.StrategyRepository
import java.sql.Connection
import javax.sql.DataSource

/**
 * [StrategyRepository]의 첫 production 구현(M6/6F-1, D-6F1-1~6). 저장소에 이 port의 실
 * 구현이 없었다 — `EvaluateCandidatesUseCase.evaluate()`의 첫 줄이 `strategies.load()`라
 * 이 어댑터 없이는 어떤 후보도 판정에 들어가지 못한다.
 *
 * **D-6F1-2 — 도메인 타입을 직접 만들지 않는다.** [OperatorStrategy]의 유일한 생성 경로는
 * [bidvector.strategy.validate]다(`internal constructor`). 이 클래스는 행을
 * [StrategyRow]로 읽고 [StrategyRow.toDraft]로 **초안**([StrategyDraft], 평범한 public
 * 타입)을 만든 다음 그 검증 함수를 그대로 통과시킨다 — port 시그니처·가시성은
 * 무편집이고 새 public 표면이 없다(scope.md (2b) 표).
 *
 * **D-6F1-6 — 정책은 조립이 주입한다.** [policy]는 이미 해소된
 * [Resolution.Resolved]다 — 이 클래스는 [bidvector.strategy.STRATEGY_POLICY]를 참조하지
 * 않는다(어댑터가 정책 파일의 두 번째 독자가 되지 않는다).
 */
class JdbcStrategyRepository(
    private val dataSource: DataSource,
    private val policy: Resolution.Resolved<StrategyPolicyData>,
) : StrategyRepository {
    /**
     * **D-6F1-4 — 전략 없음(첫 기동)은 실패가 아니라 빈 전략이다.** 행이 없으면
     * `StrategyDraft()`(전부 기본값)와 `StrategyRevision(0)`을 그대로 [validate]에
     * 넣는다 — 그 값도 검증 함수를 지난다(이 갈래가 `Invalid`로 떨어질 일은 없다: 빈
     * draft는 어떤 정책 범위에서도 위반을 만들지 않는다).
     *
     * **D-6F1-3 — 저장된 값이 현재 정책으로 무효면 실패한다.** 전략을 지어내지
     * 않는다 — [StrategyValidation.Invalid]는 [InvalidStoredStrategyException]으로
     * 번역되어 그 사유(위반 목록)를 그대로 담아 전파된다.
     */
    override fun load(): OperatorStrategy {
        val row = dataSource.connection.use { it.querySingletonStrategyRow() }
        val draft = row?.toDraft() ?: StrategyDraft()
        val revision = StrategyRevision(row?.revision ?: 0)
        return when (val result = validate(draft, revision, policy)) {
            is StrategyValidation.Valid -> result.strategy
            is StrategyValidation.Invalid -> throw InvalidStoredStrategyException(result.violations)
        }
    }

    /**
     * 현재 값(싱글턴 upsert)과 개정 이력(append-only insert)을 한 트랜잭션에서 함께
     * 쓴다 — [OperatorStrategy]의 필드를 [StrategyRow]로 그대로 옮긴다(우회 (6), 자기
     * 값을 지어 쓰지 않는다). [JdbcNoticeRepository.persist]와 같은 관례로 `catch`를
     * 두지 않는다 — `connection.use { }`가 커밋되지 않은 트랜잭션을 닫으며 롤백한다
     * (business control flow에 exception을 쓰지 않는다, v2-지침서.md §5).
     */
    override fun save(applied: AppliedStrategy) {
        val row = applied.strategy.toRow()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.prepareStatement(Sql.UPSERT_STRATEGY).use { statement ->
                statement.bindStrategyRow(1, row)
                statement.executeUpdate()
            }
            connection.prepareStatement(Sql.INSERT_STRATEGY_REVISION).use { statement ->
                statement.bindStrategyRow(1, row)
                statement.executeUpdate()
            }
            connection.commit()
        }
    }

    private fun Connection.querySingletonStrategyRow(): StrategyRow? =
        prepareStatement(Sql.SELECT_STRATEGY).use { statement ->
            statement.executeQuery().use { rs -> if (rs.next()) rs.toStrategyRow() else null }
        }
}

/**
 * D-6F1-3의 구체 예외 — 저장된 전략이 [policy]로 무효할 때만 던진다(전략 없음과 다르다,
 * D-6F1-4). [violations]가 무엇이 무효인지 그대로 나른다 — 사유를 문자열로 뭉개지 않는다.
 */
class InvalidStoredStrategyException(
    val violations: List<StrategyViolation>,
) : IllegalStateException("저장된 전략이 현재 정책으로 무효하다: $violations")
