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

- A(신규): `adapters/src/main/kotlin/bidvector/adapters/ml/`(`CandidateShapeValidation`·
  `FractionRules`·`GrpcBidPredictionGateway`·`MlCallPolicyData`·`MoneyMapping`·
  `ParsedSuccessFields`·`ReleaseCheck`·`ReleaseShapeValidation`·`RequestMapping`·
  `ResilientPredictionCall`·`ResponseMapping`·`RetryRules`)·
  `adapters/src/test/kotlin/bidvector/adapters/ml/`(`BreakerTest`·
  `DeadlineCancellationRetryTest`·`GrpcBidPredictionGatewayTest`·`MlAdapterDependencyTest`·
  `MlCallPolicyDataTest`·`MlGateRegistrationTest`·`MlTestFixtures`·`ReleaseCheckTest`·
  `ResponseMappingTest`·`SuccessShapeFailClosedTest`)·
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
  adapters/src/main/kotlin/bidvector/adapters/ml/CandidateShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/FractionRules.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/GrpcBidPredictionGateway.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/MoneyMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseCheck.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseShapeValidation.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResilientPredictionCall.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/ml/RetryRules.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/BreakerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/DeadlineCancellationRetryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/GrpcBidPredictionGatewayTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlTestFixtures.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/ReleaseCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/ResponseMappingTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/SuccessShapeFailClosedTest.kt \
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

**한 파일을 두 커밋 이상이 만졌으면 ②를 최신 커밋부터 과거 순으로 돌린다**(verifier r4
I-3 — 실측: `gate-tests.properties`처럼 같은 파일에 커밋이 둘이면 과거→최신 순으로
역적용할 때 나중 커밋의 hunk 컨텍스트가 먼저 커밋의 원문과 안 맞아 `apply -R` 자체가
`error: patch failed`로 죽는다. 최신 커밋을 먼저 역적용하면 그 다음 커밋의 hunk 가
기대하는 컨텍스트로 되돌아가 있어 순서대로 적용된다 — ①의 `git log` 출력이 이미 최신이
위인 순서이므로 **그 출력 순서 그대로 위에서부터** 돌리면 된다).

`adapters/build.gradle.kts`·`app/.../VerdictExecutors.kt`·`decision/.../ReviewReason.kt`·
`docs/discovery/{capability-map,data-dictionary}.md`·`milestone-4.md`·`adapters/src/test/
.../contract/{ContractFractionRules,ContractRetryRules,PredictionContractTest}.kt`는 이
range에서 4D-1 커밋만 건드렸음을 커밋 로그로 확인했다(위 「목록 산출」 대응 커밋 넷:
`e0f4fed`·`5065cab`·`2acf37a`·`a96c53c`) — 병합 뒤 다른 slice 커밋이 같은 파일을 추가로
만지면 ①②를 그 파일에 대해서만 다시 돌린다.

**verifier r2 G-3 재확인** — 수정 라운드 커밋(`7ffb379`·`fc42988`·`2161d2e`·`d405d10`·
`b81a723`) 각각에 대해 `git show --stat`으로 건드린 경로를 다시 대조했다: 다섯 전부
A 목록의 `adapters/ml/**`·`workflow/prediction/**`·evidence 파일만 건드렸고 위 M 목록
(공유 가능 파일) 아홉 개 중 어느 것도 건드리지 않았다.

**verifier r3 H-3 재확인(2026-09-10)** — 이번 라운드 커밋(`3472036`·`9bb67c3`)도 같은
방식으로 대조했다: `3472036`(H-1)은 A 목록 파일만(`ResilientPredictionCall.kt`·
`BreakerTest.kt`) 건드렸다. `9bb67c3`(H-2·H-4·H-6)은 A 목록 파일 둘(`SuccessShapeFail
ClosedTest.kt`·`MlGateRegistrationTest.kt`) 외에 **M 목록의 `config/quality/
gate-tests.properties`도 건드렸다** — 그 파일의 M 항목 hunk 커밋 목록에 `9bb67c3`를
추가한다(대응 커밋: `a96c53c`·`9bb67c3` 둘). 나머지 M 파일 여덟은 이번 라운드 커밋
둘 중 어느 것도 건드리지 않았다(기계 확인, 손으로 늘리지 않는다).

## 실측(임시 clone, 2026-09-10, HEAD=`9bb67c3` — 이 라운드의 마지막 코드·test 커밋) — 5단계

**절차 — verifier r3 H-3**: 목록 산출·5단계 실측은 이 라운드의 마지막 코드·test 커밋을
만든 **뒤에 한 번만** 돈다(evidence 커밋 자신은 A/M 목록에 들지 않는다) — 코드·test
커밋 전부 완료 → 목록 기계 산출 → 임시 clone → ①~⑤ → 이 문서에 기록 → evidence 커밋.
아래는 그 결과다.

**이전 실측(b81a723 대상) 은 이 실측으로 대체된다** — 그 뒤 커밋(`cbf769a`·`3472036`·
`9bb67c3`)이 A 목록에 파일을 더 추가했으므로(`CandidateShapeValidation.kt`는 `cbf769a`
시점엔 이미 A 목록에 있었으나 `SuccessShapeFailClosedTest.kt`·`MlGateRegistrationTest.kt`
는 `9bb67c3`에서 새로 생겼다), 그 시점 실측은 그 시점 A 목록에 대해서만 유효했다. 더
이전(a96c53c 대상) 실측이 거짓 양성이었던 사유(evidence 파일이 그 시점에 아직 없어
`git restore`가 pathspec 오류로 원자적 무동작 — 오류가 나면 어떤 경로도 되돌리지 않는다)
는 위 절에 남긴다(장부 정직성, 재발 방지 기록 — H-3 자체가 이 정정을 반복하지 않기 위한
절차 처방이다).

① `git clone . /tmp/4d1-rollback-verify-r3 && git checkout 9bb67c3` — exit 0.
② 위 A 항목 `git restore` 명령 실행 — exit 0(경로 33개 전부 매치, pathspec 오류 없음).
②' M 항목 hunk 격리 11건(`gate-tests.properties`는 두 커밋 — `9bb67c3`를 먼저, `a96c53c`를
   나중에 역적용해야 컨텍스트가 맞는다, 최신 커밋 먼저) — 전 11건 exit 0(hunk 충돌 없음).
③ 대상 파일 전부(A 33 + M 9)에 대해 `git diff d0a44a739864896d2ca894fbe8b286651d8f1156
   -- <경로>`가 비어 있음(`wc -l` → 0, 42개 전부) — **내 줄이 사라졌다** 확인.
④ `./gradlew --no-daemon :decision:compileKotlin :workflow:compileKotlin
   :adapters:compileKotlin :app:compileKotlin` — exit 0(BUILD SUCCESSFUL).
⑤ `./gradlew --no-daemon :decision:test :workflow:compileTestKotlin
   :adapters:test --tests 'bidvector.adapters.contract.*' :app:compileTestKotlin` —
   exit 0(BUILD SUCCESSFUL, 2A~2D contract test 전건 포함).

**결론(유지)** — A 목록만으로는 이 range의 rollback이 서지 않는다(M 항목
`PredictionContractTest.kt`가 A 항목 `ReleaseCheck.kt`의 심볼을 참조하기 때문, r2 G-3
재검증에서 처음 실측). 병합 전 실제 되돌림은 여전히 「브랜치를 버린다」로 충분하지만
(A 항목과 M 항목이 이 range에서 함께 사라지므로 문제가 안 됨), **경로 한정 부분
되돌림을 실제로 수행할 때는 A 목록 restore와 M 항목 hunk 격리를 항상 같이 돌려야
한다.** 라운드가 더 늘어 파일이 늘면 **이 라운드의 마지막 코드·test 커밋에서** 이
5단계 전체를 다시 잰다(H-3 절차 — 중간 커밋에서 재지 않는다).
