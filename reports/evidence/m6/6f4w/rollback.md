# M6/6F-4-w — rollback.md

base = `git merge-base HEAD origin/main` = `02164e44397836c8bedbcf824b8f7659d7be4108`(고정 SHA 아님,
라운드마다 재산출).

## 목록 (기계 산출, `git diff --name-status <base>..HEAD`)

신설(A, 삭제 대상) 16개:

```
adapters/src/main/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPort.kt
adapters/src/test/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPortTest.kt
reports/evidence/m6/6f4w/checklist.md
reports/evidence/m6/6f4w/commands.md
reports/evidence/m6/6f4w/rollback.md
reports/evidence/m6/6f4w/scope.md
strategy/src/test/resources/compile-fixtures/mutant-4-keyword-scope-text-direct-construction-typo.kt.txt
strategy/src/test/resources/compile-fixtures/mutant-5-keyword-scope-text-copy-bypass-typo.kt.txt
strategy/src/test/resources/compile-fixtures/mutant-6-full-scope-text-direct-construction-typo.kt.txt
strategy/src/test/resources/compile-fixtures/mutant-7-full-scope-text-copy-bypass-typo.kt.txt
strategy/src/test/resources/compile-fixtures/negative-4-keyword-scope-text-direct-construction.kt.txt
strategy/src/test/resources/compile-fixtures/negative-5-keyword-scope-text-copy-bypass.kt.txt
strategy/src/test/resources/compile-fixtures/negative-6-full-scope-text-direct-construction.kt.txt
strategy/src/test/resources/compile-fixtures/negative-7-full-scope-text-copy-bypass.kt.txt
strategy/src/test/resources/compile-fixtures/positive-4-keyword-scope-text-via-assembly.kt.txt
strategy/src/test/resources/compile-fixtures/positive-6-full-scope-text-via-assembly.kt.txt
```

수정(M, base 로 restore) 16개(공유 파일 2개 포함, L-3 정정 — 앞 라운드 표제는 A 15·M 14였다):

```
adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/extraction/ExtractionGateTest.kt
adapters/src/test/kotlin/bidvector/adapters/ml/UnavailableMlAnalysisTest.kt
app/src/test/kotlin/bidvector/app/conformance/StrategyExecutors.kt
config/quality/gate-tests.properties                          # 공유 파일 — 아래 별도 절차
milestone-6.md                                                 # 공유 파일 — 아래 별도 절차
strategy/src/main/kotlin/bidvector/strategy/Text.kt
strategy/src/test/kotlin/bidvector/strategy/CompileFailureHarnessTest.kt
strategy/src/test/kotlin/bidvector/strategy/WatchRulesTest.kt
strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt
strategy/src/test/resources/compile-fixtures/mutant-2-keyword-scope-cross-text-typo.kt.txt
strategy/src/test/resources/compile-fixtures/negative-2-keyword-scope-cross-text.kt.txt
strategy/src/test/resources/compile-fixtures/positive-2-keyword-scope-own-text.kt.txt
workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt
workflow/src/test/kotlin/bidvector/workflow/evaluation/KeywordHitsCounterTest.kt
workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt
```

## 절차

```bash
BASE=$(git merge-base HEAD origin/main)

# 1) 신설 파일 삭제
git rm -f \
  adapters/src/main/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPort.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPortTest.kt \
  reports/evidence/m6/6f4w/checklist.md \
  reports/evidence/m6/6f4w/commands.md \
  reports/evidence/m6/6f4w/rollback.md \
  reports/evidence/m6/6f4w/scope.md \
  strategy/src/test/resources/compile-fixtures/mutant-4-keyword-scope-text-direct-construction-typo.kt.txt \
  strategy/src/test/resources/compile-fixtures/mutant-5-keyword-scope-text-copy-bypass-typo.kt.txt \
  strategy/src/test/resources/compile-fixtures/mutant-6-full-scope-text-direct-construction-typo.kt.txt \
  strategy/src/test/resources/compile-fixtures/mutant-7-full-scope-text-copy-bypass-typo.kt.txt \
  strategy/src/test/resources/compile-fixtures/negative-4-keyword-scope-text-direct-construction.kt.txt \
  strategy/src/test/resources/compile-fixtures/negative-5-keyword-scope-text-copy-bypass.kt.txt \
  strategy/src/test/resources/compile-fixtures/negative-6-full-scope-text-direct-construction.kt.txt \
  strategy/src/test/resources/compile-fixtures/negative-7-full-scope-text-copy-bypass.kt.txt \
  strategy/src/test/resources/compile-fixtures/positive-4-keyword-scope-text-via-assembly.kt.txt \
  strategy/src/test/resources/compile-fixtures/positive-6-full-scope-text-via-assembly.kt.txt

# 2) 비공유 수정 파일 — base 로 restore
git restore --source="$BASE" --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/extraction/ExtractionGateTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/UnavailableMlAnalysisTest.kt \
  app/src/test/kotlin/bidvector/app/conformance/StrategyExecutors.kt \
  strategy/src/main/kotlin/bidvector/strategy/Text.kt \
  strategy/src/test/kotlin/bidvector/strategy/CompileFailureHarnessTest.kt \
  strategy/src/test/kotlin/bidvector/strategy/WatchRulesTest.kt \
  strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt \
  strategy/src/test/resources/compile-fixtures/mutant-2-keyword-scope-cross-text-typo.kt.txt \
  strategy/src/test/resources/compile-fixtures/negative-2-keyword-scope-cross-text.kt.txt \
  strategy/src/test/resources/compile-fixtures/positive-2-keyword-scope-own-text.kt.txt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/KeywordHitsCounterTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt

# 3) 공유 파일(gate-tests.properties·milestone-6.md) — base..HEAD 구간에서 이 파일들을
#    만진 커밋이 이 slice(구현 레인 + 팀장 계약 문서 레인)뿐임을 실측했다(아래 「공유 파일
#    커밋 목록」) — 다른 slice 의 줄이 섞이지 않으므로 base..HEAD 통째 역적용이 안전하다.
git diff "$BASE"..HEAD -- config/quality/gate-tests.properties | git apply -R
git diff "$BASE"..HEAD -- milestone-6.md | git apply -R
```

### 공유 파일 커밋 목록 (`git log --oneline <base>..HEAD -- <파일>`, 실측 HEAD 기준)

- `config/quality/gate-tests.properties`: `ba258f00`(구현 레인, `NoticeWatchSubjectPortTest`
  등재) · `aee66633`(구현 레인, `WatchTextAssemblyTest` 등재). 이 slice 밖 커밋 없음
  (수정 라운드 1 재확인 — 새 커밋 없음).
- `milestone-6.md`: `5d896c39`(팀장, 착수 문단). 이 slice 밖 커밋 없음(재확인 동일).

## 임시 clone 실측 ①~⑥

**실측 HEAD: `13f29522c486636f7fc3d10f71594f0f9f71a2e3`**

```bash
git clone . /tmp/6f4w-rollback-probe-r1
cd /tmp/6f4w-rollback-probe-r1
git checkout m6-6f4w/2026-09-23   # HEAD 는 13f29522 — 이 SHA 와 일치 확인 후 진행
BASE=$(git merge-base HEAD origin/main)   # = 02164e44...
<위 1)~3) 절차 실행>
```

실제로 위 clone 을 만들어 절차를 끝까지 실행했다(2026-09-23, scratchpad `rollback-probe`).

| # | 확인 | 결과 |
|---|---|---|
| ① | 각 명령 exit | `git rm` exit 0(16 삭제) · `git restore` exit 0(14 파일) · `git apply -R` ×2 exit 0 |
| ② | D/M 수 | `git status --porcelain` 집계 — D 16 · M 16(목록과 일치) |
| ③ | `git diff --name-only $BASE -- <되돌린 경로 전부>` 가 빈 출력 | 빈 출력 확인 |
| ③b | 트리 동일성(강화 확인) | `git add -A && git diff --cached $BASE` — **0줄**(트리 전체가 base 와 완전히 동일) |
| ④ | 되돌린 트리 compile | `./gradlew --no-daemon :adapters:compileKotlin :strategy:compileKotlin :workflow:compileKotlin :app:compileKotlin` — `BUILD SUCCESSFUL`(32 actionable tasks: 18 executed·14 from cache) |
| ⑤ | 되돌린 트리 test | `./gradlew --no-daemon :adapters:test :strategy:test :workflow:test :app:test` — `BUILD SUCCESSFUL`(50 actionable tasks) |
| ⑥ | 되돌린 트리 게이트(`check`) | `./gradlew --no-daemon check` — `BUILD SUCCESSFUL in 1m 12s`(346 actionable tasks: 210 executed·86 from cache·50 up-to-date) — `leakPatternGate`·`gateExecutionGate` 포함 전건 GREEN |

## 유효성 확인 (2026-09-19 정정 규율)

`git diff --name-only <실측 HEAD>..<판정 SHA> -- <rollback.md 가 되돌리는 경로 전부(위 A·M
32개, `rollback.md` 자신 포함)>` 가 **빈 출력**이면 유효. 실측 HEAD(`13f29522`) 이후 이
문서를 커밋하는 것 말고 다른 산출물 커밋이 없으면 대조 대상 경로 집합에는 변화가 없어
빈 출력이다 — verifier 가 재실행 시점(판정 SHA)에서 다시 확인한다.
