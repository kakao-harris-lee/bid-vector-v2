# Slice 계약 — M4 / 4D-2 · 임베딩 gateway (실 client 배선)

> **지위**: **착수 계약 2026-09-10.** `OPEN-4D-LADDER-SCORE-SOURCE` = (a) 결정이 2E 착수 조사(D-2E-1 — 「모델 의존 성분」의 최소 단위는
> 점수가 아니라 **벡터**)로 네 조각으로 분해된 것의 **④ 실 client 배선**이다. ①계약은 2E(`EmbeddingService`, 승인 태그 안 additive)가,
> ②조합 커널은 4B-4(`composePriority`·`SemanticMatch`)와 4B-5(성분 파생)가, ③실 servicer 는 M5 provider slice 가 맡는다.
>
> **4D-1 교훈을 그대로 적용한다 — 한 slice 에 층을 섞지 않는다.** 4D-1 은 client·정책·매핑·release 대조를 한 slice 에 넣어 verifier
> 4라운드(재작업 3/5)를 썼고, 되풀이의 뿌리는 「기존 골격의 계약을 모른 채 가지를 하나 더함」이었다. 4D-2 는 **골격이 이미 있다** —
> `ResilientPredictionCall`·`ReleaseCheck`·`MlCallPolicyData`·`RetryRules` 가 4D-1 산출물로 서 있고, 이 slice 는 그것을 **재사용**한다.
> 새로 만드는 것은 임베딩 고유의 것뿐이다(차원·정규화 검증, 임베딩 release 축, 텍스트 종류).
>
> **레인 격리.** 다른 세션이 `m4-4b5/2026-09-10`(worktree `bid-vector-v2-m4e`)에서 4B-5(성분 파생, `decision` 순수)를 진행 중이다.
> 소스 경로가 겹치지 않는다 — 그쪽은 `decision/**`, 이쪽은 `workflow/embedding/**`·`adapters/ml/**`. 공유 파일은
> `config/quality/gate-tests.properties`(서로 **다른 키**)와 `milestone-4.md` 둘뿐이다.

```yaml
milestone: m4
slice: 4d2-embedding-gateway
base_sha: 7785bbd   # 리뷰 요청 시점에 40자로 재확인한다 — 3G 종결·4B-4 병합 커밋(main == m4/2026-09-08)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3)
branch: m4/2026-09-08   # worktree /Users/harris/Development/private/bid-vector-v2-m4
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/embedding/**   # **port + 값 타입**(D-4D2-1) — 4D-1 이 `workflow/prediction` 을 소유한 전례 그대로
  - workflow/src/test/kotlin/bidvector/workflow/embedding/**   # 값 타입 불변식·fake port 계약
  - adapters/src/main/kotlin/bidvector/adapters/ml/**          # `GrpcEmbeddingGateway` + 임베딩 고유 매핑·검증. 4D-1 의 resilience·release 골격은 **재사용**(사본 금지)
  - adapters/src/test/kotlin/bidvector/adapters/ml/**          # in-process fake servicer(4D-1 대역 재사용)·차원/정규화/release 실패 표·deadline·취소·breaker·bounded retry
  - config/quality/gate-tests.properties                       # `gate.tests.adapters`·`gate.tests.workflow` 등재만(4B-5 는 `gate.tests.decision` — 다른 키)
  - milestone-4.md                                             # 4D 절 4D-2 문단(착수·종결)
  - reports/evidence/m4/4d2/**
out_of_scope:
  - decision/**                                                # 4B-4·4B-5 산출물(`SemanticMatch`·`PriorityInputs`·성분 파생) — 이 slice 는 **소비도 하지 않는다**
  - workflow 조합기(`OpportunityAnalysis : MlAnalysisPort`)·텍스트 합성 규약(`OPEN-2E-TEXT-SYNTHESIS`)·시장 평균·workload port   # 4B-6
  - 벡터 **저장**·pgvector·kNN 조회                             # persistence/후속 — 이 slice 는 벡터를 **돌려주기만** 한다
  - 임베딩 캐시·배치(`repeated text`)                           # 2E 설계 검토가 「측정된 필요 없음」으로 뺐다. 만들고 싶어지면 멈추고 보고
  - 실 servicer(M5) · `ManagedChannel` 생성·TLS·인증(M6)        # 4D-1 과 같은 경계
  - contracts/**(proto 편집)                                    # 2E 종결 산출물. 승인 태그 대비 breaking 0 이 전제다
  - 4D-1 산출물의 **동작 변경**                                  # 재사용은 하되 `GrpcBidPredictionGateway`·`ResponseMapping` 등의 거동을 바꾸지 않는다. 공유가 필요해 형태를 바꿔야 하면 멈추고 보고
acceptance_commands:
  - "S-0  임시 clone(git clone . 또는 worktree add --detach)에서 ./gradlew --no-build-cache clean check"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :adapters:test"
  - "S-3  ./gradlew :workflow:test"
  - "S-4  ./gradlew :adapters:moduleDependencyGate :adapters:sizeGate :adapters:cpdCheck :adapters:contractGate"
  - "S-5  ./gradlew :app:test"                # 필터 없이 전건
  - "S-6  ./gradlew qualityBaseline"
  - "S-7  ./gradlew :app:gateExecutionGate"   # S-5 와 **별도 호출**
rollback: |
    **정본은 `reports/evidence/m4/4d2/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`.
    신설 패키지(`workflow/embedding`)는 삭제, `adapters/ml` 은 **파일 단위**(4D-1 파일을 되돌리지 않는다 — `git diff --name-status` 로 기계 산출).
    **공유 파일**(`gate-tests.properties`·`milestone-4.md`)은 **커밋 해시 hunk 격리** — 「내 줄 사라짐」과 **「남의 줄 남음」을 둘 다** 실측하고,
    `--3way` 도 자동 해소에 실패할 수 있으므로 **수동 해소 절차를 미리 적고** 임시 clone 에서 끝까지 실행한다(2026-09-10).
    **rollback 목록은 자기를 담은 커밋을 가리킬 수 없다**(2026-09-10) — 공유 파일을 만지는 문서 커밋과 목록 갱신 커밋을 나누고 후자는 evidence 경로만 만진다.
```

작성: 2026-09-10, 세션 모델 단독. 근거: `contracts/proto/bidvector/ml/v1/embedding.proto`(2E 종결) · `reports/evidence/m2/2e/scope.md` D-2E-1~6 ·
`milestone-4.md` 4D 절과 4D-1 종결 기록 · `ADR 0010` D-1~D-3 · `capability-map.md` §14.3 `OPEN-4D-LADDER-SCORE-SOURCE` · 4D-1 산출물
(`workflow/prediction/**`·`adapters/ml/**`).

---

## 착수 결정 — 운영자 확인 필요

| ID | 결정 | 근거와 귀결 |
| --- | --- | --- |
| **D-4D2-1** | **임베딩 port 와 값 타입을 4D-2 가 소유한다**(`workflow/embedding/**`) | **4D-1 전례**: gateway slice 가 `workflow/prediction`(`BidPredictionPort`·`BidPredictionOutcome`·`BidPredictionRequest`·`CallBudget`)과 어댑터를 **함께** 소유했다. port 모양은 소비자가 아니라 **계약(proto)**에서 나온다 — 2E 가 그 계약을 고정했으므로 지금 확정할 수 있다. **⚠ 4B-5 계약의 괄호 서술(「4B-6 이 임베딩 port 를 한다」)과 어긋난다** — 그대로 두면 4D-2 가 4B-6 에 막히고, 4B-6 은 조합기·텍스트 합성·시장 평균·workload port 까지 안고 있어 4D-1 이 실패한 「한 slice 에 층 섞기」가 재현된다. 이 결정으로 4B-6 은 **조합기와 나머지 port** 만 지고 임베딩 port 를 **소비**한다 |
| **D-4D2-2** | **정책 값을 예측과 분리한다** — 임베딩 전용 값 슬롯 | 4D-1 의 값(`deadlineCeiling=5s`·`maxAttempts=3`·backoff `200ms/800ms`·breaker `50%/10/30s`)은 **투찰율 예측**의 실측 전 placeholder 로 승인된 것이다(`OPEN-4D-POLICY-VALUES` 종결, 실측은 5E). 임베딩은 호출 성격이 다르므로(후보마다 텍스트 하나, 순차) **같은 구조·다른 값 슬롯**을 둔다. 착수 값은 4D-1 과 같게 두되 `OPEN-4D2-POLICY-VALUES` 로 열어 둔다 |
| **D-4D2-3** | **미가용은 `Absent(사유)` 로 접힌다 — 중립값으로 접지 않는다** | legacy 의 sentinel(「없으면 중립 0.5」)을 4B-5 가 `Absent(reason)` 로 뒤집었고 4B-4 재정규화가 그 부재를 흡수한다. 임베딩 실패가 **0.5 나 0 으로 접히면 fail-open** 이다 — 이 slice 는 결과 타입에 `Unavailable(사유)` 가지를 두고 **점수를 만들지 않는다** |

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**. 리뷰 요청 시점에 갱신한다. rollback 대상 아님.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **port 와 결과 타입** — `EmbedTextPort`(suspend, `CallBudget` 필수 인자: deadline 없는 호출이 시그니처에 없다) + `EmbeddingOutcome = Embedded \| Unavailable(사유)`. 벡터 값 타입은 **차원과 정규화를 자기 불변식으로** 갖는다 | 4D-1 `BidPredictionPort` 관례 · D-2E-1 |
| ② | **gRPC client 배선** — `EmbeddingService.EmbedText` 의 coroutine stub 을 배선한다. deadline·취소 전파·bounded retry·breaker 는 **4D-1 골격 재사용**(`ResilientPredictionCall` 계열 — 사본을 만들면 cpdCheck 가 잡는다) | `ADR 0010` D-1·D-2 · 4D-1 |
| ③ | **응답 검증은 fail-closed** — `values.size == dimension` · `normalization == L2`(UNSPECIFIED 거부) · `release` 다섯 성분 **비공백** · `TextKind` 미지정 거부. **어느 하나라도 어긋나면 예외가 아니라 `Unavailable(사유)`** 로 접는다(4D-1 `SuccessShapeFailClosedTest` 관례 — 검증층이 값 타입 `init` 보다 **먼저** 걸린다) | proto 주석 「client 가 `values.size` 와 대조」 · 4D-1 verifier r2 G-1·G-2 |
| ④ | **release 선택자 대조** — `latest_promoted` 로 요청했으면 `GetEmbeddingMetadata.promoted` 와 대조한다(`releaseSatisfiesSelector` 재사용, `OPEN-2A-RELEASE-CHECK-4D`) | proto 주석이 4D-2 를 지목 |
| ⑤ | **미가용 사유를 값으로 나른다** — 채널 부재·deadline 초과·breaker open·검증 실패·`ApplicationFailure` 각각이 **다른 사유**다. 점수를 만들지 않는다(D-4D2-3) | ADR 0010 D-3 · 4D-1 fail-safe |
| ⑥ | **재시도 계층은 하나** — bounded retry 는 이 어댑터에만. 상위 어디에도 재시도를 넣지 않는다 | ADR 0005 D-11 |

**만들지 않는 것**: 벡터 저장·kNN · 캐시·배치 · 조합기·텍스트 합성 · 실 servicer · proto 편집 · 4D-1 거동 변경.

---

## 위협 모델 — 4D-2 고유 경계

**방어한다**: (a) **위조·손상된 벡터가 점수가 되는 것**(차원 불일치·정규화 미지정 — 코사인이 내적이려면 L2 여야 한다) (b) **provenance 공백**(release 다섯 성분) (c) **fail-open**(미가용이 중립 점수로 접히는 것 — legacy sentinel 의 형태) (d) **재시도 중복**(계층 둘) (e) **deadline 없는 호출**(시그니처에서 제거) (f) 취소가 전파되지 않아 thread/connection 이 고갈되는 것.

**방어하지 않는다**: 실 servicer 의 벡터 **품질**(M5) · 텍스트 합성 규약의 옳음(4B-6·`OPEN-2E-TEXT-SYNTHESIS`) · 채널 보안·인증(M6) · 벡터 저장의 무결성(persistence/후속) · 빌드 스크립트를 임의로 쓰는 저자(2026-09-03 경계).

**우회 후보 — 값 위조 축**: (1) 차원이 다른 `values` → ③ 이 `Unavailable` 로 접는다 (2) `normalization=UNSPECIFIED` 로 정규화 안 된 벡터 주입 → ③ (3) release 공백 → ③ (4) `latest_promoted` 요청에 다른 release 응답 → ④ (5) 예외를 던져 상위가 `try` 로 삼키게 → **port 계약이 결과 갈래**라 `try` 쓸 자리가 없다(4B-2 관례) (6) **벡터 값 타입을 밖에서 지어 점수 경로에 주입** → 값 타입의 생성 경로를 `internal` 로 닫고 어댑터는 원시 배열만 다룬다(4C-1 `ClaimedOutboxRow` 관례).

**우회 후보 — 값 획득 축 (하네스 (2b))**: 새로 public 이 되는 것(port·결과 타입·벡터 값 타입·`GrpcEmbeddingGateway`)을 **전수**하고 각각이 밖에 허락하는 것을 설계 검토가 표로 낸다. **「연다」·「경계로 처리」 행도 실측 목록에 넣는다.** **`object` 커널 계수 고정 항목**(2026-09-10): 이 slice 에서 「임베딩 호출을 몇 번 했는가」를 세야 한다 — **계수용 함수·인터페이스를 주입하지 마라.** 관측 지점은 **in-process fake servicer**(test 대역)다. 4B-2 가 세 라운드를 쓴 자리이고 4D-1 이 같은 대역으로 이미 풀었다.

---

## OPEN — 수령·신설

| OPEN | 4D-2 처리 |
| --- | --- |
| `OPEN-4D-LADDER-SCORE-SOURCE` | 이 slice 는 ④(client) 담당. ②(4B-4·4B-5·4B-6)·③(M5)이 남으면 항목은 활성 유지 |
| `OPEN-2A-RELEASE-CHECK-4D` | ④ 가 임베딩 축에서 닫는다 — 예측 축의 처리와 같은 규칙 재사용 |
| `OPEN-4D2-POLICY-VALUES`(신설) | 임베딩 호출 정책 값(deadline·재시도·backoff·breaker)의 실측 근거 — 착수 값은 4D-1 과 같게 두고 **5E 실측**으로 갱신(`OPEN-M2-DEADLINE-VALUES` 와 같은 경로) |
| `OPEN-2E-TEXT-SYNTHESIS` | **이 slice 가 닫지 않는다** — 텍스트를 **받아서** 보낼 뿐이다. 4B-6 소관 |
| `OPEN-4D2-VECTOR-PERSISTENCE`(신설) | 벡터를 어디에 저장하고 kNN 을 어떻게 도는가(pgvector 여부·차원 고정·재계산 정책) — 이 slice 는 벡터를 돌려주기만 한다. persistence 후속 |

---

## 계약 갱신 — 2026-09-10 (운영자 확인 + 착수 조사 귀결)

| ID | 결정 | 귀결 |
| --- | --- | --- |
| **D-4D2-1 확정** | **임베딩 port 와 값 타입은 4D-2 가 소유한다**(운영자 승인 2026-09-10) | 4D-1 이 `workflow/prediction` 을 소유한 전례 그대로다. **4B-6 은 임베딩 port 를 만들지 않고 소비한다** — 4B-5 계약의 괄호 서술(「4B-6 이 임베딩 port」)은 이 결정으로 대체된다. 그 레인에 전달은 운영자가 한다 |
| **D-4D2-4**(신설) | **`callResilient` 을 제네릭화한다** — 「4D-1 산출물 동작 변경 금지」는 **동작** 불변을 뜻하지 타입 매개변수 추출까지 막는 것이 아니다 | 착수 조사 실측: `ResilientPredictionCall.kt` 의 `callResilient`·`PredictionCallOutcome.Responded`·`BoundedRetryOutcome.Success`·`attemptOnce`·`isRetryableFailureResponse` 가 전부 `CalculateOptimalBidResponse` 에 **하드코딩**돼 있어 그대로는 임베딩에 못 쓴다. 사본을 만들면 (ⅰ) cpdCheck 가 잡고 (ⅱ) verifier r1 F-1·r2 G-4·r3 H-1 이 어렵게 세운 로직을 **두 벌 유지**하게 된다. 그래서 응답 타입을 타입 매개변수로, 「재시도 가능한 application failure 인가」를 술어 인자로 뽑는다. **`internal` 이므로 (2b) 공개 표면은 늘지 않는다** — 4B-2 가 걸린 함정(주입 인자가 공개 시그니처가 됨)과 다른 자리다. **동작 불변의 증거는 4D-1 의 기존 test 전건 무변경 초록**이다(`BreakerTest`·`DeadlineCancellationRetryTest`·`SuccessShapeFailClosedTest` 등) — 그 test 들을 **고쳐야 한다면 동작이 바뀐 것**이니 멈추고 보고한다 |
