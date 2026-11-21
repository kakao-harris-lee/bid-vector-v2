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
  - adapters/src/test/kotlin/bidvector/adapters/access/                    # 열 권한 · 가입 저장소 test
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt  # 축9 주석 + 열 권한 축
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
  - app/src/main/kotlin/bidvector/app/BidVectorApplication.kt              # 가입 상한 빈(app.http 를 아는 유일한 자리)
  - app/src/main/kotlin/bidvector/app/access/AccountCommandRunner.kt       # create 거부 어휘 한 줄
  - app/src/test/kotlin/bidvector/app/http/                                # 집합 등식 둘 · 가입 endpoint · 상한
  - app/src/test/kotlin/bidvector/app/access/AccountCommandRunnerTest.kt   # 완화된 create 의 로그·종료 코드
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
  - web/src/**/*.test.tsx                                                  # 위 화면·셸·라우트의 짝 test
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

## 갱신 이력

- 2026-11-21 초판(착수).
