# M6/6G 실험 스냅숏 스키마 (D-6G-2) — 레인 간 계약

> **지위: 두 레인이 합의한 계약.** 2026-09-27 초판(Kotlin 레인)과 Python 레인의 독자 초안이 엇갈려, **Python 레인의
> 구조를 채택**하고 Kotlin 레인이 생산 쪽 제약을 반영해 정정했다. 형태를 바꾸려면 `schema_version` 을 올리고 이
> 문서를 먼저 고친다 — 한쪽만 바꾸지 않는다.
> **v5(2026-09-28, D-6G-58·66)**: 축 완료를 **시도 원장이** 정한다 — 완료되지 않은 축이 있는 공고는
> 행을 쓰지 않고 `incomplete_axis` 로 계수한다. manifest 칸 하나와 닫힌 항등식의 항 하나가 늘고,
> 표본 업무 범위 칸(`sample_scope_divisions`)이 하나 더 는다.
> **v4(2026-09-28, D-6G-39)**: 표본 목록이 파일이 됐다 — 파일 하나(`sample-list.tsv`)·manifest 칸 셋
> (`sample_size`·`sampled_without_detail`·`sampled_without_notice`)·`sample_list_sha256` 의 정의가 바뀐다.
> 스냅숏 실물은 **저장소 밖**(`~/.local/bid-vector-snapshots/<snapshot_id>/`)에 둔다(data-extract §7 · ADR 0010 D-8).

`schema_version`: **`snapshot-v5`** · 파일 **셋**: `manifest.json` · `rows.jsonl` · `sample-list.tsv`

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
{ "schema_version": "snapshot-v5", "snapshot_id": "…", "row_count": 23856,
  "period_start": "2026-02-06", "period_end": "2026-09-26",
  "rows_sha256": "…", "sample_list_sha256": "…",
  "sample_size": 24000, "sampled_without_detail": 96, "sampled_without_notice": 24,
  "incomplete_axis": 24, "sample_scope_divisions": ["CONSTRUCTION", "SERVICE"] }
```

| 칸 | 뜻 |
|---|---|
| `period_start`·`period_end` | **개찰일** 범위다(창을 자르는 축). 공고일은 행마다 `notice.noticed_on` 이 나른다 — 창 **포함** 판정은 그쪽이다(D-6G-14) |
| `rows_sha256` | `rows.jsonl` **바이트**의 sha256 hex |
| `sample_list_sha256` | **`sample-list.tsv` 바이트**의 sha256 hex(§2.1) |
| `sample_size` | `sample-list.tsv` 의 줄 수 = 확정된 표본 크기 |
| `sampled_without_detail` | 표본인데 상세 축 관측이 하나도 없는 공고 수(부르지 못했거나 응답이 비었다) |
| `sampled_without_notice` | 표본이고 상세는 있는데 **공고 목록 canonical 이 없어** 행을 만들지 못한 공고 수 |
| `incomplete_axis` | 상세 축 가운데 **완료되지 않은 것이 있는** 공고 수(v5, D-6G-58) — 아래 |
| `sample_scope_divisions` | **확정 범위가 말하는 업무 집합**(v5, D-6G-66) — §2.1 의 닫힌 어휘, 오름차순 정렬. 아래 |

**`incomplete_axis` 가 왜 행이 아니라 계수인가.** 한 축이 페이지 중간에 끊기면 그 축의 원문은
**일부만** 있다. 그 반쪽으로 행을 쓰면 값이 조용히 틀린다(예: 개찰완료 2쪽 중 1쪽만 받은 공고의
투찰자 수가 실제보다 적다) — 그리고 그 행은 「값이 있는 정상 행」으로 보여 어느 제외 사유에도
걸리지 않는다. 완료는 **시도 원장**이 정한다(마지막 AXIS 결말이 성공이거나 빈 응답일 때만 완료),
raw 행의 존재가 아니다. 다음 실행이 그 축을 다시 부르면 이 공고는 행이 된다.

**`sample_scope_divisions` 가 왜 표본 행에서 오지 않는가.** 문턱(최소 표본 수)이 업무 수로 정해지는데,
그 수를 **표본 행에서 센 distinct** 로 잡으면 한 업무가 통째로 빠졌을 때 distinct 가 줄고 문턱도 함께
내려간다 — 결측이 문턱을 스스로 낮추는 모양이고, 그것은 조용하다. 이 칸은 확정 파일이 싣는 **설정된**
범위이고(`sample-scope.json` 의 `divisions`), 표본 행의 업무 집합은 이 칸의 **부분집합**이어야 한다.
비어 있는 업무는 그 자리에서 보이고 판정이 그 업무를 따로 공시한다.

값은 §2.1 의 닫힌 어휘이고 **오름차순·중복 없음**이다. 판독은 셋을 검사한다 — 어휘 밖이면
`UNKNOWN_BUSINESS_DIVISION`, 정렬·중복이 어긋나면 `INVALID_VALUE`, 표본 목록의 업무가 이 칸 밖이면
`SAMPLE_SCOPE_MISMATCH` 로 **스냅숏 전체를 거부**한다.

**판정문의 업무 대표 어휘**(닫힌 셋): `COVERED` · `UNDERPOWERED`. 확정 범위의 업무 하나에 행이
하나도 오지 않으면 `UNDERPOWERED` 다 — 문턱은 내려가지 않고, 그 업무가 판정 JSON 의
`snapshot.division_coverage` 에 행 수 0 과 함께 남는다. 행이 있는데 적어서 검정력이 모자란 것은
이 축이 아니라 **창 단위** UNDERPOWERED 가 잰다(D-6G-31) — 두 축은 다른 것을 센다.

**닫힌 항등식:** `sample_size == row_count + sampled_without_detail + sampled_without_notice +
incomplete_axis`.
표본 하나하나가 행이 되었거나, 되지 못한 사유로 계수된다 — 어느 쪽도 아닌 공고는 없다. 판독은 이
등식을 검사하고, 깨지면 **구조 실패**다(계수가 행을 설명하지 못한다는 뜻이므로 데이터의 성질이 아니다).

## 2.2 해시의 용도 구분자 — 두 무작위가 같은 digest 를 쓰지 않는다 (v5, vr r4 L-14)

표본 추첨(Kotlin `StratifiedSampler`)과 S0 밴드 내 난수(Python `strategies`)가 **같은 `seed|key` 형태**를
쓰면, 두 seed 값이 우연히 같을 때 「어떤 공고가 뽑혔나」와 「S0 가 그 공고에 낸 값」이 같은 digest 에서
나온다 — 두 무작위가 상관되고 판정이 그만큼 덜 독립이다. 해시 입력의 **맨 앞에 용도 구분자**를 붙여 두
영역을 가른다.

| 용도 | 해시 입력 | 쓰는 레인 |
|---|---|---|
| 표본 추첨 순서 | `sha256("sample-draw" + "\|" + seed + "\|" + 공고 키 해시)` | Kotlin |
| S0 밴드 내 난수 | `sha256("s0-band" + "\|" + seed + "\|" + 공고 키 해시)` | Python |

구분자 값을 바꾸면 표본과 S0 값이 둘 다 바뀐다 — 그것은 **새 실험**이고 정책 version 을 올린다.

## 2.1 `sample-list.tsv` — 표본은 결과를 보기 전에 파일로 확정된다 (v4, D-6G-39)

v3 까지 `sample_list_sha256` 은 **스냅숏 행에서 역산**한 값이었다. 판독이 그 값을 행 집합으로 다시 계산해
대조했으니 정의상 언제나 맞았다 — 「결과를 보기 전에 확정됐다」를 **아무것도 검사하지 못하는** 순환 대조였다.
생산 쪽도 마찬가지로 실행마다 표본틀을 다시 걷고 다시 뽑았다. 수집이 3~4일에 걸치면 늦게 개찰된 공고가 창에
들어와 **표본 자체가 달라진다**.

v4 는 표본을 파일로 못 박는다. 수집 갈래의 **첫 표본틀 단계**가 `sample-list.tsv` 를 저장소 밖에 쓰고, 그
뒤의 모든 수집 실행과 추출은 **그 파일만** 읽는다. 다시 뽑지 않는다. 파일이 이미 있으면 덮어쓰지 않는다.

형태는 줄 단위 TSV 다 — `notice_key_hash <TAB> business_division <TAB> notice_week`, **해시 오름차순**,
줄마다 `\n`(끝 줄 포함). 헤더가 없다.

```
0a1f…(64 hex)	CONSTRUCTION	2026-W07
3c92…(64 hex)	SERVICE	2026-W08
```

| 칸 | 뜻 |
|---|---|
| `notice_key_hash` | §5 의 정의 그대로(소문자 hex 64자) |
| `business_division` | 층의 업무 축 — Kotlin `BusinessDivision` 어휘(`CONSTRUCTION`·`SERVICE`·`GOODS`·`FOREIGN`). 행의 `category` 와는 **다른 축**이지만 어휘는 같다 — 예제가 갈려 있으면 다음 사람이 어느 쪽을 정본으로 읽을지 헷갈린다(D-6G-46) |
| `notice_week` | 층의 시간 축 — 공고일의 ISO 주(`YYYY-Www`) |

추출은 이 파일을 **바이트 그대로 복사**해 스냅숏 곁에 놓는다(해시 동일성이 목적이므로 다시 렌더링하지
않는다). 판독의 대조는 셋이다 — ⑴ 파일 sha256 == `sample_list_sha256`, ⑵ 모든 행의 `notice_key_hash` 가
파일의 키 집합 안(밖이면 `SAMPLE_LIST_MISMATCH`), ⑶ §2 의 닫힌 항등식.

## 3. `rows.jsonl`

공고 하나 = 한 줄. **`notice_key_hash` 오름차순**으로 쓴다(같은 입력이면 바이트까지 같은 파일 — 재현 test 의
「바이트 동일」이 이것을 잰다). 키 순서는 아래 선언 순서로 고정한다.

### 3.1 `notice` — 투찰 시점

| 칸 | 형 | 정정·주의 |
|---|---|---|
| `notice_key_hash` | string(64 hex) | §5 |
| `category` | `CONSTRUCTION`\|`SERVICE`\|`GOODS` | 닫힌 셋. `FOREIGN` 은 **오지 않는다**(§4 ⑫) |
| `noticed_on` | date \| null | 공고일(`ntceNticeDt`) — 창 포함 판정(D-6G-14). **개찰일로 대체하지 않는다**(v2 는 그렇게 접혀 제외 ⑬ 이 개찰일로 돌았다). 없으면 `NOTICE_DATE_ABSENT` |
| `bid_close_at` | datetime(offset) \| null | 입찰 마감(`bidClseDt`) — A·기초금액 공개 시점 절단의 기준. 없으면 `BID_CLOSE_AT_ABSENT` |
| `base_amount` | int \| null | 기초금액. **기초금액 조회 오퍼레이션의 값만** 싣고, `base_amount_disclosed_at < bid_close_at` 인 행만이다(§3.4) |
| `base_amount_disclosed_at` | datetime(offset) \| null | 기초금액공개일시(`bssamtOpenDt`) — 「그 값이 언제 공개되었나」 |
| `floor_rate` | number \| **null** | fraction(percent ÷ 100) |
| `reserve_range_begin_rate` | number \| null | fraction. 원문은 **% 이고 부호가 문자열 안**에 있다(`-3`) — 추출이 선행 `+` 를 허용해 파싱하고 100 으로 나눈다 |
| `reserve_range_end_rate` | number \| null | fraction(`+3` → `0.03`). **시작률의 반대수라는 보장이 없다** — 비대칭 범위를 지원하라 |
| `a_value` | `{total:int, open_at:datetime, standard_market_price_applicable:bool\|null}` \| null | `total` 의 합산 규칙은 §3.3 |
| `successful_bid_method_code` | string \| null | `sucsfbidMthdCd` — 문서상 옵션이라 부재 가능 |
| `successful_bid_method_name` | string \| null | `sucsfbidMthdNm` — 같음 |
| `prearranged_price_decision_method` | string \| null | `prearngPrceDcsnMthdNm` — A 오퍼레이션에서만 오므로 그 호출이 없으면 부재 |
| `notice_ordinal` | int | `bidNtceOrd` |
| `procurement_class_code` | string \| null | **추가**(§4 ⑧) — 선박 제조 물품 판정 입력 |
| `demand_agency_code` | string \| null | **원값**(해시 아님) — 기관 코드는 개인정보가 아니다(상호·사업자번호는 여전히 비수집). 지자체 판정의 *입력*이지 판정이 아니다(§4 ⑪) |
| `bid_price_formula_a_applicable` | bool \| null | `bidPrceCalclAYn` — **공사에만 오는 필수 필드**. 「이 공고가 A값 공고인가」를 한 필드로 답한다. 공사가 아니면 null |
| `pure_construction_cost` | int \| null | `bssAmtPurcnstcst`(공사 전용) — 제외 ⑨의 입력 |
| `has_award_method_application_standard` | bool | `sucsfbidMthdAppStd` 의 **존재 여부만**(D-6G-33) — 원문은 자유텍스트라 무엇이 실릴지 모른다. 채움률은 이 불리언으로 낸다 |
| `has_application_basis_content` | bool | `aplBssCntnts`(공사 전용)의 존재 여부만 — 같은 이유 |

**뺀 칸 둘**: `is_local_government`·`is_foreign_capital`. 사유는 §4 ⑪⑫ — 앞은 **판정할 수 없고**, 뒤는 **구조적으로
항상 거짓**이다. 지어낸 불리언을 싣지 않는다.

### 3.2 `outcome` — 개찰 시점

| 칸 | 형 | 정정·주의 |
|---|---|---|
| `opened_on` | date \| null | 개찰일 — 창 자르기·누출 절단. **수집 갈래가 실제로 채우는 출처**(raw 관측)에서 온다. 없으면 `OPENING_DATE_ABSENT` — 대체값(EPOCH 등)을 지어내지 않는다 |
| `progress_division` | string \| null | 진행구분 — **개찰로 드러나는 값**이라 `outcome` 쪽이다(v2 는 투찰 시점 타입에 있었다). 유찰·재입찰 판정(③) 입력 |
| `planned_price` | int \| null | 예정가격. 없으면 `PLANNED_PRICE_ABSENT`(단수 예가·예비가격 상세 미수신 공고에서 난다) |
| `opening_base_amount` | int \| null | **개찰결과 출처**의 기초금액. `notice.base_amount` 와 **다른 칸**이다(§3.4) |
| `reserve_prices` | int[15] \| null | **위치 = 순번**이다. 15행이 모두 있고 순번이 1..15 로 빠짐없을 때만 배열을 싣고, 아니면 `null`(부분 배열을 싣지 않는다) |
| `drawn_serial_numbers` | int[] \| null | 1-기반 순번. **수집된 추첨 필드에서 온다**(v2 는 상수 `null` 이라 실 추출이면 전 행이 제외 ⑤ 에 걸렸다). 개수가 4라는 보장은 하지 않는다 |
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
> 하한가가 낮아져 **적격이 더 쉽게** 나온다 — 즉 이 배제는 보수적인 쪽이 **아니다**.

**`a_value.standard_market_price_applicable`**(D-6G-23, `snapshot-v2` 에서 신설) — `smkpAmtYn` 의 원문 술어를
불리언으로 나른다. 출처는 기초금액 조회(공사 op 6)이고, `Y`/`N` 밖의 값이나 부재는 `null`(판정 불가)이다.
**이 칸이 있어야 배제의 영향 범위를 셀 수 있다** — 없으면 「몇 건이 이 배제에 걸리는가」를 판정문이 말하지
못한다(D-6G-17 이 그 공시를 요구한다).

`a_value` 가 `null` 인 공고에는 이 술어도 없다. 그것이 옳다 — 배제는 A 의 합산액에만 영향을 주므로, A 자체가
없는 공고는 애초에 영향 범위 밖이다. 계수의 분모는 **A 값을 가진 공고**다.

> **계수는 범위이지 크기가 아니다.** 「몇 건이 걸리는가」는 알 수 있지만 「A 가 얼마나 달라지는가」는 모른다 —
> `smkpAmt` 의 **금액**을 스냅숏이 싣지 않기 때문이다. 크기까지 재려면 그 금액 칸이 필요하고, 그것은 또 한 번의
> `schema_version` 인상이다(지금 판에서는 하지 않는다 — 계약이 요구한 것은 계수다).

## 3.5 `null` 은 **행 단위** 제외다 — 스냅숏 전체 거부가 아니다 (v3, D-6G-28)

v2 는 `planned_price`·`bid_close_at` 을 필수로 선언해 놓고 생산 쪽이 nullable 이었다. 한 행의 `null` 이
판독을 `INVALID_VALUE` 로 떨어뜨려 **스냅숏 전체**가 거부됐고, 그 공고를 제외 규칙이 처리할 기회조차
오지 않았다.

v3 은 둘을 가른다.

| 갈래 | 대상 | 처분 |
|---|---|---|
| **값 결측** | 위 표에서 `\| null` 로 선언된 칸 | **그 행만** 제외 사유로 내리고 계수한다. 나머지 행은 그대로 산다 |
| **구조 실패** | 미지 키 · `schema_version` 불일치 · `rows_sha256` 불일치 · 닫힌 셋 밖의 `category` | **스냅숏 전체 거부**(그대로) — 두 레인이 어긋났다는 신호이지 데이터의 성질이 아니다 |

값 결측이 부르는 사유 넷을 새로 둔다(나머지는 기존 어휘 그대로):
`NOTICE_DATE_ABSENT` · `BID_CLOSE_AT_ABSENT` · `OPENING_DATE_ABSENT` · `PLANNED_PRICE_ABSENT`.

**생산 쪽이 귀속하는 제외 사유 셋**(행이 아예 오지 않으므로 판독은 계수로만 본다):
`SAMPLED_WITHOUT_DETAIL` · `SAMPLED_WITHOUT_NOTICE` · **`INCOMPLETE_AXIS`**(v5). 셋 다 manifest 의
같은 이름 칸이 수를 나르고, 닫힌 항등식이 그 수가 행을 설명함을 보증한다. 판정문은 이 어휘로
결측을 귀속한다 — 이름이 닫혀 있어야 「왜 빠졌는지 모르는 공고」가 생기지 않는다.

## 3.6 첫 공고 차수는 `"000"` 이다 (D-6G-35)

제외 ③(재입찰·정정)이 「차수가 올라갔는가」로 판정하므로 **첫 차수 값**이 정책 값이어야 한다.
추측하지 않고 문서·저장소 관측에서 확인했다.

| 근거 | 값 |
|---|---|
| V2 필드 계약 정본(M3/3A) — `bidNtceOrd` 항목크기 3, **샘플 `000`**, fixture `koneps-collection-012` | `000` |
| 선행 조사 P-5 §3.1 응답 전수표 — 기초금액 조회 **세 오퍼레이션 모두** XML 예제가 `000`(표 샘플이 아니라 XML 예제를 기준으로 삼은 표다) | `000` |
| license-limit 요청 명세 샘플 | `000` |
| 선행 조사 P-3 — 입찰가격산식 A(op 24) 응답 **표** 샘플 | `001` |

**판정: `"000"`.** 마지막 한 건은 그 문서가 든 **표본 공고가 마침 2차였다**는 뜻이지 「첫 차수가 001」이라는
진술이 아니다 — 같은 조사가 표 샘플과 XML 예제가 어긋나는 자리를 다섯 세고 XML 을 기준으로 삼았다.
형식은 제로패딩 세 자리이므로 숫자로 바꾸면 `0` 이다(`int` 변환은 계약이 금지하지만 제외 판정은 값 비교다).

**이 값을 쓰는 곳은 Python 레인의 제외 규칙**이므로 정책 파일에 두는 것도 그쪽이다 — Kotlin 은 근거만 세운다.

## 4. D-6G-13 제외 열다섯 — 판정은 Python 레인이, 입력은 Kotlin 레인이

초판은 `exclusions` 배열을 스냅숏에 실었다. **철회한다**: Python 레인이 이미 입력에서 판정하는 모듈을 갖고 있고,
판정 자리가 둘이면 서로 어긋날 때 어느 쪽이 정본인지가 없다. 스냅숏은 **입력만** 싣고 판정은 한 자리에서 한다.
계약이 요구하는 성질(전략을 보기 전 입력 단계 · 사유별 계수 · 전략 간 동일 집합)은 그 모듈이 전략보다 먼저,
전략과 무관하게 도는 한 그대로 선다.

| D-6G-13 | 입력 | 상태 |
|---|---|---|
| ① 낙찰방법 · ⑦ 소액수의견적 · ⑩ 중소기업자간 경쟁물품 | `successful_bid_method_code`·`_name` | 준다 |
| ② 하한율 부재·밴드 밖 | `floor_rate` | 준다 |
| ③ 유찰·재입찰·정정 | `notice_ordinal` + `outcome.progress_division` | 진행구분은 개찰로 드러나므로 outcome 쪽이다(v3) |
| ④ 단일 예정가격 | `prearranged_price_decision_method` · `reserve_prices` | 준다 |
| ⑤ 예비가격·추첨 결측 | `reserve_prices` · `drawn_serial_numbers` | 준다 |
| ⑥ A 결측·공개 늦음 | `a_value` · `bid_close_at` | 준다 |
| ⑧ 선박 제조 물품 | `procurement_class_code` | 대분류만으로는 못 가른다 |
| ⑨ 순공사원가 98% | `pure_construction_cost` | 준다(공사) — 문서 XML 예제가 빈 값이라 **채움률은 실측 전이다** |
| ⑪ 지자체 발주 | `demand_agency_code` | **판정은 못 한다**(아래) |
| D-6G-22 채움률 | `has_award_method_application_standard`·`has_application_basis_content` | 원문 없이 **존재 여부만**으로 낸다(v3) |
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
- **`sample_list_sha256`** = `sample-list.tsv` **파일 바이트**의 sha256 hex(v4 — §2.1). v3 까지는 스냅숏
  행에서 역산한 값이었고, 그 대조는 순환이라 아무것도 검사하지 못했다.

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
