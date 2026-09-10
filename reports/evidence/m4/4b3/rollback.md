# rollback.md — M4/4B-3

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬, 2026-09-04 개정).
이 slice는 base `ff210c187bb7885da7639de0434d815e59e7f32a`에서 가른 전용 브랜치
`m4-4b3/2026-09-10`(worktree `bid-vector-v2-m4e`)에서 산다 — 하네스 레인 커밋(`CLAUDE.md`·
`.claude/**`)은 이 range에 0건(`git log --oneline ff210c187bb7885da7639de0434d815e59e7f32a..HEAD
-- CLAUDE.md .claude/` 결과 없음, scope.md 「하네스 레인 변경」 절). **병합 전에는 「브랜치를
버린다」가 곧 rollback**(scope.md 원문)이고, 아래는 병합 뒤(또는 병합 전 부분 되돌림이 필요할
때)를 대비한 경로 한정 절차다.

## 목록 산출 (기계적, 손으로 쓰지 않는다)

```
git diff --name-status ff210c187bb7885da7639de0434d815e59e7f32a..HEAD
```

**개수는 이 명령의 출력이 정본이다** — 산문에 옮겨 적지 않는다. 분류만 적는다:

- **A(신규, 전체 삭제 대상)**: `adapters/src/main/kotlin/bidvector/adapters/ml/
  UnavailableMlAnalysis.kt`·`adapters/src/test/kotlin/bidvector/adapters/ml/
  UnavailableMlAnalysisTest.kt`·`reports/evidence/m4/4b3/{scope.md,commands.md,
  checklist.md,rollback.md}`(이 파일 자신 포함 — 아래 「되돌리는 명령」 참고).
- **M(변경, 이 range 안에서는 이 slice 커밋만 — 브랜치 격리로 다른 slice와 겹치지 않음)**:
  `decision/src/main/kotlin/bidvector/decision/LadderInput.kt`·
  `decision/src/main/kotlin/bidvector/decision/VerdictLadder.kt`·
  `decision/src/test/kotlin/bidvector/decision/VerdictLadderTest.kt`·
  `workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt`·
  `workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt`·
  `workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseIsolationTest.kt`·
  `workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseTest.kt`·
  `workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt`.
- **M(변경, 병합 뒤 공유될 파일 — 줄 단위/hunk 격리 필수)**: `config/quality/gate-tests.properties`
  (scope.md 「레인 격리」 절 — `m4/2026-09-08`의 4C-2 lane이 `gate.tests.adapters` 키를
  같은 시기에 만진다. 이 range 자체는 이 slice 커밋만 갖지만 병합 뒤에는 공유 파일이 된다).

**라운드가 더 늘어 파일이 늘면 이 절차를 다시 돌린다** — 명령을 재실행해 목록을 다시 낸다.

## 되돌리는 명령

**A 항목(신규 파일)** — 전체 삭제. `git checkout <base> -- <경로>`는 쓰지 않는다(base에
없는 신규 경로마다 pathspec 오류로 exit 1, M1/1A 16차 교훈):

```
git restore --source=ff210c187bb7885da7639de0434d815e59e7f32a --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/UnavailableMlAnalysis.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/UnavailableMlAnalysisTest.kt \
  reports/evidence/m4/4b3/scope.md \
  reports/evidence/m4/4b3/commands.md \
  reports/evidence/m4/4b3/checklist.md \
  reports/evidence/m4/4b3/rollback.md
```

**M 항목(이 range 전용, 브랜치 격리로 안전)** — 같은 방식으로 base 상태 복원:

```
git restore --source=ff210c187bb7885da7639de0434d815e59e7f32a --staged --worktree -- \
  decision/src/main/kotlin/bidvector/decision/LadderInput.kt \
  decision/src/main/kotlin/bidvector/decision/VerdictLadder.kt \
  decision/src/test/kotlin/bidvector/decision/VerdictLadderTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseIsolationTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt
```

**`config/quality/gate-tests.properties`(공유 파일)** — 파일 전체 restore는 쓰지 않는다.
자기 커밋 해시로 hunk를 격리해 되돌린다(evidence-pack 2026-09-09 규격):

```
git log --oneline ff210c187bb7885da7639de0434d815e59e7f32a..HEAD -- config/quality/gate-tests.properties
git diff <sha>~1..<sha> -- config/quality/gate-tests.properties | git apply -R
```

이 slice가 그 파일을 만진 커밋은 하나뿐(등재 커밋, 아래 「실측」의 커밋 SHA)이라 순서
문제는 없다 — 커밋이 둘 이상으로 늘면 최신→과거 순으로 역적용한다(4D-1 verifier r4 I-3
선례).

## 실측(임시 clone, HEAD=`43619ce` — 이 slice의 마지막 evidence 커밋) — 5단계

절차 — 코드·test·gate 등재 커밋 전부 완료 → 목록 기계 산출 → 임시 clone → ①~⑤ → 이
문서에 기록 → evidence 커밋. 아래는 그 결과다.

① `git clone . /tmp/4b3-rollback-verify && cd /tmp/4b3-rollback-verify && git checkout 43619ce` — exit 0.
② 위 A 항목 `git restore` 명령 실행 — exit 0(경로 6개 전부 매치, pathspec 오류 없음).
②' M 항목(이 range 전용 8개) `git restore` 명령 실행 — exit 0.
②'' `config/quality/gate-tests.properties` hunk 격리 역적용(커밋 1건) — exit 0.
③ 대상 파일 전부(A 6 + M 8 + 공유 1 = 15)에 대해
   `git diff ff210c187bb7885da7639de0434d815e59e7f32a -- <경로>`가 비어 있음(15개 전부,
   `wc -l` → 0) — **내 줄이 사라졌다** 확인.
③' `gate-tests.properties`의 4D-1·4C-2 등 다른 slice 행(예: `gate.tests.adapters` 안
   `bidvector.adapters.ml.GrpcBidPredictionGatewayTest` 등 4D-1 등재분)이 그대로 남아
   있음을 `grep` 대조로 확인 — **남의 줄이 남았다** 확인.
④ `./gradlew --no-daemon :decision:compileKotlin :workflow:compileKotlin :adapters:compileKotlin :app:compileKotlin` — exit 0(BUILD SUCCESSFUL).
⑤ `./gradlew --no-daemon :decision:test :workflow:test --tests 'bidvector.workflow.evaluation.*' :adapters:compileTestKotlin :app:test --tests '*Conformance*'` — exit 0(BUILD SUCCESSFUL).

**결론** — A·M·공유 세 갈래 되돌림을 함께 적용하면 base 상태로 완전히 복원되고, 되돌린
트리는 컴파일·test 모두 통과한다. 병합 전 실제 되돌림은 「브랜치를 버린다」로 충분하다
(A·M 항목이 이 range에서 함께 사라지므로 문제가 안 됨). **경로 한정 부분 되돌림을 실제로
수행할 때는 A·M·공유 세 갈래를 항상 같이 돌려야 한다.**

**라운드가 더 늘어 파일이 늘면 이 5단계 전체를 이 라운드의 마지막 코드·test·gate 커밋에서
다시 잰다**(중간 커밋에서 재지 않는다).
