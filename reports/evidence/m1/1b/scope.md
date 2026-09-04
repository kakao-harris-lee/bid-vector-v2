# Slice 계약 — M1 / 1B · Money/Rate/Basis

```yaml
milestone: m1
slice: 1b-money-rate-basis
base_sha: 66c1ab79af4c5a68145811a9e87008dfdb10da3c
head_sha: 리뷰 시점의 HEAD
  # 1B 의 산출물은 in_scope 경로의 변경이다. range 에는 하네스 레인 커밋이 섞일 수 있으며
  # 아래 「하네스 레인 변경」 절이 가른다(evidence-pack SKILL.md 2026-09-04, 1A 관례 승계).
in_scope:
  - shared-kernel/**                  # ADR 0002 D-9 소유 — Money/Rate/Basis/VatTreatment/
                                       # Provenance 값 타입과 RoundingPolicy·PolicyVersion·
                                       # EffectiveDatedPolicy 정책 데이터(D-결정 5, 아래)를 포함한다
  - config/quality/architecture-policy.properties   # 조건부 — D-결정 6. 현재 판정은 「갱신 불필요」
  - config/quality/member-effects.properties        # 조건부 — OPEN-1B-CASEFOLD 미결이라 현재는 보류
  - fixtures/manifest.yaml            # 조건부 — 경로만. money-basis·rate-unit case 를
                                       # authoritative 로 되돌리는 실제 편집은 Phase 3(계약
                                       # 술어를 갖춘 뒤)이며 fixture-curator 소관이다(D-결정 6 하단)
  - docs/discovery/data-dictionary.md   # 운영자 결정 2026-09-04로 넓힘(계약 갱신) — §1.1·§1.2·
                                         # §1.4.2·§4.1·§9·§11의 해당 행/절만. 다른 절은 out
  - docs/adr/0002-money-rate-basis.md   # 같은 갱신 — §2 D-10 신설·D-9 정정·§6 ① 정정만
  - docs/adr/0007-test-pyramid-and-ratchet.md   # 같은 갱신 — §5 OPEN-ADR-06·OPEN-ADR-16
                                                 # 결정 시점 등재만
  - docs/discovery/capability-map.md    # 같은 갱신 — §14.2의 해당 행(OPEN-ADR-06·OPEN-DIC-08·
                                         # 신설 OPEN-1B-CONTRACT)과 그 안의 낡은 file:line
                                         # 정정만. 다른 절은 out
  - config/quality/gate-tests.properties   # 운영자 결정 2026-09-04로 승격(계약 갱신) —
                                            # gate.tests.shared-kernel 키 신설에 한정. app 키는 out
  - reports/evidence/m1/1b/**
out_of_scope:
  - procurement · qualification · strategy · decision · settlement · workflow · adapters · app  # 1C~1E, M2~
  - 1C(자격 판정) · 1D(first-match provenance rule 자체 · floor shortfall) · 1E(strategy·state) 소유 규칙
    # 1B 가 만드는 것은 carrier(Absent/Unmeasurable/Measured, BaseAmountProvenance 라벨 타입)이지
    # rule 자체가 아니다 — 조사 C §1.5·§1.7 이 그 경계를 긋는다
  - corpus 재추출 · fixture 신설                      # fixture-curator 소관, OPEN-1B-CONTRACT 미결
  - Python ML · 기존 Python 과의 byte-for-byte 동등성
  - mutation testing 적용                            # OPEN-ADR-07 — 카탈로그 좌표 등재만(1A 관례 승계)
  - bid-vector/ symlink 아래 기존 저장소                # 읽기 전용
  - _workspace/**                                    # .gitignore 대상
  - 승인 문서 편집 일체                                 # 기본 out. 운영자 결정이 실제로 난 항목만
                                                       # 「계약 갱신」 절로 개별 승격한다(1A 관례).
                                                       # 위 in_scope 넷(data-dictionary·ADR 0002·
                                                       # ADR 0007·capability-map)이 그 승격이고,
                                                       # **그 문서 안에서도 승격된 절 밖은 여전히 out**이다
acceptance_commands:
  # B-0~B-3 은 1A acceptance A-0·A-1·A-2·A-5 의 승계다(scope.md 문면 그대로, 재정의하지 않는다).
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # B-0 (= 1A A-0)
  - "./gradlew --no-build-cache clean check"                    # B-1 (= 1A A-1)
  - "./gradlew :app:test --tests '*ArchitectureGate*'"          # B-2 (= 1A A-2, 위반 fixture 음성 포함)
  - "./gradlew :build-logic:test"                               # B-3 (= 1A A-5)
  - "./gradlew :shared-kernel:test"                              # B-4 — 1B 신규 도메인 test(property 포함)
  - "./gradlew qualityBaseline"                                  # B-5 — OPEN-ADR-06 입력 실측, 1B 가 첫 도메인 코드
  - "./gradlew :shared-kernel:domainApiTypeGate"                 # B-6 — 1B 가 처음 실효시키는 게이트(단독 실행)
  - "./gradlew :shared-kernel:domainSourceReferenceGate"         # B-7 — 같은 이유로 단독 실행
rollback: |
    **정본은 `reports/evidence/m1/1b/rollback.md`**(`agent-workflow.md` §6 이 요구하는 파일).
    되풀이하지 않는다 — 같은 사실을 두 자리에 적으면 한쪽이 낡는다.
```

작성: 2026-09-04, kotlin-implementer (v2-slice-pipeline Phase 1).

---

## 하네스 레인 변경 (상시 절, 1A 관례 승계)

`git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/` —
**없음**(재확인: fixture-curator 레인 커밋 `93be34a` 합류 뒤에도 이 range 에 하네스 경로
변경이 없다). 목록이 생기면 SHA·경로·목적 한 줄씩 등재하고 「slice 산출물이 아니며
in_scope 밖, 운영자 승인 하에 같은 range 에 있다」를 명시한다.

---

## 이 slice 가 하는 일

`milestone-1.md` 「Slice 1B」가 요구하는 넷을 만든다 — ① 원 단위 금액과 계산용 decimal 의
명시적 분리 ② `BaseAmount`·`EstimatedAmount`·`YegaAmount`·`BidAmount` + 운영자 결정
2026-09-04(A3)로 확정된 `AllocatedBudget`·`AwardAmount`(여섯 타입, 아래 「계약 갱신」)
③ `Rate(fraction 고정)`·`Basis`·`VatTreatment`·`Provenance` ④ versioned `RoundingPolicy`.
조사 C §1이 승인 문면에서 뽑은 계약표가 타입 계약의 정본이고, 이 문서는 그 표를 되풀이하지
않는다.

**1B 가 만드는 것과 만들지 않는 것의 경계**(조사 C §1.7·§2): `Uncertain` variant 자체는
1C·1D 소유이나, 그것이 딛는 carrier(`Absent(reason)`·`Unmeasurable(reason)`·`Measured<T>`)는
1B 산출물이다. `BaseAmountProvenance` 라벨 타입(승인 명세 다섯 값)은 1B 가 선언하되
first-match rule 은 1D 소유다.

---

## D-결정 — 조사 B 가 확정한 것

| # | 결정 | 근거 |
| --- | --- | --- |
| **D-1** | **Money 라이브러리 불채택.** `Long`(확정 금액) + `java.math.BigDecimal`(파생 금액·`Rate`) 위에 자체 값 객체를 둔다 — JavaMoney·Joda-Money 둘 다 ADR 0002 D-1(다섯 성분)·D-3(basis 별 타입)을 표현하지 못하고, 오프라인 리뷰 레인 캐시(`~/.codex-review/gradle-ro`)에도 부재해 리뷰 레인 사전 스모크가 해석 실패로 죽는다 | 조사 B §1(「1. Money 라이브러리 후보」 표·§1.3 오프라인 실측) |
| **D-2** | **`kotest-property` 6.2.4 를 그대로 쓴다 — 카탈로그·배선 추가 없음.** 전 모듈에 `testImplementation` 으로 이미 걸려 있다(`bidvector.kotlin-conventions.gradle.kts`). jqwik 은 `ADR 0007` §1.1.1 이 이미 탈락시켰다 | 조사 B §2(「2. property test 도구」 표) |
| **D-3** | **`java.util.Currency` 를 허용 목록에 열지 않는다 — 도메인 소유 `enum class Currency { KRW }`(또는 값 객체)를 둔다.** `Currency` 는 `effect.surface.classes`(T-D, 로케일 자원 I/O)에 있어 열면 이질 효과가 함께 열린다. 이 도메인은 KRW 하나뿐이라 로케일 자원이 불필요하다 | 조사 B §3.4 |
| **D-4** | **`ModuleBoundaryAnchor` 삭제와 첫 값 타입 추가는 같은 커밋이어야 한다.** `DomainApiTypeGateTask` 가 도메인 모듈 소스 트리가 비면 `GradleException` 을 던진다 — 삭제만 먼저 하면 `check` 가 깨진다 | 조사 B §3.1 「주의 — 앵커를 지우는 순서」 |
| **D-5** | **`RoundingPolicy`/`PolicyVersion`/`EffectiveDatedPolicy` 정책 데이터는 `config/quality/**`(또는 별도 `config/policy/**`)가 아니라 `shared-kernel` 안의 Kotlin 선언(값 테이블)으로 둔다.** 이유 둘 — ① `config/quality/**` 는 `milestone-1.md` 「게이트 위협 모델」·`ADR 0007` §1.1.2 가 게이트 정의 표면으로 선언했다(도메인 데이터를 섞으면 안 된다) ② 도메인은 `.properties` 를 읽을 수단이 없다(T-C 가 `Properties`·`ResourceBundle` 을 닫는다) | 조사 B §5.1. **`shared-kernel` 소유 여부 자체는 `OPEN-1B-POLICY-HOME`(아래) — 「`config/quality` 가 아니다」만 확정** |

---

## OPEN 표 — 조사 C 의 14건 (기존 8 · 신규 6) + 조사 B corpus 후보

**결정은 이 문서가 내리지 않는다.** 운영자가 이 세션에서 답하면 「계약 갱신」 절로 append한다.

### 기존 8 (조사 C §3)

| id | 미결 요지 | 선택지 | 결정 주체 | 결정 없이 만들 수 있는 것 / 막히는 것 | legacy 근거 (조사 A) |
| --- | --- | --- | --- | --- | --- |
| `OPEN-DIC-04` | `AllocatedBudget`·`YegaAmount`·`AwardAmount` 의 **과세 처리** | 선택지 열거 없음 — U-1·U-1b 는 추정가격·기초금액 둘만 정했다 | 운영자(`capability-map.md` §14.2) | **가능**: 세 타입을 `vatTreatment = Unknown` 으로 선언하고 §1.2.1 의 타입 차단을 그대로 건다. **막힘**: 세 금액이 과세 처리를 아는 금액과 산술 비교에 들어가는 경로 자체(그 경로가 없는 것이 의도다) | *"legacy 는 답을 주지 않는다. 세 금액의 과세를 적은 자리가 0건이고, 부가세 산술도 0건이다"*(조사 A 축 5.3) — **운영자 결정 2026-09-04(B9): 「가능」쪽 approach 를 그대로 승인. 값(과세 처리 자체)은 OPEN 유지** |
| `OPEN-DIC-05` | `BaseAmountProvenance` 승인 라벨 다섯이 legacy 실측을 덮는가 — ① `suspect-fractional` 대응 이름 부재 ② 미판정(`NULL`)과 「출처를 모름」(`Unknown`)이 같은 값인가 | 다섯 값 그대로 두거나 여섯째 variant 를 더한다(승인 명세 집합 변경) | 운영자 | **가능**: 다섯 값(`Clean`·`DerivedYega`·`DerivedVat`·`SuspectRatio`·`Unknown`) 그대로 선언. **막힘**: 여섯째 variant 추가와 「미판정」 별도 상태 설계 | *"reliable_base 는 `NULL` 과 `clean` 의 동일 취급을 설계 목표로 선언하고, 더 새 모듈 award_rate_label 은 반대로 '판정된 적이 없다는 뜻' 이라며 별도 status 를 낸다"*(조사 A 축 6.2) — **운영자 결정 2026-09-04(B10): 다섯 값 그대로 채택. 「미판정」 별도 상태는 1D 로. OPEN 유지** |
| ~~`OPEN-DIC-08`~~ | 파생 `Money`(`BidAmount`)·파생 율(`AssessmentRate`·`AwardRate`)이 자기 값에 무엇을 실어 입력 fact 를 되짚게 하는가 | ① 아무것도 안 싣는다(`DecisionProvenance` 로만 되짚기) ② 입력 fact 참조 + 정책 version 을 값에 싣는다 ③ `FactProvenance` 에 파생 산출 variant 를 더한다 | 운영자(운반 범위) | **가능**: `FloorRateOrigin`·`BidRateOrigin` 처럼 이미 닫힌 축까지, `BidAmount` 를 `provenance` 필수인 `Money` 로 선언하는 것까지. **막힘**: 파생 `Money` 의 `provenance` 값이 무엇인가(③은 `FactProvenance` 어휘 변경이라 1B 가 임의로 못 한다) | *"파생 율은 legacy 가 선택지 ②를 이미 구현했다 — `award_rate_label` 이 값+분모값+분모출처+축 주장 가능 여부+원본 오염 태그를 payload 로 싣고 그 대가까지 문서화한다"*(조사 A 축 6.3) — **해소 — 운영자 결정 2026-09-04(B11): 선택지 ② 채택.** 정본은 `data-dictionary.md` §9·§11.1 |
| `OPEN-DIC-10` | `RoundingPolicy` 의 `mode` 값 · 금액 축 밖 `scaleDigits` · 첫 `effectiveFrom` · 「금액 축 밖」의 경계 | `mode`: ⓐ 사사오입 ⓑ 짝수 자리 ⓒ 버림 ⓓ 올림 ⓔ 그 밖 / 축 밖 `scaleDigits`: ⓐ 축별 승계 ⓑ 통일 / 첫 `effectiveFrom`: ⓐ 과거 전부 덮음 ⓑ 시행 시점 명시(`NotApplicable`) | 운영자(값 결정) | **가능**: 형태 전부 — `RoundingPolicy(scaleDigits, mode)`, `EffectiveDatedPolicy` wrapper, `resolve` 계약, 금액 축 자리수(정의 ① 원 단위 정수로 이미 닫힘), 하한 이상 보장 단언(정의 ②, mode 무관). **막힘**: 정책 엔트리의 **값** — `mode` 없이는 반올림 경계 property test(P-2b 제외)의 기대값이 서지 않는다 | *"legacy 는 반올림 모드를 어디에도 선언하지 않는다. 관측되는 half-to-even 은 파이썬 round() 의 기본값이며 의도의 증거가 아니다"*(조사 A 축 4 D-5) — **운영자 결정 2026-09-04(B7): 형태·version 배관·하한 이상 보장 단언은 1B 가 구현. `mode`·축 밖 `scaleDigits`·첫 `effectiveFrom` 값은 OPEN 유지** |
| `OPEN-DEC-07` | 기준 금액 신뢰 비율 `1.15` 의 마진 `0.05` 값 | 결정은 이미 (b) V2 코퍼스에서 재유도로 확정 — 값이 아직 산출되지 않음 | V2 코퍼스(코퍼스 확보가 선행 조건) | **가능**: 정책 엔트리 자리(부가세 배수 1.1 + 마진)와 판정 순서만 고정. **막힘**: 임계값을 쓰는 경계 fixture | 조사 A 는 이 축을 직접 다루지 않음(§5.4 부가세율 표가 `1.15` 를 「마진이 OPEN-DEC-07 재유도 대상」으로만 인용) — **운영자 결정 2026-09-04(B8): 판정 순서만 고정 승인. 값은 OPEN 유지** |
| `OPEN-ADR-16` | `duplicate mechanical helper` 축을 무엇으로, 어떤 기준으로 측정하는가 | (a) PMD CPD 등 기존 도구 채택 (b) `qualityBaseline` 자체 판정 추가 (c) 측정하지 않고 리뷰에 맡김 | 운영자, 시점은 1B 도구 조사 뒤 | **가능**: 도구 조사 자체가 1B 산출물. **막힘**: 여섯째 축의 래칫 배선 | 조사 A 는 이 축을 다루지 않음(1A 이월 항목) — **운영자 결정 2026-09-04(C12): `OPEN-ADR-06`과 함께 1B 종료 시점에 결정. 시점만 확정, 선택지는 OPEN 유지**(`docs/adr/0007` §5) |
| `OPEN-ADR-06` | 래칫 축에 클래스/타입 단위 크기(또는 상속 깊이·mixin 수)를 넣는가 | (a) 클래스/타입 크기 축 추가 (b) 결합도 축(fan-in/out, public API 수)으로 대신 잡는다 (c) 추가하지 않고 리뷰에 맡긴다 | 운영자 — 1A 실측 뒤 | **가능**: 1A `qualityBaseline` 의 아홉 축이 이미 값을 낸다 — 1B 착수를 막지 않는다. **막힘**: 축 채택 결정 | 조사 A 는 이 축을 다루지 않음 — **운영자 결정 2026-09-04(C12): 위와 같음, 같은 시점**(`docs/adr/0007` §5) |
| `OPEN-REG-05` | 기초금액·추정가격 차이의 잔차 성분 · 저장 값의 행별 과세 처리 | 선택지 없음 — 측정 대상 | V2 코퍼스(잔차 측정) | **가능**: basis 태그가 다른 값 쌍 fixture(어느 쪽이 크다는 전제 없이). **막힘**: 과세/비과세로 정의된 경계 쌍 | *"같은 필드에 대해 두 선언이 공존하고, 어느 쪽도 다른 쪽을 인용하지 않는다"* — 부가세 포함/제외/별도 다섯 선언이 저장소 안에서 서로 모순(조사 A 축 5.2) — **운영자 결정 2026-09-04(C14): 과세/비과세 경계 쌍을 만들지 않는 기존 방침 재확인. OPEN 유지** |

### 신규 6 — 표기·정의 불일치 및 계약 공백 (조사 C §6)

| id | 미결 요지 | 선택지 | 결정 주체 | 결정 없이 만들 수 있는 것 / 막히는 것 | legacy 근거 (조사 A) |
| --- | --- | --- | --- | --- | --- |
| ~~`D-1`(조사C)~~ | 추정가격 타입 이름이 둘 — 승인 명세는 `EstimatedAmount`, 0C 정본은 `EstimatedPrice` | 승인 명세 개정 또는 0C 정본 개정 | 운영자(명세 개정 권한) | **가능**: 1B 가 잠정 이름 하나로 구현을 진행(타입 형태 자체는 결정 무관). **막힘**: 두 문서 표기의 최종 일치 | 조사 A 는 이름 표기 축을 다루지 않음(개념 자체는 M-3·M-5 가 다룬다) — **해소 — 운영자 결정 2026-09-04(A1): `EstimatedAmount` 채택.** `data-dictionary.md` §1.2 정정 |
| ~~`D-2`(조사C)~~ | basis 타입 이름이 둘 — 승인 명세는 `Basis`, 0C 정본은 `AmountBasis` | 위와 같음(경중 낮음) | 운영자 | 위와 같음 | 해당 없음 — **해소 — 운영자 결정 2026-09-04(A2): `Basis` 채택.** `data-dictionary.md` §1.1 정정 |
| ~~`D-3`(조사C)~~ | 금액 타입 집합의 크기 — 승인 명세는 넷, 0C 정본은 여덟(`AllocatedBudget`·`AwardAmount`·`ConstructionCapacityAmount`·`AwardedContractLimit` 추가) | 1B in_scope 를 넷으로 두거나 여덟으로 둔다 | 운영자(scope 결정) — `capability-map.md` §14.2 가 `OPEN-DIC-04` 를 1B 로 지목하며 세 타입을 이름으로 드는 것이 「여덟」 쪽 근거 | **가능**: 넷(`BaseAmount`·`EstimatedAmount`·`YegaAmount`·`BidAmount`) 우선 구현. **막힘**: `AllocatedBudget`·`AwardAmount`·`ConstructionCapacityAmount`·`AwardedContractLimit` 포함 여부 — 즉 1B 완료 판정의 범위 자체 | *"`BaseAmount`뉴타입을 `bid_base` 경계 시그니처 한 곳에만 강제"*(B-4) — legacy 도 전수 타입화를 완결하지 못했다 — **해소 — 운영자 결정 2026-09-04(A3): 1B in_scope = 넷 + `AllocatedBudget`·`AwardAmount`(여섯).** `ConstructionCapacityAmount`·`AwardedContractLimit`는 1C/1D 소유 |
| ~~`O-1`(조사C)~~ | overflow 실패 계약이 승인 문면에 없다 — `milestone-1.md` 1B 항목의 이름 한 줄이 전부 | 신규 계약 정의 필요(선택지 미제시) | 운영자/spec-writer | **가능**: 없음 — 근거 없이 값·계약을 지어내지 않는다(`v2-지침서.md` §7). **막힘**: overflow property test(P-5) | *"`optional_float('1e400') → inf`, `_positive_or_none` 를 통과한 `inf` 가 `BaseAmount(inf)` 로 가격 경로에 흐른다"* — legacy 다섯 coercion 함수의 accept set 이 전부 다르다(조사 A 축 1 「문자열 파싱 지점」표) — **해소 — 운영자 결정 2026-09-04(A4): `Long` 원 단위 연산은 `Math.*Exact` 계열, overflow는 1급 실패(`Unmeasurable` 계열).** `docs/adr/0002` D-10 신설 |
| ~~`O-2`(조사C)~~ | `BigDecimal` `Rate` 의 동등성·해시 규정이 없다 — `equals` 가 scale 을 본다 | 미제시 — 1B 설계 판단으로 닫을 여지가 있는 축(운영자 확인 권고) | 운영자 확인 또는 1B 구현 판단(경중 낮음) | **가능**: `Rate` 를 정규화된 `BigDecimal`(고정 scale)로 생성하고 동등성을 값 의미로 재정의. **막힘**: 반올림·왕복 property(P-1a·P-2a)의 정확한 기대값 | 해당 없음 — legacy 는 `Decimal` 사용 0건(조사 A 축 4 D-2) — **해소 — 운영자 결정 2026-09-04(A5): 값 동등(`compareTo == 0`) 채택, scale 은 표현.** `data-dictionary.md` §1.4.2 추가 |
| ~~`O-3`(조사C)~~ | 정책 식별 축이 둘(`ruleVersion`+`corpusScope`)이라는 §4.1 규칙이 `RoundingPolicy` 에도 걸리는가 | 예/아니오 | 운영자 또는 data-dictionary 저자 확인 | **가능**: `PolicyVersion(effectiveFrom, source)` 한 축으로 배선. **막힘**: `corpusScope` 축 필요 여부 | 해당 없음 — legacy 는 정책 version 개념 자체가 0건(조사 A 축 4 D-6) — **해소 — 운영자 결정 2026-09-04(A6): 코퍼스 무관 정책(`RoundingPolicy`)은 `ruleVersion` 한 축으로 충분(예외 채택).** `data-dictionary.md` §4.1 추가 |

### 조사 B corpus 후보 — 되돌림 열쇠의 소유 slice 미지정

| id | 미결 요지 | 결정 주체 | 결정 없이 만들 수 있는 것 / 막히는 것 |
| --- | --- | --- | --- |
| `OPEN-1B-CONTRACT` | fixture 계약 술어 설계(presence/non-null·형태 · 의미 범주 · `equals-path`)가 1B in_scope 인가 — `money-basis` 6건·`rate-unit` 5건이 2026-09-02 `insufficient-evidence` 로 강등됐고, `uncovered_axes` 의 `unblocks_when` 이 그 소유를 「M1 계약 설계」에만 두고 slice 를 지정하지 않았다 | 운영자·spec-writer | **가능**: 타입 계약 자체는 이 술어 설계 없이도 선다(§1B 완료 조건 대조가 그 분리를 이미 확인). **막힘**: `milestone-1.md` 완료 조건 「승인된 authoritative corpus 전체 통과」를 1B 축에서 증명하는 것 — 술어가 없으면 그 corpus 는 여전히 0건이다 — **운영자 결정 2026-09-04(C13): 「M1 계약 술어 설계」의 소유 slice = M1 1B.** `capability-map.md` §14.2에 신설 행으로 등재. **fixture-curator 레인이 담당을 이어받아 실행했다(`fixtures.md`)**: 술어 셋(`is-present`·`differs-from-path`·`differs-from-case`)의 **설계는 `fixtures/manifest.yaml`의 `m1_contract_binding.predicate_design`에 있다.** **실행 도구 확장(`fixtures/tools/manifest_contract.py`의 `holds()`·스윕 변이체 생성 규칙)은 미착수** — 그 파일은 in_scope 밖이라 이 레인이 열지 않았다(`fixtures.md` 알려진 제한 2) |
| `OPEN-1B-CORPUS` | 1B 축 authoritative corpus 0건인 채로 완료 조건을 어떻게 다루는가 | 운영자 | **가능**: example test 를 「입력 형태·기대 방향의 참고」로 쓰되 「승인된 corpus 통과」로 계상하지 않는다(조사 B §4.2). **막힘**: 위와 동일 — 그 완료 조건 자체 — **운영자 결정 2026-09-04(C13)이 되돌림 조건을 정했으나 fixture-curator 레인 실행 결과 되돌린 case 는 0건이다**(`fixtures.md` §1). 남은 미결 요지를 다시 좁힌다 — **① 술어 어휘 동결 해제(도구 범위, `manifest_contract.py`) ② fixture 결과 토큰(`Accepted`/`Rejected` 등)과 1B 계약 어휘(`Fact.Known`/`Absent`·`ReasonCode`)의 정렬 = 재추출**(기대값 파일이 바뀌므로 되돌림이 아니다). **결정 주체는 운영자, 담당 slice는 미정**(오케스트레이터가 그 결정을 운영자에게 올린다) — `money-basis-001`·`004`(BLOCK-4, 컴파일 차단이라 거부 객체 자체가 없음)와 `money-basis-003`(BLOCK-5, 검색 경로 타입 부재)은 술어가 갖춰져도 별도로 막힌다 |

**착수를 막는 항목은 없다** — 위 OPEN 전부 「형태·타입 계약은 결정 없이 선다」쪽이고, 값·범위·명세
집합 변경이 걸린 항목만 구현의 뒤쪽(정책 엔트리 값, example fixture 승격, 완료 조건 판정)에서
막힌다.

---

## 위협 모델 — 1B 고유 경계 (Phase 2.5 가 채운다)

`milestone-1.md` 「게이트 위협 모델」(운영자 결정 2026-09-03)의 방어/비방어 경계를 그대로
승계한다 — 모듈·소스·의존 선언을 통해 들어오는 위반을 방어하고, 게이트 정의·배선 자체를
바꾸는 편집(`build-logic/**`·`config/quality/*.properties`·`-x`·`enabled = false` 등)은
방어하지 않는다.

**1B 고유의 경계는 여기서 정하지 않는다.** 타입 계약이 컴파일 시점에 막는 것(basis 교차 대입,
`vatTreatment` 혼입 비교) / 런타임 실패 계약으로 막는 것(overflow — 운영자 결정 2026-09-04로
계약이 생겼다: `Math.*Exact` 위반 시 1급 실패, `docs/adr/0002` D-10, unknown provenance) /
어느 쪽도 막지 않는 것(값 오분류 — 옳은 타입에 잘못된 값을 넣는 것은 타입이 못 막는다)의
구체 경계는 Phase 2.5 설계 검토가 채운다 — 게이트·계약형 slice 설계 검토 필수 규정
(`v2-slice-pipeline` SKILL, 운영자 지시 2026-09-02)이 이 slice 에도 적용된다.

---

## 이 slice 가 하지 않는 것

- **1C·1D·1E 소유 규칙을 만들지 않는다.** first-match provenance rule 자체, 자격 판정,
  법정 하한 판정, strategy predicate 는 각 slice 소유다 — 1B 는 그것들이 딛는 carrier 와
  라벨 타입만 만든다.
- **OPEN 을 임의로 해소하지 않는다.** 위 표의 값·명세 집합 변경은 운영자 결정을 기다린다.
- **fixture 를 신설하지 않는다.** fixture-curator 소관이고 `OPEN-1B-CONTRACT` 가 열려 있다.
- **evidence 가 자기를 검사하는 장치를 만들지 않는다.** 대조는 `commands.md` 의 명령이 낸다.

---

## 계약 갱신

### 2026-09-04 — 운영자 결정 14건을 채택한다 (조사 C·B가 낸 OPEN 패키지)

**넓힌 범위**: `docs/discovery/data-dictionary.md`(§1.1·§1.2·§1.4.2·§4.1·§9·§11의 해당
행/절), `docs/adr/0002-money-rate-basis.md`(§2 D-10 신설·D-9 정정·§6 ① 정정),
`docs/adr/0007-test-pyramid-and-ratchet.md`(§5 `OPEN-ADR-06`·`OPEN-ADR-16` 결정 시점),
`docs/discovery/capability-map.md`(§14.2의 `OPEN-ADR-06`·`OPEN-DIC-08`·신설
`OPEN-1B-CONTRACT` 행과 그 안의 낡은 `file:line` 정정), `fixtures/manifest.yaml`(경로만 —
실제 편집은 Phase 3). **그 문서 안에서도 위에 적은 절/행 밖은 여전히 out_of_scope다.**

**결정 열넷**(A1~A6·B7~B14·C12~C14 — 팀장이 전달한 운영자 패키지의 라벨을 그대로 쓴다):

| # | 결정 | 정본 |
| --- | --- | --- |
| A1 | 추정가격 타입 이름 = `EstimatedAmount` | `data-dictionary.md` §1.2 |
| A2 | basis 타입 이름 = `Basis` | `data-dictionary.md` §1.1 |
| A3 | 1B 금액 타입 집합 = 넷 + `AllocatedBudget`·`AwardAmount`(여섯). `ConstructionCapacityAmount`·`AwardedContractLimit`는 1C/1D | 이 파일 D-결정 표 확장(위 「이 slice가 하는 일」) |
| A4 | overflow 실패 계약 — `Long` 연산은 `Math.*Exact`, overflow는 1급 실패(`Unmeasurable` 계열) | `docs/adr/0002` D-10(신설) |
| A5 | `BigDecimal` `Rate` 동등성 — 값 동등(`compareTo == 0`), scale은 표현 | `data-dictionary.md` §1.4.2 |
| A6 | 정책 식별 두 축 규칙의 예외 — 코퍼스 무관 정책(`RoundingPolicy`)은 `ruleVersion` 한 축 | `data-dictionary.md` §4.1 |
| B7 | `OPEN-DIC-10` — 형태·배관·하한 이상 보장은 1B, `mode`·축 밖 값은 OPEN 유지 | 위 OPEN 표(변경 없음, 확인만) |
| B8 | `OPEN-DEC-07` — 판정 순서만 고정, 값은 OPEN 유지 | 위 OPEN 표(확인만) |
| B9 | `OPEN-DIC-04` — 세 금액 `VatTreatment.Unknown` 선언 승인, 값은 OPEN 유지 | 위 OPEN 표(확인만) |
| B10 | `OPEN-DIC-05` — 승인 다섯 값 그대로, 「미판정」은 1D | 위 OPEN 표(확인만) |
| B11 | `OPEN-DIC-08` — 선택지 ② 채택(입력 fact 참조 + 계산 정책 version, 금액·율 동일) | `data-dictionary.md` §9·§11.1(해소) · `capability-map.md` §14.2(해소) |
| C12 | `OPEN-ADR-16`·`OPEN-ADR-06` — 1B 종료 시점에 함께 결정(시점만 확정) | `docs/adr/0007` §5 · `capability-map.md` §14.2 |
| C13 | corpus — 「M1 계약 술어 설계」 소유 slice = M1 1B | `capability-map.md` §14.2(신설 `OPEN-1B-CONTRACT` 행) |
| C14 | `OPEN-REG-05` — 과세/비과세 경계 쌍 미생성 방침 재확인 | 위 OPEN 표(확인만, 문서 변경 없음) |

**문서 정정 후보(조사 A) — `AwardRate` 분모**: 1B 범위 밖으로 등재만 한다.
`data-dictionary.md` §9에 `OPEN-DIC-11`(신설)로 등재했다 — legacy 안에서 `AwardRate`
분모가 기초금액이라는 §1.4.2의 서술이 다수설이 아니라는 조사 A의 발견을 판정 없이 기록한다.

**갈래**: A1·A2·A3·A4·A5·A6·B11은 **전건 해소**(그 축의 결정 없이 만들 수 없던 부분까지
답했다). B7·B8·B9·B10·C14는 **부분 확정**(「결정 없이 가능」했던 접근을 명시 승인했을
뿐 값은 그대로 OPEN이다). C12는 **시점만 확정**(선택지는 그대로 OPEN). C13은 **담당만
확정**(술어 자체는 미설계).

### 역방향 파급 — data-dictionary.md·ADR 0002 편집이 만든 줄 밀림

이 slice가 `data-dictionary.md`·`docs/adr/0002-money-rate-basis.md`에 줄을 넣었으므로
그 두 파일을 가리키는 다른 문서의 `file:line`을 stem 기준으로 훑었다:

```
grep -rnoE 'data-dictionary\.md:[0-9]+|data-dictionary:[0-9]+' \
  --include='*.md' --include='*.kt' --include='*.kts' --include='*.properties' . \
  | grep -v '^\./bid-vector/' | grep -v '/build/' | grep -v '^\./docs/discovery/data-dictionary.md:'
grep -rnoE 'docs/adr/0002-money-rate-basis\.md:[0-9]+|docs/adr/0002:[0-9]+|ADR ?0002[^)]{0,3}:[0-9]+' \
  --include='*.md' --include='*.kt' --include='*.kts' --include='*.properties' . \
  | grep -v '^\./bid-vector/' | grep -v '/build/' | grep -v '^\./docs/adr/0002-money-rate-basis.md:'
```

**`docs/discovery/capability-map.md`의 네 좌표를 이 slice가 정정했다**(§14.2 — `OPEN-QUAL-11`
· `OPEN-DIC-03` · `OPEN-DEC-03` · `OPEN-REG-05` 행). 정정하며 실측한 것 — **그 네 좌표는
이 slice의 편집 이전, HEAD 시점부터 이미 어긋나 있었다**(예: `docs/adr/0002:186`이 가리키던
자리가 HEAD에서도 `## 5.` 절 머리말이었지 인용문이 아니었다). 이 slice의 편집이 원인이
아니라 **이 slice의 역방향 파급 검사가 이전부터 있던 drift를 발견**한 사례다. 줄 번호 대신
절 제목·인용문으로 바꿔 다시 밀리지 않게 했다.

**`reports/evidence/m0/0c/**`·`m0/0d/**`의 일곱 좌표는 고치지 않는다.** 그 evidence는
닫힌 slice의 봉인된 기록이라(evidence-pack SKILL — 이력은 되쓰지 않는다) 이 slice가
편집할 수 없다. 이 slice의 `data-dictionary.md`·`docs/adr/0002` 편집으로 그 좌표들이
가리키는 실제 줄이 이동했을 수 있으나, **그 evidence는 작성 시점의 상태에 대한 기록이므로
사후 이동은 정의상 발생한다**(같은 원칙이 1A checklist 「낡는 좌표」 절이 이미 확인한 것과
같다). `capability-map.md`·`docs/adr/0007` 자체의 `file:line` 드리프트는 기존
`OPEN-ADR-15`(registry 통합 slice 소관)가 이미 추적 중이며 이 slice의 `docs/adr/0007`
편집(결정 시점 추가, §5 하단)은 그 등재보다 아래 지점이라 기존 인용 좌표를 추가로 밀지
않았다 — 확인 명령은 `checklist.md`의 「알려진 제한」이 낸다.

### 2026-09-04 — Phase 3 구현 완료. 설계 검토(`04_design-review.md`) 브리프와 갈린 결정 셋

**넓힌 범위 없음** — 아래는 브리프의 세부 지시와 실제 구현이 갈린 지점의 사유이지 in_scope
변경이 아니다.

1. **커밋 경계를 여덟(C1~C8)에서 넷으로 묶었다.** Kotlin 은 모듈 소스 전체를 한 번의
   `compileKotlin` 으로 컴파일하므로 `Carrier.kt`(`Measurement.Measured` 가
   `PolicyVersion` 참조) ↔ `Policy.kt`(`Resolution.NotApplicable` 이 `ReasonCode` 참조)
   처럼 상호 참조하는 파일은 브리프가 그은 개념 경계(①어휘 ②Rate ③carrier ④정책 ⑤파생)
   대로 커밋을 쪼개면 중간 커밋이 컴파일되지 않는다. 파일 의존 순서를 실측해 네 커밋으로
   재구성했고, 각 커밋을 `git stash --keep-index` 로 이후 파일을 제외한 상태에서
   `:shared-kernel:check` 를 실제로 돌려 독립 통과를 확인했다(`commands.md`·`checklist.md`
   「커밋 목록」).
2. **컴파일 실패 하네스(C8)와 크기 기반 분기 부재 architecture test(L-7)를 이월했다.**
   브리프 자신이 C8을 "마지막 커밋으로 미루고 타입 설계가 착지한 뒤에 붙이는 것을 추천"
   했고, 게이트 하나 도입에 이 저장소가 리뷰 라운드를 크게 쓴 이력(`CLAUDE.md` 변경 이력)
   을 고려해 이번 slice 에서는 붙이지 않는다. 사유·이월 대상은 `checklist.md` 「이월 항목」.
3. **fixture 되돌림(C13)을 술어 설계까지만 하고 `fixtures/manifest.yaml` 편집은 하지
   않았다.** manifest 편집은 fixture-curator 소관이라고 브리프 §4.7 단계 3 자신이 적는다 —
   kotlin-implementer 레인이 그 파일을 편집하면 레인 경계를 넘는다. `OPEN-1B-CONTRACT` 는
   담당(1B)만 확정된 채로 남고, 술어 자체의 설계는 fixture-curator 협업이 필요한 별도
   착수점으로 `checklist.md` 에 남긴다.

**acceptance 전건 재실행**: `scope.md` `acceptance_commands`(B-0~B-7 전부) 를 최종 HEAD
에서 재확인했다 — 결과는 `commands.md` Phase 3 절이 갖는다. 31 test 0 실패 0 skip,
격리 worktree `check` 통과, `domainApiTypeGate`·`domainSourceReferenceGate` 실제 도메인
API 위에서 단독 실행 통과.

### 2026-09-04 — 운영자 결정: 이월 셋 중 컴파일 실패 하네스는 1B 안에서 닫는다

**넓힌 범위**: `config/quality/gate-tests.properties`(`gate.tests.shared-kernel` 키 신설에
한정 — 위 in_scope 표에 반영). fixture 되돌림(C13)은 여전히 이월 — fixture-curator 레인이
병렬로 진행 중이며 `fixtures/**`·`golden-manifest.json`은 그대로 out_of_scope다.

**사유**: 컴파일 실패 하네스는 milestone-1 1B 항목의 "basis 교차 대입 … 의 실패 계약"과
설계 검토 L-2("상호 대입이 컴파일되지 않는다"가 CI로 증명되지 않음)의 유일한 기계적 증명
수단이라 이월 대상이 아니다(운영자 결정 2026-09-04). `CompileFailureHarnessTest`(다섯
음성·양성 쌍)를 `kotlin-compiler-embeddable`로 구현했고, `gateExecutionGate`가 그 실행을
단언한다 — 음성 실측: `--tests "*MoneyTest*"`로 하네스를 실행 집합에서 뺀 뒤
`gateExecutionGate`를 단독 돌려 "게이트 test class 가 실행되지 않았다"로 실패함을 확인했다
(공허한 통과가 아니라는 증거, `commands.md`).

**L-7(크기 기반 단위 추측 분기 부재의 architecture test) 판정**: **신규 architecture test를
세우지 않고, 기존 property test(P-3a)·example test(E-3)로 충분하다고 판정한다.**
`Rate.ofFraction`/`ofPercent`(`Rate.kt`)는 정의 자체가 값 크기를 보는 조건문을 전혀 갖지
않는다 — 두 함수 다 `normalized(value)` 또는 `normalized(value.divide(100))`뿐이고 `if`가
없다. P-3a가 "단위 선언 없이 Rate가 만들어지지 않는다"(경로가 이름 있는 팩토리 둘뿐)를,
E-3이 legacy 임계 0.5·1.5·1.500001·2.0을 통과시켜 "크기와 무관하게 변환이 이름으로만
갈린다"를 이미 실측으로 고정한다. **다만 이것은 build-logic 확장을 통한 회귀 방지
게이트가 아니다** — 누군가 `Rate.kt`에 매직넘버 분기를 새로 넣어도 이 판정 자체는 CI가
자동으로 막지 못하고, P-3a·E-3의 기존 기대값과 충돌해야 잡힌다(간접 방어). 직접적인
구조적 게이트(예: 소스에서 `Rate.kt`·`MoneyArithmetic.kt` 안의 조건부 분기를 탐지)를
세우려면 `build-logic/**` 확장이 필요하고, 그것은 이 slice 의 in_scope 밖이며 위협 모델
경계(`milestone-1.md` 「게이트 위협 모델」)가 `build-logic/**`을 방어 대상 밖에 둔 것과
같은 이유로 비용 대비 편익이 낮다고 판단했다(게이트 하나 도입에 리뷰 라운드를 크게 쓴
이력, `CLAUDE.md` 변경 이력). **이월하지 않고 여기서 닫는다** — 미달로 등재하되
`checklist.md`에 이 판정 전문을 남긴다.
