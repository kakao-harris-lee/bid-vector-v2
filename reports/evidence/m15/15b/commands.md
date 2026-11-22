# M15 / 15B — acceptance 실측

base `99f2fb25` · 실측 자리는 라운드마다 움직인다 — **그 SHA 의 정본은 `rollback.md`** 다(여기 적으면 라운드마다 낡는다). 아래는 CI 의 job 명령
그대로이고, 결과는 **한 줄**로만 적는다.

## Kotlin `check` job

**`--continue` 로 잰다**(verifier r1 BLOCKER-1). Gradle 은 첫 실패 뒤 task 스케줄링을 멈추므로,
알려진 빨강(`migrationOrderGate`)이 **그 뒤의 게이트를 전부 가린다** — 앞 판의 「그 밖은 전부
통과」는 근거 없는 문장이었고 실제로 아홉이 숨어 있었다. 알려진 빨강이 있는 동안 acceptance 는
`--continue` 로만 잰다.

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check --continue` | **exit 0 — 실패 task 0**(라운드 5, 32분 48초). 라운드 3·4 의 같은 명령도 exit 0 이었다 — 아래 「낡은 산출물」을 먼저 읽는다 |

수정 라운드 1 이 닫은 숨은 실패 아홉(모두 이 slice 가 낸 것): ktlint 넷(import 순서 둘 ·
체인 줄바꿈 · `when` 중괄호 · KDoc 겹침 둘) · `sizeGate`(512줄) · `cpdCheck`(controller 둘의
11줄 중복) · `detekt`(ReturnCount) · `:app:test`(의존 허용 목록 넷 · 상수 시간 비교 구조) ·
`gateExecutionGate`(앞 것의 파급).

수정 라운드 3 이 닫은 실패 여덟(15A 머지와 평가 상한이 낸 것): `:adapters:sizeGate`(병합
합집합이 `CleanMigrationColumnTest` 를 501줄로 밀었다) · `:app:sizeGate`(러너 `run` 69줄) ·
`:app:detekt`(파일당 함수 12 · 인지 복잡도 22) · ktlint 둘(main 셋 · test 넷) ·
`:app:jarContentGate`·`:app:packageOwnershipGate`(**둘 다 같은 원인** — `sortedBy` 가
`Comparisons.kt` 출처의 합성 비교자 클래스를 산출물에 남긴다. 명시 `Comparator` 로 바꿨고,
선례는 `bidvector.adapters.snapshot` 의 행 정렬이다).

## 낡은 산출물이 만든 가짜 RED (라운드 3)

같은 라운드의 **첫** 전건은 `:app:test` 10건과 `gateExecutionGate` 를 붉혔고, 그 10건은
이 slice 가 만지지 않은 제안·전략 경로였다. 사유는 결함이 아니라 **낡은 산출물**이다 —
15A 가 `AccountId` 를 다른 패키지로 옮겼는데 공유 Gradle build cache·증분 컴파일이
`HttpTestApplication` 의 SAM 람다를 다시 내지 않아, 호출이 `AbstractMethodError`(옛 서명)로
떨어지고 HTTP 층이 그것을 500 으로 옮겼다. 판독은 **응답 본문**(배열이 아니라 오류 객체)이
아니라 그 예외였다 — 일회용 probe 로 use case 를 직접 불러 잡았고, probe 는 같은 자리에서
지웠다.

처분: 모듈별 `build/classes`·`build/kotlin` 을 지우고 전건을 다시 돌렸다(위 표의 exit 0).
그 run 에서 `:app:compileTestKotlin` 은 **cache 가 아니라 실행**이다(로그의 task 상태).
CI 는 빈 cache 로 돌아 이 상태에 닿지 않는다(main `d2c0513d` 초록). 같은 서명의 오류
(`AbstractMethodError`·`NoSuchMethod`·`ClassNotFound`)를 **만지지 않은 타입**에서 보면 결함으로
읽기 전에 이 처분을 먼저 한다(15C 레인이 같은 날 같은 자리에서 독립으로 재현했다 — 그쪽은
`--no-build-cache` 까지 썼다).

중간 확인(전건 전에 좁혀 돌린 것):

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon testClasses` | exit 0 |
| `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.access.*'` | exit 0 |
| `./gradlew --no-daemon :app:test --tests '…AccountSignUpEndpointTest' 외 셋` | exit 0 |
| `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.access.*' 외 하나` | exit 0 (Testcontainers 실 PostgreSQL) |

## web job

**W-1~W-9 의 실측 자리는 라운드 4 의 산출물 HEAD 다**(앞 판은 r1 의 수치를 그대로 두어 `test`
건수가 어긋나 있었다 — r3 CR L-4). **W-10~W-13 은 PR 의 `web` 체크가 정본**이다(playwright
브라우저와 출하 jar 가 붙는 구간이라 이 레인에서 다시 재지 않는다).

| step | 명령 | 결과 |
| --- | --- | --- |
| W-1 | `npm ci` | exit 0 |
| W-2 | `npm run lint` | exit 0 |
| W-3 | `npm run typecheck` | exit 0 |
| W-4 | `npm run gate:src-tree` | exit 0 |
| W-5 | `npm run api:types:check` | exit 0 — 생성 타입이 문서와 바이트까지 같다(201058 bytes). OpenAPI 주석 이동(CR L-7)이 생성물을 **바꾸지 않는다**는 측정이기도 하다 |
| W-6 | `npm run test` | exit 0 — **75 파일 · 2014 test** |
| W-7 | `npm run gate:tests` | exit 0 — 등록부 기준 skipped/todo 0 |
| W-8 | `npm run build` | exit 0 |
| W-9 | `npm run gate:boundary-canary` | exit 0 |
| W-10 | `npx playwright install chromium` | PR `web` 체크 |
| W-11 | `npm run test:e2e` | PR `web` 체크 |
| W-12 | `./gradlew --no-daemon :app:bootJar` | PR `web` 체크 |
| W-13 | `npm run test:e2e:journey` | PR `web` 체크(가입 여정 셋 포함) |

## 닫힌 빨강 — `migrationOrderGate`

등식은 `읽어낸 version 다중집합 == (1..max ∖ 은퇴{10,11})` 이다. 라운드 1·2 에서 이 브랜치의
최대는 **29** 인데 main 에 **28** 이 없어 결번 하나로 등식이 깨졌다. **은퇴 목록에 28 을 넣지
않았다** — `config/quality/migration-policy.properties` 가 그 자리를 「영구히 비워 둔 자리이지
머지 순서의 회피로가 아니다」로 못박고, 처방은 **뒤에 오는 레인이 기다리는 것**이다.
15A(V28)가 main 에 들어오고 이 브랜치가 그것을 병합한 라운드 3 에서, 다른 조치 없이 초록이
됐다(V27 · V28 · V29 연속).

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

## 수정 라운드 3 — 거동이 바뀐 자리

15A 가 15B 로 넘긴 축을 닫는다(그 slice 의 알려진 제한 6~9).

| 넘어온 축 | 처분 |
| --- | --- |
| run 비용이 계정 수에 선형(`OPEN-M15-EVAL-COST-PER-ACCOUNT`) | run 당 계정 상한 `bidvector.evaluation.max-accounts-per-run`(기본 **50**). **id 순**으로 잘라 운영 계정이 언제나 먼저이고, 상한 밖은 마침 줄의 `deferred=` 다 — 실패가 아니라 **미룬 것**이라 종료 코드를 바꾸지 않는다. test 셋: 상한 경계 · 넉넉한 상한의 음성 대조 · 상한 1 에서 `[3,1,2]` 가 1 을 고른다 |
| 실패 비용도 계정 수에 선형(같은 OPEN, 둘째 문단) | 연속 실패 상한 `bidvector.evaluation.max-consecutive-account-failures`(기본 **3**). 닿으면 남은 계정을 `deferred` 로 세고 `evaluation-commit stopped cause=consecutive-account-failures` 한 줄을 남긴다. test 둘: 연속 셋에서 멈추고 시도가 `[1,2,3]` 이다 · **흩어진 실패 셋은 멈추지 않는다** |
| runbook 의 종료 코드 어휘가 낡았다 | §3.4·§3.5 를 고쳤다 — `FAILED`(1) 은 청중을 못 읽은 run 하나, 계정 하나의 실패는 `INCOMPLETE`(2) + `account-failed cause=`. 계정 계수 **넷**과 `suppressed` 가 종료 코드에 닿는 방식을 표로 세우고, §1.2·§1.4 에 설정 키 둘, §6 에 「단일 운영자 전용」이 M15 로 깨진 사실을 적었다 |
| 보유자 선택이 측정되지 않았다(LOW ⓐ) | `channelHolderOf` 값 함수 + `[2,1]`·`[3,1,2]` 단언. `minByOrNull` → `firstOrNull` 변이가 붉는다 |
| 인터럽트 플래그를 잃는다(LOW ⓒ) | 계정별 `catch` 가 `InterruptedException` 에서 플래그를 되세운다 |
| 멱등 키 형식이 안 잠겼다(LOW ⓓ) | `OutboxNotificationRequestPortTest` 가 `notification-requested-<계정>-<공고>-<차수>` 를 축어로 단언한다 |

**ⓑ 는 닫지 않았다**(보유자 불변식을 공장 쪽에도 두는 축) — 러너 밖으로 옮기면 이 slice 가
만지지 않는 조립이 함께 움직인다.

## 수정 라운드 4 — 거동이 바뀐 자리

| 지적 | 처분 |
| --- | --- |
| **`deferred` 가 「미룸」이 아니다**(CR M-1 · v F-1) — `take(n)` 고정 절단은 상한 밖 계정을 **영원히** 뺀다 | **거동으로 고쳤다**(팀장 결정). `accountsForRun` 이 보유자를 고정하고 나머지 `n-1` 칸을 **KST 달력일마다 `n-1` 칸씩** 미끄러뜨린다 — 연속한 `ceil((K-1)/(n-1))` 달력일이면 전원이 한 번씩 돈다. 축은 날짜의 **순수 함수**라 같은 날 재실행은 같은 집합이고(영속 커서 없음), 그래서 runbook 의 처방에서 「더 자주 돌린다」를 **뺐다** |
| 멈춤 줄 `remaining` 의 뜻(CR L-2) | **run 전체의 남은 수**로 바꿨다 — 마침 줄의 `deferred` 와 같은 값이다. 두 원천이 함께 서는 판을 test 가 든다 |
| 설정 KDoc 이 r2 가 지운 축출을 설명한다(CR M-3) | fail-closed 로 고쳤다 — 「이 수를 낮추면 **새 주소의 가입이 먼저 막힌다**」 |
| `OPEN-M15-EVAL-COST-PER-ACCOUNT` 가 장부에 없다(CR M-2) | `capability-map.md` OPEN 표에 행 하나(닫은 것 둘 · 남는 것 셋). `OPEN-M15-SIGNUP-HARDENING` ④ 에 fail-closed 가 만든 **전역 잠금 문턱**을 한 줄(CR L-3) |
| cause 값에 공백(CR L-1) | `window-limit` · `tracked-address-capacity` |
| OpenAPI 고아 주석(CR L-7) | M7/7D 줄을 `SignInRequest` 위로 되돌렸다(생성 타입 바이트 무변화 — W-5) |
| `paths.ts` 의 「한 자리」(CR L-5) | 주장을 「화면 둘과 라우트 표」로 좁히고 남은 리터럴 둘(`AppShell`·`WriteFailure`)을 적었다 |
| in_scope 의 안 만지는 파일(CR L-6) | `AppHttpDependencyGateTest.kt` 에 「변경 없음」 주석 |
| 평가 상한 기본값이 무측정(v L-3) | `EvaluationCommitWiringTest` 셋 — 기본 50/3 · 접두 바인딩 · 1 미만 거부 |
| 인터럽트 복원이 무측정(v L-2) | 러너 test 하나(아래 변이 M-B RED) |
| 건너뜀이 연속을 끊는지 미고정(v L-1) | **끊는다**로 결정하고 KDoc + test 로 고정했다 — 「전략이 없다」는 전략 **읽기가 성공했다**는 뜻이라 체계가 살아 있다는 증거다 |
| 네 계수 보존 등식 무측정(CR L-8) | 상한·멈춤·건너뜀 test 에 `accounts + skipped + failed + deferred == 청중` 한 줄씩 |
| web 표가 낡았다(CR L-4) | W-1~W-9 를 라운드 4 HEAD 에서 재측(그 자리에서 `test` 가 2009 → **2014**). W-10~W-13 은 PR 체크가 정본임을 표에 적었다 |

### 열린 질문 ① — `SignUpOutcome.SessionUnavailable` 은 **죽은 분기가 아니다**

두 라운드 미측정이던 축을 쟀다. 출하 경로는 **둘**이다(r4 CR L-3 — 앞 판은 하나만 적었다) —
세션 발급의 소진 `when` 이 둘 다 `SessionUnavailable` 로 접는다:

- **`CredentialMoved`** — 가입이 행을 세운 **뒤** 조건부 UPDATE 가 0행을 고친다(운영자 `rotate` ·
  행 소멸).
- **`Contended`** — 커넥션 대기 상한 초과(확정 어댑터의 첫 단계). **부하가 걸리는 날 실제로 서는
  쪽은 이쪽**이고, 앞 판의 evidence 는 이 절반을 빠뜨렸다.

세 층이 같은 코드로 그 갈래를 지난다 — use case(`AccountSignUpTest`) · **HTTP 503 매핑과 「쿠키를
내지 않는다」**(신설 endpoint test) · 화면 문면(`SignUpScreen.test.tsx`). 그래서 503 매핑도 화면
안내도 OpenAPI 의 그 절도 살아 있다.

### 변이 실측 (라운드 4, 표적 `EvaluationCommitRunnerTest`)

변이마다 **바꿔치우고**(더하기 아님) 재고, 되돌린 뒤 `git status --porcelain` 이 빈 출력인 것을
확인했다.

| # | 변이 | 결과 | 죽인 test |
| --- | --- | --- | --- |
| M-A | 회전 오프셋을 `0` 고정 | **RED** | 연속한 KST 달력일이면 모든 계정이 한 번씩 돈다 — 보유자는 매일 든다 |
| M-B | 인터럽트 플래그 복원 줄 제거 | **RED** | 인터럽트 예외는 플래그를 되세운다 |
| M-C | 연속 reset 을 평가 성공 갈래로만 | **RED** | 건너뜀이 연속 실패를 끊는다 |
| M-D | 멈춤 줄 `remaining` 을 창 안쪽만 세도록 | **RED** | 상한 밖과 멈춤이 함께 서면 두 줄의 수가 같다 |

## 수정 라운드 5 — 거동이 바뀐 자리

| 지적 | 처분 |
| --- | --- |
| **회전 축을 「영업일」이라 부른다**(CR M-1 · v F-1) — 평일만 도는 timer 는 **영구 기아**를 만든다 | 축은 그대로 두고(코드 0줄) **문면을 「KST 달력일」로** 바로잡았다(KDoc 아홉 자리 · runbook · OPEN 행 · evidence). 보증이 **달력일마다 한 번 이상 도는 cron** 을 전제한다는 것과 **일회성 결행(위상 이동) ↔ 주기적 결행(영구 기아)**의 구별을 runbook §3.4 인용 블록·`accountsForRun` KDoc·OPEN ⓐ 에 적었다. 처방된 timer 는 매 달력일 돈다(`m7-7f-production.md` §13.3) |
| **인터럽트에서 순회가 계속된다**(CR L-4) — 종료 신호 하나가 `failed=1,2,3…` 으로 불어난다 | **거동으로 고쳤다** — 그 자리에서 멈추고 남은 계정을 `deferred` 로 센다. 멈춤 줄이 사유를 가른다(`cause=interrupted`), 줄의 모양은 하나다. 종료 코드는 여전히 `INCOMPLETE` |
| `accountsForRun` 을 **직접 재는 test 가 없다**(CR L-2 · v F-2) | `EvaluationCommitRotationTest` 신설(같은 커밋에서 게이트 등록부 등재) — `(K,n,날짜) → 뽑힌 계정` 표(경계 넷 + 음수 날짜), 「보유자는 늘 첫 원소·중복 0·크기 `min(n,K)`」를 −40..40 일 전수로, 「한 주기면 전원」을 **모든 시작 위상**에서 |
| KST 경계가 안 잠겼다(v F-3) | 23:59 KST 와 00:01 KST 두 run 으로 잠갔다 |
| 마침 줄 KDoc 의 거짓 문장(CR L-1) | 「상한이 크고 **멈춤도 없으면**」으로 — `deferred` 의 원천이 둘이다 |
| `SessionUnavailable` 경로 절반 누락(CR L-3) | 위 「열린 질문 ①」에 `CredentialMoved` · `Contended` 둘 다 적었다 |
| 번호 ⑤⑦⑥(CR L-5) · `LoginScreen.tsx` 빈 줄(CR L-6) · `require` 정본(OQ ③) · 본문 선파싱 미등재(OQ ①) | 각각 정정 · 한 줄 삭제 · KDoc 한 줄 · `OPEN-M15-SIGNUP-HARDENING` ③ 에 한 줄 |

### 변이 실측 (라운드 5, 표적 `EvaluationCommitRotationTest` · `EvaluationCommitRunnerTest`)

앞 라운드에서 **살아남았던 둘**(법 한 칸 어긋남 · UTC 구역)을 포함해 넷 전부 RED 다.

| # | 변이 | 결과 | 죽인 test |
| --- | --- | --- | --- |
| M-E | 법을 `span-1` 로 | **RED**(r4 에서는 초록) | 회전 창 표 · 어느 날 시작해도 한 주기면 전원 |
| M-F | `others.size <= slots` 를 `<` 로 | **RED** | 회전 창 표(나머지 없는 자리에서 법이 0 이 된다) |
| M-G | 회전 구역 `Asia/Seoul` → `UTC` | **RED**(r4 에서는 초록) | 회전 경계는 KST 자정이다 |
| M-H | 인터럽트에서 멈추지 않는다 | **RED** | 인터럽트 예외는 플래그를 되세우고 run 을 멈춘다 |

## 알려진 제한

① 남용 상한의 키가 `remoteAddr` 다 — 역방향 프록시 뒤에서는 상한이 **사실상 전역**이고, 형식이
  맞는 싸구려 요청 N건이면 가입 경로가 창 하나 동안 닫힌다. `X-Forwarded-For` 를 믿지 않는 쪽을
  골랐다(신뢰 프록시 설정 없이 읽으면 상한이 0 이 된다). 손잡이는 둘 — 설정 값과 거부 로그.
② 창이 프로세스 메모리에 있다 — 재기동이면 초기화되고 인스턴스가 둘이면 각자 센다.
③ 가입과 세션 발급이 한 트랜잭션이 아니다 — 세션 발급이 실패하면 계정만 남고, 화면이 그 사람을
  로그인으로 안내한다(권한이 새는 방향이 아니다).
④ 이 slice 는 15A 의 계정별 분리를 전제한다. 15A 전에는 가입자가 운영 계정의 단일 행 전략·프로필을
  본다 — 그래서 머지 순서가 고정이었다(15A 는 `d2c0513d` 로 main 에 들었고 이 브랜치가 병합했다).
⑤ `deferred` 를 읽는 경보가 없다 — 회전이 「영원히 빠지는 계정」은 없앴지만, 상한을 넘은 날 그
  계정의 평가는 **최대 `ceil((K-1)/(n-1))` 달력일 늦는다**. 그 지연을 보는 자리가 없고 종료 코드는
  0 이라 cron 은 성공을 적는다. 닫는 자리는 경보 쪽이지 종료 코드가 아니다(15A 알려진 제한 ⑧ 과
  같은 자리).
⑥ 오늘의 run 실비는 0 에 가깝다 — 출하 `MlAnalysisPort` 가 `UnavailableMlAnalysis` 고정이다
  (D-6A3-2). 기본값 50 은 **측정이 아니라** ML 배선이 서는 날을 위한 자리다.
⑦ **공평성이 「매 KST 달력일 도는 cron」을 전제한다**(라운드 5, r4 CR M-1 · v F-1). 시작 위치가
  `날짜 mod P` 에만 달려 있어 **주기적** 결행(평일만 도는 timer 등)은 잔여류가 덮지 못하는 창을
  만들고, 거기 든 계정은 **영구히** 평가되지 않는다. 일회성 결행은 위상만 옮긴다. 처방된 timer 는
  매 달력일 돈다(`m7-7f-production.md` §13.3). 코드로 닫는 교환(보폭 1 · 실행 순번 축)은
  `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ 가 든다.
⑧ 회전은 **「가장 오래 평가되지 않은 순」이 아니다** — 축이 날짜의 순수 함수라 계정 집합이 바뀌면
  창의 경계도 함께 움직인다. 영속 커서를 두지 않은 것은 결정이다(그 상태가 있으면 되돌림과
  재실행이 서로 다른 판을 만든다). 등재 자리는 위와 같다.
