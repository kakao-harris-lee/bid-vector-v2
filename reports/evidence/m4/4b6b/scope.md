# Slice 계약 — M4 / 4B-6b · opportunity 조합기 `OpportunityAnalysis : MlAnalysisPort` — **착수 계약 2026-09-12**

> **지위**: 운영자 결정 2026-09-11(「추천 방식으로 진행」 — 4B-6b 순차 착수, 등재 완전성 test 포함). 4B-6 은 둘로 갈렸다 — **4B-6a**(텍스트 합성 규약 v1·키워드
> 매칭·프로필/workload port, 종결 2026-09-11)와 이 slice **4B-6b**(조합기). 임베딩 port·값 타입·gateway 는 **4D-2 소유**(정본 `reports/evidence/m4/4d2/scope.md` D-4D2-1,
> 종결·병합 2026-09-11) — 이 slice 는 `bidvector.workflow.embedding.EmbedTextPort` 를 **소비**한다. 선행 조건(4D-2 병합)은 충족됐다(`main` 에 `80e86bc`).
> 조사 digest: `_workspace/m4-4b6b/01_signature_digest.md`(읽기 전용 탐색, 2026-09-11). 설계 검토: `_workspace/m4-4b6b/02_design-review.md`(세션 모델 단독).
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m4-4b6b/2026-09-12`. 같은 시각 다른 코드 레인은 없다(5A 는 PR #6 로 종결).

```yaml
milestone: m4
slice: 4b6b-opportunity-analysis-composer
base_sha: 571059ab5ada307126a9dd35bc33b846dd4ae63f   # 4B-6a·5A 병합 뒤 main(PR #6 head)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt      # 조합기 — MlAnalysisPort 실 구현
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/EmbeddingBridge.kt          # EmbeddingVector → UnitVector(재정규화) · EmbeddingUnavailableReason → MlUnavailableReason · DerivationAbsence → MlUnavailableReason (internal 순수 함수)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt            # 4B-6a 파일 편집 허용 — evaluation.TextKind 삭제, SynthesizedText.kind 를 bidvector.workflow.embedding.TextKind 로(D-4B6B-1)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt    # 슬롯 추가: embeddingBudget·predictionBudget(Duration)·releaseSelector·objective·categoryOffset (D-4B6B-5, 값은 policy-values.md)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt  # 경로 전수(아래 ⑤)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/EmbeddingBridgeTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt   # FakeEmbedTextPort(결정적 단위 벡터·release 고정)·FakeBidPredictionPort·FakeOperatorProfilePort·FakeWorkloadPort
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt        # TextKind 참조 교체만(golden 불변)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt   # 새 슬롯 불변식·출하 값
  - workflow/src/test/kotlin/bidvector/workflow/WorkflowGateRegistrationTest.kt        # 등재 완전성(운영자 결정 2026-09-11 (a)) — gate.tests.workflow ↔ src/test 재귀 스캔, 양방향
  - config/quality/gate-tests.properties                                              # gate.tests.workflow 에 신설 test 등재(줄 단위 공유 파일)
  - milestone-4.md, reports/evidence/m4/4b6b/**                                        # policy-values.md = OPEN-4B6B-POLICY-VALUES
out_of_scope:
  - workflow/embedding/** · workflow/prediction/** · adapters/** (4D-1·4D-2 소유 — 소비만. `UnavailableMlAnalysis` 는 production 배선이 없어(digest §3) 삭제하지 않는다 → 6A 배선 slice 가 처분)
  - decision/** (4B-4·4B-5 소비만 — `deriveExpectedMargin` 이 `UnitScore` 를 직접 내는 형태·`FloorRateOutOfRange` 어휘 그대로 사용)
  - workflow/evaluation/{Ports,EvaluateCandidatesUseCase}.kt (4B-2·4B-3 — **무편집**. `CallBudget` 은 조합기가 정책에서 만든다, use case 시그니처 불변)
  - 유사 공고(비성분)·pgvector · 시장 평균(`OPEN-4B5-COMPETITIVENESS`, `competitivenessNotCollected()`) · STR-05(`categoryOffset` 슬롯 값 0 고정, 산식 후속) · 확률 축(영구 null) · 프로필 저장·편집(M6) · workload 집계(port 소비만) · 경쟁 표본 조립(`competitionSamples = emptyList()`) · 임베딩·예측 캐시 · app 모듈 DI 배선(6A)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0 — 격리 트리
  - "./gradlew --no-build-cache --no-daemon clean check"                                              # S-1 — leakPatternGate 포함
  - "./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*'"                  # S-2
  - "./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'"                        # S-3 — adapters·contract·grpc 참조 0(embedding·prediction 은 bidvector.workflow 루트로 허용)
  - "./gradlew --no-daemon :workflow:gateExecutionGate"                                               # S-4
  - "./gradlew --no-daemon :workflow:test --tests '*WorkflowGateRegistrationTest*'"                   # S-5 — 등재 완전성(양성: 등재 한 줄 삭제 → 붉음, 실측 뒤 원복)
  - "./gradlew --no-daemon :adapters:test --tests '*UnavailableMlAnalysisTest*'"                      # S-6 — 4B-3 fail-safe 경로 무영향(use case 무편집 증명)
  - "grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation; test $? -eq 1"   # S-7
  - "./gradlew qualityBaseline"                                                                        # S-8
rollback: |
    **정본 `reports/evidence/m4/4b6b/rollback.md`**(구현 레인). 신설 파일 삭제 + 편집한 4B-6a 파일 둘(`TextSynthesis.kt`·`OpportunityPolicyData.kt`)·test 둘은 base 로 restore.
    공유 파일 `gate-tests.properties`·`milestone-4.md` 는 hunk 격리(`git diff <sha>~1..<sha> -- <파일> | git apply -R`; `--3way` 실패 시 수동 절차를 미리 적는다).
    문서 커밋과 목록 갱신 커밋 분리, 목록은 자기 커밋을 가리키지 않는다. 임시 clone(`git clone --no-hardlinks`) 에서 restore → in_scope diff 0 → `:workflow:compileKotlin compileTestKotlin test gateExecutionGate` 실측.
```

작성: 2026-09-12, 세션 모델 단독. 근거: `milestone-4.md` 4B 「사다리 입력 = ML 점수 셋」·4B-6a 종결 문단 · 4B-3 scope(`MlAnalysisOutcome.Unavailable`) · 4B-4 scope(D-4B4-1~5, `composePriority` 규율) ·
4B-5 scope(③④ 산식·G-1 인계·D-4B5-1) · 4D-1 scope(`BidPredictionPort`) · 4D-2 scope(D-4D2-1, `EmbedTextPort` suspend + `CallBudget`) · ADR 0010 D-1(deadline 필수)·D-6(미가용 하나·fail-safe) · ADR 0005 D-9 · `OPEN-4D-LADDER-SCORE-SOURCE` (a).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점에 재실행해 등재한다.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **조합기** `OpportunityAnalysis(embed: EmbedTextPort, prediction: BidPredictionPort, profile: OperatorProfilePort, workload: WorkloadPort, watchSubjects: WatchSubjectPort, capacity: CapacityPort, clock: Clock, 정책 슬롯 셋) : MlAnalysisPort` — `analyze(notice, correlationId)` 가 fact 를 값으로 바꾸고 4B-4·4B-5 커널을 부른다. **주 생성자 `internal`** + 보조 생성자 public(정책 슬롯은 출하 정본 고정 — 4B-2 규율 「`object` 커널을 세야 할 때 무엇을 주입하는가」: 주입은 port 여섯과 Clock 뿐, 판정 함수·정책은 주입하지 않는다) | M4 4B · 4B-3 ⑤ 승계 · (2b) 고정 항목 |
| ② | **경로**(순서 고정): (1) `watchSubjects.subjectFor(notice)` → `Unavailable` → `MlAnalysisOutcome.Unavailable(ScoreNotProvided)` (2) `profile.current()` null → `Unavailable(ScoreNotProvided)`(프로필 미설정 = match 불가 = 사다리 점수 없음) (3) `synthesizeNoticeText`·`synthesizeProfileText` → 하나라도 `Empty` → `Unavailable(InvalidRequest)`(2E 가 공백을 거부하는 것과 같은 판정을 호출 전에) (4) `embed(EmbedTextRequest(text, kind, releaseSelector, correlationId), CallBudget(policy.embeddingBudget))` **둘 순차**(notice → profile) → 하나라도 `Unavailable(reason)` → `Unavailable(bridge(reason))`; 둘의 `release` 가 다르면 `Unavailable(ReleaseMismatch)`(D-4B6B-3) (5) `EmbeddingVector → UnitVector`(재정규화, D-4B6B-2) → `SemanticMatch.of(notice, profile, policy.categoryOffset, priorityPolicy)` → `DimensionMismatch` → `Unavailable(ContractViolation)` · `OffsetOutOfRange` → `Unavailable(InvalidRequest)`(정책 오류는 fail-closed) (6) 예측: `notice.baseAmount` null → 예측 생략, budgetCapture·expectedMargin `Absent(ScoreNotProvided)`; 있으면 `prediction.predict(BidPredictionRequest(...), CallBudget(policy.predictionBudget))` → `Predicted` 면 두 성분 산출, `Unmeasurable` → 두 성분 `Absent(ScoreNotProvided)`, `Unavailable(reason)` → 두 성분 `Absent(reason)` — **가격 예측 미가용 ≠ 사다리 미가용**(D-4B6B-4) (7) 파생: `deriveUrgency(deadlineAt − clock.now)` · `deriveLoadRatio(snapshot)` · `workload.current()` · `competitivenessNotCollected()` · `deriveExecutionComplexity(ComplexityInputs(budget = baseAmount, keywordHits = KeywordHitsCounter.count(subject.fullText), remaining, loadRatio, match, capacity))` (8) `composePriority(PriorityInputs(...), priorityPolicy)` → `Composed(priority)` → `Analyzed(priority, probability = null, matched = match)` · `Unavailable(reason)` → `Unavailable(reason)` | 4B-4 D-4B4-1(match Absent → Unavailable) · 4B-5 ③④ · ADR 0010 D-6 |
| ③ | **`floorRate` 관문**(4B-5 G-1 인계, D-4B6-4): `notice.floorRate?.rate.fraction > 1` 이면 `MarginInputs` 를 만들지 않고 expectedMargin `Absent(bridge(FloorRateOutOfRange))`; ≤ 1 이면 `MarginInputs(recommendedRate = candidates.base, floorRate, predictedRate = candidates.base(D-4B6B-6), priceFitness = fitness→UnitScore(범위 밖 → 두 성분 Absent(ContractViolation)), capacity = 1 − loadRatio(D-4B6B-7))` · budgetCapture: `recommended = (baseAmount × BidRate(candidates.base)) 반올림`(shared-kernel `MoneyArithmetic`, `MoneyArithmeticUnmeasurable` → Absent) | 4B-5 ③·G-1 |
| ④ | **브리지**(`EmbeddingBridge.kt`, internal 순수 함수): `EmbeddingUnavailableReason(10) → MlUnavailableReason` 이름 1:1 `when`(소진, else 없음) · `DerivationAbsence → MlUnavailableReason`: 전부 `ScoreNotProvided`(fact 부재는 「값 미제공」) 단 `FloorRateOutOfRange → InvalidRequest` · `EmbeddingVector(List<Float>, ε 0.01) → UnitVector(List<BigDecimal>, ε priorityPolicy.normEpsilon 0.0001)`: 값을 BigDecimal 로 옮기고 **L2 norm 으로 나눠 재정규화** 뒤 생성(재정규화 없이는 4B-4 ε 를 못 만족한다 — digest §4·§6) | 4B-4 `UnitVector` init · 4D-2 `EmbeddingVector` init |
| ⑤ | **test 경로 전수**: 정상(Analyzed, probability null, matched = match) · subject Unavailable · profile null · 합성 Empty(notice/profile 각각) · embed Unavailable 10 사유 각각 → 같은 이름 · release 불일치 → ReleaseMismatch · 차원 불일치 → ContractViolation · offset 정책 밖 → InvalidRequest · baseAmount null → 두 성분 Absent 이면서 **Analyzed 유지** · 예측 Unavailable(DeadlineExceeded) → Analyzed 유지 + 두 성분 drop · 예측 Unmeasurable → 같음 · floorRate > 1 → margin drop · fitness 범위 밖 → drop · 재정규화(Float 벡터 ε 0.009 → UnitVector 성립) · 예산: fake 가 받은 `CallBudget.remaining == policy 값` · 호출 순서·횟수(embed 2·predict ≤1) · 같은 입력 두 번 → 같은 결과(무상태) · use case 통합 1건(실 `EvaluateCandidatesUseCase` + 이 조합기 + fake 들 → 사다리 판정까지) | 4B-3 ⑤ · 4B-4 (4) 우회 |
| ⑥ | **등재 완전성** `WorkflowGateRegistrationTest`: `src/test/kotlin/bidvector/workflow` 를 `walkTopDown` 으로 훑어 `*Test.kt` FQCN 집합 ↔ `gate.tests.workflow` 집합 **양방향** 차집합 빈 것(누락·잉여 둘 다) + 양성 대조 1. 자기 자신도 등재 | 운영자 결정 2026-09-11 (a) · 4B-6a verifier 참고 |

**만들지 않는 것**: 임베딩·예측 port/gateway 편집 · use case 편집 · 캐시 · 시장 평균 · STR-05 산식 · 확률 축 · app 배선 · `UnavailableMlAnalysis` 삭제.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4B6B-1** | `TextKind` 를 **4D-2 판(`bidvector.workflow.embedding.TextKind`) 하나로** — 4B-6a `evaluation.TextKind` 삭제, `SynthesizedText.kind` 타입 교체. 매핑 함수를 두지 않는다(값 집합 동일, 두 타입은 중복) | digest §8 · 중복 최소화 | 계약 고정 |
| **D-4B6B-2** | 벡터 변환은 **재정규화 포함**. `EmbeddingVector` ε 0.01 은 전송 안전판, `UnitVector` ε 0.0001 은 코사인 정밀도 — 둘 사이를 재정규화가 잇는다. 차원은 그대로(불일치는 `SemanticMatch` 가 판정) | digest §4·§6 | 계약 고정 |
| **D-4B6B-3** | notice·profile 임베딩의 `ModelReleaseRef` 가 다르면 `Unavailable(ReleaseMismatch)` — 서로 다른 release 의 벡터 공간은 비교 불가. `releaseSelector` 는 둘에 같은 값 | ADR 0010 D-6 | 계약 고정 |
| **D-4B6B-4** | **가격 예측 미가용은 사다리 미가용이 아니다** — budgetCapture·expectedMargin 두 성분만 `Absent(reason)` 로 빠지고 4B-4 재정규화가 흡수. 사다리 미가용은 match 축(subject·profile·합성·임베딩)에서만 | 4B-4 D-4B4-1 · 4B-5 D-4B5-1 | 계약 고정 |
| **D-4B6B-5** | 정책 슬롯 추가: `embeddingBudget: Duration`·`predictionBudget: Duration`(ADR 0010 D-1 deadline 필수 — 조합기가 호출마다 `CallBudget` 을 만든다, use case 는 예산을 모른다)·`releaseSelector = LatestPromoted`·`objective = SCENARIO_TRIPLE`·`categoryOffset = 0`. **값은 `policy-values.md`(`OPEN-4B6B-POLICY-VALUES`) 승인 대상** — 추천: embedding 2s · prediction 3s(adapters 정책은 참조 금지 — 대조 근거는 policy-values.md 산문으로) | ADR 0010 D-1 · 4B-2 규율 | 값 승인 대기 |
| **D-4B6B-6** | `predictedRate = candidates.base`, `recommendedRate = candidates.base` — M2 계약은 「예측 낙찰율」을 별도로 나르지 않는다(ML-03·D-M2-8). alignment 성분(가중치 .20)이 **상수 1** 이 되므로 알려진 제한·`OPEN-4B6B-PREDICTED-RATE`(전략이 시나리오를 고르는 slice 또는 M2 additive 확장에서 해소) | 4B-5 ③ · M2 계약 | 계약 고정(운영자 즉답 선택지: (a) 위·추천 (b) `predictedRate = candidates.conservative` — 의미 근거 없음) |
| **D-4B6B-7** | `capacity(UnitScore) = 1 − loadRatio` — 용량 여유. `CapacitySnapshot` 부재는 구조상 없음(`CapacityPort` 항상 값) | 4B-5 ③ 「용량」 | 계약 고정 |

---

## 위협 모델 — 4B-6b 고유 경계

**방어한다**: (a) 문자열·벡터 직접 주입 — 합성은 4B-6a 타입, 벡터는 4D-2 port 결과만(`analyze` 인자는 `Notice`·`CorrelationId` 뿐이라 조합기 밖에서 벡터를 넣을 자리가 없다) (b) 미가용을 0 점으로 — 모든 부재는 `Absent`/`Unavailable`, 숫자 기본값 없음 (c) 예산 없는 원격 호출 — `CallBudget` 정책 필수 (d) release 혼합 — D-4B6B-3 (e) 정책 오류 fail-open — `OffsetOutOfRange` → `InvalidRequest` (f) 판정 함수 주입 — 주 생성자 `internal`, 보조 생성자는 port 만.
**방어하지 않는다**: 임베딩 품질·모델 옳음(M5) · gateway 의 deadline 준수(4D-1·4D-2) · use case 의 판정(4B-1~3) · 프로필 내용의 진실성(M6).

**우회 후보(≥5)**: (1) fake `EmbedTextPort` 가 정규화 안 된 벡터 반환 → `EmbeddingVector` init 이 거부(4D-2) — 조합기는 그 뒤 재정규화만 (2) 두 임베딩 release 다름 → ReleaseMismatch (3) `profile.current()` 가 빈 `ProfileFacts` → `synthesizeProfileText` Empty → InvalidRequest (4) 예측이 `fitness` 1.5 반환 → 두 성분 Absent(ContractViolation), Analyzed 유지 (5) `floorRate` 1.2 → margin Absent(InvalidRequest via FloorRateOutOfRange) (6) 정책 `categoryOffset` 0.5 → OffsetOutOfRange → InvalidRequest (7) 예산 0 → `OpportunityPolicyData.init` 가 > 0 강제(`CallBudget` init 과 짝) (8) 같은 notice 로 두 번 → 무상태이므로 결과 동일.

---

## (2b) 값 획득 축

| 표면 | 가시성 | 판정 |
| --- | --- | --- |
| `OpportunityAnalysis` 주 생성자 | internal | 닫는다(판정 함수·정책 주입 불가) |
| `OpportunityAnalysis` 보조 생성자(port 여섯·Clock) | public | 연다 — port 주입은 없던 권한이 아니다(use case 가 이미 port 를 받는다) |
| `EmbeddingBridge` 함수 셋 | internal | 닫는다 |
| `OpportunityPolicyData` 새 슬롯 | public data | 연다(정책 입력) — `init` 이 예산 > 0 강제. offset 범위는 `init` 이 아니라 `SemanticMatch.of` 가 `OffsetOutOfRange` 로 fail-closed(verifier r1 F-5 정정) |
| `WorkflowGateRegistrationTest` | test | — |

수정 라운드마다 이 표를 갱신한다(「이번 수정이 새 public 표면을 만들었는가」).

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-4B6B-POLICY-VALUES` | D-4B6B-5 값(예산 둘·selector·objective·offset 0) — 종결 시 승인 |
| `OPEN-4B6B-PREDICTED-RATE` | D-4B6B-6 — alignment 상수 1, 해소는 전략 시나리오 선택 또는 M2 additive |
| `OPEN-4B5-COMPETITIVENESS` | 수령 유지(`competitivenessNotCollected()`) |
| `OPEN-4B6-PROFILE-SOURCE` | 수령 유지(M6 — port 소비만) |
| 4B-6a F-2(절단 단위 코드포인트 vs 2E 판정) | 이 slice test 로 고정: `EmbedTextRequest.text` 의 상한 판정 단위(4D-2 gateway·2E `max-chars`)와 4B-6a 절단 단위 대조, 어긋나면 알려진 제한 |
| `UnavailableMlAnalysis` 삭제 | 6A(app 배선)로 인계 |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-12 (구현 뒤, 팀장 등재) | **in_scope 추가**: `workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt`(순수 파이프라인 변환)·`PredictionFacts.kt`(예측·마진 파생) — 조합기를 port I/O(class)/순수 변환/예측 파생 셋으로 나눔. **D-4B6B-5 슬롯 추가**: `recommendedAmountRounding: RoundingPolicy`(scale 0·HALF_UP, 코드베이스 관례) — `baseAmount × BidRate → BidAmount` 가 `MoneyArithmetic.roundedWith` 의 반올림 정책을 요구하고, 4B-5 `budgetCaptureRounding`(scale 6)은 다른 용도라 재사용하지 않음. 값은 `policy-values.md` §4, `OPEN-4B6B-POLICY-VALUES` 에 포함 | detekt `TooManyFunctions`(11/파일) 게이트가 단일 파일을 거부(commands.md S-1 라운드) · shared-kernel 금액 연산 API 의 필수 인자. 둘 다 새 public 표면이 아니라 파일 분할·정책 슬롯(verifier 표적: (2b) 표 재확인) |
| 2026-09-12 (verifier r1 뒤) | **in_scope 추가** `workflow/build.gradle.kts` — `test` task 의 입력에 `config/quality/gate-tests.properties` 를 선언(F-2: 선언이 없어 파일만 바뀌면 task 가 UP-TO-DATE 로 건너뛰어 등재 완전성 test 가 거짓 초록). **F-1 관문 확장**: `MarginInputs.init` 의 세 술어(recommendedRate·predictedRate·floorRate ≤ 1) 전부를 `PredictionFacts` 가 호출 전에 판정 — 후보율 > 1 은 계약 위반이라 두 성분 `Absent(ContractViolation)`, 예외 0. **rollback base 는 slice base `571059a`**(계약 커밋 `9787329`·`b902097` 은 팀장 문서 레인 — 목록 제외 선언) | verifier r1 F-1(high)·F-2(medium)·F-3(low) |
| 2026-09-12 (구현 뒤) | OPEN 신설 `OPEN-4B6B-BASE-AMOUNT-PROVENANCE` — `Notice` 가 `BaseAmountProvenance` 축을 나르지 않아 `BidPredictionRequest.baseAmountProvenanceLabel` 을 `Unknown` 고정. 해소는 procurement 가 provenance 를 fact 로 나르는 slice | 구현 실측(digest §9 `Notice` 필드) |
