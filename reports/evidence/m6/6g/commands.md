# M6/6G Kotlin 수집 레인 — 실행 명령과 결과

- base `678c6ed7` · worktree `bid-vector-v2-m6-6g` · 브랜치 `m6-6g/2026-09-27`
- 이 문서는 **Kotlin 레인**의 기록이다(Python 실험 레인은 자기 기록을 갖는다).

## acceptance (CI job 명령 그대로)

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon check` | BUILD SUCCESSFUL — 컴파일 · ktlint · detekt · sizeGate · 의존 방향 · architecture test · 계약 게이트 전부 |
| `./gradlew --no-daemon qualityBaseline` | BUILD SUCCESSFUL(게이트가 아니라 측정) |
| `./tools/one-command-check.sh` | 「Kotlin 전건 + Python 전건 통과」 |

실측 HEAD: `51528f2c`(이 레인의 마지막 산출물 커밋 시점의 트리).

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
| D-6G-11 「기본값 없음」이 **거동**인가 | `OpeningCollectionWiringTest`·`SnapshotExtractionWiringTest` | 상한 둘·seed·층당 목표·키가 없으면 기동 실패 · 값 어긋남·미등재 업종·평문 http 도 실패 · mode 없으면 갈래가 안 뜬다 |

## 수정 라운드 1 이 바꾼 것 (요약)

`OPEN-6G-BASE-AMOUNT-OPERATION` 뒤에 남아 있던 **실데이터면 승인 0건**인 결함 셋을 걷어냈다:
추첨번호가 상수 `null` · 공고일이 개찰일로 접힘 · 값 결측 한 행이 스냅숏 전체를 거부. 셋 다 손으로 쓴
fixture 아래에서 초록이었고, **레인 간 왕복 golden**(출하 추출 경로가 낸 바이트를 Python 이 읽는다)이
그 구멍을 닫는다. 호출 상한은 실행 사이에 이어지도록 영속 원장에서 seed 한다.

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
| 상세 단계의 쿼터 멈춤을 무시한다(K6) | **RED**(13 중 1 실패) — 잠긴다 |
| 예산의 「날」을 조회 대상 공고일로 센다 | **RED** — 공고일 슬롯마다 일 회계가 0 으로 되돌아 일 상한이 아무것도 막지 못한다 |
| 첫 페이지 throttle 의 `settle(-1)` 을 던지게 되돌린다 | **RED** — 이미 실 호출을 쓴 run 이 통째로 죽는다 |
| `opening_base_amount` 에 다른 값을 꽂는다(두 기초금액 뒤바뀜 부류) | **RED**(11 중 1 실패) — 잠긴다 |
| 종료 자리를 공고 목록 배선 안에만 둔다(원래 코드) | **RED** — 개찰 축·추출 갈래를 혼자 켜면 기동 실패. E2E 는 test 설정의 `@Primary` 가 가려 못 봤고 **배선 test 가 잡았다** |
| 예산의 「날」을 조회 대상 공고일로 센다 | **RED** — 공고일 슬롯마다 일 회계가 0 으로 되돌아 일 상한이 아무것도 막지 못한다. 이 변이는 **처음에 실제 코드였다**(test 가 잡았다) |

이 자리는 **처음에 잠겨 있지 않았다.** fixture 의 두 기초금액이 같은 값이라 어느 쪽을 써도 출력이 같아
뒤바뀜이 드러나지 않았다(Python 레인이 자기 쪽에서 같은 함정을 겪고 알려 왔다). 둘을 다르게 둔 test 를
더해 잠갔다 — **두 칸을 가른 의미는 값이 다를 때만 드러난다**.

## 레인 간 왕복이 잡은 것 (D-6G-27 의 값)

`BID_CLOSE_AT_ABSENT` 가 **한 번도 발화하지 않던 자리**는 어느 한 레인의 fixture 안에서도 보이지
않았다. 추출 쪽 provenance 규칙(마감이 없으면 기초금액을 싣지 않는다)과 소비 쪽 규칙 순서가 **함께**
만든 것이라, 두 레인이 같은 바이트를 볼 때만 드러난다. 소비 레인이 규칙 순서를 고쳐(마감 결측이 뿌리
원인이고, 「기초금액이 없거나 늦다」로 이름 붙이면 판정문이 사실과 다른 말을 한다) 네 사유가 각각 제
이름으로 나온다.

생산 쪽에서 고치는 길(마감이 없을 때 기초금액을 그냥 싣기)은 **택하지 않았다** — 투찰 시점에 알 수
없었는지 검증하지 못한 값이 전략 입력으로 들어간다. 그것이 D-6G-19 가 두 칸을 가른 이유다.

## 스테이징 혼입 (사실 선언)

두 레인이 **한 worktree** 를 쓴다. 이 레인의 `commands.md`·`rollback.md` 미커밋 편집이 Python 레인의
evidence 커밋에 함께 실렸다 — 그 레인이 같은 경로를 스테이징할 때 worktree 에 있던 내 줄까지 담겼다.
**이력을 되쓰지 않는다**(공유 이력이다) — 사실로 적는다. 두 레인의 절은 문서 안에서 갈려 있어 내용이
서로 덮이지는 않았다.

**반대 방향도 한 번 있었다**(소비 레인 보고) — 그 레인의 커밋에 이 레인의 golden 두 파일이 딸려
들어갔다. 이력을 되쓰지 않고 사실로 적는다. 그 뒤 이 레인의 커밋이 정본을 덮었으므로 내용은 생산
쪽의 것이다.

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

**앞 라운드에 여기 적었던 「코드는 끝에서 끝까지 선다」는 사실이 아니었다.** 추출이 추첨번호를 상수
`null` 로 두어 실 추출이면 전 행이 제외 ⑤ 에 걸렸고(code-review H-1), 공고일이 개찰일로 접혀 제외 ⑬ 이
개찰일로 돌았다(H-2). fixture 가 손으로 쓴 것이라 재현 test 가 초록이었고 나는 그 초록을 근거로 적었다.

이번 라운드가 그 문장을 **참이 되게 고쳤다**: 두 결함을 고치고, E2E 가 표에 직접 INSERT 하지 않고
수집 갈래(가짜 transport)로 적재한 뒤 추출하며, 그 바이트를 golden 으로 굳혀 두 레인이 같은 바이트를
본다. 남은 것은 **실행**(실 KONEPS 호출·dev DB write)이고 검증 뒤 운영자 승인 아래 연다.

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
| S-3 | `uv run mypy --strict src/ml_engine` | 소스 95개, 오류 0 |
| S-4 | `uv run lint-imports` | 계약 8 유지, 0 깨짐 |
| S-5 | `uv run python -m pytest tests -q` | **1168 passed**(skip 0 — golden 부재는 skip 이 아니라 fail 이다) |
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
| D-6G-27 레인 간 왕복 golden | `tests/app/test_backtest_golden.py` | 생산 v4 바이트가 판독·제외를 통과(여덟) |
| D-6G-31 창 단위 UNDERPOWERED | `verdict.evaluable_window_count` · `_not_evaluable` | 판정 가능 창 과반 미만이면 전체 NotEvaluable |
| D-6G-32 주/보조 유의수준 | `VerdictThresholds.alpha_for` · `run._is_primary` | 보정 전후 **사이**의 p 로 주는 실패·보조는 통과 |
| D-6G-32 표본 결정식 멈춤 | `run._sampling_stop` · `SamplingBudget.minimum_required_sample` | 예산 초과·최소 미달 각각 멈춤 |
| D-6G-39 표본 목록은 파일이다 | `sample_list.parse_sample_list` · `adapters.SnapshotFiles.sample_list_bytes` | 파일 셋이 셋 · 형태 다섯(칸·hex·정렬·개행·짧은 해시) |
| D-6G-39 ⑴ 해시는 **파일 바이트**의 것 | `sample_list.check_sample_list` | manifest 값과 그 파일의 sha256 대조 |
| D-6G-39 ⑵ 행 ⊆ 표본 목록 | 같은 함수의 `stray` | 목록 밖 행은 거부 · **진부분집합은 정상** |
| D-6G-39 ⑵ 선언 == 파일의 키 수 | 같은 함수의 `len(sampled)` 대조 | 선언을 남은 행만큼 줄이는 사후 선택이 잡힌다 |
| D-6G-39 ⑶ 닫힌 항등식 | 같은 함수의 `accounted` | `sample_size == 행 + 상세없음 + 공고없음` |
| D-6G-39 계수 공시 | `report.verdict_payload` | 판정 JSON 이 세 계수를 판 셋마다 싣는다(실측) |
| D-6G-44 golden 부재는 실패 | `test_backtest_golden._golden_files` | golden 을 치우면 RED(치환 실측) |
| D-6G-33 리터럴 게이트 | `test_evaluation_no_stray_numeric_literals` | 문자열에 숨긴 수·조립 근 둘 |
| D-6G-28 값 결측은 행 단위 | `reasons` 의 새 사유 넷 · `AdmittedNotice` 의 해소된 네 칸 | 한 행이 빠져도 **나머지는 산다** |
| D-6G-28 구조 실패는 전체 거부 | `jsonrow.row_mapping` · `_assemble` | 미지 키·버전·checksum·닫힌 셋 밖 업무 넷을 한 test 로 |
| D-6G-33 자유텍스트 | `NoticeObservation.has_*` 두 칸 | 원문 이름이 타입에 **없음**을 전수 대조 |
| D-6G-35 첫 공고 차수 | `exclusion.first_notice_ordinal`(정책) | 값이 `0` 이고 `1` 이면 제외 ③ 이 발화 |
| D-6G-36 결정방법 부재 | `rules._is_single_prearranged` · `UndecidableAxis.PREARRANGED_PRICE_METHOD` | 부재 통과 · 단일예가 제외 · 부재+예비가격 없으면 ⑤ 가 잡음 |
| D-6G-32 manifest 기간 | `snapshot.opening_date_range` · `_check_period` | manifest 기간이 행의 개찰일 범위와 **일치**해야 한다 |
| D-6G-27 golden 자리 단일성 | `test_no_golden_lives_outside_the_declared_path` | 선언 자리 밖 golden 이 있으면 RED |
| 제외 사유의 귀속 | `_RULES` 순서(마감 -> 기초금액) | 마감 결측이 기초금액 사유로 계수되지 않는다 |
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
| **V3** | McNemar p 를 `P(X >= k+1)` 로 | **9 failed** — `comb`+`Fraction` 유리수 산술(구현과 다른 경로)로 낸 손 계산 값과 대조 |
| **V5** | seed 안정성 판정 `return True` | **1 failed** |
| **V7** | 창 이력 `< start - embargo` -> `< window.end` | **1 failed** |
| **V8** | Passed 조건에서 `pooled.passed` 제거 | **1 failed** |
| **V9** | 전략 간 표본 동일성 단언 `return True` | **1 failed** |
| **G1** | 임계를 `float("0.05")` 문자열로 | **2 failed** |
| **G2** | 조립 근(`app/backtest_job.py`)에 임계 리터럴 | **2 failed** |
| **W1** | 공고일이 없으면 개찰일로 대체(v2 회귀) | **1 failed** |
| **W2** | 값 결측을 다시 전체 거부로(필수 판독) | **2 failed** |
| **W3** | 구조 실패를 행 단위로 접음(미지 키 무시) | **6 failed** |
| **P1** | manifest 기간 대조 제거 | **3 failed** |
| **P2** | 기간을 일치에서 **포함 관계**로 약화 | **3 failed** |
| **P3** | 선언된 자리 밖에 golden 을 심는다 | **1 failed** |
| **R1** | S4 시뮬레이션에서 순공사원가선 제거 | **1 failed** |
| **R2** | `_simulated_floors` 호출부를 **옛 인라인으로 되돌림**(H-2 그 자체) | **1 failed** |
| **G3** | 임계를 `Decimal as D` 별칭으로 | **2 failed** |
| **G4** | 임계를 `json.loads("0.05")` 로 | **2 failed** |
| **G5** | 임계를 문자열 연결 `"0." + "05"` 로 | **2 failed** |
| **G6** | 조립 근에 문자열 수 | **2 failed** |
| **N6** | 과반 술어를 `<=` 에서 `<` 로(정확히 절반 경계) | **1 failed** |
| **L1** | 표본 목록 **파일 바이트** 해시 대조 제거 | **1 failed** |
| **L2** | 목록 밖 행 탐지를 빈 목록으로 무력화 | **1 failed** |
| **L3** | 닫힌 항등식 제거 | **처음엔 살아남았다** — 아래 |
| **L4** | 「파일 키 수 == 선언」 대조 제거 | **처음엔 살아남았다** — 아래 |
| **L5** | TSV 칸 수 검사 제거 | **1 failed** |
| **L6** | TSV 오름차순 검사 제거 | **처음엔 살아남았다** — 아래 |
| **L7** | TSV 끝 줄 개행 검사 제거 | **1 failed** |
| **L8** | TSV 소문자 hex 64자 검사 제거 | **2 failed** |
| **L9** | ⑵ 를 `⊆` 에서 `==` 로 되돌림(v3 술어) | **2 failed** — 단, **golden 은 초록** |
| **A1** | golden 을 선언된 자리에서 치운다(현행 fail) | **8 failed**, 부재 메시지로 |
| **A2** | 같은 상태에서 부재 처리를 skip 으로 되돌림 | **6건이 조용히 skip** — 잠금이 서는 자리 |

누계 **서른여덟**(M1~M13 · V3·V5·V7·V8·V9 · G1~G6 · W1~W3 · P1~P3 · R1·R2 · N6 · L1~L9 · A1·A2).
L·A 를 뺀 전부가 RED 이고 복원 뒤 초록이다. A2 는 **초록이 되는 것이 측정값**이다 — 부재를
skip 으로 접으면 소비 test 여섯이 조용히 통과한다는 것을 보이는 대조군이다.

**L3·L4·L6 이 드러낸 것**: 셋 다 처음엔 살아남았고 원인은 구현이 아니라 **test 가 느슨해서**였다.
⑶ 항등식 test 는 표본 크기를 기본값으로 둬서 실은 ⑵ 의 키 수 대조에 걸리고 있었고(항등식을 통째로
지워도 초록), 형태 위반 test 는 사유를 두 개의 합집합으로 받아 형태 검사가 빠진 자리를 계수 대조가
덮었다. 항등식 test 는 선언을 파일 키 수에 **맞춰** 두고 항등식만 깨지게, 키 수 test 는 반대로
항등식을 닫고 키 수만 어긋나게 판을 갈랐다. 형태 test 는 사유를 **정확히 하나**로 좁혔다.
재측정에서 여덟 전부 RED(생존 0).

## 레인 간 왕복 golden — 실제로 결함을 잡았다 (D-6G-27)

생산 쪽(Kotlin `SnapshotWriter` 출하 추출 경로)이 낸 바이트를 소비 쪽 판독·제외에 통과시킨다.
**현황**: 10행 · 승인 2 · 제외 8 = 값 결측 사유 넷이 각각 2건, 모두 제 이름으로. golden test 여덟 통과.

왕복이 **소비 쪽 fixture 로는 영영 못 잡는 결함**을 하나 잡았다. 마감(`bid_close_at`)이 빠진 행이
`BASE_AMOUNT_ABSENT_OR_LATE` 로 계수되고 있었다 — 생산 쪽이 `base_amount_disclosed_at < bid_close_at`
인 행만 기초금액을 싣기 때문에 마감이 빠지면 **기초금액도 함께 `null`** 이 되고, 내 규칙 표가
기초금액을 먼저 봐서 뿌리가 아닌 쪽에 사유를 붙였다. 내 규칙과 생산 쪽 규칙이 **함께** 만든
결함이라 한쪽 레인의 fixture 안에서는 나타나지 않는다. `BID_CLOSE_AT_ABSENT` 를 앞으로 옮겨 고쳤고
test 가 두 갈래를 가른다(마감이 없어 기초금액이 따라 빈 행 ↔ 마감은 있고 기초금액만 없는 행).

golden 이 수렴하기까지 승인 0건의 원인 넷을 단계별로 실측해 생산 레인에 전달했다(추첨번호 4개 ·
A 공개를 마감 앞으로 · 공사 순공사원가 · 투찰자 복수). 추첨번호 4 요구는 **내리지 않았다** —
예정가격이 예비가격 15개 중 **무작위 4개의 평균**이라 4가 아니면 그 평균이 성립하지 않는다.

V3·V5·V7·V8·V9 는 verifier r1 이 **초록**으로 실측한 다섯이고, G1 은 같은 보고의 게이트
변이다. 일곱 전부 이제 붉어진다(복원 뒤 272 passed).

**M10 이 드러낸 것**: 두 기초금액이 같은 fixture 에서는 어느 쪽을 써도 test 가 통과해
provenance 분리가 잠기지 않았다. 두 값을 **다르게** 둔 공고를 만드는 test 를 더해 RED 로
만들었다(재측정 **1 failed**, 복원 251 passed). 변이가 살아남은 것 자체가 산출물이다.

숫자 리터럴 게이트의 하위 패키지 보강도 같은 방식으로 실측했다 — 판정 임계를 리터럴로 바꾸니
리터럴 산포와 출하 임계 누출 둘이 붉어지고, 복원하니 초록이었다.

## 알려진 제한 — 왕복이 덮지 않는 자리

**golden 이 표본 결측 경로를 지나가지 않는다.** 지금 golden 은 `sample_size` 10 · 행 10 · 목록 10 ·
`sampled_without_detail` 0 · `sampled_without_notice` 0 이라 「표본 == 행」이다. v4 가 연 세 자리 중
**진부분집합 경로와 두 결측 계수**가 왕복에서 헛돈다 — 생산 쪽이 상세 못 받은 표본을 목록에서 빼거나
결측 계수를 상수 0 으로 적어도 golden 은 초록이다(지난 라운드 추첨번호 상수 `null` 과 같은 갈래).
소비 쪽 단위 test 는 셋을 각각 잠그고 변이로 RED 를 봤지만(L2·L3·L4) 그것은 이 레인의 fixture 다.
**L9 가 그 구멍을 수치로 보인다**: ⑵ 를 계약의 `⊆` 에서 v3 의 `==` 로 되돌리면 단위 test 둘이
붉어지는데 **golden 여덟은 그대로 초록**이다. 즉 지금 판으로는 왕복이 이 술어의 회귀를 못 잡는다 —
「표본인데 상세를 못 받은 공고가 하나라도 있으면 스냅숏 전체가 거부된다」는 실수집 정지 결함이
생산 바이트를 지나가도 초록으로 통과한다는 뜻이다.
생산 레인에 「상세 없는 표본 하나 · 공고 canonical 없는 표본 하나」를 golden 에 넣어 달라고 전달했다.

## 알려진 제한 (판정 JSON 이 매번 싣는다)

판정 JSON 의 `limitations` 배열이 코드로 고정돼 있어 판정문이 이것들을 숨길 수 없다: 반사실 미측정 ·
지자체 판정 불가 · 선박 분류 코드 미확정 · 예가 범위율/순공사원가 출처 대기 · A 합산의 표준시장단가금액
제외 · 하한가 경계 1원 규칙 미확정 · S1 공사 한정 · S3(GBM) 부재 · 게시 하한율 밴드 미검증.

## 혼입 (사실 선언 — 앞 라운드 문면 정정)

**앞 라운드에 적은 「그 뒤 생산 레인의 커밋이 정본을 덮었다」는 거짓이었다.** 기계로 확인한 이력은
이렇다(`git log -- <golden 경로>`):

| 커밋 | 레인 | 한 일 |
|---|---|---|
| `7b40aa9e` | 생산 | golden 을 합의 자리에 **추가**(4행) |
| `0fa5834e` | **이 레인** | golden 을 **삭제**(그 순간 디렉터리가 없어 넓은 `git add` 가 삭제를 기록했다) |
| `de6f21d9` | **이 레인** | golden 을 **다시 추가**(10행) |

생산 레인의 그 뒤 커밋은 이 경로를 **한 번도 만지지 않았다**(그때 이미 커밋돼 있어 담을 것이 없었다).
즉 **golden 이 git 에 있는 것은 이 레인의 커밋 때문**이고, 마지막으로 만진 것도 이 레인(`de6f21d9`)이다.
앞 문면은 그 사실을 뒤집어 적었다 — 이력은 되쓰지 않고 정정만 한다.

원인은 하나다: `git add ml-engine/tests/` 로 경로를 넓게 잡았고, 이 디렉터리는 상대 레인의 test 실행에
따라 **나타났다 사라진다**. 이번 라운드부터 **파일 단위로만** 스테이징한다(이번 커밋에서 golden 이
수정 상태였으나 들어가지 않았음을 확인했다).

## 대조표가 거짓이었던 자리 (사실 선언)

앞 라운드 대조표는 **D-6G-32 의 「S4 시뮬레이션에 순공사원가선」을 이행으로 적었다.** 실제로는
`_simulated_floors` 가 **정의만 되고 호출되지 않았고**(호출부가 옛 인라인 컴프리헨션을 그대로 썼다),
S4 는 채점이 쓰는 두 실격선 중 하나를 보지 못한 채 투찰률을 골랐다(code-review r2 H-2). 이번 라운드에
호출을 붙이고 변이 둘(R1·R2)로 잠갔다. **대조표에 이행이라 적고 실제가 다르면 그것이 결함이다** —
이 줄을 남겨 둔다.

**같은 갈래가 하나 더 있었다.** D-6G-44 의 「golden 부재는 skip 이 아니라 fail」을 앞 라운드에
**docstring 에만** 적고 코드는 `pytest.skip` 으로 두었다. golden 이 자리에 있었으므로 그 갈래는
실행되지 않았고 초록이 유지됐다 — 문면과 코드가 갈린 것을 아무것도 잡지 못했다. 이번에 `pytest.fail`
로 바꾸고 A1·A2 로 양쪽을 실측했다. **문면이 이행을 주장하면 그 주장을 test 가 받쳐야 한다.**

## 이탈

- **D-6G-39 는 닫혔다(앞 라운드의 이탈 해소).** 스키마 문서가 v4 로 서면서 표본 목록이 파일
  (`sample-list.tsv`)이 됐고, 판독이 요구하던 「행 집합 == 표본 목록」을 계약대로 **⊆** 로 바꿨다.
  v3 까지의 `sample_list_sha256` 대조는 판독기가 행에서 역산한 값을 manifest 와 맞추는 **순환**
  이어서 「결과를 보기 전에 표본이 확정됐다」를 아무것도 검사하지 못했다 — 지금은 파일 바이트가
  대조 대상이라 실패할 수 있다. 항등식·키 수·형태까지 넷을 변이로 실측했다(L1~L8).
- **`smkpAmt` 배제의 「크기」는 여전히 못 잰다(계수는 낸다).** D-6G-23 의 술어 칸이 `snapshot-v2` 로
  들어와 **영향 범위**(참 공고 수·판정 불가 수·분모)는 공시한다. 하지만 「A 가 얼마나 달라지는가」는
  `smkpAmt` 의 **금액**이 스냅숏에 없어 모른다 — `limitations` 의
  `STANDARD_MARKET_PRICE_MAGNITUDE_UNMEASURED` 가 그 한계를 매번 싣는다. 금액 칸은 또 한 번의
  `schema_version` 인상이라 이 판에서 하지 않았다(계약이 요구한 것은 계수다).
- **파생 정책으로 재현 test 를 돈다**(위 §4 주석). 출하 임계로 돌리면 CI 가 수 분을 잡는다.
  판정식 축은 출하 값 그대로이고 그 목록을 test 가 단언한다.
