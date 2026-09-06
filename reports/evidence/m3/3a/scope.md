# Slice 계약 — M3 / 3A · 수집 port 와 canonical fact — **초안, 구현 전**

> **지위**: M2 진행 중에 세션 모델이 쓴 **계약 초안**. 구현·gradle·fixture 편집 없음. 착수는 M2 계약 승인 뒤 운영자 지시로 하며 그때
> `base_sha` 재고정, `prep/m3-prep.md` D-M3-3·4·8 과 아래 D-3A-1~2 답 수령, `milestone-3.md` 착수 문단. 3A 는 **도메인 모듈만**
> 만지므로 M2 경로와 겹치지 않는다.

```yaml
milestone: m3
slice: 3a-collection-ports-and-canonical-facts
base_sha: 040ab9d   # 초안 시점 앵커(M1 전체 승인) — **M2 승인 뒤 착수 시 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - procurement/**                                    # 도메인: canonical fact 타입·수집 command·port 인터페이스 셋·식별자 값 객체·필드 계약 레지스트리 타입·수집 회계·provenance 판정 지점·정책 데이터 형태·test
  - procurement/src/main/resources/policy/**          # 정책 데이터 형태만(값은 curator·운영자): 필드 계약 목록 · resultCode 17 범주 · 해석 순서(§5.2)
  - config/quality/api-type-policy.properties         # 조건부 — 도메인 API 타입 허용 목록에 3A 값 타입 등재 시(게이트 정의 편집, 사유 evidence)
  - config/quality/gate-tests.properties              # 조건부 — `gate.tests.procurement`
  - app/src/test/kotlin/bidvector/app/conformance/**   # 조건부 — `koneps-collection` authoritative 가 있을 때 runner dispatch(1B-c~1E 관례)
  - app/build.gradle.kts                              # 조건부 — testImplementation(project(":procurement")) 한 줄
  - fixtures/manifest.yaml, fixtures/input/**, fixtures/expected/**   # 조건부 — D-M3-8 승격·신설(curator, 기존 기대값 무변경)
  - milestone-3.md                                    # 「Slice 3A」 착수 문단, 착수 시
  - reports/evidence/m3/3a/**
out_of_scope:
  - adapters/**                                       # HTTP·파싱·DB 는 3B·3D. 3A 는 port 와 타입만
  - shared-kernel/**                                  # 1B·1D 승인 산출물 — 필요한 carrier 부재 시 멈추고 보고
  - 감시·자격·판정 소비                                 # M4 4B
  - 공고 상태 전이표의 실행(§2.2.1)                       # `NoticeStatus` 값·전이 test 는 3A 가 두되 **이벤트를 만드는 use case** 는 4B
  - 브라우저 크롤(COL-09)·mock 데이터(COL-10)·수집 스케줄·lease(OPS-01/02)
  - M2 경로 일체, capability-map.md·data-dictionary.md 편집
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :procurement:test"                                                                     # S-2 — 도메인 test(property 포함: 회계 항등식·식별자 정규화·first-match)
  - "./gradlew :procurement:domainApiTypeGate :procurement:domainSourceReferenceGate :procurement:typeShapeGate :procurement:sizeGate :procurement:cpdCheck"   # S-3
  - "./gradlew :app:test --tests '*Conformance*'"                                                      # S-4 — 조건부(authoritative ≥ 1)
  - "./gradlew qualityBaseline"                                                                        # S-5
  - "python3 fixtures/tools/mutation_sweep_adversarial.py"                                             # S-6 — 조건부(manifest 편집 시), 종료 코드 0/1/2
rollback: |
    **정본은 `reports/evidence/m3/3a/rollback.md`**(착수 시). procurement 모듈은 앵커로 돌아간다. 조건부 경로는 채택된 것만.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-3.md` 3A·완료 조건 · `capability-map.md` COL-01·02·05·06·07·08 · `data-dictionary.md` §2.2.1·§5.1~5.3 ·
`v2-지침서.md` §4.1·§4.3 · `ADR 0006` D-3·D-7 · `ADR 0004` D-5 · 1B(`Money`·`Provenance`)·1D(`BaseAmountProvenance`)·1C(정책 배관) 산출물.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 재고정한 base 로 낸다.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **port 셋** — `NoticeSourcePort`·`OpeningResultSourcePort`·`DocumentSourcePort` 를 `procurement` 안에 **도메인 소유 인터페이스**로. 입력은 조회 조건 값 객체(기준일 = KST 캘린더 일자 타입, 페이지 커서), 출력은 `SourceBatch<Observation>`(원문 항목 + 회계). 어댑터(3B)가 구현 | 3A 「port」 · ADR 0006 D-3 「domain 이 의존하는 port 는 domain 안에」 · COL-01 「KST 기준일」 |
| ② | **외부 DTO 와 command 의 분리** — `RawNoticeObservation`(원문 키→문자열 값 그대로, `observedAt`, 출처) ↔ `NoticeCollected` command(canonical 값 + provenance). 변환은 **한 함수**(`canonicalize`)이고 원문은 버리지 않는다(감사) | 3A 「외부 DTO 와 domain command 분리」·「원문 field, canonical value, provenance, observed-at 보존」 |
| ③ | **식별자 값 객체** — `NoticeId(number: NoticeNumber, round: NoticeRound)`. `NoticeRound` 는 **제로패딩 문자열 식별자**(`"000"` 보존, `int` 변환 금지 — R-QUAL-05). 정규화 규칙은 값 객체가 소유(호출부 반복 금지 — COL-05). `source_url` 정규화 불변식(같은 url ⇒ 같은 id)은 property test | COL-01 acceptance 「제로패딩 차수 원문 보존」 · COL-05 「식별자 값 객체가 정규화 규칙을 소유」 · §5.3 「식별자는 숫자가 아니다」 |
| ④ | **필드 계약 레지스트리(타입 + 정책 데이터)** — `KonepsFieldContract(rawName, concept, basis, scale, unit, nullability, vatTreatment, authoritative, presentIn, provenance, effectiveFrom, expectedRange)`. **등재되지 않은 raw 키는 canonical 값으로 소비될 수 없다**(타입: 소비 함수가 계약을 인자로 요구) — 미지 키는 회계 `unknownFields` 로 계수. `scale`·`basis` 어긋남(fraction 자리에 백분율)은 항목 거부 + 사유 | §5.3 · COL-07 acceptance 둘 · D-M3-3 |
| ⑤ | **provenance 판정 지점 하나** — 기초금액·추정가격 해석 순서는 정책 데이터(§5.2 「해석 순서는 정책 데이터」, 추정가격 순서에 기초금액 키 없음), `0`·미상 후보는 건너뜀, 결과는 `Provenance`(1B) + `BaseAmountProvenance` 라벨(1D decision 27) 을 **같은 지점**이 낸다. 검증기도 같은 함수를 호출 | §5.1 「KONEPS 수집 경로의 variant 전부를 같은 판정 지점이 낸다」 · §4.3 · §5.2 「해석을 한 지점에」 |
| ⑥ | **파생이 원본을 덮지 않는다 — 타입** — `ResolvedBaseAmount = sealed { Direct(Published), FallbackFromBudget(sourceKey), DerivedFromOpening }`. 개찰 행의 `sucsfbidRate`(사정률)는 기초금액을 배출하지 않고 예정가를 **파생 필드 + provenance** 로. 점유 가드(권위값만 덮음)는 3D 의 write 규칙이고 3A 는 **「덮어도 되는가」를 `isAuthoritative` 데이터**로 선언 | COL-02 acceptance 둘 · §5.1 규율 1·2 · §5.2 「자리가 같아야 한다면 타입이 달라야 한다」 · ADR 0004 D-5 |
| ⑦ | **수집 회계** — `CollectionAccounting(received, normalized, duplicate, dropped, dropReasons, sourceTotal, pagesFetched, truncated, unknownFields)`, **`received = normalized + duplicate + dropped` 항등식은 생성자 불변식**(위반 = 생성 실패). `dropReasons` 는 sealed 코드. 뺄셈 역산·`setdefault` 채움 없음 | COL-06 acceptance 「항등식 위반 시 산출 실패」 · legacy 형태 처리 |
| ⑧ | **금액·율·일시 canonical** — 콤마 금액 문자열·백분율 문자열(`"87.995"`)·타임존 없는 일시는 **어댑터가 원문 unit 을 기록하며 변환**하고 3A 는 `Money`(1B)·`Rate`(fraction)·`Instant`+KST 일자 타입만 받는다. 값 크기로 단위 추측 금지(ADR 0002 D-4) — percent/fraction 은 필드 계약의 `scale` 이 정한다 | §4.1 · COL-01 골든 형태 · R-RATE-01 |
| ⑨ | **`NoticeStatus` 와 전이표(값·거부만)** — `Open·Renoticed·Closed·Awarded·Failed·Cancelled`, 표에 없는 쌍은 거부이며 관측 가능. `isBiddable(notice, now)` 파생 술어. 이벤트를 만드는 use case 는 4B | §2.2.1 · §13.2 「전이표는 명시적 state/event table」 |
| ⑩ | **corpus** — `koneps-collection` case 를 runner 가 `canonicalize`·회계·식별자 정규화 위에서 대조(입력에 없는 값을 runner 가 만들지 않는다 — 1D 관례). authoritative 수는 D-M3-8 | M3 완료 조건 「fixture 기반 입력 전체가 명세대로 정규화」 |

**만들지 않는 것**: HTTP·재시도(3B) · DB write·upsert(3D) · LLM(3C) · 스케줄·lease · 감시/자격 소비 · 마감일시 `or` 폴백 사슬(COL-01 「채택하지 않는다」) ·
`"미상" = 0.0` 관례(COL-02) · 코드+라벨 한 셀의 도메인 유입(COL-08).

---

## 운영자 결정 필요 — 착수 전(D-3A-1~2) · 계약 고정(D-3A-3~6)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3A-1** | **canonical fact 의 aggregate 경계** — `Notice` 하나에 공고·자격 원문·예비가격·개찰 결과를 다 두는가 | (a) **`Notice`(공고 fact + 상태) · `OpeningResult`(개찰·예비가격·낙찰) · `QualificationText`(자격 원문) 셋을 `NoticeId` 로 묶는 별도 fact** (b) `Notice` 단일 aggregate | **(a)** — 셋은 출처(피드)·시점·수집 실패 단위가 다르고(COL-03 「공고 1건당 별도 호출」, COL-04 「표적조회 서브콜」), 단일 aggregate 면 멤버 30 게이트와 「부분 성공」 표현이 어긋난다. §2.1 aggregate 경계 넷과 대조해 착수 시 확정 | 착수 전 |
| **D-3A-2** | **정책 데이터 초기값의 출처** — 필드 계약 목록·resultCode 17·해석 순서 | (a) **legacy `field_contract_spec.py`·조달청 참고자료 §에러코드에서 curator 가 추출, `legacy-behavior`/`authoritative`(문서 출처) 층 표기, 운영자 승인** (b) 세션 모델이 직접 기입 | **(a)** — 값은 승인 대상(§5.3 「소비되는 모든 키에 필수」). 3A 는 형태·version 배관만 | 착수 전 |
| **D-3A-3** | 원문 보존 형태 — `RawNoticeObservation.fields: Map<RawKey, String>`(문자열 그대로)이고 타입 변환은 canonical 쪽에서만. `RawKey` 는 값 객체(문자열 키 dict 의 오타 무시 회귀 — legacy `base.py` 자인) | — | 계약 고정 |
| **D-3A-4** | 일시는 `Instant` + 원문 문자열 + 해석 규칙 id(정책) — 「타임존 없는 일시 문자열」은 KST 로 해석하되 그 해석이 provenance 에 남는다 | — | 계약 고정 |
| **D-3A-5** | 업무구분은 `BusinessCategory(code: CategoryCode, label: Label?)` 두 값 — 매핑 없는 코드는 `label = null`(미지), 임의 라벨 금지(COL-08) | — | 계약 고정 |
| **D-3A-6** | 회계의 `dropReasons` 어휘는 3A 소유 sealed(`MissingNoticeNumber`·`UnknownField`·`ContractViolation(scale/basis)`·`ParseFailure(kind)`) — OPS-09 실패 분류와 겹치지 않게 접두 | — | 계약 고정 |

---

## 위협 모델 — 3A 고유 경계

**방어한다**: (a) 파생값이 canonical 자리를 덮음(타입 ⑥ + `isAuthoritative` 데이터) (b) 미지 raw 키의 조용한 소비(④ 타입 — 계약 없는 키는 소비 함수에 못 들어간다) (c) 차수의 `int` 변환(③ 타입) (d) 단위 추측(⑧ — 계약 `scale` 없이는 변환 불가) (e) 회계 항등식 파괴(⑦ 생성자 불변식) (f) 상태 전이의 조용한 무시(⑨ 거부 관측) (g) 정책 데이터 리터럴의 main 유입(1C·1D 관례, 코드 리뷰).
**방어하지 않는다**: 어댑터가 원문을 **정직하게** 옮기는가(3B test) · DB write 의 점유 가드 실행(3D) · 정책 데이터 **내용**(승인) · KONEPS 자체의 필드 변경(④ 는 관측까지) · 스케줄·중복 실행(OPS-01/02).

**우회 후보(≥5)**: (1) `RawKey` 를 문자열로 만들어 계약 없는 키 소비 → 소비 함수 서명이 `KonepsFieldContract` 요구 (2) 회계를 `copy(dropped = …)` 로 조작 → 불변식 재검사(init) (3) `NoticeRound("0")` 와 `"000"` 을 같게 봄 → 값 객체 등가성은 원문 문자열 (4) `ResolvedBaseAmount.Direct` 를 예산 키 값으로 조립 → `Direct` 는 `Provenance.Published` 만 받는 생성자 (5) 전이표 밖 전이를 `copy(status=…)` 로 → `Notice` 상태 변경은 `transition(event)` 하나(1E `internal constructor` 관례) (6) 정책 해석 순서를 코드에 하드코딩 → `domainSourceReferenceGate` 는 못 잡음 — 리뷰 항목·정책 데이터 부재 시 구성 실패.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m3-prep/01_scout_collection.md`)

- 대기.

---

## OPEN — 수령·신설

| OPEN | 3A 처리 |
| --- | --- |
| `OPEN-COL-02`(확정) | resultCode 17 범주 = 정책 데이터(D-M3-4) — 3A 는 형태, 3B 가 소비 |
| `OPEN-COL-03` | `BusinessCategory` 두 값 + 미지 코드 표현까지. 전체 코드표는 활성 유지 |
| `OPEN-DIC-08`(닫힘) | 파생값은 계산 정책 version 만 — `DerivedFromOpening` 의 predecessor 참조는 `DecisionProvenance` 소유(1B 조정) |
| `OPEN-REG-05` | 기초금액·추정가격 과세 처리 — 3A 는 필드 계약의 `vatTreatment` 슬롯까지, 값은 그 OPEN |
| 신설 후보 `OPEN-3A-AGGREGATE` | D-3A-1 의 경계와 §2.1 aggregate 넷의 정합 — 착수 시 |
