"""M5/5D — golden 소비 자리(scope.md ⑨, D-M5-7 (a)). curator 병행 레인이
`fixtures/expected/ml-kernel-*` case 를 신설하기 전에는 이 test 가 **명시 skip**한다
(조용한 통과가 아니라, 「대기 중」이라는 사실을 pytest 리포트에 남긴다).

case 가 도착하면: 후보 율은 `Rate.fraction` 문자열(scale 보존) 비교여야 한다(digest §7
정밀도 규약 — float 허용오차 비교를 쓰면 D-5D-1 의 경계 `Decimal` 변환이 검증되지 않는다).
"""

from __future__ import annotations

from pathlib import Path

import pytest

_FIXTURES_EXPECTED_DIR = Path(__file__).resolve().parents[4] / "fixtures" / "expected"


def _kernel_golden_cases() -> list[Path]:
    if not _FIXTURES_EXPECTED_DIR.exists():
        return []
    return sorted(_FIXTURES_EXPECTED_DIR.glob("ml-kernel-*"))


@pytest.mark.skipif(
    not _kernel_golden_cases(),
    reason="curator case 대기 — OPEN-5D-GOLDEN(fixtures/expected/ml-kernel-* 미도착)",
)
def test_kernel_golden_cases_match_string_fraction() -> None:
    """curator case 도착 후 채울 자리 — 후보 율 문자열 비교(scale 보존, digest §7)."""
    cases = _kernel_golden_cases()
    assert cases, "OPEN-5D-GOLDEN 해소 뒤에는 이 목록이 비어 있으면 안 된다"
