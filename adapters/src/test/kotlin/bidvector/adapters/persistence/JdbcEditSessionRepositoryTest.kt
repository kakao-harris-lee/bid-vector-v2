package bidvector.adapters.persistence

import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSessionConflictException
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionState
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.TransitionOutcome
import bidvector.workflow.strategy.toSnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

private val OPERATOR = EDIT_SESSION_TEST_OPERATOR
private val FIELD = EDIT_SESSION_TEST_FIELD

/**
 * S-30(scope.md) — 세션 왕복·낙관적 충돌(0행 → 실패)·상태 전이 보존·만료 시각 왕복. 공용
 * fixture 는 [EditSessionWorkflowTestSupport](v2-지침서.md §5 「파일 500줄 한도」로
 * [JdbcEditSessionSaveGuardTest](D-6B1-10 회귀 보호)와 갈렸다 — 설계 변경이 아니다).
 */
class JdbcEditSessionRepositoryTest : EditSessionWorkflowTestSupport() {
    @Test
    fun `begin 으로 만든 세션을 load 하면 원본과 같은 스냅숏이 나온다 — 왕복`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-round-trip")
        val begun = workflow(sessions).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)

        sessions.load(id) shouldBe begun.session.toSnapshot()
    }

    @Test
    fun `WaitingForConfirmation 전이(예산 한계 포함)가 저장 뒤 그대로 복원된다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-confirmation")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)

        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.7"), candidateLimit = 5)
        val provided =
            flow.provideValue(EditCommand.ProvideValue(CommandId("cmd-1"), id, Actor.Operator(OPERATOR), FIELD, draft))
        check(provided is CommandResult.Processed)

        sessions.load(id) shouldBe provided.outcome.session.toSnapshot()
        sessions.load(id)!!.stateKind shouldBe "WAITING_FOR_CONFIRMATION"
    }

    @Test
    fun `Applied 까지 전이한 세션이 revision 을 보존한 채 복원된다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-applied")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        flow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        val confirmed =
            flow.confirm(EditCommand.Confirm(CommandId("cmd-2"), id, Actor.Operator(OPERATOR), StrategyRevision(1)))
        check(confirmed is CommandResult.Processed)

        val loaded = sessions.load(id)
        loaded shouldBe confirmed.outcome.session.toSnapshot()
        loaded!!.stateKind shouldBe "APPLIED"
        loaded.stateRevision shouldBe 2
    }

    @Test
    fun `만료 시각을 넘긴 세션을 expire 하면 EXPIRED 로 저장되고 부가 데이터가 없다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-expired")
        val startClock = Clock { Instant.parse("2026-09-17T00:00:00Z") }
        workflow(sessions, clock = startClock, timeout = Duration.ofMinutes(1)).begin(id, OPERATOR, FIELD)

        val afterExpiry = Clock { Instant.parse("2026-09-17T00:10:00Z") }
        val state = workflow(sessions, clock = afterExpiry).expire(id)

        state shouldBe EditSessionState.Expired
        val loaded = sessions.load(id)
        loaded!!.stateKind shouldBe "EXPIRED"
        loaded.stateField shouldBe null
        loaded.stateDraft shouldBe null
        loaded.stateRevision shouldBe null
        loaded.stateCancelReasonKind shouldBe null
        loaded.expiresAt shouldBe Instant.parse("2026-09-17T00:01:00Z")
    }

    @Test
    fun `같은 세션 값을 두 번 save 하면 두 번째는 낙관적 충돌로 실패한다 — 0행`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-conflict")
        val begun = workflow(sessions).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)

        val flow = workflow(sessions)
        val provided =
            flow.provideValue(
                EditCommand.ProvideValue(
                    CommandId("cmd-1"),
                    id,
                    Actor.Operator(OPERATOR),
                    FIELD,
                    StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                ),
            )
        check(provided is CommandResult.Processed)
        val transitioned = provided.outcome.session

        // 이 시점에 DB 의 session_version 은 이미 transitioned.sessionVersion 과 같다(방금
        // workflow.provideValue 가 저장했다) — 같은 값을 다시 save 하면 전제조건
        // (저장소 버전 = 새 버전 - 1)이 거짓이라 0행 → 예외(동시 writer 가 같은 버전을
        // 다시 쓰려는 상황의 재현).
        val exception = shouldThrow<EditSessionConflictException> { sessions.save(transitioned) }
        exception.sessionId shouldBe id
        exception.expectedVersion shouldBe transitioned.sessionVersion - 1

        // 충돌 뒤에도 저장소의 값은 그대로다(덮이지 않았다 — 조용한 덮어쓰기가 아니다).
        sessions.load(id) shouldBe transitioned.toSnapshot()
    }

    @Test
    fun `같은 id 로 begin 이 경합하면(둘 다 session_version=0) 두 번째는 충돌한다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-begin-race")
        val begun = workflow(sessions).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)

        // 같은 begin() 을 몰아 만든 두 번째 세션(둘 다 sessionVersion=0)을 그대로 다시
        // save 한다 — 저장소의 state 는 아직 비종단(WaitingForValue)이라 "버전 0 이면
        // 무조건 통과" 규칙이 있었다면 조용히 덮였을 자리(D-6B1-3, 구현 레인 실측 수정).
        val exception = shouldThrow<EditSessionConflictException> { sessions.save(begun.session) }
        exception.sessionId shouldBe id
    }

    @Test
    fun `종단 상태 위에 새 begin 은 충돌 없이 새 episode 로 갈아 끼운다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-restart")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        flow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        flow.confirm(EditCommand.Confirm(CommandId("cmd-2"), id, Actor.Operator(OPERATOR), StrategyRevision(1)))
        sessions.load(id)!!.stateKind shouldBe "APPLIED"

        val restarted = workflow(sessions).begin(id, OPERATOR, EditableField.CandidateLimit)
        restarted.shouldBeInstanceOf<BeginOutcome.Started>()
        sessions.load(id)!!.stateKind shouldBe "WAITING_FOR_VALUE"
    }

    /**
     * verifier r1 HIGH-1 재현·회귀 보호 — `Sql.UPSERT_EDIT_SESSION`을 원시 값으로 직접 몬다
     * (adapters 는 `EditSession`을 만들 수 없어 두 "경쟁하는 writer"를 도메인 API 로는
     * 구성할 수 없다 — `EditStrategyWorkflow`는 load-then-save 가 한 호출 안에서 원자적이라
     * 같은 사전 상태를 두 번 읽는 경합을 재현하지 못한다. 저장소 write 경로 자체의 계약을
     * 재는 이 셋은 그래서 SQL 수준이 맞다).
     */
    private fun Connection.rawUpsertEditSession(
        id: String,
        state: String,
        sessionVersion: Int,
        statePayload: String? = "{}",
    ): Int =
        prepareStatement(Sql.UPSERT_EDIT_SESSION).use { statement ->
            var index = 1
            statement.setString(index++, id)
            statement.setString(index++, "op-raw")
            statement.setString(index++, state)
            statement.setString(index++, statePayload)
            statement.setTimestamp(index++, Timestamp.from(Instant.parse("2026-09-17T00:00:00Z")))
            statement.setInt(index++, sessionVersion)
            statement.setString(index, null)
            statement.executeQuery().use { rs -> if (rs.next()) 1 else 0 }
        }

    @Test
    fun `종단 상태 위의 다른 종단 전이는 버전이 같으면 충돌한다 — HIGH-1 잃어버린 갱신 재현`() {
        val id = "jdbc-terminal-race"
        appConnection().use { connection ->
            connection.rawUpsertEditSession(id, "WAITING_FOR_CONFIRMATION", sessionVersion = 1) shouldBe 1
            connection.commit()

            // writer A — 정상 순차 전이(저장소 1 -> 새 값 2), APPLIED 로 승격.
            connection.rawUpsertEditSession(id, "APPLIED", sessionVersion = 2) shouldBe 1
            connection.commit()

            // writer B — 같은 시작(v1, WaitingForConfirmation)을 읽었다고 가정하고 CANCELLED,
            // 같은 목표 버전(2)으로 경합한다. 저장소는 이미 종단(APPLIED)이라 HIGH-1 이전에는
            // "저장소가 종단이면 버전을 안 본다"는 첫 갈래가 무조건 통과시켰다(잃어버린 갱신).
            // 지금은 그 갈래가 EXCLUDED.session_version = 0 도 요구해 여기(2)서는 통과하지
            // 않고, 둘째 갈래(저장소 버전 2 = 새 값 2 - 1 = 1?)도 거짓이라 0행이어야 한다.
            connection.rawUpsertEditSession(id, "CANCELLED", sessionVersion = 2) shouldBe 0
            connection.commit()
        }
    }

    @Test
    fun `같은 종단 전이를 두 번 쓰면 두 번째는 충돌한다 — 이중 적용 방지`() {
        val id = "jdbc-double-apply"
        appConnection().use { connection ->
            connection.rawUpsertEditSession(id, "WAITING_FOR_CONFIRMATION", sessionVersion = 5) shouldBe 1
            connection.commit()

            connection.rawUpsertEditSession(id, "APPLIED", sessionVersion = 6) shouldBe 1
            connection.commit()

            // 같은 목표 버전(6)으로 APPLIED 를 또 쓴다 — 저장소가 이미 6 이라 둘째 갈래
            // (6 = 6-1?) 거짓, 첫 갈래도 새 값이 0 이 아니라 거짓 — 0행이어야 한다.
            connection.rawUpsertEditSession(id, "APPLIED", sessionVersion = 6) shouldBe 0
            connection.commit()
        }
    }

    /**
     * verifier r1 MEDIUM-3 시정 — 이 파일의 다른 test 는 전부 `dataSource()`(admin)로
     * `JdbcEditSessionRepository`를 만든다. admin 은 GRANT 와 무관하게 항상 성공하므로
     * `bidvector_app`의 실제 부여가 틀려도(예: UPDATE 를 뺀 뒤 axis9 기대 행렬만 맞춰 고침,
     * MUT-G) 이 파일의 다른 test 는 못 잡는다. 이 test 는 형제 파일(`RawAppendOnlyTest` 등)과
     * 같은 방식으로 `appConnection()`(`SET ROLE bidvector_app`)을 직접 써서, 저장 경로가
     * 실제로 요구하는 권한(INSERT+UPDATE — `ON CONFLICT DO UPDATE` 한 문이 둘 다 쓴다)이
     * 실효 부여와 맞는지, 그리고 부여 밖(DELETE)은 거부되는지를 앱 역할로 직접 잰다.
     */
    @Test
    fun `앱 역할 연결로 UPSERT 양 분기가 성공하고 DELETE 는 거부된다 — GRANT 실효 권한`() {
        val id = "jdbc-app-role-grant"
        appConnection().use { connection ->
            // INSERT 분기(신규 id, ON CONFLICT 미충돌).
            connection.rawUpsertEditSession(id, "WAITING_FOR_VALUE", sessionVersion = 0) shouldBe 1
            connection.commit()

            // UPDATE 분기(ON CONFLICT DO UPDATE) — INSERT 권한만으로는 이 분기가 못 돈다.
            connection.rawUpsertEditSession(id, "WAITING_FOR_VALUE", sessionVersion = 1) shouldBe 1
            connection.commit()

            shouldThrow<PSQLException> {
                connection.prepareStatement("DELETE FROM edit_session WHERE id = ?").use { statement ->
                    statement.setString(1, id)
                    statement.executeUpdate()
                }
            }
            connection.rollback()
        }
    }

    /**
     * verifier r1 HIGH-2 재현·회귀 보호 — `EditSessionRow.toSnapshot`(디코드 층)을 직접
     * 지나야 하므로 원시 SQL 로 행을 심는다. `EditSessionSnapshotTest`(workflow)의 24건은
     * 스냅숏을 Kotlin 에서 직접 만들어 넣어 이 디코드 층을 지나지 않는다 — 그래서 DB 행에서
     * 출발하는 이 셋이 필요하다.
     */
    private fun seedRawEditSessionRow(
        id: String,
        state: String,
        statePayload: String,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO edit_session (id, operator_id, state, state_payload, expires_at, session_version) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, "op-seed")
                    statement.setString(3, state)
                    statement.setString(4, statePayload)
                    statement.setTimestamp(5, Timestamp.from(Instant.parse("2026-09-17T00:00:00Z")))
                    statement.setInt(6, 1)
                    statement.executeUpdate()
                }
        }
    }

    @Test
    fun `state_payload 의 revision 이 숫자가 아니면 load 가 거부한다 — HIGH-2 재현`() {
        val id = "jdbc-bad-revision"
        seedRawEditSessionRow(id, "APPLIED", """{"revision":"not-a-number"}""")

        shouldThrow<IllegalArgumentException> { repository().load(EditSessionId(id)) }
    }

    @Test
    fun `draft 의 리스트 키가 없으면 load 가 거부한다 — HIGH-2 재현`() {
        val id = "jdbc-missing-list-key"
        seedRawEditSessionRow(id, "WAITING_FOR_CONFIRMATION", """{"field":{"kind":"CANDIDATE_LIMIT"},"draft":{}}""")

        shouldThrow<IllegalArgumentException> { repository().load(EditSessionId(id)) }
    }

    @Test
    fun `draft 의 리스트 키가 배열이 아니면 load 가 거부한다 — HIGH-2 재현`() {
        val id = "jdbc-list-type-mismatch"
        val draft =
            """{"focusCategories":"not-an-array","focusRegionTerms":[],"excludeRegionTerms":[],""" +
                """"requiredKeywordTerms":[],"excludeKeywordTerms":[]}"""
        seedRawEditSessionRow(id, "WAITING_FOR_CONFIRMATION", """{"field":{"kind":"CANDIDATE_LIMIT"},"draft":$draft}""")

        shouldThrow<IllegalArgumentException> { repository().load(EditSessionId(id)) }
    }

    /**
     * Codex 1라운드 HIGH 재현·회귀 보호 — `requireIntValue`/`requireLongValue`가 변환
     * "가능성"만 보고 정수 "표기"인지 안 봐서, 소수·지수 표기 JSON 숫자가 조용히 절삭돼
     * 통과했다(`1.2`→`1`, `1e2`→`100`, `:adapters` 실 컴파일 classpath 의 jackson-databind
     * 2.21.5 로 재현). DB 행에서 출발해 `EditSessionRow.toSnapshot`(디코드 층)을 직접
     * 지난다 — `EditSessionSnapshotTest`(workflow)는 이 층을 지나지 않는다(HIGH-2 재현
     * 셋과 같은 이유).
     */
    @Test
    fun `state_payload 의 revision 이 소수(1_2)면 load 가 거부한다 — Codex 1라운드 HIGH 재현`() {
        val id = "jdbc-fractional-revision"
        seedRawEditSessionRow(id, "APPLIED", """{"revision":1.2}""")

        shouldThrow<IllegalArgumentException> { repository().load(EditSessionId(id)) }
    }

    @Test
    fun `state_payload 의 revision 이 지수 표기(1e2)면 load 가 거부한다 — Codex 1라운드 HIGH 재현`() {
        val id = "jdbc-exponential-revision"
        seedRawEditSessionRow(id, "APPLIED", """{"revision":1e2}""")

        shouldThrow<IllegalArgumentException> { repository().load(EditSessionId(id)) }
    }

    /** `requireLongValue`(`MoneySnapshot.won`) 쪽도 같은 결함이었다 — Int 축과 대칭으로 잠근다. */
    @Test
    fun `draft 의 minBudget won 이 소수면 load 가 거부한다 — Codex 1라운드 HIGH 재현(Long 축)`() {
        val id = "jdbc-fractional-won"
        val minBudget =
            """{"won":1.2,"currency":"KRW","vatTreatment":"INCLUSIVE","provenanceKind":"OPERATOR_DECLARED"}"""
        val draft =
            """{"focusCategories":[],"focusRegionTerms":[],"excludeRegionTerms":[],""" +
                """"requiredKeywordTerms":[],"excludeKeywordTerms":[],"minBudget":$minBudget}"""
        seedRawEditSessionRow(id, "WAITING_FOR_CONFIRMATION", """{"field":{"kind":"CANDIDATE_LIMIT"},"draft":$draft}""")

        shouldThrow<IllegalArgumentException> { repository().load(EditSessionId(id)) }
    }

    /**
     * 과잉 거부 방지(팀장 요청) — 정수 표기의 정상 `won`은 여전히 통과하고 값도 보존된다.
     * `revision`(Int) 축의 정상 통과는 기존 왕복 test 들(`Applied 까지 전이…` 등)이 이미
     * 잠근다 — 이 test 는 그 test 들이 안 덮는 `won`(Long, `readMoney`)을 DB 행에서
     * 출발해 잠근다.
     */
    @Test
    fun `draft 의 minBudget won 이 정수면 load 가 그대로 복원한다 — 과잉 거부 방지`() {
        val id = "jdbc-integral-won"
        val minBudget =
            """{"won":1200000,"currency":"KRW","vatTreatment":"INCLUSIVE","provenanceKind":"OPERATOR_DECLARED"}"""
        val draft =
            """{"focusCategories":[],"focusRegionTerms":[],"excludeRegionTerms":[],""" +
                """"requiredKeywordTerms":[],"excludeKeywordTerms":[],"minBudget":$minBudget}"""
        seedRawEditSessionRow(id, "WAITING_FOR_CONFIRMATION", """{"field":{"kind":"CANDIDATE_LIMIT"},"draft":$draft}""")

        val loaded = repository().load(EditSessionId(id))

        loaded!!.stateDraft!!.minBudget!!.won shouldBe 1_200_000L
    }

    /**
     * verifier r7 HIGH-5 재현·회귀 보호 — 인코더는 정확한 십진 노드로 쓰지만, 디코더의
     * 기본 `ObjectMapper`는 부동소수 토큰을 `DoubleNode`로 읽어 `.asText()`가 **그 double
     * 의 최단 표기**를 돌려준다(척도·유효숫자가 예외·거부 없이 바뀐다 — HIGH-2/Codex 1
     * 라운드가 막은 "값이 다른 값으로" 축과 같은 계열이지만 숫자가 아니라 **표기**가
     * 갈리는 자리). `0.70`(정확 왕복 기대)이 `0.7`로 오는 것과 double 정밀도를 넘는
     * 고정밀 값의 끝자리가 바뀌는 것, 둘 다 DB 행에서 출발해 직접 확인한다 — 기존 왕복
     * test 의 소수 리터럴(`0.7`)은 우연히 double 최단 표기와 같아 이 결함을 못 봤다.
     */
    @Test
    fun `state_payload 의 소수가 척도(0_70)를 유지한 채 복원된다 — HIGH-5 재현`() {
        val id = "jdbc-decimal-scale"
        val draft =
            """{"focusCategories":[],"focusRegionTerms":[],"excludeRegionTerms":[],""" +
                """"requiredKeywordTerms":[],"excludeKeywordTerms":[],"bidNowThreshold":0.70}"""
        seedRawEditSessionRow(id, "WAITING_FOR_CONFIRMATION", """{"field":{"kind":"CANDIDATE_LIMIT"},"draft":$draft}""")

        val loaded = repository().load(EditSessionId(id))

        loaded!!.stateDraft!!.bidNowThreshold shouldBe BigDecimal("0.70")
    }

    @Test
    fun `state_payload 의 소수가 double 정밀도를 넘는 자릿수도 그대로 복원된다 — HIGH-5 재현`() {
        val id = "jdbc-decimal-high-precision"
        val precise = "0.123456789012345678901"
        val draft =
            """{"focusCategories":[],"focusRegionTerms":[],"excludeRegionTerms":[],""" +
                """"requiredKeywordTerms":[],"excludeKeywordTerms":[],"minimumMatchScore":$precise}"""
        seedRawEditSessionRow(id, "WAITING_FOR_CONFIRMATION", """{"field":{"kind":"CANDIDATE_LIMIT"},"draft":$draft}""")

        val loaded = repository().load(EditSessionId(id))

        loaded!!.stateDraft!!.minimumMatchScore shouldBe BigDecimal(precise)
    }

    /**
     * verifier r7 HIGH-5 — 실제로 깨지는 계약(왕복 단언만으로는 안 보인다). `0.70`으로
     * `provideValue`를 두 번(같은 command) 보내면, 척도가 온전히 왕복하지 않는 한 두
     * 번째 호출에서 `session.lastCommand`(DB 재로드·디코드된 값, 척도 1)와 재전달된
     * `command`(원본 척도 2)가 데이터 클래스 동등성에서 갈려 `IdempotencyConflict`로
     * 거부된다 — 계약은 「재전달 효과 0 의 `Accepted`」(HIGH-3 와 같은 계약, 다른 기제).
     */
    @Test
    fun `척도 있는 소수(0_70)로 같은 command 를 재전달하면 효과 0 의 Accepted 다 — HIGH-5 계약 회귀`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-decimal-scale-idempotent")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        val command =
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.70")),
            )
        val first = flow.provideValue(command)
        check(first is CommandResult.Processed)

        val redelivered = flow.provideValue(command)

        redelivered.shouldBeInstanceOf<CommandResult.Processed>()
        redelivered.outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
    }
}
