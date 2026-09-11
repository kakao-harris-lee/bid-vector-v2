"""S-6 양성 대조 표본 — 함수 50줄 초과 + `dict` 약한 경계를 의도적으로 담는다.

`tests/gates/test_design_ratchet.py`가 `tools/design_ratchet.scan_source`를 이 파일에 직접
돌려 두 지표가 실제로 잡히는지 확인한다. 이 파일은 `[tool.design-ratchet] target_dirs`
(`src/ml_engine`·`tools`) 밖이라 실제 S-6 실행(`design_ratchet.py --check`)에는 안 걸린다.
"""

from __future__ import annotations


def oversized_function() -> int:
    total = 0
    total += 0
    total += 1
    total += 2
    total += 3
    total += 4
    total += 5
    total += 6
    total += 7
    total += 8
    total += 9
    total += 10
    total += 11
    total += 12
    total += 13
    total += 14
    total += 15
    total += 16
    total += 17
    total += 18
    total += 19
    total += 20
    total += 21
    total += 22
    total += 23
    total += 24
    total += 25
    total += 26
    total += 27
    total += 28
    total += 29
    total += 30
    total += 31
    total += 32
    total += 33
    total += 34
    total += 35
    total += 36
    total += 37
    total += 38
    total += 39
    total += 40
    total += 41
    total += 42
    total += 43
    total += 44
    total += 45
    total += 46
    total += 47
    total += 48
    total += 49
    total += 50
    total += 51
    total += 52
    total += 53
    total += 54
    return total


def weak_boundary_function(payload: dict) -> None:
    return None
