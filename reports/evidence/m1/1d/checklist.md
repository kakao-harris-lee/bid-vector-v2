# checklist — M1 / 1D (Provenance first-match · Floor Shortfall)

레인(kotlin-implementer)이 슬라이스 마감에 쓴다. 출력 전문·라운드 이력 절 없음
(`evidence-pack` 규격). 종결 = **verifier ready-for-review + 사용자 승인**(Codex 없음,
CLAUDE.md 운영자 지시 2026-09-04).

## 구현 순서(커밋 4개, TDD RED→GREEN + 후속 1개)

1. `95585b5` — shared-kernel 좁은 확장(D-1(a)+D-9): `Rate.fraction` 공개 읽기 +
   `Comparable<Rate>`, `BidRate.recommended`·`AssessmentRate.observed` factory,
   `criticalAssessmentRate` 나눗셈. `RateArithmeticTest` 신규.
2. `a865bc6` — `decision` 모듈 배선(`qualification` 관례 복제) + provenance/floor
   커널 둘. `ProvenanceRulesTest`·`FloorShortfallKernelTest` 신규.
3. `ccc1f1b` — corpus runner 확장(`ProvenanceFloorExecutors.kt` 신규, `TARGET_DOMAINS`
   +3축). authoritative 7 case 전건 대조.
4. `bc15983` — `gate-tests.properties` 에 `gate.tests.decision` 등재.
5. `af25a38`(후속, curator `32b1b39`·`cec7732` 뒤) — `floor-threshold-002` dispatch
   등재(P-5 복구) + `floorThresholdExecutor`가 입력 `policy` 블록을 실제로 읽도록
   수정(아래 「판단이 갈린 지점」) + 스윕 `ASSERTED` 표 한 행. authoritative 8 case
   전건 대조로 갱신.
6. `1ddbb69`(verifier r1 F-1+F-3) · `f7cc530`(F-4) · `e0b8a3b`(F-2) — 아래
   「verifier r1 수정 라운드 처리」 참고.

## verifier r1 수정 라운드 처리 (재작업 누계 1/5)

`_workspace/m1-1d/02_verifier_report.md` not-ready(F-1 high) 뒤 finding 별로 처리했다.
D-10 서명 정정(`41d730b`, scope.md·milestone-1.md)은 팀장(세션 모델)이 먼저 커밋했다 —
이 레인은 그 정정을 구현했다.

| id | sev | 처리 |
| --- | --- | --- |
| F-1 | high | `criticalAssessmentRate`가 `Measurement<Derived<AssessmentRate>>`를 낸다(decision 17). `AssessmentRate.observed` 대신 같은 모듈의 `internal` 생성자를 직접 쓴다. `FloorShortfallJudgement.critical`·`FloorShortfall.Measured.criticalAssessmentRate`도 `Derived<AssessmentRate>`로 바꿔 봉투가 정책 version을 그대로 나른다. 컴파일 probe로 bare `AssessmentRate`가 `measureFloorShortfall`에 타입 불일치로 거부됨을 실측(`1ddbb69`). |
| F-3 | low(F-1과 동봉) | 새 함수 `criticalAssessmentRateFor(bid, floor, policy: Resolution.Resolved<FloorShortfallPolicyData>)`가 `criticalRateScale` 슬롯에서 `RoundingPolicy`를 만든다 — 이 함수가 그 슬롯의 유일한 소비자다. `floorShortfallExecutor`가 이 경로로 바꿔 배선을 완성했다(`1ddbb69`). |
| F-2 | medium | `isCleanInteger`·`isDerivedYega`·`isDerivedVat` 각각에 음성 case·경계 case(허용 오차 미만만 참) 를 추가했다. 세 술어를 실제로 항상 true 로 바꿔 신규 6 test 전부가 즉시 실패함을 확인(`e0b8a3b`). |
| F-4 | low | `floorThresholdExecutor`의 `$.comparison` 문자열이 이제 `policy.shortfallComparison` 토큰에서 나온다(`comparisonStringFor`). 정책 부재 입력은 decision 28 초기값 문면을 쓴다(`f7cc530`). |
| F-5 | low(장부) | `commands.md`의 「M 6·D 12」를 「M 6·D 14」로 정정 — `git rm -f` 목록 실제 항목 수(14)와 재실측 결과가 일치한다. |
| F-6 | low(장부) | 팀장(세션 모델)이 `milestone-1.md`에서 이미 처리(`41d730b`) — 이 레인은 건드리지 않았다. |

## D-1(a)/D-9 셋+하나 이행 대조

scope.md 운영자 결정: "in_scope 의 shared-kernel 세 경로가 열린다"(`BidRate.recommended`·
`AssessmentRate.observed`·`criticalAssessmentRate`) + D-9 사후 확인 대상(넷째 —
`Rate.fraction` 공개 읽기·`Comparable<Rate>`).

| 항목 | 구현 | 위치 |
| --- | --- | --- |
| `BidRate.recommended(rate)` | `companion object` factory, origin=`Recommended` | `Rate.kt` |
| `AssessmentRate.observed(rate)` | `companion object` factory, KDoc이 "파생 아님"을 진술 | `Rate.kt` |
| `criticalAssessmentRate(bid, floor, scale)` | `Measurement<Derived<AssessmentRate>>` 반환(verifier r1 F-1 — decision 17), VAT 전건 없음(Rate 는 그 축을 안 나름) | `RateArithmetic.kt`(신규 파일) |
| D-9 (사후 확인) `Rate.fraction` 공개 + `Comparable<Rate>` | 생성자는 `internal` 그대로, 읽기만 공개 | `Rate.kt` |

**사후 확인**: D-9 없이는 `floor-threshold-001`·`003`(`$.criticalAssessmentRate.fraction`
검증)를 runner 가 실행할 방법이 없었다(scout §8 blocker) — 이번 구현이 그 확인이다.

## 위협 모델 우회와 막는 자리 (scope.md 대응표)

| # | 우회 | 막는 자리 | 실측(commands.md 변이) |
| --- | --- | --- | --- |
| (a) `Unmeasurable`→`0`/`Measured` 접기 | `FloorShortfall.Measured` 생성자 internal, 생성 경로는 `measureFloorShortfall` 하나 | 변이 2 |
| (b) 확률 어휘가 공개 표면에 | `Frequency`·`FloorShortfall`·`ProvenanceJudgement` 등 공개 타입·필드 이름에 `probab` 문자열 0(육안 확인 — grep 결과 없음) | 코드 리뷰만(자동 test 없음, 알려진 제한 ①) |
| (c) rule order 가 코드 상수화 | `ProvenanceRules.firstMatchedRule` 이 `policy.value.ruleOrder` 만 읽는다, 정책 데이터 부재 시 `ProvenancePolicyData.init` 이 구성 자체를 거부 | 변이 1 |
| (d) 복구 추정치가 원본 자리에 기록 | `ProvenanceJudgement.original`·`recoveryEstimate` 별도 필드, `Money` 구조적 불변 | `ProvenanceRulesTest.③ 원본 참조는...보존된다`(원본 참조 동일성 property) |
| (e) 최소 표본·경계 비교·밴드가 리터럴 | `domainSourceReferenceGate` 는 못 잡는다(1C F-3 과 같은 한계) — 정책 파일 키 부재 → 구성 실패로 대신, main 소스 리터럴 스캔은 코드 리뷰 | 육안 확인(아래 「main 리터럴 부재 확인」) |
| (f) 밴드 밖 표본이 분모에 잔류 | `ShortfallTally.qualifiedDenominator = rawCount - outsideBand`(계산 프로퍼티, 별도 생성자 인자 아님) | 변이 3 |
| (g) 규칙 무매치가 `Clean` 으로 접힘 | `classificationFor(null) = Unknown`, `Clean` 은 `CleanInteger` 매치 경로로만 | 변이 5 |
| (경계 등가) DEC-04 무조건 acceptance | `ShortfallComparison.StrictlyGreater`(decision 28) — 정확히 경계값인 표본은 미달 아님 | 변이 4 — 최초 단위 test 단독 방어, 후속(`af25a38`, ft-002 배선) 재실측으로 corpus(`floor-threshold-002`)도 함께 캐치 확인(알려진 제한 ② 갱신) |

## main 리터럴 부재 확인

`grep -nE "1\.15|0\.05|1e-6|0\.90|1\.10|1\.10|0\.01|\b150\b|\bscale.*=.*6\b" decision/src/main/kotlin -r`
— 매치 없음(정책값은 test 정책 인스턴스와 runner 에만 있다). 유일하게 main 에 있는 정수
리터럴은 `RateArithmeticTest`/`FloorShortfallKernelTest` 밖의 **test** 코드에 있는
`LEGACY_CRITICAL_RATE_SCALE_DIGITS = 6`(`ProvenanceFloorExecutors.kt`, app 모듈 test
소스 — decision main 이 아니다)와 `Frequency`/`ShortfallTally` 의 구조적 하한(`0`,
`denominator > 0`)뿐이다 — 이들은 정책값이 아니라 타입 불변식이다.

## OPEN 처리

- **`OPEN-DIC-05`**: decision 27 로 해소 — `classificationFor(null) = Unknown`(라벨 다섯
  불변) + 판정 레코드 부재를 커널이 만들지 않음(`ProvenanceJudgement` 없이는 어떤 소비자도
  `Clean` 을 얻지 못한다 — 이 슬라이스는 판정 레코드 자체만 내고 저장 상태는 M3 소관).
- **`OPEN-DEC-06`**: `biasDirection` 필수 필드로 구현(D-8) — `Overestimates`·
  `Underestimates`·`Indeterminate` 셋, `biasIndeterminateBand` 정책값 기준.
- **`OPEN-DEC-01`**: `minAssessmentSamples`+`minAssessmentSamplesRationale`(근거 문자열
  필수, blank 거부) 구현.
- **`OPEN-DEC-07`**: 값을 넣지 않았다 — `trustRatioMax: Rate?` 슬롯만, `SuspectRatio` 가
  `ruleOrder` 에 있는데 슬롯이 비면 `ProvenancePolicyData.init` 이 구성을 거부(D-5). 활성
  유지.
- **`OPEN-DIC-10`**: 십진 렌더링 미구현 유지 — `Frequency` 는 유리수(`numerator`/
  `denominator: Int`)만 낸다.
- **`OPEN-DEC-03`**: 1D 축 아님(스코프에서 확인, 건드리지 않음).

## 알려진 제한

1. **확률 어휘 금지(위협 모델 (b))는 자동 test 가 없다** — `checklist.md` 의 육안 확인
   (`Frequency`·`FloorShortfall`·`ProvenanceJudgement`·`FloorUnmeasurableReason` 공개
   필드·타입 이름에 `probab` 없음)에만 의존한다. 1C 도 같은 한계(우회 (5), `typeShapeGate`
   가 문자열 내용을 못 본다)를 알려진 제한으로 등재했다 — 이름 스캔 gate 신설은 이
   slice `out_of_scope`(게이트 정의 편집은 하네스 저자 소관).
2. **[해소 — 후속 커밋 `af25a38`] 경계 등가 방어는 이제 corpus 도 진다.** `floor-threshold-001`·
   `003`의 realized 값(1.0499·1.0501)은 critical(1.05)과 정확히 같지 않아 그 쌍은 경계
   등가 자체를 겨누지 않는다는 것은 여전히 사실이나, curator 가 승격한
   `floor-threshold-002`(realized=critical=1.05)가 그 자리를 authoritative corpus 로
   채웠다 — `mutation_sweep_adversarial.py`의 `$.sampleIsShortfall` 값 변이가 캐치됨을
   실측(변이 44→47). `FloorShortfallKernelTest.④`는 여전히 두 번째 방어선(다른 축의
   critical 값)이다.
3. **[해소 — 후속 커밋 `af25a38`] `floor-threshold-002`는 authoritative 8번째 case로
   등재됐다**(curator `32b1b39`) — dispatch 표에 등재하고, runner가 그 입력의
   `policy.shortfallComparison`을 실제로 읽도록 고쳤다(아래 「판단이 갈린 지점」).
4. **어휘 ⑥(`AmountNotRepresentable`)은 미채택** — `base-amount-provenance-004`·`005`
   가 강등 상태라 계약에 걸리지 않는다. `ReasonCode` 에 여섯째 값을 더하지 않았다(scout
   §5.2 ⑥ 그대로).
5. **`FloorUnmeasurableReason`↔`ReasonCode` 다리는 만들지 않았다**(D-14 그대로) — 두
   어휘가 만나는 자리에 변환 함수가 없다. OPEN 후보로만 남긴다(scout §5.2 ⑦).
6. **rowHits 기반 corpus 실행은 predicate(`isSuspectRatio` 등)를 한 번도 부르지 않는다** —
   authoritative 3 case 전부 `ruleHits` 로 매치를 추상 선언하므로(scout §5.3), 술어 넷의
   실측 커버리지는 `ProvenanceRulesTest` 의 단위 test(row 기반)만 진다. 술어 값(허용
   오차·배수) 자체는 `OPEN-DEC-07`·§12.1 재유도 전이라 corpus 로 잠글 수 없다 — 의도된
   경계(scope.md D-4).
7. **공유 working tree 크로스레인 혼입(장부층, 회귀 아님)** — 커밋 `bc15983`
   (`gate-tests.properties` 등재)에 다른 레인이 스테이징해 둔
   `fixtures/tools/mutation_sweep_adversarial.py`·`mutation_sweep_targeted.py` 편집이
   같은 index 를 통해 혼입됐다(그 레인 자신의 커밋 `6974151` 이 이미 자인). 이 slice
   `out_of_scope` 파일이고 이력을 되쓰지 않는다(하네스 관례, 2026-09-02 CLAUDE.md
   entry와 같은 갈래) — rollback.md 는 이 두 파일을 대상에서 제외한다(아래 「범위 밖
   변경」).
8. **`milestone-1.md`·`capability-map.md`·`data-dictionary.md` 갱신은 이 구현 레인이
   아니라 세션 모델이 착수 전(base_sha 이후 8커밋)에 이미 완료했다** — CLAUDE.md 「기획
   문서 레인」 규율 그대로(1C 알려진 제한 ⑥과 같은 경계).

## 판단이 갈린 지점 — 후속 커밋 `af25a38`

**001·003(정책 블록 없는 입력)의 판정을 어떻게 낼 것인가** — 팀장 지시가 준 선택지:
(a) 두 정책값(`StrictlyGreater`·`GreaterOrEqual`) 모두로 판정해 같음을 단언하고 그
공유값을 낸다(입력 무변경) / (b) curator 에게 001·003 도 `policy` 를 싣도록 요청한다.
**(a) 채택**(팀장 추천과 일치) — 이유: 001·003 의 `verifies` 는 방향(미달/적격)만
주장하고 경계 자체를 주장하지 않으므로, 그 case 들이 어느 비교값에도 무관하게 같은
결과를 내야 한다는 사실 자체가 검증할 가치가 있는 불변식이다(`shortfallWithoutPolicy`
의 `check()`가 그 불변식을 실행마다 재확인한다 — 어느 한쪽만 부르는 것보다 강하다).
(b)는 입력을 늘려야 하고 001·003 의 `verifies` 문면과도 맞지 않는다(그 case 들은
정책 자체를 주장 대상으로 삼지 않는다).

**manifest 갱신 — curator 완료**: `floor-threshold-002`의 `contract_binding.
does_not_carry` ⑦이 이 후속 커밋(`af25a38`)으로 낡았던 것을 curator 가 `6f60c3f`로
정정했다(⑦ 문면을 인용해 배선된 현재 거동을 적고, 결속 소유는 여전히 runner라고
못 박음 — D-12와 같은 갈래). 이 레인이 별도로 인계할 항목은 없다.

## 판단이 갈린 지점 — verifier r1 수정 라운드(`1ddbb69`)

**F-3 배선에서 반올림 모드를 누가 정하는가.** `FloorShortfallPolicyData`는
`criticalRateScale`(자리수)만 갖고 `RoundingMode`는 갖지 않는다 — `OPEN-DIC-10`이
모드의 legacy 값 자체를 아직 정하지 않았기 때문이다(D-10). 그런데 `RoundingPolicy`는
자리수와 모드를 함께 요구한다. 선택지: (a) `criticalAssessmentRateFor`(decision
main)가 `RoundingMode.HALF_UP`을 구조적 관례로 주입한다 (b) `FloorShortfallPolicyData`에
`mode: RoundingMode` 필드를 추가해 정책이 값을 싣게 한다. **(a) 채택** — (b)는
`OPEN-DIC-10`이 아직 안 정한 것을 이 slice가 먼저 정하는 셈이라 범위를 넘는다(D-5의
`trustRatioMax` 슬롯-only 처방과 같은 원칙). (a)는 F-3이 요구하는 "슬롯의 실제
소비"를 만족시키면서 모드값 자체는 지어내지 않는다 — `HALF_UP`은 반올림 방향
자체가 어느 쪽이든 결과가 크게 갈리지 않는 범용 규칙이지 legacy 재현 대상 값이
아니다(1B `resolvedPolicy` test 헬퍼도 같은 값을 기본으로 쓴다). 이 판단은 F-3의
"단순 소비자 신설"이라는 낮은 심각도에 맞는 범위로 유지했다 — `OPEN-DIC-10` 자체를
닫지 않는다.

## acceptance 전건 (`commands.md` 상세)

P-0(격리 worktree `clean check`)·P-1(`--no-build-cache clean check`, **3회** 재검증)·
P-2(`:decision:test`)·P-3(`:shared-kernel:test`)·P-4(도메인 게이트 다섯)·P-5
(`:app:test --tests '*Conformance*'`, authoritative **8** case)·P-6(`qualityBaseline`)·
P-7(스윕 — `mutation_sweep_adversarial.py`·`mutation_sweep_targeted.py`·
`manifest_contract.py`·`check_legacy_numbers.py`)·P-8(`:build-logic:test`) 전부 exit 0.
verifier r1 수정 라운드 뒤 P-0·P-1·P-2·P-3·P-4·P-5·P-6·P-7·P-8 전건을 이 순서로
foreground 재실행했다(commands.md 상세).

## 완료 판정 — 1D 축

`milestone-1.md` 「완료 조건」 대조: **authoritative 8 case 전건이 `check` 안에서 실행·
대조된다**(`gate.tests.app`·`gate.tests.decision` 등재, `gateExecutionGate` 강제).
`Unmeasurable`이 값·0·`Measured`로 접히는 공개 경로가 없다(내부 생성자 + 위협 모델 (a)
변이 실측). rule order 는 정책 데이터로만 온다(위협 모델 (c) 변이 실측). 판정: **1D 축은
승인된 authoritative corpus 전체 통과를 충족한다.** 알려진 제한 1~8은 이 판정 밖의
별도 축이고 각자의 이관처가 닫는다.
