# M4/4B-6b Rollback

base_sha: `9787329712188c0a606b4afd90c1dcb33c1b9264`
head_sha: `74d9cebc9c82d1f2f78ad46c436937fce5246fdc`

## 변경 파일 목록(기계 산출)

```
$ git diff --name-status 9787329712188c0a606b4afd90c1dcb33c1b9264..HEAD
M	config/quality/gate-tests.properties
A	workflow/src/main/kotlin/bidvector/workflow/evaluation/EmbeddingBridge.kt
A	workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt
A	workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt
M	workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt
A	workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt
M	workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt
A	workflow/src/test/kotlin/bidvector/workflow/WorkflowGateRegistrationTest.kt
A	workflow/src/test/kotlin/bidvector/workflow/evaluation/EmbeddingBridgeTest.kt
A	workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt
A	workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt
M	workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt
M	workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt
```

## 공유 파일 확인 — `config/quality/gate-tests.properties`

이 range 안에서 이 파일을 만진 커밋은 이 slice의 `74d9ceb` 하나뿐이다(같은 시각
다른 코드 레인 없음, scope.md 명시):

```
$ git log --oneline 9787329712188c0a606b4afd90c1dcb33c1b9264..HEAD -- config/quality/gate-tests.properties
74d9ceb test(m4-4b6b): WorkflowGateRegistrationTest — workflow 게이트 등재 완전성(운영자 결정 2026-09-11 a)
```

다른 레인의 변경과 섞이지 않았으므로 **hunk 격리가 아니라 파일 전체를 base로
restore**해도 안전하다(2026-09-08 하네스 레율 — 겹치는 파일이 있을 때만 hunk
격리가 필요).

## Rollback 절차

```bash
# 1) in_scope 신설 파일 삭제
rm -f \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/EmbeddingBridge.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt \
  workflow/src/test/kotlin/bidvector/workflow/WorkflowGateRegistrationTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/EmbeddingBridgeTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt

# 2) in_scope 편집 파일 + 공유 파일을 base 로 restore(경로 개별 인자)
git restore --source=9787329712188c0a606b4afd90c1dcb33c1b9264 --staged --worktree -- \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt \
  config/quality/gate-tests.properties

# 3) 삭제도 인덱스에 반영
git add -A -- workflow/src/main/kotlin/bidvector/workflow/evaluation \
  workflow/src/test/kotlin/bidvector/workflow config/quality/gate-tests.properties
```

## 확인(2026-09-08/09-09 규율 — 목록·diff 0·compile·test 전부)

- 목록 산출: `git diff --name-status <base>..HEAD`(위 블록, 라운드마다 재실행)
- diff 0: `git diff --name-status 9787329712188c0a606b4afd90c1dcb33c1b9264 -- workflow/ config/quality/gate-tests.properties` → 빈 출력(실측)
- compile+test: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :workflow:test :workflow:gateExecutionGate` → **exit 0**(실측)

## 실측 — 임시 clone

`git clone --no-hardlinks . /tmp/bv-4b6b-rollback-check` → head 커밋으로 checkout →
위 절차 그대로 실행 → `git diff --name-status <base> -- <in_scope 경로>` 빈 출력 확인
→ `:workflow:compileKotlin :workflow:compileTestKotlin :workflow:test
:workflow:gateExecutionGate` **BUILD SUCCESSFUL** 확인 → clone 삭제. `cp -r` 미사용
(연결 worktree 오염 회피, m4d/4D-1 교훈).

## 롤백 후 알려진 영향

- `MlAnalysisPort`의 유일한 실 구현이 사라져 `EvaluateCandidatesUseCase`는 다시
  test fake(`FakeMlAnalysisPort`)로만 배선 가능한 상태로 돌아간다(6A 배선 slice
  전 단계와 동일) — 이 slice 도입 전과 완전히 같은 상태.
- `gate.tests.workflow`에서 신설 다섯(`WorkflowGateRegistrationTest`·
  `EmbeddingBridgeTest`·`OpportunityAnalysisTest`)과 이 slice가 실측으로 찾아
  등록한 기존 미등재 `LadderPolicySlotTest`도 함께 빠진다 — `LadderPolicySlotTest`
  재등록은 이 slice가 아니라 그 test를 신설한 slice(4B-2)의 몫으로 넘어간다(알려진
  회귀 — 롤백이 이 slice 이전에 이미 있던 미등재 상태로 정확히 되돌린다는 뜻이지
  새 결함이 아니다).
