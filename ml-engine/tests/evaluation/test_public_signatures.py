"""RED — 설계 검토 (5)-10 「public 시그니처 전수 test」. `ml_engine.evaluation`의
public 함수(`__all__`에 등재된 callable)를 `inspect`로 전수해, 임계·seed 목록·창
수·층 이름을 낱개 인자로 받는 매개변수 이름이 없는지 확인한다(설계 검토 (1) 첫 행 —
`EvaluationPolicy`를 받는 함수만 있어야 한다)."""

from __future__ import annotations

import inspect

import ml_engine.evaluation as evaluation_module

_FORBIDDEN_PARAMETER_NAMES = frozenset(
    {
        "threshold",
        "paired_t_threshold",
        "maturity_threshold",
        "min_evaluation_rows",
        "max_origins",
        "agency_baseline_min_count",
        "stability_seeds",
        "seeds",
        "amount_band_edges",
        "gate_stratum",
        "stratum",
    }
)

# 순수 산식·구조 유틸리티 — 정책을 모른 채(policy 매개변수 없이) 값을 그대로 받는다.
# 완화 표면이 아니다: 게이트 진입점(`gate_outcome`·`passes_gate`·
# `plan_evaluation_windows`)은 전부 `policy` 하나만 받고, 이 함수들은 그 진입점이
# `policy.*`에서 풀어 넘기는 하위 계산일 뿐 별도 CLI/env 노출이 없다(설계 검토 (1)
# 첫 행의 대상은 "임계 완화가 가능한 게이트 진입점"이지 모든 산식 함수가 아니다).
# `trial_outcome`은 verifier r1 H-3 뒤 `_trial_outcome`(비공개)로 내려가 이 표면
# 전수 대상에 없다 — `evaluation.__all__`에 등재되지 않는다.
_POLICY_UNAWARE_UTILITIES = frozenset(
    {
        "holdout_overlaps",  # stratum: 순수 인덱스 계산, 정책 자체를 모른다
        "indices_in_window",  # stratum: 위와 같음
        "minimum_detectable_improvement",  # threshold: legacy 그대로, 호출부가 policy 에서 전달
        "required_row_count",  # threshold: 위와 같음
    }
)


def _public_callables() -> list[tuple[str, object]]:
    return [
        (name, getattr(evaluation_module, name))
        for name in evaluation_module.__all__
        if callable(getattr(evaluation_module, name))
        and inspect.isfunction(getattr(evaluation_module, name))
    ]


def test_evaluation_public_functions_exist_for_every_all_entry() -> None:
    missing = [
        name
        for name in evaluation_module.__all__
        if not hasattr(evaluation_module, name)
    ]
    assert not missing


def test_no_evaluation_public_function_takes_bare_threshold_or_seed_parameters() -> (
    None
):
    violations: list[str] = []
    for name, func in _public_callables():
        if name in _POLICY_UNAWARE_UTILITIES:
            continue
        signature = inspect.signature(func)
        for parameter_name in signature.parameters:
            if parameter_name in _FORBIDDEN_PARAMETER_NAMES:
                violations.append(f"{name}({parameter_name})")
    assert not violations, (
        f"evaluation public 함수가 임계·seed·창 수를 낱개 인자로 받는다: {violations}"
    )


def test_gate_outcome_and_passes_gate_require_policy_parameter() -> None:
    for func in (evaluation_module.gate_outcome, evaluation_module.passes_gate):
        assert "policy" in inspect.signature(func).parameters


def test_trial_outcome_not_in_public_surface() -> None:
    """verifier r1 H-3 — 안정성 없이 `Passed`를 낼 수 있는 함수는 public 이 아니다."""
    assert "trial_outcome" not in evaluation_module.__all__
    assert not hasattr(evaluation_module, "trial_outcome")


def test_policy_unaware_utilities_allowlist_has_no_stale_entries() -> None:
    """반대 방향 확인 — 허용 목록에 이제는 낱개 인자가 없어진 이름이 남지 않게."""
    names_with_forbidden_params = set()
    for name, func in _public_callables():
        signature = inspect.signature(func)
        if any(p in _FORBIDDEN_PARAMETER_NAMES for p in signature.parameters):
            names_with_forbidden_params.add(name)
    stale = _POLICY_UNAWARE_UTILITIES - names_with_forbidden_params
    assert not stale, f"허용 목록에 더 이상 해당하지 않는 항목이 있다: {stale}"


def test_load_evaluation_policy_is_the_only_way_to_construct_shipped_values() -> None:
    """`load_evaluation_policy`는 `path` 하나만 받는다 — CLI·env 로 값을 주입하는
    추가 매개변수가 없다(ML-07 acceptance ④)."""
    signature = inspect.signature(evaluation_module.load_evaluation_policy)
    assert list(signature.parameters) == ["path"]
