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

**verifier r2 G-3 재확인(2026-09-10)** — 수정 라운드 커밋(`7ffb379`·`fc42988`·`2161d2e`·
`d405d10`·`b81a723`) 각각에 대해 `git show --stat`으로 건드린 경로를 다시 대조했다: 다섯
전부 A 목록의 `adapters/ml/**`·`workflow/prediction/**`·evidence 파일만 건드렸고 위 M
목록(공유 가능 파일) 아홉 개 중 어느 것도 건드리지 않았다 — M 항목 대응 커밋 넷은
갱신 대상이 아니다(기계 확인, 손으로 늘리지 않는다).

## 실측(임시 clone, 2026-09-10, HEAD=`b81a723`) — 5단계, 재검증

**이전 실측(a96c53c 대상) 정정** — 재검증 중 이전 기록이 거짓 양성이었음을 발견했다:
그 clone에서 A 항목 `git restore` 한 명령에 `reports/evidence/m4/4d/{commands,checklist,
policy-values}.md`가 섞여 있었는데, `a96c53c` 시점에는 그 evidence 파일들이 아직
생기기 전이라 pathspec 오류가 났다 — `git restore`는 원자적이라 **오류가 나면 어떤
경로도 되돌리지 않는다**(`git status --porcelain` 재확인, 변경 0건). 즉 그 실측은
「원본 그대로의 트리」를 컴파일·테스트한 것이라 통과가 rollback 을 아무것도 증명하지
않았다. 아래는 evidence 파일이 전부 존재하는 HEAD(`b81a723`)를 대상으로 다시 잰
결과다.

① `git clone . /tmp/4d1-rollback-verify-r2 && git checkout b81a723` — exit 0.
② 위 A 항목 `git restore` 명령 실행 — exit 0(경로 28개 전부 매치, pathspec 오류 없음).
③ 대상 파일 전부에 대해 `git diff d0a44a739864896d2ca894fbe8b286651d8f1156 -- <경로>`가
   비어 있음(restore 뒤 `wc -l` → 0, 28개 전부) — **내 줄이 사라졌다** 확인.

**②만으로는 ④가 막힌다** — A 항목만 되돌리면 `PredictionContractTest.kt`(M 항목)가 이
range에서 얻은 `import bidvector.adapters.ml.releaseSatisfiesSelector`(2acf37a, 승격 전
private 함수를 지우고 얻은 import)가 방금 삭제한 `ReleaseCheck.kt`를 가리켜
`:adapters:compileTestKotlin`이 `Unresolved reference` 로 죽는다(실측 확인,
verifier r2 G-3가 지목한 것과는 다른 새 gap — A 목록 문제가 아니라 A+M 을 같이
되돌려야 한다는 gap). **가짜 rollback 을 문서에 남기지 않으려고** M 항목 hunk 격리
(①②, 위 「M 항목」절)를 이 범위의 M 파일 9개 전부에 대해 이어서 적용했다:

```
for f, sha in [(adapters/build.gradle.kts, 2acf37a),
  (adapters/src/test/.../contract/ContractFractionRules.kt, 2acf37a),
  (adapters/src/test/.../contract/ContractRetryRules.kt, 2acf37a),
  (adapters/src/test/.../contract/PredictionContractTest.kt, 2acf37a),
  (app/src/test/.../VerdictExecutors.kt, e0f4fed),
  (config/quality/gate-tests.properties, a96c53c),
  (decision/src/main/.../ReviewReason.kt, e0f4fed),
  (docs/discovery/capability-map.md, a96c53c),
  (docs/discovery/data-dictionary.md, a96c53c),
  (milestone-4.md, a96c53c)]:
  git diff <sha>~1..<sha> -- <f> | git apply -R
```

전 10건 exit 0(hunk 충돌 없음 — 이 range에서 각 파일을 한 커밋만 건드렸기 때문, 위
「M 항목」 절 참고).

④ `./gradlew --no-daemon :decision:compileKotlin :workflow:compileKotlin
   :adapters:compileKotlin :app:compileKotlin` — exit 0(BUILD SUCCESSFUL).
⑤ `./gradlew --no-daemon :decision:test :workflow:compileTestKotlin
   :adapters:test --tests 'bidvector.adapters.contract.*' :app:compileTestKotlin` —
   exit 0(BUILD SUCCESSFUL, 2A~2D contract test 전건 포함).

**결론** — A 목록만으로는 이 range의 rollback이 서지 않는다(M 항목 하나가 A 항목
심볼을 참조하기 때문). 병합 전 실제 되돌림은 여전히 「브랜치를 버린다」로 충분하지만
(A 항목과 M 항목이 이 range에서 함께 사라지므로 문제가 안 됨), **경로 한정 부분
되돌림을 실제로 수행할 때는 A 목록 restore와 M 항목 hunk 격리를 항상 같이 돌려야
한다** — 이 절이 그 순서를 성문화한다. 라운드가 더 늘어 파일이 늘면 이 5단계 전체를
다시 잰다(다음 재검증에서 이 결론 문단이 여전히 맞는지도 함께 확인).
