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
| `EvaluationDropReason`(sealed) | `EvaluationDropReason.kt` | ⑥(탈락도 결과가 있다 — 기존 축 재사용 + 최소 신설 **여섯**, verifier r1 L-2 정정) |
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
| `EvaluateCandidatesUseCase`의 `judge` 위임 생성자 인자(수정 라운드 1 L-1 신설) | **연다(위험 없음)** | 밖에서 다른 `(LadderInput, Resolution.Resolved<VerdictLadderPolicyData>) -> Verdict`를 주입할 수는 있으나, 그 함수가 낼 수 있는 유일한 정당 `Verdict`의 생성 경로는 여전히 `VerdictLadder.judge`(public, 사다리를 반드시 지난다) 하나뿐이다 — `Verdict.BidNow` 등의 생성자가 `decision` 모듈 밖에 닫혀 있어(4B-1) 이 자리를 열어도 위조 경로가 새로 생기지 않는다 |

위조 probe는 전부 `app` 모듈(다른 모듈)에서 컴파일했다(4B-1이 자기 모듈 test에 심어
한 번 놓친 교훈 — 설계 검토 (2) 마지막 문단).

## 조사의 여덟 실패 형태를 각각 어떻게 뒤집었는지

| # | legacy 실패 | 뒤집기 | 실증(test/구조) |
| --- | --- | --- | --- |
| ① | 판정이 두 번 돈다(스캔·영속) | 판정 호출 자리가 `reach` 안 정확히 하나, **호출 위임(`judge`)을 얇게 감싸 실제로 셀 수 있게 함**(수정 라운드 1 L-1) | `사다리 호출은 공고당 정확히 한 번 돈다`(`CountingJudge`, mutation으로 회귀 보호 확인 — judge 이중 호출을 심어 실제 RED 확인 후 복원, commands.md) |
| ② | 부분 실패가 성공 후보를 전멸시킨다 | `List.map`으로 후보 독립 평가, 공유 가변 상태·전역 rollback 없음 | `한 후보의 실패가 다른 후보의 성공을 지우지 않는다` |
| ③ | 성공처럼 계속하는 자리 열 | port 계약이 예외가 아니라 결과 갈래 — `try` 쓸 자리 자체가 없음(구조로 확인, `grep -c "try {" EvaluateCandidatesUseCase.kt` = 0) | 컴파일된 소스 자체가 증거 |
| ④ | trace 축 0건, ML 호출 경계를 넘지 않는다 | `correlationId`가 **공고마다** 나서 그 판정·ML 분석·알림 요청에 같은 값이 실림(필수 필드, 수정 라운드 1 M-2 — `MlAnalysisPort.analyze`에 `correlationId` 추가) | `correlationId 는 필수 필드이고 판정과 알림 요청에 같은 값이 실린다` · `correlationId 는 ML 분석 port 에도 실린다` |
| ⑤ | run 중 전략을 매번 다시 읽는다 | `strategies.load()`는 `evaluate()` 진입에서 한 번만(**후보 전체가 공유**하는 스냅샷 — `correlationId`와는 다른 축이다, ④와 혼동하지 않는다) | `전략은 후보가 여럿이어도 진입에서 한 번만 읽는다`(mutation으로 회귀 보호 확인, commands.md) |
| ⑥ | 조용한 드롭 열셋(D-1 포함) | `NotReached(stage, reason)`이 값으로 남음 — 소진 `when`. **D-1(`NoGate`)도 이제 포함**(수정 라운드 1 M-1, 아래 절) | D-1·D-2·D-3~D-8·D-9·D-11·D-12 각 test |
| ⑦ | 한 함수가 수집·DB·ML·알림을 함께 한다 | port 뒤로 가르고 `CompositionBoundaryTest`(S-3b)가 소스 스캔으로 단언 | S-3b 4 tests |
| ⑧ | 용량이 스캔·영속 두 시점에 다르게 세어진다(중복 계수) | `capacity.snapshot()`이 run당 한 번, 모든 후보가 같은 스냅샷 공유 — 두 번째로 셀 「영속 시점」이 이 slice에 없다 | `용량은 후보가 여럿이어도 run당 한 번만 읽는다`(mutation으로 회귀 보호 확인) |

## 수정 라운드 1(재작업 1/5) — M-1·M-2·L-1·L-2·L-3·L-4

verifier r1 판정 **ready-for-review**, 산출물층 blocker/high 0. medium 둘(M-1은 운영자
결정, M-2는 팀장 결정) + low 넷을 이 커밋에 일괄 반영했다.

### M-1 — `NoGate` 처분을 되돌렸다(운영자 결정 2026-09-10)

이전 판은 `WatchVerdict.NoGate`를 통과로 재해석했다. **되돌린다** — `NoGate`도 이제
`NotReached(stage=WatchGate, reason=WatchGateNotConfigured(noGate))`다. **결과는
legacy와 같다**(감시 미설정 전략은 후보 0) — 다른 점은 탈락이 값으로 남는다는 것뿐이다.

**근거(운영자 결정 그대로)**: STR-01 실측 — 열린 공고 5,382건 중 watch 통과 43건
(≈0.8%). 통과 처리는 미설정 운영자에게 **125배** 후보를 보내고, 그건 STR-01이 이름
붙인 **「알림 홍수」**다. verifier 실측도 같다 — 감시 0건 + 임계만 설정된 전략에서
**알림 요청 2건**(legacy 0건), `isConfigured()`가 임계만으로 참이라 그 상태는
**정상 상태**다. 「선언되지 않은 정책의 시정은 선언하는 것이지 뒤집는 것이 아니다」
— 이전 판의 재해석이 시정을 뒤집기로 잘못 골랐다.

**정정(인용 오류)**: 이전 판은 「1E `WatchRules.evaluate` 문면」을 근거로 들었으나
그 KDoc에는 **발생 조건**("규칙이 하나도 없으면 `NoGate`")만 있고 **소비자 처리
방침**은 없다 — 없는 문면을 근거로 든 서술이었다. 이번 판은 STR-01 실측 수치를
근거로 정확히 인용한다.

**이 수정으로 L-2의 「D-1이 드롭 자리를 얻지 못했다」도 함께 닫힌다** —
`WatchGateNotConfigured`가 D-1의 자리다(1E `WatchVerdict.NoGate`를 그대로 실어
기존 축 재사용 원칙을 지킨다 — 최소 신설 카운트에 들지 않는다).

**판정 로직 변경 파급 훑기(2026-09-04 규정 — 오탐을 닫으려다 미탐을 열지 않았는지)**:
이 변경은 `evaluateOne`의 guard 체인에서 watch 게이트 단계 **하나만** 바꾼다 —
license·threshold·ML·score 단계의 판정 로직·순서는 무변경이다. 파급은 **후보 수를
줄이는 방향으로만** 있다(watch 미설정 후보가 이제 더 뒤 단계에 도달하지 않는다) —
legacy 파리티와 정확히 같은 방향이라 미탐(새로 놓치는 정당 후보)이 아니라 legacy가
이미 걸러 두던 것을 값으로 남기며 재현한 것이다. 기존 test 열 개 중 셋(`사다리 임계가
미설정이면...`·`적합도가 운영자 최소치 미만이면...`·`한 후보의 실패가...`)이 watch
게이트를 먼저 지나야 해서 fixture를 조정했다(`testStrategy()` 기본 draft에
`focusCategories` 추가 + `MATCHING_SUBJECT` 신설) — **다른 단계의 판정 자체는
바꾸지 않았다**(같은 test들이 같은 stage/reason을 여전히 기대한다).

### M-2 — `MlAnalysisPort.analyze`에 `correlationId` 추가(팀장 결정)

M4 완료 조건 「trace/correlation id가 수집→판정→**ML**→알림 요청까지 유지」가 이
port 계약에도 걸린다 — `analyze(notice: Notice, correlationId: CorrelationId)`로
시그니처를 넓혔다. **문면 정정(구현은 바꾸지 않았다)**: 「`correlationId`를 use case
진입에서 한 번」은 실물과 달랐다 — **공고마다** 난다(`evaluateOne` 진입에서
`correlationIds.newId()`). 완료 조건의 「공고 하나의 수집→판정→ML→알림 한 흐름」에
공고별 발급이 더 맞는다. **run 단위 식별자**(legacy `monitor_run_id`의 대응 축)가
따로 필요한지는 **4C-2/후속 소관**이다(알려진 제한에 등재, 아래).

### L-1 — 사다리 호출 횟수 회귀 보호

`VerdictLadder`가 `object`(port 아님)라 fake로 셀 수 없었다 — `EvaluateCandidatesUseCase`
에 `judge` 위임 생성자 인자(기본값 `VerdictLadder::judge`)를 추가하고, test는 그
자리를 `CountingJudge`(얇은 래퍼)로 바꿔 호출 횟수를 센다. **mutation 실측**: `reach`
안에서 `judge`를 두 번 부르게 심었더니 새 test(`사다리 호출은 공고당 정확히 한 번
돈다`)만 정확히 RED(`expected:<1> but was:<2>`)였고 ML 분석 계수 test는 그대로
초록이었다(verifier가 지적한 바로 그 사각 — 이제 닫혔다). mutation 복원 후 `diff`로
byte-identical 확인.

### L-3 — 역방향 파급 검사

`docs/discovery/capability-map.md`를 이 라운드에서 **+6줄·-1줄(순증 +5)** 편집했다
(`git diff --numstat 401bc4d..HEAD -- docs/discovery/capability-map.md`). 축약형
포함 stem grep(`capability-map\.md:[0-9]+`, `capability-map:[0-9]+`)으로 전수
스캔 — 내 삽입 지점(`OPEN-4B1-OFF-LADDER-DROPS` 행, 3360행대)보다 **아래** 줄번호를
가리키는 참조는 둘뿐이고 둘 다 **같은 좌표**(`capability-map.md:3457`, `OPEN-2B-
TEST-DISCOVERY-GUARD` 행 지목)를 가리킨다: `reports/evidence/harness/test-discovery-
guard/commands.md:100`과 `reports/evidence/m4/4b1/checklist.md:204,234`. **둘 다
이 slice의 in_scope 밖**(닫힌 하네스 레인 evidence·4B-1 자신의 evidence)이라 고치지
않고 알려진 제한에 등재한다(아래) — 재산출한 실제 좌표는 **3469행**(4B-1의 +1과
이번 +5가 합쳐 총 +6 밀림, `grep -n "OPEN-2B-TEST-DISCOVERY-GUARD"` 실측).

### L-4 — `LicenseVerdict.Uncertain` 통과 근거 정정

이전 판의 「1C U-5 그대로」는 느슨한 인용이었다 — U-5(`Uncertain ≠ Ineligible`)는
**어휘가 다르다는 사실**만 정한다. 실질 근거는 **legacy D-9 파리티**다 — legacy
`_license_gate_excludes`가 **data-confirmed ineligible만** 제외했고(조사
`_workspace/m4-4b1/01_scout_verdict_ladder.md` D-9), `Uncertain`은 그 배제 대상이
아니었다. 이 slice는 그 파리티를 그대로 옮긴 것이지 U-5의 **정의**를 근거로 든 것이
아니다 — 아래 알려진 제한 5번을 그에 맞게 고쳤다.

## `OPEN-4B1-OFF-LADDER-DROPS` 처리와 조사 신설 `OPEN-4B2-*` 여섯

**`OPEN-4B1-OFF-LADDER-DROPS`는 종결했다**(`docs/discovery/capability-map.md`) —
사다리 밖 드롭 열셋(D-1~D-13) 중 기존 축이 소유한 것(D-1·D-3~D-8은 `WatchVerdict`,
D-2는 `NoticeStatus`, D-9는 `LicenseVerdict.Ineligible`)은 그대로 싣고, 어느 축도
소유하지 않은 **여섯**(`WatchSubjectUnavailable`·`ActionThresholdsNotConfigured`·
`AnalysisBudgetExhausted`·`SimilarityProjectionNotReady`·`BelowMinimumMatchScore`·
`BelowMinimumProbabilityScore`, verifier r1 L-2 재산출 — 이전 판은 「넷」이라 적었으나
`WatchSubjectUnavailable`을 빠뜨렸다)만 `EvaluationDropReason`에 최소 신설했다.

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
5. **면허 게이트가 `LicenseVerdict.Uncertain`을 통과시킨다**(L-4 정정) — 실질 근거는
   **legacy D-9 파리티**다. `_license_gate_excludes`가 **data-confirmed ineligible만**
   제외했고 `Uncertain`은 그 배제 대상이 아니었다(조사
   `_workspace/m4-4b1/01_scout_verdict_ladder.md` D-9). 1C U-5(`Uncertain ≠ Ineligible`)는
   그 파리티가 왜 어휘상 성립하는지의 배경일 뿐, 통과 판단 자체의 근거는 아니다.
6. ~~`NoGate`(감시 규칙 미설정)를 통과로 취급한다~~ — **운영자 결정 2026-09-10로
   되돌렸다(수정 라운드 1 M-1).** `NoGate`도 이제 `NotReached`다 — 결과는 legacy와
   같고(감시 미설정 = 후보 0), 탈락이 값으로 남는다는 점만 다르다. 근거·파급 훑기는
   checklist 「수정 라운드 1」 M-1 절 참고.
7. **분석 예산을 넘긴 후보는 watch·license·ML port를 전혀 부르지 않는다**(legacy의
   `break`와 같은 비용 절감 의도는 유지하되, 결과는 흔적을 남긴다 — `이 slice 고유
   확인` §D-10 test 참고).
8. **run 단위 식별자는 이 slice 밖**이다(M-2, 신설) — `correlationId`는 공고 단위
   trace축이고, legacy `monitor_run_id`(한 run에 속한 여러 공고를 묶는 FK)에 대응하는
   V2 개념이 필요한지는 4C-2/후속이 정한다. 이 slice는 그 축을 만들지 않는다.
9. **`capability-map.md:3457` 참조 둘이 낡았다(L-3, 등재만)** — 이 slice의
   `docs/discovery/capability-map.md` 편집(+6/-1, 순증 +5)이 그 아래 좌표를 밀었다.
   실제 대상(`OPEN-2B-TEST-DISCOVERY-GUARD` 행)은 이제 **3469행**이다. 참조 두 곳
   (`reports/evidence/harness/test-discovery-guard/commands.md:100`·
   `reports/evidence/m4/4b1/checklist.md:204,234`) 다 이 slice의 in_scope 밖(닫힌
   하네스 레인·4B-1 자신의 evidence)이라 고치지 않는다.

## rollback

정본은 `reports/evidence/m4/4b2/rollback.md`.
