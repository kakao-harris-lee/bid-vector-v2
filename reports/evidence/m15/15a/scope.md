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
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitLines.kt   # 계수 한 줄
  - app/src/test/kotlin/bidvector/app/**                   # 격리 test 다섯 축 · 기존 test 세션 전환
  # ── 계정 식별자의 이사 (라운드 2, 「갱신 이력」 (2)) ─────────────────────
  # 포트 서명에 계정을 넣자 `workflow.strategy -> workflow.access` 가 서고 패키지 순환
  # 넷이 닫혔다. 끊는 변은 하나뿐이라 `AccountId` 를 공유 커널로 옮겼고, 그 **한 타입의
  # import** 가 모듈 넷에 걸쳐 있다 — 아래 트리가 통째로 든다(195 파일 가운데 대부분이
  # import 한 줄이다). 거동을 바꾸는 자리는 위에 이름으로 적은 것들 그대로다.
  - shared-kernel/src/main/kotlin/bidvector/sharedkernel/AccountId.kt   # 신설
  - shared-kernel/src/test/kotlin/bidvector/sharedkernel/AccountIdTest.kt   # 그 모듈의 변이 래칫 회복
  - workflow/src/main/kotlin/bidvector/workflow/**
  - adapters/src/main/kotlin/bidvector/adapters/**
  - app/src/main/kotlin/bidvector/app/**
  # app test 트리의 `bidvector.app` **밖** 둘 — 아키텍처 음성 표본과 가드 probe 다. 글롭
  # (`app/src/test/kotlin/bidvector/app/**`)이 덮지 않아 이름으로 적는다(r1 verifier L2).
  - app/src/test/kotlin/bidvector/archfixture/violating/app/rules/RuleNegativeSamples.kt
  - app/src/test/kotlin/bidvector/guardprobe/AccountBindingGuardTest.kt
  # ── 계약 · 게이트 장부 ──────────────────────────────────────────────────
  - openapi/bidvector-operator-api.yaml    # 계정 전용이 된 operation 의 보안 선언
  - config/quality/gate-tests.properties   # 새 Kotlin test class 를 **만드는 커밋에서** 등재
  - config/quality/architecture-policy.properties   # 허용 뿌리 · 주입/2층 목록 · 어댑터 멤버 서술자
  - web/e2e/harness/journey-up.mjs         # 여정 하네스의 전략 심기 축(라운드 4)
  - .github/workflows/ci.yml               # 컨테이너 스모크의 인증 축(라운드 5, 팀장 승인)
  - tools/local-tls-smoke.sh               # 같은 사유 — 프록시 경유 /api 축
  - tools/db-rehearsal.sh                  # 백업 리허설의 전략 질의(싱글턴 키 → 계정 키)
  - reports/evidence/m15/15a/
out_of_scope:
  - 열린 가입(`POST /api/accounts`) · V29 · 공개 경로 집합            # 15B
  - 사업자번호 선택 입력 · V30                                        # 15C
  - 계정별 Telegram 채널 · 이메일 · 비밀번호 찾기 · 탈퇴             # 계약 「범위 밖」
  - `account_business_profile`(V22) · `account_construction_capacity` # 이미 계정 단위다
  - `notification_inbox`(V23) 의 스키마                               # 이미 계정 단위다
  - web/src/**                                                        # 화면 변경 0(아래 「web 접촉」)
acceptance_commands:
  # ci.yml `check` job 의 Kotlin 전건.
  - ./gradlew --no-daemon check
  # ci.yml `web` job 의 step 전건(W-1~W-13). **라운드 4 에서 acceptance 에 들었다** —
  # 여정 하네스가 자격 헤더로 전략을 심고 있었고 그 경로가 401 이 됐다(「web 접촉」).
  - (web/) npm ci · lint · typecheck · gate:src-tree · api:types:check · test · gate:tests ·
    build · gate:boundary-canary · playwright install chromium · test:e2e ·
    ./gradlew --no-daemon :app:bootJar · test:e2e:journey
  # ci.yml `container` job. **라운드 5 에서 들었다** — 컨테이너 스모크가 자격 헤더로 전략
  # 왕복을 몰고 있었고 그 경로가 401 이 됐다. 이 job 은 이미지 빌드·compose 기동이 필요해
  # 로컬 실행 비용이 크므로 판정은 PR 의 CI 가 낸다(로컬 실측 대상이 아니다).
  - (CI) container job — 컨테이너 스모크(S-23b) · 로컬 패키징 스모크(S-26) · 백업 리허설(S-23c)
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

## web 접촉 — 화면 0, 하네스 하나

**화면 소스(`web/src/**`)는 열지 않는다.** OpenAPI 의 변경은 **보안 선언**(`security:`)뿐이고
`openapi-typescript` 는 그것을 타입으로 내보내지 않는다 — 생성 파일의 바이트가 그대로임을
`npm run api:types:check` 로 **실측했다**(재생성 대조, 195341 바이트 동일). 착수 때 이 절은
그 등식을 예측으로 적었고, 라운드 4 가 그것을 측정으로 바꿨다.

**그럼에도 `web` job 전건이 acceptance 에 들었다** — 바이트가 움직여서가 아니라, 여정 e2e 의
**하네스**가 출하 jar 를 자격 헤더로 두드려 여력 상한을 심고 있었기 때문이다. 그 경로는 V28
뒤 401 이고, 하네스는 기동 중에 죽어 여정 28 건이 **하나도 수집되지 않았다**(실측). Kotlin
`check` 는 이 자리를 보지 못한다 — 다른 job 이고 다른 프로세스다.

귀결 둘. ① `web/e2e/harness/journey-up.mjs` 가 in_scope 에 들어왔다(위). ② acceptance 가
`check` 하나에서 **두 job**으로 늘었다. 「소스가 겹치지 않으니 job 하나」라는 착수 때의 근거는
**소스 겹침만 보고 런타임 의존(하네스 → 출하 API)을 보지 않은 것**이었다.

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

## 알려진 제한 (r1 리뷰 라운드에 등재)

6. **일일 평가 비용이 계정 수에 선형이다 — `OPEN-M15-EVAL-COST-PER-ACCOUNT`**(신설 id,
   `OPEN-M15-COMMIT-CAPACITY` 와 **다른 축**이다: 그쪽은 「진행 입찰 수 값이 계정마다 없다」이고
   이쪽은 「run 비용에 상한이 없다」).

   run 이 계정마다 서고 각 run 이 **그 계정의** `candidateLimit` 까지 ML·임베딩을 부른다 —
   계정 K 에 대해 비용이 K 배다. `bidvector.evaluation.candidate-cap` 은 전역이고 러너가
   **로그에 싣기만** 할 뿐 run 을 묶지 않는다.

   **실패 비용도 같은 모양으로 늘었다**(r1 에서 계정별 격리를 넣은 결과다). 앞 판은 첫 예외에서
   멈춰 한 번만 실패했지만, 이제 DB 가 내려가면 계정마다 한 번씩 **K × 타임아웃**을 치른다.
   격리 자체는 옳다(한 계정이 남의 평가를 끊지 않는다) — 비용이 그 대가라는 사실이 여기 있다.

   **방아쇠: 15B 머지 전에 결정해야 한다.** 가입이 열리면 계정 수가 운영자의 손을 떠난다.
   선택지 셋 — run 전체의 전역 상한 · 계정별 상한의 고정(clamp) · run 마감 시각(deadline).

7. **운영 runbook 의 종료 코드 어휘가 낡았다** — `docs/runbook/m6-6e-operations.md` §3.4·§3.5.
   `FAILED`(1) 은 이제 **청중을 못 읽은 경우 하나**이고, 계정 하나의 실패는 `INCOMPLETE`(2) 와
   `evaluation-commit account-failed cause=` 줄이다. 종료 줄의 `failed=`·`suppressed=` 두 칸도
   문서에 없다.

   **이 slice 가 고치지 않는다** — runbook 은 in_scope 밖이고, **15B 가 그 파일을 어차피
   연다**(가입이 계정 명령 절을 바꾼다). 거기서 함께 고친다. 그때까지 운영자가 읽는 문서는
   종료 코드의 뜻을 한 갈래 좁게 말한다.

8. **「평가한 계정 0 · 건너뜀 N」이 종료 코드 0 이다**(결정은 유지한다 — 갓 설치한 체계가 매일
   운영자를 깨우지 않게 한다, 러너 KDoc). 계수(`accounts=0 skipped=N`)가 그 사실을 말하지만
   **그 계수를 읽는 경보 규칙이 없다**. 즉 전원이 상한을 비워 둔 채 며칠이 지나도 cron 은 성공을
   보고한다. 운영 공백으로 등재한다 — 닫는 자리는 경보 쪽이지 종료 코드가 아니다.

9. **15B 로 넘긴 낮은 축 넷**(r1 리뷰). ⓐ 「채널 보유자를 **값으로** 고른다」가 측정되지 않았다 —
   `minByOrNull` 을 `firstOrNull` 로 바꿔도 초록이다(청중이 늘 오름차순이라). `[2, 1]` 순서의
   청중으로 한 건 재면 닫힌다. ⓑ 그 불변식을 공장 쪽에도 둘 수 있다(지금은 러너만 안다).
   ⓒ 계정별 `catch` 가 `InterruptedException` 의 인터럽트 플래그를 되세우지 않는다.
   ⓓ `OutboxNotificationRequestPortTest` 가 멱등 키 형식을 축어로 고정하지 않는다.

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
- 2026-11-21 (1) 라운드 1 이 만든 자리를 in_scope 와 대조해 등재했다 — 계정별 run 공장
  (`EvaluationCommitRuns`) · 출하 E2E 의 세션 지원 · 마이그레이션 갈래 test · 평가 포트 셋 ·
  `architecture-policy.properties`. 반대로 계약이 적었으나 **건드리지 않은** 둘
  (`JdbcNotificationAudience` · `EvaluationCommitLog`)은 뺐다 — 청중 고정이 run 안에서
  이뤄져 어댑터를 바꿀 필요가 없었고, 계수 줄은 `EvaluationCommitLines` 였다.
- 2026-11-21 (2) **`AccountId` 를 `bidvector.sharedkernel` 로 옮겼다.** 포트 서명에 계정을 둔
  결과 `workflow.strategy -> workflow.access` 가 서고, `workflow.access` 가 이미 반대 방향으로
  시계·해시를 보고 있어 ArchUnit 슬라이스 규칙이 **순환 넷**을 냈다. 넷 다 `workflow.access`
  로 들어오는 변을 요구하고 그 원천은 하나뿐이라, 식별자를 아무것도 참조하지 않는 자리로
  옮기는 것이 그 변을 없애는 유일한 길이다. in_scope 가 모듈 넷의 트리로 넓어진 사유다.
- 2026-11-21 (3) **acceptance 에 `web` job 이 들었다**(위 「web 접촉」) — 여정 하네스가 자격
  헤더로 전략을 심고 있었고 그 경로가 401 이 됐다. `web/e2e/harness/journey-up.mjs` 등재.
- 2026-11-21 (4) **`container` job 과 그 스크립트 셋이 in_scope 에 들었다**(팀장 승인, 선택 (a)) —
  컨테이너 스모크가 `GET /api/strategy` 와 begin→value→confirm 왕복을 **자격 헤더**로 몰고
  있었고 V28 뒤 그 경로는 401 이다. 고친 모양은 **배포 표면을 건드리지 않는 쪽**이다:
  `docker/compose.yaml` 에 계정·비밀을 더하지 않고, 스모크가 실행 시점에 일회 비밀 문구를
  만들어(`openssl rand`, `::add-mask::`, 저장 안 함) **운영 runbook(m7-7f §12)과 같은 일회
  명령**으로 계정을 만든 뒤 세션으로 왕복을 돈다. 자격 축은 아직 그 축으로 닿는 경로
  (`/api/notices`)에 남겨 **죽지 않았음을 계속 잰다**. 같은 사유로 `tools/local-tls-smoke.sh`
  의 `/api` 라우팅 축과 `tools/db-rehearsal.sh` 의 전략 질의(싱글턴 키 → 계정 키)도 고쳤다.
- 2026-11-22 (5) **r1 리뷰 라운드** — 계약 위반 하나(전역 Telegram 채널)를 구현으로 닫고,
  장부 밖이던 경로 셋(`AccountIdTest` · `RuleNegativeSamples` · `AccountBindingGuardTest`)을
  이름으로 등재했다. 전역 채널 축이 `workflow.evaluation` 에 타입 둘
  (`SuppressedNotificationChannel` · `NotificationRequestOutcome.Suppressed`)을 더한다.
