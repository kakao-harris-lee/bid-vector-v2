# M6/6F-2 — rollback.md

마이그레이션 비대칭 없음 — 이 slice는 스키마·인덱스를 만들지 않는다(읽기 전용 질의, D-6F2-7).

## 목록(기계 산출, 마지막 내용 커밋 `ee31d60` 뒤 재산출 — 수정 라운드 1 반영)

```
$ git diff --name-status 547fd7b..HEAD -- . ':!reports/evidence'
A	adapters/src/main/kotlin/bidvector/adapters/evaluation/JdbcCandidateSource.kt
A	adapters/src/main/kotlin/bidvector/adapters/evaluation/UuidCorrelationIdFactory.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A	adapters/src/main/kotlin/bidvector/adapters/strategy/SystemClock.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/CandidateStatusSetTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationGateRegistrationTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/JdbcCandidateSourceTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/strategy/SystemClockTest.kt
M	config/quality/gate-tests.properties
M	milestone-6.md
```

목록은 `A` 8 + `M` 3 = 경로 11(착수 라운드의 9에서 수정 라운드 1이 `EvaluationGateRegistrationTest
.kt`(신설, D-6F2-10)·`config/quality/gate-tests.properties`(계약 갱신 (1)로 in_scope 편입) 둘을
더했다).

**verifier r1 LEDGER-1 정정** — 착수 라운드 판이 이 절에 「`D` 8(신규 여덟 삭제) + `M` 2 — 목록과
정확히 일치」·「신규 파일 다섯 main + 셋 test」라고 적었으나, 그 시점의 실제 값은 `D` 7 + `M` 2
(주 9경로, main 3 + test 4)였다 — 목록(9경로) 자체는 정확했고 그 목록을 요약한 **숫자**가
어긋나 있었다. 이번 판은 아래 「임시 clone 실측」 절의 실제 명령 출력을 그대로 옮긴다 — 요약
숫자를 손으로 다시 세지 않는다.

**라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을 재산출한다** — 손으로 고치지 않는다.

## 공유 파일 셋 — 겹침 확인과 절차

`git log --oneline 547fd7b..HEAD -- <파일>`로 이 range 안에서 그 파일을 만진 커밋을 먼저
나열했다:

- `adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt` — `d2ce42e`(이 slice)
  **하나뿐**.
- `milestone-6.md` — `511b514`(이 slice의 착수 계약 고정 커밋, 팀장) **하나뿐**.
- `config/quality/gate-tests.properties` — `c8d290e`(이 slice, 수정 라운드 1) **하나뿐**.

세 파일 모두 이 range 안에서 이 slice의 커밋만 만졌으므로 **hunk 격리 없이 `--source=547fd7b`
전체 복원으로 충분**하다(겹치는 다른 slice의 줄이 없다). scope.md 계약 갱신 (1)이
`gate-tests.properties`를 「공유 파일이라 hunk 격리」로 지정했지만, 실측(위 `git log`)은 이
range 안에서 이 slice가 그 파일의 유일한 저자임을 보인다 — 다른 레인이 나중에 같은 파일을
만지면 그 시점부터는 hunk 격리(`git diff c8d290e~1..c8d290e -- config/quality/gate-tests
.properties | git apply -R`)가 필요하다(절차만 미리 적어 둔다).

## 복원 명령

```
git restore --source=547fd7b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/evaluation/JdbcCandidateSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/evaluation/UuidCorrelationIdFactory.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/SystemClock.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/CandidateStatusSetTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/JdbcCandidateSourceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/strategy/SystemClockTest.kt \
  config/quality/gate-tests.properties \
  milestone-6.md
```

`A` 항목(신규 여덟)은 `--source`에 없어 삭제된다. `M` 항목 셋(`Sql.kt`·`gate-tests.properties`·
`milestone-6.md`)은 base 내용으로 복원된다. `reports/evidence/m6/6f2/**`는 되돌리지 않는다.

## 임시 clone 실측(①~⑥, `git clone .` → 브랜치 체크아웃 → 복원, 전경 실행)

| # | 확인 | 결과 |
| --- | --- | --- |
| ① 복원 명령 exit | `git restore ...` | exit 0 |
| ② D/M 수 | `git status --porcelain` | `D` 8(신규 여덟 삭제) + `M` 3(`Sql.kt`·`gate-tests.properties`·`milestone-6.md`) — 위 목록과 정확히 일치(11경로) |
| ③ diff 빈 것 | `git diff 547fd7b -- <같은 열한 경로>` | 출력 없음(exit 0) — **내 줄이 사라졌다** |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | BUILD SUCCESSFUL(exit 0), 14 executed + 18 from cache |
| ⑤ test | `./gradlew --no-daemon :adapters:test --tests '*StrategyAdapterDependencyTest*' --rerun-tasks` | BUILD SUCCESSFUL(exit 0) — 6F-1이 세운 같은 형태 게이트가 되돌린 트리에서도 선다(**남의 줄이 남았다** — 이 slice가 손대지 않은 파일이고 복원 목록에도 없다) |
| ⑥ 게이트(`check`) | `./gradlew --no-daemon check` | BUILD SUCCESSFUL(exit 0), 346 actionable tasks(192 executed·117 from cache·37 up-to-date) — `gateExecutionGate` 포함(신설 다섯 등재를 되돌렸으므로 그 게이트가 그 이름들을 더는 요구하지 않는다는 것도 함께 확인) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** — ③이 `git diff 547fd7b`가 빈 것을 직접
재므로, 되돌린 트리는 base 커밋의 해당 열한 경로와 파일 내용이 동일하다. ④⑤⑥이 그 트리가
서고·통과하고·게이트를 지난다는 것까지 확인한다.

## 하네스 레인 절 갱신 필요성

이 rollback은 `CLAUDE.md`·`.claude/**` 경로를 되돌리지 않는다. `git log --oneline
547fd7b..HEAD -- CLAUDE.md .claude/`는 이 판정 시점까지 빈 출력이다(하네스 레인 커밋 혼입
없음) — scope.md 「하네스 레인 변경: 없음」은 이 라운드까지도 사실이다.
