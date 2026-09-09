# checklist.md — M4/4D-1 리뷰 준비도

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — 커밋 뒤 `git status --porcelain -- <in_scope 경로>` 결과 없음(clean-tree 게이트, 양성 대조 포함).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·detekt·cpd·sizeGate·moduleDependencyGate·gateExecutionGate·qualityBaseline·contractGate 포함) GREEN.
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — fixture 신설 없음(golden-manifest.json N/A). `MlCallPolicyData` 값은 착수 placeholder로 `policy-values.md`에 등재(`OPEN-4D-POLICY-VALUES`).
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] 인증값 노출 스캔 통과 — commands.md 「인증값 노출 스캔」 절, 매치 0.
- [x] F-9(장부층, low) — `_workspace/m4-4d/01_scout_ml_gateway.md`의 `capability-map.md`
      좌표 인용(§2.4)이 이 slice가 §14.3에 신설 행을 끼워 넣으며 밀렸다(`.gitignore`
      대상이라 evidence 경로 밖 — 여기 등재만 하고 그 노트 자체는 고치지 않는다).

## 설계 검토 대비 결정 변경 — `internal constructor` → `public`(중요)

설계 검토 (2) 값 획득 축 표는 `BidPredictionOutcome.Predicted`·`BidRateCandidates`·`PriceFitness`·
`Uncertainty`·`ModelReleaseRef` 다섯을 `internal constructor`로 닫으라고 지시했다(「어댑터 매핑만
짓는다」). **구현 중 실측으로 이 지시가 기술적으로 성립하지 않음을 확인했다** — Kotlin의
`internal`은 **Gradle 모듈(컴파일 단위) 경계**이고, 그 값을 실제로 짓는 `GrpcBidPredictionGateway`
는 `adapters` 모듈, 타입 선언은 `workflow` 모듈이라 서로 다른 컴파일 단위다. `internal constructor`
로 시도하면 `adapters:compileKotlin` 자체가 `Cannot access '<init>': it is internal`로 실패한다
(commands.md RED 항목). 이 저장소의 기존 「같은 이유로 열려 있는」 선례가 이미 있다 —
`bidvector.workflow.evaluation.MlAnalysisOutcome.Analyzed`(4B-2)도 cross-module adapter 생성을
전제로 public 생성자다. **결정**: 다섯 타입을 public 생성자로 정정하고 `BidPredictionOutcome.kt`
KDoc에 근거를 남겼다. 위조 방어는 컴파일 층이 아니라 (a) `bidvector.adapters.ml.mapSuccess`가
유일한 실제 생성 경로라는 코드 관례 (b) `ResponseMappingTest`·`GrpcBidPredictionGatewayTest`의
값 검증 커버리지(sample_size·후보 개수/순서/origin·정규형 fail-closed)가 진다. 이것은 scope
축소가 아니다 — ④⑤⑦의 도메인 판정(값·기본값으로 접지 않음, fail-closed)은 전부 그대로
`ResponseMapping.kt`·`ParsedSuccessFields.kt`·`ReleaseShapeValidation.kt`에 구현·test 됐다.

## 우회 후보 (1)~(12) ↔ 코드 대응 ↔ 실측

| # | 우회 | 코드 대응 | 실측 근거 |
| --- | --- | --- | --- |
| 1 | deadline 없이 stub 직접 호출 | `CallBudget`이 `predict`의 필수 인자, stub은 `GrpcBidPredictionGateway` 생성자 주입 뒤 `private val` | `PredictionValueTest.CallBudget remaining 은 0보다 커야 한다` — 시그니처 자체가 없음(타입 층) |
| 2 | 재시도 불가 status를 재시도 | `isRetryableTransportStatus`(allow-list, `RetryRules.kt`) | `GrpcBidPredictionGatewayTest.재시도 불가 transport status(INVALID_ARGUMENT)는 한 번만 시도한다` — calls=1 |
| 3 | `Unmeasurable`을 값으로 접음 | sealed 가지, 기본값 없음(`BidPredictionOutcome.Unmeasurable`) | `ResponseMappingTest.Unmeasurable 세 사유는 서로 다른 도메인 값으로 매핑된다` |
| 4 | `latest_promoted` 응답 release ≠ promoted | `releaseSatisfiesSelector`(승격, `ReleaseCheck.kt`) | `ReleaseCheckTest.latest_promoted 요청은...` + `GrpcBidPredictionGatewayTest.latest_promoted 인데...ReleaseMismatch 다` |
| 5 | `exact_release` 응답이 다른 release | 같은 함수, `EXACT_RELEASE` 분기 | `ReleaseCheckTest.exact_release 요청은 응답 release 가 정확히 같아야 통과한다` |
| 6 | UNSPECIFIED/UNRECOGNIZED enum 통과 | `when` 전수·else 없음(`mapUnmeasurable`·`mapApplicationFailure`·`ParsedSuccessFields`) | `ResponseMappingTest`의 UNSPECIFIED·후보 순서/개수/origin·정규형 위반 test 6건 |
| 7 | breaker open에 캐시 답 | `PredictionCallOutcome.BreakerOpen`뿐, 캐시 경로 없음 | `BreakerTest.연속 실패로 breaker 가 열리면...calls.get() shouldBe 2`(3번째 호출에서 servicer 미호출) |
| 8 | resilience4j `Retry` 이중화 | `adapters.ml`에 `io.github.resilience4j.retry` import 부재 | `MlAdapterDependencyTest.ml 패키지는 resilience4j retry 패키지를 import 하지 않는다` + 양성 대조 |
| 9 | 정책 리터럴 | `MlCallPolicyData` 생성 불변식(`MlCallPolicyData.kt`) | `MlCallPolicyDataTest`(deadlineCeiling·maxAttempts·backoff·breaker·featureSchemaVersion 6건 + verifier r1 F-4 — 출하 `ML_CALL_POLICY` 값 자체를 재는 단언 1건) |
| 10 | `Predicted` 위조 | (a) `mapSuccess`가 유일한 관례 생성 경로(b) test 커버리지 — 컴파일 층 폐쇄는 위 「설계 검토 대비 결정 변경」 참고, 이 slice는 구성상 닫지 못함(알려진 제한) | `ResponseMappingTest`·`GrpcBidPredictionGatewayTest` 값 검증 test 전체 |
| 11 | `PriceFitness`를 `UnitScore`/`Rate` 자리에 | 별도 타입(상호 변환 함수 없음) — 자연 컴파일 거부 | 코드 리뷰(`BidPredictionOutcome.kt`) — `PriceFitness.score: BigDecimal`, `UnitScore`는 `decision` 모듈 소유라 애초에 같은 표현식에 오지 않음 |
| 12 | 취소 미전파 | grpc-kotlin coroutine stub이 취소를 그대로 전파, 어댑터가 `CancellationException`을 잡지 않음(`RetryRules.kt`·`ResilientPredictionCall.kt`·`GrpcBidPredictionGateway.kt` 전부 `StatusException`/`StatusRuntimeException`만 개별 catch) | `DeadlineCancellationRetryTest.coroutine 취소가 predict 를 통해 servicer 의 계산 중단으로 이어진다` |
| 13 | **재시도 폭풍 — 백오프 없이 즉시 재시도**(verifier r1 F-1 high, **닫는다**로 변경) | `ResilientPredictionCall.awaitBackoffOrDeadlineExceeded`가 attempt 마다 정책 backoff 만큼 `delay`, 남은 예산이 그조차 못 감당하면 재시도 없이 `DeadlineExceeded` | `GrpcBidPredictionGatewayTest.재시도 사이에 정책 백오프만큼 실제로 지연한다(경과시간 probe)`·`남은 예산이 백오프를 감당하지 못하면...`·`DEADLINE_EXCEEDED 도 예산이 없으면...`(F-3 겸용) |
| 14 | **release 성분 공백 유출**(verifier r1 F-2 high, **닫는다**로 변경) | `ModelReleaseRef` 비공백 불변식 + `hasNonBlankRelease`(구조 검증 단계) + `fetchPromoted`의 공백 promoted → null 접기 + `mapSuccess`의 schema 대조 | `ResponseMappingTest`의 P4a/P4b/schema 불일치 test 3건·`GrpcBidPredictionGatewayTest`의 P4c(양쪽 공백) |

## 값 획득·위조 축 표(최종)

| 타입 | 판정 | 근거 |
| --- | --- | --- |
| `CallBudget` | 연다(불변식으로 닫힘) | 호출부(workflow 후속)가 남은 예산에서 만든다 |
| `BidPredictionRequest`·`AgencyId`·`CompetitionSample`·`ModelReleaseSelector.Exact` | 연다(불변식으로 닫힘) | 호출부가 조립. 자유 `String` 0 |
| `BidRateCandidates`·`PriceFitness`·`Uncertainty`·`ModelReleaseRef`·`BidPredictionOutcome.Predicted` | **공개(위 「설계 검토 대비 결정 변경」), 값 불변식으로 부분 닫힘(verifier r1 F-5)** | `mapSuccess`(어댑터)가 유일한 실제 생성 경로, test 커버리지가 위조 방지를 대신함. `BidRateCandidates`는 conservative≤base≤aggressive, `PriceFitness`는 음수 거부, `ModelReleaseRef`는 다섯 성분 비공백(F-2) — `PredictionValueTest`로 회귀 방지 |
| `BidPredictionOutcome.Unmeasurable`·`Unavailable` | 연다 | 「없음」 위조는 fail-safe 방향이라 위험이 없다(design 원안 그대로) |
| `MlUnavailableReason.*`(신설 9값) | `data object`(닫힘, decision 소유) | `ReviewReason.kt` — 4B-1 관례 계승 |
| `MlCallPolicyData` | 연다(불변식으로 닫힘) | 값은 정책 슬롯 |

## 알려진 제한

1. **실 servicer 부재(M5 5E 미착수)** — 이 slice는 fake servicer(in-process)로만 증명된다. 실제 ML 서버 계약 준수는 이 slice의 방어 범위 밖(scope.md 위협 모델 「방어하지 않는다」).
2. **요청 조립·경쟁 표본 정제는 4B 후속** — `BidPredictionRequest`를 실제로 만드는 호출부(어느 공고의 어느 fact, 남은 예산 계산)는 이 slice가 갖지 않는다. `CompetitionSample`은 예비가격 추첨 관측(`ReserveDrawObservation`)을 나르지 않는다(scope에 명시되지 않은 축, distribution predictor 입력은 4B 후속).
3. **정책 값 실측은 5E** — `MlCallPolicyData`의 deadline·재시도·백오프·breaker 값은 3C/2D의 보수적 상한을 차용한 착수 placeholder다(`OPEN-M2-DEADLINE-VALUES`, `OPEN-4D-POLICY-VALUES`).
4. **사다리 점수(priority·probability·matched) 출처는 미결** — `OPEN-4D-LADDER-SCORE-SOURCE`(운영자 결정 필요, 착수 가정 (c)). `MlAnalysisPort`는 이 slice가 구현하지 않는다.
5. **채널 생성(`ManagedChannel`)·TLS·인증은 M6** — `GrpcBidPredictionGateway`는 생성자로 이미 만들어진 채널만 받는다.
6. **`Predicted` 등 다섯 타입의 컴파일 층 폐쇄 불가** — 위 「설계 검토 대비 결정 변경」. 이것은 의도적 완화이지 누락이 아니다(근거·대안 방어 기록됨).
7. **`GetModelMetadata` 조회 실패의 사유 세분화 없음** — `fetchPromoted`가 status 무관하게 `null`(대조 불가)로 접어 `ReleaseMismatch`로 수렴한다. transport 실패와 진짜 불일치가 같은 `MlUnavailableReason`을 받는다(구현 결정, 별도 사유 신설은 과설계로 판단).
8. **`resolvePolicy`의 설정 오류 fail-fast(verifier r1 F-10)** — `ML_CALL_POLICY`가 기준일을 못 푸는 가지는 `error(...)`를 던진다. `ML_CALL_POLICY`가 `Initial` 항목 하나뿐인 한(`MlCallPolicyDataTest` 실측) `predict` 경로에서 실질 도달 불가 — 제거하지 않고 KDoc 근거만 남겼다(`GrpcBidPredictionGateway.resolvePolicy`).

## milestone-4.md 종결 문단

착수 계약 승인은 scope.md에, 「4D-1/4D-2 분할」 문단은 milestone-4.md에 반영했다. **종결 문단은
사용자 승인 시점에 추가한다**(scope.md in_scope 「종결 문단(승인 시점)」 — 4E 관례와 동일, 아직
사용자 승인 전이라 이 slice에서는 작성하지 않는다).
