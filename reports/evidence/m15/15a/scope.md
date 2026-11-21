# M15 / 15A — 전략·프로필·편집 세션의 계정별 분리 — slice 계약

```yaml
milestone: M15
slice: 15a-account-scoped-strategy
base_sha: 99f2fb25989078d7ab1640de0dc8e399f5d1688d   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  # ── 마이그레이션 ────────────────────────────────────────────────────────
  - adapters/src/main/resources/db/migration/V28__account_scoped_strategy_profile.sql   # 신설
  # ── 포트 (workflow) — 계정 인자를 더한다 ────────────────────────────────
  - workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/ProfilePorts.kt
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt
  - workflow/src/main/kotlin/bidvector/workflow/suggestion/ListSuggestionsUseCase.kt
  - workflow/src/main/kotlin/bidvector/workflow/profile/ProfileUseCases.kt
  - workflow/src/main/kotlin/bidvector/workflow/profile/ReadOnboardingChecklistUseCase.kt
  - workflow/src/test/kotlin/bidvector/workflow/**        # 포트 fake · 조립 인자
  # ── 어댑터 (adapters) ───────────────────────────────────────────────────
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt           # 계정 키 질의
  - adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt
  - adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcEditSessionRepository.kt
  - adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyEditTransaction.kt
  - adapters/src/main/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepository.kt
  - adapters/src/main/kotlin/bidvector/adapters/profile/JdbcOperatorProfileWriter.kt
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/PinnedStrategyRepository.kt
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/EvaluationCommitRun.kt
  - adapters/src/main/kotlin/bidvector/adapters/notificationinbox/JdbcNotificationAudience.kt
  - adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGate.kt
  - adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseJudgement.kt
  - adapters/src/main/kotlin/bidvector/adapters/qualification/PinnedLicenseGate.kt
  - adapters/src/test/kotlin/bidvector/adapters/**         # 마이그레이션 래칫 · 저장소 test
  # ── HTTP · 배선 (app) ───────────────────────────────────────────────────
  - app/src/main/kotlin/bidvector/app/http/AccountScopedRoutes.kt     # 뿌리 넷을 더한다
  - app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt
  - app/src/main/kotlin/bidvector/app/http/StrategyEditController.kt
  - app/src/main/kotlin/bidvector/app/http/SuggestionController.kt
  - app/src/main/kotlin/bidvector/app/http/ProfileCommandController.kt
  - app/src/main/kotlin/bidvector/app/http/EvaluationDryRunController.kt
  - app/src/main/kotlin/bidvector/app/http/BidPriceRequestController.kt
  - app/src/main/kotlin/bidvector/app/wiring/StrategyEditExecutor.kt   # StrategyQuery 포함
  - app/src/main/kotlin/bidvector/app/wiring/SuggestionQuery.kt
  - app/src/main/kotlin/bidvector/app/wiring/EvaluationDryRunFactory.kt
  - app/src/main/kotlin/bidvector/app/wiring/BidPriceRequestFactory.kt
  - app/src/main/kotlin/bidvector/app/wiring/EvaluationCommitWiring.kt
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitLog.kt   # 계수 한 줄
  - app/src/test/kotlin/bidvector/app/**                   # 격리 test 다섯 축 · 기존 test 세션 전환
  # ── 계약 · 게이트 장부 ──────────────────────────────────────────────────
  - openapi/bidvector-operator-api.yaml    # 계정 전용이 된 operation 의 보안 선언
  - config/quality/gate-tests.properties   # 새 Kotlin test class 를 **만드는 커밋에서** 등재
  - reports/evidence/m15/15a/
out_of_scope:
  - 열린 가입(`POST /api/accounts`) · V29 · 공개 경로 집합            # 15B
  - 사업자번호 선택 입력 · V30                                        # 15C
  - 계정별 Telegram 채널 · 이메일 · 비밀번호 찾기 · 탈퇴             # 계약 「범위 밖」
  - `account_business_profile`(V22) · `account_construction_capacity` # 이미 계정 단위다
  - `notification_inbox`(V23) 의 스키마                               # 이미 계정 단위다
  - web/**                                                            # 화면 변경 0(아래 「web 무접촉」)
  - .github/workflows/ci.yml                                          # acceptance 는 오늘의 job 명령 그대로
acceptance_commands:
  # ci.yml `check` job 의 Kotlin 전건. 이 slice 는 서버·DB 전용이라 그 job 하나다
  # (워크플로가 `ml-engine`·`web` 과 소스가 겹치지 않음을 선언한다 — 「web 무접촉」 참고).
  - ./gradlew --no-daemon check
rollback: rollback.md
```

## 이 slice 가 바꾸는 것

`operator_strategy` · `operator_strategy_revision` · `operator_profile` · `edit_session` 네 표가
**싱글턴에서 계정 소유로** 옮겨 간다(V28). 그 표를 읽고 쓰는 모든 경로가 계정을 인자로 받고,
평가 커밋 러너는 **계정마다** 그 계정의 전략·프로필로 평가해 **그 계정의 알림함에만** 쓴다.

**왜 15B(가입)보다 먼저인가**(계약 머리말): 단일 행을 둔 채 가입을 열면 가입자가 운영자의
전략·면허를 보고 고치며 운영자의 후보 알림을 전부 받는다.

## 설계 — 계정을 **포트 서명**에 둔다

`StrategyReader.load(account)` · `StrategyRepository.save(account, applied)` ·
`EditSessionRepository.load(account, id)`/`save(account, session)` ·
`OperatorProfilePort.current(account)` · `OperatorProfileStorePort.save(account, facts)`.

**이 선택은 발명이 아니라 저장소가 미리 적어 둔 처분이다.** `OperatorProfileStorePort` 의 KDoc 이
「계정을 받지 않는다 … 계정 단위 전환은 R-2 남은 절반(별 slice)이고, **그때 이 서명이 바뀌면
컴파일 오류가 고칠 자리를 전부 가리킨다**」라고 적어 두었고, `ProfileUseCases` · `ReadOnboardingChecklistUseCase` ·
`JdbcOperatorProfileWriter` 가 같은 문장을 세 번 더 적었다. 이 slice 가 그 자리다.

**대안(조립 시점에 계정을 묶는 요청 스코프 어댑터)을 쓰지 않는 이유**: 포트가 계정을 모르면
「계정을 빠뜨린 호출」이 **시그니처상 존재**하고, 그것을 막는 것은 배선 관례뿐이다. 서명에 두면
빠뜨린 자리가 컴파일에서 멈춘다 — 이 slice 가 지키려는 것이 바로 그 전수성이다.

## 행이 없는 계정의 기본값

| 표 | 행이 없을 때 | 첫 저장 |
| --- | --- | --- |
| `operator_strategy` | 빈 전략(`StrategyDraft()` + `revision 0`) — **기존 동작 그대로**(D-6F1-4) | upsert 가 그 계정의 행을 만든다 |
| `operator_profile` | `null` = 「프로필 미설정」 — **기존 동작 그대로**(D-6F6-3) | upsert 가 그 계정의 행을 만든다 |
| `edit_session` | `null` | `begin` 이 그 계정의 행을 만든다 |

**빌려 오지 않는다**: 질의의 술어가 `account_id = ?` 하나라 다른 계정의 행이 결과집합에 들어올
구조가 없다. 「없으면 비어 있다」는 이미 있던 갈래이고, 이 slice 는 그 갈래가 **계정마다** 서게
한다.

## 평가 커밋 러너 — 계정마다 한 번

1. 계정 전수를 읽는다(`NotificationAudiencePort` — 그 포트가 이미 「체계의 알림이 갈 계정 전수」다).
2. 계정마다 전략을 읽는다. **여력 상한(`maxActiveBids`)이 없으면 그 계정을 건너뛰고 센다.**
   빈 전략(행 없음)은 상한도 없으므로 같은 술어가 둘을 함께 덮는다.
3. 평가한 계정의 알림함에만 쓴다 — 청중은 `PinnedNotificationAudience(account)` 하나다
   (`PinnedStrategyRepository` 와 같은 꼴: run 하나가 보는 값을 조립이 고정한다).
4. 러너 로그가 `계정 N · 건너뜀 M` 을 낸다.

**앞 판과의 차이 한 줄**: 상한 미설정이 **기동 실패**였다가 **그 계정 건너뜀**이 된다. 평가하지
않는다는 결과는 같고(지어낸 상한으로 평가하지 않는다), 한 계정의 미설정이 다른 계정의 평가를
멈추지 않는다. 계수로 관측된다.

**Telegram 은 바꾸지 않는다**: 전역 경로(outbox → relay)는 그대로이고, **가장 작은 계정**의
run 만 그 채널에 쓴다. 다른 계정의 후보는 알림함에만 남는다(계약 「Telegram」 행 · `OPEN-STR-07`).

## 계정 전용 뿌리가 넷 늘어난다

`AccountScopedRoutes.ROOTS` 에 `/api/strategy` · `/api/suggestions` · `/api/bid-price-requests` ·
`/api/evaluation-dry-runs` 가 든다. **계약이 그 넷 가운데 셋을 적었고**, dry-run 은 전략을 읽으므로
같은 규칙을 피할 수 없다 — 계정 없는 주체에게 전략을 주려면 **누군가의 행을 빌려야** 하고 그것이
이 slice 가 막는 것이다.

**귀결**: 자격 헤더(`operator.credential.value`) 경로는 그 넷에 **401** 이다. 화면은 영향이 없다
(웹은 세션 쿠키로 부른다). 운영 스크립트가 그 경로를 쓰면 세션을 내야 한다 — 알려진 제한 3.

이 변경은 **선언이 아니라 판정으로 잠긴다**: `ProductionHttpSurfaceTest` 가 「자격 헤더로 닿는
매핑 ∩ 계정 전용 = ∅」을 실제 401 로 재고, `OpenApiSecurityContractTest` 가 문서의 보안 선언이
그 판정과 같은지를 양방향으로 잰다.

## 마이그레이션 V28 — 이관 규칙을 SQL 안에서 결정적으로

네 표에 같은 세 걸음을 쓴다.

1. `ADD COLUMN account_id BIGINT REFERENCES account (id)`
2. `UPDATE … SET account_id = (SELECT min(id) FROM account)` — 계정이 하나 이상이면 **가장 작은
   id**(지금의 운영 계정)에 붙는다. 계정이 0 이면 `min()` 이 `NULL` 이다.
3. `DELETE FROM … WHERE account_id IS NULL` — **계정이 0 이면 그 행은 버린다.** 소유자가 없는
   행을 `NOT NULL` 아래 둘 수 없고, 둘 수 있다 해도 어느 계정도 그것을 읽지 못한다(질의 술어가
   `account_id = ?` 다). 깨끗한 DB 에서는 네 표가 비어 있어 이 걸음이 0 행이다.
4. 싱글턴 키를 걷는다 — `DROP COLUMN id` 가 PK 와 `CHECK (id = 1)` 을 함께 가져간다
   (제약 이름을 손으로 적지 않는다). `operator_strategy_revision` 은 `DROP CONSTRAINT …_pkey`.
5. `SET NOT NULL` + 새 PK — `operator_strategy(account_id)` · `operator_profile(account_id)` ·
   `operator_strategy_revision(account_id, revision)` · `edit_session(id)` 는 그대로 두고
   `account_id` 를 `NOT NULL` 로(세션 id 는 서버가 만드는 UUID 라 이미 전역 유일하다).

**갈래 셋을 test 로 잰다**(계정 0 · 1 · 2 이상) — 마이그레이션을 V27 까지 돌린 DB 에 계정과
싱글턴 행을 심고 V28 을 돌려 결과를 본다.

## 착수 실측 — 계약의 전제를 코드로 다시 잼

base `99f2fb25` 의 worktree 에서 쟀다. 수치와 그것을 낸 명령은 `commands.md` 가 정본이다.

| 축 | 잰 것 | 결과 |
| --- | --- | --- |
| 마지막 마이그레이션 | `db/migration/` | V27 — **V28 이 다음 번호다**(계약의 배정과 같다) |
| 싱글턴 CHECK 자리 | V9 · V12 | `operator_strategy.id` · `operator_profile.id` 각각 `CHECK (id = 1)`. `operator_strategy_revision` 은 `revision` PK · `edit_session` 은 계정 칸이 **아예 없다** |
| 계정 전용 뿌리 | `AccountScopedRoutes.ROOTS` | 다섯(입찰함 · 결과 · 알림함 · 프로필 · 시작하기). 전략 · 제안 · 투찰가 · dry-run 은 **밖** |
| 전략을 읽는 자리 | `StrategyReader.load()` 호출 전수 | 여섯 — 전략 조회기 · 편집 use case · 제안 · 체크리스트 · dry-run 팩토리 · 커밋 배선 |
| V12 를 읽는 자리 | `OperatorProfilePort.current()` 호출 전수 | 다섯 — 제안 · 기회 분석 · 프로필 조회 · 체크리스트 · 면허 게이트 둘 |
| 알림 청중 | `NotificationAudienceSql.SELECT_ACCOUNT_IDS` | `SELECT id FROM account ORDER BY id` — 새 후보 알림이 **모든 계정**에 간다(계약의 입력 그대로) |
| 자격 헤더로 도는 기존 E2E | `OperatorCredentialAuthenticator.CREDENTIAL_HEADER` 사용처 | 전략 · 제안 · dry-run · 투찰가의 production E2E 가 그 헤더를 쓴다 → **세션으로 전환**해야 한다(아래 알려진 제한 3 · 계약 갱신 (1)) |
| 두 계정 E2E 선례 | `ProfileOwnershipE2ETest` | 실 DB · 실 세션 · 로그인 둘의 하네스가 **이미 있다**. 격리 test 는 그 꼴을 그대로 쓴다(새 하네스를 만들지 않는다) |
| 마이그레이션 래칫 | `CleanMigration{,Check,Column,Index,Privilege}Test` | 표별 CHECK 수 · 열 전수 · PK 열 집합 · FK 목록 · 권한 전수가 **전부 등재형**이라 V28 이 그 다섯을 함께 움직인다 |

## web 무접촉

이 slice 는 `web/` 을 열지 않는다. OpenAPI 의 변경은 **보안 선언**(`security:`)뿐이고
`openapi-typescript` 는 그것을 타입으로 내보내지 않는다 — 생성 파일의 바이트가 그대로임을
`npm run api:types:check` 상당의 재생성 대조로 확인하고 `commands.md` 에 한 줄 남긴다.
바이트가 움직이면 그때 `web` job 전건이 acceptance 에 든다(계약 갱신으로 등재).

## 알려진 제한 (착수 시점)

1. **계정이 0 인 DB 에서 V28 은 네 표의 남은 행을 버린다.** 운영 DB 는 계정이 하나라 이 갈래에
   닿지 않는다. dev DB 에 계정 없이 전략이 남아 있으면 그 전략이 사라진다 — 되돌릴 수 없다
   (반입 스크립트가 백업을 뜬다, 계약 「승인 경계」).
2. **상한 미설정이 기동 실패가 아니라 건너뜀이 된다**(위 「평가 커밋 러너」). 관측은 로그 계수다.
3. **자격 헤더만 쓰는 소비자는 전략·제안·투찰가·dry-run 에 닿지 못한다.** `AccountScopedRoutes`
   가 이미 같은 제한을 입찰함·알림함·프로필에 선언해 두었고(「R-2 남은 절반이 풀 때까지」) 이
   slice 가 그 집합을 넷 넓힌다.
4. **Telegram 은 가장 작은 계정의 후보만 나른다.** 다른 계정의 새 후보는 알림함에만 남는다.
5. **`operator_strategy_revision` 의 개정 번호는 계정마다 독립이다.** 두 계정이 같은 `revision`
   값을 각자 가질 수 있고, 그것이 PK `(account_id, revision)` 의 뜻이다.

## 승인 경계

- **DB 마이그레이션이 든다** — 계약이 V28 을 배정했다. 번호는 V28 하나, 기존 마이그레이션 파일은
  **편집하지 않는다**(append-only).
- 실제 외부 API/LLM/Telegram 호출 0 · 운영 DB write 0. test 는 Testcontainers.
- push/PR 은 하되 **머지하지 않는다**.
- 재작업 상한 5 에 닿으면 보고한다.

## base 확인

`git rev-parse 99f2fb25` → `99f2fb25989078d7ab1640de0dc8e399f5d1688d` ·
`git merge-base --is-ancestor 99f2fb25 HEAD` 를 착수 커밋에서 기계로 확인했다(위 yaml 의 40자는
손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에
줄이 서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에
있다. 귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-21 초판(착수).
