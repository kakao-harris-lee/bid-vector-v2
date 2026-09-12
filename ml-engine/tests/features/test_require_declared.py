"""RED — `require_declared`(D-5B-8, legacy `artifact_contracts.py:200-208` 사유 계승).
`sample_scope` 기본값 금지 규율 — None → `Undeclared`, 값 → 통과."""

from __future__ import annotations

from ml_engine.features.schema import Undeclared, require_declared


def test_require_declared_passes_through_value() -> None:
    assert require_declared("sample_scope", "core-2026") == "core-2026"


def test_require_declared_none_is_undeclared_result_not_default() -> None:
    result = require_declared("sample_scope", None)
    assert result == Undeclared("sample_scope")


def test_require_declared_zero_is_not_none_and_passes_through() -> None:
    """0 은 falsy 지만 None 이 아니다 — falsy 접힘 금지와 같은 축."""
    assert require_declared("training_row_count", 0) == 0
