# M15 / 15g — M15 종결 준비 — slice 계약

```yaml
milestone: M15
slice: 15g
base_sha: 394a23544de325b42624f71756aa3b82fa1a902b   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  # ── 1. PR #192 넘김 (정본 = 그 PR 의 「팀장 — 머지 결정」 코멘트) ─────────
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt
  - docs/runbook/m6-6e-operations.md          # §3.4 `cause=interrupted` 문면
  - web/e2e/journey/settings.spec.ts          # teardown 예외 축 · 양성 대조 ⑤ 네 속성
  - web/src/display/profileDisplay.ts         # KDoc 오착 교정(거동 0)
  - reports/evidence/m15/15c/scope.md         # 알려진 제한 8
  - reports/evidence/m15/15c/commands.md      # web job 귀속 · 날짜 절 둘 접기
  - reports/evidence/m15/15f/scope.md         # 알려진 제한 새 항목 · A-4f 근거 2
  - reports/evidence/m15/15f/commands.md      # 크기 계수 기준 한 줄
  # ── 2. 활용사례 캡처 재촬영 ─────────────────────────────────────────────
  - docs/usecase/captures/                    # PNG 열여섯 + manifest.json
  # ── evidence ────────────────────────────────────────────────────────────
  - reports/evidence/m15/15g/scope.md
  - reports/evidence/m15/15g/commands.md
  - reports/evidence/m15/15g/rollback.md
out_of_scope:
  - milestone-15.md 종결 절 — **팀장이 운영 반입 뒤 직접 쓴다**
  - 새 기능 · 새 마이그레이션 · 새 operation · 새 의존성 · wire 변경(openapi/**)
  - 출하 거동 변경 — 이 slice 의 Kotlin·TS 편집은 전부 문서·주석·test 다
  - 멈춤 술어의 **범위**를 좁히는 변경(`Thread.interrupted()` 로 지우기 — 아래 처분 ⑩)
  - 여정 teardown 을 「진입 값 복원」으로 바꾸는 변경(아래 처분 ⑪)
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

둘이다.

1. **PR #192 가 차단하지 않고 넘긴 지적을 닫는다.** 정본은 그 PR 의 「팀장 — 머지 결정」
   코멘트이고, 판정 전문 둘(code-reviewer APPROVE · verifier ready-for-review)도 같은 PR 에 있다.
2. **활용사례 캡처를 다시 찍는다.** `a-start-*` 는 15C 가 시작하기 화면에 사업자번호 절을
   더해 낡았다(PR #189 의 「캡처 (M-5)」 절이 그 사실과 「M15 종결에서 찍는다」를 적었다).

**출하 거동은 0 이다.** Kotlin 편집은 KDoc·주석·test 이고, TS 편집은 여정 spec 의 teardown·
양성 대조와 표시층 KDoc 의 위치다. 화면이 그리는 것도, wire 도 바뀌지 않는다.

## 처분표 — 각 지적을 (a) 수정 (b) 근거 있는 거부 (c) 등재 중 하나로

| # | 원천 | 지적 | 처분 | 무엇을 했나 |
| --- | --- | --- | --- | --- |
| ① | CR M-1 | 15c evidence 의 최종 전건 절이 **다른 라운드의 측정**을 그 트리의 측정으로 적는다 | **(a) 수정 — 귀속 복원** | W-1~W-13 전수는 **수정 라운드 1**(표적 라운드)의 수다. 그 수(vitest 74 파일 2010 test · 스모크 39 · 여정 29)를 라운드 이름과 함께 되살리고, 최종 트리의 web 축 정본이 **PR #189 CI** 임을 같은 줄이 말한다 |
| ② | CR L-1 · v F-3 | `stopCauseFor` KDoc 의 「플래그는 **번역을 타지 않는다**」가 과하고 runbook 이 그 과한 형태를 운영 지시로 옮겼다 | **(a) 수정** | KDoc 을 「**되세우는** 계층은 전부 잡는다(HikariCP 가 그렇다)」로 완화하고 되세우지 않는 계층이 사각임을 같은 자리가 말한다. runbook §3.4 의 `cause=interrupted` 항목에 **그 줄이 서지 않는 경우**를 더했다. 15f scope 알려진 제한에 **새 항목**(제한 1 과 다른 축이라 끼워 넣지 않는다) |
| ③ | v F-3 | A-4f 근거 2 의 「JVM 종료 훅이 인터럽트를 보지 못한다」가 부정확하다 | **(a) 수정** | 종료 훅은 별 스레드라 main 의 플래그와 무관하다. 근거를 **`SpringApplication.exit` 의 사실**로 바꿨다 — close 가 던져도 `exitCode != 0 ? exitCode : 1` 이라 생성기가 낸 2 가 1 이 되지 않는다 |
| ④ | v F-1 | 멈춤 사유 **우선순위**(인터럽트 > 연속 상한)를 잠그는 test 가 없다 | **(a) 수정** | 셋째 연속 실패에서 플래그가 서는 표 한 줄을 더해 `cause=interrupted` 를 단언한다. `when` 두 줄 순서를 바꾸는 변이가 **RED** 인 것을 실측했다(`commands.md` M1) |
| ⑤ | v F-2 | 15c 알려진 제한 8 의 「닫혔다」가 **`title` 하나**에만 맞다 | **(a) 수정 — 넓히는 쪽** | 여정 ⑤ 를 **네 속성**(placeholder · title · alt · aria-label)으로 돌린다. 네 속성 각각을 모집단에서 빼는 변이가 RED 인 것을 실측했다. 제한 8 의 문면을 그 사실로 갱신했다 |
| ⑥ | v F-4 | evidence 크기 계수 기준이 없고 15c 에 날짜 붙은 병합 절 둘이 남았다 | **(a) 수정** | 15f `commands.md` 에 계수 기준 한 줄(15b·15c evidence 수정을 **산출물**로 센다). 15c 의 병합 절 둘을 **현행 상태 하나**로 접었다 — 두 번의 병합이 있었다는 사실은 결론 한 줄로 남는다 |
| ⑦ | CR L-3 | teardown 의 「되돌림을 먼저 다 돌린다」가 `expect` 에만 성립한다 — 첫 되돌림이 **던지면** 둘째가 건너뛴다 | **(a) 수정** | 되돌림 둘과 읽기 둘을 각각 `try/catch` 로 감싸 **예외도 사유 문자열**로 바꿨다. 주석의 주장이 참이 되고 끝 상태 단언 넷은 그대로다 |
| ⑧ | CR L-6 | `businessDetailsBodyOf` 의 큰 KDoc 이 `isHeadquartersRegion` 에 붙어 있다 | **(a) 수정 — 거동 0** | 그 블록을 `businessDetailsBodyOf` 바로 위로 옮겼다(`isHeadquartersRegion` 의 한 줄 KDoc 은 제자리) |
| ⑨ | CR L-8 | SQL 갈래를 처음 태우는 test 가 **메시지 부재**는 재지 않는다 | **(a) 수정** | 앞 test 와 **같은 두 줄**(접속 문자열 꼴 어휘 부재)을 그 test 에 더했다 |
| ⑩ | CR L-2 | 멈춤 판정이 **스레드 전역의 끈적한 플래그**를 읽는다 — run 에 범위가 묶여 있지 않다 | **(c) 결정 한 줄 + 등재** | 좁히지 않는다 — `Thread.interrupted()` 로 지우면 **run 시작 직전에 온 진짜 종료 신호를 삼킨다**. A-4f 와 같은 꼴로 그 선택을 `stopCauseFor` KDoc 의 결정 한 줄로 적었다. 아래 알려진 제한 1 |
| ⑪ | CR L-4 | teardown 은 되돌림이 아니라 **절대값 강제**다 — 들어올 때 값이 있었어도 지운다 | **(c) 등재** | 아래 알려진 제한 2. 진입 값 복원은 여정에 읽기를 하나 더 다는 설계 변경이라 이 「거동 0」 slice 밖이다 |
| ⑫ | CR L-5 | teardown 의 두 관문이 `=== null`/`toBeNull()` 인데 그 키는 계약의 `required` **밖**이다 | **(c) 등재** | 아래 알려진 제한 3. 계약 쪽 `required` 보강은 **wire 변경**이라 out_of_scope 다 |
| ⑬ | CR L-7 | 「더하지 않은 방어」의 실측이 **다른 도구**를 잰다 — **지우는** 가드는 공허하지 않다 | **(c) 등재** | 아래 알려진 제한 4. ⑩ 과 **같은 결정**의 다른 얼굴이다(지우는 가드를 넣는 것 = 술어의 범위를 run 으로 좁히는 것) |
| ⑭ | CR L-9 | `Pair<Int, String>` 의 구성 요소가 이름을 잃어 두 사용처가 폐기 구멍을 쓴다 | **(a) 수정 — 거동 0** | 「첫째 = 멈춘 자리(1-based) · 둘째 = 사유」를 **`stopped` 선언의 기존 주석에 접어** 넣었다(두 사용처가 함께 보는 자리다). 리뷰가 권한 (b) 다. **줄을 더하지 않은 것이 조건이다** — 라운드 1 에서 루프 안에 두 줄을 더했다가 `evaluateAccounts` 가 51 줄이 돼 `sizeGate` 가 붉었다(아래 라운드 1) |
| ⑮ | PR #189 M-5 | `a-start-*` 캡처가 낡았다 | **(a) 수정** | 출하 캡처 하네스로 열여섯 장 재촬영 + `manifest.json`. 자세한 것은 `commands.md` |

## 라운드 1 — 자기 결함 하나(PR CI `check` 가 잡았다)

⑭ 의 주석을 **루프 안에** 두 줄로 넣어 `evaluateAccounts` 가 **51 줄**이 됐고 `:app:sizeGate` 가
붉었다(`함수 50 줄 한도 초과 1건`). 로컬에서 돈 것은 `:app:test` 표적 하나라 그 게이트가 한
번도 돌지 않았다 — **「부분 게이트는 안 돌린 것과 같다」의 실물**이고, 이 저장소가 15C 에서 같은
모양을 이미 적었다(그때는 ktlint·detekt 였다).

처분은 「줄을 줄이려 함수를 쪼개기」가 **아니다** — 그것은 크기 회피용 기계적 분할이다. 같은
사실을 `stopped` **선언의 기존 주석에 접어** 두 줄을 그대로 둔 채 실었다(49 줄). 고친 뒤 표적
대신 **Kotlin 품질 게이트 묶음**을 돌렸다: `sizeGate` · `detekt` · ktlint 둘 · `cpdCheck` ·
평가 test — exit 0.

## 알려진 제한

1. **멈춤 술어의 범위는 run 이 아니라 스레드다**(CR L-2). `stopCauseFor` 는 「이 run 중에
   인터럽트가 왔는가」가 아니라 「지금 이 스레드에 플래그가 서 있는가」를 묻는다. 루프에 들어오기
   **전에** 남은 플래그가 있으면 아무 관계 없는 첫 실패가 `cause=interrupted` 로 적힌다. 오늘
   실현이 0 인 근거는 둘 — 러너가 일회성 `ApplicationRunner` 라 기동 직후 main 스레드에서 한 번
   돌고, 되세우고 삼키는 두 선례(relay · 수집)는 평가 경로가 아니다. 좁히는 길
   (`Thread.interrupted()` 로 읽으며 지우기)은 **run 시작 직전에 온 종료 신호를 삼키므로** 공짜가
   아니다 — 그래서 결정으로 적고 좁히지 않는다.
2. **여정 teardown 은 사업자번호를 「되돌리지」 않고 `null` 로 강제한다**(CR L-4). 다른 네 칸은
   읽어 온 값을 보존하지만 번호 축은 진입 상태와 무관하게 비워지고, 그 강제가 이 파일의 **모든**
   test 에 걸린다. 이 teardown 은 **「여정 하네스 계정의 사업자번호는 늘 비어 있다」는 불변식**에
   기댄다 — seed 가 생기거나 번호를 저장하는 spec 이 앞서는 날 teardown 은 되돌림이 아니라
   지우기이고, 끝 상태 단언은 그래도 초록이다. 15f 알려진 제한 5(**다른 칸**을 함께 쓰는 여정)와
   다른 축이다.
3. **teardown 의 두 관문이 계약의 `required` 밖 키에 `null` 등식을 건다**(CR L-5). 서버가
   `Profile.businessRegistrationNumber` 를 늘 싣는 것이 전제다(계약의 `required` 목록에는 없다).
   서버가 키를 빼는 날 `undefined === null` 이 거짓이라 teardown 이 매 test 마다 불필요한 절
   교체를 쓰고, 끝 상태 단언은 이 파일의 **모든** test 를 붉게 만든다 — 그때의 실패 문면
   (「설정 여정이 사업자번호를 남겼다」)은 거짓이다.
4. **「더하지 않은 방어」의 실측은 단언 가드만 잰다**(CR L-7). 15f 가 공허하다고 측정한 것은
   `@BeforeEach` 의 **단언** 가드이고, 제한 1 의 기작에 맞는 도구는 **읽으며 지우는** 한 줄이다 —
   그것은 단언이 아니라 공허할 수 없다. 넣지 않은 이유는 제한 1 의 결정과 같다(지우면 run 직전의
   신호를 잃는다). 다음 레인이 이 물음을 다시 열 때 재는 것은 **그 교환**이지 가드의 공허함이
   아니다.
5. **캡처에 신선도 게이트가 없다**(PR #189 M-5 가 적은 사실). `manifest.json` 이 그때의 HEAD 를
   적으므로 **낡음은 읽을 수 있지만**, 화면이 바뀌어도 아무 게이트가 붉지 않는다. 제출 직전에 손으로
   다시 뜨는 것이 절차다.

## 승인 경계

- 새 operation · 새 마이그레이션 · 새 외부 호출 · 새 의존성 **0**.
- 계약 파일(`openapi/**`)과 생성 타입을 바꾸지 않는다 — wire 는 그대로다.
- `milestone-15.md` 를 **건드리지 않는다** — 종결 절은 팀장이 운영 반입 뒤에 쓴다.
- 운영 DB write · 실제 외부 API/LLM/Telegram 호출 · push 외의 merge 는 하지 않는다.
- 캡처는 **하네스**(버릴 Postgres · fake KONEPS · 합성 fixture)만 찍는다. 운영 컨테이너에
  닿지 않는다.
- 재작업 상한 5 에 닿으면 보고한다.

## base 확인

`git rev-parse origin/main` → `394a23544de325b42624f71756aa3b82fa1a902b` ·
`git merge-base --is-ancestor 394a23544 HEAD` 를 착수 커밋에서 기계로 확인했다(위 yaml 의
40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에
줄이 서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에
있다. 귀속은 라운드마다 기계로 재산출해 확인한다.
