"""`ml_engine.inference.rounding` — 경계 `Decimal` quantize 단일 출처(D-5D-10, D-5D-1).

`scenario.py`(bid_rate 산출)와 `policy.py`(clamp_min 정책 불변식, verifier r2 F-1)가 이
함수 하나를 공유한다. 두 곳이 각자 quantize 를 구현하면 반올림 규칙이 갈릴 수 있고,
정책이 「clamp_min 이 통과 가능하다」고 판단한 근거와 커널이 실제로 내는 `bid_rate` 값이
다른 규칙으로 계산돼 어긋나는 사고가 난다(F-1 — 이전 판은 정책이 quantize **전** 값만
보고 `clamp_min > 0`을 검사해, `quantize(clamp_min, digits) == 0`인 정책을 통과시켰다).
"""

from __future__ import annotations

from decimal import ROUND_HALF_UP, Decimal


def quantize_bid_rate(value: Decimal | float, digits: int) -> Decimal:
    """`Decimal(str(x))`(D-5D-1) 뒤 `digits` 자리로 `ROUND_HALF_UP`(D-5D-10) — legacy
    `round(rate, 4)`는 Python 기본 규칙(banker's rounding, `ROUND_HALF_EVEN`)이라 정확히
    0.5 인 자리에서 갈릴 수 있다. **의도된 갈림**이다(legacy 출력은 정답이 아니다)."""
    quantum = Decimal(1).scaleb(-digits)
    return Decimal(str(value)).quantize(quantum, rounding=ROUND_HALF_UP)
