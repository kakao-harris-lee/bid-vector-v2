"""RED — 설계 검토 (5)-10 「public 시그니처 전수 test」. `ml_engine.evaluation`의
public 함수(`__all__`에 등재된 callable)를 `inspect`로 전수해, 임계·seed 목록·창
수·층 이름을 낱개 인자로 받는 매개변수 이름이 없는지 확인한다(설계 검토 (1) 첫 행 —
`EvaluationPolicy`를 받는 함수만 있어야 한다)."""

from __future__ import annotations

import importlib
import inspect
import pkgutil
import sys
import typing

import ml_engine
import ml_engine.evaluation as evaluation_module
from ml_engine.evaluation.policy import (
    _INDEXED_LIST_PREFIXES,
    _SCALAR_KEYS,
    EvaluationPolicy,
)
from ml_engine.inference.policy import InferencePolicy
from ml_engine.registry.policy import Policy as RegistryPolicy
from ml_engine.serving.policy import ServingPolicy
from ml_engine.training.policy import TrainingPolicy

_POLICY_ALIASES = frozenset({"threshold", "seeds", "stratum"})
"""정책 키의 짧은 별칭 — 키 이름 그대로가 아니어도 같은 값을 낱개로 받는 자리들."""

_FORBIDDEN_PARAMETER_NAMES = (
    frozenset(_SCALAR_KEYS) | frozenset(_INDEXED_LIST_PREFIXES) | _POLICY_ALIASES
)
"""**열거가 아니라 생성이다**(cr M-3). 앞 판은 손 열거였고, 이 slice 가 `_SCALAR_KEYS` 에
`effective_date` 를 더하면서 그 열거는 갱신되지 않았다 — `def f(*, effective_date: date, …)`
를 `evaluation.__all__` 에 더하는 길이 이 게이트를 그대로 지나갔다(`gate_baseline`·
`gate_model`·`segment_axes` 누락은 이 slice 전부터 있던 틈이다). 이제 정책 키를 더하면 이
게이트의 감시 범위가 **같은 커밋에서** 자란다 — 같은 slice 가 새 게이트(완화 경로 넷)에
적용한 규율을 옆자리에도 적용한 것이다."""

# 순수 산식·구조 유틸리티 — 정책을 모른 채(policy 매개변수 없이) 값을 그대로 받는다.
# 완화 표면이 아니다: 게이트 진입점(`gate_outcome`·`passes_gate`·
# `plan_evaluation_windows`)은 전부 `policy` 하나만 받고, 이 함수들은 그 진입점이
# `policy.*`에서 풀어 넘기는 하위 계산일 뿐 별도 CLI/env 노출이 없다(설계 검토 (1)
# 첫 행의 대상은 "임계 완화가 가능한 게이트 진입점"이지 모든 산식 함수가 아니다).
# `trial_outcome`은 H-3 뒤 `_trial_outcome`(비공개)로 내려가 이 표면
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
    """H-3 — 안정성 없이 `Passed`를 낼 수 있는 함수는 public 이 아니다."""
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


def test_forbidden_parameter_names_track_the_policy_schema() -> None:
    """cr M-3 — 모집단이 스키마에서 나온다. 정책 키를 더하고 이 게이트를 잊는 길이 닫혔다."""
    assert frozenset(_SCALAR_KEYS) <= _FORBIDDEN_PARAMETER_NAMES
    assert frozenset(_INDEXED_LIST_PREFIXES) <= _FORBIDDEN_PARAMETER_NAMES
    assert "effective_date" in _FORBIDDEN_PARAMETER_NAMES
    assert _POLICY_ALIASES <= _FORBIDDEN_PARAMETER_NAMES


# ── cr r4 M-2 · v r4 M-7 — 정책 입구 전수를 **해석된 타입**으로 모은다 ──────
# 앞 판은 ⑴ 모듈 셋을 **손으로 적고** ⑵ `"Policy" in str(return_annotation)`(주석 문자열
# 부분 일치)과 `"path" in parameters`(인자 이름)로 모집단을 만들었다. 둘 다 텍스트 술어라
# 셋이 지나갔다(verifier 실측): 타입 **별칭**으로 반환 주석을 가리기 · 인자를 `source` 로
# **개명** · 입구를 **넷째 모듈**에 두기. 셋 다 게이트를 바꾸지 않고 통과했다.
#
# 이제 모집단은 **src 전체**(`ml_engine.*` 전 모듈 순회)에서 `typing.get_type_hints` 로
# **해석한** 반환 타입이 정책 클래스를 담는 함수 전수다 — 별칭은 해석되고, 모듈 위치는
# 무관하고, 인자 이름은 모집단 선정에 쓰이지 않는다.
#
# 모집단은 평가·추론·serving·training·registry **축 전부**를 함께 센다(cr r4 L-5 — 파일이
# `tests/evaluation/` 에 있지만 범위는 그 디렉터리가 아니다).


def _policy_types() -> tuple[type, ...]:
    return (
        EvaluationPolicy,
        InferencePolicy,
        RegistryPolicy,
        ServingPolicy,
        TrainingPolicy,
    )


def _returns_a_policy(func: object) -> bool:
    try:
        hints = typing.get_type_hints(func)  # type: ignore[arg-type]
    except (NameError, TypeError, AttributeError):
        # 해석 불가 주석은 모집단 밖이다. 좁혀 잡는 이유: `except Exception` 이면 새로
        # 생긴 해석 오류를 **조용히** 모집단에서 빼 게이트가 비어 간다.
        return False
    returned = hints.get("return")
    if returned is None:
        return False
    parts = typing.get_args(returned) or (returned,)
    return any(
        isinstance(part, type) and issubclass(part, _policy_types()) for part in parts
    )


def _policy_factories() -> set[tuple[str, str]]:
    """src 전체에서 「정책류를 돌려주는 함수」 전수 — `(모듈, 이름)`."""
    found: set[tuple[str, str]] = set()
    for info in pkgutil.walk_packages(ml_engine.__path__, "ml_engine."):
        module = importlib.import_module(info.name)
        for name, obj in vars(module).items():
            if not inspect.isfunction(obj) or obj.__module__ != module.__name__:
                continue
            if _returns_a_policy(obj):
                found.add((module.__name__, name))
    return found


_POLICY_FILE_ENTRY_POINTS = frozenset(
    {
        ("ml_engine.evaluation.policy", "load_evaluation_policy"),
        ("ml_engine.evaluation.policy_source", "load_approved_evaluation_policy"),
        ("ml_engine.inference.policy", "_read_policy_file"),
        ("ml_engine.inference.policy", "load_approved_inference_policy"),
        ("ml_engine.inference.policy", "load_inference_policy"),
        ("ml_engine.serving.policy", "load_serving_policy"),
        ("ml_engine.training.policy", "load_training_policy"),
    }
)
"""정책을 **파일에서** 만드는 자리 전수 — `path` 하나만 받아야 한다. 그것이 「임계를 낱개
인자로 받는 public 함수는 없다」(설계 검토 (1) 첫 행)의 기계적 표현이다. 모듈 private
(`_read_policy_file`)도 넣는다: 거기에 매개변수가 생기면 public 래퍼 시그니처는 그대로여도
주입 자리가 열린다."""

_POLICY_DERIVATIONS = frozenset(
    {
        # 파일이 아니라 **이미 로드된 것**에서 파생한다 — 그래서 `path` 를 받지 않는다.
        ("ml_engine.evaluation.policy_source", "verify_policy_source"),
        ("ml_engine.inference.policy", "_coerce_values"),
        ("ml_engine.inference.policy", "_inference_policy_of"),
        # 뿌리 로더 — `known_keys`(스키마 전수)를 받는다. 값이 아니라 **어떤 키를 아는가**이고,
        # 소비 로더가 자기 스키마를 넘기는 자리다(그래서 `== ["path"]` 밖의 유일한 예외).
        ("ml_engine.registry.policy", "load_policy"),
    }
)
"""`path` 를 받지 않는(또는 받되 스키마 인자가 더 있는) 정책 파생 자리 — **사유와 함께**
명시 예외로 둔다. 새 함수가 이 둘 중 어느 집합에도 없으면 아래 등식이 붉다."""


def test_policy_factories_split_into_file_entry_points_and_derivations() -> None:
    """src 전체의 정책 파생 함수 전수 == 파일 입구 + 명시 예외. 새 입구를 **어느 모듈에
    두든**, 반환 주석을 **별칭으로 가려도**, 인자를 **개명해도** 이 등식이 먼저 붉다."""
    assert _policy_factories() == _POLICY_FILE_ENTRY_POINTS | _POLICY_DERIVATIONS
    assert frozenset() == _POLICY_FILE_ENTRY_POINTS & _POLICY_DERIVATIONS


def test_every_policy_file_entry_point_takes_only_a_path() -> None:
    """ML-07 acceptance 4 — CLI·env 로 값을 주입하는 추가 매개변수가 없다."""
    for module_name, name in sorted(_POLICY_FILE_ENTRY_POINTS):
        module = sys.modules[module_name]
        signature = inspect.signature(getattr(module, name))
        assert list(signature.parameters) == ["path"], (module_name, name)


def test_no_policy_derivation_takes_a_threshold_shaped_parameter() -> None:
    """명시 예외 쪽도 **임계 이름**은 받지 않는다 — 예외 선언이 주입 통로가 되지 않게."""
    violations: list[str] = []
    for module_name, name in sorted(_POLICY_DERIVATIONS):
        module = sys.modules[module_name]
        signature = inspect.signature(getattr(module, name))
        violations.extend(
            f"{module_name}.{name}({parameter})"
            for parameter in signature.parameters
            if parameter in _FORBIDDEN_PARAMETER_NAMES
        )
    assert not violations, violations
