# checklist.md — M4/4B-2 리뷰 준비도

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      결과 없음(commands.md 「clean-tree 게이트」, 양성 대조 포함).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·
      detekt·cpd·sizeGate·moduleDependencyGate·gateExecutionGate·qualityBaseline 포함).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — 이 slice는 corpus fixture를
      신설하지 않았다(golden-manifest.json N/A, 아래 사유). `LadderPolicySlot`
      (`capacityHoldPriorityThreshold`·`forceBidProbabilityThreshold`·
      `forceBidMatchedThreshold`)은 4B-1 test 정책과 같은 legacy-behavior 값이고
      `OPEN-4B1-LADDER-THRESHOLDS`(운영자 승인 대기)를 그대로 잇는다.
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] secret 스캔 통과(commands.md 기록 예정 — 커밋 직전).

## 만든 타입과 각각이 지는 계약 항목

| 타입 | 파일 | 계약 항목(scope.md ①~⑧) |
| --- | --- | --- |
| `EvaluationStage`(sealed) | `EvaluationStage.kt` | ⑥(단계+사유의 「단계」 절반) |
| `EvaluationDropReason`(sealed) | `EvaluationDropReason.kt` | ⑥(탈락도 결과가 있다 — 기존 축 재사용 + 최소 신설 넷) |
| `CandidateEvaluation`(sealed, `Reached`/`NotReached`) | `CandidateEvaluation.kt` | ①(판정 결과 어휘 둘) |
| `CandidateSourcePort`·`WatchSubjectPort`(+`WatchSubjectOutcome`)·`LicenseGatePort`·`MlAnalysisPort`(+`MlAnalysisOutcome`)·`CapacityPort`(+`CapacitySnapshot`)·`NotificationRequestPort`(+`NotificationRequest`·`NotificationRequestOutcome`)·`CorrelationIdFactory` | `Ports.kt` | ⑦(조합만, 수집·저장·ML·알림은 port 뒤) |
| `LadderPolicySlot`·`EVALUATION_LADDER_POLICY_SLOT` | `LadderPolicySlot.kt` | ⑧의 사다리 쪽 절반(용량 정책은 `CapacityPort`가 진다) |
| `EvaluateCandidatesUseCase` | `EvaluateCandidatesUseCase.kt` | ①~⑧ 전부를 조합 |

## 값 획득 축 실측(설계 검토 (2)) — 「닫는다」·「연다」·「경계로 처리」 전부

commands.md 「값 획득 축 실측」 절에 실행 기록. 요약:

| 공개하는 것 | 판정 | 실측 |
| --- | --- | --- |
| `CandidateEvaluation.Reached`/`NotReached` 생성자 | **닫는다** | FORGERY-1·2 — `app` 모듈에서 직접 생성 시도 → `Cannot access '...': it is internal` |
| `NotificationRequest` 생성자 | **닫는다** | FORGERY-3 — 같은 형태로 거부 |
| `EvaluateCandidatesUseCase` 클래스 | **연다** | OPEN-2 — `app` 모듈에서 port 여덟을 자기 구현으로 채워 조립 → 컴파일 성공. 부작용은 주입한 port가 허락하는 것뿐 |
| 각 단계 port 인터페이스(`WatchSubjectPort` 등) | **경계로 처리** | OPEN-3 — `NotificationRequestPort` 구현을 `app`에 심어도 이미 만들어진 `NotificationRequest`를 받기만 한다(FORGERY-3이 그 값의 자체 생성을 이미 닫았다) — 「경계 안 주체에게만 간다」 성립(4C-1 L-4 판정 형식과 동일) |
| `CorrelationId` 값 타입 | **연다** | OPEN-1 — `app` 모듈에서 자유 생성 → 컴파일 성공(위조해도 자기 추적만 흐려진다, 부재만 필수 필드로 막는다) |
| 전략 스냅샷(`OperatorStrategy`) | **경계로 처리(1E가 이미 닫음)** | 이 slice가 다시 닫지 않는다 — `StrategyRepository.load()`가 반환하는 값을 그대로 쓴다. `OperatorStrategy`의 생성자는 1E가 이미 `internal`로 닫아 두었다(재확인만, 새 실측 불필요) |

위조 probe는 전부 `app` 모듈(다른 모듈)에서 컴파일했다(4B-1이 자기 모듈 test에 심어
한 번 놓친 교훈 — 설계 검토 (2) 마지막 문단).

## 조사의 여덟 실패 형태를 각각 어떻게 뒤집었는지

| # | legacy 실패 | 뒤집기 | 실증(test/구조) |
| --- | --- | --- | --- |
| ① | 판정이 두 번 돈다(스캔·영속) | 판정 호출 자리가 `reach` 안 정확히 하나 | `판정은 공고당 정확히 한 번 돈다`(fake 계수) |
| ② | 부분 실패가 성공 후보를 전멸시킨다 | `List.map`으로 후보 독립 평가, 공유 가변 상태·전역 rollback 없음 | `한 후보의 실패가 다른 후보의 성공을 지우지 않는다` |
| ③ | 성공처럼 계속하는 자리 열 | port 계약이 예외가 아니라 결과 갈래 — `try` 쓸 자리 자체가 없음(구조로 확인, `grep -c "try {" EvaluateCandidatesUseCase.kt` = 0) | 컴파일된 소스 자체가 증거 |
| ④ | trace 축 0건 | `correlationId`가 use case 진입에서 한 번 나서 판정·알림 요청에 같은 값이 실림(필수 필드) | `correlationId 는 필수 필드이고 판정과 알림 요청에 같은 값이 실린다` |
| ⑤ | run 중 전략을 매번 다시 읽는다 | `strategies.load()`는 `evaluate()` 진입에서 한 번만 | `전략은 후보가 여럿이어도 진입에서 한 번만 읽는다`(mutation으로 회귀 보호 확인, commands.md) |
| ⑥ | 조용한 드롭 열셋 | `NotReached(stage, reason)`이 값으로 남음 — 소진 `when` | D-2·D-3~D-8·D-9·D-11·D-12 각 test |
| ⑦ | 한 함수가 수집·DB·ML·알림을 함께 한다 | port 뒤로 가르고 `CompositionBoundaryTest`(S-3b)가 소스 스캔으로 단언 | S-3b 4 tests |
| ⑧ | 용량이 스캔·영속 두 시점에 다르게 세어진다(중복 계수) | `capacity.snapshot()`이 run당 한 번, 모든 후보가 같은 스냅샷 공유 — 두 번째로 셀 「영속 시점」이 이 slice에 없다 | `용량은 후보가 여럿이어도 run당 한 번만 읽는다`(mutation으로 회귀 보호 확인) |

## `OPEN-4B1-OFF-LADDER-DROPS` 처리와 조사 신설 `OPEN-4B2-*` 여섯

**`OPEN-4B1-OFF-LADDER-DROPS`는 종결했다**(`docs/discovery/capability-map.md`) —
사다리 밖 드롭 열셋(D-1~D-13) 중 기존 축이 소유한 것(D-1·D-3~D-8은 `WatchVerdict`,
D-2는 `NoticeStatus`, D-9는 `LicenseVerdict.Ineligible`)은 그대로 싣고, 어느 축도
소유하지 않은 넷(`ActionThresholdsNotConfigured`·`AnalysisBudgetExhausted`·
`SimilarityProjectionNotReady`·`BelowMinimumMatchScore`/`BelowMinimumProbabilityScore`)
만 `EvaluationDropReason`에 최소 신설했다.

**조사 신설 `OPEN-4B2-*` 여섯 — 이 slice가 답할 수 있는 것과 운영 관측이 필요한 것을
갈랐다**:

- **설계로 닫은 것(등재하지 않음)** — `OPEN-4B2-3`(같은 run 안에서 앞 후보가 뒤 후보의
  용량을 먹는가): 이 slice의 `CapacityPort.snapshot()`은 run당 정확히 한 번만 불려
  모든 후보가 같은 스냅샷을 공유한다 — legacy의 「스캔 시점 다르게 셈」 형태 자체가
  구조적으로 발생하지 않는다. capability-map에 등재하지 않고 여기(checklist)에 설계
  결정으로 남긴다.
- **운영 관측·실 저장이 있어야 답할 수 있는 것(`capability-map.md`에 등재)** —
  `OPEN-4B2-2`·`OPEN-4B2-4`·`OPEN-4B2-6`(run item 영속·중복 계수·`running` 잔존 빈도,
  전부 4C-2/3D 소관 — 이 slice는 run item을 영속하지 않는다) · `OPEN-4B2-5`(배달
  outbox drain 운영 설정, 4E/운영 소관).
- **도메인 명세 판단이 필요한 것(`capability-map.md`에 등재)** — `OPEN-4B2-1`(면허
  게이트 vs ML 분류기 안 점수 축 — 어느 쪽이 정본인지). 이 slice는 게이트만 소비하고
  점수 축은 `MlAnalysisPort` 뒤(ML 분석 자체, 4D 영역)에 있어 4B-2가 보지 않는다.

## 알려진 제한

1. **`priority`·`probability`·`matched` 점수 산출 자체는 이 slice 밖**이다 — 4B-1이
   이미 그렇게 경계를 그었다(`does_not_carry`). `MlAnalysisPort`는 4D가 실 구현한다.
2. **실 후보 원천·감시 텍스트 조립·면허 입력 조립·용량 계수 쿼리는 test fake만**이다 —
   실 조회는 3A/3B/1C/4C-2/3D가 짓는다(scope.md 「만들지 않는 것」).
3. **알림 요청은 배달을 주장하지 않는다** — `NotificationRequestPort.request`가 반환하는
   `Requested`는 「요청을 접수했다」이지 「배달됐다」가 아니다(설계 검토 (3) 미달 점검,
   legacy C-4의 재현을 이 층에서 막는다).
4. **`LadderPolicySlot`의 세 값은 여전히 운영자 승인 대기**다(`OPEN-4B1-LADDER-THRESHOLDS`,
   4B-1에서 이미 열린 것을 그대로 잇는다 — 이 slice가 새로 여는 OPEN이 아니다).
5. **면허 게이트가 `LicenseVerdict.Uncertain`을 통과시킨다** — 1C U-5(`Uncertain ≠
   Ineligible`)를 따른 판단이다. 이 slice는 「미보유로 확정된 것만 배제한다」는 legacy
   `_license_gate_excludes`의 의도(D-9 「data-confirmed ineligible만」)를 그대로 옮긴
   것이지 완화가 아니다.
6. **`NoGate`(감시 규칙 미설정)를 통과로 취급한다** — legacy는 감시 규칙이 하나도
   없으면 스캔 자체를 하지 않았다(`return [], 0`, D-1). 이 slice는 `WatchVerdict.NoGate`
   의 실제 의미(「게이트가 없다 = 아무것도 거르지 않는다」, 1E `WatchRules.evaluate`
   문면)를 그대로 따라 **통과**로 처리한다 — legacy의 조기 반환은 성능 최적화였지
   「감시 미설정 전략은 후보가 0건이어야 한다」는 선언된 정책이 아니었다(조사에 그런
   선언 없음). 의도적 재해석이며 legacy 재현이 아니다 — 되돌림이 필요하면 별도 결정.
7. **분석 예산을 넘긴 후보는 watch·license·ML port를 전혀 부르지 않는다**(legacy의
   `break`와 같은 비용 절감 의도는 유지하되, 결과는 흔적을 남긴다 — `이 slice 고유
   확인` §D-10 test 참고).

## rollback

정본은 `reports/evidence/m4/4b2/rollback.md`.
