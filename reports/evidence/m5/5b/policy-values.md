# M5/5B 정책 값 — `OPEN-5B-POLICY-VALUES`(**사용자 승인 2026-09-12, 착수 시**)

> **지위: 승인.** 값은 legacy-behavior(근거 주석 없음 — 조사 §c-2 「정책 2」)이고 이 slice 가 새로 지어낸 수치는 없다.
> 정본은 이 문서 — 값을 바꾸려면 이 문서를 먼저 갱신한다. 구현은 `EncodingPolicy` **필수 인자**(기본값 없음)로 받고,
> 출하 값은 `ml_engine.features` 의 상수 하나에 두며 test 가 이 표와 대조한다.

## §1 2단 pseudo-count 수축 κ (D-5B-7)

| 항목 | 값 | legacy 좌표 |
| --- | --- | --- |
| `agency_prior_strength` | `12.0` | `bid-vector/app/domain/award_rate_features.py` `AWARD_RATE_AGENCY_PRIOR_STRENGTH`(`ed4b06c`) |
| `category_prior_strength` | `40.0` | 같은 파일 `AWARD_RATE_CATEGORY_PRIOR_STRENGTH` |

가중치 정본 `pseudo_count_weight = n / (n + κ)`(`assessment_shrinkage.py`), 사정률 축 κ 와 **별개 선언**(legacy 관례 계승).

## §2 이 slice 가 값을 갖지 않는 것

스키마 version 문자열 `award-rate-features-v2`(D-5B-1, 정책 값이 아니라 계약 식별자) · 분모 어휘(wire enum 이름에서 도출, D-5B-2) ·
`sample_scope`(5C manifest) · 정책 33(5C·5D).

## 해소 조건

5C 재학습이 κ 튜닝 근거(홀드아웃 지표)를 내면 이 표를 갱신하고 `EncodingPolicy` 출하 상수와 test 를 함께 바꾼다.

## change_history

| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-12 착수 | §1 두 값 등재·승인 | 운영자 「추천 방식으로 진행」(D-5B-7 legacy 값) |
