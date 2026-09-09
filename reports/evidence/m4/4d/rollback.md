# rollback.md — M4/4D-1

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬, 2026-09-04 개정).
이 slice는 base `d0a44a739864896d2ca894fbe8b286651d8f1156`에서 가른 전용 브랜치
`m4-4d/2026-09-10`(worktree `bid-vector-v2-m4e`)에서 산다 — 하네스 레인 커밋(`CLAUDE.md`·
`.claude/**`)은 이 range에 0건(`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 결과
없음). 다른 slice와 겹치는 파일도 없다 — `config/quality/gate-tests.properties`는 4B-2가
`m4/2026-09-08`(별도 브랜치)에서 같은 시기에 만지지만 이 range 자체는 이 slice 커밋만
갖는다(브랜치 격리, scope.md 「레인 격리」 절). **병합 전에는 「브랜치를 버린다」가 곧
rollback**(scope.md 원문)이고, 아래는 병합 뒤(또는 병합 전 부분 되돌림이 필요할 때)를
대비한 경로 한정 절차다.

## 목록 산출 (기계적, 손으로 쓰지 않는다)

```
git diff --name-status d0a44a739864896d2ca894fbe8b286651d8f1156..HEAD
```

**개수는 이 명령의 출력이 정본이다** — 산문에 옮겨 적지 않는다. 분류만 적는다:

- A(신규): `adapters/src/main/kotlin/bidvector/adapters/ml/`(`FractionRules`·
  `GrpcBidPredictionGateway`·`MlCallPolicyData`·`MoneyMapping`·`ParsedSuccessFields`·
  `ReleaseCheck`·`RequestMapping`·`ResilientPredictionCall`·`ResponseMapping`·`RetryRules`)·
  `adapters/src/test/kotlin/bidvector/adapters/ml/`(`BreakerTest`·
  `DeadlineCancellationRetryTest`·`GrpcBidPredictionGatewayTest`·`MlAdapterDependencyTest`·
  `MlCallPolicyDataTest`·`MlTestFixtures`·`ReleaseCheckTest`·`ResponseMappingTest`)·
  `decision/src/test/kotlin/bidvector/decision/MlUnavailableReasonTest.kt`·
  `workflow/src/main/kotlin/bidvector/workflow/prediction/`(`BidPredictionOutcome`·
  `BidPredictionPort`·`BidPredictionRequest`·`CallBudget`)·
  `workflow/src/test/kotlin/bidvector/workflow/prediction/`(`PredictionBoundaryTest`·
  `PredictionValueTest`)·`reports/evidence/m4/4d/`(이 파일 자신 제외 — 아래 「되돌리는
  명령」 참고).
- M(변경, 줄 단위 — 다른 slice와 공유될 수 있는 파일): `adapters/build.gradle.kts`·
  `adapters/src/test/kotlin/bidvector/adapters/contract/{ContractFractionRules,
  ContractRetryRules,PredictionContractTest}.kt`·`app/src/test/kotlin/bidvector/app/
  conformance/VerdictExecutors.kt`·`config/quality/gate-tests.properties`·
  `decision/src/main/kotlin/bidvector/decision/ReviewReason.kt`·
  `docs/discovery/capability-map.md`·`docs/discovery/data-dictionary.md`·`milestone-4.md`.
- M(전체 복원 — 이 slice 자신의 계약 문서, 다른 slice와 겹치지 않음):
  `reports/evidence/m4/4d/scope.md`.

**라운드가 더 늘어 파일이 늘면 이 절차를 다시 돌린다** — 명령을 재실행해 목록을 다시 낸다.

## 되돌리는 명령

**A 항목(신규 파일)** — 전체 삭제. `git checkout <base> -- <경로>`는 쓰지 않는다(base에
없는 신규 경로마다 pathspec 오류로 exit 1, 1A 16차 교훈):

```
git restore --source=d0a44a739864896d2ca894fbe8b286651d8f1156 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/FractionRules.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/GrpcBidPredictionGateway.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MoneyMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseCheck.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResilientPredictionCall.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RetryRules.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/BreakerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/DeadlineCancellationRetryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/GrpcBidPredictionGatewayTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlTestFixtures.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/ReleaseCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/ResponseMappingTest.kt \
  decision/src/test/kotlin/bidvector/decision/MlUnavailableReasonTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt \
  workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionPort.kt \
  workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt \
  workflow/src/main/kotlin/bidvector/workflow/prediction/CallBudget.kt \
  workflow/src/test/kotlin/bidvector/workflow/prediction/PredictionBoundaryTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/prediction/PredictionValueTest.kt \
  reports/evidence/m4/4d/scope.md \
  reports/evidence/m4/4d/commands.md \
  reports/evidence/m4/4d/checklist.md \
  reports/evidence/m4/4d/policy-values.md
```

**M 항목** — 이 range 안에서는 각 파일을 4D-1 혼자 만졌으나(브랜치 격리), 병합 뒤에는
`config/quality/gate-tests.properties`가 4A/4B-2/4C-1/4E와 같은 파일을 공유하게 되므로
**파일 전체 restore는 쓰지 않는다** — 자기 커밋 해시로 hunk를 격리해 되돌린다
(evidence-pack 2026-09-09 규격):

```
git log --oneline d0a44a739864896d2ca894fbe8b286651d8f1156..HEAD -- <파일>   # ① 이 파일을 만진 커밋 나열
git diff <sha>~1..<sha> -- <파일> | git apply -R                              # ② 자기 커밋만 역적용
```

`adapters/build.gradle.kts`·`app/.../VerdictExecutors.kt`·`decision/.../ReviewReason.kt`·
`docs/discovery/{capability-map,data-dictionary}.md`·`milestone-4.md`·`adapters/src/test/
.../contract/{ContractFractionRules,ContractRetryRules,PredictionContractTest}.kt`는 이
range에서 4D-1 커밋만 건드렸음을 커밋 로그로 확인했다(위 「목록 산출」 대응 커밋 넷:
`e0f4fed`·`5065cab`·`2acf37a`·`a96c53c`) — 병합 뒤 다른 slice 커밋이 같은 파일을 추가로
만지면 ①②를 그 파일에 대해서만 다시 돌린다.

## 실측(임시 clone, 2026-09-10) — 5단계

① `git clone . /tmp/4d1-rollback-verify && git checkout a96c53c` — exit 0.
② 위 A 항목 `git restore` 명령 실행 — exit 0.
③ 대상 파일 전부에 대해 `git diff d0a44a739864896d2ca894fbe8b286651d8f1156 -- <경로>`가
   비어 있음(restore 뒤 `wc -l` → 0) — **내 줄이 사라졌다** 확인.
④ `./gradlew --no-daemon :decision:compileKotlin :workflow:compileKotlin
   :adapters:compileKotlin :app:compileKotlin` — exit 0(BUILD SUCCESSFUL).
⑤ `./gradlew --no-daemon :decision:test :workflow:compileTestKotlin
   :adapters:test --tests 'bidvector.adapters.contract.*' :app:compileTestKotlin` —
   exit 0(BUILD SUCCESSFUL, 2A~2D contract test 전건 포함).

M 항목(gate-tests.properties 등)은 이 range 전용이라 병합 전에는 hunk 격리 실측이
의미가 없다(전체 되돌림 = A 항목과 함께 브랜치를 버리는 것과 동일) — 병합 뒤 실제로
다른 slice와 공유되는 시점에 ①②(hunk 격리) 명령을 재실측한다.
