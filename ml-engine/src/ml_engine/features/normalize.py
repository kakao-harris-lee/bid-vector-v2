"""Reuse: bid-vector/app/utils/textfmt.py@ed4b06c

`ml_engine.features.normalize` — 공종·발주기관 키 정규화(D-5B-9).

legacy `normalize_lookup_key(value, aliases)`에서 별칭 해소 부분을 걷어내고
(`_NO_ALIASES` 계승 — 별칭 표를 두면 학습 어휘에 없는 철자가 조용히 다른 값으로 접힌다,
`award_rate_features.py` 모듈 docstring), 타입을 `str | None` → `str`로 좁혔다(D-5B-9).
`None`은 호출부(`facts.py`)가 `Missing` 타입으로 이미 갈라낸 뒤이므로 이 함수에 닿지 않는다.
빈 문자열(정규화 후)의 처리는 이 함수의 책임이 아니다 — 호출부가 `FactRejected.EmptyKey`로
판정한다(D-5B-9, 이 함수는 `strip().lower()`만 한다).
"""

from __future__ import annotations


def normalize_feature_key(value: str) -> str:
    """공종·발주기관 키를 소문자 정규화한다(양쪽 경로 단일 출처, alias 표 없음)."""
    return value.strip().lower()
