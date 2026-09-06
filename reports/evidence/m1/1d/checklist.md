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
7. `b1d1c17`(verifier r2 N-1+N-3+N-5 일괄) — 아래 「verifier r2 표적 재검증 처리」 참고.

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

## verifier r2 표적 재검증 처리 (verdict: ready-for-review — 재작업 아님, 일괄 커밋)

`_workspace/m1-1d/03_verifier_report_r2.md`가 r1 finding 여섯 중 다섯을 닫힘으로,
F-5를 절반(파생 문서만 정정, 정본 `rollback.md` 미정정)으로 판정했다. 신규 medium
1·low 4는 산출물 blocker/high가 아니라 차단 없이 잔여 non-blocker 일괄 처리(Phase 4
규정)로 처리한다.

| id | sev | 처리 |
| --- | --- | --- |
| N-1 | medium | 반올림 모드가 main 리터럴이었다(`criticalAssessmentRateFor`의 `RoundingMode.HALF_UP` + `ProvenanceRules`의 술어 둘 안 `setScale(0, RoundingMode.HALF_UP)`). verifier 판정 (b) 채택 — `FloorShortfallPolicyData.criticalRateScale: Int`를 `criticalRateRounding: RoundingPolicy`(1B 타입 재사용, 자리수+모드)로, `ProvenancePolicyData`에 `integerRoundingMode: RoundingMode` 슬롯을 신설해 `isCleanInteger`·`isDerivedVat`가 그 값을 받는다. main에 `RoundingMode` 값 리터럴 0건(타입 참조는 남는다). 모드를 바꾸면 빨개지는 test 셋 추가 — floor 쪽(2÷3의 비종결 몫, `HALF_UP`→0.7·`DOWN`→0.6) + provenance 쪽 술어 직접 호출 + `evaluateHits`를 통한 dispatcher 배선 확인. 넷 다 실제로 모드를 리터럴로 되돌려 RED를 확인한 뒤 원복했다(dispatcher 배선 쪽은 최초 시도에서 생존 — 아래 참고, `b1d1c17`). |
| N-3 | low | `criticalAssessmentRateFor`가 만드는 `Derived.derivedFrom.policyVersion`이 `policy.version`과 같음을 잠그는 test 추가(`b1d1c17`). |
| N-4 | low(등재만) | 알려진 제한 ⑨ 참고. |
| N-5 | low | `mutation_sweep_adversarial.py`의 `ASSERTED`에 bap 001·002·003 세 행(`$.classification`·`$.firstMatchedRule`/`$.evidence.firstMatchedRule`·`$.policyVersion`) 추가(`b1d1c17`). 캐치 47→65(+18), exit 0 유지. |
| F-5(잔여) | low(장부) | **정본** `rollback.md`의 「D 12개」를 「D 14개」로 정정(r1에서 파생 문서 `commands.md`만 고치고 정본을 놓쳤던 것을 여기서 마저 닫는다). |
| N-2 | low(장부) | 팀장(세션 모델)이 `scope.md` D-13에서 처리 — 이 레인은 건드리지 않았다. |

**N-1 실측 중 발견 — dispatcher 배선 최초 시도 생존**: `matches()`가 `policy.integerRoundingMode`
대신 `RoundingMode.CEILING`을 하드코딩하도록 바꿔도 기존 test(직접 술어 호출만 있었다)가
전부 초록이었다 — `evaluateHits`/`judgeRow`를 통한 dispatcher 배선 자체를 잠그는 test가
없었다. `evaluateHits — 정책의 integerRoundingMode 가 clean-integer 매치 여부를 실제로
바꾼다` test를 추가해 같은 변이를 다시 넣어 실제로 실패함을 확인한 뒤 원복했다 — F-1의
「직접 호출 test만으로는 배선을 못 잠근다」교훈을 N-1 자체 검증에도 적용한 사례다.

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
| (e) 최소 표본·경계 비교·밴드·**반올림 자리수/모드**(N-1 확장)가 리터럴 | `domainSourceReferenceGate` 는 못 잡는다(1C F-3 과 같은 한계) — 정책 파일 키 부재 → 구성 실패로 대신, main 소스 리터럴 스캔은 코드 리뷰. 반올림은 `criticalRateRounding`/`integerRoundingMode` 정책 슬롯으로 옮겨 값 리터럴 0건(verifier r2 N-1) | 육안 확인(아래 「main 리터럴 부재 확인」) + 모드 변이 test 넷(N-1) |
| (f) 밴드 밖 표본이 분모에 잔류 | `ShortfallTally.qualifiedDenominator = rawCount - outsideBand`(계산 프로퍼티, 별도 생성자 인자 아님) | 변이 3 |
| (g) 규칙 무매치가 `Clean` 으로 접힘 | `classificationFor(null) = Unknown`, `Clean` 은 `CleanInteger` 매치 경로로만 | 변이 5 |
| (경계 등가) DEC-04 무조건 acceptance | `ShortfallComparison.StrictlyGreater`(decision 28) — 정확히 경계값인 표본은 미달 아님 | 변이 4 — 최초 단위 test 단독 방어, 후속(`af25a38`, ft-002 배선) 재실측으로 corpus(`floor-threshold-002`)도 함께 캐치 확인(알려진 제한 ② 갱신) |

## main 리터럴 부재 확인

`grep -nE "1\.15|0\.05|1e-6|0\.90|1\.10|0\.01|\b150\b" decision/src/main/kotlin -r` —
매치 없음(정책값은 test 정책 인스턴스와 runner 에만 있다). 유일하게 main 에 있는 정수
리터럴은 `Frequency`/`ShortfallTally` 의 구조적 하한(`0`, `denominator > 0`)뿐이다 —
정책값이 아니라 타입 불변식이다.

**[verifier r2 N-1 뒤 갱신]** `grep -n "RoundingMode\." decision/src/main/kotlin -r` —
매치 1건, `ProvenancePolicyData.kt`의 KDoc 안 인용(*"이전 판은 ... `RoundingMode.HALF_UP`
을..."*)뿐이고 실제 코드 값이 아니다. `criticalAssessmentRateFor`·`isCleanInteger`·
`isDerivedVat` 어디에도 `RoundingMode` 값 리터럴이 없다 — 자리수·모드 둘 다 정책
슬롯(`FloorShortfallPolicyData.criticalRateRounding`·`ProvenancePolicyData.
integerRoundingMode`)에서 온다. `ProvenanceFloorExecutors.kt`의
`LEGACY_CRITICAL_RATE_SCALE_DIGITS = 6`은 app 모듈 test 소스라 이 확인 대상이 아니다
(decision/shared-kernel main 이 아니다).

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
9. **[verifier r2 N-4] `AssessmentRate.observed`로 파생값을 만드는 것을 기계가 막지
   않는다** — `criticalAssessmentRateFor` 안에서 `AssessmentRate(Rate.ofFraction(quotient))`
   (내부 생성자 직접 호출)를 `AssessmentRate.observed(Rate.ofFraction(quotient))`로
   되돌려도 값이 같아 전건 초록이다(파생값과 재구성값이 같은 `Rate`를 감싸면 결과
   `AssessmentRate`가 구조적으로 동일하기 때문 — `Derived` 래퍼가 이미 정책 version을
   따로 나르므로 내부 `AssessmentRate` 자체의 생성 경로 차이는 corpus·test 어디서도
   관측되지 않는다). 방어는 이름·KDoc 경계(`AssessmentRate.observed`의 "파생값의 생성
   경로가 아니다" 문단, verifier r1 F-1 수정)와 코드 리뷰뿐이다 — 등재만 하고 기계적
   가드는 이 slice `out_of_scope`(추가 가드는 shared-kernel 타입 자체를 갈라야 하는
   더 큰 변경이다).

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

**[뒤집힘 — verifier r2 N-1]** 위 판단은 기각됐다. verifier r2는 인용한 선례(D-5
`trustRatioMax` 슬롯-only 처방)가 오히려 반대 방향을 가리킨다고 지적했다 —
`trustRatioMax`는 기본값 없는 슬롯(`Rate?`)이지 main이 값을 지어내는 자리가 아니다.
(b)(정책 데이터에 모드 슬롯을 두는 것)는 `OPEN-DIC-10`을 닫는 것이 아니라 "호출부가
주입한다"는 `RoundingPolicy` KDoc의 배치 그대로다 — 오히려 (a)(main이 리터럴을
박음)가 그 미결정 사항을 먼저 정하는 셈이었다. **verifier r2 판정 (b)를 채택**해
`criticalRateRounding: RoundingPolicy`·`integerRoundingMode: RoundingMode` 슬롯을
신설했다(위 「verifier r2 표적 재검증 처리」 N-1). 이 뒤집힘 자체를 교훈으로 남긴다 —
"인용한 선례가 실제로 같은 방향을 가리키는지"를 판단이 갈린 지점에 적을 때
재확인해야 한다.

## acceptance 전건 (`commands.md` 상세)

P-0(격리 worktree `clean check`)·P-1(`--no-build-cache clean check`, **3회** 재검증)·
P-2(`:decision:test`)·P-3(`:shared-kernel:test`)·P-4(도메인 게이트 다섯)·P-5
(`:app:test --tests '*Conformance*'`, authoritative **8** case)·P-6(`qualityBaseline`)·
P-7(스윕 — `mutation_sweep_adversarial.py`·`mutation_sweep_targeted.py`·
`manifest_contract.py`·`check_legacy_numbers.py`)·P-8(`:build-logic:test`) 전부 exit 0.
verifier r1 수정 라운드 뒤 P-0·P-1·P-2·P-3·P-4·P-5·P-6·P-7·P-8 전건을 이 순서로
foreground 재실행했다(commands.md 상세). **verifier r2 잔여 일괄(N-1·N-3·N-5·F-5
잔여) 뒤 P-1·P-2·P-3·P-5·P-7을 재실행 — 전부 exit 0**(commands.md 상세, 캐치 수
47→65).

## 완료 판정 — 1D 축

`milestone-1.md` 「완료 조건」 대조: **authoritative 8 case 전건이 `check` 안에서 실행·
대조된다**(`gate.tests.app`·`gate.tests.decision` 등재, `gateExecutionGate` 강제).
`Unmeasurable`이 값·0·`Measured`로 접히는 공개 경로가 없다(내부 생성자 + 위협 모델 (a)
변이 실측). rule order 는 정책 데이터로만 온다(위협 모델 (c) 변이 실측). 반올림 자리수·
모드가 main 리터럴 없이 정책 슬롯에서만 온다(verifier r2 N-1). 판정: **1D 축은
승인된 authoritative corpus 전체 통과를 충족한다.** 알려진 제한 1~9는 이 판정 밖의
별도 축이고 각자의 이관처가 닫는다.
