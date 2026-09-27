# M6/6G 실험 스냅숏 스키마 (D-6G-2) — 레인 간 계약

> **지위: 두 레인이 합의한 계약.** 2026-09-27 초판(Kotlin 레인)과 Python 레인의 독자 초안이 엇갈려, **Python 레인의
> 구조를 채택**하고 Kotlin 레인이 생산 쪽 제약을 반영해 정정했다. 형태를 바꾸려면 `schema_version` 을 올리고 이
> 문서를 먼저 고친다 — 한쪽만 바꾸지 않는다.
> 스냅숏 실물은 **저장소 밖**(`~/.local/bid-vector-snapshots/<snapshot_id>/`)에 둔다(data-extract §7 · ADR 0010 D-8).

`schema_version`: `snapshot-v1` · 파일 둘: `manifest.json` · `rows.jsonl`

## 0. 왜 한 행이 두 반쪽인가

한 줄은 `notice`(투찰 시점에 알 수 있는 것)와 `outcome`(개찰로 드러나는 것)으로 갈린다. 전략에 넘어가는 것은
`notice` 뿐이고 `outcome` 은 채점만 읽는다 — **누출 금지(위협 모델 ①)를 문서가 아니라 타입으로 닫는다.** 전략이
예정가격을 보려면 dataclass 를 고쳐야 하고 그 편집은 diff 에 드러난다. 초판의 평평한 한 객체는 이 성질이 없었다.

## 1. 수치 표현 — JSON 수치로 간다

초판은 금액·비율을 **문자열**로 적었다(부동소수 왕복 회피). **철회한다**: 소비 쪽 산술이 `float`·numpy float64
전 구간이라 문자열로 실어도 읽는 즉시 `float` 이 된다 — 바꾸지 않는 보장을 위해 양쪽에 churn 만 남는다.

대신 **금액은 JSON 정수**로 쓴다(원 단위, 소수점 없음). 1e12 원까지 double 이 정확히 담으므로 왕복이 exact 하다.
비율은 JSON 실수다.

> **경계 1원의 판정은 이 스키마가 결정하지 못한다.** 하한가 `(예정가격 − A) × r + A` 의 **절상·절하 규칙**이
> 미확정이고(선행 조사가 문면을 확보하지 못했다), 그 미확정이 부동소수 오차(~1e-4 원)보다 **훨씬 크다**. 투찰금액이
> 하한가와 1원 차이인 공고의 적격 판정은 그 규칙이 정해질 때까지 미정이다 — 판정문에 한계로 적는다.

## 2. `manifest.json`

```json
{ "schema_version": "snapshot-v1", "snapshot_id": "…", "row_count": 24000,
  "period_start": "2026-02-06", "period_end": "2026-09-26",
  "rows_sha256": "…", "sample_list_sha256": "…" }
```

| 칸 | 뜻 |
|---|---|
| `period_start`·`period_end` | **개찰일** 범위다(창을 자르는 축). 공고일은 행마다 `notice.noticed_on` 이 나른다 — 창 **포함** 판정은 그쪽이다(D-6G-14) |
| `rows_sha256` | `rows.jsonl` **바이트**의 sha256 hex |
| `sample_list_sha256` | §5. 결과를 보기 전에 표본이 확정됐다는 증거(우회 ⑦) |

## 3. `rows.jsonl`

공고 하나 = 한 줄. **`notice_key_hash` 오름차순**으로 쓴다(같은 입력이면 바이트까지 같은 파일 — 재현 test 의
「바이트 동일」이 이것을 잰다). 키 순서는 아래 선언 순서로 고정한다.

### 3.1 `notice` — 투찰 시점

| 칸 | 형 | 정정·주의 |
|---|---|---|
| `notice_key_hash` | string(64 hex) | §5 |
| `category` | `CONSTRUCTION`\|`SERVICE`\|`GOODS` | 닫힌 셋. `FOREIGN` 은 **오지 않는다**(§4 ⑫) |
| `noticed_on` | date | 공고일 — 창 포함 판정(D-6G-14) |
| `bid_close_at` | datetime(offset) | **줄 수 있다**(`bidClseDt` — 이미 계약·canonical 칸에 있다). A 공개 시점 절단의 기준 |
| `base_amount` | int \| null | 기초금액. **기초금액 조회 오퍼레이션의 값만** 싣고, `base_amount_disclosed_at < bid_close_at` 인 행만이다(§3.4) |
| `base_amount_disclosed_at` | datetime(offset) \| null | 기초금액공개일시(`bssamtOpenDt`) — 「그 값이 언제 공개되었나」 |
| `floor_rate` | number \| **null** | fraction(percent ÷ 100) |
| `reserve_range_begin_rate` | number \| null | fraction. 원문은 **% 이고 부호가 문자열 안**에 있다(`-3`) — 추출이 선행 `+` 를 허용해 파싱하고 100 으로 나눈다 |
| `reserve_range_end_rate` | number \| null | fraction(`+3` → `0.03`). **시작률의 반대수라는 보장이 없다** — 비대칭 범위를 지원하라 |
| `a_value` | `{total:int, open_at:datetime}` \| null | `total` 의 합산 규칙은 §3.3 |
| `successful_bid_method_code` | string \| null | `sucsfbidMthdCd` — 문서상 옵션이라 부재 가능 |
| `successful_bid_method_name` | string \| null | `sucsfbidMthdNm` — 같음 |
| `prearranged_price_decision_method` | string \| null | `prearngPrceDcsnMthdNm` — A 오퍼레이션에서만 오므로 그 호출이 없으면 부재 |
| `notice_ordinal` | int | `bidNtceOrd` |
| `progress_division` | string \| null | **추가**(§4 ③) — 유찰·재입찰 판정 입력 |
| `procurement_class_code` | string \| null | **추가**(§4 ⑧) — 선박 제조 물품 판정 입력 |
| `demand_agency_code` | string \| null | **원값**(해시 아님) — 기관 코드는 개인정보가 아니다(상호·사업자번호는 여전히 비수집). 지자체 판정의 *입력*이지 판정이 아니다(§4 ⑪) |
| `bid_price_formula_a_applicable` | bool \| null | `bidPrceCalclAYn` — **공사에만 오는 필수 필드**. 「이 공고가 A값 공고인가」를 한 필드로 답한다. 공사가 아니면 null |
| `pure_construction_cost` | int \| null | `bssAmtPurcnstcst`(공사 전용) — 제외 ⑨의 입력 |
| `award_method_application_standard` | string \| null | `sucsfbidMthdAppStd`(D-6G-22) — 자유텍스트, 채움률 미상 |
| `application_basis_content` | string \| null | `aplBssCntnts`(D-6G-22, 공사 전용) — 자유텍스트, 값 어휘 미상 |

**뺀 칸 둘**: `is_local_government`·`is_foreign_capital`. 사유는 §4 ⑪⑫ — 앞은 **판정할 수 없고**, 뒤는 **구조적으로
항상 거짓**이다. 지어낸 불리언을 싣지 않는다.

### 3.2 `outcome` — 개찰 시점

| 칸 | 형 | 정정·주의 |
|---|---|---|
| `opened_on` | date | 개찰일 — 창 자르기·누출 절단 |
| `planned_price` | int | 예정가격 |
| `opening_base_amount` | int \| null | **개찰결과 출처**의 기초금액. `notice.base_amount` 와 **다른 칸**이다(§3.4) |
| `reserve_prices` | int[15] \| null | **위치 = 순번**이다. 15행이 모두 있고 순번이 1..15 로 빠짐없을 때만 배열을 싣고, 아니면 `null`(부분 배열을 싣지 않는다) |
| `drawn_serial_numbers` | int[] \| null | 1-기반 순번. 개수가 4라는 보장은 하지 않는다 |
| `participant_count` | int \| null | 목록 축 관측이라 부재 가능 |
| `bidder_rows` | 아래 | |

`bidder_rows[]`:

| 칸 | 형 | 정정·주의 |
|---|---|---|
| `ordinal` | int | **추가.** `amount` 오름차순(null 뒤), 동값은 원문 행 순서 — 1부터. **항상 있다** |
| `rank` | int \| **null** | `opengRank` 원문. **결측·중복이 흔하다**(실측: 표본 15건 중 전 행 유일은 4건뿐) — 초안의 non-null `int` 는 깨진다. 순위로 경쟁자 분포를 만들지 말고 `amount` 로 만들어라 |
| `amount` | int \| **null** | 협상에 의한 계약에서는 **부재한다**(문서가 명시) |

동가 1위(D-6G-13 ⑮)는 같은 `amount` 가 둘 이상인 것으로 센다 — 초안 그대로다.

### 3.4 기초금액이 두 칸인 이유 — provenance 분리 (D-6G-19)

기초금액은 두 곳에서 온다: **기초금액 조회**(공개일시를 함께 준다)와 **개찰결과**(개찰 뒤에 보이는 값).
둘을 한 칸에 접으면 전략이 투찰 시점에 알 수 없었던 값을 입력으로 쓰게 된다.

- `notice.base_amount` — 기초금액 조회의 값만. 그중에서도 **`bssamtOpenDt < 입찰 마감`** 인 행만이다.
  공개가 마감보다 늦으면 그 값은 투찰 시점에 없던 값이라 **null 로 두고** 그 공고를 제외 사유로 센다.
- `outcome.opening_base_amount` — 개찰결과 출처. 채점에만 쓴다.

예가 범위율도 같은 규율을 따른다 — 기초금액 조회에서만 오고, 같은 공개일시 조건을 지난다.

> **개찰 뒤 공개되는 예비가격 15개로 반폭 `h` 를 역산하지 마라.** 투찰 시점에 모르는 값이고,
> 역산은 누출이다(팀장 지시 2026-09-27).

### 3.3 `a_value.total` 이 무엇을 더한 값인가

**더한다**: 국민연금보험료 · 국민건강보험료 · 노인장기요양보험료 · 퇴직공제부금비 · 산업안전보건관리비 ·
안전관리비 · 품질관리비(단 `qltyMngcstAObjYn` 이 참일 때만).

**더하지 않는다**: 표준시장단가금액(`smkpAmt`). 예규 원문의 A 일곱 항목 열거에 없고, 응답이 적용 여부 술어를
따로 주지만 **그 술어가 여는 근거 예규 문면을 확보하지 못했다**.

> **이 배제의 방향을 오해하지 마라.** 하한가는 A 에 대해 **증가**한다(`∂/∂A = 1 − r > 0`). A 를 작게 잡으면
> 하한가가 낮아져 **적격이 더 쉽게** 나온다 — 즉 이 배제는 보수적인 쪽이 **아니다**. `smkpAmtYn` 이 참인 공고의
> 수를 따로 세어 판정문에 싣는다(영향 범위를 숨기지 않는다).

## 4. D-6G-13 제외 열다섯 — 판정은 Python 레인이, 입력은 Kotlin 레인이

초판은 `exclusions` 배열을 스냅숏에 실었다. **철회한다**: Python 레인이 이미 입력에서 판정하는 모듈을 갖고 있고,
판정 자리가 둘이면 서로 어긋날 때 어느 쪽이 정본인지가 없다. 스냅숏은 **입력만** 싣고 판정은 한 자리에서 한다.
계약이 요구하는 성질(전략을 보기 전 입력 단계 · 사유별 계수 · 전략 간 동일 집합)은 그 모듈이 전략보다 먼저,
전략과 무관하게 도는 한 그대로 선다.

| D-6G-13 | 입력 | 상태 |
|---|---|---|
| ① 낙찰방법 · ⑦ 소액수의견적 · ⑩ 중소기업자간 경쟁물품 | `successful_bid_method_code`·`_name` | 준다 |
| ② 하한율 부재·밴드 밖 | `floor_rate` | 준다 |
| ③ 유찰·재입찰·정정 | `notice_ordinal` + **`progress_division`** | 칸을 더했다 |
| ④ 단일 예정가격 | `prearranged_price_decision_method` · `reserve_prices` | 준다 |
| ⑤ 예비가격·추첨 결측 | `reserve_prices` · `drawn_serial_numbers` | 준다 |
| ⑥ A 결측·공개 늦음 | `a_value` · `bid_close_at` | 준다 |
| ⑧ 선박 제조 물품 | **`procurement_class_code`** | 칸을 더했다(대분류만으로는 못 가른다) |
| ⑨ 순공사원가 98% | `pure_construction_cost` | 준다(공사) — 문서 XML 예제가 빈 값이라 **채움률은 실측 전이다** |
| ⑪ 지자체 발주 | `demand_agency_code` | **판정은 못 한다**(아래) |
| ⑫ 외자 | — | **구조적으로 0**: 수집 갈래가 외자 오퍼레이션을 부르지 않아 `FOREIGN` 행이 생기지 않는다. 계수는 0 으로 공시하되 「행을 걸러서 0」이 아니라 「들어오지 않아 0」이라고 적는다 |
| ⑬ 시행일 경계 | `noticed_on` | 준다 |
| ⑭ 적격 투찰자 없음 | 하한가 산식 | Python 레인이 붙인다 |
| ⑮ 동가 1위 | `bidder_rows[].amount` | 준다 |
| (P-1.5) 투찰금액 부재 | `bidder_rows[].amount` null | 준다 |

**⑪ 지자체 판정이 없다.** 기관 코드에서 지자체를 가르려면 「행자부 코드 공간 ↔ 지자체」 대응이 필요한데 그 대응을
authoritative 하게 확보하지 못했다 — 지어내면 DEC-03(운영자 확정 범위)을 코드가 조용히 재정의한다.
`demand_agency_code` 를 그대로 싣고 판정은 **열어 둔다**. 신설 `OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`.

## 5. 해시 두 개의 정확한 정의

재현하려면 바이트까지 같아야 하므로 축어로 적는다.

- **`notice_key_hash`** = `sha256("<공고번호>/<차수>")` 의 소문자 hex 64자. 구분자는 `/`, salt 없음, 차수는
  제로패딩 3자리 원문(`"001"`). salt 가 없는 이유: 표본 선택 술어가 같은 값을 다시 계산할 수 있어야 하고
  공고번호 자체가 공개값이다. 이 해시가 지우는 것은 비밀이 아니라 **조인 키**이며, 비식별의 대상은 공고가
  아니라 투찰자다.
- **표본 뽑기 순서** = `sha256("<seed>|<notice_key_hash>")` 오름차순(구분자는 `|`). 층마다 앞에서 N 개.
- **`sample_list_sha256`** = 뽑힌 `notice_key_hash` 를 **오름차순 정렬**해 `\n` 으로 이은 문자열(끝에 개행
  없음)의 sha256 hex.

셋 다 Kotlin 쪽에 이미 구현돼 있고 test 가 잠근다.

## 6. 지금 줄 수 없는 것

1. **⑪ 지자체 판정** — 선행 조사 P-5 로 **authoritative 필드가 없음이 확정**됐다. 기관 코드 원값을 싣고
   판정은 열어 둔다(`OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`). Python 레인의 민감도 분석이 이 칸을 쓴다.
2. **추출 명령 자체가 아직 없다** — 이 문서는 계약이고 구현은 뒤따른다. 수집 갈래(Kotlin)는 섰다.

`OPEN-6G-BASE-AMOUNT-OPERATION` 은 **닫혔다**(D-6G-19 구현) — 예가 범위율·순공사원가·A값 공고 여부가
모두 수집 경로를 갖는다. 다만 **채움률은 실측 전**이다: 문서 XML 예제에서 `bssAmtPurcnstcst` 가 빈 값이고
`sucsfbidMthdAppStd` 는 여덟 예제가 전부 빈 값이다 — 실수집 뒤 채움률을 판정문에 공시한다.

## 7. 판독 규율 (Python 레인 초안 그대로 채택)

미지 키는 조용히 무시하지 않고 **스냅숏 전체를 거부한다**(fail-closed). 상호·사업자번호·담당자가 실려 오는 경로를
금지 목록 열거가 아니라 **허용 키 전수 대조**로 막는 자리다 — 키를 더하려면 양쪽을 같이 고쳐야 한다.
`category` 는 닫힌 셋이고 그 밖의 값은 행 거부(조용한 fallback 없음). 거부 사유는 필드 이름만 나르고 공고
식별자를 담지 않는다.

투찰자에는 상호·사업자번호·대표자명이 **없다**. 사업자번호·대표자명은 어댑터 경계의 허용 목록 반전이 관측
생성 **전에** 떨어뜨려 DB 에 들어오지도 못하고, 상호는 raw 관측까지 오지만 추출이 그 열을 고르지 않는다.
