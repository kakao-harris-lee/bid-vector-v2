# M15 / 15B — 열린 가입 · slice 계약

```yaml
milestone: M15
slice: 15b
base_sha: 99f2fb25989078d7ab1640de0dc8e399f5d1688d   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - adapters/src/main/resources/db/migration/V29__account_open_signup.sql  # app 역할의 열 단위 INSERT + 기본값 셋
  - adapters/src/main/kotlin/bidvector/adapters/access/AccessSql.kt        # 가입 INSERT 문장 하나
  - adapters/src/main/kotlin/bidvector/adapters/access/JdbcAccountStore.kt # 가입 저장소(요청 경로, 좁은 포트)
  - adapters/src/main/kotlin/bidvector/adapters/access/BrowserAccessTransaction.kt  # 조립에 가입 포트 하나
  - adapters/src/test/kotlin/bidvector/adapters/access/                    # 열 권한 · 가입 저장소 test · 공유 정책 helper(r1)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt  # 축9 주석(열 권한은 아래 신설 class)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt     # 계약 갱신 (1) — DEFAULT 셋이 열 장부에 선다
  - adapters/src/test/kotlin/bidvector/adapters/persistence/MigrationLockTimeoutTest.kt     # 계약 갱신 (3) — V29 가 관례 모집단에 든다
  - workflow/src/main/kotlin/bidvector/workflow/access/AccessPorts.kt      # AccountSignUpPort(좁은 쓰기 하나)
  - workflow/src/main/kotlin/bidvector/workflow/access/AccessValues.kt     # ClearPassphrase 의 길이 술어(값이 타입 안에 머문다)
  - workflow/src/main/kotlin/bidvector/workflow/access/SignUpPolicyData.kt # 가입 정책(신설, versioned)
  - workflow/src/main/kotlin/bidvector/workflow/access/BrowserAccessWorkflow.kt  # signUp — 세션 발급은 기존 issue 재사용
  - workflow/src/main/kotlin/bidvector/workflow/access/AccountProvisioning.kt    # create 완화(같은 login_name 만 거부)
  - workflow/src/test/kotlin/bidvector/workflow/access/                    # 가입 use case · 정책 · provisioning test
  - app/src/main/kotlin/bidvector/app/http/RequestAuthentication.kt        # OPEN_ROUTES 에 POST /api/accounts
  - app/src/main/kotlin/bidvector/app/http/AccountSignUpController.kt      # 신설 — 가입 endpoint 하나
  - app/src/main/kotlin/bidvector/app/http/SignUpThrottle.kt               # 신설 — 원격 주소별 메모리 창
  - app/src/main/kotlin/bidvector/app/http/ErrorBody.kt                    # 코드 둘(중복 · 상한 초과)
  - app/src/main/kotlin/bidvector/app/wiring/BrowserAccessExecutor.kt      # signUp 표면 하나
  - app/src/main/kotlin/bidvector/app/wiring/BrowserAccessWiring.kt        # 가입 정책 빈(r1 v LOW-1 — 누락 등재)
  - app/src/main/kotlin/bidvector/app/http/OperatorSessionController.kt    # r1 — 201 응답 조립을 공유 함수로
  - app/src/main/kotlin/bidvector/app/BidVectorApplication.kt              # 가입 상한 빈(app.http 를 아는 유일한 자리)
  - app/src/main/kotlin/bidvector/app/access/AccountCommandRunner.kt       # (변경 없음 — 완화는 use case 쪽이고 로그 어휘는 그대로다)
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt # r3 — run 당 계정 상한 · 연속 실패 중단 · 인터럽트 플래그 (계약 갱신 (7))
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitLines.kt  # r3 — 시작·마침 줄 계수, 채널 보유자 값 함수, 멈춤 줄
  - app/src/main/kotlin/bidvector/app/wiring/EvaluationWiring.kt           # r3 — 상한 둘(여기만 기본값을 갖는다)
  - app/src/main/kotlin/bidvector/app/wiring/EvaluationCommitWiring.kt     # r3 — 상한 둘을 러너로
  - app/src/test/kotlin/bidvector/app/http/                                # 집합 등식 둘 · 가입 endpoint · 상한
  - app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt  # (변경 없음 — r2 가 tier1 등재를 걷으면서 되돌렸다, r3 cr L-6)
  - app/src/test/kotlin/bidvector/app/SignUpThrottleWiringTest.kt          # r2 — 출하 상한의 값·배선
  - web/src/paths.ts                                                       # r2 — 세션 전 자리 주소 한 자리(순환 import 제거)
  - app/src/test/kotlin/bidvector/app/access/AccountCommandRunnerTest.kt   # 완화된 create 의 로그·종료 코드
  - app/src/test/kotlin/bidvector/app/evaluation/                          # r3 — 상한 경계 · 운영 계정 우선 · 연속 실패 중단 · 보유자 값 선택
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPortTest.kt  # r3 — 멱등 키 형식을 축어로 고정(15A LOW ⓓ)
  - docs/runbook/m6-6e-operations.md                                       # r3 — 종료 코드 어휘 정정 · 계정 계수 넷 · 설정 키 둘(15A 알려진 제한 ⑦)
  - config/quality/architecture-policy.properties                          # handler · 주입 타입 · 비밀값 참조자
  - config/quality/gate-tests.properties                                   # 새 test class 를 만드는 커밋에서 등재
  - openapi/bidvector-operator-api.yaml                                    # operation 하나 + 스키마 + ErrorBody 코드 둘
  - web/src/api/generated/operator-api.ts                                  # 재생성(바이트 등식)
  - web/src/api/approvedOperations.ts                                      # 쌍 하나 등재
  - web/src/api/queries.ts                                                 # signUp 호출 하나
  - web/src/SignUpScreen.tsx                                               # 신설 — 가입 화면
  - web/src/LoginScreen.tsx                                                # 「계정 만들기」 링크 하나
  - web/src/routes.tsx                                                     # 가입 자리(화면 자리 표 밖, 로그인과 같은 축)
  - web/src/AppShell.tsx                                                   # 가입 자리에도 탐색을 그리지 않는다
  - web/src/AppRoot.tsx                                                    # 라우트 트리에 가입 자리(r1 v LOW-1 — 누락 등재)
  - web/config/gate-tests.txt                                              # 가입 화면 test 등재(r1 v LOW-1 — 누락 등재)
  - web/e2e/harness/journey-up.mjs                                         # r1 — 여정 레인의 상한 손잡이
  - docs/discovery/capability-map.md                                       # r1 — OPEN-M15-SIGNUP-HARDENING 등재
  - web/src/**/*.test.ts · web/src/**/*.test.tsx                           # 위 화면·셸·라우트의 짝 test(`routes.test.ts` 는 `.ts` 다)
  - web/e2e/journey/signup.spec.ts                                         # 여정 하나(가입 → 시작하기 → 화면들)
  - web/e2e/journey/shared.ts                                              # 가입 자리의 ALLOWED_ROUTES 한 줄
  - web/config/journey-specs.txt                                           # 새 여정 spec 을 같은 커밋에서 등재
  - reports/evidence/m15/15b/
out_of_scope:
  - operator_strategy · operator_profile · edit_session 의 계정별 분리     # 15A(V28) — 이 레인은 그 표를 만지지 않는다
  - account_business_profile 의 사업자번호 칸                               # 15C(V30)
  - 이메일 확인 · 비밀번호 찾기 · 탈퇴 · 초대 · 약관 동의                   # 계약 「범위 밖」 · OPEN-M15-SIGNUP-HARDENING
  - 영속 rate limit(DB·Redis)                                               # 같음 — 이 slice 는 메모리 창 하나다
  - rotate 명령                                                             # 계약 「rotate 는 그대로」
  - .github/workflows/ci.yml                                                # acceptance 는 오늘의 job 명령 그대로
  - ml-engine/**                                                            # 이 slice 는 건드리지 않는다
acceptance_commands:   # ci.yml 의 job 명령 그대로
  - ./gradlew --no-daemon check                 # check job
  - web: npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - web: npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - web: npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - web: ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
rollback: rollback.md
```

## 이 slice 가 뒤집는 결정과 그 근거

V20 은 「앱 역할에 `account` INSERT 가 없다 — 앱이 뚫려도 계정을 만들 수 없다」를 열 단위 GRANT 로
세웠다. M15 계약(2026-11-21, 운영자 지시 「초대 코드 + 운영자 승인 **없이** 바로 가입」)이 그 결정의
**DB 권한 절반**을 뒤집는다. 뒤집지 **않는** 절반을 여기 적어 둔다 — 그 둘이 한 덩어리가 아니다.

| V20 의 경계 | 이 slice 뒤 |
| --- | --- |
| `account` 에 INSERT 0 | **열 둘**(`login_name` · `passphrase_hash`)만 INSERT. 나머지 열은 그대로 거부 |
| `passphrase_hash` · `login_name` UPDATE 0 | **그대로** — 해시 교체는 여전히 운영자 명령의 소유자 연결뿐 |
| `created_at` · `updated_at` UPDATE 0 | **그대로**. 그 열은 V29 가 DEFAULT 를 주므로 앱이 **값을 고를 수 없다** |
| 요청 경로의 포트에 생성·교체 서명 0 | 생성은 **좁은 포트 하나**(`AccountSignUpPort.insertIfAbsent`). 교체 서명은 여전히 없다 |
| `DELETE`·`TRUNCATE` 0 | **그대로** |

## 착수 실측 (계약 행 ↔ 코드)

| 계약이 말한 것 | 착수 실측 | 처분 |
| --- | --- | --- |
| `OPEN_ROUTES` 는 하나 | `setOf("POST /api/session")`, 집합 등식 test **두 곳**(`BrowserSessionAuthenticationTest`·`OpenApiSecurityContractTest`) | 같은 커밋에서 셋을 함께 바꾼다 |
| `GRANT INSERT (login_name, passphrase_hash)` 면 충분한가 | **아니다** — V20 의 `created_at`·`updated_at`·`failed_attempts` 가 NOT NULL 이고 DEFAULT 가 없다. 두 열만 적는 INSERT 는 NOT NULL 위반으로 떨어진다 | V29 가 그 셋에 DEFAULT 를 준다(계약 갱신 (1)) |
| `CleanMigrationPrivilegeTest` 의 기대를 바꾼다 | 그 행렬은 `has_table_privilege` 라 **열 단위 부여를 세지 않는다** — `account` 의 `insert` 는 V29 뒤에도 `false` 다 | 행렬 값은 그대로 두고 **열 권한 축**을 더한다. 거동이 바뀐 자리는 `AccessPrivilegeTest` 의 「계정을 만들 수 없다」이고 그쪽이 RED 로 먼저 선다 |
| 세션 응답을 「`POST /api/session` 과 같이」 | `SessionResponse(csrf, expiresAt)` + `Set-Cookie` — 발급 자리는 `BrowserAccessWorkflow.issue` 하나(private) | 가입을 **같은 class** 에 두어 그 자리를 재사용한다(사본 0) |
| 원격 주소별 상한 | 앱 앞에 역방향 프록시가 서고 `forward-headers-strategy` 설정이 **없다** | `remoteAddr` 로 센다. 프록시 뒤에서는 그 값이 하나라 상한이 사실상 전역 10/시간이다 — **테스트 단계 한정**으로 KDoc·OPEN 에 적는다(아래 알려진 제한 ①) |
| 가입 직후 막는 자리가 있는가 | 화면 다섯은 전부 「빈 얼굴 + 사유」 경로를 이미 갖는다(`ScreenFaces`·`AbsentValue`). 라우팅은 세션 유무만 본다 | 막는 자리 0 — 여정이 그 사실을 실측한다 |

## 승인 경계 (계약 「승인 경계 — 테스트 단계 한정」의 이 slice 몫)

- 열린 가입과 시간당 10 건 상한은 **테스트 단계의 완화**다. 상용 전 재결정은
  `OPEN-M15-SIGNUP-HARDENING` 이 든다(초대/승인 · 이메일 확인 · 영속 rate limit · 약관 동의 · 탈퇴 파기).
- **`rotate` 를 건드리지 않는다.** `create` 의 완화는 「계정이 있으면 거부」 → 「같은 login_name 이
  있으면 거부」 하나다.
- 기존 게이트 **술어**를 바꾸지 않는다. 바꾸는 것은 등재 목록(핸들러 · 주입 타입 · 비밀값 참조자 ·
  게이트 test)과, 이 slice 가 거동을 뒤집은 단언 둘(`AccessPrivilegeTest` 의 계정 INSERT ·
  `AccountProvisioningTest` 의 「계정이 있으면 거부」)뿐이다.
- 마이그레이션 번호는 계약이 배정한 **V29** 고정. 15A(V28)·15C(V30) 의 표를 만지지 않는다.

## 알려진 제한

① **상한의 키가 원격 주소다.** 역방향 프록시 뒤에서는 모든 가입이 한 주소로 보여 상한이 전역
  10/시간이 된다. `X-Forwarded-For` 를 믿지 **않는다** — 신뢰 프록시 설정이 없는 상태에서 그 헤더를
  읽으면 공격자가 값을 지어 상한을 0 으로 만든다(지금 형태는 fail-closed 쪽이다).
② **창이 메모리에 있다.** 재기동하면 계수가 초기화되고, 인스턴스가 둘이면 각자 센다. 테스트 단계
  상한임을 KDoc 이 적는다.
③ **가입과 세션 발급이 한 트랜잭션이 아니다.** 로그인 경로와 같은 경계(문장마다 제 트랜잭션)를 쓰므로,
  세션 발급이 실패하면 계정만 남는다 — 그 계정은 로그인으로 들어올 수 있다(권한이 새지 않는다).
④ **`deferred` 를 읽는 경보가 없다**(계약 갱신 (7)·(10)). 회전이 「영원히 빠지는 계정」은 없앴지만,
  상한을 넘은 날 그 계정의 평가는 **최대 `ceil((K-1)/(n-1))` 영업일 늦는다** — 그 지연을 보는 자리가
  없고 cron 은 성공을 적는다. 종료 코드로 알리지 않기로 한 것은 결정이고(미룬 것은 실패가 아니다),
  그 결정의 대가가 이 공백이다. 닫는 자리는 경보 쪽이지 종료 코드가 아니다 — 15A 알려진 제한
  ⑧(「`accounts=0 skipped=N` 을 읽는 경보가 없다」)과 **같은 자리**다.
⑤ **회전은 「가장 오래 평가되지 않은 순」이 아니다.** 축이 날짜의 순수 함수라 **cron 을 거른 날의
  창은 그냥 지나간다**(영속 커서가 없다 — 그 상태를 두면 되돌림과 재실행이 서로 다른 판을 만든다).
  계정 집합이 바뀌면 창의 경계도 바뀌므로 한 바퀴가 정확히 맞아떨어지지 않는 날이 있다. 등재 자리는
  `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ 다.

## base 확인

`git rev-parse 99f2fb25` · `git merge-base --is-ancestor 99f2fb25 HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 머지 순서

**15A 뒤에 머지한다**(계약 「머지 순서와 병렬」). 단일 행 전략·프로필을 둔 채 가입을 열면 가입자가
운영 계정의 전략·프로필을 보고 고친다. PR 본문에 그 조건을 적는다.

## 계약 갱신 (구현 중 실측으로 드러난 것 — 팀장 보고 대상)

**(1) V29 가 DEFAULT 셋을 함께 준다.** 계약은 GRANT 와 시퀀스 권한만 적었는데, 열 둘만 적는 INSERT 는
V20 의 NOT NULL 세 열(`created_at`·`updated_at`·`failed_attempts`)에서 떨어진다. DEFAULT 를 주는 것이
열을 더 여는 것보다 **좁다** — 앱은 그 열에 값을 고를 수 없고(INSERT 권한 밖), 운영자 명령은 지금처럼
명시 값을 싣는다(거동 0).

**(2) 열 권한 축은 `CleanMigrationPrivilegeTest` 밖에 선다.** 그 class 의 행렬은
`has_table_privilege` 라 **열 단위 부여를 세지 않는다** — V29 뒤에도 `account.insert` 가 `false`
이고, 한 파일에 열 축을 합치면 크기 게이트(파일 500줄)를 넘는다. 그래서 신설
`AccountColumnPrivilegeTest` 가 집합 등식으로 재고(`BidCaseColumnPrivilegeTest` 선례),
`CleanMigrationPrivilegeTest` 는 **주석으로 그 분담을 적는다**. 거동이 실제로 뒤집힌 자리는
`AccessPrivilegeTest` 의 계정 INSERT 이고 그쪽이 RED 로 먼저 섰다.

**(3) V29 도 잠금 상한(`SET LOCAL lock_timeout`)을 선언한다.** 기존 표(`account`)를 `ALTER`
하므로 V18 이 세운 관례의 모집단에 든다 — `MigrationLockTimeoutTest` 의 비공허성 래칫이 그
사실을 든다.

**(4) 계약이 적은 시퀀스 GRANT 를 두지 않는다.** `account.id` 는 `GENERATED ALWAYS AS IDENTITY`
이고 그 시퀀스는 열에 소유되어 `serial` 과 달리 별도 USAGE 를 요구하지 않는다. 권한을 넓히지 않는
쪽을 골랐고, 그 전제는 문장이 아니라 **실측**이 든다(`AccessPrivilegeTest` 가 앱 역할로 production
INSERT 문장을 직접 쏘아 성공을 잰다).

**(5) 남용 상한의 수가 정책이 아니라 설정이다**(수정 라운드 1, cr H-1) — **main 반영됨**(PR #190, main `6ea304dd`). 계약은 「시간당 10건」을
적었는데, 키가 `remoteAddr` 이라 프록시 뒤에서 버킷이 하나이고 10 은 **열 번의 싸구려 요청으로
한 시간을 닫는다**. 수를 `bidvector.signup.attempts-per-hour`(기본 30)로 빼 운영자가 올릴 수 있게
하고, 거부가 로그 한 줄을 남긴다. 계약 문면은 PR #190 이 이미 고쳤다.

**(6) 비밀번호 상한이 바이트가 아니라 글자 수이고 값이 1024 다**(수정 라운드 1, cr M-1) — **main 반영됨**(PR #190, main `6ea304dd`). 계약의
「bcrypt 72바이트 상한 초과는 거부」는 **이 저장소에서 거짓**이다 — 해시 어댑터가 긴 입력을
SHA-512 로 접지 자르지 않는다. 상한은 입력 위생 경계로만 남는다. 계약 문면은 PR #190 이 이미 고쳤다.

**(7) 15A 가 넘긴 축을 이 slice 가 닫는다 — 머리는 run 당 계정 상한이다**(팀장 결정 2026-11-22,
`OPEN-M15-EVAL-COST-PER-ACCOUNT`). 계약에 없던 축이고, **가입을 여는 slice 가 닫아야 하는 이유**가
그 OPEN 의 문면이다 — 가입이 열리면 계정 수가 운영자의 손을 떠나고, 평가 비용은 계정 수에 선형이다.

- **전역 상한** `bidvector.evaluation.max-accounts-per-run`(기본 **50**). 계정을 **id 순으로** 잘라
  **운영 계정(가장 작은 id)이 언제나 먼저**다 — 정렬을 질의에 기대지 않는 이유는 채널 보유자 선택과
  같다. 상한 밖은 **버리는 것이 아니라 미루는 것**이라 마침 줄의 `deferred=` 로 세고 **종료 코드를
  바꾸지 않는다**(`skipped` 와 같은 자격, `failed` 와 다른 자격).
- **연속 실패 중단** `bidvector.evaluation.max-consecutive-account-failures`(기본 **3**). 끊기지 않는
  실패는 그 계정들의 일이 아니라 체계가 내려간 신호이고, 그때 남은 계정을 계속 도는 것은 K × 타임아웃을
  치르는 일이다(15A 가 계정별 격리를 넣으며 생긴 대가, 그 OPEN 의 둘째 문단). 남은 수는 같은
  `deferred` 에 들어가고, **왜 멈췄는지는 따로 한 줄**이 말한다
  (`evaluation-commit stopped cause=consecutive-account-failures`). 그 run 은 `failed>0` 이라 종료
  코드가 이미 `INCOMPLETE`(2) 다 — 중단 자체가 새 종료 코드를 만들지 않는다.
- **둘 다 기본값을 갖는다** — `candidate-cap` 과 반대다. 상한은 「주지 않으면 무제한」이 되면 안 되는
  값이고, 기본값 자리를 비우면 미설정이 곧 무제한이 된다. 대신 **1 미만은 기동 실패**다(0 은 상한이
  아니라 「아무도 평가하지 않는다」다).
- **오늘의 실비는 0 에 가깝다** — 출하 배선의 `MlAnalysisPort` 가 `UnavailableMlAnalysis` 고정이라
  (D-6A3-2, 그 사실은 조립 게이트가 잠근다) 계정당 ML 호출 비용이 서지 않는다. 기본값 **50** 은 측정이
  아니라 **ML 배선이 서는 날을 위한 자리**이고, 그 근거를 적어 두지 않으면 수가 실측처럼 읽힌다.

**(8) 15A 가 넘긴 나머지 셋도 같은 커밋에서 닫는다.** ⓐ 채널 보유자 선택을 `channelHolderOf` 값 함수로
빼고 `[2, 1]` 청중 한 건으로 잰다(앞 판은 청중이 늘 오름차순이라 `minByOrNull` → `firstOrNull` 변이가
초록이었다). ⓒ 계정별 `catch` 가 `InterruptedException` 의 플래그를 되세운다 — 삼키면 그 스레드의 다음
blocking 호출이 인터럽트를 보지 못한다. ⓓ `OutboxNotificationRequestPortTest` 가 멱등 키 형식을 축어로
고정한다. **ⓑ 는 닫지 않는다**(불변식을 공장 쪽에도 두는 축) — 러너 밖으로 옮기면 이 slice 가 만지지
않는 조립이 함께 움직인다.

**(9) runbook §3.4·§3.5 의 종료 코드 어휘를 고친다**(15A 알려진 제한 ⑦ — 그 slice 가 15B 로 넘긴 축).
`FAILED`(1) 은 **청중을 못 읽은 run 하나**이고 계정 하나의 실패는 `INCOMPLETE`(2) + `account-failed
cause=` 줄이다. 마침 줄의 계정 계수 **넷**(`accounts`·`skipped`·`failed`·`deferred`)과 요청 계수
`suppressed` 가 종료 코드에 각각 어떻게 닿는지를 표로 세웠다. §1.2·§1.4 에 설정 키 둘을, §6 의 「단일
운영자 전용」 한 줄에 M15 가 그것을 깼다는 사실을 적었다 — **그 줄을 이 slice 가 거짓으로 만든다.**
§7 이 요구하는 §3.4 등식(표의 종료 코드 == `EvaluationCommitExitCode`)은 재측정했다(commands.md).

**(10) 상한 밖은 회전한다 — 거동 변경**(수정 라운드 4, r3 리뷰 M-1·F-1 · 팀장 결정 2026-11-22).
라운드 3 의 `take(n)` 고정 절단은 **같은 계정을 매 run 미뤘다**: 상한이 오르거나 더 작은 id 가
사라질 때까지 `n` 번째 뒤의 가입자는 **어느 run 에서도** 평가되지 않았고, 종료 코드는 0 이라
cron 은 성공을 적었다. 줄이 그것을 「미룸」이라 부른 것이 결함이다.

- **보유자(가장 작은 id)는 매일 든다** — 전역 발송 채널이 그 계정에 묶여 있어 그 자리를 비울 수 없다.
- 남은 `n-1` 칸이 나머지 `K-1` 계정 위를 **영업일마다 `n-1` 칸씩** 미끄러진다
  (`start = floorMod(영업일 × (n-1), K-1)`). 연속한 `ceil((K-1)/(n-1))` 영업일이면 전원이 한 번씩 돈다.
- 축은 **KST 달력일**이고 **순수 함수**다 — 같은 날 다시 돌리면 같은 집합이다(영속 커서를 두지
  않는다: 그 상태가 있으면 되돌림과 재실행이 서로 다른 판을 만든다). 그래서 runbook 의 처방에서
  「더 자주 돌린다」를 **뺐다** — 그 수단은 이 원천에 효과가 0 이다.
- `n = 1` 이면 칸이 0 이라 보유자만 돈다(공평성 등식의 분모가 0 인 자리, KDoc 이 적는다).

같은 라운드에서 `SignUpOutcome.SessionUnavailable` 의 **생사도 쟀다** — 죽은 분기가 **아니다**.
use case 층(`AccountSignUpTest`)·HTTP 층(503 매핑·쿠키 0, 신설)·화면(`SignUpScreen.test.tsx`)
셋이 같은 코드로 그 갈래를 지난다. 실물에서 서는 순간은 가입이 행을 세운 **뒤** 세션 확정의 조건부
UPDATE 가 0행을 고치는 때다(운영자 `rotate` · 행 소멸).

## 갱신 이력

- 2026-11-21 초판(착수).
- 2026-11-21 전건 acceptance 뒤 계약 갱신 (2)~(4) 와 in_scope 둘 등재.
- 2026-11-21 수정 라운드 1 — 계약 갱신 (5)·(6), in_scope 다섯 등재(누락 셋 + 이 라운드가 더한 둘).
- 2026-11-21 수정 라운드 2 — in_scope 셋 등재(누락 하나 + 신설 둘), 계약 갱신 (5)·(6) 의 main 반영 표시.
- 2026-11-22 15A 머지(main `d2c0513d`) 병합 후 라운드 3 — 계약 갱신 (7)~(9), in_scope 여덟 등재
  (평가 러너 축 여섯 + 멱등 키 test + runbook), 알려진 제한 ④ 등재, `web/src/**/*.test.ts`
  누락 등재(`routes.test.ts` 가 `.tsx` 글롭 밖이었다), rollback 복원 원천을 병합된 main 으로.
- 2026-11-22 수정 라운드 4 — 계약 갱신 (10) **상한 밖 회전**(거동 변경), `OPEN-M15-EVAL-COST-PER-ACCOUNT`
  를 OPEN 장부에 등재, 알려진 제한 ④ 를 회전 기준으로 고쳐 씀, in_scope 에 `docs/discovery/capability-map.md`·
  `openapi/*`·`web/src/paths.ts` 는 이미 있고 신설 경로 0.
