# M15 / 15B — acceptance 실측

base `99f2fb25` · 실측 HEAD `48a88a17`(이 slice 의 마지막 산출물 커밋). 아래는 CI 의 job 명령
그대로이고, 결과는 **한 줄**로만 적는다.

## Kotlin `check` job

**`--continue` 로 잰다**(verifier r1 BLOCKER-1). Gradle 은 첫 실패 뒤 task 스케줄링을 멈추므로,
알려진 빨강(`migrationOrderGate`)이 **그 뒤의 게이트를 전부 가린다** — 앞 판의 「그 밖은 전부
통과」는 근거 없는 문장이었고 실제로 아홉이 숨어 있었다. 알려진 빨강이 있는 동안 acceptance 는
`--continue` 로만 잰다.

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check --continue` | **exit 1 — 실패 task 둘**: `:adapters:migrationOrderGate` · `:build-logic:test`(`MigrationOrderTest`). 둘 다 **같은 원인**(V28 부재)이고 그 밖의 task 는 전부 통과 |

수정 라운드 1 이 닫은 숨은 실패 아홉(모두 이 slice 가 낸 것): ktlint 넷(import 순서 둘 ·
체인 줄바꿈 · `when` 중괄호 · KDoc 겹침 둘) · `sizeGate`(512줄) · `cpdCheck`(controller 둘의
11줄 중복) · `detekt`(ReturnCount) · `:app:test`(의존 허용 목록 넷 · 상수 시간 비교 구조) ·
`gateExecutionGate`(앞 것의 파급).

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

## 수정 라운드 1 — 거동이 바뀐 자리

| 지적 | 처분 |
| --- | --- |
| 상한이 프록시 뒤에서 전역(cr H-1) | 수를 **설정 속성**으로 뺐다(`bidvector.signup.attempts-per-hour`, 기본 **30**). 429 가 **로그 한 줄**을 남긴다(사유·창 계수만 — 주소·아이디 0). 신뢰 프록시 + XFF 는 `OPEN-M15-SIGNUP-HARDENING` 이 받는다. 여정 레인은 그 값을 크게 받아 재실행 429 가 없다 |
| 상한이 「시도」를 센다는 성질이 test 로 안 잠겼다(v MEDIUM-1) | endpoint test 하나 추가 — **형식이 틀린 시도 열 건**(계정 0건)으로 칸을 채우고 열한 번째 유효 요청이 429. 상한을 use case 뒤로 옮기는 변이에서 이 test 가 붉는다 |
| 72바이트 근거가 거짓(cr M-1 · v LOW-2) | 상한을 **글자 수 1024**로 바꿨다. 이 저장소의 해시는 긴 입력을 SHA-512 로 접지 **자르지 않는다** — 한글이 영문과 같은 규칙 아래 선다. 문면 넷(값 객체·정책·OpenAPI·화면)을 함께 고쳤다. `fitsLength` 의 평문 `String` 사본도 사라졌다 |
| 오류 문장이 `<label>` 안(cr M-2) | `<label>` **밖**으로 냈고 `aria-invalid`·`aria-describedby` 를 **글자로** 적었다(펼친 속성은 터치 타깃 게이트의 이름 덮어쓰기 축에 걸린다) |
| 가입 화면에 로그인으로 돌아갈 자리가 없다(cr M-3) | 「로그인으로」 링크 하나. 503 안내 문면도 그 자리를 가리킨다 |
| `OPEN-M15-SIGNUP-HARDENING` 미등재(cr M-4) | `docs/discovery/capability-map.md` 의 OPEN 표에 등재(항목 일곱 — 409 가 아이디 존재를 알리는 축 포함) |
| e2e 가 계약의 「설정 저장」을 빠뜨렸다(v MEDIUM-2) | 여정 하나 추가 — 가입 → 시작하기 → **사업 정보 저장** → 오늘의 공고. 그 표(V22)는 이미 계정별이라 다른 여정의 전역 상태를 건드리지 않는다 |
| 가입 INSERT 의 `null` 이 두 사실을 접는다(cr LOW) | 접는 것이 **의도**임을 KDoc 이 그렇게 적도록 고쳤다(그 창에서는 재시도가 성공한다) |
| 상한 맵의 키 수 무제한(cr LOW) | `tracked-address-limit`(기본 10000) — 넘치면 가장 오래 전에 처음 본 주소부터 버린다 |
| in_scope 누락 셋(v LOW-1) | 등재. 이 라운드가 더한 자리도 함께 등재했다 |

## 알려진 제한

① 남용 상한의 키가 `remoteAddr` 다 — 역방향 프록시 뒤에서는 상한이 **사실상 전역**이고, 형식이
  맞는 싸구려 요청 N건이면 가입 경로가 창 하나 동안 닫힌다. `X-Forwarded-For` 를 믿지 않는 쪽을
  골랐다(신뢰 프록시 설정 없이 읽으면 상한이 0 이 된다). 손잡이는 둘 — 설정 값과 거부 로그.
② 창이 프로세스 메모리에 있다 — 재기동이면 초기화되고 인스턴스가 둘이면 각자 센다.
③ 가입과 세션 발급이 한 트랜잭션이 아니다 — 세션 발급이 실패하면 계정만 남고, 화면이 그 사람을
  로그인으로 안내한다(권한이 새는 방향이 아니다).
④ 이 slice 는 15A 의 계정별 분리를 전제한다. 15A 전에는 가입자가 운영 계정의 단일 행 전략·프로필을
  본다 — 그래서 머지 순서가 고정이다.
