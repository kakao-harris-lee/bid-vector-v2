# Slice 계약 — M3 / 3A · 수집 port 와 canonical fact — **착수 2026-09-07**

> **지위**: M2 진행 중에 세션 모델이 쓴 **계약 초안**. 구현·gradle·fixture 편집 없음. 착수는 M2 계약 승인 뒤 운영자 지시로 하며 그때
> `base_sha` 재고정(40자), `prep/m3-prep.md` D-M3-3·4·8 과 아래 D-3A-0~2 답 수령, `milestone-3.md` 착수 문단. 3A 의 도메인 코드는 M2 경로와
> 겹치지 않으나 **조건부 경로 둘(`config/quality/gate-tests.properties`·`app/**`)은 2A 가 편집한 파일이라 2A 머지 뒤 병합**(3B 와 같은 형태).
> **정책 데이터 분리·회계 항등식 불변식·필드 계약 타입을 세우는 slice 이므로 Phase 2.5 설계 검토 대상**이다 — 아래 「위협 모델」·「우회 후보」
> 절은 저작 레인이 쓴 **검토 입력**이지 검토 결과가 아니다.

```yaml
milestone: m3
slice: 3a-collection-ports-and-canonical-facts
base_sha: a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f   # 착수 2026-09-07 재고정 = M2 완료·push 시점. 초안 시점은 040ab9d
head_sha: 리뷰 시점의 HEAD
in_scope:
  - procurement/**                                    # 도메인: canonical fact 타입·수집 command·port 인터페이스 셋·식별자 값 객체·필드 계약 레지스트리 타입·수집 회계·provenance 판정 지점·정책 데이터 형태·test
  - shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt   # D-3A-0 (a) 승인 — 신설: 제로패딩 문자열 값 객체(정규화 규칙 소유, 산술 미정의, 등가성은 원문 문자열)
  - shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt     # D-3A-0 (a) — `Published(noticeRevision: NoticeRound)` 타입 교체(좁은 확장, 1D D-1 선례)
  - shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt           # D-3A-0 (a) — `FloorRateOrigin.NoticeValue(noticeRevision)` 도 같은 타입(조사 G-1: 같은 사실이 두 자리) — 이 파일의 다른 편집 금지
  - shared-kernel/src/test/kotlin/**                   # 위 타입 교체의 영향 test 만(값 불변, 생성자 인자 형태만)
  - decision/src/test/kotlin/**                        # 같은 타입 교체의 영향 test 만(`FloorShortfallKernelTest` 의 `NoticeValue(0)` 4곳 → `NoticeRound`, 인자 형태만·값 불변) — 구현 레인 발견으로 착수 뒤 추가(세션 모델 정정). `decision/src/main/**` 은 여전히 out_of_scope
  - config/quality/gate-tests.properties              # `gate.tests.procurement`(M2 종결로 조건 해제 — 2A·2D 가 만든 키에 병합)
  - app/src/test/kotlin/bidvector/app/conformance/**   # (i) 기존 executor 의 `noticeRevision` `toInt` 접힘을 `NoticeRound` 로 정정(D-3A-0 파급 — `CorpusExecutors`·`StrategyExecutors`) (ii) `koneps-collection` dispatch 는 curator 승격 뒤 병합(authoritative ≥ 1 이 조건)
  - app/build.gradle.kts                              # testImplementation(project(":procurement")) 한 줄
  - reports/evidence/m3/3a/policy-values.md           # **curator 레인 산출**(별도 세션, D-3A-2 (a)) — 필드 계약 목록·resultCode 17 범주·해석 순서의 승인 표(층 표기·출처). 3A 구현 레인은 읽기만
  - milestone-3.md                                    # 「Slice 3A」 착수 문단, 착수 시
  - reports/evidence/m3/3a/**
out_of_scope:
  - adapters/**                                       # HTTP·파싱·DB 는 3B·3D. 3A 는 port 와 타입만
  - shared-kernel/** 의 위 넷 밖                        # 1B·1D 승인 산출물 — 필요한 carrier 부재 시 멈추고 보고
  - fixtures/manifest.yaml, fixtures/input/**, fixtures/expected/**   # **curator 레인(별도 세션) 소유** — D-M3-8 (a) 승격·신설(완료·승인 `5acd5f0`). 3A 구현 레인은 case 내용을 편집하지 않는다. **예외(잔여 일괄, 2026-09-07 세션 모델)**: `koneps-collection` case 의 `contract_binding` 필드만 3A 가 채운다(`pending-3a` → 실제 타입·executor, 1D ft-002 관례) — 그 밖의 필드·해시·기대값 무편집
  - 감시·자격·판정 소비                                 # M4 4B
  - 공고 상태 전이표의 실행(§2.2.1)                       # `NoticeStatus` 값·전이 test 는 3A 가 두되 **이벤트를 만드는 use case** 는 4B
  - 브라우저 크롤(COL-09)·mock 데이터(COL-10)·수집 스케줄·lease(OPS-01/02)
  - config/quality/api-type-policy.properties         # 허용 목록이 없는 파일(금지 타입 목록뿐) — 새 값 타입에 편집 불요, 편집 = 게이트 완화
  - decision/** 의 BaseAmountProvenance 판정(first-match 커널) # 1D 소유. 3A 는 부르지도 복제하지도 않는다(⑤) — 두 축의 교차는 M4 workflow
  - M2 경로 일체, capability-map.md·data-dictionary.md 편집   # 필요한 개정 사실(COL-06 셈·§5.1 차수 타입)은 OPEN 으로 **등재만**, 개정은 별도 문서 slice·운영자 승인
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

`git log --oneline a9f1ff9..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-07) **없음**.

**착수 2026-09-07 — 운영자 결정**: D-M3-1~8 전부 추천안(`prep/m3-prep.md` §3) · D-3A-0 (a) · D-3A-1 (a) · D-3A-2 (a) · 착수 순서 「curator 선행(별도
세션) → 3A 계약 확정 → Phase 2.5(세션 모델 직접, `_workspace/m3-3a/01_design-review.md`) → 3A 구현」, 3A 도메인 코드는 fixture 승인 전에
시작하되 runner dispatch 는 승인 뒤 병합. **계약 정정 둘(착수 시, 세션 모델)**: ① 정책 데이터의 자리는 리소스 파일이 아니라 **Kotlin
`EffectiveDatedPolicy` 인스턴스**(1C `LICENSE_QUALIFICATION_POLICY`·1D `ProvenancePolicyData` 관례 — `source` 에 출처·승인일) — 3A 는
형태 + test 정책 인스턴스까지, 운영 값 인스턴스는 curator 승인 표(`policy-values.md`) 수령 뒤 **한 커밋**(3A 잔여 일괄 또는 3B 착수 전).
corpus 9 case 는 입력 안에 `fieldContract.registeredKeys` 를 들고 있어 정책 값에 의존하지 않는다. ② D-3A-0 의 좁은 확장은 `Published`
하나가 아니라 **`FloorRateOrigin.NoticeValue(noticeRevision)`(조사 G-1)과 app conformance executor 의 `toInt` 접힘**(fixture 15건이 이미
`"noticeRevision": "000"` 문자열)까지다 — 그 접힘이 정확히 R-QUAL-05 형태라 3A 가 함께 정정한다.

**병렬 레인 경계(착수 시, 공유 working tree)**: curator 레인 = `fixtures/**` + `reports/evidence/m3/3a/policy-values.md` + `fixtures-*.md` evidence ·
문서 레인 = `docs/discovery/**`·`reports/evidence/m3/{3c,3d}/**` · 하네스 레인 = `build-logic/**`·`contracts/tools/**`·`config/quality/**` **단
`gate-tests.properties` 제외**(3A in_scope). 3A 구현 레인은 그 경로들을 편집하지 않고, 각 레인은 자기 경로만 개별 `git add` 후 즉시 커밋.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **port 셋** — `NoticeSourcePort`·`OpeningResultSourcePort`·`DocumentSourcePort` 를 `procurement` 안에 **도메인 소유 인터페이스**로. 입력은 조회 조건 값 객체(기준일 = KST 캘린더 일자 타입, 페이지 커서), 출력은 `SourceBatch<Observation>`(원문 항목 + 회계). 어댑터(3B)가 구현 | 3A 「port」 · ADR 0006 **D-2** 표(「`procurement` — KONEPS 공고·개찰 canonical facts 와 수집 port」) · ADR 0005 D-10.1 방향 점검(「domain 이 의존하는 port 는 domain 안에 선다」) · COL-01 「KST 기준일」 |
| ② | **외부 DTO 와 command 의 분리** — `RawNoticeObservation`(원문 키→문자열 값 그대로, `observedAt`, 출처) ↔ `NoticeCollected` command(canonical 값 + provenance). 변환은 **한 함수**(`canonicalize`)이고 원문은 버리지 않는다(감사) | 3A 「외부 DTO 와 domain command 분리」·「원문 field, canonical value, provenance, observed-at 보존」 |
| ③ | **식별자 값 객체** — `NoticeId(number: NoticeNumber, round: NoticeRound)`. `NoticeRound` 는 **제로패딩 문자열 식별자**(`"000"` 보존, `int` 변환 금지 — R-QUAL-05). 정규화 규칙은 값 객체가 소유(호출부 반복 금지 — COL-05). `source_url` 정규화 불변식(같은 url ⇒ 같은 id)은 property test | COL-01 acceptance 「제로패딩 차수 원문 보존」 · COL-05 「식별자 값 객체가 정규화 규칙을 소유」 · §5.3 「식별자는 숫자가 아니다」 |
| ④ | **필드 계약 레지스트리(타입 + 정책 데이터)** — `KonepsFieldContract(rawName, concept, basis, scale, unit, nullability, vatTreatment, authoritative, presentIn, provenance, effectiveFrom, expectedRange)`. **등재되지 않은 raw 키는 canonical 값으로 소비될 수 없다**(타입: 소비 함수가 계약을 인자로 요구) — 미지 키는 회계 `unknownFields` 로 계수. `scale`·`basis` 어긋남(fraction 자리에 백분율)은 항목 거부 + 사유. **`expectedRange` 는 밴드를 재선언하지 않고 단일 출처(밴드 정책 데이터 — DTO 게이트와 판정이 같은 값을 읽는 자리)를 참조**한다 | §5.3(규율 1·**규율 2 「계약이 밴드를 재선언하지 않고 단일 출처를 참조한다」**) · COL-07 acceptance 둘 · D-M3-3 |
| ⑤ | **`Provenance` 해석 지점 하나(수집 경로 축만)** — 기초금액·추정가격 해석 순서는 정책 데이터(§5.2 「해석 순서는 정책 데이터」, 추정가격 순서에 기초금액 키 없음), `0`·미상 후보는 건너뜀, KONEPS 수집 경로의 `Provenance` variant(`Published`·`DerivedFromOpening`·`FilledFromBudgetKey`·`CopiedFromBaseAmount`·`Undeclared`) 전부를 **한 함수**가 낸다. 검증기도 같은 함수를 호출. **`BaseAmountProvenance` 라벨(판정 축)은 3A 가 내지 않는다** — 판정 커널은 `decision`(1D) 소유이고 `procurement` 는 같은 층의 `decision` 을 볼 수 없다(ADR 0006 D-4 — 공유 가능한 domain 모듈은 `shared-kernel` 뿐). 두 축의 교차(수집 fact → 판정 입력)는 `workflow`(M4 4B)가 조합 | §5.1 「KONEPS 수집 경로의 variant 전부를 같은 판정 지점이 낸다」 · §4.3 · §5.2 「해석을 한 지점에」 · COL-02 경계 「판정은 DEC-08 소유」 · ADR 0006 D-4 · M2 D-M2-10(두 축을 한 enum 에 접지 않는다) |
| ⑥ | **파생이 원본을 덮지 않는다 — 타입** — `ResolvedBaseAmount = sealed { Direct(Published), FallbackFromBudget(sourceKey), DerivedFromOpening }`. 개찰 행의 `sucsfbidRate`(사정률)는 기초금액을 배출하지 않고 예정가를 **파생 필드 + provenance** 로. 점유 가드(권위값만 덮음)는 3D 의 write 규칙이고 3A 는 **「덮어도 되는가」를 `isAuthoritative` 데이터**로 선언 | COL-02 acceptance 둘 · §5.1 규율 1·2 · §5.2 「자리가 같아야 한다면 타입이 달라야 한다」 · ADR 0004 D-5 |
| ⑦ | **수집 회계** — `CollectionAccounting(received, normalized, duplicate, dropped, dropReasons, sourceTotal, pagesFetched, truncated, unknownFields)`, **`received = normalized + duplicate + dropped` 항등식은 생성자 불변식**(위반 = 생성 실패). `dropReasons` 는 sealed 코드. 뺄셈 역산·`setdefault` 채움 없음. **셈의 정의**: COL-06 문면의 항등식은 `duplicate` 와 `dropped` 가 **서로소**인 셈이고 V2 는 그 문면을 그대로 채택한다(`dropReasons` 에 `Duplicate` 없음). legacy 의 `dropped_count` 는 중복을 포함해 **문면과 다르다**(조사) — 정정 대상은 문면이 아니라 legacy 형태이며, 그 차이를 계약 주석과 property test 로 고정한다 | COL-06 acceptance 「항등식 위반 시 산출 실패」 · legacy 형태 처리 · 조사 요약(legacy 중복 계수) |
| ⑧ | **금액·율·일시 canonical** — 콤마 금액 문자열·백분율 문자열(`"87.995"`)·타임존 없는 일시는 **어댑터가 원문 unit 을 기록하며 변환**하고 3A 는 `Money`(1B)·`Rate`(fraction)·`Instant`+KST 일자 타입만 받는다. 값 크기로 단위 추측 금지(ADR 0002 D-4) — percent/fraction 은 필드 계약의 `scale` 이 정한다 | §4.1 · COL-01 골든 형태 · R-RATE-01 |
| ⑨ | **`NoticeStatus` 와 전이표(값·거부만)** — `Open·Renoticed·Closed·Awarded·Failed·Cancelled`, 표에 없는 쌍은 거부이며 관측 가능. `isBiddable(notice, now)` 파생 술어. 이벤트를 만드는 use case 는 4B | §2.2.1 · §13.2 「전이표는 명시적 state/event table」 |
| ⑩ | **corpus** — `koneps-collection` case 를 runner 가 `canonicalize`·회계·식별자 정규화 위에서 대조(입력에 없는 값을 runner 가 만들지 않는다 — 1D 관례). authoritative 수는 D-M3-8 | M3 완료 조건 「fixture 기반 입력 전체가 명세대로 정규화」 |
| ⑪ | **조회 가치 술어(COL-03 의 도메인 판단)** — `DetailFetchDecision = sealed { Fetch, Skip(reason ∈ { AlreadyHeld, AgeGateNotPassed(until), RecheckGateNotPassed(until) }) }` 를 `procurement` 의 **순수 함수**로: 예비가격이 이미 저장된 공고는 `AlreadyHeld`, 개찰 후 age-gate 미만은 `AgeGateNotPassed`, gate 를 넘긴 뒤는 `Fetch` **정확히 1회**(recheck-gate 가 다음 창을 정함). 값(24h/48h)은 정책 데이터. 3B 는 이 술어의 결과를 **실행**하고 `Skip` 을 회계 `backoffSkipped` 로 계수. 백오프 상태를 데이터 테이블 컬럼에 얹지 않는다(COL-03 legacy 형태 처리 — 상태는 관측 시각에서 파생) | COL-03 경계 「무엇을 언제 조회할 가치가 있는가라는 도메인 판단만 소유」 · acceptance 셋 · 3B ⑧ |

**만들지 않는 것**: HTTP·재시도(3B) · DB write·upsert(3D) · LLM(3C) · 스케줄·lease · 감시/자격 소비 · **`BaseAmountProvenance` 판정**(`decision` 1D 소유 — 3A 는 `Provenance` 해석까지) ·
마감일시 `or` 폴백 사슬(COL-01 「채택하지 않는다」) · `"미상" = 0.0` 관례(COL-02) · 코드+라벨 한 셀의 도메인 유입(COL-08) · capability-map·data-dictionary 개정(OPEN 등재만).

---

## 운영자 결정 필요 — 착수 전(D-3A-0~2) · 계약 고정(D-3A-3~6)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3A-0** ✅ (a) 승인 2026-09-07 | **`Provenance.Published(noticeRevision: Int)` 의 정정** — shared-kernel(1B)의 `Published` 가 차수를 `Int` 로 나른다. M1 은 값을 주장하지 않아 비껴갔지만 3A 는 차수를 표적조회 필수 입력(`bidNtceOrd`, 제로패딩 `"000"`)으로 쓴다 — `Int` 면 `R-QUAL-05`(1차 공고 전부 자격 상실) 재현 경로(조사 요약 11) | (a) **shared-kernel 좁은 확장**(1D D-1 (a) 선례): `Published(noticeRevision: NoticeRound)` 로 타입 교체, `NoticeRound` 는 제로패딩 문자열 값 객체를 shared-kernel 에 신설 (b) 3A 가 `procurement` 안에서 `NoticeRound` 를 두고 `Published` 는 건드리지 않음 — 두 값이 두 자리 (c) `Published(noticeRevision: String)` | **(a)** — 같은 사실이 두 타입으로 있으면(b) 변환 자리에서 `int` 회귀가 되살아난다. (c) 는 정규화 규칙 없는 문자열. **대가**: 승인 산출물(1B) 편집 → 계약 갱신 + 운영자 승인; `data-dictionary.md` §5.1 문면(`Published(noticeRevision)`)의 타입 갱신은 **별도 문서 개정**(3A out_of_scope — 여기서는 `OPEN-3A-NOTICE-ROUND` 로 등재만). 1B 코퍼스의 `Published` 사용 case 는 값 변경 없이 타입만 | 착수 전 |
| **D-3A-1** ✅ (a) 승인 2026-09-07 | **canonical fact 의 aggregate 경계** — `Notice` 하나에 공고·자격 원문·예비가격·개찰 결과를 다 두는가 | (a) **`Notice`(공고 fact + 상태) · `OpeningResult`(개찰·예비가격·낙찰) · `QualificationText`(자격 원문) 셋을 `NoticeId` 로 묶는 별도 fact** (b) `Notice` 단일 aggregate | **(a)** — 셋은 출처(피드)·시점·수집 실패 단위가 다르고(COL-03 「공고 1건당 별도 호출」, COL-04 「표적조회 서브콜」), 단일 aggregate 면 멤버 30 게이트와 「부분 성공」 표현이 어긋난다. §2.1 aggregate 경계 넷과 대조해 착수 시 확정 | 착수 전 |
| **D-3A-2** ✅ (a) 승인 2026-09-07 — curator 는 별도 세션 | **정책 데이터 초기값의 출처** — 필드 계약 목록·resultCode 17·해석 순서 | (a) **legacy `field_contract_spec.py`·조달청 참고자료 §에러코드에서 curator 가 추출, `legacy-behavior`/`authoritative`(문서 출처) 층 표기, 운영자 승인** (b) 세션 모델이 직접 기입 | **(a)** — 값은 승인 대상(§5.3 「소비되는 모든 키에 필수」). 3A 는 형태·version 배관만 | 착수 전 |
| **D-3A-3** | 원문 보존 형태 — `RawNoticeObservation.fields: Map<RawKey, String>`(문자열 그대로)이고 타입 변환은 canonical 쪽에서만. `RawKey` 는 값 객체(문자열 키 dict 의 오타 무시 회귀 — legacy `base.py` 자인) | — | 계약 고정 |
| **D-3A-4** | 일시는 `Instant` + 원문 문자열 + 해석 규칙 id(정책). **착수 보강(조사 G-7)**: §5.3 계약에 시각 축 슬롯 `sourceZone` 을 더해 「타임존 없는 문자열의 출처 zone」이 필드 계약에서 읽히게 한다(값 크기·형태로 추측 금지와 같은 갈래) — 「타임존 없는 일시 문자열」은 KST 로 해석하되 그 해석이 provenance 에 남는다. **조사 관측(미확정, ledger 미등재)**: legacy 는 KONEPS 벽시계 문자열을 **UTC 로 파싱**하는데 원문은 한국 현지시각으로 보여 저장 마감이 9시간 밀렸을 수 있다 — 공식 문서로 출처 타임존을 확인 못 함. V2 는 해석 규칙을 **정책 데이터**(`KST` 초기값, 출처 문서 확인 뒤 승인)로 두고 원문을 보존하므로 규칙이 틀려도 재해석 가능. 착수 시 `OPEN-3A-SOURCE-TZ` 등재 | — | 계약 고정 |
| **D-3A-5** | 업무구분은 `BusinessCategory(code: CategoryCode, label: Label?)` 두 값 — 매핑 없는 코드는 `label = null`(미지), 임의 라벨 금지(COL-08) | — | 계약 고정 |
| **D-3A-6** | 회계의 `dropReasons` 어휘는 3A 소유 sealed(`MissingNoticeNumber`·`UnknownField`·`ContractViolation(scale/basis)`·`ParseFailure(kind)`) — OPS-09 실패 분류와 겹치지 않게 접두. **소스 중립**(조사 G-5: legacy 는 공고 경로 `parse_rejected`·개찰 경로 `missing_notice_number` 로 갈렸다) | — | 계약 고정 |
| **D-3A-7** | **Basis 토큰의 정본은 코드**(`Basis.ESTIMATED` 등, 조사 G-2) — fixture 문자열 `ESTIMATED_PRICE` 는 curator 승격 시 코드 토큰으로 정합(기대값 의미 불변), runner 는 매핑하지 않는다 | — | 계약 고정(curator 브리프) |
| **D-3A-8** | **§5.5 시공능력 수집 필드**(`cnstrtnAbltyEvlAmtList`, 조사 G-9)는 3A 가 `UnnormalizedFigure` + 필드 계약으로 **수집 형태만** 둔다 — 단위·과세 정규화는 `OPEN-QUAL-10` 소유 | — | 계약 고정 |

---

## 위협 모델 — 3A 고유 경계

**방어한다**: (a) 파생값이 canonical 자리를 덮음(타입 ⑥ + `isAuthoritative` 데이터) (b) 미지 raw 키의 조용한 소비(④ 타입 — 계약 없는 키는 소비 함수에 못 들어간다) (c) 차수의 `int` 변환(③ 타입) (d) 단위 추측(⑧ — 계약 `scale` 없이는 변환 불가) (e) 회계 항등식 파괴(⑦ 생성자 불변식) (f) 상태 전이의 조용한 무시(⑨ 거부 관측) (g) 정책 데이터 리터럴의 main 유입(1C·1D 관례, 코드 리뷰) (h) 조회 가치 술어(⑪)의 우회 — 상세 조회 port 의 서명이 `DetailFetchDecision.Fetch` 값을 인자로 요구해(타입 증거) 술어를 거치지 않은 호출이 컴파일되지 않는다.
**방어하지 않는다**: 어댑터가 원문을 **정직하게** 옮기는가(3B test) · DB write 의 점유 가드 실행(3D) · 정책 데이터 **내용**(승인) · KONEPS 자체의 필드 변경(④ 는 관측까지) · 스케줄·중복 실행(OPS-01/02).

**우회 후보(≥5)**: (1) `RawKey` 를 문자열로 만들어 계약 없는 키 소비 → 소비 함수 서명이 `KonepsFieldContract` 요구 (2) 회계를 `copy(dropped = …)` 로 조작 → 불변식 재검사(init) (3) `NoticeRound("0")` 와 `"000"` 을 같게 봄 → 값 객체 등가성은 원문 문자열 (4) `ResolvedBaseAmount.Direct` 를 예산 키 값으로 조립 → `Direct` 는 `Provenance.Published` 만 받는 생성자 (5) 전이표 밖 전이를 `copy(status=…)` 로 → `Notice` 상태 변경은 `transition(event)` 하나(1E 관례 — `@ConsistentCopyVisibility` + `internal constructor` **조합**이 `copy()` 를 닫는다) (6) 정책 해석 순서를 코드에 하드코딩 → `domainSourceReferenceGate` 는 못 잡음 — 리뷰 항목·정책 데이터 부재 시 구성 실패 (7) 어댑터가 ⑪ 을 건너뛰고 상세 조회를 부름 → port 서명이 `Fetch` 증거를 요구(방어 (h)) + COL-03 acceptance test(gate 미만 공고의 상세 호출 0회, gate 뒤 정확히 1회) (8) `DetailFetchDecision.Fetch` 를 어댑터가 직접 조립 → `internal constructor`(술어 함수만 생성).

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m3-prep/01_scout_collection.md` (b)·(g)·(h))

- **9 case 전부 `insufficient-evidence`**, 회계·타임존 case 없음 → ⑩ 의 corpus 는 D-M3-8 (a) 신설이 선행.
- **`Published(noticeRevision: Int)`** → D-3A-0. 차수는 표적조회 필수 입력이라 3A 가 값을 실제로 나른다.
- parse 실패가 `None → 0.0` 으로 접힘 · 단위를 값 크기로 판별 · `resultCode` 부재가 `"00"` → ④(계약 `scale`/`unit` 없이는 변환 불가)·⑥·⑦ 의 근거.
- 점유 가드가 `budget_estimate` 한 축에만 → ⑥ `isAuthoritative` 데이터는 **전 금액 축**에.
- raw payload 미저장 → ② 의 `RawNoticeObservation` 보존은 신설(감사 요구).
- `OPEN-COL-*` 넷 중 3A 를 막는 것 없음(조사 (h)) — `OPEN-COL-03` 은 미지 코드 표현으로 비껴간다.

---

## OPEN — 수령·신설

| OPEN | 3A 처리 |
| --- | --- |
| `OPEN-COL-02`(확정) | resultCode 17 범주 = 정책 데이터(D-M3-4) — 3A 는 형태, 3B 가 소비 |
| `OPEN-COL-03` | `BusinessCategory` 두 값 + 미지 코드 표현까지. 전체 코드표는 활성 유지 |
| `OPEN-DIC-08`(닫힘) | 파생값은 계산 정책 version 만 — `DerivedFromOpening` 의 predecessor 참조는 `DecisionProvenance` 소유(1B 조정) |
| `OPEN-REG-05` | 기초금액·추정가격 과세 처리 — 3A 는 필드 계약의 `vatTreatment` 슬롯까지, 값은 그 OPEN |
| 신설 `OPEN-3A-AGGREGATE` | D-3A-1 (a) 의 **수집 fact 셋**(`Notice`·`OpeningResult`·`QualificationText`, `NoticeId` 로 묶임)과 §2.1 aggregate 넷의 정합 — §2.1 은 `Notice` 가 자격 원문을 **소유**한다고 적는다. 3A 의 셋은 **수집 단위**(출처·시점·실패 단위)이지 소유권 재배정이 아니다: aggregate 조립은 3D·4B. 문면 정합은 문서 레인이 등재(`capability-map.md` §14.3) |
| 신설 후보 `OPEN-3A-SOURCE-TZ` | KONEPS 일시 문자열의 출처 타임존(조사: legacy 는 UTC 파싱, 원문은 KST 로 보임 — 9시간 어긋남 가능, 운영 피해 기록 없음) — 공식 문서 확인 뒤 정책값 승인. regression-ledger 등재 후보 |
| 신설 후보 `OPEN-3A-NOTICE-ROUND` | D-3A-0 채택 시 `data-dictionary.md` §5.1 `Published(noticeRevision)` 타입 문면 개정(별도 문서 slice·운영자 승인) — 3A 는 등재만 |
