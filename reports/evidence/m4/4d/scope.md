# Slice 계약 — M4 / 4D-1 · ML gateway — 투찰율 후보 gateway (`CalculateOptimalBid` client)

> **지위**: **착수 계약 2026-09-10.** `milestone-4.md` 4D 를 **계약이 나르는 것**에 맞춰 좁힌 앞쪽이다. 세션 모델 단독 작성(운영자 지시 2026-09-04).
>
> **왜 좁혔나 (착수 조사 실측 — `_workspace/m4-4d/01_scout_ml_gateway.md` §2.1·§3·§7).** 4B-1 `LadderInput` 과 4B-2 `MlAnalysisPort` 는 ML 이
> `priority`·`probability`·`matched` 점수 셋을 준다고 전제하지만, 승인 태그 `contracts/v1-approved-2026-09-07` 의 `.proto` 전체에 그 이름의 필드는
> **0건**이다. `CalculateOptimalBid` 의 `Success` 는 투찰율 후보 셋(라벨 고정)·`PriceFitness`·`Uncertainty`·`ModelRelease` 를 내고, probability 축은
> ML-03·D-M2-8 로 **의도적으로 없다**. legacy 의 그 점수 셋은 가격 예측이 아니라 opportunity-analysis 서브시스템(분류·유사도·가중합·캘리브레이션,
> 4B-1 조사 §7)의 산출이고 capability-map ML 축·data-dictionary §6 어디에도 그 서브시스템의 V2 소유 판정이 없다. **계약이 나르지 않는 값을 어댑터가
> 만들면 그것이 ADR 0010 D-3 의 제3 변환이다.** 그래서 4D-1 은 `milestone-4.md` 4D 문면 그대로 「M2 client · deadline/cancel/breaker/bounded retry ·
> domain → contract 매핑 · **ML response → candidate 매핑** · provenance 보존 · fail-safe」를 세우고, 사다리 점수의 출처는 신설 `OPEN-4D-LADDER-SCORE-SOURCE`
> 로 운영자 결정에 올린다(아래 「운영자 결정 필요」). 4B-2 의 `MlAnalysisPort` 는 **이 slice 가 구현하지 않는다.**
>
> **레인 격리.** 다른 세션이 `m4/2026-09-08`(worktree `bid-vector-v2-m4`)에서 4B-2 를 진행 중이다. 이 slice 는 4E 종결 병합 커밋 `d0a44a7`
> (= `m4/2026-09-08` 의 `1016fa3` + 4E)에서 가른 브랜치 `m4-4d/2026-09-10`(worktree `bid-vector-v2-m4e`)에 산다. 병합은 사용자 승인 사항. 4B-2 와
> 겹칠 파일은 `config/quality/gate-tests.properties`(다른 키)·`milestone-4.md`(다른 절)·`docs/discovery/data-dictionary.md`(4B-2 는 무편집) 이고,
> **`workflow/evaluation/**` 는 편집하지 않는다.**

```yaml
milestone: m4
slice: 4d1-ml-gateway-bid-prediction-client
base_sha: d0a44a739864896d2ca894fbe8b286651d8f1156   # 4E 병합 커밋(m4/2026-09-08 의 1016fa3 포함). 브랜치 m4-4d/2026-09-10 의 분기점
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다(4A r1 B-3).
branch: m4-4d/2026-09-10   # worktree /Users/harris/Development/private/bid-vector-v2-m4e
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/prediction/**     # port(BidPredictionPort, suspend)·요청 값(BidPredictionRequest·CallBudget·ModelReleaseSelector·CompetitionSample·AgencyId·OptimizationObjective 미러)·결과 sealed(BidPredictionOutcome: Predicted|Unmeasurable|Unavailable)·BidRateCandidates·PriceFitness·Uncertainty·ModelReleaseRef
  - workflow/src/test/kotlin/bidvector/workflow/prediction/**     # 값 불변식·경계 test(4A 술어 재사용)
  - adapters/src/main/kotlin/bidvector/adapters/ml/**              # GrpcBidPredictionGateway(port 구현)·MlCallPolicy(정책 데이터)·RetryRules(2D 승격)·ResilientPredictionCall(breaker+deadline+bounded retry 한 계층)·RequestMapping·ResponseMapping·ReleaseCheck(2B 술어 승격)
  - adapters/src/test/kotlin/bidvector/adapters/ml/**              # fake servicer 위 gateway test·release 대조·deadline/cancel/retry(2D test 실 경로 재사용)·breaker·매핑 전수·정책 불변식·의존 경계
  - adapters/src/test/kotlin/bidvector/adapters/contract/ContractRetryRules.kt        # main 승격 뒤 위임(CPD 0) — 2D test 는 그대로 통과해야 한다
  - adapters/src/test/kotlin/bidvector/adapters/contract/ContractFractionRules.kt     # 같은 이유(decimal string 정규형 술어)
  - adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt    # `releaseSatisfiesSelector` 를 main 으로 승격하고 이 test 가 main 을 import — 단언·case 무변경
  - adapters/build.gradle.kts                                       # `bidvector:ml-contract`·grpc-kotlin-stub·grpc-stub·grpc-core 를 main 으로(netty-shaded 는 test 유지). 2A 주석 갱신. sizeGate 50줄 축이면 dependencies 블록 하나 더
  - decision/src/main/kotlin/bidvector/decision/ReviewReason.kt     # `MlUnavailableReason` 값 추가만(ADR 0010 D-6 「4D 착수 시 사전 등재」) — 기존 값·다른 타입 무변경
  - decision/src/test/kotlin/bidvector/decision/**                  # 소진 when 회귀 test 1건
  - docs/discovery/data-dictionary.md                               # §3.6 에 `MlUnavailableReason` 어휘 블록 한 개(4B-1 이 「4D 가 넓힌다」로 인계)
  - docs/discovery/capability-map.md                                # §14.3 `OPEN-2A-RELEASE-CHECK-4D` 닫힘 표시 + `OPEN-4D-LADDER-SCORE-SOURCE` 신설 행만
  - config/quality/gate-tests.properties                            # `gate.tests.adapters`·`gate.tests.decision`·`gate.tests.workflow` 에 등재만
  - milestone-4.md                                                  # 4D 절에 「4D-1/4D-2 분할」 문단 + 종결 문단(승인 시점)
  - reports/evidence/m4/4d/**
out_of_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/**       # 4B-2 진행 중 — `MlAnalysisPort` 구현·편집 금지(OPEN-4D-LADDER-SCORE-SOURCE 결정 뒤 4D-2/4B 후속)
  - workflow/**/strategy/** · workflow/**/event/** · workflow/**/notification/**   # 4A·4C-1·4E 산출물 — 소비만
  - 사다리 점수(priority·probability·matched)의 산출·매핑            # 계약에 없다 — OPEN-4D-LADDER-SCORE-SOURCE
  - 경쟁 표본의 조립·정제(저장소 질의 → CompetitionSample)          # 호출부(4B 후속) 소유. 4D-1 은 값의 형태만(2B 「정제는 송신 전 Kotlin 어댑터」의 「어댑터」는 조립 어댑터가 아니라 형태 검증 — 이 slice 는 형태 불변식까지)
  - ManagedChannel 생성·주소·TLS·인증(app/M6)                       # 생성자 주입
  - TrainingJobService client(2C)·폴링 스케줄(ADR 0005 스케줄러)   # 후속
  - 실 servicer(M5 5E — 미착수, `ml-engine/` 에 서버 0) · Python ML 계산 · 교차언어 소켓 스모크(2D `crossLangSmokeTest` 그대로)
  - 응답 `Diagnostics` 의 도메인 이식(렌더링 소관) · 캐시 · TimeLimiter(gRPC deadline 과 이중 계층)
  - 정책 값의 실측(`OPEN-M2-DEADLINE-VALUES` — 5E 실측 → 4D version 갱신; 이 slice 는 보수적 초기값 + 승인 대상 등재)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — moduleDependencyGate·sizeGate·cpdCheck·gateExecutionGate·contractGate 전건 포함
  - "./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.*'"                          # S-2a
  - "./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.prediction.*'"                  # S-2b
  - "./gradlew --no-daemon :decision:test"                                                             # S-2c — 소진 when 회귀 + 기존 사다리 test 무변경
  - "./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.contract.*'"                    # S-3 — 2A~2D consumer test 가 승격 뒤에도 전건 통과(crossLang 은 기존 제외 필터 그대로)
  - "./gradlew --no-daemon :adapters:test --tests '*MlAdapterDependencyTest*' :workflow:test --tests '*PredictionBoundaryTest*'"   # S-4 — 의존 경계(domain import allow-list + resilience4j retry 부재 + 채널 라이브러리 FQN 0)
  - "./gradlew --no-daemon :adapters:gateExecutionGate :decision:gateExecutionGate :workflow:gateExecutionGate"   # S-5
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m4/4d/rollback.md`**. 신설 패키지 넷(workflow/prediction main·test, adapters/ml main·test)과 evidence 는 삭제,
    `ReviewReason.kt`·`ContractRetryRules.kt`·`ContractFractionRules.kt`·`PredictionContractTest.kt`·`adapters/build.gradle.kts`·
    `gate-tests.properties`·`data-dictionary.md`·`capability-map.md`·`milestone-4.md` 는 **줄 단위**(커밋 해시 hunk 격리, evidence-pack 2026-09-09).
    병합 전에는 「브랜치를 버린다」가 rollback.
```

근거: `milestone-4.md` 4D · `prep/m4-prep.md` §2 4D 행·D-M4-6 · `ADR 0010` D-1~D-7·§5 · `ADR 0005` D-9·D-11 · M2 2A ⑥·D-2A-3 · 2B ③·⑤·D-2B-3 · 2D ⑥ ·
`OPEN-2A-RELEASE-CHECK-4D`·`OPEN-M2-DEADLINE-VALUES` · `capability-map.md` ML-01·ML-02·ML-03·DEC-06 · `data-dictionary.md` §3.6·§6 · 조사 노트
`_workspace/m4-4d/01_scout_ml_gateway.md`. legacy 실물은 조사하지 않았다(ADR 0010: 「legacy 에는 gRPC 경로가 없다」).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline d0a44a739864896d2ca894fbe8b286651d8f1156..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**. 리뷰 요청 시점마다 갱신.

---

## 운영자 결정 필요 — `OPEN-4D-LADDER-SCORE-SOURCE` (착수 가정 (c), 확정은 종결 승인 시)

**확정 2026-09-10 (a).** 사용자 승인으로 M2 계약을 `v1` 안에서 additive 확장(새 RPC — opportunity
analysis)하고 M5 provider 를 동반하는 경로로 정했다. **귀결**: 사다리 점수 경로는 **M2 후속
slice**(opportunity-analysis RPC 를 2A~2D 절차로 추가) **+ M5 provider slice** 로 착수한다.
4B-2 `MlAnalysisPort` 의 미가용 값·`analyze` 의 suspend 전환은 **그 후속 slice 의 계약**에서
다룬다(이 slice 가 손대지 않는다). **그때까지는 착수 가정 (c)** — `MlAnalysisPort` 는 항상
`Unavailable` 로 남는다(4B-2 lane 에 이미 전달한 배선 조언, 아래 문단 그대로 유효).

**물음**: 4B-1 사다리가 읽는 `priorityScore`·`probabilityScore`·`matchedScore` 는 V2 에서 누가 만드는가.

| 선택지 | 뜻 | 비용·귀결 |
| --- | --- | --- |
| (a) M2 계약을 `v1` 안에서 additive 확장 | 새 RPC(예: opportunity analysis)를 2A~2D 절차로 추가, M5 provider 동반 | M2 후속 slice + M5 slice. legacy 산식(가중합·캘리브레이션·0.49 cap)의 재활용 판정이 선행돼야 한다 — capability-map 에 그 서브시스템의 분류가 없다 |
| (b) Kotlin 계산으로 재정의 | 점수 셋을 `Success`(적합도·불확실성)와 도메인 fact 에서 Kotlin 이 유도 | `probability` 는 ML-03 위반 후보(적합도를 확률 자리에) — 이름·의미를 바꾸는 data-dictionary §6 결정이 필요 |
| **(c) 당분간 미가용** | `MlAnalysisPort` 는 항상 `Unavailable` → 모든 후보 `Verdict.Review(MlUnavailable(ScoreNotProvided))` | 코드 변경 0. 4B-2 의 use case 가 fail-safe 경로를 **실제로** 밟는다(현재는 `Analyzed.priorityScore` non-null 이라 도달 불가 — 조사 §3 ②). **추천: (a) 를 별도 결정으로 두고 그때까지 (c)** |

4D-1 산출물은 (a)(b)(c) 어느 쪽에도 그대로 필요하다(투찰가 추천 경로는 사다리 점수와 독립). 이 slice 는 (c) 를 착수 가정으로 두고 `capability-map.md`
§14 에 활성 `OPEN` 으로 등재만 한다. **4B-2 lane 에 알릴 것**: `MlAnalysisOutcome` 에 미가용 값이 없고(`Analyzed.priorityScore` non-null·
`SimilarityProjectionNotReady` 는 드롭 신호) `analyze` 가 non-suspend 다 — 2026-09-10 크로스세션 메시지는 승인 만료로 미전달, 메모리
`m4-ladder-score-source-gap` 에 기록.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **port 와 요청·결과 값** — `workflow.prediction.BidPredictionPort.predict(request: BidPredictionRequest, budget: CallBudget): BidPredictionOutcome`(suspend). 요청은 도메인 값만(`ResolvedBaseAmount`·`BusinessCategory?`·`AgencyId?`·`BaseAmountProvenance`·`List<CompetitionSample>`·`OptimizationObjective`·`ModelReleaseSelector`·`CorrelationId`), 자유 `String` 0. 결과 `BidPredictionOutcome = Predicted(candidates: BidRateCandidates, fitness: PriceFitness, uncertainty: Uncertainty, release: ModelReleaseRef) \| Unmeasurable(reason) \| Unavailable(reason: MlUnavailableReason)`. `Predicted` 와 그 성분은 `internal constructor`(어댑터 매핑만 생성) | 4D 「domain input → contract DTO mapping」·「ML response → candidate mapping」 · ADR 0005 D-9 · 2B ③(후보 정확히 3, 라벨 순서) |
| ② | **deadline 필수·취소 전파** — `CallBudget(remaining: Duration)` 이 port 의 필수 인자(양수 불변식, 상한은 정책). 어댑터는 그 값에서만 `withDeadlineAfter` 를 만든다. coroutine 취소 → gRPC cancel 은 2D `ContractDeadlineCancellationRetryTest` 의 취소 case 를 **실 어댑터 경로**로 재실행해 증명 | ADR 0010 D-2 · M4 완료 조건 「ML timeout 시 thread/connection 고갈 없음」 |
| ③ | **bounded retry 한 계층 + breaker** — 2D `isRetryableTransportStatus`·`isRetryableApplicationFailure`·`callWithTransportRetry` 를 `adapters/ml` main 으로 승격(test 는 위임). `DEADLINE_EXCEEDED` 는 남은 예산이 있을 때만, `RESOURCE_EXHAUSTED` 는 백오프 뒤. resilience4j `CircuitBreaker` 만 쓰고 `Retry`·`TimeLimiter` 는 쓰지 않는다(재시도 계층 하나, deadline 은 gRPC 가 잰다). breaker open 이면 호출 없이 `Unavailable(CircuitOpen)` | ADR 0010 D-4·D-5 · ADR 0005 D-11 · 2D ⑥ |
| ④ | **미가용은 이름 하나** — 어댑터 밖으로 예외가 나가지 않는다. transport 재시도 소진·deadline 소진·breaker open·`ApplicationFailure`(`MODEL_NOT_READY` 재시도 뒤 지속 포함)·release 불일치·계약 위반(정의 밖 enum·후보 개수/순서·정규형 위반·`sample_size==0` 인 `Success`) 이 전부 `Unavailable(reason)` 이고 사유는 `decision.MlUnavailableReason` 의 **신설 값**(`DeadlineExceeded`·`CircuitOpen`·`RetryBudgetExhausted`·`TransportFailed`·`ModelNotReady`·`ReleaseMismatch`·`ContractViolation`·`UnsupportedSchema`·`UnsupportedRelease`·`InvalidRequest`) | ADR 0010 D-6 · D-M4-6 (a) · 4B-1 KDoc 「이 sealed 는 4D 가 넓힌다」 |
| ⑤ | **`Unmeasurable` 은 성공한 호출의 정직한 답** — `INSUFFICIENT_SAMPLES`·`UNTRAINED_SEGMENT`·`FEATURE_ABSENT` 셋이 서로 다른 `Unmeasurable(reason)` 으로, 값·기본값·`Unavailable` 로 접지 않는다 | ADR 0010 D-3 · 2A D-2A-3 · ML-02 |
| ⑥ | **제3 변환 금지의 client 집행** — 2B `releaseSatisfiesSelector` 를 main 으로 승격. `exact_release` 요청이면 응답 release 가 요청과 같아야 하고, `latest_promoted` 요청이면 **같은 `predict` 호출 안에서** `GetModelMetadata` 를 불러 `promoted` 와 대조한다. 불일치 = `Unavailable(ReleaseMismatch)`. `Predicted.release`(release_id·artifact_checksum·feature_schema_version·code_version·dataset_id)는 non-null 이라 provenance 가 탈락할 수 없다 | `OPEN-2A-RELEASE-CHECK-4D`(이 slice 가 닫는다) · ADR 0010 D-3 · 4D 「release/checksum/schema provenance 보존」 |
| ⑦ | **fail-closed 매핑** — 응답 enum 의 `UNSPECIFIED`·`UNRECOGNIZED` 는 `when` 전수의 명시 가지로 `ContractViolation`(else 없음). 후보는 `BidRateCandidates(conservative, base, aggressive)` 세 필드(리스트 아님). decimal string 은 2B `isNormalizedFraction`(승격) 으로 검증하고 `Rate` 는 shared-kernel 의 정규 생성 경로로만 | 2A ⑥ · 2B ③ · 2D ④ |
| ⑧ | **정책 데이터 슬롯** — `MlCallPolicyData(deadlineCeiling, maxAttempts, backoff(IntervalFunction 입력값), breakerFailureRateThresholdPercent, breakerSlidingWindowSize, breakerWaitDurationInOpenState)` + `ML_CALL_POLICY: EffectiveDatedPolicy`(3C 관례, 운영/test 인스턴스 분리). 봉투의 `deadline_policy_version` 에 정책 version 을 싣는다. **값은 착수 placeholder(보수적)** — `policy-values.md` 에 등재, 승인은 종결 시, 실측 갱신은 `OPEN-M2-DEADLINE-VALUES`(5E) | ADR 0010 D-1·§5 · 2A ④ |
| ⑨ | **가격 적합도 ≠ 확률** — `PriceFitness` 는 `UnitScore`·`Rate` 와 상호 대입이 불가한 별도 값 타입. `OPEN-ML-03` 의 「타입 분리」 후보를 Kotlin 쪽 실물로 세운다(종결은 운영자) | ML-03 · 2B ⑤ |
| ⑩ | **trace 축** — `BidPredictionRequest.correlationId`(4C-1 `CorrelationId`) → `RequestEnvelope.correlation_id`. `request_id` 는 호출마다 어댑터가 발급(재시도는 같은 `request_id` — D-4 멱등) | M4 완료 조건 「trace/correlation id 유지」 · 2A ④ |

**만들지 않는 것**: `MlAnalysisPort` 구현 · 사다리 점수 · 표본 조립 · 채널 생성 · training client · TimeLimiter · resilience4j Retry · 캐시 · `Diagnostics` 이식 ·
실 소켓 test · 리터럴 정책 값 · 자유 `String` 사유.

---

## 계약 고정 결정 (D-4D-1~7)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4D-1** | 4D 를 **4D-1(투찰율 후보 gateway) / 4D-2(사다리 점수 경로, `OPEN-4D-LADDER-SCORE-SOURCE` 결정 뒤)** 로 나눈다 | 머리 「왜 좁혔나」 | 계약 고정 — 종결 시 운영자 재확인 |
| **D-4D-2** | port 는 `workflow.prediction` 신설 패키지(4B-2 의 `evaluation` 무편집). `suspend` 다 — coroutine stub 을 동기로 접으면 취소 전파(D-2)가 끊긴다 | ADR 0005 D-10 · 조사 §3 ① | 계약 고정 |
| **D-4D-3** | 2D `ContractRetryRules`·2B `ContractFractionRules`·2B `releaseSatisfiesSelector` 를 **main 으로 승격**하고 test 가 main 을 쓴다 — 같은 규칙이 두 벌이면 CPD 가 잡고, 다르면 「test 는 통과하는데 실 client 는 다르다」가 된다 | `OPEN-2A-RELEASE-CHECK-4D` 「그 test 를 실제 배선에 재사용」 · 2D ⑥ (e) | 계약 고정 |
| **D-4D-4** | `latest_promoted` 의 metadata 대조는 port 메서드가 아니라 **어댑터 내부 단계** — 호출부가 대조를 잊을 수 없다 | 선언만 있고 강제되지 않는 ML-03 형태 회피 | 계약 고정 |
| **D-4D-5** | `Unmeasurable` 과 `Unavailable` 은 **다른 가지** — 전자는 4B 가 `Skip`/`Review` 로 어떻게 읽을지 결정할 도메인 결과, 후자만 `MlUnavailable` | ADR 0010 D-3 표 · D-6 | 계약 고정 |
| **D-4D-6** | `MlUnavailableReason` 신설 값은 전부 `data object`(payload 없음 — transport status 코드·detail_code 를 싣지 않는다: 자유 문자열 유입 경로 차단, 진단은 어댑터 로그) | 4E ⑤ 와 같은 규율 | 계약 고정 |
| **D-4D-7** | 정책 초기값(placeholder, 승인 대상): `deadlineCeiling=5s`·`maxAttempts=3`·backoff `200ms·800ms`·breaker 50%/10/30s. 근거는 실측이 아니라 3C `EXTRACTION_POLICY` 와 2D `retry.sample.max-attempts=3` 의 보수적 상한 — `policy-values.md` 에 층 `legacy-behavior 0건·실측 0건·운영자 승인` 으로 | ADR 0010 D-1 「보수적 상한 + 측정 의무」 | 착수 가정 |

---

## 위협 모델 — 4D-1 고유 경계

**방어한다**: (a) deadline 없는 호출(② 타입) (b) 재시도 불가 status 의 재시도·재시도 계층 이중화(③) (c) fail-open — 어떤 실패도 `Predicted` 가 되지 않고
예외로 새지 않음(④, 결과 타입에 「통과」 위조 경로 없음: `Predicted` internal) (d) `Unmeasurable` 의 변환(⑤) (e) 제3 변환 — 요청이 지목하지 않은
release 의 답(⑥) (f) provenance 탈락(⑥ non-null) (g) 정의 밖 enum·후보 개수·정규형 위반의 조용한 통과(⑦) (h) 정책 리터럴(⑧) (i) 적합도의 확률 오용(⑨)
(j) 채널 라이브러리·grpc 타입의 workflow 유입(`workflow.prediction` 경계 test — 허용 루트 `kotlin`·`kotlinx`·`java`·`bidvector.sharedkernel`·
`bidvector.procurement`·`bidvector.decision`·`bidvector.workflow`).
**방어하지 않는다**: 실 servicer 의 준수(5E) · 요청 조립·표본 정제의 옳음(4B 후속) · 정책 값의 옳음(`OPEN-M2-DEADLINE-VALUES`) · 채널·TLS·인증(M6) ·
사다리 점수(`OPEN-4D-LADDER-SCORE-SOURCE`) · 빌드 스크립트를 임의로 쓰는 저자(2026-09-03 경계) · 생성 코드의 결함.

**승인 문면과의 대조 — 경계가 요구 축소가 아님**: `milestone-4.md` 4D 여섯 줄 가운데 ①~⑩ 이 여섯을 전부 덮는다. 「ML response → candidate mapping」의
candidate 는 2B 의 `Candidate`(투찰율 후보)이지 사다리 점수가 아니다 — 축소가 아니라 문면 그대로다. 사다리 점수 경로는 4B-1·4B-2 가 전제했으나 어느 승인
문서도 4D 에 배정한 적이 없는 요구이므로 `OPEN` 으로 올린다.

**우회 후보(≥5)**: (1) deadline 없이 stub 직접 호출 → `CallBudget` 필수 인자, stub 은 어댑터 `private` (2) `INVALID_ARGUMENT` 재시도 → allow-list 술어 test
(3) `Unmeasurable` 을 `Unavailable`·기본값으로 → sealed 세 가지 매핑 test (4) `latest_promoted` 응답 ≠ promoted 통과 → `ReleaseMismatch` test (5) `UNSPECIFIED`
enum 통과 → `ContractViolation` test (6) breaker open 에 옛 답 → `CircuitOpen` 만 (7) resilience4j `Retry` 추가 → 의존 경계 test (8) 정책 리터럴 → 슬롯
불변식 (9) `Predicted` 위조 → `internal constructor` 컴파일 거부 실측 (10) `PriceFitness` 를 `UnitScore` 자리에 → 컴파일 거부 실측 (11) 취소 미전파 → 2D
취소 case 실 경로 재실행.

---

## OPEN — 수령·신설

| OPEN | 4D-1 처리 |
| --- | --- |
| `OPEN-2A-RELEASE-CHECK-4D` | ⑥ 으로 **닫는다**(capability-map §14.3 표시) |
| `OPEN-M2-DEADLINE-VALUES` | ⑧ placeholder + 승인 등재, 실측은 5E — **활성 유지** |
| `OPEN-ML-03` | ⑨ 가 타입 분리 실물을 세운다 — 종결은 운영자(등재만) |
| `OPEN-2B-OBJECTIVE-VALUES`·`OPEN-ML-02` | 경계 밖(5D·운영자) |
| **`OPEN-4D-LADDER-SCORE-SOURCE`**(신설) | **결정 (a), 2026-09-10 — 소유 M2 후속 slice + M5 provider slice**. 그때까지 착수 가정 (c) 유지 |
| **`OPEN-4D-POLICY-VALUES`**(신설) | ~~D-4D-7 값 — 종결 승인 시 확정~~ **종결(사용자 승인 2026-09-10)** — 정본 `reports/evidence/m4/4d/policy-values.md` |

---

## 병합 결정 (사용자 승인 2026-09-10 ④)

병합 대상은 `m4/2026-09-08`(현재 `main`과 동일 커밋 `edd57fb`, r3 검증 관찰 §7 — 병합 축이
슬라이스 시작 시점 이후 움직였음을 verifier가 실측했다). 팀장이 이 종결 등재 커밋 뒤에
병합을 실행한다 — 이 slice(구현 레인)는 병합·push 를 하지 않는다.

---

## 계약 갱신 — 2026-09-10 (구현 중, 팀장 등재)

1. **좁은 예외 — `app/src/test/kotlin/bidvector/app/conformance/VerdictExecutors.kt`.** `MlUnavailableReason` 에 값을 더하자(④) 이 파일의 소진 `when` 이
   컴파일되지 않아 구현 레인이 가지를 추가했다(4C-1 의 `StrategyEditExecutors.kt` 파급과 같은 클래스 — 승인된 어휘 확장의 기계적 파급, corpus 기대값
   무변경). `in_scope` 에 **이 파일 한 개**를 추가한다. 이 파일은 4B-1 이 만든 것이고 `m4/2026-09-08` 쪽 4B-2 가 조건부 in_scope 로 갖는다 — 병합 시 줄 단위
   충돌 축으로 등재.
2. **`internal constructor` 이탈 — 설계 검토 (2) 표의 오류.** `Predicted`·`BidRateCandidates`·`PriceFitness`·`Uncertainty`·`ModelReleaseRef` 는 `workflow` 에
   선언되고 `adapters` 가 만든다. Kotlin `internal` 은 Gradle 모듈 단위라 다른 모듈의 생성이 컴파일되지 않는다 — 설계 검토가 4C-1·4E(같은 모듈 안 생성)의
   관례를 모듈 경계 너머로 잘못 옮겼다. 구현은 public 생성자(4B-2 `MlAnalysisOutcome.Analyzed` 선례)이고, 위조 경로는 값 획득 축 표에서 **「연다 — 경계로
   처리」** 로 재분류한다. 4C-1 교훈(「경계로 처리」 행이 수정 라운드에서 결함이 됐다)에 따라 verifier 표적: 위조된 `Predicted` 가 하류에서 무엇을 살 수
   있는가(현재 소비자 0 — 4B 후속), 그리고 `adapters` 밖에서 `Predicted` 를 만드는 정당한 사유가 있는가(test fake 뿐이어야 한다).
3. **`adapters/build.gradle.kts` 에 `implementation(project(":decision"))`** — `MlUnavailableReason` 참조에 필요. adapters 는 domain 모듈을 의존해도
   되는 층(ADR 0006, `ModuleDependencyGate` 통과 실측은 commands.md).
