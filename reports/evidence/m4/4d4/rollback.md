# rollback.md — M4 / 4D-4

## 대상

in_scope 경로 한정 restore. `base_sha = 44721cf`. 목록은 `git diff --name-status
44721cf..HEAD -- <in_scope 경로>`(수동 기입 금지 — 라운드마다 재실행)로 낸다.
`reports/evidence/m4/4d4/**`(evidence)·`milestone-4.md`·`scope.md`(팀장 레인)는
이 rollback 대상이 아니다 — 구현 레인은 편집하지 않았다.

```
M  config/quality/gate-tests.properties
M  workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/EvidenceLines.kt
M  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt
M  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt
M  workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionEvidence.kt
M  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt
M  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseIsolationTest.kt
M  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseTest.kt
M  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt
A  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvidenceLinesTest.kt
M  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt
M  workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt
```

## 공유 파일 판별(2026-09-08·09·16 규율)

`config/quality/gate-tests.properties` 는 이 range(`44721cf..HEAD`)에서 이 slice의
커밋(`913a3e3`·`af6b669`) 둘만 만졌다(`git log --oneline 44721cf..HEAD --
config/quality/gate-tests.properties` 로 확인) — 다른 slice와 겹치지 않아 hunk
격리 없이 `base..HEAD` 전체 역적용으로 충분하다. `EvidenceLines.kt`·
`EvidenceLinesTest.kt` 는 `git mv` 로 경로만 바뀌었을 뿐(`workflow.notification`
→ `workflow.evaluation`) base 에는 두 경로 다 없어 `A`(신규)로 취급 — restore 가
`--source` 없는 경로를 삭제해 준다(별도 `git rm` 불필요).

## 절차(in_scope 경로 개별 인자, `A` 항목 포함 — 삭제는 restore 가 처리)

```bash
git restore --source=44721cf --staged --worktree -- \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionEvidence.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/EvidenceLines.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseIsolationTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvidenceLinesTest.kt \
  config/quality/gate-tests.properties
```

`git checkout <base> --` 는 쓰지 않는다(신규 경로마다 pathspec 오류로 exit 1 —
1A 16차 high 재발 방지). `EvidenceLines.kt`·`EvidenceLinesTest.kt` 는 `--source`
에 해당 경로가 없으므로 restore 가 작업 트리에서 삭제한다.

## 임시 clone 실측(`git clone --no-hardlinks`, `/tmp/4d4-rollback-check`, 브랜치
`m4-4d4/2026-09-16` HEAD `226a2ed`)

① `git diff --name-status 44721cf..HEAD -- <in_scope>` — 위 목록과 일치(기계 산출, 재확인).
② `git restore --source=44721cf ...` 실행 — `git status --short` 결과: `M` 11·`D` 2(신규 두
파일이 삭제로 나타남 — `A`→`D`, restore 의 정상 동작). exit 0.
③ `git diff 44721cf -- workflow/src/main/kotlin/bidvector/workflow/evaluation/
workflow/src/test/kotlin/bidvector/workflow/evaluation/
config/quality/gate-tests.properties | wc -l` → **0** — in_scope 경로가 base 와
바이트 동일.
④ `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin` →
exit 0.
⑤ `./gradlew --no-daemon :workflow:test` → exit 0.
⑥ `./gradlew --no-build-cache --no-daemon clean check`(전건, `evaluation` 게이트가
이 slice 가 유일하게 닿은 게이트가 아니라 — carrier 변경이 `app` conformance·
architecture 게이트까지 걸치므로 전건을 돈다) → **exit 0**(355 actionable tasks,
57s). 되돌린 트리가 게이트를 붉히지 않는다 — evidence 디렉터리(`reports/evidence/
m4/4d4/**`)는 되돌리지 않았고 그것이 게이트에 걸리지 않음도 이 실행으로 확인됐다
(M4/leak-baseline-coord 사고의 재발 없음).

## 하네스 레인 변경

없음. `git log --oneline 44721cf..HEAD -- CLAUDE.md .claude/` 결과 0건(단일
역적용 뒤 재등재 대상 없음).

## 예상 복구 시간

원격 상태 변경·마이그레이션 없음(순수 Kotlin 도메인/애플리케이션 코드) — 위
`git restore` 한 명령 + 재빌드 시간(로컬 실측 ~1분) 이내.
