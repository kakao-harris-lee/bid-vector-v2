# checklist — M1 / 1D (Provenance first-match · Floor Shortfall)

레인(kotlin-implementer)이 슬라이스 마감에 쓴다. 출력 전문·라운드 이력 절 없음
(`evidence-pack` 규격). 종결 = **verifier ready-for-review + 사용자 승인**(Codex 없음,
CLAUDE.md 운영자 지시 2026-09-04).

## 구현 순서(커밋 4개, TDD RED→GREEN)

1. `95585b5` — shared-kernel 좁은 확장(D-1(a)+D-9): `Rate.fraction` 공개 읽기 +
   `Comparable<Rate>`, `BidRate.recommended`·`AssessmentRate.observed` factory,
   `criticalAssessmentRate` 나눗셈. `RateArithmeticTest` 신규.
2. `a865bc6` — `decision` 모듈 배선(`qualification` 관례 복제) + provenance/floor
   커널 둘. `ProvenanceRulesTest`·`FloorShortfallKernelTest` 신규.
3. `ccc1f1b` — corpus runner 확장(`ProvenanceFloorExecutors.kt` 신규, `TARGET_DOMAINS`
   +3축). authoritative 7 case 전건 대조.
4. `bc15983` — `gate-tests.properties` 에 `gate.tests.decision` 등재.

## D-1(a)/D-9 셋+하나 이행 대조

scope.md 운영자 결정: "in_scope 의 shared-kernel 세 경로가 열린다"(`BidRate.recommended`·
`AssessmentRate.observed`·`criticalAssessmentRate`) + D-9 사후 확인 대상(넷째 —
`Rate.fraction` 공개 읽기·`Comparable<Rate>`).

| 항목 | 구현 | 위치 |
| --- | --- | --- |
| `BidRate.recommended(rate)` | `companion object` factory, origin=`Recommended` | `Rate.kt` |
| `AssessmentRate.observed(rate)` | `companion object` factory, KDoc이 "파생 아님"을 진술 | `Rate.kt` |
| `criticalAssessmentRate(bid, floor, scale)` | `Measurement<AssessmentRate>` 반환, VAT 전건 없음(Rate 는 그 축을 안 나름) | `RateArithmetic.kt`(신규 파일) |
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
| (경계 등가) DEC-04 무조건 acceptance | `ShortfallComparison.StrictlyGreater`(decision 28) — 정확히 경계값인 표본은 미달 아님 | 변이 4(단위 test 단독 방어 — 알려진 제한 ② 참고) |

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
2. **경계 등가 방어(변이 4)는 corpus 가 아니라 단위 test 단독 책임이다** — authoritative
   `floor-threshold-001`·`003` 의 realized 값(1.0499·1.0501)이 critical(1.05)과 정확히
   같지 않아 그 fixture 쌍은 경계 등가 자체를 겨누지 않는다. `FloorShortfallKernelTest.④`
   가 유일한 방어선 — fixture 승격(정확히 critical 과 같은 realized 표본)은 fixture-curator
   소관, 이 slice `out_of_scope`.
3. **`floor-threshold-002`(경계 등가 case)는 여전히 insufficient-evidence** — D-3(decision
   28)이 `shortfallComparison` 초기값을 정책으로 승인했지만, `002` 를 authoritative 로
   되돌리는 재추출(fixture 입력에 policy version 을 싣는 작업)은 fixture-curator 소관이라
   이 slice 가 수행하지 않았다. 8번째 authoritative case 후보로 남는다.
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

## acceptance 전건 (`commands.md` 상세)

P-0(격리 worktree `clean check`)·P-1(`--no-build-cache clean check`)·P-2
(`:decision:test`)·P-3(`:shared-kernel:test`)·P-4(도메인 게이트 다섯)·P-5
(`:app:test --tests '*Conformance*'`)·P-6(`qualityBaseline`)·P-8(`:build-logic:test`)
전부 exit 0. P-7(스윕)은 `fixtures/manifest.yaml` 무편집이라 생략(scope.md 조건).

## 완료 판정 — 1D 축

`milestone-1.md` 「완료 조건」 대조: **authoritative 7 case 전건이 `check` 안에서 실행·
대조된다**(`gate.tests.app`·`gate.tests.decision` 등재, `gateExecutionGate` 강제).
`Unmeasurable`이 값·0·`Measured`로 접히는 공개 경로가 없다(내부 생성자 + 위협 모델 (a)
변이 실측). rule order 는 정책 데이터로만 온다(위협 모델 (c) 변이 실측). 판정: **1D 축은
승인된 authoritative corpus 전체 통과를 충족한다.** 알려진 제한 1~8은 이 판정 밖의
별도 축이고 각자의 이관처가 닫는다.
