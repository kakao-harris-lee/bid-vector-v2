# M6/6A-3+6F-3 — 평가 endpoint(dry-run) + 여력 상한 + 포트 아홉 조립 (2026-09-23)

`EvaluateCandidatesUseCase` 를 **처음으로 production 에서 돌게** 한다 — 배선 순서의 **3번과 4번을 한 slice** 로
(milestone-6 「M6 잔여 해소와 배선」 + 운영자 결정 넷 2026-09-23). 6F-1~6F-7 이 낸 포트 구현 여덟과
`UnavailableMlAnalysis` 를 app 에 꽂고, `CapacityPort` 의 첫 production 구현(요청 스코프)과 그것이 요구하는
**여력 상한 전략 필드**(V16)를 내며, **dry-run 전용** 평가 endpoint 하나를 연다.

- base: 이 브랜치가 분기해 나온 **현재** `main` — `git merge-base HEAD origin/main`(고정 SHA 아님, D-6F5-30).
  착수 실측값 `febad567`(PR #43 6F-4-w 머지 커밋). 라운드마다 재산출.
- 선행: 6F-1·6F-2·6F-4-w·6F-5-a·6F-6·6F-7·6A-1 병합 완료(전부 `main`).
- worktree `bid-vector-v2-m6-6a3f3`, 브랜치 `m6-6a3f3/2026-09-23`.

## 운영자 결정 넷 (2026-09-23, 선택지 + 추천, 전부 추천안 채택)

1. **`MlAnalysisPort` 처분 — 자리지킴 유지.** `UnavailableMlAnalysis` 를 배선한다. ML 단계에 도달한 후보는 전부
   `Review(MlUnavailable(ScoreNotProvided))` 다. 실 배선(`OpportunityAnalysis` + 포트 여섯 + 서빙 배포 + 외부 호출
   승인)은 **`OPEN-ML-ANALYSIS-WIRING`** 신설로 별도 slice.
2. **endpoint 는 dry-run 전용.** 알림 요청을 outbox 에 쓰지 않는다 — 응답에 「낳았을 알림 요청」의 공고 ID 만 싣는다.
   D-6A1-4(「실행 경로는 dry-run 강제로 시작한다」) 문면 그대로. 커밋 경로는 발송 채널(`OPEN-STR-12`)과 함께
   **`OPEN-6A3-EVALUATION-COMMIT`** 으로 신설.
3. **포트 아홉의 Spring 배선(`OPEN-6F-ASSEMBLY`)을 이 slice 가 흡수한다.** endpoint 가 조립의 뿌리라 배선 없이는 돌지
   않는다. 6F-7 이 조립에 넘긴 둘(D-6F7-11 트랜잭션 원자성 · 하위 소비자의 `Failed` 로깅)은 **outbox 커밋 경로의
   물음**이라 결정 2 에 따라 `OPEN-6A3-EVALUATION-COMMIT` 이 받는다.
4. **Codex 심판 없음.** V16 은 두 표에 nullable INT 한 열을 더하는 최소 변경 — `migration-reviewer` + `verifier` 로
   닫는다.

## 착수 조사가 확정한 것

- **여력 상한 필드가 없다.** 결정 ③(2026-09-18)은 「상한은 전략 표에 영속·감사되는 자리」인데 V9 두 표
  (`operator_strategy`·`operator_strategy_revision`)와 `OperatorStrategy`·`StrategyDraft` 에 활성 투찰 상한이 없다
  (`candidate_limit` 은 후보 상한, 다른 축). → **V16 신설**(D-6A3-4).
- `CapacityPort.snapshot()` 은 인자가 없고 `evaluate()` 진입에서 정확히 한 번 불린다. 그래서 「현재 활성 수는 요청이
  싣는다」(결정 ③)는 **요청마다 어댑터를 짓는다**는 뜻이다 — 요청 스코프 어댑터(D-6A3-5).
- 포트 아홉 중 production 구현: `JdbcStrategyRepository`(dataSource, 정책) · `JdbcCandidateSource`(dataSource, clock,
  **cap 기본값 없음** — `OPEN-6F2-CANDIDATE-BOUND` 가 조립 축에 넘긴 값) · `NoticeWatchSubjectPort`() ·
  `StoredRequirementLicenseGate`(`JdbcRequirementStore`, `OperatorProfilePort`, 정책) · `UnavailableMlAnalysis`() ·
  `UuidCorrelationIdFactory`() · `SystemClock`() · `OutboxNotificationRequestPort`(**dry-run 에서는 꽂지 않는다**) ·
  `CapacityPort` **없음**(이 slice). `JdbcOperatorProfileRepository`(dataSource)는 면허 게이트의 의존.
- `OpenApiContractTest` D-6A1-38 은 응답 속성을 **스칼라·스칼라 배열만** 허용한다(object 배열·`$ref`·`oneOf` 전부
  거부, 런타임 재귀). 후보별 결과를 object 배열로 실으면 **게이트가 붉다.** → D-6A3-6.
- `ErrorMapping` 은 도메인 실패 → HTTP 의 유일한 매핑표(D-6A1-7)이고 예외 메시지를 응답에 싣지 않는다. 새 실패
  둘(상한 미설정 · 후보 cap 초과)은 **그 표에 행을 더한다** — 다른 자리에 매핑을 만들지 않는다.
- app 의 HTTP test 기반: `HttpIntegrationTestBase`(`TestRestTemplate` 직접 생성) · `ProductionAssemblyAuthAuditTest`
  (Testcontainers PostgreSQL, production 조립을 실제로 부팅). 이 장비에 docker 있음(verifier 실측).

## 계약 고정 결정

**D-6A3-1 — 이름·범위.** slice ID `6A-3+6F-3`, evidence `reports/evidence/m6/6a3f3/`, 결정 ID `D-6A3-n`. 산출물은
넷: ⓐ V16 + 전략 도메인·저장소·응답의 `maxActiveBids` ⓑ 요청 스코프 어댑터 둘(`CapacityPort`·dry-run
`NotificationRequestPort`) ⓒ 포트 아홉의 Spring 배선(`EvaluationWiring`) ⓓ `POST /api/evaluation-dry-runs` + OpenAPI.

**D-6A3-2 — `MlAnalysisPort` 는 `UnavailableMlAnalysis` 고정(운영자 결정 1).** 배선이 `OpportunityAnalysis`·
`GrpcBidPredictionGateway`·`GrpcEmbeddingGateway` 를 **참조하지 않는다** — 의존 게이트로 잠근다(우회 6).

**D-6A3-3 — dry-run 은 타입으로 닫는다(운영자 결정 2).** dry-run 조립이 꽂는 `NotificationRequestPort` 는 요청 스코프
`RecordingNotificationRequestPort`(`adapters.evaluation`, 요청을 메모리에 모으고 `Requested` 를 낸다) **하나**다.
`OutboxNotificationRequestPort` 는 이 slice 의 app 배선 어디에도 나타나지 않는다 — **허용 목록 게이트**: app 의
production 클래스가 참조하는 `NotificationRequestPort` 구현 집합 == {`RecordingNotificationRequestPort`}(집합 등식,
바이트코드 층). 거동 쪽: Testcontainers E2E 에서 **dry-run 뒤 outbox 행 수가 변하지 않는다**(존재 단언이 아니라
전후 등식).

**D-6A3-4 — 여력 상한은 전략 필드 `maxActiveBids` 다(결정 ③ 이행).**
- V16: 두 표에 `max_active_bids INT CHECK (max_active_bids IS NULL OR max_active_bids > 0)` — nullable(기존 행 보존,
  DEFAULT 없음 — D-6F1-5 「값 지어내기 없음」). 되돌림은 파일 삭제가 아니라 **새 V 파일의 `DROP COLUMN`**(6F-4 규율).
- 도메인: `StrategyDraft.maxActiveBids: Int?` → `validate()` 가 `MaxActiveBids(value > 0)` 로 올린다(위반 코드
  `MaxActiveBidsNotPositive` 신설, `CandidateLimitNotPositive` 와 같은 형태). `OperatorStrategy.maxActiveBids: MaxActiveBids?`.
  `OperatorStrategy` 는 `strategy` 밖에서 만들지 않는다(D-6F1-2 불변식 유지).
- 저장소: `StrategyRow`·`Sql`(INSERT 둘·SELECT)·`EditSessionRow` draft codec(6B-1 세션 스냅샷도 같은 draft 를 나른다 —
  **빠뜨리면 세션 왕복에서 값이 조용히 사라진다**, 왕복 test 로 잠근다).
- 응답: `StrategyReadResponse.maxActiveBids: Int?` + OpenAPI `maxActiveBids`(integer, nullable).
- **상한 미설정은 fail-closed 다.** 전략에 `maxActiveBids` 가 없으면 dry-run 은 **409 `MAX_ACTIVE_BIDS_NOT_CONFIGURED`** 로
  거부한다 — 결정 ③ 이 「상한만 두고 현재값 0 고정」을 기각한 이유(용량 게이트가 꺼진 채 초록)와 대칭이다.
  현재값을 0 으로도 상한을 무한으로도 지어내지 않는다.

**D-6A3-5 — `CapacityPort` 는 요청 스코프 `RequestCapacityPort(currentActiveBids, strategy)` 다.**
`currentActiveBids` 는 요청 본문이 싣고(`>= 0`, 아니면 400 `INVALID_REQUEST`), 상한은 **같은 요청에서 한 번 읽은
전략**에서 온다. **전략은 요청당 정확히 한 번 읽는다** — use case 의 `strategies.load()` 와 여력 어댑터가 **같은 객체**를
본다(요청 스코프에서 한 번 적재한 전략을 둘에게 주는 형태. `StrategyRepository` 를 두 번 부르면 개정 사이에 갈릴 수
있다 — 4B-1 형태 ⑧ 「두 시점에 다르게 세어진다」의 재발이다). 계수 test 로 잠근다(`load` 호출 1회).

**D-6A3-6 — 응답은 평탄하다: 결과별 공고 ID 배열 + 건수. 후보별 사유 상세는 OPEN.**
D-6A1-38 게이트를 **넓히지 않는다**(세 라운드로 닫힌 게이트다). 응답 `EvaluationDryRunResponse`:
`strategyRevision: int` · `candidateCount: int` · `currentActiveBids: int` · `maxActiveBids: int` ·
`bidNowNoticeIds: [string]` · `reviewNoticeIds: [string]` · `skipNoticeIds: [string]` · `notReachedNoticeIds: [string]` ·
`wouldNotifyNoticeIds: [string]`(recording port 가 모은 요청의 공고 ID). **불변식 test**: `wouldNotifyNoticeIds` 집합 ==
`bidNowNoticeIds` 집합(사다리와 알림 요청 경로가 같은 판정을 본다), 네 배열의 길이 합 == `candidateCount`, 네 배열은
서로소. 후보별 사유(`EvaluationStage`·`EvaluationDropReason`·`Verdict.reasons`)는 **`OPEN-6A3-EVALUATION-DETAIL`** —
게이트를 깊이 1 object 배열까지 허용하도록 넓히는 것은 contract-keeper 결정이고, 지금 dry-run 의 물음(「무엇이
알림되나」)은 ID 집합으로 답해진다. 공고 ID 만 싣는다 — 공고명·기관명 원문은 응답에 실리지 않는다(privacy).

**D-6A3-7 — 후보 cap 은 배선 설정값이고 초과는 409 다(`OPEN-6F2-CANDIDATE-BOUND` 닫음).** `JdbcCandidateSource.cap` 은
설정 속성 `bidvector.evaluation.candidate-cap`(**기본값 없음**, 없으면 기동 실패 — 6A-1 자격증명·DataSource 속성과
같은 규율)으로 준다. `CandidateCapExceededException` → **409 `CANDIDATE_CAP_EXCEEDED`**(`ErrorMapping` 행 추가) —
조용히 자르지 않는다(D-6F2-4)의 HTTP 쪽 절반.

**D-6A3-8 — 배선은 `app.wiring.EvaluationWiring` 하나에 모은다.** 싱글턴 빈: `JdbcCandidateSource`·`NoticeWatchSubjectPort`·
`JdbcRequirementStore`·`JdbcOperatorProfileRepository`·`StoredRequirementLicenseGate`(`LICENSE_QUALIFICATION_POLICY`
해소, 실패 시 기동 실패 — `PersistenceWiring` 의 전략 정책과 같은 형태)·`UnavailableMlAnalysis`·
`UuidCorrelationIdFactory`·`SystemClock`. 요청 스코프: 위 둘. **컨트롤러는 use case 만 부른다**(6A 완료 조건) —
요청마다 use case 를 짓는 `EvaluationDryRunFactory`(app.wiring)가 있고 컨트롤러는 `factory.forRequest(현재값).evaluate()`
만 한다. `analysisBudget` 은 `strategy.candidateLimit?.value`(후보 상한 — 결정 ③ 표의 「후보 상한」 필드가 바로 이
자리다. 없으면 무제한 `null`, 기존 use case 기본값 그대로).

**D-6A3-9 — `OPEN-6F4W-ASSEMBLE-CALLER` 를 이 slice 가 닫는다.** app ArchUnit 층에 「`bidvector.strategy.TextKt.assemble*`
를 호출하는 production 클래스 집합 == {`bidvector.adapters.evaluation.NoticeWatchSubjectPortKt`}」 집합 등식 규칙을
더한다. 허용 집합은 게이트 안이 아니라 `config/quality/architecture-policy.properties` 에 둔다(「검사 대상 목록을
계약 파일에서」). 배선이 호출자를 더하는 slice 가 바로 이 slice 라 **여기서 서야** 한다.

**D-6A3-10 — audit·인증은 기존 필터가 덮는다.** 새 경로는 `OperatorCredentialFilter`·`RequestAuditFilter` 를 그대로
지난다(D-6A1-21 전수 test 가 새 매핑을 자동으로 센다 — **그 test 가 실제로 새 경로를 세는지 실측**). 요청 본문
(`currentActiveBids` 정수 하나)은 audit 에 담기지 않는다(D-6A1-7).

**D-6A3-11 — OpenAPI 는 수작성 단일 출처(D-6A1-8) 그대로.** 새 path·요청 스키마·응답 스키마·409/400 응답을 손으로
적고 `OpenApiContractTest` 가 대조한다. `info.version` 을 `6a3f3` 로 올린다.

## 계약 갱신 (1) — 0단계 측정의 결정 둘 (2026-09-23, 팀장)

**D-6A3-12 — `StrategyDraftSnapshot`(workflow)에 `maxActiveBids` 를 더한다. `in_scope` 를 한 파일군 넓힌다.**
0단계 실측: `StrategyDraft(` 호출 39건이 전부 named-arg 라 도메인 필드 추가는 컴파일 파괴 0건이지만, 6B-1 세션 영속의
port 형 스냅샷 `StrategyDraftSnapshot`(`workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionSnapshot.kt`)은
`out_of_scope: workflow/src/main/**` 안이라 구현 레인이 멈췄다(옳다). 구현 레인의 완화 논거 「6A-2 가 없어 그 필드를 채울
명령이 없다 → 관측 가능한 손실 0」은 **반대 방향을 놓친다**: 상한이 **있는** 전략에서 시작한 세션이 `JdbcEditSessionRepository`
로 영속·복원된 뒤 확정되면 스냅샷이 안 나른 `maxActiveBids` 가 **`null` 로 저장돼 상한이 조용히 지워진다** — endpoint 는
없어도 그 경로는 production 코드에 이미 있다(6B-1 왕복 안정성 계약의 새 필드 구멍). **값이 조용히 사라지는 경로를
만들지 않는다.**
- 넓히는 경로: `workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionSnapshot.kt` + 스냅샷↔도메인 draft 변환이
  사는 같은 패키지 파일(있으면, 구현 레인이 실측해 이름을 checklist 에 적는다). **조건**: nullable 필드 하나의 additive
  변경만 — 포트 시그니처·use case·`EditSession` 상태기계 무변경. 편집 **명령**(field kind)은 더하지 않는다
  (`OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`, 6A-2).
- 잠그는 test 둘: ① 세션 스냅샷 codec 왕복 등식(필드 누락 시 RED) ② **상한 있는 전략 → 세션 시작 → 영속·복원 → 확정 →
  저장된 전략의 `maxActiveBids` 가 보존된다**(workflow 또는 adapters test 중 그 경로가 이미 있는 자리에 케이스 추가).
- rollback 목록·`in_scope` 를 재산출한다(세 번째 「in_scope 눈멂」 금지).

**D-6A3-13 — acceptance 의 `container` job 서술 정정.** 착수 계약이 「container job(앱 이미지 빌드)」로 적었으나 실제
CI `container` job 은 **ml-serving 이미지 + compose + `RealServerIntegrationTest`** 이고 app 전용 Dockerfile 은 저장소에
없다(`docker/ml-serving.Dockerfile` 뿐 — 앱 이미지는 6A-2 소관). acceptance 는 CI 원문 그대로이므로 실행 대상은 바뀌지
않는다 — 문면만 정정하고, 「이 slice 의 배선 변경이 부팅에 닿는다」는 확인은 `ProductionAssemblyAuthAuditTest` 계열
(production 조립 실제 부팅, `check` job 안)이 진다.

**0단계 결정 채택**: 요청당 한 번 읽기는 **(a) 요청 스코프 데코레이터** `PinnedStrategyRepository(loaded, delegate)`
(`load` 는 적재값, `save` 위임) — use case 시그니처 무변경. `RequestCapacityPort(currentActiveBids: Int, maxActiveBids: Int)`.
설정은 `@ConfigurationProperties`(기본값 없음, `PersistenceProperties` 형) — `app/src/main/resources` 신설 없음.

## 위협 모델 — 6A-3+6F-3 고유 경계

지키는 것: **① dry-run endpoint 는 외부 effect 를 만들지 않는다**(outbox 에 행이 생기지 않는다, 발송 없음)
**② 용량 게이트는 꺼진 채 돌지 않는다**(상한 미설정·음수 현재값은 거부) **③ 판정 경로는 use case 하나**(컨트롤러·
배선이 판정을 복제하거나 우회하지 않는다) **④ 응답에 원문 텍스트가 실리지 않는다**(공고 ID 만).

경계 밖: 알림 **발송**과 outbox 커밋(`OPEN-6A3-EVALUATION-COMMIT`·`OPEN-STR-12`) · 실 ML(`OPEN-ML-ANALYSIS-WIRING`) ·
`currentActiveBids` 값의 정직성(호출자가 싣는다 — 결정 ③) · 후보별 사유 상세(`OPEN-6A3-EVALUATION-DETAIL`) ·
RBAC(`OPEN-6A-RBAC`) · 커넥션 풀(`OPEN-6A1-CONNECTION-POOL`).

### 우회 — 여덟

1. **dry-run 조립에 `OutboxNotificationRequestPort` 를 꽂는다.** ← D-6A3-3 허용 목록 게이트(집합 등식) + E2E 전후
   outbox 행 수 등식.
2. **`RecordingNotificationRequestPort` 가 어딘가 영속한다.** ← 의존 게이트: `adapters.evaluation` 의 그 클래스 상수 풀에
   JDBC·persistence 루트 참조 0(기존 `EvaluationAdapterDependencyTest` 덮개 안, 허용 루트 유지).
3. **상한 없이 돌린다**(max=null → 0 이나 무한으로 지어냄). ← D-6A3-4 fail-closed 409 + test(상한 없는 전략 저장 뒤
   호출 → 409, outbox·audit 외 부수 효과 0).
4. **전략을 두 번 읽어 상한과 판정이 다른 개정을 본다.** ← D-6A3-5 계수 test(`load` 1회).
5. **컨트롤러가 use case 를 우회해 포트를 직접 부른다**(예: 후보 목록만 읽어 응답). ← app 의존 게이트: `app.http` 가
   `workflow.evaluation` 의 **use case·결과 타입만** 참조하고 port 타입을 참조하지 않는다(허용 목록).
6. **배선이 실 ML gateway 를 끌어온다.** ← D-6A3-2 의존 게이트(`app.wiring` 이 `adapters.ml` 에서 참조하는 클래스 집합
   == {`UnavailableMlAnalysis`}).
7. **응답 DTO 에 공고명·기관명을 싣는다.** ← OpenAPI 스키마 대조(키 집합 등식) + privacy-gate 판정.
8. **새 endpoint 가 인증·audit 필터를 비껴간다.** ← D-6A1-21 전수 test 가 새 매핑을 세는지 실측 + 401 test.

### (2b) 값 획득 축 — 새 public 표면 전수 (구현 레인이 표로 낸다, 라운드마다 갱신)

예상 행: `EvaluationDryRunController`·`EvaluationDryRunRequest`·`EvaluationDryRunResponse`(닫는다 — use case 결과를
옮길 뿐 새 계산값 없음) · `EvaluationDryRunFactory`(경계로 처리 — 요청 스코프 use case 생성, **`currentActiveBids` 외
어떤 인자도 밖에서 받지 않는다**) · `RequestCapacityPort`·`RecordingNotificationRequestPort`(닫는다 — 생성자 인자가 값의
전부, 실측) · `MaxActiveBids`(닫는다 — `validate()` 경유) · `EvaluationWiring` 빈 메서드(경계로 처리 — Spring 만 부른다,
`open fun` 이라 test 가 부를 수 있음을 실측으로 적는다) · ArchUnit 규칙·정책 키(게이트). **`object`/companion 주입
자리**: 없음 예상 — 있으면 행으로.

### 설계 검토 (Phase 2.5, 세션 모델)

(0) 경계 문장은 위 위협 모델. (1) 게이트 술어는 전부 **구조**(바이트코드 허용 목록·집합 등식·ArchUnit·OpenAPI 키
집합 등식·계수 test)이고 문자열 grep 이 없다. (2) 우회 여덟 각각 폐쇄 장치가 있다. (2b) 표는 구현 레인이 실측으로
채운다. (3) 과잉·미달: **미달 후보** — 후보별 사유가 응답에 없다(OPEN 으로 명시, 미달이 아니라 결정) · `maxActiveBids`
편집 endpoint 가 없다(6A-2 세션 명령 endpoint 소관 — 이 slice 는 저장된 값을 읽기만 하고, 값은 마이그레이션 뒤
운영자가 DB 로 넣거나 6A-2 가 편집 경로를 연다. **알려진 제한**으로 등재). **과잉 후보** — 없음(요청 스코프 어댑터
둘은 결정 ③·2 의 최소 이행).

## in_scope

```yaml
in_scope:
  - adapters/src/main/resources/db/migration/V16__operator_strategy_max_active_bids.sql   # D-6A3-4
  - strategy/src/main/kotlin/bidvector/strategy/StrategyTypes.kt                          # maxActiveBids
  - strategy/src/main/kotlin/bidvector/strategy/StrategyValidation.kt                     # draft 필드 + 위반 코드
  - strategy/src/main/kotlin/bidvector/strategy/ActionThresholds.kt                       # MaxActiveBids 타입(위치는 구현 레인 판단, 같은 파일군)
  - strategy/src/test/kotlin/bidvector/strategy/**
  - adapters/src/main/kotlin/bidvector/adapters/strategy/**                               # StrategyRow·Sql·EditSessionRow·JdbcStrategyRepository
  - adapters/src/test/kotlin/bidvector/adapters/strategy/**
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/**                             # RequestCapacityPort·RecordingNotificationRequestPort
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/**
  - app/src/main/kotlin/bidvector/app/http/**                                             # 컨트롤러·DTO·ErrorMapping 행
  - app/src/main/kotlin/bidvector/app/wiring/**                                           # EvaluationWiring·Factory
  - app/src/main/kotlin/bidvector/app/BidVectorApplication.kt                             # 필요 시(속성 바인딩)
  - app/src/main/resources/**                                                             # 설정 속성 선언이 있으면
  - app/src/test/kotlin/bidvector/app/**                                                  # http·wiring·architecture(D-6A3-9)·conformance(draft 필드)
  - openapi/bidvector-operator-api.yaml                                                   # D-6A3-11
  - config/quality/gate-tests.properties                                                  # 신설 게이트 등재(추가만) — 공유 파일
  - config/quality/architecture-policy.properties                                         # D-6A3-9 허용 집합 키(추가만) — 공유 파일
  - fixtures/**                                                                           # 전략 편집 corpus 에 필드가 필요할 때만, 근거 기록
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionSnapshot.kt   # D-6A3-12 — nullable 필드 하나 additive
  - workflow/src/test/kotlin/bidvector/workflow/strategy/**                        # D-6A3-12 test ②
  - reports/evidence/m6/6a3f3/**
  - milestone-6.md                                                                        # 착수·종결 문단(팀장) — 공유 파일
out_of_scope:
  - workflow/src/main/**                          # use case·포트 무변경 — 예외는 아래 한 파일군(D-6A3-12)
  - adapters/src/main/kotlin/bidvector/adapters/ml/**   # UnavailableMlAnalysis 그대로 (결정 1)
  - ml-engine/**
  - 세션 편집 endpoint                            # 6A-2
  - outbox 커밋 경로·발송                          # OPEN-6A3-EVALUATION-COMMIT · OPEN-STR-12
```

**계약 밖을 만져야 하면 멈추고 보고한다**(6F-4-w 계약 갱신 (2) 의 규율 — 세 번째 「in_scope 눈멂」을 만들지 않는다).

## acceptance

CI 워크플로 job 명령 그대로(`.github/workflows/ci.yml`) — 구현 레인이 착수 시 원문을 대조해 `commands.md` 에 옮긴다.
Kotlin `check` job 전체 + `container` job(ml-serving 이미지·compose·`RealServerIntegrationTest` — D-6A3-13 정정) + `one-command-check.sh`.
- **버릴 clone 에서**, **캐시 우회 한 번**(`--rerun-tasks`).
- Testcontainers E2E(production 조립 부팅): ① 빈 DB → `POST` 200, 네 배열 빈, `candidateCount` 0 ② 상한 없는 전략 →
  409 ③ 음수 현재값 → 400 ④ 인증 없음 → 401 ⑤ **표본 공고 1건 이상 적재 → 판정 분포가 결정적**(`UnavailableMlAnalysis`
  라 ML 도달 후보는 `review`) ⑥ 호출 전후 outbox 행 수 등식 ⑦ `wouldNotifyNoticeIds == bidNowNoticeIds`.
- 변이 실측(구현 보고에 명령·exit): dry-run 조립에 outbox 포트를 꽂는 변이 RED · `load` 두 번 변이 RED · 상한 미설정
  409 를 200 으로 바꾸는 변이 RED · `assemble*` 새 호출자 변이(app 에 한 줄) RED · OpenAPI 에 필드 하나 빼기 RED.

## rollback

`in_scope` 경로 한정 `git restore --source=<base> --staged --worktree --`, `<base>` = `git merge-base HEAD origin/main`.
목록은 `git diff --name-status <base>..HEAD` 기계 산출, 라운드마다 재산출. 공유 파일 셋(`gate-tests.properties`·
`architecture-policy.properties`·`milestone-6.md`)은 커밋 해시 hunk 격리(`git apply -R`). **V16 은 파일 삭제로
되돌리지 않는다** — 실 DB 가 적용된 뒤라면 새 V 파일의 `DROP COLUMN` 이 절차다(rollback.md 에 두 갈래를 적는다:
미적용 DB → 파일 삭제, 적용 DB → 새 V). 임시 clone ①~⑥ 실측, `실측 HEAD` 기록, 유효성 술어는 6F-4-w 와 같다.

## 하네스 레인 변경 (상시 절)

착수 시점 없음. 생기면 여기에 적는다.

## OPEN — 수령·신설

| ID | 방향 | 내용 |
|---|---|---|
| `OPEN-6F-ASSEMBLY` | **수령·닫음(dry-run)** | 포트 아홉 배선 = 이 slice. 6F-7 인계 둘(D-6F7-11·`Failed` 로깅)은 커밋 경로 → 아래 신설로 |
| `OPEN-6F4W-ASSEMBLE-CALLER` | **수령·닫음** | D-6A3-9 ArchUnit 집합 등식 |
| `OPEN-6F2-CANDIDATE-BOUND` | **수령·닫음** | D-6A3-7 설정 속성 + 409 |
| 4B-6b 인계(`UnavailableMlAnalysis` 처분) | **수령·닫음** | 운영자 결정 1 — 자리지킴 배선 |
| `OPEN-6F-ACTIVE-BID-DEFINITION`(결정 ③) | **이행** | D-6A3-4·5 |
| `OPEN-ML-ANALYSIS-WIRING` | **신설** | `OpportunityAnalysis` + 포트 여섯 실 배선 + ml-engine 서빙 + 외부 호출 승인 |
| `OPEN-6A3-EVALUATION-COMMIT` | **신설** | outbox 커밋 경로(발송 채널과 함께). D-6F7-11 원자성·`Failed` 로깅을 받는다 |
| `OPEN-6A3-EVALUATION-DETAIL` | **신설** | 후보별 사유 상세 응답 — D-6A1-38 을 깊이 1 object 배열까지 넓히는 contract-keeper 결정 뒤 |
| `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` | **신설** | `maxActiveBids` 편집 경로(6A-2 세션 명령 endpoint 가 받는다). 그 전엔 DB 직접 설정 |
| `OPEN-6F3-BID-RECORD` | 변경 없음 | 투찰 기록 표로 현재값을 세는 축(6D·6E) |
| `OPEN-6A1-CONNECTION-POOL` | 변경 없음 | 배선이 커져도 풀은 6C/6E |

## 확인하지 않은 것 (착수 시점)

- `notice_title` 실 데이터 0건(6F-4 인계) — E2E 표본 공고의 감시 텍스트는 빈 문자열이라 필수 키워드 규칙이 있으면
  전건 불일치다. E2E 는 **키워드 규칙 없는 전략**으로 결정성을 잰다. 사실로 적는다.
- `StrategyRepository` 인터페이스에 `save` 가 있는지·요청 스코프 「한 번 읽기」를 어떤 형태로 구현할지(데코레이터 vs
  적재값 전달)는 구현 레인의 0단계 측정 뒤 계약 갱신으로 고정한다.
- 전략 편집 corpus(`fixtures/`)가 draft 필드 추가에 영향받는지 — 0단계에서 `check` 로 실측.

## 리뷰 레인

`verifier`(opus) + `code-reviewer`(**호출 시 `model: sonnet` 명시**) 병렬. **`migration-reviewer`**(V16) ·
**`privacy-gate`**(새 HTTP 응답에 공고 식별자·요청 audit) · **`contract-keeper`**(OpenAPI 변경) — 이 장비에 전용
정의가 없으면 범용 에이전트(opus)가 지시문 전문으로 대행하고 리포트 첫 줄에 적는다. **Codex 없음**(운영자 결정 4).
종결은 **`verifier ready-for-review` + 사용자 승인**.
