# M15 / 15B — acceptance 실측

base `99f2fb25` · 실측 HEAD `48a88a17`(이 slice 의 마지막 산출물 커밋). 아래는 CI 의 job 명령
그대로이고, 결과는 **한 줄**로만 적는다.

## Kotlin `check` job

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check` | **exit 1 — 빨강 하나**(아래 「알려진 빨강」). 그 밖의 게이트·test 는 전부 통과 |

빨강이 그 하나뿐이라는 근거: 같은 실행의 `FAILED` 줄이 `MigrationOrderTest` ·
`:adapters:migrationOrderGate` · 그 둘을 품은 `:build-logic:test` 셋이고 전부 같은 원인이다.

중간 확인(전건 전에 좁혀 돌린 것):

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon testClasses` | exit 0 |
| `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.access.*'` | exit 0 |
| `./gradlew --no-daemon :app:test --tests '…AccountSignUpEndpointTest' 외 셋` | exit 0 |
| `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.access.*' 외 하나` | exit 0 (Testcontainers 실 PostgreSQL) |

## web job

| step | 명령 | 결과 |
| --- | --- | --- |
| W-1 | `npm ci` | exit 0 |
| W-2 | `npm run lint` | exit 0 |
| W-3 | `npm run typecheck` | exit 0 |
| W-4 | `npm run gate:src-tree` | exit 0 |
| W-5 | `npm run api:types:check` | exit 0 — 생성 타입이 문서와 바이트까지 같다 |
| W-6 | `npm run test` | exit 0 — 75 파일 · 2009 test |
| W-7 | `npm run gate:tests` | exit 0 — 등록부 기준 skipped/todo 0 |
| W-8 | `npm run build` | exit 0 |
| W-9 | `npm run gate:boundary-canary` | exit 0 |
| W-10 | `npx playwright install chromium` | exit 0 |
| W-11 | `npm run test:e2e` | exit 0 — 39 passed |
| W-12 | `./gradlew --no-daemon :app:bootJar` | exit 0 |
| W-13 | `npm run test:e2e:journey` | exit 0 — 31 passed(가입 여정 셋 포함) |

## 알려진 빨강 — `migrationOrderGate`

등식은 `읽어낸 version 다중집합 == (1..max ∖ 은퇴{10,11})` 이다. 이 브랜치의 최대는 **29** 이고
main 에는 아직 **28** 이 없다 — 15A 가 그 번호를 쓴다(그 레인 확인, 파일명까지). 결번 하나가
남아 등식이 깨진다.

**은퇴 목록에 28 을 넣지 않는다.** `config/quality/migration-policy.properties` 가 그 자리를
「영구히 비워 둔 자리이지 머지 순서의 회피로가 아니다」로 못박고, 처방은 **뒤에 오는 레인이
기다리는 것**이라고 적는다. 계약의 머지 순서도 15A → 15B 다. 15A 가 main 에 들어오면 이 게이트는
다른 조치 없이 초록이 된다.

## 착수 실측에서 드러난 것 — 계약 갱신 (1)·(2)

**(1) V29 가 DEFAULT 셋을 함께 준다.** 계약은 GRANT 만 적었는데, 열 둘만 적는 INSERT 는 V20 의
NOT NULL 세 열에서 떨어진다. DEFAULT 는 권한을 넓히는 것보다 **좁다** — 앱은 그 열에 값을 고를 수
없다. 그 변화가 `CleanMigrationColumnTest` 의 열 장부 세 줄(`hasDefault`)에 선다.

**(2) 열 권한 축은 `CleanMigrationPrivilegeTest` 밖에 선다.** 그 class 의 행렬은
`has_table_privilege` 라 **열 단위 부여를 세지 않는다** — V29 뒤에도 `account.insert` 는 `false`
이고 그 사실을 주석이 적는다. 「어느 열이 열렸는가」는 `AccountColumnPrivilegeTest`(신설)가
집합 등식으로 재고, 거동이 실제로 뒤집힌 자리(`AccessPrivilegeTest` 의 계정 INSERT)가 RED 로
먼저 섰다. 그 분담은 `BidCaseColumnPrivilegeTest` 의 선례와 같고, 한 파일에 합치면 크기 게이트
(파일 500줄)를 넘는다.

**(3) V29 도 잠금 상한을 선언한다.** 기존 표(`account`)를 `ALTER` 하므로 V18 이 세운 관례
모집단에 든다 — `MigrationLockTimeoutTest` 의 비공허성 줄이 그 사실을 래칫으로 든다.

## 개인정보·비밀값

- 응답·로그·e2e 산출물에 비밀 문구 원문이 실리지 않는 것을 세 층이 잰다: use case 층(저장되는
  것은 해시뿐) · HTTP 층(응답 전문에 원문 0) · 실 DB 층(열 덤프에 원문 0).
- 누출 패턴 스캔은 참조형으로 돈다 — `grep -rniE -f config/quality/leak-patterns.txt <경로>`.
  이 evidence 에 스캔 어휘를 축어로 적지 않는다.
- e2e 가 쓰는 자격 값은 하네스가 실행마다 짓는 일회용이고 DB 는 버려진다.

## 알려진 제한

① 남용 상한의 키가 `remoteAddr` 다 — 역방향 프록시 뒤에서는 상한이 사실상 전역이 된다.
  `X-Forwarded-For` 를 믿지 않는 쪽을 골랐다(신뢰 프록시 설정 없이 읽으면 상한이 0 이 된다).
② 창이 프로세스 메모리에 있다 — 재기동이면 초기화되고 인스턴스가 둘이면 각자 센다.
③ 가입과 세션 발급이 한 트랜잭션이 아니다 — 세션 발급이 실패하면 계정만 남고, 화면이 그 사람을
  로그인으로 안내한다(권한이 새는 방향이 아니다).
④ 이 slice 는 15A 의 계정별 분리를 전제한다. 15A 전에는 가입자가 운영 계정의 단일 행 전략·프로필을
  본다 — 그래서 머지 순서가 고정이다.
