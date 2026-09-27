# M6/6G Kotlin 수집 레인 — 실행 명령과 결과

- base `678c6ed7` · worktree `bid-vector-v2-m6-6g` · 브랜치 `m6-6g/2026-09-27`
- 이 문서는 **Kotlin 레인**의 기록이다(Python 실험 레인은 자기 기록을 갖는다).

## acceptance (CI job 명령 그대로)

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon check` | BUILD SUCCESSFUL — 컴파일 · ktlint · detekt · sizeGate · 의존 방향 · architecture test · 계약 게이트 전부 |
| `./gradlew --no-daemon qualityBaseline` | BUILD SUCCESSFUL(게이트가 아니라 측정) |
| `./tools/one-command-check.sh` | 「Kotlin 전건 + Python 전건 통과」 |

실측 HEAD: `7e4e6c48`(이 레인의 마지막 산출물 커밋 시점의 트리).

## 실 KONEPS 호출

**없다.** 이 레인은 코드·test·evidence 까지다. 모든 시나리오는 loopback in-process mock server
(`MockKonepsServer`, 소켓은 127.0.0.1 뿐)에서 돈다. 서비스 키는 합성값이고 운영 키를 쓰지 않았다.
실수집은 검증 뒤 운영자 키로 팀장이 연다.

## A-1 호출 상한 (승인 수치의 자리)

일 **20,000** · 총 **80,000**. 코드에 **기본값이 없다** — 두 값 모두 설정이 주어야 예산 원장이 선다.
원장이 「한 걸음 통째로 허가/거부」를 진다는 것과 일·총 두 한도가 서로 다른 사유로 무는 것은 test 가 잠근다.

## `OPEN-6F8-QUOTA-XML-ENVELOPE` — 닫았다

| 축 | 이전 | 지금 |
|---|---|---|
| 게이트웨이 XML 오류 봉투 | JSON 파서가 실패 → `StructureFailure` → 다음 슬롯 | 코드 원소를 읽어 **JSON 봉투와 같은 범주표**를 지난다 |
| 일 한도 초과(봉투 quota 범주) | 재시도 소진 뒤 멈춤(거부될 호출 여럿) | **재시도 없이 즉시** `QuotaExhausted` → 실행 멈춤 |
| HTTP 429(속도 한도) | 재시도 | **그대로 재시도**(legacy 실측 「~2분 안에 회복, 원인은 동시성」) |

판독은 문자열 탐색이 아니라 문서 구조다 — 한도 초과 메시지의 문면을 맞춰 보면 게이트웨이가 문구를
바꾸는 날 조용히 열린다. DTD·외부 엔티티는 끈다(오류 본문은 신뢰 경계 밖 바이트다).

**이 개정이 바꾼 기존 판정**: M3 의 두 test 가 「`resultCode 22` 는 재시도 대상」을 잠그고 있었다.
일 트래픽 한도는 백오프로 회복되지 않으므로 그 판정을 새 판정으로 바꿨다(두 test 모두 개정하고 사유를
문서에 남겼다). 게이트 술어를 바꾸는 변경이라 **표적 재검증 대상**이다.

## 누출 스캔

참조형: `grep -rniE -f config/quality/leak-patterns.txt <이 레인의 산출물 경로>` → **0건**.

## 이 레인이 만든 새 public 표면

| 표면 | 자리 |
|---|---|
| `SourceEndpoint.BID_PRICE_FORMULA_A`·`BASE_AMOUNT_DETAIL` | procurement — 다섯째·여섯째 수집 축 |
| `FieldConcept` 24 토큰(낙찰방법 2 · 예정가격결정방법 · 공고게시일시 · A 축 11 · 기초금액 조회 축 6 · D-6G-22 둘 · 물품 표기 하나) | procurement |
| `OpeningResultSourcePort.fetchBidPriceFormulaA`·`fetchBaseAmount` | procurement — 포트 메서드 둘 |
| `noticeIdIn` | procurement — 표본틀이 (공고번호,차수)만 얻는 자리(원문 키는 이 모듈 안에 남는다) |
| `CollectOpeningResultsUseCase`·`OpeningCollectionSource`·`OpeningCollectionHalt`·`OpeningCollectionPlan`·`OpeningCollectionReport` | workflow/collection — 수집 갈래 |
| `SnapshotWriter`·`SnapshotRow`·`SnapshotNotice`·`SnapshotOutcome`·`SnapshotBidderRow`·`orderedBidderRows`·`SNAPSHOT_SCHEMA_VERSION`(`snapshot-v2`) | adapters/snapshot — 스냅숏 바이트 |
| `NoticeKeyHash` · `SamplingSeed` · `SampleStratum` · `SampleCandidate` · `StratumOutcome` · `SampleOutcome` · `StratifiedSampler` | workflow/collection |
| `CollectionCallBudget` · `CallBudgetLedger` · `BudgetOutcome` · `BudgetLimit` | workflow/collection |

`KonepsOperationPolicy.BID_PRICE_FORMULA_A`·`readGatewayErrorCode` 는 `internal` 이라 표면이 아니다.

## 계약 문장 ↔ 심볼 대조

| 계약 문장 | 심볼 | 무엇이 잠그는가 |
|---|---|---|
| D-6G-11 「일 한도 초과는 멈춤, 429 는 직렬 유지」 | `isRetryableStep` 의 봉투 분기 | 「즉시 멈춘다」·「429 는 재시도된다」 두 test |
| D-6G-11 「공고 식별자 해시 + 정책 seed 로 결정적으로」 | `StratifiedSampler.select` | 같은 seed 재현 · 후보 순서 무관 · seed 다르면 다름 |
| D-6G-11 「층 = 업무 대분류 × 공고 주」 | `SampleCandidate.stratum` | 업무 가름 · 주 가름 두 test |
| D-6G-11 「표본 목록 sha256」 | `SampleOutcome.sampleListSha256` | 정렬 목록에서만 나온다는 test |
| 우회 ⑦ 「결과를 보고 표본을 고른다」 | `SampleCandidate` 의 칸 | **타입**이 막는다 — 결과를 담을 칸이 없다 |
| D-6G-11 「호출 상한 — 설정값, 기본값 없음, 초과 시 멈춤」 | `CollectionCallBudget`·`CallBudgetLedger` | 기본값 없음 · 일/총 두 사유 · 한 걸음 통째 거부 |
| D-6G-12 「낙찰방법(`sucsfbidMthdCd`·`Nm`)」 | `AWARD_METHOD_CODE`·`AWARD_METHOD_NAME` | 관측까지 닿는 wire test(계약 표만 보는 test 는 이것을 못 잰다) |
| D-6G-12 「A값 오퍼레이션 + 합산 여부 술어 + A 공개일시」 | `SourceEndpoint.BID_PRICE_FORMULA_A` 축 13행 | 관측 생존 · 요청 축 · allow-list 반전 · 두 일시 분리 |
| D-6G-12 「예정가격 결정방법」 | `PLANNED_PRICE_DECISION_METHOD` | 계약 원장 test |
| D-6G-13 제외 열다섯 | `snapshot-schema.md` §4 의 입력 대조표 | 판정은 Python 레인 모듈이, 입력은 이 레인이 |
| D-6G-2 「스냅숏 · manifest」 | `SnapshotWriter` | 정렬·키 순서·이스케이프·순번·manifest 해시 · 기초금액 두 칸 분리 열한 test |
| D-6G-23 「표준시장단가 적용 여부」 | `a_value.standard_market_price_applicable`(`snapshot-v2`) | A 묶음 셋 동반 · A 없으면 술어도 없음 |
| D-6G-1 「개찰결과 수집 갈래」 | `CollectOpeningResultsUseCase` | 표본만 상세 호출 · 표본 사전 확정 · 층 가름 둘 · 원문 적재 |
| D-6G-19 「기초금액 조회 op 5·6·7」 | `SourceEndpoint.BASE_AMOUNT_DETAIL` 축 6행 + `BASE_AMOUNT_DETAIL` 서술자 | 모든 업무에서 부른다는 test |
| D-6G-19 「provenance 분리」 | `SnapshotNotice.baseAmount` ↔ `SnapshotOutcome.openingBaseAmount` | 타입이 두 칸으로 가른다 |
| D-6G-20 「공고당 호출 = 상세 셋(+공사 A값)」 | `detailAxesFor` | 공사 넷·그 밖 셋, 소진 `when` |
| D-6G-22 「새 호출 비용 0 칸」 | `AWARD_METHOD_APPLICATION_STANDARD`·`APPLICATION_BASIS_CONTENT` | 계약 원장 test |
| D-6G-1 「수집 모드 한 갈래」 | `OpeningCollectionWiring`(`mode=once` 일 때만) | 출하 조립 E2E 셋 |
| D-6G-11 「호출 상한은 설정값, 기본값 없음」 | `OpeningCollectionProperties` | 상한 도달 시 멈춤·종료 코드 미완 E2E |
| D-6G-2 「저장소 밖 출력 · manifest」 | `SnapshotExtractionRunner`·`JdbcSnapshotSource` | Testcontainers E2E 셋(바이트 결정성·상호 부재·provenance 분리) |

## 스키마 합의 (2026-09-27, 두 레인)

초판(평평한 한 객체 · 금액 문자열 · `exclusions` 배열)과 Python 레인 독자 초안이 엇갈려 **Python 레인의
구조를 채택**했다 — `notice`(투찰 시점) / `outcome`(개찰 시점) 분리가 누출 금지를 규율이 아니라 **타입**으로
닫는다(전략이 예정가격을 보려면 타입을 고쳐야 하고 그 편집이 diff 에 드러난다). 금액 문자열 표기는 철회했다
— 소비 쪽 산술이 `float` 전 구간이라 읽는 즉시 float 이 되어 churn 만 남는다(금액은 JSON 정수로 못박아
왕복만 exact 하게 둔다). 생산 쪽 정정은 nullability 와 판정 가능성 — 상세는 `snapshot-schema.md`.

## 알려진 제한

1. **기초금액 조회 축의 채움률이 실측 전이다.** `OPEN-6G-BASE-AMOUNT-OPERATION` 은 D-6G-19 구현으로
   닫혔다 — op 5·6·7 을 수집 축으로 열어 예가 범위율·순공사원가·A값 공고 여부가 모두 경로를 갖는다.
   다만 값이 실제로 얼마나 차 있는지는 모른다: 문서 XML 예제에서 `bssAmtPurcnstcst` 가 빈 값이고
   `sucsfbidMthdAppStd` 는 예제 여덟이 전부 빈 값이다. 제외 ⑨가 전량에 걸릴 수 있다 — 실수집 뒤
   채움률을 판정문에 공시한다.
2. **호출 예산은 걸음 단위 근사다.** 한 걸음(공고 하나의 상세 조회)이 여러 페이지를 걸으면 그 걸음의
   실제 호출 수는 걸은 뒤에 알려진다 — 초과분은 최대 한 걸음의 페이지 수다. 어댑터 안쪽에 예산을
   넣으면 정확해지지만 의존 방향이 뒤집힌다.
3. **제외 ⑪(지자체 발주)을 판정하지 못한다.** 기관 코드에서 지자체를 가르려면 「행자부 코드 공간 ↔
   지자체」 대응이 필요한데 authoritative 하게 확보하지 못했다 — 지어내면 DEC-03(운영자 확정 범위)을
   코드가 조용히 재정의한다. `demand_agency_code` 를 입력으로 싣고 판정은 연다. 신설
   **`OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`**. 용역·물품의 「A값 공고인가」도 같은 성질이다 —
   `bidPrceCalclAYn` 이 공사기초금액조회에만 있어 A 항목 합이 0 인지로 간접 판정할 수밖에 없다.
4. **`smkpAmt`(표준시장단가금액)의 합산 근거 예규 문면을 확보하지 못했다.** 응답이 적용 여부 술어를
   함께 주므로 별도 규율이 있다는 뜻이지만, 계약은 관측값과 술어를 나르기만 하고 합산 판단을 하지 않는다.
5. **A 합산 항목의 `basis` 가 `null`이다.** A 는 기초금액·예정가격·낙찰금액 어느 축도 아니라 산식의
   항이고, 그 셀에 맞는 basis 어휘가 승인 표에 없다(미확정 칸은 인스턴스화하지 않는다).
6. **`bidNtceOrd` 를 A 오퍼레이션의 행 식별자로 쓴다.** 문서가 요청 항목으로 적지 않아 보내지 않고,
   응답의 차수 키가 행을 가른다. 한 공고번호·한 차수에 행이 둘 이상이면 뒤 행이 `duplicate` 로 접힌다
   — 그런 응답의 관측이 아직 없다.

## 변이 실측 (이 레인)

| 변이 | 결과 |
|---|---|
| `opening_base_amount` 에 다른 값을 꽂는다(두 기초금액 뒤바뀜 부류) | **RED**(11 중 1 실패) — 잠긴다 |
| 예산의 「날」을 조회 대상 공고일로 센다 | **RED** — 공고일 슬롯마다 일 회계가 0 으로 되돌아 일 상한이 아무것도 막지 못한다. 이 변이는 **처음에 실제 코드였다**(test 가 잡았다) |

이 자리는 **처음에 잠겨 있지 않았다.** fixture 의 두 기초금액이 같은 값이라 어느 쪽을 써도 출력이 같아
뒤바뀜이 드러나지 않았다(Python 레인이 자기 쪽에서 같은 함정을 겪고 알려 왔다). 둘을 다르게 둔 test 를
더해 잠갔다 — **두 칸을 가른 의미는 값이 다를 때만 드러난다**.

## 스테이징 혼입 (사실 선언)

두 레인이 **한 worktree** 를 쓴다. 이 레인의 `commands.md`·`rollback.md` 미커밋 편집이 Python 레인의
evidence 커밋에 함께 실렸다 — 그 레인이 같은 경로를 스테이징할 때 worktree 에 있던 내 줄까지 담겼다.
**이력을 되쓰지 않는다**(공유 이력이다) — 사실로 적는다. 두 레인의 절은 문서 안에서 갈려 있어 내용이
서로 덮이지는 않았다.

그 귀결로 이 문서의 「내 절」을 담은 커밋과 그 커밋의 메시지가 어긋난다. 이 절 자체가 그 대조표다.

## 하지 않은 것 (이 레인의 몫 가운데)

| 몫 | 상태 |
|---|---|
| 수집 갈래 use case | **했다** — 표본틀·표본·상세 넷, 예산·쿼터 멈춤 |
| 수집 갈래 **Spring 배선 + 가짜 transport E2E** | **했다** — 출하 조립 부팅, 실 KONEPS 호출 0 |
| 스냅숏 바이트 생성(JSONL·manifest) | **했다** — `SnapshotWriter` |
| 스냅숏 **DB 판독 어댑터 + 추출 CLI + Testcontainers E2E** | **했다** — 읽기만 한다(DB write 0) |
| **실수집·실추출 실행** | **안 했다** — 운영자 키·승인 아래 팀장이 연다 |
| 제외 사유 판정의 구현 | **이 레인 몫이 아니게 됐다** — 두 레인 합의로 판정은 Python 쪽 한 자리에서 하고, 이 레인은 입력 칸만 진다(스키마 §4). 판정 자리가 둘이면 어긋날 때 정본이 없다 |

코드는 끝에서 끝까지 선다. 남은 것은 **실행**뿐이고 그것은 실 KONEPS 호출과 dev DB write 가 필요한
지점이라 검증 뒤 운영자 승인 아래 연다.

**두 갈래가 다 돌아야 스냅숏이 선다** — 추출은 개찰 축 원문(raw_observation)과 canonical notice 를
잇는다. 대분류·낙찰하한율·마감일시는 개찰 축 응답에 **없어서** 공고 목록 갈래가 세운 canonical 에서만
온다. 공고 목록 관측이 없는 공고는 행이 만들어지지 않고 그 수가 계수된다(지어내지 않는다).

---

# Python 레인 (ml-engine) — acceptance 와 대조표

> 이 절은 Python 레인(`ml-engine/**` 와 정책 파일)의 것이다. 위 절들은 Kotlin 레인이 쓴다.
> **Gradle 은 이 레인에서 돌리지 않았다**(호스트 무거운 빌드 1개 규율 — Kotlin 레인 몫).

## acceptance — CI `ml-engine` job 의 명령 그대로

`.github/workflows/ci.yml` 의 `ml-engine` job 단계를 순서대로 돌렸다(작업 디렉터리 `ml-engine/`).
결과는 핵심 한 줄만 적는다.

| 단계 | 명령 | 결과 |
|---|---|---|
| S-1 | `uv sync --frozen --all-extras` | 성공 |
| S-2 | `uv run ruff check .` / `uv run ruff format --check .` | 위반 0 |
| S-3 | `uv run mypy --strict src/ml_engine` | 소스 93개, 오류 0 |
| S-4 | `uv run lint-imports` | 계약 8 유지, 0 깨짐 |
| S-5 | `uv run python -m pytest tests -q` | **1119 passed**(6G 추가분 포함) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 위반 0 |
| S-7 | `uv run python tools/reuse_provenance_check.py` + 양성 대조 | 통과(6G 신규 모듈은 이식이 아니라 대상 밖) |

`ml-engine` job 의 나머지 단계(S-1b serving extras 분리 · S-9 Python 버전 대조 · S-11 wheel 재수출)는
이 레인이 건드린 축이 아니고 CI 가 같은 명령으로 돈다.

**실험 실행 자체(실수집·스냅숏·판정)는 acceptance 가 아니라 산출물**이고, 이 레인은 **실 데이터를
돌리지 않았다** — 코드·test·evidence 까지다.

## 계약 문장 ↔ 심볼 대조표

| 계약 | 심볼 | 잠그는 test |
|---|---|---|
| D-6G-2 불변 스냅숏 · 누출 금지 타입화 | `backtest.snapshot` 의 `NoticeObservation` / `OpeningOutcome` | 개찰 결과 이름이 `notice` 반쪽에 없음을 전수 대조 |
| D-6G-3 S0 밴드 내 균등 난수 | `UniformBandStrategy` | 밴드 안·seed 결정성·공고별 차이 |
| D-6G-3 경쟁 표본은 개찰일 이전만 | `build_competitor_pool(before=…)` | 대상 공고를 이력에 **심어** 확인 |
| D-6G-15 S1 = 하한율 + offset, 공사 한정 | `RuleAnchorStrategy` | 산식 일치 · 용역에서 기권 |
| D-6G-3 S2 는 출하 정책 그대로 | `app.backtest_distribution.DistributionEngine` | 조립이 사전 등록 다섯인지 |
| D-6G-3 S4 제도 분포 + 경쟁자 분포 | `InstitutionalMonteCarloStrategy` | 표본 부족 시 기권 · 격자 안 · 결정성 |
| D-6G-4 지표 셋 | `metrics.score_notice` | 실격선 전부 · bp 는 적격일 때만 · 최저 적격 대조 |
| D-6G-10 would-have-won 은 전체 순위 대조 | 같은 함수의 `lowest_eligible_amount` 비교 | 경계 셋(−1·동가·+1) |
| D-6G-5·14 창 | `windows.plan_backtest_windows` | 달력 블록 · embargo · 제외 사유 기록 |
| D-6G-6 판정식·Bonferroni | `verdict.passes_window` · `VerdictThresholds.primary_alpha` | 보정 전후 **사이**의 p |
| D-6G-6 3값 결과 | `StrategyPassed` / `StrategyFailed` / `StrategyNotEvaluable` | 판정 순서 넷 |
| D-6G-6 McNemar 정확 검정 | `mcnemar.one_sided_p_value` | 손으로 센 이항 꼬리와 대조 |
| D-6G-12 업무별 하한가 | `floor.floor_price` + `exclusions.resolve_a_value` | A값 공고의 약 77 bp 차이 |
| D-6G-13 제외와 계수 | `exclusions.admit_rows` · `exclusion_counts` | 사유별 발화 · 0건 공시 · 시그니처에 전략 없음 |
| D-6G-16 없는 값을 메우지 않는다 | `RESERVE_PRICE_RANGE_ABSENT` · `AdmittedNotice` 의 비-`None` 필드 | 범위율 null 에서 제외 발화 |
| D-6G-19 기초금액 provenance 분리 | `AdmittedNotice.base_amount` / `opening_base_amount` · `StrategyInput.base_amount` | 두 값을 **다르게** 둔 공고에서 전략이 투찰 시점 칸을 쓰는지 |
| D-6G-19 A값 공고 판정 | `rules.resolve_a_value` 의 `bid_price_formula_a_applicable` 분기 | 술어 거짓 → A=0 승인 · 술어 없음 → 제외 |
| D-6G-22 채움률 여섯 칸 | `exclusions.fill_rates` | 판정 JSON 이 0.0 도 숨기지 않고 싣는다 |
| D-6G-23 `snapshot-v2` 술어 칸 | `AValue.standard_market_price_applicable` | v1·v3 둘 다 거부 · `null` 수용 · 미지 키 거부 |
| D-6G-17 배제의 영향 범위 | `exclusions.standard_market_price_scope` | 참·판정 불가·분모 셋을 따로 |
| D-6G-20 표본 크기 결정식 | `records.SamplingRecord` · `policy.SamplingBudget` | 예산 초과 시 멈춤 |
| D-6G-21 판정 불가와 민감도 둘 | `UndecidableAxis` · `SampleVariant` | 판 셋의 순서 · `estimate_available` |
| D-6G-22 낙찰방법 채움률 | `exclusions.bid_method_fill_rate` | 판정 JSON 에 실림 |
| D-6G-9 보고에 식별자 없음 | `report.verdict_payload` | fixture 의 공고 키 해시가 판정 바이트에 없음 |
| 재현(위협 모델 ③) | `app.backtest_job.run_backtest_job` | 두 번 돌려 바이트 동일 · 줄 순서 무관 · 정책 한 값으로 달라짐 |

## 변이 실측 (판정식·누출 가드·제외 동일성)

`tests/evaluation` 만 돌려 붉어지는지 봤다(변이 심고 → 확인 → 복원). 복원 뒤 246 passed.

| # | 변이 | 결과 |
|---|---|---|
| M1 | 누출 절단 `<` → `<=`(같은 날 개찰분이 표본에 섞임) | **2 failed** |
| M2 | Bonferroni 제거(`primary_alpha` → `alpha`) | **1 failed** |
| M3 | 하한가 산식에서 A 제거(`예정가격 × r`) | **1 failed** |
| M4 | A 공개일시 절단 제거(마감 뒤 공개된 A 를 통과) | **1 failed** |
| M5 | 예가 범위율 null 을 제외하지 않음 | **2 failed** |
| M6 | would-have-won 을 `<=` 로(동가를 승으로 셈) | **1 failed** |
| M7 | 기권을 「적격·승」으로 셈(표본에서 사실상 제외) | **2 failed** |
| M8 | 투찰 시점 기초금액이 비면 개찰 출처로 메움 | **1 failed** |
| M9 | A 적용 여부 술어를 무시(항상 A값 공고로 취급) | **1 failed** |
| M10 | 전략이 개찰 출처 기초금액을 씀 | **처음엔 살아남았다** — 아래 |

| M11 | 술어를 무시하고 A 값 공고 전량을 셈 | **1 failed** |
| M12 | 판정 불가를 참으로 접음 | **1 failed** |
| M13 | `snapshot-v1` 을 조용히 받아들임 | **78 failed**(판독기 전역) |

**M10 이 드러낸 것**: 두 기초금액이 같은 fixture 에서는 어느 쪽을 써도 test 가 통과해
provenance 분리가 잠기지 않았다. 두 값을 **다르게** 둔 공고를 만드는 test 를 더해 RED 로
만들었다(재측정 **1 failed**, 복원 251 passed). 변이가 살아남은 것 자체가 산출물이다.

숫자 리터럴 게이트의 하위 패키지 보강도 같은 방식으로 실측했다 — 판정 임계를 리터럴로 바꾸니
리터럴 산포와 출하 임계 누출 둘이 붉어지고, 복원하니 초록이었다.

## 알려진 제한 (판정 JSON 이 매번 싣는다)

판정 JSON 의 `limitations` 배열이 코드로 고정돼 있어 판정문이 이것들을 숨길 수 없다: 반사실 미측정 ·
지자체 판정 불가 · 선박 분류 코드 미확정 · 예가 범위율/순공사원가 출처 대기 · A 합산의 표준시장단가금액
제외 · 하한가 경계 1원 규칙 미확정 · S1 공사 한정 · S3(GBM) 부재 · 게시 하한율 밴드 미검증.

## 이탈

- **`smkpAmt` 배제의 「크기」는 여전히 못 잰다(계수는 낸다).** D-6G-23 의 술어 칸이 `snapshot-v2` 로
  들어와 **영향 범위**(참 공고 수·판정 불가 수·분모)는 공시한다. 하지만 「A 가 얼마나 달라지는가」는
  `smkpAmt` 의 **금액**이 스냅숏에 없어 모른다 — `limitations` 의
  `STANDARD_MARKET_PRICE_MAGNITUDE_UNMEASURED` 가 그 한계를 매번 싣는다. 금액 칸은 또 한 번의
  `schema_version` 인상이라 이 판에서 하지 않았다(계약이 요구한 것은 계수다).
- **파생 정책으로 재현 test 를 돈다**(위 §4 주석). 출하 임계로 돌리면 CI 가 수 분을 잡는다.
  판정식 축은 출하 값 그대로이고 그 목록을 test 가 단언한다.
