# M4/4B-7 정책 값 — OPEN-4B7-POLICY-VALUES(승인 대기)

정본은 이 문서다 — `OpportunityPolicyData`의 4B-7 신규 슬롯 둘, `SampleEligibilityPolicyData`,
`SAMPLE_PROVENANCE_POLICY`(workflow 쪽 잠정 인스턴스, D-4B7-8) 값을 바꾸려면 이 문서를 먼저
갱신한다(3A `KONEPS_COLLECTION_POLICY` · 4B-6b `policy-values.md` 관례).

## §1 경쟁 표본 조회 축(D-4B7-3, `OpportunityPolicyData` 신설 슬롯 둘)

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `sampleWindowDays` | `365` | 착수 placeholder — 5C/5E 과거 개찰 결과 실 분포 실측 뒤 갱신 대상. legacy 근거 없음(신규 축) |
| `maxSamples` | `500` | 같은 성격 — 엔진 호출 payload 크기·조회 비용의 보수적 상한(실측 아님) |

## §2 예비가격 정상 건수(D-4B7-2 ③, `SampleEligibilityPolicyData`)

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `expectedReservePriceCount` | `15` | 엔진 `ml-engine/policy/inference-v1.yaml` `reserve.expected_price_count` — 착수 조사 표(scope.md)가 이미 1:1로 확인했다. 어긋나면 엔진이 `PRICE_COUNT_MISMATCH`로 거부하므로 이 값은 엔진 쪽 정본과 항상 같아야 한다 |

## §3 D-4B7-8 라벨용 provenance 정책 — legacy 값 그대로 이관(`SAMPLE_PROVENANCE_POLICY`)

`decision` 모듈은 아직 `ProvenancePolicyData`의 운영 정본(`EffectiveDatedPolicy` 인스턴스)을
갖지 않는다 — M1/1D는 타입·커널만 세웠고 값 채택은 다른 slice로 미뤘다. 이 slice의 in_scope는
`decision` 모듈을 포함하지 않으므로, D-4B7-8(표본 라벨링)이 필요로 하는 값을 `workflow` 쪽
잠정 인스턴스(`SAMPLE_PROVENANCE_POLICY`, `SampleEligibility.kt`)로 둔다. 값은 legacy
분류기(`bid-vector/app/services/base_amount_basis.py`)를 그대로 옮긴 것이다 — 지어낸 값이
없다.

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `ruleOrder` | `[SuspectRatio, CleanInteger, DerivedYega, DerivedVat]` | `base_amount_basis.py`의 `_CLASSIFICATION_RULES`(주석 "ORDER IS LOAD-BEARING") — 비율 의심이 정수 판정 앞, 정수 판정이 예정가 역산·VAT 파생 앞 |
| `trustRatioMax` | `Rate.ofFraction(1.15)` | `app/core/constants.py` `BID_BASE_TRUST_RATIO_MAX = 1.15` |
| `cleanIntegerTolerance` | `0.000001`(1e-6) | `base_amount_basis.py` `_INTEGER_TOLERANCE` |
| `vatMultiplier` | `1.1` | `base_amount_basis.py` `_VAT_MULTIPLIER` |
| `vatTolerance` | `0.01` | `base_amount_basis.py` `_VAT_TOLERANCE` |
| `yegaTolerance` | `1.0`(원) | `base_amount_basis.py` `_YEGA_TOLERANCE` |
| `integerRoundingMode` | `RoundingMode.HALF_UP` | legacy `round()`의 정확한 반올림 모드는 미결(`OPEN-DIC-10`, `ProvenancePolicyData` KDoc) — `OpportunityPolicyData.recommendedAmountRounding`과 같은 코드베이스 전역 관례로 잠정 채택. 값 자체가 legacy 실측은 아니다 |

`budgetEstimate`(`ProvenanceRow`) 매핑 — `Notice.estimatedAmount`(추정가격)를 쓴다. 근거:
legacy `Project.budget_estimate`는 추정가격이다(`bid-vector/app/core/money.py`
`BUDGET_ESTIMATE = "budget_estimate"  # 추정가격`, `docs/discovery/data-dictionary.md` §1.2
`추정가격(presmptPrce)` 행). `winningAmount`는 `OpeningResult.finalAwardAmount`,
`winningRate`는 `OpeningResult.winningRate`.

## §4 D-4B7-7 시간대(정책 슬롯 아님 — 상수)

`OPENING_DATE_ZONE = ZoneId.of("Asia/Seoul")` — `actual_opening_at`을 날짜로 접는 시간대.
scope.md 문면이 이미 "슬롯 없이 상수로 두되 checklist 등재"를 지정했다 — 정본은
`OPEN-3A-SOURCE-TZ`(아직 미결)이고, 이 slice는 착수 가정을 상수로만 둔다(값을 지어내지 않되,
새 정책 슬롯을 추가로 열지도 않는다).

## 해소 조건

`OPEN-4B7-POLICY-VALUES`는 §1(조회 창·상한)·§2(예비가격 건수 — 엔진과 대조 확정 필요)·
§3(provenance 임계 넷 + 순서, `decision` 모듈에 정본이 생기면 그쪽으로 흡수)의 값이 각각
실측·운영자 승인으로 확정될 때 닫힌다. §2는 엔진 정책 파일과 1:1이라 우선순위가 높다(값이
어긋나면 표본 전부가 `PRICE_COUNT_MISMATCH`로 거부된다).

## 사용자 승인

미승인 — 이 문서는 구현 레인이 작성한 초안이다. §1·§2·§3 전부 승인 대기.
