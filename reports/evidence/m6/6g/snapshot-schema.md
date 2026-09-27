# M6/6G 실험 스냅숏 JSONL 스키마 (D-6G-2)

> **지위: 레인 간 계약.** Kotlin 레인이 이 형태로 쓰고 Python 레인이 이 형태로 읽는다(fixture 도 이 형태로 짓는다).
> 형태를 바꾸려면 `schema_version` 을 올리고 이 문서를 먼저 고친다 — 한쪽만 바꾸지 않는다.
> 스냅숏 실물은 **저장소 밖**(`~/.local/bid-vector-snapshots/<snapshot_id>/`)에 둔다(D-6G-2 · data-extract §7 · ADR 0010 D-8).

`schema_version`: `6g-snapshot-1`

## 0. 파일 구성

| 파일 | 내용 |
|---|---|
| `notices.jsonl` | 공고 하나 = 한 줄(UTF-8, 개행 구분, 줄 안에 개행 없음). 아래 §1 |
| `manifest.json` | 한 객체. 행 수 · 기간 · sha256 · 표본 목록 sha256 · 표집 축. 아래 §3 |

**정렬**: `notices.jsonl` 은 `notice_key_hash` **오름차순**으로 쓴다 — 같은 입력이면 바이트까지 같은 파일이 나온다
(재현성 D-6G-2, acceptance 의 「바이트 동일」 재현 test 가 이것을 잰다). JSON 객체의 키 순서는 이 문서의 선언 순서로 고정한다.

**수치의 표현**: 금액·비율은 **문자열**이다(JSON number 로 쓰지 않는다). 부동소수 왕복이 값을 바꾸는 자리를 만들지 않는다 —
읽는 쪽이 `Decimal` 로 받는다. 개수·순번만 JSON 정수다.

**`null` 의 뜻**: 「관측되지 않았다」이다. 「0」·「없음」과 구분된다 — 제외 판정은 `exclusions` 가 따로 나른다(§2).

## 1. `notices.jsonl` 레코드

```json
{
  "notice_key_hash": "9f2c…",
  "business_division": "CONSTRUCTION",
  "notice_date": "2026-06-03",
  "opening_date": "2026-06-17",
  "opening_window": "2026-W25",
  "base_amount_won": "1234567890",
  "planned_price_won": "1250000000",
  "floor_rate_fraction": "0.87745",
  "floor_rate_origin": "PUBLISHED",
  "award_method_code": "01",
  "award_method_name": "적격심사",
  "planned_price_method_name": "복수예가",
  "reserve_price_range_begin_rate": "-0.02",
  "reserve_price_range_end_rate": "0.02",
  "total_reserve_price_candidate_count": 15,
  "reserve_prices": [
    { "sequence": "01", "amount_won": "1248000000", "is_drawn": true, "draw_count": 2 }
  ],
  "draw_numbers": [3, 7, 11, 14],
  "draw_numbers_kind": "VERIFIED",
  "construction_a_value_won": "98000000",
  "construction_a_value_disclosed_at": "2026-06-16T00:00:00Z",
  "construction_a_value_summed": true,
  "participant_count": 42,
  "bidders": [
    { "ordinal": 1, "bid_amount_won": "1100000000", "bid_rate_percent": "88.000", "opening_rank": 1, "bid_at": "2026-06-17T00:50:00Z" }
  ],
  "exclusions": []
}
```

### 1.1 칸의 뜻

| 칸 | 형 | 뜻 · 출처 |
|---|---|---|
| `notice_key_hash` | string(64, 소문자 hex) | `sha256("<공고번호>/<차수>")`. **salt 없음** — 표본 선택 술어(D-6G-11)가 같은 해시를 다시 계산할 수 있어야 하고, 공고번호 자체는 공개값이다. **되돌릴 수 있다**(11자리 공간) — 이것은 비밀이 아니라 **조인 키 제거**다. 비식별의 대상은 공고가 아니라 투찰자다 |
| `business_division` | enum | `GOODS` · `SERVICE` · `CONSTRUCTION` · `FOREIGN`. 값은 수집 오퍼레이션이 정한다(응답 필드 아님) |
| `notice_date` | date | **공고일**. 창 포함 판정의 기준(D-6G-14 — 예규 적용례 「시행일 이후 최초 입찰공고분」) |
| `opening_date` | date | 개찰일(실개찰일시의 KST 날짜). 창을 **자르는** 기준(D-6G-5) |
| `opening_window` | string | 개찰 주 창 id, ISO 주(`YYYY-Www`). 비중첩 · 층의 한 축 |
| `base_amount_won` | string\|null | 기초금액 |
| `planned_price_won` | string\|null | 예정가격 |
| `floor_rate_fraction` | string\|null | 낙찰하한율 **fraction**(percent 아님). 없으면 그 공고는 `exclusions` 에 ② |
| `floor_rate_origin` | string\|null | 하한율 출처 어휘 |
| `award_method_code` | string\|null | 낙찰방법코드(`sucsfbidMthdCd`) — 제로패딩 보존 |
| `award_method_name` | string\|null | 낙찰방법명(`sucsfbidMthdNm`). 제외 ①의 입력 |
| `planned_price_method_name` | string\|null | 예정가격 결정방법. 제외 ④의 입력 |
| `reserve_price_range_begin_rate` | string\|null | 예가 범위 **시작**율 fraction(`rsrvtnPrceRngBgnRate`). S0 의 반폭 h 는 이 값과 아래 값에서 나온다 — 상수 2%·3% 를 코드에 박지 않는다 |
| `reserve_price_range_end_rate` | string\|null | 예가 범위 **끝**율 fraction(`rsrvtnPrceRngEndRate`) |
| `total_reserve_price_candidate_count` | int\|null | 총예가건수 |
| `reserve_prices` | array | 복수예비가격 후보 행. §1.2 |
| `draw_numbers` | array[int]\|null | 관측된 추첨번호(1-기반 인덱스). 투찰자 귀속 없음 |
| `draw_numbers_kind` | enum\|null | `VERIFIED` · `OUT_OF_RANGE` · `RANGE_CHECK_UNAVAILABLE`. 「검사 못 했다」가 조용한 통과로 접히지 않는다 |
| `construction_a_value_won` | string\|null | 공사 A값. 공사가 아니면 항상 `null` |
| `construction_a_value_disclosed_at` | datetime(UTC, `Z`)\|null | A 공개일시. **입찰 마감 뒤면** 제외 ⑥(투찰 시점에 알 수 없는 값) |
| `construction_a_value_summed` | bool\|null | 합산 여부 술어 |
| `participant_count` | int\|null | 참가업체수(목록 축 관측) |
| `bidders` | array | 투찰 행. §1.3 |
| `exclusions` | array[enum] | 제외 사유. §2. **빈 배열이면 실험 대상** |

### 1.2 `reserve_prices[]`

| 칸 | 형 | 뜻 |
|---|---|---|
| `sequence` | string | 복수예가순번(`compnoRsrvtnPrceSno`) — 제로패딩 보존 |
| `amount_won` | string\|null | 예비가격 |
| `is_drawn` | bool\|null | 추첨 여부 |
| `draw_count` | int\|null | 추첨 횟수 |

`sequence` 오름차순(문자열 사전순)으로 쓴다.

### 1.3 `bidders[]` — **상호를 싣지 않는다**

| 칸 | 형 | 뜻 |
|---|---|---|
| `ordinal` | int | **공고 안 순번**, 1-기반. 투찰자의 유일한 식별이다 — 상호·사업자번호·대표자명은 싣지 않는다(D-6G-2 ⑤). 공고 사이에 같은 `ordinal` 이 같은 업체를 뜻하지 **않는다** |
| `bid_amount_won` | string\|null | 투찰금액. 협상에 의한 계약에서는 부재한다(제외 ⑯의 입력) |
| `bid_rate_percent` | string\|null | 투찰률 percent 원문 형(분자는 투찰금액) |
| `opening_rank` | int\|null | 개찰순위 원문. **결측·중복이 흔하다** — 순위로 경쟁자 분포를 만들지 말고 `bid_amount_won` 으로 만든다 |
| `bid_at` | datetime(UTC, `Z`)\|null | 투찰일시 |

**`ordinal` 의 결정 규칙**(재현성): `bid_amount_won` **오름차순**, `null` 은 뒤로, 같은 금액은 원문 행 순서 — 그 뒤 1부터 매긴다.
오름차순이라 `ordinal = 1` 은 **최저 투찰**이다(개찰순위 1위와 같은 행이라는 보장은 없다 — `opening_rank` 와 어긋나면
그 자체가 관측 사실이다).

사업자등록번호·대표자명은 **DB 에 들어오지 못한다**(어댑터 경계 허용 목록 반전이 계약 미등재 키를 관측 생성 전에 떨어뜨린다).
상호는 raw 관측까지 오지만 이 추출이 그 열을 고르지 않는다.

## 2. `exclusions` 어휘 (D-6G-13)

수집·추출 단계에서 판정 가능한 것만 싣는다. **전략을 보기 전에** 정해지고, 전략 간 동일 집합이다.
한 공고에 여러 사유가 붙을 수 있다(배열, 선언 순서로 정렬).

| 값 | D-6G-13 | 판정 자리 |
|---|---|---|
| `AWARD_METHOD_NOT_QUALIFICATION_REVIEW` | ① | 추출(낙찰방법) |
| `FLOOR_RATE_MISSING_OR_OUT_OF_BAND` | ② | 추출(하한율 · 밴드 `[0.30, 0.995]`) |
| `REBID_OR_FAILED_ROUND` | ③ | 추출(진행구분 · 재입찰번호 · 차수) |
| `SINGLE_PLANNED_PRICE` | ④ | 추출(예정가격 결정방법 · 총예가건수) |
| `RESERVE_PRICE_INCOMPLETE` | ⑤ | 추출(예비가격 행 수 · 추첨 정보) |
| `CONSTRUCTION_A_VALUE_MISSING_OR_LATE` | ⑥ | 추출(A값 부재 또는 공개일시 > 마감) |
| `SMALL_SUM_NEGOTIATED` | ⑦ | 추출(낙찰방법) |
| `SHIP_MANUFACTURING_GOODS` | ⑧ | 추출(업무 세부 분류) |
| `PURE_CONSTRUCTION_COST_INPUT_MISSING` | ⑨ | 추출(순공사원가 입력 부재) |
| `SME_COMPETITION_CAPABILITY_REVIEW` | ⑩ | 추출(낙찰방법) |
| `LOCAL_GOVERNMENT_ORDER` | ⑪ | 추출(발주 기관 축) |
| `FOREIGN_PROCUREMENT` | ⑫ | 수집(업무 대분류 `FOREIGN`) |
| `FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY` | ⑬ | 추출(공고일이 시행일 경계 구간) |
| `NO_ELIGIBLE_BIDDER` | ⑭ | 실험(적격 투찰자 0) — **추출은 싣지 않는다** |
| `TIED_TOP_BID` | ⑮ | 추출(최저 투찰금액이 둘 이상) |
| `BID_AMOUNT_ABSENT` | (P-1.5) | 추출(협상계약 등 투찰금액 부재) |

⑭ `NO_ELIGIBLE_BIDDER` 는 하한가 산식을 적용해야 나오므로 **실험 단계**가 붙인다 — 추출은 이 값을 쓰지 않는다.
Python 레인은 자기가 붙인 ⑭ 를 계수에 더한다(같은 어휘를 쓰되 출처가 다르다는 것을 판정문에 적는다).

## 3. `manifest.json`

```json
{
  "schema_version": "6g-snapshot-1",
  "snapshot_id": "2026-09-28T01-00-00Z",
  "created_at": "2026-09-28T01:00:00Z",
  "row_count": 24000,
  "notice_date_range": { "from": "2026-01-30", "to": "2026-09-26" },
  "opening_date_range": { "from": "2026-02-06", "to": "2026-09-26" },
  "sample_list_sha256": "…",
  "sampling": {
    "policy_seed": "…",
    "strata": ["business_division", "notice_week"],
    "target_per_stratum": 500
  },
  "files": [
    { "path": "notices.jsonl", "sha256": "…", "row_count": 24000 }
  ],
  "exclusion_counts": { "FLOOR_RATE_MISSING_OR_OUT_OF_BAND": 312 }
}
```

| 칸 | 뜻 |
|---|---|
| `sample_list_sha256` | 표본 목록(정렬된 `notice_key_hash` 를 개행으로 이은 텍스트)의 sha256. **결과를 보기 전에** 확정된 목록이라는 증거 — 판정 JSON 이 이 값을 싣는다(우회 ⑦ 표본 쇼핑) |
| `files[].sha256` | 파일 바이트의 sha256. 같은 스냅숏이면 같은 판정이 나와야 한다는 요구의 고정점 |
| `exclusion_counts` | 추출이 붙인 사유별 계수(⑭ 제외). 사유별 계수 공시(D-6G-2 ④) |

## 4. Python 레인이 fixture 를 지을 때

- 합성 스냅숏(비식별)은 같은 파일 구성·같은 키 순서·같은 정렬로 짓는다 — 재현 test 가 「바이트 동일」을 잰다.
- 실제 공고번호·상호를 쓰지 않는다. `notice_key_hash` 는 임의의 64자 hex 로 지어도 된다(형태만 맞으면 된다).
- 금액·비율이 **문자열**이라는 것이 가장 자주 틀리는 자리다.
