# M15 / 15f — M15 후속 정리 — slice 계약

```yaml
milestone: M15
slice: 15f
base_sha: 5a2f98ba7402971facacdabbe98f8782b5268ab0   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  # ── A. 15B 이월 (PR #188 팀장 코멘트) ───────────────────────────────────
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerFixtures.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRotationTest.kt
  - docs/runbook/m6-6e-operations.md          # §3.4 멈춤 사유 둘
  - docs/discovery/capability-map.md          # OPEN-M15-EVAL-COST-PER-ACCOUNT ⓑ 보강
  - reports/evidence/m15/15b/commands.md      # M-A 행 괄호 정정
  # ── B. 15C 이월 (PR #189 팀장 코멘트) ───────────────────────────────────
  - web/e2e/journey/settings.spec.ts          # afterEach teardown · 양성 대조 둘
  - web/e2e/journey/privateValues.ts          # 예외를 value 하나로 좁힌다
  - web/e2e/journey/onboarding.spec.ts        # 빈 줄
  - web/src/display/profileDisplay.ts         # 주석 L-9
  - web/src/display/profileDisplay.test.ts    # 그 거동을 잠그는 단언
  - web/src/screens/ProfileDetailsCard.tsx    # aria-live
  - web/src/screens/SettingsScreen.test.tsx   # inputMode 잠금
  - reports/evidence/m15/15c/scope.md         # 제한 8 갱신
  - reports/evidence/m15/15c/commands.md      # 시각 · 라운드 절 · 비밀값 스캔 문면
  - reports/evidence/m15/15c/rollback.md      # 술어 공백
  # ── evidence ────────────────────────────────────────────────────────────
  - reports/evidence/m15/15f/scope.md
  - reports/evidence/m15/15f/commands.md
  - reports/evidence/m15/15f/rollback.md
out_of_scope:
  - 새 기능 · 새 마이그레이션 · 새 operation · 새 의존성
  - 계약 파일(openapi/**)과 생성 타입 — 이 slice 는 wire 를 바꾸지 않는다
  - 출하 거동 변경: 사업자번호 입력의 `trim()` 축(아래 처분표 L-9 ⓑ)
  - 평가 run 의 계수 넷(`accounts`·`skipped`·`failed`·`deferred`)과 종료 코드 사상
acceptance:
  # CI 워크플로 job 의 명령 그대로다. 부분 게이트는 안 돌린 것과 같다.
  - PR CI `check`        (.github/workflows/ci.yml)
  - PR CI `web`
  - PR CI `container`
  - PR CI `ml-engine`
  - PR CI `mutation`
rollback: in_scope 경로 한정 복원 — `rollback.md` 가 정본
```

## 이 slice 가 하는 일

15A·15B·15C 의 리뷰가 **차단하지 않고 넘긴 지적**을 한 PR 로 닫는다. 정본은 둘이다 —
PR #188 의 마지막 팀장 코멘트(15B 이월 넷)와 PR #189 의 「팀장 — 머지 결정」 코멘트
(15C 이월 다섯 묶음). 새 기능·새 마이그레이션은 없다.

**거동을 바꾸는 항목은 하나뿐이다**(A-1: 멈춤 사유 판정). 나머지는 test·문서·evidence 문면이다.

## A-1 — 멈춤 사유를 **스레드 인터럽트 플래그**에 건다

앞 판의 술어는 `failure is InterruptedException` 이라 **정확한 타입 하나**만 봤다. 출하
DB 경로의 인터럽트는 그 모양으로 오지 않는다 — HikariCP 는 연결 획득 중 인터럽트를 받으면
**플래그를 되세운 뒤** `SQLException(…, InterruptedException)` 으로 감싸 던진다. 그 예외에
대해 앞 판은 `null`(계속 돈다)을 냈고, 종료 신호 하나가 `failed=1,2,3…` 으로 불어났다.

**새 술어**: `Thread.currentThread().isInterrupted`. 두 모양이 같은 답을 낸다 —
raw `InterruptedException` 은 `noteAccountFailure` 가 플래그를 되세우고(그 호출이 술어보다
**먼저** 선다), 감싼 예외는 감싼 쪽이 이미 세워 둔다. 플래그가 선 스레드에서 계속 도는 것은
어느 사유로 왔든 의미가 없다 — **「누가 나를 껐다」는 예외 타입이 아니라 그 플래그다.**

왜 타입 열거를 늘리지 않는가: 번역된 인터럽트의 모양 집합이 열려 있다
(`ClosedByInterruptException` · `InterruptedIOException` · 그것을 감싼 Spring
`DataAccessException` · 드라이버마다 다른 사본). 열거는 그 집합을 따라잡지 못한다.

## 처분표 — 각 지적을 (a) 수정 (b) 근거 있는 거부 (c) OPEN 등재 중 하나로

### A. 15B 이월 (PR #188 팀장 코멘트)

| # | 지적 | 처분 | 무엇을 했나 |
| --- | --- | --- | --- |
| A-1 | [medium, v F-1] 감싼 인터럽트를 `stopCauseFor` 가 못 알아본다 | **(a) 수정** | 술어를 스레드 인터럽트 플래그에 걸었다. 회귀 test 「감싼 예외 + 플래그 선 상태」가 구판에서 RED 인 것을 실측했다(`commands.md`) |
| A-2 | [CR M-1 / v F-3] runbook §3.4 가 멈춤 사유 하나만 열거한다 | **(a) 수정** | §3.4 의 그 bullet 을 둘로 갈랐다 — `consecutive-account-failures` 는 그대로, `interrupted` 는 「고칠 자리가 계정이 아니다」로 처방이 다르다 |
| A-3 | [CR L-1 / v F-2] test 이름·KDoc 의 「영업일」 · `15b/commands.md` M-A 행 괄호 | **(a) 수정** | test 이름과 KDoc 을 「KST 달력일」로 바꾸고, 그 이름을 가리키는 M-A 행을 새 이름으로 갱신했다(괄호의 주장이 참이 됐다). **식별자 `businessDay` 는 바꾸지 않았다** — 아래 「A-3 가 바꾸지 않은 것」 |
| A-4a | [CR L-2] `stopCause` 기본값이 의미 있는 값이다 | **(a) 수정** | 멈춤을 **한 값**으로 만들었다 — 자리와 사유가 따로 살지 않는다 |
| A-4b | [CR L-5] `runnerFor` 의 `date`/`clock` 이중 인자 | **(a) 수정** | `date` 를 지우고 날짜 축을 재는 호출자가 `clock = clockAt(…)` 을 준다. 파라미터가 여섯으로 줄어 `@Suppress("LongParameterList")` 도 함께 사라졌다 |
| A-4c | [CR L-6] 집합 비교에 `shouldContainExactly` | **(a) 수정** | `shouldContainExactlyInAnyOrder` + `toSet()` 제거(`List` 를 그대로 넘긴다) |
| A-4d | [CR L-4] 인터럽트와 DB 장애가 마침 줄에서 같은 값이다 | **(c) OPEN 등재** | `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓑ 에 한 구절 — 「`failed=1 · cause=interrupted` 는 장애가 아니라 중단이다」 |
| A-4e | [CR L-3] 번역된 인터럽트의 상한 2 | **(a) 수정 — A-1 이 함께 닫았다** | 플래그 술어가 번역된 인터럽트도 첫 건에서 잡으므로 상한 2 가 1 이 된다. 그 사실이 `stopCauseFor` KDoc 에 있다 |
| A-4f | [열린 질문] 플래그가 선 채 `SpringApplication.exit` | **(b) 근거 있는 거부 — 조사 후 「그대로 둔다」** | 아래 「A-4f 조사」 |

### B. 15C 이월 (PR #189 팀장 코멘트)

| # | 지적 | 처분 | 무엇을 했나 |
| --- | --- | --- | --- |
| B-5 | [medium, CR M-6] 심은 값의 되돌림이 test 본문에 있다 | **(a) 수정** | 같은 파일의 `clearWatchCondition` 선례대로 `afterEach` 멱등 teardown 으로 옮겼다 — 출하 API 로 사업 정보 절을 되돌리고 **끝 상태**(`businessRegistrationNumber === null`)를 단언한다. 본문 ④ 는 사라진다 |
| B-6 | [medium, v F-1] 축어 축에 suite 안 양성 대조가 없다 | **(a) 수정** | 설정 ③ 의 양성 대조를 **두 쌍**으로 갈랐다 — 하이픈 꼴은 꼴 정규식만, 숫자 열 자리는 축어만 붉게 만든다. 두 쌍 모두 **문제 목록을 축어로** 단언하므로 한 축을 꺼도 다른 쌍이 가린다 |
| B-7 | [low, v F-2] `inputMode="tel"` 이 잠기지 않았다 | **(a) 수정** | 단위 test 하나(`numeric` 으로 되돌리는 변이가 RED) |
| B-8a | [CR L-9] 번호 `trim()` 의 화면↔API 관대함 차이 | **(a) 수정 — 주석을 사실로** | 주석이 「그대로 보낸다」와 「턴다」를 함께 주장했다. 화면의 두 층(안내·본문)은 **둘 다** 턴 값을 보므로 서로 어긋나지 않는다 — 어긋난 것은 문장이다. 그 거동을 단위 test 로 잠갔다. 턴 것을 그만두는 길(ⓑ)은 **출하 거동 변경**이라 이 slice 의 out_of_scope 다 |
| B-8b | [CR L-10] 안내 문단이 바뀌는데 알려지지 않는다 | **(a) 수정** | `aria-live="polite"`(assertive 가 아니다 — 이 문단은 판정이 아니다) |
| B-8c | [CR L-11] 예외가 노드 **전체**를 모집단에서 뺀다 | **(a) 수정** | 면제를 그 노드의 **`value` 하나**로 좁혔다. 예외 칸의 속성 넷은 모집단에 남는다. 양성 대조를 함께 두었다 — 예외 칸에 `title` 을 심으면 술어가 문제를 낸다 |
| B-8d | [CR L-12] 심는 축어가 고정 상수라 fixture 와 겹칠 수 있다 | **(b) 근거 있는 거부 + 등재** | 아래 「B-8d 거부 사유」 |
| B-8e | [CR L-13] 지운 헬퍼 자리의 빈 줄 둘 | **(a) 수정** | 한 줄로 |
| B-9a | [CR L-7] `15c/rollback.md` 술어의 공백 | **(a) 수정** | 공백 제거 |
| B-9b | [CR L-8] `15c` evidence 두 파일의 모순 | **(a) 수정** | 합성 번호를 scope.md 의 꼴 표에 **축어로 두는 쪽**으로 정했다(제한 10 의 전제가 그 사실이다). `commands.md` 의 육안 줄을 그 사실대로 고쳤다 |
| B-9c | [v F-3] `15c/commands.md` 15A 병합 절의 시각 | **(a) 수정** | `2026-11-21T20:30Z` |
| B-9d | [v F-4] 라운드별 절이 쌓였다 | **(a) 수정** | 해소된 사실 둘(임시 연속 번호 · 결번)을 결론 한 줄로 접고, 측정 절을 **현행 상태 하나**로 세웠다 |
| B-9e | [v F-5] 알려진 제한 8 이 측정됐다 | **(a) 수정** | 「suite 안에 양성 대조가 없다」가 **거짓이 됐다** — B-8c 가 그 자리를 suite 안에 세웠다. 제한 8 을 그 사실로 갱신했다 |

## A-4f 조사 — 플래그가 선 채 `SpringApplication.exit` 로 들어간다

**질문**: 인터럽트된 스레드에서 context close 가 던지면 `SpringApplication.exit` 이 그것을
삼키고 종료 코드가 2 대신 1 이 될 수 있는가.

**조사 결과 — 이 slice 는 그대로 둔다**(「그대로 둔다」를 결정으로 적는다). 근거 셋:

1. **이 slice 가 만든 위험이 아니다.** 플래그는 15B r4 판에서도 선 채로 `finishRun` 이 돌았다
   (`noteAccountFailure` 가 되세운다 — 15A r1 리뷰 LOW ⓒ 의 처분이다). A-1 이 바꾼 것은
   **사유 문자열과 멈추는 시점**이지 플래그의 생애가 아니다.
2. **걱정한 2→1 경로가 이 판에 없다**(M15/15G 가 v F-3 으로 교체한 근거). 앞 판의 문장은
   「지우면 **JVM 종료 훅과** context close 가 인터럽트를 보지 못한다」였는데 **종료 훅 부분이
   부정확하다** — 훅은 별 스레드에서 돌아 main 의 플래그와 무관하다. 결론을 세우는 실제 근거는
   `SpringApplication.exit` 자신이다: context close 가 던지면 바깥 catch 로 가고 돌려주는 값이
   `exitCode != 0 ? exitCode : 1` 이라, 생성기가 낸 **2 는 close 예외로 1 이 되지 않는다**.
   (지우지 않는 쪽의 사유는 따로 있다 — close 는 **이 스레드에서** 돌고, 그 경로가 인터럽트를
   보지 못하게 되는 것이 15A r1 이 닫은 결함을 종료 경로에서 다시 여는 일이다.)
3. **실해의 상한이 작다.** 갈리는 것은 `INCOMPLETE`(2) 와 `FAILED`(1) 뿐이고, 둘 다 **비-0** 이라
   cron 은 어느 쪽이든 실패로 읽는다. 사유는 종료 코드가 아니라 **`stopped cause=interrupted`
   줄**이 말하고, A-2 가 그 줄의 처방을 runbook 에 세웠다.

**다만 KDoc 의 지시 대상을 고쳤다** — `noteAccountFailure` 가 말하던 「이 스레드의 다음
blocking 호출」은 A-1 뒤로 **루프가 아니라 종료 경로**를 가리킨다(멈춘 뒤 `runBlocking` 재진입이
없다). 그 문장을 지금 가리키는 것으로 바꿨다.

## A-3 가 바꾸지 않은 것 — 식별자 `businessDay`

회전 축의 날짜를 나르는 이름은 여전히 `businessDay` 다(`accountsForRun` 의 인자 · 러너의 private
함수). 「영업일」의 영어 대응이라 KDoc 의 「KST 달력일」과 어긋나 보인다.

**바꾸지 않은 이유**: 15B r5 가 이 축을 **「문면만 바로잡고 코드 0줄」**로 처분했고, 그 판에서
verifier `ready-for-review` 와 code-reviewer `APPROVE` 가 났다 — 식별자는 두 레인이 본 뒤 받아들인
상태이지 이 라운드가 새로 발견한 누락이 아니다. CR L-1 이 지목한 것도 **한국어 문면 둘**(test
이름 · KDoc)이고 둘 다 닫혔다. 이름을 바꾸면 `internal` 시그니처와 명명 인자 호출이 함께 움직여
이 「거동 0」 PR 에 **유일하게 거동과 무관한 넓은 diff** 가 생긴다.

**다음 레인에 주는 신호**: KDoc 이 「**KST 달력일**(주말·공휴일을 포함한다 — 「영업일」이 아니다)」을
그 함수 머리에 적고 있으므로 어휘를 베끼는 자리는 그 문장이다. 이름을 고칠 값이 있다고 보면
회전 축을 다시 여는 slice(`OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ)가 같은 커밋에서 한다.

## B-8d 거부 사유 — 심는 축어를 난수로 바꾸지 않는다

**오늘 그 위험이 조용하지 않다.** 설정 여정 ① 이 **심기 전에** `assertNoPrivateValues(page)` 를
부른다 — fixture 가 그 열 자리를 품게 되는 날 그 단언이 **심기 전에** 붉어지고, 실패 자리가
「③ 양성 대조」가 아니라 「① 출발점」이라 진단이 갈린다. 같은 호출이 `journey`·`onboarding`
여정에도 있어 모집단이 좁지 않다.

**난수로 바꾸면 잃는 것이 있다.** 제한 10(`leak-patterns.txt` 에 사업자번호 꼴을 넣지 않는다)의
전제는 「이 slice 의 evidence·test 가 **합성 번호를 축어로** 담는다」이다. 난수는 그 전제를
거짓으로 만들어 제한 10 의 결정을 다시 열고, 실패를 재현할 때 「그 실행의 값」을 로그에서
되찾아야 한다. 교환이 이익이 아니다.

**등재한다** — 아래 알려진 제한 3.

## 알려진 제한

1. **A-1 의 술어는 「이 스레드」를 본다.** `runBlocking` 아래의 outbox 쓰기가 다른 스레드에서
   재개돼도 러너의 catch 는 러너 스레드에서 도므로 오늘은 같은 답이다. 평가 경로가 자기
   스레드 풀을 갖는 날 이 전제를 다시 봐야 한다 — 그날은 플래그가 아니라 **취소 토큰**이 축이다.
2. **인터럽트는 여전히 `failed` 로 센다**(A-4d 로 등재). 마침 줄만 보는 경보에게 배포 중
   SIGTERM 과 DB 장애가 같은 값이다. 가르는 정보는 `stopped cause=` 줄에 있고 runbook §3.4 가
   읽는 법을 든다.
3. **여정이 심는 사업자번호는 고정 축어다**(B-8d). 뒷날 여정 fixture 에 같은 열 자리가 섞이면
   누출 게이트가 **누출이 아닌데** 붉는다 — 붉는 자리는 심기 전 단계라 진단은 갈리지만, 그 줄의
   문면은 「저장한 번호가 입력칸 밖에 있다」로 **오해를 부른다**.
4. **`aria-live="polite"`(B-8b)는 브라우저·스크린 리더의 거동이라 test 로 잴 수 있는 것은
   속성의 존재뿐이다.** 읽히는지는 이 저장소의 어느 게이트도 재지 않는다.
5. **B-5 의 teardown 은 사업 정보 절을 통째로 다시 쓴다**(절 단위 교체 계약). 되돌림이 필요한
   경우에만 쓰므로 멱등하지만, 그 PR 이 다른 칸을 함께 쓰는 여정을 더하면 teardown 이 읽어 온
   현재 값으로 다시 쓰는 것이 맞는지 그때 다시 봐야 한다.
6. **A-1 의 술어는 「플래그를 되세우는 번역」만 잡는다**(r1 cr L-1 · M15/15G 등재). 제한 1 과
   **다른 축**이다 — 그쪽은 「다른 스레드」이고 이것은 「**같은 스레드인데 플래그가 지워진**
   번역」이다. `InterruptedException` 은 던져지는 순간 인터럽트 상태를 지우므로, 그것을 잡아
   되세우지 **않고** 감싸는 계층(상류 라이브러리 · 미래의 드라이버 · `Future.get` 류를 감싼
   자리)이 끼면 플래그는 꺼진 채 오고 술어는 앞 판과 똑같이 눈이 먼다. `noteAccountFailure` 는
   **정확히 `InterruptedException` 일 때만** 되세우므로 감싼 예외를 구제하지 못한다. 그때 run 은
   그 실패를 여느 계정 실패로 센다 — 연속 상한에 닿으면 `cause=consecutive-account-failures` 로
   멈추고, 닿지 않으면 멈춤 줄 없이 `failed=` 에만 남는다. 운영자가 어느 쪽이든 계정 장애로 읽지
   않도록 runbook §3.4 가 「거꾸로는 읽지 않는다」를 든다(15G · M15 종결에서 문면 정정).

## 승인 경계

- 새 operation · 새 마이그레이션 · 새 외부 호출 · 새 의존성 **0**.
- 계약 파일(`openapi/**`)과 생성 타입을 바꾸지 않는다 — wire 는 그대로다.
- 운영 DB write · 실제 외부 API/LLM/Telegram 호출 · push 외의 merge 는 하지 않는다.
- 재작업 상한 5 에 닿으면 보고한다.

## base 확인

`git rev-parse origin/main` → `5a2f98ba7402971facacdabbe98f8782b5268ab0` ·
`git merge-base --is-ancestor 5a2f98ba7 HEAD` 를 착수 커밋에서 기계로 확인했다(위 yaml 의
40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에
줄이 서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에
있다. 귀속은 라운드마다 기계로 재산출해 확인한다.
