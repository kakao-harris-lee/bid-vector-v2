# M4/4B-6b Rollback

base_sha: `571059ab5ada307126a9dd35bc33b846dd4ae63f`(slice base, scope.md 정본 — verifier r1
F-3 정정. 이전 판은 계약 커밋 `9787329`를 base 로 잘못 적어 `milestone-4.md`(+12줄)를
목록에서 놓쳤다)
head_sha(코드 마지막 커밋): `d21cb21`(이 문서를 갱신하는 커밋 자신은 목록에 없다)

## 팀장 문서 레인 — 목록 제외 선언

다음 커밋은 `scope.md`·`milestone-4.md`(계약 문서)만 만진 팀장 문서 레인이다 —
구현 레인의 in_scope 산출물이 아니라 이 rollback 목록에서 제외한다:

```
$ git log --oneline 571059ab5ada307126a9dd35bc33b846dd4ae63f..d21cb21 -- milestone-4.md reports/evidence/m4/4b6b/scope.md
434a6e6 docs(m4-4b6b): 계약 갱신 — verifier r1 반영(...)
b902097 docs(m4-4b6b): 계약 갱신 — 파일 셋 분할(detekt)·recommendedAmountRounding 슬롯·OPEN-4B6B-BASE-AMOUNT-PROVENANCE 등재
9787329 docs(m4-4b6b): 착수 계약 고정 2026-09-12 — 조합기 OpportunityAnalysis(...)
```

## 변경 파일 목록(기계 산출, 위 문서 레인 제외)

`reports/evidence/m4/4b6b/scope.md`는 팀장 문서 레인이라(위 선언) pathspec 에서
직접 제외한다 — 제외하지 않으면 디렉터리 전체 pathspec 이 그 파일도 잡아 명령
출력과 이 목록이 어긋난다(verifier r2 실측). `milestone-4.md`는 이 디렉터리
바깥이라 애초에 이 pathspec 에 안 걸린다(제외 불필요, 참고로만 남긴다).

```
$ git diff --name-status 571059ab5ada307126a9dd35bc33b846dd4ae63f..d21cb21 -- workflow/ config/quality/gate-tests.properties reports/evidence/m4/4b6b/ ':!reports/evidence/m4/4b6b/scope.md'
M	config/quality/gate-tests.properties
A	reports/evidence/m4/4b6b/checklist.md
A	reports/evidence/m4/4b6b/commands.md
A	reports/evidence/m4/4b6b/policy-values.md
A	reports/evidence/m4/4b6b/rollback.md
M	workflow/build.gradle.kts
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
A	workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt
M	workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt
```

명령을 실제로 돌려 위 13줄과 정확히 일치함을 확인했다(verifier r2 재실측, 2026-09-12).
Rollback 절차 자체는 evidence 네 파일(checklist·commands·policy-values·rollback)을
개별 인자로 지정해 `scope.md`를 건드리지 않는다 — 목록 명령의 pathspec 제외와 별개로
실행 절차도 이중으로 안전하다.

## 공유 파일 확인 — `config/quality/gate-tests.properties`

이 range 안에서 이 파일을 만진 커밋은 전부 이 slice의 구현 레인이다(같은 시각 다른
코드 레인 없음, scope.md 명시) — 다른 레인과 섞이지 않아 hunk 격리 대신 파일 전체를
base 로 restore 해도 안전하다:

```
$ git log --oneline 571059ab5ada307126a9dd35bc33b846dd4ae63f..d21cb21 -- config/quality/gate-tests.properties
2f9fab9 fix(m4-4b6b): F-1(high) — MarginInputs 세 술어 전부를 호출 전에 판정
74d9ceb test(m4-4b6b): WorkflowGateRegistrationTest — workflow 게이트 등재 완전성(운영자 결정 2026-09-11 a)
```

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
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt \
  reports/evidence/m4/4b6b/checklist.md \
  reports/evidence/m4/4b6b/commands.md \
  reports/evidence/m4/4b6b/policy-values.md \
  reports/evidence/m4/4b6b/rollback.md

# 2) in_scope 편집 파일 + 공유 파일을 base 로 restore(경로 개별 인자)
git restore --source=571059ab5ada307126a9dd35bc33b846dd4ae63f --staged --worktree -- \
  workflow/build.gradle.kts \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt \
  config/quality/gate-tests.properties

# 3) 삭제도 인덱스에 반영
git add -A -- workflow/src/main/kotlin/bidvector/workflow/evaluation \
  workflow/src/test/kotlin/bidvector/workflow \
  workflow/build.gradle.kts \
  config/quality/gate-tests.properties \
  reports/evidence/m4/4b6b
```

## 확인(2026-09-08/09-09 규율 — 목록·diff 0·compile·test 전부)

- 목록 산출: `git diff --name-status <base>..<코드 마지막 커밋>`(위 블록, 라운드마다 재실행)
- diff 0: `git diff --name-status 571059ab5ada307126a9dd35bc33b846dd4ae63f -- workflow/ config/quality/gate-tests.properties reports/evidence/m4/4b6b/checklist.md reports/evidence/m4/4b6b/commands.md reports/evidence/m4/4b6b/policy-values.md reports/evidence/m4/4b6b/rollback.md` → 빈 출력(실측)
- compile+test: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :workflow:test :workflow:gateExecutionGate` → **exit 0**(실측)

## 실측 — 임시 clone

`git clone --no-hardlinks . /tmp/bv-4b6b-rollback-check-r1` → head 커밋(`d21cb21`)으로
checkout → 위 절차 그대로 실행 → `git diff --name-status <base> -- <in_scope 경로>` 빈
출력 확인 → `:workflow:compileKotlin :workflow:compileTestKotlin :workflow:test
:workflow:gateExecutionGate` **BUILD SUCCESSFUL** 확인 → clone 삭제. `cp -r` 미사용
(연결 worktree 오염 회피, m4d/4D-1 교훈).

## 롤백 후 알려진 영향

- `MlAnalysisPort`의 유일한 실 구현이 사라져 `EvaluateCandidatesUseCase`는 다시
  test fake(`FakeMlAnalysisPort`)로만 배선 가능한 상태로 돌아간다(6A 배선 slice
  전 단계와 동일) — 이 slice 도입 전과 완전히 같은 상태.
- `gate.tests.workflow`에서 신설 여섯(`WorkflowGateRegistrationTest`·
  `EmbeddingBridgeTest`·`OpportunityAnalysisTest`·`PredictionFactsTest`)과 이
  slice가 실측으로 찾아 등록한 기존 미등재 `LadderPolicySlotTest`도 함께 빠진다 —
  `LadderPolicySlotTest` 재등록은 이 slice가 아니라 그 test를 신설한 slice(4B-2)의
  몫으로 넘어간다(알려진 회귀 — 롤백이 이 slice 이전에 이미 있던 미등재 상태로
  정확히 되돌린다는 뜻이지 새 결함이 아니다).
- `workflow/build.gradle.kts`의 `gate-tests.properties` 입력 선언(F-2 수정)도
  함께 빠진다 — `:workflow:test`가 다시 그 파일 변경을 입력으로 못 본다(이 slice
  도입 전과 같은 상태로, 새 결함이 아니다).
