# Rollback — M4/4D-3

## 대상과 방법

착수 경계(`base_sha = 4b9fa21`) 이후 이 slice 가 만진 파일은 전부 이 slice 자기
이력뿐이다(아래 「대상 파일」의 각 파일을 `git log --oneline 4b9fa21..HEAD -- <파일>`로
확인 — 전부 정확히 1개 커밋, 다른 레인·다른 slice 의 줄이 섞이지 않았다). 그래서 커밋
해시별 hunk 격리가 아니라 **착수 경계 기준 단일 역적용**(2026-09-16 규칙)으로 충분하다.

```bash
git restore --source=4b9fa21 --staged --worktree -- <대상 파일 개별 인자>
rm -f adapters/src/main/kotlin/bidvector/adapters/ml/DiagnosticsShapeValidation.kt
```

## 대상 파일(`git diff --name-status 4b9fa21..HEAD` 기계 산출, 기록 시점)

- `M` (base 로 restore): `workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt`,
  `adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt`,
  `adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt`,
  `workflow/src/test/kotlin/bidvector/workflow/prediction/PredictionValueTest.kt`,
  `adapters/src/test/kotlin/bidvector/adapters/ml/ResponseMappingTest.kt`,
  `adapters/src/test/kotlin/bidvector/adapters/ml/SuccessShapeFailClosedTest.kt`,
  `workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt`,
  `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt`,
  `adapters/src/test/kotlin/bidvector/adapters/ml/MlTestFixtures.kt`
- `A` (삭제): `adapters/src/main/kotlin/bidvector/adapters/ml/DiagnosticsShapeValidation.kt`

**라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 위 목록은 `git diff --name-status
4b9fa21..HEAD` 로 매번 다시 낸다.

하네스 경로(`CLAUDE.md`·`.claude/**`)와 팀장 문서 레인 커밋(`3672ee5`이 만진
`data-dictionary.md`·`capability-map.md`·`milestone-4.md`, `93c8ff0`이 만든 `scope.md`)은
**되돌리지 않는다** — in_scope 밖.

## 임시 clone 실측(2026-09-16, `git clone --no-hardlinks` → 브랜치 `m4-4d3/2026-09-16` 체크아웃)

① `git restore --source=4b9fa21 --staged --worktree -- <9개 M 파일>` — exit 0.
② `rm -f DiagnosticsShapeValidation.kt` — 신설 파일 삭제.
③ `git diff 4b9fa21 -- <9개 M 파일>` — 출력 없음(base 와 동일, exit 0). `ls
DiagnosticsShapeValidation.kt` — 파일 없음 확인.
④ `./gradlew --no-daemon :workflow:compileKotlin :adapters:compileKotlin
:workflow:compileTestKotlin :adapters:compileTestKotlin` — exit 0.
⑤ `./gradlew --no-daemon :workflow:test :adapters:test` — exit 0(4D-1 이전 상태의
기존 test 전부 통과, 4D-3 신설 test 는 파일과 함께 사라져 실행되지 않음).
⑥ `./gradlew --no-build-cache --no-daemon clean check`(전건, 되돌린 트리 루트) —
2026-09-16T01:47:09Z 완료, exit 0(355 actionable tasks·338 executed, `gateExecutionGate`·
`contractGate`·`leakPatternGate`·`koverVerify` 포함 전 모듈 통과). 이 clone 은
evidence 커밋(이 문서 포함)을 아직 갖지 않은 시점의 HEAD(`b992318`)에서 코드만
되돌린 상태다 — 코드 되돌림 자체가 게이트를 붉히지 않음을 확인했다.

## 복구 방법·예상 소요

- 코드 복귀: `git revert b992318 439a191`(구현 → test 역순, 또는 위 `restore` 스크립트를
  워킹 트리에 직접 적용 후 커밋) — 컴파일·전건 `check` 재확인 포함 5분 내.
- `Predicted`의 `diagnostics` 필드가 사라지므로 `mapSuccess`·`predicted()` 호출부는
  자동으로 4D-1 시점 시그니처로 돌아간다(컴파일이 강제) — 별도 flag/route 전환 불필요,
  DB write·외부 API 배선이 없는 순수 도메인 타입 slice라 서비스 재기동 외 추가 복구
  절차가 없다.
