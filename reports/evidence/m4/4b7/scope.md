# Slice 계약 — M4 / 4B-7 · 과거 경쟁 표본 공급(`OPEN-4D3-SAMPLE-SUPPLY` (a) — 공종/전역 2계층부터)

> **지위**: 운영자 결정 2026-09-16 「`OPEN-4D3-SAMPLE-SUPPLY` (a) 추천안으로 진행」 + **D-4B7-1 (a) 채택**(「D-4B7-1 추천 방향으로 진행」).
> Kotlin 요청이 분포 엔진(5D-2/5D-3, 5E-2 로 wire 매핑 완료)을 구동하도록 ① 같은 공종의 과거 개찰 결과를 조회하는 port
> ② `OpeningResult`+`Notice` → `CompetitionSample`(예비가격 추첨 관측 포함) 순수 변환(자격 술어·오염 라벨) ③ `OpportunityAnalysis`
> 가 표본과 표본 축(공종)을 요청에 싣게 한다. 기관 fact 는 없으므로(`OPEN-2B-AGENCY-ID`) 표본 축 `agency_id` 는
> `NOT_COLLECTED_YET` — 엔진은 `PARENT_CATEGORY`/`GLOBAL` 로 답한다. 4D-1 알려진 제한 2(「요청 조립·경쟁 표본 정제는 4B
> 후속」) 해소. 세션 모델 단독 작성. Phase 2.5 설계 검토(게이트형 — 자격 술어·라벨) `_workspace/m4-4b7/02_design-review.md`.
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m4-4b7/2026-09-16`, base = `origin/main`(PR #20 5E-2 병합 뒤 — 다른 레인).
> 구현 `kotlin-implementer`(sonnet) → `verifier`(opus). Kotlin(`workflow`·`adapters`)과 문서만 — Python 레인과 소스 겹침 0.

## 착수 조사 실측(계약 경계의 근거 — `scout-4b7` + 팀장 직접 대조)
| 사실 | 위치 | 귀결 |
| --- | --- | --- |
| 엔진 관문: `reserve_draw` 없음 → `NO_RESERVE_DRAW` · 예비가격 수 ≠ `reserve.expected_price_count`(15) → `PRICE_COUNT_MISMATCH` · 예비가 `Money` 4성분(basis `BASE_AMOUNT`·KRW·provenance≠UNSPECIFIED·>0) 위반 → `RESERVE_PRICE_INVALID` · 중심비 0.8~1.2 밖 → `CENTER_OUT_OF_BAND` · `observed_bid_rate` 파싱/밴드 0.5~1.5 · `base_amount` 4성분 · `selected_numbers` 는 거부 축 아님 · `base_amount_provenance_label` CLEAN 만 K5 집계(`admit_clean`), 나머지는 계수 · `opened_on`·`award_rate` 는 파싱만 | `ml-engine/.../inference/observations.py`·`assessment.py`·`policy/inference-v1.yaml` | 표본 자격 술어(D-4B7-2)는 이 관문과 1:1 |
| Kotlin 도메인 `CompetitionSample` 에 `reserveDraw` 자리 없음 · `toProto()` 가 wire 7번 필드를 절대 안 채움 · `origin` 은 상수 `OBSERVED` | `workflow/prediction/BidPredictionRequest.kt` · `adapters/ml/RequestMapping.kt` | D-4B7-5 |
| 개찰 결과 도메인은 원자재를 다 갖는다: `reservePrices: List<OpeningReservePriceRow>`(`baseReservePrice: ReservePriceCandidateAmount(won, currency)` — **basis 미확정이라 `Money` 비구현**, M3 P-9 ③) · `drawNumbers: DrawNumberObservation`(`NotObserved`/`Verified`/`OutOfRange`/`RangeCheckUnavailable`, 번호 집합) · `openingRankOne: OpeningRankOneOutcome`(`OpeningRankOneBid.bidRate: Rate?`, 협상 계약은 부재 정상) · `winningRate: Rate?` · `finalAwardAmount` · `actualOpeningAt: Instant?`(`rlOpengDt`) · `observedAt` | `procurement/NoticeFacts.kt` | D-4B7-1·2·7 |
| `ReservePriceCandidateAmount` basis 미확정의 정본: `reports/evidence/m3/3a/policy-values.md` §1.7.1 `bsisPlnprc` 행(「`YEGA` 로 접으면 부분을 전체 자리에」)·P-9 ③ 「값이 미확정이라 열지 않는다」 | 3A policy-values | **D-4B7-1 (a) 로 닫는다** |
| 오염 라벨 분류기 존재·미배선: `ProvenanceRules.judgeRow(original: BaseAmount, row: ProvenanceRow(rawBaseAmount, budgetEstimate, winningAmount, winningRate), recoveryEstimate: Fact<Money>, policy) → ProvenanceJudgement(classification: BaseAmountProvenance)` — src/main 호출부 0 | `decision/ProvenanceRules.kt` | D-4B7-8 — 두 번째 분류기 금지 |
| 저장 층: `opening_result`(PK notice_number·round, `actual_opening_at` nullable, `winning_rate_fraction`, 1순위 7컬럼, `draw_numbers INT[]`…)·`opening_reserve_price`(FK, `base_reserve_price_won/currency`)·`notice`(`business_category_code`, `base_amount_*`, `deadline_at`). **보조 인덱스 0**(`CREATE INDEX` 는 audit·collection_run·outbox 뿐) · 전 저장소에 다건 스캔 SELECT 없음 · 개찰일 유일 출처 `actual_opening_at` | `adapters/persistence/Sql.kt`·`db/migration/V1~V6` | D-4B7-3·6·7 |
| `OpportunityAnalysis` 주 생성자 `internal`(port 6+Clock+정책표 3), public 보조 생성자(port 6+Clock) · `predictionRequestFor` 는 port 를 읽지 않는 파일(`PredictionFacts.kt` KDoc) · 조립 근(`app/`) 없음(호출부는 test 둘) · JDBC repository 는 `DataSource` 생성자 주입 + `dataSource.connection.use {}` · `Sql.kt` object 상수 · `ResultSet` 확장 복원 | `workflow/evaluation/*` · `adapters/persistence/*` | D-4B7-4·9 |

```yaml
milestone: m4
slice: 4b7-competition-sample-supply
base_sha: 8799e0594496593521691a24a29551ee51b6f147
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt        # ① CompetitionSample.reserveDraw: ReserveDrawObservation?(reservePrices: List<BaseAmount>, selectedNumbers: Set<Int>) — D-4B7-5
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt                       # ② CompetitionSamplePort(fun interface) + CompetitionSampleQuery(categoryCode, asOf, window, limit) + CompetitionSampleSupply(samples, excluded: Map<SampleExclusionReason, Int>) — D-4B7-3·4
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleEligibility.kt           # ③ 신설: (Notice, OpeningResult) → CompetitionSample | Excluded(reason) 순수 변환(자격 술어 D-4B7-2·7, 라벨 D-4B7-8) — port 를 읽지 않는 파일
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/{OpportunityAnalysis,PredictionFacts,OpportunityPolicyData}.kt   # ④ port 주입(보조 생성자 +1)·표본 조회 뒤 predictionRequestFor(…, supply) 인자·정책 슬롯 둘(sampleWindowDays·maxSamples) — D-4B7-3·9
  - adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcCompetitionSampleSource.kt   # ⑤ 신설: CompetitionSamplePort 구현 — 공종·개찰일 창 RO 조회(notice ⋈ opening_result ⋈ opening_reserve_price), 기존 ResultSet 복원 확장 재사용, 쓰기 0 — D-4B7-3
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                       # SELECT 상수 추가(다건 스캔 첫 사례 — 정렬 actual_opening_at DESC, LIMIT)
  - adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt                     # ⑥ reserve_draw(7) 채움(D-4B7-5) — 표본 category_code 는 이미 도메인 값이 있으면 실린다(2F)
  - adapters/src/main/kotlin/bidvector/adapters/ml/MoneyMapping.kt                       # ⑥ toProtoReserveDraw(예비가 Money 목록·번호) — verifier r1 F-8 등재
  - adapters/src/main/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSource.kt        # ⑤ 실제 배치(갱신 이력 ②) — persistence 경로 대신
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt           # ③ 변환부 분리(신설) — verifier r1 F-8 등재
  - workflow/src/test/kotlin/bidvector/workflow/{prediction,evaluation}/*Test.kt · *Fixtures.kt · adapters/src/test/kotlin/bidvector/adapters/{ml,persistence}/*Test.kt · *Fixtures.kt   # 규칙표·port fake·JDBC 조회 test(기존 persistence test 관례)·CompetitionSample 생성부 갱신
  - config/quality/gate-tests.properties                                                  # 신설 test 등재(WorkflowGateRegistrationTest·persistence 게이트 완전성)
  - reports/evidence/m4/4b7/** · milestone-4.md · docs/discovery/capability-map.md(OPEN 표) · reports/evidence/m3/3a/policy-values.md(§1.7.1 bsisPlnprc 행 뒤 D-4B7-1 닫힘 주석 — 팀장, 이력 유지)
out_of_scope:
  - 기관 fact(`OPEN-2B-AGENCY-ID`) — 표본 축 agency = NOT_COLLECTED_YET · 인덱스 마이그레이션(`OPEN-4B7-QUERY-INDEX`, D-4B7-6) · 대상 공고 요청 축 `baseAmountProvenanceLabel`(현재 Unknown — `OPEN-4B7-TARGET-LABEL`, 같은 분류기로 후속) · 근거 문구(`OPEN-4D3-DIAGNOSTICS-RENDER`) · 엔진 정책 값 · 계약 파일(`contracts/**`) · Python · `procurement` 도메인 타입 변경(`ReservePriceCandidateAmount` 는 그대로 — 변환은 workflow 에서) · 조립 근(`app/`, M6)
acceptance: CI Kotlin `check` job 전건 — 루트 `./gradlew --no-build-cache --no-daemon clean check`. 마이그레이션 없음(D-4B7-6) → migration-reviewer 불요. ml-engine job 은 소스 비중첩.
rollback: in_scope 경로 한정 단일 역적용(`git diff <base> -- <파일> | git apply -R`, 2026-09-16 규칙), 신설 파일 삭제, evidence 는 남김. 임시 clone(`git clone --no-hardlinks`) ①~⑥(⑥ 되돌린 트리 루트 `check` 전건). 되돌리면 요청 표본 0건(4D-3 상태) 복귀.
```

## 결정(운영자 결정 D-4B7-1 채택 2026-09-16 · 나머지는 계약 고정)

| ID | 판단 | 근거 |
| --- | --- | --- |
| **D-4B7-1 (a)** | 복수예비가격 후보(`bsisPlnprc`, `ReservePriceCandidateAmount`)를 **기초금액 축의 원문 관측값**으로 채택 — wire `reserve_prices` = `Money(basis BASE_AMOUNT, currency 그대로, vat UNKNOWN, provenance Published(공고 회차 = NoticeId.round))`. M3 P-9 ③ 「미확정」 을 닫는다(3A policy-values §1.7.1 에 닫힘 주석). `procurement` 타입은 바꾸지 않고 workflow 변환에서 `BaseAmount` 로 만든다 | legacy-behavior: `app/services/base_amount_basis.py:43-55` 가 15 후보를 기초금액과 같은 축으로 순회 · 엔진 K6 중심비 개연 밴드 `assessment.plausible_min/max = 0.8~1.2` 가 그 축을 전제(축이 다르면 밴드가 무의미) · 축 오판은 `CENTER_OUT_OF_BAND` 로 fail-closed. 운영자 승인 2026-09-16 |
| D-4B7-2 | 표본 자격(공급 측, 사유별 계수 — 조용한 drop 금지): ① 개찰 결과 존재(없으면 후보 아님 — 조회가 애초에 조인) ② `openingRankOne` 이 결정됐고 `bidRate != null`(→ `observed_bid_rate`) — 아니면 `RANK_ONE_RATE_MISSING` ③ 예비가격 행 수 == `expectedPriceCount`(정책 슬롯, 착수 값 15 = 엔진 `reserve.expected_price_count`) 아니면 `RESERVE_PRICE_COUNT_MISMATCH` ④ 행마다 `baseReservePrice != null` 아니면 `RESERVE_PRICE_MISSING` ⑤ `drawNumbers`: `Verified`/`RangeCheckUnavailable` → 번호 집합, `NotObserved` → 빈 집합(엔진은 거부하지 않음), `OutOfRange` → `DRAW_NUMBERS_OUT_OF_RANGE` 제외 ⑥ 공고 `baseAmount: ResolvedBaseAmount` 없으면 `BASE_AMOUNT_MISSING` ⑦ `actualOpeningAt` 없으면 `OPENING_DATE_MISSING`(D-4B7-7). **CLEAN 이 아니어도 보낸다**(라벨을 붙여 — 엔진 `admit_clean` 이 거르고 계수, 공급 측이 두 번 거르지 않는다) | 5D-2 관문과 1:1 · 5D-2 「조용한 drop 금지」 |
| D-4B7-3 | 조회 축: 같은 `CategoryCode`(정규화 문자열 동일 — 5D-3 D-5D3-1 규칙, 별칭 없음) · `actual_opening_at` < 기준 시각(`Clock`) · 창 `sampleWindowDays` · 상한 `maxSamples`(최신 순). 정책 슬롯 둘은 `OpportunityPolicyData`(placeholder 값 365일·500건, `OPEN-4B7-POLICY-VALUES` — 5C/5E 실측 뒤 갱신). 대상 공고 자신은 제외(같은 `NoticeId`) | 4D-1 정책 값 관례 · 미래 표본 누출 금지 |
| D-4B7-4 | port `CompetitionSamplePort`(workflow `Ports.kt`, `fun interface samplesFor(query): CompetitionSampleSupply`) — 어댑터는 **RO 조회만**(쓰기 0, 트랜잭션 없음, `DataSource` 주입 관례), 자격·라벨·변환은 workflow 순수 함수(`SampleEligibility.kt`). 어댑터가 도메인 `(Notice, OpeningResult)` 쌍을 내고 workflow 가 판정한다 — 판정을 SQL 에 두지 않는다 | CompositionBoundary · 4B-6b port 관례 |
| D-4B7-5 | 도메인 `CompetitionSample.reserveDraw: ReserveDrawObservation?`(`reservePrices: List<BaseAmount>` — `init` 비어 있지 않음, `selectedNumbers: Set<Int>` — `init` 전부 ≥1) — `toProto()` 가 7번 필드 채움; `null` 이면 안 채움(엔진이 `NO_RESERVE_DRAW` 계수). 기본값 `null` 은 기존 test fixture 호환용이되 **공급 경로는 항상 채운다**(자격 ③④ 가 보장) | 4D-1 알려진 제한 2 해소 |
| D-4B7-6 | **마이그레이션 없음** — 인덱스는 `OPEN-4B7-QUERY-INDEX`(M6 규모 산정 뒤, migration-reviewer + Codex 범위 승인) | 되돌리기 어려운 경로를 정확성 slice 에 섞지 않는다 |
| D-4B7-7 | `opened_on` = `actual_opening_at` 의 날짜(시간대 `OPEN-3A-SOURCE-TZ` 정본 — 착수 가정 `Asia/Seoul`, 슬롯 없이 상수로 두되 checklist 등재). 결측이면 제외 — `observed_at`(수집 시각)로 대체하지 않는다 | 「모름을 지어내지 않는다」 |
| D-4B7-8 | 라벨 = `ProvenanceRules.judgeRow(original = 공고 기초금액, row = ProvenanceRow(rawBaseAmount, budgetEstimate = 공고 사업금액 축(`ProvenanceRulesTest` 가 정하는 쪽), winningAmount = `finalAwardAmount`, winningRate), recoveryEstimate = Fact.Absent, policy = 기존 `ProvenancePolicyData` 정본).classification` → `BaseAmountProvenance` → wire 라벨. src/main 최초 호출부 — (2b) 등재 | 두 번째 분류기 금지 |
| D-4B7-9 | `OpportunityAnalysis.analyze` 가 port 로 표본을 얻어 `predictionRequestFor(…, supply)` 인자로 넘긴다. 표본 축: `categoryCode = 표본 공고의 공종`(Present), `agencyId = null`(NOT_COLLECTED_YET). 요청 축 `businessCategory` 는 기존 그대로. 조회 실패(예외)는 표본 0건 + 사유 계수가 아니라 **`Unavailable(…)` 로 접지 않고 표본 0건으로 보낸다** — 아니다: 조회 자체의 실패는 `CompetitionSampleSupply.Unavailable(reason)` 결과 값으로 나르고 `analyze` 는 `ScoreFact.Absent(ScoreNotProvided)` — 예외를 던지지 않는다 | sealed 결과 관례 · 4D-1 fail-safe |

## 위협·우회(설계 검토 정본 `_workspace/m4-4b7/02_design-review.md`)
(1) 미래 표본 누출(대상 공고 개찰일 이후 표본) → D-4B7-3 기준 시각 필터 + 대상 공고 자신 제외 · (2) 예비가격 14행인 공고를 「대충 15」로 채움 → ③ 정확 일치 · (3) 추첨 번호 범위 밖 → `OutOfRange` 제외 · (4) 낙찰율/1순위율 없는 협상 계약 → `RANK_ONE_RATE_MISSING` 제외·계수 · (5) 기초금액 오염 표본이 CLEAN 으로 → 라벨은 `ProvenanceRules` 가 붙이고 엔진이 거른다(공급 측 CLEAN 강제 없음 — 두 번 거르면 계수가 어긋난다) · (6) 예비가격 basis 오판 → 엔진 `CENTER_OUT_OF_BAND`(0.8~1.2) fail-closed(D-4B7-1 근거) · (7) SQL 에 판정 로직(WHERE 절에 자격) → D-4B7-4 · (8) 표본 공급이 `analyze` 를 죽임(DB 예외) → `Unavailable` 결과 값 · (9) 다른 공종 표본 혼입(공종 코드 정규화 불일치) → 5D-3 과 같은 정규화 함수(Kotlin 측은 `CategoryCode` 값 동일 비교 — 정규화가 수집 시점에 됐는지 verifier 표적) · (10) 상한 `maxSamples` 로 오래된 표본만 남음 → 최신 순 정렬 · (11) `selected_numbers` 중복/0 → `Set` + `init` ≥1 · (12) `Published(round)` 위조 — 표본 공고의 회차 그대로(값을 지어내지 않음).

## (2b) 값 획득 축
| 표면 | 처분 |
| --- | --- |
| `CompetitionSamplePort`·`CompetitionSampleQuery`·`CompetitionSampleSupply`(public, workflow) | 연다(port·값) — 구현체 주입은 없던 권한 아님(`BidPredictionPort` 와 같은 자리). `Supply` 는 값을 지어내지 않음(도메인 쌍에서만) |
| `OpportunityAnalysis` 보조 생성자 +1 port | 기존 관례 확장 |
| `ReserveDrawObservation`(workflow.prediction, public) | 값 타입, `init` 이 형태 강제 |
| `SampleEligibility`(internal 함수들) | 닫는다 — 같은 패키지 |
| `ProvenanceRules.judgeRow` 호출(최초 production 호출) | 기존 public 함수 소비 — 새 권한 아님 |
| `JdbcCompetitionSampleSource`(public class, adapters) | RO — 쓰기 없음. 조립 근이 없어 test 만 생성 |
| 「`object` 커널 주입」 | 없음. 「경계로 처리」 행: 없음 |

## 종결 조건
자격 규칙표 test(사유 7) · 조회 test(창·상한·최신 순·대상 제외·공종 일치 — 기존 persistence test 관례로 실 DB/컨테이너) · `toProto()` 7번 필드 왕복 · `OpportunityAnalysis` 통합 test(fake port → 요청에 표본 n·공종 축 Present·agency NOT_COLLECTED_YET) · Unavailable 경로 test · 전건 `check` · verifier ready · 사용자 승인. `OPEN-4D3-SAMPLE-SUPPLY` 닫힘(2계층), 4D-1 알려진 제한 2 해소, `OPEN-4B7-QUERY-INDEX`·`OPEN-4B7-POLICY-VALUES`·`OPEN-4B7-TARGET-LABEL` 등재.

## 하네스 레인 변경
없음(리뷰 요청 시점에 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 갱신).

## 계약 갱신 이력
| 날짜 | 변경 | 사유 |
| --- | --- | --- |
| 2026-09-16 (구현 중 발견, 팀장 흡수) | **D-4B7-4 보정 ①** — 자격 판정 함수 `judgeEligibility` 와 `OPENING_DATE_ZONE` 은 `internal` 이 아니라 **public**(workflow 모듈). `CompetitionSamplePort.samplesFor` 가 판정 끝난 `Supply` 를 반환하므로 판정은 port 구현(다른 Gradle 모듈 `adapters`) 안에서 끝나야 한다 — 순수 함수라 새 권한 없음((2b) 행 추가). **② in_scope 경로 보정** — `JdbcCompetitionSampleSource.kt`(+test) 는 `adapters/persistence/` 가 아니라 **`adapters/ml/`**: `PersistenceAdapterDependencyTest` 가 persistence 패키지의 `decision`·`workflow` import 를 막고, `adapters.ml` 은 그 넷을 이미 허용한다(`RequestMapping.kt` 와 같은 사유). **③ 어댑터 입력 보정** — `MlAdapterDependencyTest`(allow-list)가 형제 패키지 `adapters.persistence` import 도 거부하므로 JDBC 구현체·`Sql.kt` 를 재사용하지 못한다: 후보 id 는 클래스 안의 `SELECT_COMPETITION_SAMPLE_CANDIDATES`(공종 정확 일치·대상 제외·`actual_opening_at` 창·`ORDER BY … DESC NULLS LAST LIMIT`) 로 뽑고, 복원은 `procurement` port 인터페이스(`NoticeRepository`·`OpeningResultRepository`, 생성자 주입) `find(id)` 단건 — **N+1**(`OPEN-4B7-QUERY-INDEX` 에 함께 등재). `Sql.kt` 순변경 0. 실 JDBC 구현은 조립 근(M6)이 주입 | 기존 경계 게이트가 구조적으로 강제 — 게이트를 완화하지 않고 배치를 바꾼 것이 맞다(게이트 술어 무변경). verifier 표적: 이 배치가 (2b)·경계 test 를 새로 여는지 |
| 2026-09-16 (구현 중 발견) | **`OPEN-4B7-CATEGORY-NORMALIZATION` 신설** — 우회 (16) 실측: `CategoryCode.init` 은 공백만 거부하고 대소문자·내부 공백을 정규화하지 않는다. 조회 술어는 정확 일치만(지시대로 정규화를 두지 않음) — 수집 시점 정규화 여부는 M3 소관 | 5D-3 D-5D3-1 과 같은 축, Kotlin 측 정본 미정 |
| 2026-09-16 (verifier r1 F-8·F-11, 팀장) | **in_scope 보정** — 실제 변경 둘 추가: `adapters/src/main/kotlin/bidvector/adapters/ml/MoneyMapping.kt`(`toProtoReserveDraw` 신설)·`workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt`(신설 — `SampleEligibility` 의 변환부 분리). **D-4B7-9 문면 보정** — 조회 실패(`Supply.Unavailable(reason)`)는 `ScoreFact.Absent(ScoreNotProvided)` 가 아니라 **`Absent(supply.reason)`**(예: `TransportFailed`) 로 사유를 그대로 나른다 — 구현이 더 정직하고 4D-1 사유 체계와 맞다. `predictionRequestFor` 의 표본 인자는 기본값 없음(호출부 누락 = 컴파일 실패). **D-4B7-2 규칙표 +2** — `CANDIDATE_VANISHED`(스캔과 복원 사이 행 부재 — N+1 구조의 귀결, verifier F-3)·`RESERVE_PRICE_SEQUENCE_INVALID`(순번 집합 ≠ 1..N — 엔진이 `selected_numbers` 를 1-기반 인덱스로 소비, verifier F-4): 자격 사유 9 | 5D-3 F-1 과 같은 클래스(와일드카드 밖 신설 파일) · 「조용한 drop 금지」·순번 축 결합의 명시 |
| 2026-09-16 (구현 중 발견) | **D-4B7-8 보정** — `ProvenancePolicyData` 의 운영 정본(임계 넷·순서)이 `decision` 모듈에 없어 workflow 쪽 `SAMPLE_PROVENANCE_POLICY` 잠정 인스턴스(legacy `base_amount_basis.py` 값 그대로)로 둔다 — `OPEN-4B7-POLICY-VALUES` 확장(창·상한·건수 15·임계 넷). `budgetEstimate` = `Notice.estimatedAmount`(legacy `Project.budget_estimate` = 추정가격, data-dictionary §1.2) | 두 번째 분류기 금지는 지켰고(기존 `judgeRow` 호출), 정책 값만 잠정 |
