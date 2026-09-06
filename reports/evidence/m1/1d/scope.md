# Slice 계약 — M1 / 1D · Provenance 와 Floor Shortfall(기초금액 provenance first-match rule · 하한 미달 빈도)

```yaml
milestone: m1
slice: 1d-provenance-floor-shortfall
base_sha: 14495d035a55baaa7ee6618e2857d9096804c686
head_sha: 리뷰 시점의 HEAD
in_scope:
  - decision/**                                # 도메인 모듈(ADR 0006 D-2 — 「법정 하한, 추천 후보 평가, reason code」; DEC-04·DEC-08 소유). 두 커널·타입·정책 데이터 형태·test
  - shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt        # D-1 조건부 — 1B 가 닫은 생성자 옆에 **이름 있는 factory 둘**(추천 투찰율 입력 · 관측 사정률 재구성)만. 운영자 결정 D-1 (a) 없이는 열지 않는다
  - shared-kernel/src/main/kotlin/bidvector/sharedkernel/RateArithmetic.kt   # D-1 조건부 — 임계 사정률 파생(`BidRate ÷ FloorRate → AssessmentRate`) 한 함수. 신규 파일
  - shared-kernel/src/test/**                  # D-1 조건부 — 위 둘의 test 만
  - config/quality/api-type-policy.properties  # 조건부 — 도메인 API 타입 허용 목록에 1D 값 타입 등재가 필요할 때만(게이트 정의 편집이므로 사유를 evidence 에)
  - config/quality/gate-tests.properties       # 조건부 — 1D 게이트 성격 test(불변식) 등재 시 gate.tests.decision 키
  - app/src/test/kotlin/bidvector/app/conformance/**   # corpus runner dispatch 를 base-amount-provenance·floor-shortfall·floor-threshold authoritative 로 확장(1B-c·1C 관례)
  - app/build.gradle.kts                       # 조건부 — testImplementation(project(":decision")) 한 줄
  - fixtures/manifest.yaml                     # 조건부 — 1D 축 case 의 contract_binding 신설·D-3 승인 시 floor-threshold-002 승격(fixture-curator 소관, 기대값 무변경 원칙)
  - docs/discovery/data-dictionary.md          # 계약 갱신 — §3.3·§3.4·§9 의 운영자 결정이 실제로 난 행만
  - docs/discovery/capability-map.md           # 계약 갱신 — §14.2 OPEN-DIC-05·OPEN-DEC-07 행만
  - milestone-1.md                             # 계약 갱신 — 「Slice 1D」 항목만
  - reports/evidence/m1/1d/**
out_of_scope:
  - shared-kernel 의 그 밖 편집                # D-1 이 허용한 factory 둘 + 파생 함수 하나 밖. 다른 carrier 가 없으면 멈추고 보고
  - provenance 판정 **술어의 임계값 확정**      # `BID_BASE_TRUST_RATIO_MAX`(1.15 = 1.1 + 마진 0.05)의 마진은 OPEN-DEC-07(V2 코퍼스 재유도). 1D 는 정책 슬롯·단위·비교 방향만 — D-5
  - 복수예비가격 15개에서 복구 추정치를 **계산**하는 규칙   # DEC-08 「range 중점·15개 전부 유효」— 입력 형태(`recoveryCandidate`)만 받아 원본과 분리 보존(D-6). 계산은 예가 수집(M3)과 함께
  - 표본 집합의 **수집·선별**(어느 공고·기간)   # M3 3B·3D. 1D 커널 입력은 실현 사정률 표본 목록 또는 집계(tally) — D-4
  - 미판정(NULL) **저장 상태**                  # 판정 레코드의 부재는 persistence(M3 3D)의 상태. 커널 출력 값이 아니다 — D-2
  - 빈도의 십진 렌더링·반올림 자리수            # OPEN-DIC-10. 빈도는 유리수(분자·분모)로만 나른다 — D-7
  - 법정 하한율 표 값(§4.4 OPEN-DEC-10)·하한 적용 범위(§3.5 FloorApplicability)·Verdict(§3.6)   # 별 축·별 slice
  - fixture 기대값·입력 파일 편집 · 새 case 신설   # decision 19 관례. 어긋나면 멈추고 보고
  - Python ML · bid-vector/ symlink(읽기 전용) · _workspace/**
  - 승인 문서 편집 일체(위 승격 행·항목 밖)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # P-0
  - "./gradlew --no-build-cache clean check"                                                          # P-1 — 1A·1A-b 게이트 전건 + CPD main fail(2026-09-06) 위에서 초록
  - "./gradlew :decision:test"                                                                          # P-2 — 1D 도메인 test(property 포함)
  - "./gradlew :shared-kernel:test"                                                                     # P-3 — D-1 조건부(shared-kernel 편집 시) — 1B 회귀 전건 + 새 factory test
  - "./gradlew :decision:domainApiTypeGate :decision:domainSourceReferenceGate :decision:typeShapeGate :decision:sizeGate :decision:cpdCheck"   # P-4 — 게이트 단독
  - "./gradlew :app:test --tests '*Conformance*'"                                                      # P-5 — 1D 축 authoritative(bap 001·002·003 · fs 001·005 · ft 001·003, D-3 승인 시 ft 002) 가 runner 로 실행·대조
  - "./gradlew qualityBaseline"                                                                        # P-6
  - "python3 fixtures/tools/mutation_sweep_adversarial.py"                                             # P-7 — 조건부(manifest 편집 시)
  - "./gradlew :build-logic:test"                                                                     # P-8 — 1A 승계
rollback: |
    **정본은 `reports/evidence/m1/1d/rollback.md`**(scope.md 파생, 경로 한정 restore + 신규 경로 rm, 임시 clone 실측).
    decision 모듈은 자리표시자(anchor)로 돌아간다. shared-kernel 은 D-1 조건부 편집분만 되돌린다.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 착수 근거: 운영자 지시 2026-09-06(「1D 착수.」, 1C 종결
승인 뒤), `milestone-1.md` 「Slice 1D」, 1B(값 타입·`BaseAmountProvenance` 라벨)·1B-c(corpus 실행자)·
1A-b(게이트)·1C(정책 배관 관례) 종결.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 14495d035a55baaa7ee6618e2857d9096804c686..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**.

---

## 이 slice 가 하는 일

`milestone-1.md` 「Slice 1D」 다섯 문장을 **커널 둘**로 낸다(`v2-지침서.md` §4.3·§4.4 · `data-dictionary.md`
§3.3·§3.4 · `capability-map.md` DEC-04·DEC-08).

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **기초금액 provenance first-match rule** — 정책 데이터가 선언한 순서(`ruleOrder`)로 규칙을 돌려 **첫 매치** 라벨(`BaseAmountProvenance` 다섯, 1B 타입 재사용)을 내고, 어느 규칙이 매치했는지(`firstMatchedRule`)와 `policyVersion` 을 결과에 싣는다. 매치 없음 = `Unknown`(D-2) | §3.4 「first-match 순서가 load-bearing」· §4.3 |
| ② | **rule order 의 명시적 테스트** — 같은 입력·다른 순서(정책 version B)가 다른 라벨을 내는 것을 example 로 고정(`base-amount-provenance-002`·`003` 쌍). 순서는 코드 상수가 아니라 정책 데이터 | `milestone-1.md` 「rule order의 명시적 테스트」 |
| ③ | **원본 무교정** — 판정 결과는 원본 금액·판정·근거·정책 version 을 함께 나르고, 복구 추정치는 **별도 필드**(`recoveryEstimate`)에만 있다. 원본 필드가 바뀌지 않음을 타입(불변 `Money`)과 test 로 | §3.4 「원본 값을 조용히 교정하지 않는다」· DEC-08 acceptance |
| ④ | **임계 사정률과 표본 하나의 미달 판정** — `임계 사정률 = 추천 투찰율 ÷ 낙찰하한율`(basis 관계를 타입으로), `미달 ⟺ 실현 사정률 > 임계`(경계 등가는 정책값, D-3) | §3.3 「핵심 관계」· `v2-지침서.md` §4.4 · DEC-04 |
| ⑤ | **과거 빈도 계산** — 표본 집합에서 밴드(§1.4.3 B3 계열, 정책 데이터) 밖을 분모에서 빼고 미달 표본을 세어 `frequency = numerator ÷ denominator`(유리수) · `criticalAssessmentRate` · `band` · `biasDirection` · `policyVersion` 묶음으로 낸다 | DEC-04 acceptance 「묶음으로 반환」· §3.3 표 |
| ⑥ | **최소 표본 미달의 `Unmeasurable`** — 분모 < `minAssessmentSamples`(정책 데이터, 근거 「통계적 편의(운영자 승인 2026-08-26)」) 이면 값이 아니라 `Unmeasurable(SampleInsufficient(required, actual))`. 밴드 필터로 분모가 줄어 문턱 아래로 가면 **전이**(`floor-shortfall-005`) | §3.3 정직 명세 2·3 · DEC-04 |
| ⑦ | **확률 어휘 금지의 output contract** — 필드·타입 이름 어디에도 probability 를 쓰지 않고, `Unmeasurable` 이 `0`·`0%`·`Measured` 로 접히는 공개 경로가 없다(sealed + 소진 `when`) | §3.3 정직 명세 1 · M1 완료 조건 「`Unmeasurable`가 0으로 합쳐지지 않음」 |
| ⑧ | **사유 어휘 분리** — `FloorUnmeasurableReason = sealed { FloorRateUnresolved, FloorModelNotApplicable, BidRateUnavailable, SampleInsufficient(required, actual) }`. 인프라 실패는 이 어휘에 없다(예외로) | §3.3 「사유 어휘」 |
| ⑨ | **정책 데이터 형태** — `ProvenancePolicyData(ruleOrder, vatMultiplier, tolerance, trustRatioMax 슬롯)` · `FloorShortfallPolicyData(minAssessmentSamples + rationale, denominatorBand, shortfallComparison, biasIndeterminateBand)` 를 `EffectiveDatedPolicy` 로 실어 `Resolution.Resolved` 하나로 커널에 넣는다(1C F-5 관례). 값이 미정인 슬롯은 기본값 없이 부재를 드러낸다 | §4.3.2 외부화 표 · `v2-지침서.md` §4.4 「versioned 정책」 |
| ⑩ | **corpus 실행** — runner dispatch 를 1D 축 authoritative 로 넓혀 `check` 안에서 대조. 입력 형태가 다른 세 층(규칙 매치 목록 → orderer / 집계 → measure / 표본 하나 → 미달 술어)을 **입력이 가진 층**에 맞춰 부른다 — 입력에 없는 값을 runner 가 만들지 않는다(D-4) | 1B-c 관례 · M1 완료 조건 「authoritative corpus 전체 통과」 |

---

## 운영자 결정 필요 — 착수 전(D-1~D-3) · 계약 고정(D-4~D-8, 세션 모델 판단·사후 확인)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-1** | **1B 가 닫은 생성자와 1D 입력.** `AssessmentRate`·`BidRate` 는 `internal constructor` 이고 유일한 생성 경로가 금액 파생(`assessmentRateAgainst`·`bidRateAgainst`)이다(1B verifier r1 H-1). 1D 는 (i) **추천 투찰율**(`BidRateOrigin.Recommended`)을 입력으로 받아야 하는데 1B 자신이 「모듈 밖에서 구성할 공개 경로가 지금 없다 … M2/M5 가 열어야 한다」고 알려진 한계로 적었고, (ii) **임계 사정률**(`BidRate ÷ FloorRate`)은 금액이 아니라 율의 몫이라 기존 파생 경로가 없고, (iii) **표본의 실현 사정률**은 저장·전송에서 돌아오는 관측값이라 예가·기초금액 쌍 없이 재구성해야 한다 | (a) **1D in_scope 에 shared-kernel 좁은 확장 셋** — `BidRate.recommended(rate)`(1B 예고), `criticalAssessmentRate(bid, floor): AssessmentRate`(파생 함수, 새 파일 `RateArithmetic.kt`), `AssessmentRate.observed(rate)`(관측값 재구성 factory — 이름이 「파생이 아니라 재구성」임을 진술). 셋 다 test 동반, 생성자는 그대로 `internal` (b) 1D 커널이 `Rate` 원시값을 받고 축 타입을 쓰지 않는다 (c) runner 가 fixture 의 fraction 에서 예가·기초금액 쌍을 지어 `assessmentRateAgainst` 로 파생 (d) 1D 착수 보류, M2 가 factory 를 열 때까지 | **(a)** — (b) 는 §1.4.2 「축은 타입이 나른다」를 1D 에서만 버리는 것이고, (c) 는 runner 지어내기 금지 위반, (d) 는 M1 완료 조건을 M2 에 넘긴다. (a) 의 셋은 1B 가 닫은 취지(임의 값이 파생값처럼 조립되는 것)를 깨지 않는다 — 이름이 출처(추천 입력·재구성)를 진술하고 `Recommended`/관측 축은 어차피 외부에서 온다. 확장은 Rate.kt 안 factory 둘 + 새 파일 하나로 한정하고 1B 회귀 전건(P-3) 위에서 | **착수 전 결정** |
| **D-2** | **`OPEN-DIC-05` — 「아직 판정하지 않음」이 「신뢰함」으로 접히지 않게 하는 자리.** ① legacy 여섯째 라벨 `suspect-fractional` ② 미판정 `NULL` 과 `Unknown` | ① **(a)** `suspect-fractional`(비정수 금액)은 라벨이 아니라 **1B `Money` 경계 거부**(`AmountNotRepresentable`, `base-amount-provenance-004`·`005` 가 이미 그 자리)로 흡수 — 원화 금액은 정수라 커널에 도달하지 않는다 / (b) 라벨 추가(승인 명세 집합 변경) ② **(a)** `Unknown` = 「규칙 무매치」(커널 출력), 「미판정」 = **판정 레코드의 부재**(persistence 상태, 커널 값 아님). 소비자가 판정 없는 행을 `Clean` 으로 읽는 legacy 경로는 **판정 레코드를 요구하는 타입**(`ProvenanceJudgement` 없이는 `Clean` 을 얻을 수 없다)으로 막는다 / (b) `Unjudged` variant 추가(집합 변경) | **①(a)·②(a)** — 둘 다 승인 명세의 다섯 값을 바꾸지 않고 요구(「미판정 ≠ 신뢰」)를 타입 경계로 만족시킨다. §3.4 가 「어느 variant 가 나르는가는 이 문서가 정하지 않는다」고 미룬 자리를 **variant 가 아니라 레코드의 존재**로 답한다. OPEN-DIC-05 해소 → decision 27 | **착수 전 결정** |
| **D-3** | **경계 등가의 미달 판정 — 정책 초기값.** `v2-지침서.md` §4.4 는 「경계의 포함/제외는 versioned 정책」이라 값을 정하지 않았고, DEC-04 acceptance 는 「정확히 경계값인 표본은 미달로 세지 않는다」(M0 자체 도출)를 적었다. 그래서 `floor-threshold-002`(등가 → 미달 아님)가 insufficient-evidence 로 내려가 있다(운영자 2026-09-02) | (a) **정책 `shortfallComparison` 초기값 = `strictly-greater`**(등가 = 미달 아님)를 운영자 결정으로 승인, `floor-threshold-002` 를 curator 가 authoritative 로 되돌림(입력에 policy version 을 싣는 재추출) (b) 초기값 미정 — 정책 슬롯만 두고 test 는 양쪽 값을 다 돌림, 002 는 그대로 (c) `greater-or-equal` | **(a)** — 임계 사정률의 정의(`미달 ⟺ 실현 > 임계`, §3.3 「핵심 관계」·DEC-04 분류 근거)가 이미 엄격 부등호로 적혀 있어 (a) 는 문면과 일치한다. 정책값이므로 뒤에 바꿀 수 있다. (b) 도 안전하나 authoritative 가 하나 덜 돌아온다 | **착수 전 결정** |
| **D-4** | **커널 API 의 세 층** | provenance: `ProvenanceRules.firstMatch(hits: Set<RuleId>, policy)`(orderer) + 규칙 술어 `ProvenanceRule.evaluate(row, policy): Boolean` 은 별 함수 · floor: `isShortfall(realized: AssessmentRate, critical: AssessmentRate, comparison)`(표본 하나) → `tally(samples, critical, band, comparison): ShortfallTally(rawCount, outsideBand, qualifiedDenominator, shortfallNumerator)` → `measure(tally, critical, policy): FloorShortfall`. **corpus 입력이 가진 층에서 부른다** — bap 001~003 은 `ruleHits` 를 주므로 orderer 층, fs 001·005 는 집계를 주므로 `measure` 층, ft 001·003 은 표본 하나라 술어 층. runner 가 표본 149개를 지어내지 않는다 | 계약 고정 |
| **D-5** | **술어 임계값** | `vatMultiplier`(§1.2 부가세율, 1B `PolicyTable` 재사용)·`tolerance`(§12 정책값 — Phase 2 가 값·단위를 낸다)는 정책 데이터로 싣고, `trustRatioMax` 는 **슬롯만**(OPEN-DEC-07 마진 미유도 — 기본값 없음, 부재면 그 규칙은 `NotEvaluable` 로 first-match 에서 건너뛰지 않고 **판정 자체를 `Unknown` 이 아니라 정책 부재 실패**로). 값은 승계하지 않는다(§4.3.1) | 계약 고정 |
| **D-6** | **복구 추정치** | 입력 `recoveryCandidate: Fact<Money>` 를 받아 `ProvenanceJudgement.recoveryEstimate` 에 그대로 보존(계산 안 함 — 15개 예가의 range 중점 규칙은 예가 수집과 함께 M3). 원본 `Money` 는 불변이고 결과가 원본 참조를 그대로 나르므로 「원본 필드 무기록」은 구조로 성립 | 계약 고정 |
| **D-7** | **빈도 표현** | `Frequency(numerator: Int, denominator: Int)` 유리수만. 십진 값은 커널이 내지 않는다(OPEN-DIC-10) — fixture 002·003 의 `exactDecimal` 은 `not_covered` 라 잠기지 않는다. `renderedAsZeroPercent`·`calledProbability` 는 runner projection 이 `Unmeasurable`/타입 이름에서 낸다 | 계약 고정 |
| **D-8** | **`biasDirection`** | `BiasDirection = sealed { Overestimates, Underestimates, Indeterminate }` — 임계 사정률이 `biasIndeterminateBand`(§1.4.3 B3 계열, 정책 데이터) 위/안/아래로 갈린다(DEC-04 「알려진 편향」 문면). `Measured` 의 필수 필드(OPEN-DEC-06 (a)). 밴드 값은 정책 데이터, 운영자 승인 문면이 없으면 Phase 2 가 legacy 값·층을 등재하고 값은 `legacy-behavior` 표시로 | 계약 고정 |

---

## 위협 모델 — 1D 고유 경계 (Phase 2.5, 세션 모델 직접)

**방어한다** — 두 커널의 **의미 회귀**: (a) `Unmeasurable` 이 `0`·`0%`·`Measured` 로 접히는 공개 경로(sealed + 내부 생성자 + 소진 `when` 소비 test) (b) 확률 어휘가 공개 타입·필드 이름에 들어옴(이름 test — `Frequency`·`FloorShortfall` 공개 표면에 `probab` 문자열 0) (c) first-match 순서가 코드 상수가 됨(정책 데이터 부재 시 컴파일/구성 실패 · 순서 다른 두 version 이 다른 라벨 example) (d) 복구 추정치가 원본 자리에 기록됨(원본 `Money` 참조 동일성 property) (e) 최소 표본 수·경계 비교·밴드가 리터럴로 들어옴(`domainSourceReferenceGate` 는 못 잡는다 — 1C F-3 과 같은 한계, **코드 리뷰**와 정책 파일 키 부재 → 구성 실패로 대신) (f) 밴드 밖 표본이 분모에 남음(fs-005 전이 example + property `qualifiedDenominator = raw − outsideBand`) (g) 규칙 무매치가 `Clean` 으로 접힘(`Unknown` example + 판정 레코드 없이 `Clean` 을 얻을 공개 경로 없음). **방어하지 않는다** — 술어 임계값의 옳음(OPEN-DEC-07·§4.3.1 미분류), 표본 집합의 수집·선별(M3), 미판정 저장 상태(M3 3D), 십진 렌더링(OPEN-DIC-10), 게이트 정의 편집(하네스 저자), 리플렉션.

**경계가 요구 축소가 아닌 이유**: §3.3·§3.4 는 판정의 **형태·순서·정직 명세**를 정하고 임계값(§4.3.1 「자리와 구성만」)·수집·표현은 다른 자리에 둔다.

**우회 후보 (Phase 2.5 ≥5)**: (1) `FloorShortfall` 을 `Double?` 로 접는 확장 함수 — 막지 않는다(1C 와 같은 한계, 소진 `when` 소비 test 만) (2) `Measured(frequency=0/0)` 조립 — `Frequency` 생성자가 `denominator ≥ minSamples` 를 요구하지 못하므로 `Measured` 생성자를 `internal` 로 닫고 `measure` 만 생성 경로 (3) `tally` 를 건너뛰고 `measure` 에 임의 집계 주입 — 허용(입력 층 D-4), 다만 `qualifiedDenominator ≤ rawCount` 등 불변식은 `ShortfallTally.init` 이 거부 (4) `ruleOrder` 에 중복·미지 규칙 id — 정책 로드 시 거부 (5) `Unknown` 을 first-match 순서에 넣어 「매치」시키기 — 규칙 id 집합에 `Unknown` 이 없다(타입) (6) 정책 version 을 결과에 임의 주입 — 1C N-3 과 같은 한계(호출 규약), 등재.

---

## 조사(Phase 2) 결과 — `_workspace/m1-1d/` (착수 시점 진행 중)

`01_scout_preflight.md`(legacy-scout) 대기. 반영 시 이 절이 D-5·D-8 의 값·층과 어휘 불일치 목록을 받는다.

---

## OPEN — 수령·신설

| OPEN | 1D 처리 |
| --- | --- |
| `OPEN-DIC-05` | D-2 로 해소 제안(라벨 집합 불변, 레코드 존재로 답) |
| `OPEN-DEC-07` | 슬롯만(D-5). 값 재유도는 V2 코퍼스 뒤 — 담당 불변 |
| `OPEN-DEC-06`(해소) | `biasDirection` 필수 필드로 구현(D-8) |
| `OPEN-DEC-01`(해소) | `minAssessmentSamples` 정책 데이터 + 근거 문자열 |
| `OPEN-DIC-10` | 십진 렌더링 미구현(D-7) — 불변 |
| `OPEN-DEC-03` | 1D 축 아님(추천가 clamp, M4 4B 갈림) — 건드리지 않음 |
