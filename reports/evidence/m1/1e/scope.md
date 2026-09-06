# Slice 계약 — M1 / 1E · Strategy 와 상태(감시 조건 순수 predicate · 전략 값 validation · 최소 sealed state/event) — **초안, 구현 전**

> **지위**: 1D 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·fixture 편집은 하지 않았다.
> 착수는 1D 종결 뒤 운영자 별도 지시로 하며, 그때 `base_sha` 를 재고정하고 「착수 전 결정」 표의
> 답을 받은 뒤 `milestone-1.md` 「Slice 1E」 착수 문단을 쓴다(그 항목은 지금 1D 세션이 소유 중이라
> 이 초안이 건드리지 않는다).

```yaml
milestone: m1
slice: 1e-strategy-and-state
base_sha: 2af32f6   # 착수 2026-09-06 재고정(1D 종결 승인 커밋). 초안 시점은 4a4d1aa 였다
head_sha: 리뷰 시점의 HEAD
in_scope:
  - strategy/**                                # 도메인 모듈(ADR 0006 D-2 「운영자 감시 조건과 전략」; STR-01·02·03·06 소유). 감시 predicate·validation·결과/이벤트 타입·정책 데이터 형태·test
  - config/quality/api-type-policy.properties  # 조건부 — 도메인 API 타입 허용 목록에 1E 값 타입 등재가 필요할 때만(게이트 정의 편집이므로 사유를 evidence 에)
  - config/quality/gate-tests.properties       # 조건부 — 1E 게이트 성격 test(불변식·컴파일 fixture) 등재 시 gate.tests.strategy 키
  - app/src/test/kotlin/bidvector/app/conformance/**   # 조건부 — D-2·D-3 로 1E 축 authoritative 가 생길 때 runner dispatch 확장(1B-c·1C·1D 관례)
  - app/build.gradle.kts                       # 조건부 — testImplementation(project(":strategy")) 한 줄
  - fixtures/manifest.yaml                     # 조건부 — D-2 승인 시 money-basis-003 의 contract_binding·승격, D-3 승인 시 strategy 축 case 신설(fixture-curator 소관, 기존 기대값 무변경)
  - fixtures/input/**, fixtures/expected/**    # 조건부 — D-3 승인 시 **신설 case 파일만**(authored-from-approved-spec). 기존 파일 무접촉
  - docs/discovery/capability-map.md           # 계약 갱신 — §14.2·§14.3 의 `OPEN-1BC-STR16`·`OPEN-STR-*` 행과 STR-16 신설 승인 기록만
  - docs/discovery/data-dictionary.md          # 계약 갱신 — 운영자 결정이 실제로 난 행만(D-4 (a) 시 §2.2 에 전략 어휘 자리 한 절)
  - milestone-1.md                             # 계약 갱신 — 「Slice 1E」 항목만, **착수 시**
  - reports/evidence/m1/1e/**
out_of_scope:
  - shared-kernel/**                           # 1B·1D 승인 산출물. 1E 가 필요로 하는 carrier 가 없으면 멈추고 보고(D-1 (b) 채택 시에만 조건부로 열린다)
  - 텍스트 정규화·토큰화·형태소 분석              # 커널은 어댑터가 이미 두 갈래로 분리해 준 텍스트(D-6) 위에서 부분문자열 매칭만 한다. 정규화 규칙은 M3 어댑터
  - 공고 상태 필터(`isBiddable`)                 # `Notice` aggregate 의 파생 술어(§2.2.1) — procurement/M3. 1E 입력은 이미 걸러진 공고 사실
  - 임계치의 **소비**(BidNow/Review/Skip 사다리)  # `Verdict`(§3.6)·`OPEN-DIC-03` — M4 4B. 1E 는 임계치 값의 validation 만
  - STR-04 알림 범위(배달 정책) · STR-05 카테고리 가중(`후속`) · STR-06 의 분석 예산(운영 설정) · STR-07 스냅샷 재계산 디스패치 · STR-08 run 이력 · STR-09 스케줄 · STR-10 신선도 표시   # M4 4A~4C·4E
  - 전략 편집 상태 기계(`WaitingForValue → WaitingForConfirmation → Applied/Cancelled/Expired`)   # M4 4A. 1E 의 「최소 sealed state/event」는 D-4 가 경계를 긋는다
  - STR-11·STR-12 대화형 채널 편집 · STR-13~15 튜닝·자동 갱신   # `OPEN-STR-12`·`OPEN-STR-04`, M4 4A
  - 전략의 저장·조회·per-operator 스키마          # M3 3D. `OPEN-STR-07` 하류 주의(조용한 소유권 재할당 금지)는 타입 경계로만 받는다
  - STR-16 검색 **API**(엔드포인트·페이지네이션)  # M3. 1E 가 받는 것은 검색 경로가 감시 경로와 **같은 predicate 를 쓴다는 타입**뿐(D-2)
  - force-bid 우회의 출처 노출(`OPEN-STR-03`)      # verdict 결과의 성분 — M4 4B
  - fixture 기존 기대값·입력 편집                  # 승격·신설은 curator 조건부·운영자 승인 하에서만. 어긋나면 멈추고 보고
  - Python ML · bid-vector/ symlink(읽기 전용) · _workspace/**
  - 승인 문서 편집 일체(위 승격 행·항목 밖)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — 1A·1A-b 게이트 전건 + CPD main fail 위에서 초록
  - "./gradlew :strategy:test"                                                                          # S-2 — 1E 도메인 test(property 포함)
  - "./gradlew :strategy:domainApiTypeGate :strategy:domainSourceReferenceGate :strategy:typeShapeGate :strategy:sizeGate :strategy:cpdCheck"   # S-3 — 게이트 단독
  - "./gradlew :app:test --tests '*Conformance*'"                                                      # S-4 — 조건부(D-2·D-3 로 1E 축 authoritative ≥ 1 일 때) — runner 로 실행·대조
  - "./gradlew qualityBaseline"                                                                        # S-5
  - "python3 fixtures/tools/mutation_sweep_adversarial.py"                                             # S-6 — 조건부(manifest 편집 시). 종료 코드 규약(0 정상 · 1 위반 변이체 통과 · 2 형식 오류)은 `data-extract.md` §6
  - "./gradlew :build-logic:test"                                                                     # S-7 — 1A 승계
rollback: |
    **정본은 `reports/evidence/m1/1e/rollback.md`**(착수 시 작성 — scope.md 파생, 경로 한정 restore + 신규 경로 rm, 임시 clone 실측).
    strategy 모듈은 자리표시자(anchor)로 돌아간다. 조건부 경로는 채택된 것만 되돌린다.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독(CLAUDE.md 운영자 지시 2026-09-04 — 기획 파이프라인 없음). 근거:
브리프 3(`_workspace/briefs/2026-09-06-parallel-briefs.md`), `milestone-1.md` 「Slice 1E」, 1B(값 타입·`compareKnownVat`)·
1B-c(corpus 실행자·술어)·1C(정책 배관 관례)·1D(shared-kernel 좁은 확장 선례) 산출물, `_workspace/m1-1e/01_scout_preflight.md`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 2af32f6..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-06) **없음**.

**착수 2026-09-06 — 운영자 결정(착수 전 D-1~D-4 전부 추천안)**: D-1 (a) strategy 안 `Score` + 축별 뉴타입(`OPEN-1E-SCORE` 등재) ·
D-2 (a) `OPEN-1BC-STR16` 수령 + 전제 승인 둘(R-BASIS-01 「같은 쌍이 감시·검색 경로에서 같은 답」 업무 규칙 · STR-16 capability
승인) = **decision 29** · D-3 (a) STR-01 acceptance 넷 · STR-02 넷 · STR-03 셋을 업무 규칙으로 명시 승인 + 1E 안에서 case 신설 =
**decision 30** · D-4 (a) 결과 어휘 + 이벤트 payload 까지, 전이·상태 기계 없음, `data-dictionary.md` §2.2.6 어휘 자리 = **decision 31**.
정본: `capability-map.md` STR-01·02·03·16 블록 승인 주석 + §14.2 `OPEN-1BC-STR16` 행 · `data-dictionary.md` §2.2.6.

---

## 이 slice 가 하는 일

`milestone-1.md` 「Slice 1E」 세 문장을 **커널 하나(감시 predicate) + validation 하나 + 어휘 타입**으로 낸다
(`v2-지침서.md` §4.5 · `capability-map.md` STR-01·02·03·06·16 · `data-dictionary.md` §1.1·§1.3·§2.1 · `regression-ledger.md`
R-BASIS-01·02).

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **감시 predicate** — 운영자 감시 필드 일곱(중점 카테고리·중점 지역·제외 지역·필수 키워드·제외 키워드·최소/최대 예산)을 **한 순수 함수**로 판정한다. 카테고리는 **완전일치 집합 멤버십**, 지역·키워드는 **부분문자열 OR**, 제외는 하나라도 걸리면 탈락. DB·시계·ML port 접근 0(「watch rule 판정은 ML port 를 0회 호출한다」는 모듈 의존 게이트가 구조로 보장 — strategy 는 shared-kernel 만 참조) | STR-01 분류 근거·acceptance |
| ② | **텍스트 범위를 타입으로** — 키워드 규칙은 `제목+요건+카테고리` 텍스트만, 지역 규칙은 `description` 을 포함한 전체 텍스트를 본다. 두 텍스트를 **서로 다른 타입**으로 받아 규칙의 서명이 어느 텍스트를 보는지 강제한다(호출 규율 두 헬퍼 형태 미채택) | STR-02 legacy 형태 처리·acceptance 넷 |
| ③ | **결과는 boolean 이 아니다** — `WatchVerdict = sealed { Passed(matched), Rejected(failed), NoGate, Undeterminable(reason) }`. 「게이트 없음」(감시 필드 전부 비어 있음)과 「모든 공고 통과」가 **결과 타입에서** 갈린다. 판정에 필요한 공고 사실이 `Fact.Absent` 이거나 과세 처리가 달라 비교 불가면 통과도 탈락도 아닌 `Undeterminable` | STR-01 acceptance 셋째 · §1.1·§1.3 부재 1급 · §1.2.1 |
| ④ | **예산 basis 는 타입이 나른다** — 예산 상·하한은 `BaseAmount`(basis `BASE_AMOUNT`, provenance `OperatorDeclared`, 과세 처리는 기초금액 정의 그대로 `INCLUSIVE` — U-1b)이고 비교는 1B `compareKnownVat(BaseAmount, BaseAmount)` 하나다. 추정가격과의 비교는 **컴파일에서 막힌다**. 「0 = 무제한」 sentinel 은 채택하지 않는다 — 한계의 부재는 「규칙 없음」이고 `0원` 은 실제 한계다 | `OPEN-STR-01` 해소 「(c) + 기초금액」 · R-BASIS-01 V2 예방 제약 · STR-01 legacy 형태 처리 |
| ⑤ | **전략 값 validation** — `StrategyValidation = sealed { Valid(strategy, policyVersion), Invalid(violations, policyVersion) }`. 위반은 구조화 코드(`ReviewAboveBidNow`·`MinBudgetAboveMaxBudget`·`ScoreOutOfRange(field)`·`CandidateLimitNotPositive`·`BlankTerm(field)` …)이고 문장이 아니다. `OperatorStrategy` 는 **validation 을 거쳐서만** 생긴다(`internal constructor`) — 편집 경로마다 불변식을 재구현하는 legacy 형태(3곳 중복) 폐기 | STR-03 분류 근거·legacy 형태 처리·acceptance 첫·둘째 · `v2-지침서.md` §4.2 어휘 관례(3값 접힘 금지) |
| ⑥ | **「설정됐는가」와 「감시 범위를 좁히는가」는 다른 물음** — `OperatorStrategy` 위의 두 술어가 서로 다른 값으로 답한다. 후자가 거짓이면 ① 은 `NoGate` | STR-03 acceptance 셋째 |
| ⑦ | **임계치는 validation 만** — `minimumMatchScore`·`minimumProbabilityScore`·`bidNowThreshold`·`reviewThreshold` 는 범위와 교차 불변식(`review ≤ bidNow`)만 재고, **소비하지 않는다**(사다리는 M4 4B). 후보 상한은 양의 정수 **표현 상한**이고 분석 예산과 결합하지 않는다 | STR-03 · STR-06 legacy 형태 처리 |
| ⑧ | **정책 데이터 형태** — 점수 범위·기본값(legacy `0.6/0.55/0.7/0.45/1.0/10` 은 `legacy-behavior`, main 리터럴 금지 A-01)·상한 범위를 `StrategyPolicyData` 로 `EffectiveDatedPolicy` 에 실어 `Resolution.Resolved` 하나로 넣는다(1C F-5 관례). validation 결과는 그 `PolicyVersion` 을 싣는다(decision 23 의 읽기 — 자기 축의 versioned policy) | `v2-지침서.md` §5 매직넘버 · §4.2 「versioned policy data」 |
| ⑨ | **최소 sealed state/event** — D-4 가 경계를 긋는다. 추천 (a) 에서는 `StrategyRevision`(값)과 `StrategyEvent.StrategyUpdated(revision, …)` **payload 타입**까지가 1E 이고, 봉투(event id·aggregate version·idempotency)는 M4 4C, 편집 상태 기계는 M4 4A | `v2-지침서.md` §4.5 · STR-07 legacy 형태 처리(「전략 저장이 도메인 이벤트를 낳는다」) · `milestone-4.md` 4A·4C |
| ⑩ | **검색 경로와 같은 predicate** — D-2 (a) 에서 `EvaluationPath = sealed { MonitoringFilter, SearchQuery }` 태그를 두고 예산 비교는 경로와 무관하게 ④ 의 함수 하나를 쓴다. 「두 경로가 같은 답」은 주장이 아니라 **구조**(같은 함수)이고 runner 투영이 `perPath`·`pathsAgree` 를 낸다 | STR-16 acceptance 「비교 대상 금액과 basis 가 같다」 · R-BASIS-01·02 검증 방법 · `OPEN-1BC-STR16` |
| ⑪ | **corpus 실행** — 조건부. D-2·D-3 로 1E 축 authoritative 가 생기면 runner dispatch 를 넓혀 `check` 안에서 대조한다(입력에 없는 값을 runner 가 만들지 않는다 — 1D D-4 관례) | M1 완료 조건 「authoritative corpus 전체 통과」 · 1B-c 읽기 |

**만들지 않는 것**: 텍스트 정규화기·형태소 분석, 공고 상태 필터, 임계치 소비 사다리, 스냅샷 재계산·run 이력·스케줄,
편집 상태 기계, 저장소, 검색 API, 알림 범위 정책, 사람이 읽는 사유 문장(§3.1 — 렌더링 시점).

---

## 운영자 결정 필요 — 착수 전(D-1~D-4) · 계약 고정(D-5~D-12 · 조사 뒤 D-13~D-17, 세션 모델 판단·사후 확인)

> **이 초안은 묻지 않는다.** 1D 세션과 운영자가 같으므로 착수 시 한 번에 받는다. 각 행은 「선택지 + 추천 + 근거」로 닫혀 있어
> 운영자가 즉답할 수 있게 했다.

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-1** | **점수 임계치의 carrier.** `api-type-policy` 가 public 도메인 API 의 `Double`·`Float`·`Number` 를 막는다. 임계치 넷은 `[0, 1]` 의 **무차원 점수**이지 율이 아니다 — `Rate` 로 나르면 사정률·투찰율 축과 섞인다(ADR 0002 「축은 타입이 나른다」)이고, `Rate` 생성자는 어차피 `internal`(1D 가 연 factory 둘은 추천 투찰율·관측 사정률 전용) | (a) **strategy 모듈 안 `Score` 값 타입 + 축별 뉴타입(D-13)**(BigDecimal 백킹 — 1D `ProvenancePolicyData` 의 BigDecimal 선례, `internal constructor` + `Score.of(BigDecimal): Fact<Score>` 범위 검사 factory). 소비 모듈이 둘이 되는 시점(M4 4B 의 사다리가 `decision` 에 설 때)에 shared-kernel 승격을 **그 slice 의 결정**으로 (b) 지금 shared-kernel 에 `Score` 를 연다(1D D-1 (a) 선례 — 좁은 확장) (c) `Rate` 재사용 | **(a)** — 지금 소비자는 validation 하나뿐이고 shared-kernel 을 열 이유(둘째 모듈)가 아직 없다. (b) 는 소비자 없는 공개 타입을 승인 산출물에 더한다. (c) 는 축 혼동. **(a) 의 대가**: M4 4B 가 `Score` 를 `decision` 에서 못 쓰므로 그때 승격 결정이 필요하다 — OPEN 후보 `OPEN-1E-SCORE` 로 등재 | 착수 전 |
| **D-2** | **`OPEN-1BC-STR16`(money-basis-003) 을 1E 가 받는가.** 1B-c 가 「검색 경로를 나르는 타입」의 부재를 strategy slice 로 이월했다. 그 case 의 입력은 `operatorFilter(BaseAmount, OperatorDeclared)`·`noticeAmount`·`evaluatedVia[MONITORING_FILTER, SEARCH_QUERY]`, 기대값은 `perPath.{…}.{outcome, result, basisUsed}`·`pathsAgree` | (a) **받는다** — ⑩ 의 `EvaluationPath` 태그 + 같은 예산 함수. curator 가 003 에 `contract_binding` 을 달고 authoritative 로 되돌린다. **전제 승인 둘**(`uncovered_axes` 의 `unblocks_when` 문면): 운영자가 R-BASIS-01 「같은 쌍이 감시 경로와 검색 경로에서 같은 답을 내는지도 함께 고정한다」를 **업무 규칙으로 명시 승인** + **STR-16 신설 승인** (b) 받지 않는다 — 검색 API slice(M3) 가 받는다. 1E authoritative 는 D-3 에 달린다 | **(a)** — 「같은 답」을 만드는 것은 API 가 아니라 **predicate 가 하나라는 사실**이고 그것은 1E 소유다. M3 에서 받으면 검색 API 가 자기 비교 함수를 갖게 될 위험(R-BASIS-02 의 뿌리)이 다시 생긴다. 승인 둘은 이미 §14.2·manifest 가 요구한 문면이라 새 결정이 아니다 | 착수 전 |
| **D-3** | **1E 축 authoritative corpus.** `verdict` 4 는 전부 `insufficient-evidence`(`SkipReason` 어휘 미승인)이고 DEC 축이다. `capacity-gate` 4 중 authoritative 1(`-003`)은 QUAL-11 축이지 1E 가 아니다. **strategy 감시·validation 축의 case 는 0** | (a) **운영자가 STR-01 acceptance 넷 · STR-02 acceptance 넷 · STR-03 acceptance 셋을 업무 규칙으로 명시 승인**하고 fixture-curator 가 그 문면에서 `strategy-watch-*`·`strategy-validation-*` case 를 **1E 안에서** 신설한다(`extraction_method: authored-from-approved-spec`, `source.kind: operator-decision` 이 이 승인을 인용 — verdict-001 강등 사유가 「M0 산출 문서 자체 도출은 authoritative 가 아니다」였으므로 승인 없이는 같은 이유로 못 올린다) (b) 신설 없음 — 1E 는 D-2 (a) 의 money-basis-003 하나로 종결 (c) 별도 corpus slice `1E-c`(1B-c 선례) | **(a)** — 「authoritative corpus 전체 통과」를 0건 위에서 읽으면 공허 통과이고, 1B-c 의 읽기(「승인된 기대값 위에서의 통과」)는 그것을 뜻하지 않는다. 1B-c 를 별도 slice 로 뗀 비용 원인(기존 case 의 술어 동결·재추출·case 별 승인)은 여기 없다 — 기존 case 가 없고 술어는 이미 열려 있다. (c) 는 그 비용 없이 slice 하나를 더 낸다. 신설 대상 문면은 아래 「승인 대상 acceptance 목록」 | 착수 전 |
| **D-4** | **「필요한 최소 sealed state/event」의 경계.** `data-dictionary.md` §2.2 에 전략 전이표가 **없다**(Notice·Decision·TenderOutcome·Award·outbox 뿐). `milestone-4.md` 4A 가 편집 상태 기계(`WaitingForValue → …`)를, 4C 가 `StrategyUpdated` 봉투를 갖는다 | (a) **결과 어휘 + 이벤트 payload 까지** — `WatchVerdict`·`StrategyValidation`·`StrategyViolation`·`WatchRuleId` sealed, `StrategyRevision` 값, `StrategyEvent.StrategyUpdated(revision, policyVersion)` **payload 타입**. 전이 없음·상태 기계 없음. §2.2 에 「2.2.6 전략 — 어휘 자리만」 한 절(2.2.5 outbox 와 같은 형태)을 계약 갱신으로 (b) (a) + `SnapshotFreshness = sealed { Fresh, Stale(sinceRevision) }`(STR-07·10) (c) 4A 상태 기계를 앞당긴다 | **(a)** — M1 에는 이벤트를 소비하는 자리가 없고 완료 조건도 요구하지 않는다. 4A 의 상태는 **편집 흐름**(Telegram 의존 — `OPEN-STR-12` 미결) 상태라 지금 세우면 미결 위에 짓는다. (b) 의 `Stale` 은 스냅샷(파생 산출물) 소유이지 전략 소유가 아니다. (a) 가 「최소」의 정직한 읽기: **전략이 바뀌었다는 사실을 나르는 타입**까지 | 착수 전 |
| **D-5** | 커널 입력 경계 | — | `WatchSubject(categories: Set<CategoryCode>, keywordText: KeywordScopeText, fullText: FullScopeText, baseAmount: Fact<BaseAmount>)` — 어댑터(M3)가 두 텍스트를 조립한다(키워드 범위 = 제목+요건+카테고리, 전체 범위 = +description). 커널은 조립하지 않고 **어느 텍스트를 보는지를 서명으로** 정한다. `CategoryCode` 는 문자열 도메인 타입(값 크기·형태로 추측 금지). 텍스트 매칭의 접기(case-fold) 규칙은 Phase 2 가 legacy 실물을 내고 `legacy-behavior` 로 test 정책에만 — 1C 가 `lowercase()` 의 게이트 표면(`java.lang.Appendable`·`Locale`)을 실측으로 피한 선례를 따른다 | 계약 고정 |
| **D-6** | 예산 한계 형태 · **운영자 예산의 과세 처리** | — | `BudgetBound(min: BaseAmount?, max: BaseAmount?)` — `null` = 규칙 없음(감시 필드 비어 있음), `Fact.Absent` 는 **공고 쪽** 부재에만 쓴다(두 부재는 다른 뜻 — §1.3). validation: 둘 다 있으면 `compareKnownVat` 가 `Known(≤ 0)` 이어야 한다. **한계 값은 `OperatorDeclared`·`INCLUSIVE` 로만 구성한다** — 조사(스카우트 §7.2)가 `compareKnownVat` 전건(`sameKnownVat`)으로 `UNKNOWN` 한계가 항상 `Absent(VAT_TREATMENT_MISMATCH)` 를 냄을 드러냈다. 세 갈래 중 「운영자가 기초금액이라 선언한 값은 정의상 부가세 포함(U-1b)」을 택한다 — §1.2.1 의 「자동 태깅 금지」는 **legacy 유래 행** 한정이고 이것은 신규 입력의 **구성 규칙**이다. legacy 저장 값의 과세 처리(`OPEN-STR-01` 「일괄 태깅」)는 M3 마이그레이션·`OPEN-REG-05` 소유. `UNKNOWN`·`EXCLUSIVE` 한계는 validation 거부(비교 불가 한계는 규칙이 아니다) | 계약 고정 — **운영자 사후 확인 대상**(1D D-9 와 같은 급) |
| **D-7** | 규칙 id·사유 | — | `WatchRuleId = sealed { FocusCategory, FocusRegion, ExcludeRegion, RequiredKeyword, ExcludeKeyword, MinBudget, MaxBudget }`. `Passed.matched`·`Rejected.failed` 는 이 집합의 부분집합(`Rejected.failed` 비어 있지 않음 — init 거부). 한국어 사유 문자열(`strategy_reasons`) 미채택 — 문장은 표현 계층 | 계약 고정 |
| **D-8** | `Undeterminable` 사유 | — | `WatchUndeterminableReason = sealed { BaseAmountAbsent(reason: ReasonCode), BudgetNotComparable(reason: ReasonCode) }` — 후자는 `compareKnownVat` 의 `Absent` 사유(`VAT_TREATMENT_MISMATCH`·`UNDECLARED_PROVENANCE`)를 그대로 싣는다. 예산 규칙이 없으면 공고 금액 부재는 `Undeterminable` 이 아니다(그 축을 보지 않으므로) | 계약 고정 |
| **D-9** | 결합 순서 | — | 제외 규칙(지역·키워드) 하나라도 매치 → `Rejected`(제외가 우선), 그 다음 포함 규칙 전부 AND(카테고리 ∧ 지역 ∧ 키워드 ∧ 예산). 각 포함 축 안은 OR. 규칙이 하나도 없으면 `NoGate`. 순서는 정책이 아니라 **의미**(STR-01 「제외는 하나라도 걸리면 탈락」)라 코드가 갖고 example 로 고정 | 계약 고정 |
| **D-10** | validation 단일 구현 | — | `validate(draft: StrategyDraft, policy: Resolution.Resolved<StrategyPolicyData>): StrategyValidation` 하나. `StrategyDraft` 는 전부 optional 원시 입력(어댑터가 채움), `OperatorStrategy` 는 `internal constructor`. 위반은 **전부 모아** 낸다(첫 위반에서 멈추지 않음 — 편집 화면이 한 번에 보여야 한다) | 계약 고정 |
| **D-11** | 검색 경로 태그 | — | D-2 (a) 시 `EvaluationPath` 는 predicate 의 **입력이 아니라 runner 투영의 축**이다 — 커널 함수는 경로를 모른다(같은 함수를 두 번 부른 결과가 같다는 것이 `pathsAgree`). 커널에 경로 분기를 두지 않는다 | 계약 고정 |
| **D-12** | 계약 어휘 = fixture 어휘 | — | D-2·D-3 로 생기는 case 의 필드 이름은 이 계약의 타입·필드 이름을 따르고(decision 19 관례의 역방향 — 이번엔 case 가 뒤에 생긴다), 투영은 sealed 이름 문자열·`policyVersion.source`(1C ⑦ 관례). 승인 문면과 fixture 가 어긋나면 멈추고 보고 | 계약 고정 |

### D-3 승인 대상 acceptance 목록 (`capability-map.md` 문면 그대로 — 승인은 문면 단위)

| 출처 | 문면(요지) | case 후보 |
| --- | --- | --- |
| STR-01 | 중점 카테고리는 완전일치(`service` ≠ `technical-service`) · 카테고리 ∧ 필수 키워드 AND · 감시 필드 전부 빈 전략 = 「게이트 없음」(결과 타입에서 「모든 공고 통과」와 구분) · ML port 0회 | `strategy-watch-001~004` |
| STR-02 | 필수 키워드가 description 에만 → 후보 아님 · 제목/요건에 있으면 후보 · 제외 키워드가 description 에만 → 떨어뜨리지 않음 · 중점 지역은 description 포함 전체 텍스트 | `strategy-watch-005~008` |
| STR-03 | `review > bidNow` 편집은 모든 경로에서 거부·저장 전략 불변 · `minBudget > maxBudget` 거부 · 「설정됐는가」≠「감시 범위를 좁히는가」 | `strategy-validation-001~003` |

「ML port 0회」는 값 case 가 아니라 **구조 case**(모듈 의존 게이트 — `strategy` 의 의존 선언에 ML·adapters 가 없음)로 덮는다.

---

## 위협 모델 — 1E 고유 경계 (Phase 2.5, 세션 모델 직접)

**방어한다** — 감시 predicate 와 validation 의 **의미 회귀**: (a) `WatchVerdict` 가 boolean 으로 접히는 공개 경로(`passes()`·`toBoolean()` 류 없음, 소진 `when` 소비 test) (b) `NoGate` 가 `Passed` 로 접힘(example + property: 규칙 0 ⟹ `NoGate`) (c) 다른 basis 의 금액이 예산 비교에 들어옴(타입 — `BaseAmount` 만 받는다; 1B `CompileFailureHarnessTest` 관례로 `EstimatedAmount` 전달 컴파일 fixture) (d) 키워드 규칙이 description 을 봄(서명 — `KeywordScopeText` 만 받는다; `FullScopeText` 전달 컴파일 fixture) (e) 둘째 validation 구현·편집 경로별 불변식 재구현(`OperatorStrategy` `internal constructor`, 생성 경로는 `validate` 하나) (f) 기본값·범위 리터럴이 main 에 들어옴(정책 데이터 부재 → 구성 실패; `domainSourceReferenceGate` 는 못 잡는다 — 1C F-3·1D (e) 와 같은 한계, 코드 리뷰) (g) `0 = 무제한` sentinel 재도입(`BudgetBound` 의 `null` 과 `0원` 이 다른 결과를 내는 example) (h) predicate 안 ML·DB 호출(모듈 의존 게이트 — 구조) (i) `Undeterminable` 이 `Rejected` 로 접힘(소진 `when`).
**방어하지 않는다** — 어댑터가 두 텍스트를 **정직하게** 조립하는가(두 타입 모두 `String` 을 감싸므로 전체 텍스트를 키워드 범위 타입에 넣는 어댑터는 막지 못한다 — 서명이 강제하는 것은 **선택을 명시**하는 것까지), 텍스트 접기 규칙의 옳음(legacy-behavior), 정책 데이터 **내용**(범위를 `[−∞, ∞]` 로 주는 운영자), 임계치의 소비, 공고 상태 필터, 저장·동시 편집, 게이트 정의 편집(하네스 저자), 리플렉션.
**경계가 요구 축소가 아닌 이유**: STR-01·02·03 은 판정의 **형태·결합 규칙·불변식**을 정하고 텍스트 조립(§5.3 필드 계약)·정책 내용·소비는 다른 자리에 둔다.

**우회 후보 (Phase 2.5 ≥5)**: (1) `Rejected(failed = ∅)` 조립 → init 거부 (2) `OperatorStrategy` 를 validation 없이 조립 → `internal constructor`(같은 모듈 test 는 가능 — 1B·1C 와 같은 한계, 등재) (3) `EstimatedAmount.export()` 의 값을 `BaseAmount(...)` 로 다시 싸서 한계로 넣기 → 타입은 못 막는다(`BaseAmount` 생성자는 공개). 그 값은 `provenance` 를 선언해야 하므로 **어댑터의 정직성** 층 — 방어하지 않는다에 등재 (4) `FullScopeText` 의 문자열로 `KeywordScopeText` 를 만들기 → 위와 같음(선택의 명시까지만) (5) `validate` 에 범위 `[0, 0]` 정책을 줘 모든 임계치를 거부 → 정책 내용, 방어하지 않음 (6) `Undeterminable` 을 소비자가 `Rejected` 로 매핑 → 소진 `when` 만(1C (1)·1D (1) 과 같은 한계) (7) 예산 규칙 없이 `Fact.Absent` 공고를 `Passed` 로 → D-8 문면대로 **의도된 동작**(그 축을 안 본다) — 우회가 아니라 example 로 고정.

---

## 조사(Phase 2) 결과 — `_workspace/m1-1e/01_scout_preflight.md` (2026-09-06)

- **앵커 소실 둘**: `regression-ledger.md` 에 `R-STR-*` 는 없다 — 전략 축 회귀는 `R-BASIS-01`(감시 예산 필터)·`R-BASIS-02`(검색
  예산 필터) 둘이 받는다(브리프의 `R-STR-*` 지목은 오기). `data-dictionary.md` §2.2 에 전략·스냅샷·감시 run 축의 전이표는 **없다**
  (Notice·Decision·TenderOutcome·Award + outbox 형태 요구뿐) — D-4 의 전제가 실측으로 선다. legacy 에도 「전략 편집」 상태 기계는
  없고(`updated_at` 갱신뿐), 상태 축은 감시 run 5값·lineage stage 5값·스냅샷 3값 셋이며 `stale` 은 상태가 아니라 **파생 술어**다
  (스캔 시작 시점의 전략 스탬프 대 현재 값 비교 — 순수; 시간 경과 부분은 시계). 순수 커널로 가져갈 수 있는 것은 run (상태, 이벤트) 표와
  스탬프 비교뿐이고, 단일비행 클레임·고아 회수·「직전 완료 run」 선택은 DB·시계에 묶여 있다 → D-4 (a) 의 「1E 자신이 만드는 판정의
  상태까지」가 조사 방향과 일치한다.
- **legacy 실물(`legacy-behavior`, 스카우트 §1)**: watch 규칙 일곱은 순수(`filters.py` docstring *"(no scoring, no ML, no DB)"*),
  선언 순서 = 판정 순서, 첫 탈락에서 즉시 종료. 카테고리 완전일치(양쪽 `strip().lower()`), 지역은 title+**description**+requirements+
  category 부분문자열 OR, 키워드는 description **제외** 텍스트 — 규율은 호출 규약뿐(서명이 `str` 하나). 예산은 `min > 0 and budget < min`
  (**등호 통과**, `0` = 무제한 sentinel). validation 은 **같은 규칙이 네 자리, 실패 거동 셋(예외/문자열/조용한 clamp), 경계 둘**
  (`≤ bidNow` 대 `≤ bidNow − 0.01`) — STR-03 「편집 경로마다 재구현 폐기」의 실물. `min > max` 검사는 텔레그램 경로에만 있다.
  기본값 `0.6/0.55/0.7/0.45/1.0/10` 은 네 자리 리터럴 중복. 「게이트 없음」과 「전부 통과」의 구분은 `has_watch_rules` 가 대상 필드
  목록을 **데이터로** 갖는 좋은 형태이나 `_has_configured_watch_rules` 가 임계까지 포함한 다른 목록을 손으로 다시 적어 두 물음이 두
  구현으로 갈려 있다(→ ⑥). 액션 사다리 `decide` 는 I/O-free 이고 force-bid `or` 절이 `OPEN-STR-03` 의 우회 — 1E 밖(M4 4B).
- **구조적 막힘 둘(둘 다 shared-kernel 을 여는 문제가 아니라 「새 타입을 어디에 세우는가」)**: ① **점수 축 타입 부재** — `Rate` 축 넷에
  점수 축이 없고 `api-type-policy` 가 `Double`·`Float`·`Number` 를 막는다. raw `Rate.ofFraction` 은 축을 나르지 않아 매칭 점수와 확률
  점수가 서로 대입된다(§1.4.2 가 막으려는 형태) → **D-1 (a) + D-13(축별 뉴타입)**. ② **`compareKnownVat` 전건** — 운영자 예산의
  `vatTreatment` 가 `UNKNOWN` 이면 항상 `Absent(VAT_TREATMENT_MISMATCH)` 라 예산 필터가 성립하지 않는다 → **D-6** 이 답한다
  (한계는 `INCLUSIVE` 로만 구성, `UNKNOWN` 한계는 validation 거부). `Provenance.OperatorDeclared` 는 이미 있다. `Money.amount` 가
  `internal` 이지만 1E 는 원 단위를 읽을 필요가 없다(비교는 `compareKnownVat`, 렌더링은 표현 계층). 텍스트 입력 타입은 `procurement`
  가 앵커뿐이고 참조도 불가(D-4)라 **strategy 가 스스로 정의**한다 — STR-02 「타입 분리」 요구와 같은 자리(중복이 아니라 경계).
  `strategy` 모듈은 앵커뿐 — `qualification`·`decision` 의 `build.gradle.kts` 를 그대로 복제한다.
- **재사용**: 외부 라이브러리 **없음**(`external.allowed.domain` = kotlin-stdlib + annotations; 규칙 엔진·검증 라이브러리·정규식
  전부 불가·불필요 — 규칙은 `contains` 부분문자열, `OPEN-STR-05` 부분문자열 유지). legacy 순수 함수 중 이식 가치: `_matched_terms`
  (중복 제거·순서 보존), `WATCH_RULE_*_FIELDS`(대상 목록을 데이터로), `AllocationThresholds.from_settings`(경계에서 한 번 모아 커널을
  I/O-free 로 — 1E 는 정책 데이터 주입으로 대체), 스냅샷 스탬프 비교(순수 부분). `tests/test_strategy_keyword_filter.py` 와
  `scripts/seed_marine_gate.py` 의 오탐 어휘 여섯은 fixture **후보**이나 authoritative 근거가 아니다(legacy 동작·주석).
- **corpus 실측(§5)**: 전략·watch 도메인 fixture **0건**. `verdict` 4 전부 `insufficient-evidence`(`SkipReason` 어휘 미승인 —
  DEC 축), `capacity-gate` authoritative 1(`-003`)은 근거·어휘가 **자격 축**(`OPEN-QUAL-08` 분할 확정, `LicenseVerdict`·
  `UncertainReason.RequirementDataAbsent`)이라 1E 가 `TARGET_DOMAINS` 에 넣으면 자격 축 코드를 strategy 에서 돌리게 된다 —
  **넣지 않는다**(D-14). `verdict` 도 넣지 않는다(대상 0건이 조용히 통과). 1E 가 실행할 authoritative 는 **D-2·D-3 승인 전 0건**.
  어휘 불일치 여덟은 전부 verdict·capacity-gate 소유(1E 축 아님)라 등재만 — `basis` 값 둘(`AWARDED_CONTRACT_LIMIT`·
  `CONSTRUCTION_CAPACITY`)이 enum 밖이고 `Money` 구현도 없다는 사실은 capacity 축 slice 가 받는다.
- **회귀(§3)**: `R-BASIS-01` 은 1E 가 **타입으로 잡는다**(`compareKnownVat` 오버로드 — `BaseAmount` 대 `EstimatedAmount` 는
  컴파일 불가; 컴파일 fixture 로 표현). `R-BASIS-02` 는 D-2 (a) 로 predicate 공유까지만(API 는 M3). STR-07 무효화 누락은 HEAD 에
  그대로(온보딩 확정이 `preview_snapshot` 을 import 조차 안 함) — 「전략이 바뀌었다」 payload 까지가 1E, 배선 불변식은 M4.
  STR-09 트리거/전략 분리는 타입으로 표현 가능하나 스케줄 배선 검증은 M4. STR-05 degrade-to-empty 는 `후속` 분류라 1E 밖.
- **게이트 영향(§8)**: `api.forbidden.types` **높다**(→ D-1) · `limit.type.members=30` **높다** — `OperatorStrategy` 를 필드 15 짜리
  한 타입으로 옮기면 주 생성자만 15 라 **축별 분할**(D-15) · 상속 깊이 래칫 0 — sealed **인터페이스** + data class/object 만 ·
  CPD main fail **높다** — legacy 「네 자리 재구현」을 옮기면 곧바로 걸리고, 반대로 D-10 단일 구현을 구조적으로 강제 · T-C
  `Locale` 표면 — `String.lowercase()` 금지(1C 실측), 접기 함수는 1C 관례(CharArray 루프) 재구현. **1C 의 helper 는 private 이고
  모듈 간 참조 불가라 같은 함수가 두 모듈에 생긴다** — CPD `minimumTokenCount=50` 아래인지 착수 시 실측, 넘으면 shared-kernel
  승격 결정(OPEN 후보 `OPEN-1E-TEXTFOLD`) · T-B 에 `java.util.regex` 없음(불필요) · detekt `TooManyFunctions`·`ComplexInterface` 주의 ·
  `gate.tests.strategy` 신설 필요.

| ID | 판단(세션 모델, 사후 확인) | 근거 |
| --- | --- | --- |
| **D-13** | 점수는 **축별 뉴타입** — `MatchScore`·`ProbabilityScore`·`PriorityScore`(`Score` 공통 값 위, `[0,1]` 범위는 정책 데이터). `minimumMatchScore: MatchScore` · `minimumProbabilityScore: ProbabilityScore` · `bidNowThreshold`·`reviewThreshold: PriorityScore`. 교차 불변식 `review ≤ bidNow` 는 같은 축 안에서만 성립하므로 타입이 비교 자체를 제한한다 | §1.4.2 「한 축의 율을 다른 축에 대입할 수 없게」의 점수 판 · 스카우트 §7.3 |
| **D-14** | corpus runner 에 `verdict`·`capacity-gate` 를 **넣지 않는다** — 전자는 대상 0건이 조용히 통과, 후자는 자격 축 어휘. 1E 의 runner 확장은 D-2·D-3 로 생기는 case(`money-basis-003`·`strategy-*`)만. `capacity-gate` 소유 slice 미지정은 OPEN 등재 | 스카우트 §5.1·§5.3·§7.5 |
| **D-15** | `OperatorStrategy` 는 **축별 분할** — `WatchRules`(감시 필드 일곱) · `ActionThresholds`(임계 넷) · `CandidateLimit` · `StrategyRevision` 을 담는 봉투. 멤버 30 게이트가 요구하는 방향이고 ⑥ 의 두 술어가 각각 `WatchRules`(비어 있는가)·봉투(설정됐는가) 위에 선다. 경계 포함성(예산 등호 통과·`review == bidNow` 허용)은 `legacy-behavior` — 임계 쌍은 STR-03 문면(「`>` 편집 거부」)이 등호 허용을 함의하고, 예산 한계 포함성은 `BudgetBound` 가 §1.4.3 대로 **값과 함께 선언**(초기값 `Inclusive`, 정책 데이터 슬롯) | 스카우트 §8 · §1.2·§1.3 경계 실측 · STR-03 acceptance |
| **D-16** | 사유 어휘는 **1E 소유 sealed**(`StrategyViolation`·`WatchUndeterminableReason`)이고 `ReasonCode` 에 더하지 않는다 — `compareKnownVat` 의 `ReasonCode` 는 payload 로 그대로 싣는다(1D `FloorUnmeasurableReason` 선례, 어휘 소유 DEC-06 불변) | 스카우트 OPEN 7 · 1D D-14 |
| **D-17** | `StrategyUpdated` payload 에 **변경 주체(actor) 슬롯을 두지 않는다** — `OPEN-STR-04`(실험의 무승인 갱신)가 M4 4A 소유라 지금 넣으면 미결을 선점한다. 4A 가 봉투에서 넓힌다 | 스카우트 §6 OPEN-STR-04 행 |

**정책 데이터 내용은 main 에 없다** — 1C·1D 와 같이 형태·version 배관만. legacy 값(기본값 여섯·범위·포함성)은 `legacy-behavior` 로 test
정책 인스턴스에만 쓰고 스카우트 §1.1 좌표를 주석에 남긴다. 「5,382건 중 43건」 통과율은 측정일 미기재라 인용하지 않는다.

---

## OPEN — 수령·신설

| OPEN | 1E 처리 |
| --- | --- |
| `OPEN-STR-01`(해소) | 「(c) + 기초금액」을 ④·D-6 이 타입으로 집행 — 소비만 |
| `OPEN-STR-07`(해소) | 단일 회사 — 1E 타입에 operator 식별자 없음. 소유권 경계(타입)는 §2.1 문면대로 유지, 스키마 단순화는 M3 |
| `OPEN-STR-02` | 미리보기 노출 창 크기(측정) — 1E 축 아님, **막지 않는다** |
| `OPEN-STR-04` | 실험의 무승인 갱신 — M4 4A 소유, **막지 않는다**(1E 는 갱신 경로를 만들지 않는다) |
| `OPEN-STR-12` | Telegram 편집 채택 — M4 4A 소유, **막지 않는다**(D-4 (a) 가 편집 흐름 상태를 1E 밖에 둔 이유) |
| `OPEN-1BC-STR16` | **D-2** — (a) 채택 시 1E 가 닫는다(승인 둘 전제) |
| `OPEN-DIC-03` | `SkipReason` 전수성 — DEC 축, 1E 무관. `verdict-*` 4 는 1E corpus 가 아니다 |
| `OPEN-REG-05` | 기초금액·추정가격 과세 처리 — 1E 는 `BaseAmount` 끼리만 비교하므로 **막지 않는다**. R-BASIS-01 의 「과세/비과세 경계 쌍」 조건부 fixture 는 이 OPEN 뒤이고 1E 가 만들지 않는다 |
| `OPEN-1E-SCORE`(신설 후보) | D-1 (a) 의 대가 — `Score`·축 뉴타입(D-13)의 shared-kernel 승격 시점(M4 4B 가 `decision` 에서 임계치를 소비할 때). 착수 시 §14.2 에 등재 |
| `OPEN-1E-TEXTFOLD`(신설 후보) | 텍스트 접기 helper 가 1C(`qualification` private)와 1E 두 모듈에 생긴다 — CPD 50 토큰 아래면 등재만, 넘으면 shared-kernel 승격 결정 |
| `OPEN-1E-CAPACITY-OWNER`(신설 후보) | `capacity-gate` 도메인(authoritative 1 = `-003`)의 소유 slice 가 문서에 없다 — 어휘·근거가 자격 축(1C out_of_scope 「capacity(QUAL-11) 별도 slice」)이라 **1E 아님**. 담당 지목은 운영자 |
| `OPEN-STR-04`(재등재) | D-17 — `StrategyUpdated` payload 에 변경 주체 슬롯을 두지 않아 미결을 선점하지 않는다. M4 4A 가 넓힌다 |
