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
| 15 | **값 타입 `init` 위반이 예외로 `predict` 밖까지 샘**(verifier r2 G-1 high, **닫는다**로 신설 — F-5 정정) | `BidRateCandidates.init`(conservative≤base≤aggressive)은 마지막 안전판, `hasOrderedCandidateRates`(`CandidateShapeValidation.kt`)가 같은 조건을 구조 검증층(`isAcceptableSuccessShape`)에서 먼저 걸러 `Unavailable(ContractViolation)`로 접는다(F-2 `hasNonBlankRelease`와 동형 패턴) | `SuccessShapeFailClosedTest`의 내림차순·base>aggressive test 2건(예외 0, `ContractViolation`) + 클램프-포화(세 값 동일) test 1건(정상 `Predicted`) + H-6 table-driven test(8 case 전수, 아래 참고) |
| 16 | **HALF_OPEN breaker permit 이 예산 소진 가지에서 새 회복 불가능한 OPEN 이 됨**(verifier r3 H-1 high, **닫는다**로 신설 — G-4 수정의 잔여 결함) | permit 을 얻은 뒤의 결말을 `settlePermit` 하나로 좁혀 `try/finally`로 강제 — `BudgetExhausted`는 `releasePermission()`만 부른다(onError 아님, G-4 의미 유지). 새 가지가 늘어도 `finally`가 안전망이라 permit 이 새지 않는다 | `BreakerTest.HALF_OPEN 에서 예산 소진 호출이 permit 을 반납해 이후 호출이 서버에 닿는다(H-1)` — OPEN→HALF_OPEN 전이 뒤 permit 수(기본 10)보다 많은 예산 소진 호출 15회가 전부 서버에 닿고, 그 뒤 넉넉한 예산 호출도 `CircuitOpen` 없이 서버에 닿아 `Predicted`를 받는다. RED 확인: `git stash`로 프로덕션 파일만 되돌려 재현(`expected:<DeadlineExceeded> but was:<CircuitOpen>`) |

## 값 타입 `init` ≠ 게이트 (verifier r2 G-1·G-2 — 수정 라운드 1의 회귀 원인 정정)

r1 F-5 수정이 `BidRateCandidates.init`(순서)·`PriceFitness.init`(부호)을 값 타입에
직접 추가하면서, 짝이 되는 구조 검증층 술어를 빠뜨렸다(F-2가 `hasNonBlankRelease`로
세운 패턴을 F-5가 따르지 않음) — 그 결과 응답이 이 조건을 어기면 `IllegalArgumentException`이
`mapSuccess`→`handleSuccess`→`predict`를 뚫고 gateway 밖까지 샜다(r2 G-1). **규칙**: 값
타입 `init`은 마지막 안전판이지 게이트가 아니다 — 응답이 만들 수 있는 모든 `init` 위반은
`isAcceptableSuccessShape`(구조 검증층, `ParsedSuccessFields.kt`)의 술어가 먼저 잡아
`Unavailable(ContractViolation)`으로 내야 한다. 매핑 단계 전체를 하나의 함수에서
`runCatching`으로 감싸는 catch-all은 금지(CLAUDE.md) — 조건을 술어로 열거해야 한다.
이 slice가 적용한 두 처방:

- **게이트를 짝지운다(G-1)** — `BidRateCandidates`의 순서 불변식은 그대로 두고
  `hasOrderedCandidateRates`(`CandidateShapeValidation.kt`)를 `isAcceptableSuccessShape`의
  `checks` 목록에 추가해 응답 단계에서 먼저 걸린다. `init`은 이제 그 술어가 실패로 놓친
  경우에만 발동하는 방어책(defense-in-depth)이다.
- **근거 없는 불변식은 게이트가 아니라 삭제한다(G-2)** — `PriceFitness.score >= 0`은
  proto 계약 문면(「값의 산식은 이 계약이 규정하지 않는다」)에 근거가 없었다. 게이트를
  짝지우는 대신 `init` 자체를 없애 정직한 음수 값이 그대로 통과하게 했다 — 「게이트를
  세운다」와 「불변식을 없앤다」 둘 다 이 규칙의 합당한 해법이고, 선택은 계약 문면이 그
  값에 실제 제약을 두는지로 갈린다.

**verifier r3 H-6** — 위 짝 대조가 F-2 → G-1 로 손으로 두 번 났다(같은 클래스의 결함이
반복). `SuccessShapeFailClosedTest`의 table-driven test 하나가 값 타입마다 `init`이
던지는 조건(`BidRateCandidates` 두 부등식·`Uncertainty.sampleSize`·`ModelReleaseRef`
다섯 성분, `PriceFitness`는 G-2로 `init` 자체가 없어 대상 제외)을 표로 열거하고, 같은
입력을 실 gateway 경로에 넣어 예외 없이 기대한 `Unavailable` 사유로 접히는지 대조한다
— 새 `init` 조건이 검증층 술어 없이 추가되면(세 번째 F-5/G-1 반복) 이 test 가 떨어진다.

**verifier r3 H-2** — 회귀 test 가 아무리 정확해도 `gate.tests.adapters`(`config/quality/
gate-tests.properties`)에 등재되지 않으면 삭제·비활성화돼도 `check`가 초록이다. 신설
`MlGateRegistrationTest`가 이 등재 완전성 자체를 test 로 잰다 — `adapters/ml` 소스
디렉터리 스캔과 properties 파싱을 직접 대조해, 등재가 빠진 `*Test` class 를 잡는다
(자기 자신 포함, T-10 변이로 재확인 — commands.md 참고).

## 값 획득·위조 축 표(최종)

| 타입 | 판정 | 근거 |
| --- | --- | --- |
| `CallBudget` | 연다(불변식으로 닫힘) | 호출부(workflow 후속)가 남은 예산에서 만든다 |
| `BidPredictionRequest`·`AgencyId`·`CompetitionSample`·`ModelReleaseSelector.Exact` | 연다(불변식으로 닫힘) | 호출부가 조립. 자유 `String` 0 |
| `BidRateCandidates`·`PriceFitness`·`Uncertainty`·`ModelReleaseRef`·`BidPredictionOutcome.Predicted` | **공개(위 「설계 검토 대비 결정 변경」), 값 불변식은 마지막 안전판일 뿐 게이트가 아니다(verifier r1 F-5 → r2 G-1·G-2로 정정)** | `mapSuccess`(어댑터)가 유일한 실제 생성 경로. **정정(G-1·G-2)**: `init`은 절대 gateway 응답 경로에서 단독으로 걸려서는 안 된다 — 응답이 만들 수 있는 모든 `init` 위반은 구조 검증층(`isAcceptableSuccessShape`)의 술어가 먼저 잡아 `Unavailable(ContractViolation)`으로 접어야 한다(F-2 `hasNonBlankRelease` 패턴, 아래 「값 타입 `init` ≠ 게이트」). `BidRateCandidates`(conservative≤base≤aggressive)는 `hasOrderedCandidateRates`(우회 15)로 이 패턴을 따른다. `PriceFitness`는 애초에 부호 불변식의 계약 근거가 없어(proto 주석) **G-2에서 `init` 자체를 제거**했다(게이트가 아니라 불변식 삭제가 정답인 사례) — 정직한 음수 값이 그대로 `Predicted`로 통과한다. `ModelReleaseRef`는 다섯 성분 비공백(F-2, 이미 `hasNonBlankRelease` 게이트와 짝) — `PredictionValueTest`·`SuccessShapeFailClosedTest`로 회귀 방지 |
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
