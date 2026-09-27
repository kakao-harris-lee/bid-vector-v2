package bidvector.adapters.strategy

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.OwnTransactionConnectionSource
import bidvector.adapters.persistence.Sql
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
 *
 * **D-6F1-7(계약 갱신 (2), 6B-1 실측 인계) — 패키지는 `bidvector.adapters.strategy`다.**
 * `bidvector.adapters.persistence`에는 두지 않는다 — 그 패키지의
 * `PersistenceAdapterDependencyTest`가 `strategy`·`workflow` 참조를 금지하고(3D 원안),
 * 이 클래스는 [bidvector.workflow.strategy.StrategyRepository]와 [OperatorStrategy]를
 * 둘 다 참조해야 해 같은 벽을 만난다. SQL 문(`Sql.UPSERT_STRATEGY` 등)은 그대로
 * `adapters.persistence`에 남고(전략 SQL 추가만, in_scope), 이 패키지는 그것을
 * `adapters.persistence`가 이미 `internal`(모듈 범위)로 공개한 것만 재사용한다
 * (`StrategyAdapterDependencyTest`가 그 경계를 잰다).
 */
class JdbcStrategyRepository(
    private val connections: ConnectionSource,
    private val policy: Resolution.Resolved<StrategyPolicyData>,
) : StrategyRepository {
    /**
     * M6/6A-2b D-6A2b-3 — 옛 형태(이 어댑터가 커넥션과 커밋을 스스로 쥔다)를 그대로 남긴다.
     * [OwnTransactionConnectionSource] 가 「호출 하나 = 트랜잭션 하나」를 지므로 [save] 의
     * 두 문이 여전히 한 커밋에 든다. 편집 경로는 이 생성자가 아니라 [ConnectionSource] 를
     * 받는 위 생성자로 서서 **호출부의** 트랜잭션(전략+outbox+세션)에 참여한다.
     */
    constructor(dataSource: DataSource, policy: Resolution.Resolved<StrategyPolicyData>) :
        this(OwnTransactionConnectionSource(dataSource), policy)

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
        val row = connections.withConnection { it.querySingletonStrategyRow() }
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
     * 두지 않는다 — 실패는 그대로 전파되고 롤백은 커넥션의 주인이 진다.
     *
     * **M6/6A-2b D-6A2b-3 — 커밋 주체는 이 클래스가 아니라 [connections] 다.** 편집
     * 경로에서는 [bidvector.adapters.persistence.TransactionBoundary] 가 주인이라 이
     * 두 문이 outbox 등록·세션 전진과 **같은 커밋**에 든다(4A 잔여 창 폐쇄). 옛
     * `DataSource` 생성자에서는 [OwnTransactionConnectionSource] 가 주인이라 거동이
     * 이전과 같다.
     */
    override fun save(applied: AppliedStrategy) {
        val row = applied.strategy.toRow()
        connections.withConnection { connection ->
            connection.prepareStatement(Sql.UPSERT_STRATEGY).use { statement ->
                statement.bindStrategyRow(1, row)
                statement.executeUpdate()
            }
            connection.prepareStatement(Sql.INSERT_STRATEGY_REVISION).use { statement ->
                statement.bindStrategyRow(1, row)
                statement.executeUpdate()
            }
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
