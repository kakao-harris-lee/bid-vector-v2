# M6/6F-2 — rollback.md

마이그레이션 비대칭 없음 — 이 slice는 스키마·인덱스를 만들지 않는다(읽기 전용 질의, D-6F2-7).

## 목록(기계 산출, 마지막 내용 커밋 `b1f37b5` 뒤 재산출)

```
$ git diff --name-status 547fd7b..HEAD -- . ':!reports/evidence'
A	adapters/src/main/kotlin/bidvector/adapters/evaluation/JdbcCandidateSource.kt
A	adapters/src/main/kotlin/bidvector/adapters/evaluation/UuidCorrelationIdFactory.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A	adapters/src/main/kotlin/bidvector/adapters/strategy/SystemClock.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/CandidateStatusSetTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/evaluation/JdbcCandidateSourceTest.kt
A	adapters/src/test/kotlin/bidvector/adapters/strategy/SystemClockTest.kt
M	milestone-6.md
```

**라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을 재산출한다** — 손으로 고치지 않는다.

## 공유 파일 둘 — 겹침 확인과 절차

`git log --oneline 547fd7b..HEAD -- <파일>`로 이 range 안에서 그 파일을 만진 커밋을 먼저
나열했다:

- `adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt` — `d2ce42e`(이 slice)
  **하나뿐**. 다른 레인과 겹치지 않는다.
- `milestone-6.md` — `511b514`(이 slice의 착수 계약 고정 커밋, 팀장) **하나뿐**. 다른 레인과
  겹치지 않는다.

두 파일 모두 이 range 안에서 이 slice의 커밋만 만졌으므로 **hunk 격리 없이 `--source=547fd7b`
전체 복원으로 충분**하다(겹치는 다른 slice의 줄이 없다). 병행 레인 표(scope.md)의 `m6-6a/
2026-09-17`은 아직 구현 미착수라 `Sql.kt`를 건드리지 않았다 — 실측(위 `git log`)이 그것을
확인한다.

## 복원 명령

```
git restore --source=547fd7b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/evaluation/JdbcCandidateSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/evaluation/UuidCorrelationIdFactory.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/SystemClock.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/CandidateStatusSetTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/JdbcCandidateSourceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/strategy/SystemClockTest.kt \
  milestone-6.md
```

`A` 항목(신규 파일 다섯 main + 셋 test)은 `--source`에 없어 삭제된다. `M` 항목 둘
(`Sql.kt`·`milestone-6.md`)은 base 내용으로 복원된다. `reports/evidence/m6/6f2/**`는
되돌리지 않는다(evidence는 slice 종결 기록이지 wiring이 아니다) — 되돌린 뒤에도 이 디렉터리는
그대로 남는다.

## 임시 clone 실측(①~⑥, `git clone .` → 브랜치 체크아웃 → 복원)

| # | 확인 | 결과 |
| --- | --- | --- |
| ① 복원 명령 exit | `git restore ...` | exit 0 |
| ② D/M 수 | `git status --porcelain` | `D` 8 (신규 여덟 삭제) + `M` 2(`Sql.kt`·`milestone-6.md`) — 목록과 정확히 일치 |
| ③ diff 빈 것 | `git diff 547fd7b -- <같은 아홉 경로>` | 출력 없음(exit 0) — **내 줄이 사라졌다** |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | BUILD SUCCESSFUL(exit 0), 14 executed + 18 from cache |
| ⑤ test | `./gradlew --no-daemon :adapters:test --tests '*StrategyAdapterDependencyTest*' --rerun-tasks` | BUILD SUCCESSFUL(exit 0) — 6F-1이 세운 같은 형태 게이트가 되돌린 트리에서도 그대로 선다(**남의 줄이 남았다** — `StrategyAdapterDependencyTest.kt`는 이 slice가 손대지 않았고 복원 대상 목록에도 없다) |
| ⑥ 게이트(`check`) | `./gradlew --no-daemon check` | BUILD SUCCESSFUL(exit 0), 346 actionable tasks(192 executed·117 from cache·37 up-to-date) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** — ③이 `git diff 547fd7b`가 빈 것을
직접 재므로, 되돌린 트리는 base 커밋의 해당 아홉 경로와 파일 내용이 동일하다(트리 동일성
실측). ④⑤⑥이 그 트리가 서고·통과하고·게이트를 지난다는 것까지 확인한다.

## 하네스 레인 절 갱신 필요성

이 rollback은 `CLAUDE.md`·`.claude/**` 경로를 되돌리지 않는다(scope.md 「하네스 레인 변경」
절이 착수 시점 「없음」이고 이 slice 진행 중 갱신되지 않았다 — 팀장 최종 리뷰 요청 시 그
절을 `git log --oneline 547fd7b..HEAD -- CLAUDE.md .claude/`로 재확인해 갱신하는 것은 이
slice의 evidence 종결 단계 소관이다).
