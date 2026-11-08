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
from typing import Final, Literal

import ml_engine
import ml_engine.evaluation as evaluation_module
from ml_engine.evaluation.policy import _INDEXED_LIST_PREFIXES, _SCALAR_KEYS

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
#
# **이 test 는 src 전 모듈을 in-process 로 import 한다**(cr r5 L-5). 지금 `sys.modules` 를
# 보는 게이트는 `tests/gates/test_serving_purity.py` 하나이고 **서브프로세스 격리**라 영향이
# 0 이다. in-process 로 `sys.modules` 를 보는 게이트를 새로 만들면 같은 격리를 쓸 것.
#
# **이 전수가 지는 것은 유입 층이다**(§0 — 리뷰의 자리). 방어선이 아니다: 미승인 값이 판정에
# 닿는 것은 `evaluation.policy_source` 의 재구성 승인 축이 막는다.


_POLICY_TYPE_NAMES: Final[tuple[tuple[str, str], ...]] = (
    ("ml_engine.evaluation.policy", "EvaluationPolicy"),
    ("ml_engine.inference.policy", "InferencePolicy"),
    ("ml_engine.registry.policy", "Policy"),
    ("ml_engine.serving.policy", "ServingPolicy"),
    ("ml_engine.training.policy", "TrainingPolicy"),
)
"""정책 클래스는 **이름으로** 적고 타입 객체는 순회와 **같은 import 패스**에서 꺼낸다.

모듈 수준에서 class 를 묶으면 `issubclass` 가 조용히 거짓이 된다 — `tests/training/
conftest.py` 의 `pytest_collection_finish` 가 `ml_engine.training.*` 를 `sys.modules` 에서
지우므로, 뒤에 재-import 된 모듈의 class 객체가 **다른 객체**다(이 저장소가 5C-1 에서 처음
만난 함정이고 `tests/app/test_server.py` 머리말이 같은 것을 적는다). 표적 실행에서는 초록이고
전건에서만 붉어, 모집단이 조용히 줄어드는 모양이었다 — 실측으로 드러났다."""


_UNRESOLVED_BOUND: Final = object()
"""**해석하지 못한 경계**를 나타내는 잎 — 「모집단 밖」이 아니라 **해석 실패**로 보낸다.

`TypeVar` 의 경계·제약과 PEP 695 별칭의 `__value__` 는 `typing.get_type_hints` 가 들어가지
않는 구멍이라, 거기 적힌 문자열은 아무도 풀어 주지 않는다(`def f[P: "EvaluationPolicy"]` 의
`__bound__` 는 `str`, `TypeVar("_P", bound="EvaluationPolicy")` 는 `ForwardRef`). 아래에서
**같은 namespace 로 직접 푼다** — 풀리면 그 정책 타입이 잎으로 나와 모집단 등식이 받고, 안
풀리면 이 잎을 내서 `_resolved_hints` 가 **해석 실패**를 선언한다(해석 실패 등식이 붉다).

**주장의 범위**(cr r9 M-3): 이 fail-closed 는 ⑴ **어느 깊이의** 문자열·`ForwardRef` 잎과
⑵ 경계·제약·`__value__` 의 **지연 평가가 터지는 경우**에 대한 것이고, 그 둘이 닫는 모양은
변이로 쟀다(cr r9 M-2·M-4). **이 가지가 아닌 잔여**는 그대로 있다: 반환 주석에 정책 타입이
한 번도 나오지 않는 모양(`-> Self` · `NewType("Pol", EvaluationPolicy)` · **무경계** 제네릭
`def f[P](…) -> P`)은 풀 문자열이 없어 여기 오지 않는다 — 아래 모집단 등식 docstring 의
잔여 목록이 정본이다.

**오탐 쪽 대가는 하나가 아니라 최소 셋이다**(v r10 M-A — 앞 판은 「하나」로 적었고 그것이
잰 범위를 넘었다). 셋 다 비정책 코드를 통과도 등재도 아닌 **해석 실패**로 붉힌다.
⑴ **클래스 스코프 이름을 문자열 경계로** 쓴 함수
(`class Box: class Inner: …; def f[P: "Inner"](…) -> P`) — 그 이름이 함수 `__globals__` 에
없다(선언 목록에 등재할 수도 없다 — `get_type_hints` 자체는 성공하므로 사유 test 가 붉힌다).
⑵ **`Annotated` 의 문자열 메타데이터**가 이 가지 안에 들어온 경우
(`type Meters = Annotated[int, "meters"]` → `-> Meters` ·
`def f[T: Annotated[int, "positive"]](…) -> T`) — `get_args(Annotated[int, "meters"])` 는
`(int, "meters")` 라 **메타데이터가 잎 자리로** 가고, 거기서 `"meters"` 는 타입이 아니라
풀리지 않는다. ⑶ **제네릭 별칭이 자기 타입 파라미터를 문자열로** 가리키는 경우
(`type Box[T] = list["T"]` → `-> Box`) — 푸는 namespace 가 함수 쪽이라 `"T"` 가 없다.

⑵⑶ 이 터지는 자리는 `get_type_hints` 가 **들어가지 않는 구멍**(별칭 `__value__` ·
`TypeVar` 경계)뿐이다 — 매개변수·반환에 **직접** 적은 `Annotated[int, "meters"]` 는
`get_type_hints` 가 최상위를 벗기므로 통과한다(v r10 F1·F2 GREEN 실측). 셋 다 src 에
**0** 이고(집합 불변), 탈출구는 ⑴ 따옴표 제거 · ⑵ 메타데이터를 비문자열로 · ⑶ 첨자를
적기(`-> Box[int]`)다. **후속 처방**(이 slice 밖, 등재만 한다): `get_origin` 이
`Annotated` 면 첫 인자만 펴고 `__metadata__` 는 버린다."""


def _flatten_annotation(
    annotation: object, namespace: dict[str, object]
) -> list[object]:
    """주석을 **끝까지 펴서** 잎 노드 목록으로.

    `typing.get_type_hints` 는 `type X = …`(PEP 695) **별칭을 풀지 않는다** — 반환값이
    `TypeAliasType` 이고 `get_args` 는 빈 tuple 이다(실측). 그래서 앞 판은 별칭으로 가린
    입구를 모집단에서 놓쳤다(변이 O4 가 GREEN 으로 그것을 잡았다). 별칭은 `__value__` 로
    한 겹 풀고, 합 타입은 `get_args` 로 풀고, **재귀**한다(별칭의 별칭도 풀린다).

    `namespace` 는 문자열 경계를 푸는 자리다(`_annotation_namespace`)."""
    return _flatten_with_guard(annotation, set(), namespace)


def _flatten_with_guard(
    annotation: object, seen: set[int], namespace: dict[str, object]
) -> list[object]:
    """`_flatten_annotation` 의 본체 — **재귀 별칭 가드**가 있다.

    이 저장소에는 자기를 참조하는 합 타입 별칭이 있어(실측: 가드 없이 `RecursionError`),
    이미 지난 노드를 `id` 로 기억한다. 가드가 **잎을 버리지는 않는다**: 같은 노드를 두 번째로
    만났을 때만 멈추므로 처음 만난 모든 잎이 결과에 들어간다.

    **문자열은 잎 자리에서 푼다**(cr r9 M-2): 경계 자리에서만 풀면 한 겹만 깊어도 샌다 —
    `[P: list["EvaluationPolicy"]]` · `[P: Optional["EvaluationPolicy"]]` ·
    `[P: tuple["EvaluationPolicy", int]]` · `TypeVar("_T", bound=Optional["EvaluationPolicy"])` ·
    `type Pol = "EvaluationPolicy"` 다섯이 두 인터프리터 모두 **GREEN** 이었다(실측). 여기서
    풀면 깊이와 무관해진다 — 풀리면 모집단, 못 풀면 해석 실패."""
    if annotation is _UNRESOLVED_BOUND:
        # 가드보다 먼저 — 해석 실패 표지는 몇 번을 만나든 잎으로 남아야 한다.
        return [annotation]
    if isinstance(annotation, str | typing.ForwardRef):
        annotation = _resolved_bound(annotation, namespace)
        if annotation is _UNRESOLVED_BOUND:
            return [annotation]
    if id(annotation) in seen:
        return []
    seen.add(id(annotation))
    if isinstance(annotation, typing.TypeAliasType):
        # 별칭의 `__value__` 도 **지연 평가**라 읽기 자체가 터질 수 있다(아래 경계와 같은
        # 기제 — `type Pol = HiddenPolicy` 가 `TYPE_CHECKING` 전용이면 `NameError`, 실측).
        try:
            value = annotation.__value__
        except Exception:  # noqa: BLE001 — 지연 평가가 무엇을 던지든 해석 실패로 보낸다
            return [_UNRESOLVED_BOUND]
        return _flatten_with_guard(value, seen, namespace)
    if isinstance(annotation, typing.TypeVar):
        # 불변 ①: `-> P` 를 잎으로 두면 `_is_policy_type` 이 거짓이라 그 입구가 모집단에서
        # 조용히 빠진다 — 경계(`__bound__`)와 제약(`__constraints__`)을 펴야 정책 타입이
        # 잎으로 나온다(PEP 695 · 구식 `TypeVar(bound=…)` 같은 가지).
        # 불변 ②: 그 읽기 **자체가** 터질 수 있다 — PEP 695 의 지연 경계가
        # `TYPE_CHECKING` 전용 이름을 가리키면 `NameError` 가 전파된다. 터지면 크래시가
        # 아니라 해석 실패로 보낸다. 문자열 경계는 위 잎 자리가 푼다.
        # (v r7 M-1 · v r8 M-1' · cr r9 M-2·M-4)
        try:
            bound = annotation.__bound__
            constraints = annotation.__constraints__
        except Exception:  # noqa: BLE001 — 지연 평가가 무엇을 던지든 해석 실패로 보낸다
            return [_UNRESOLVED_BOUND]
        leaves: list[object] = []
        if bound is not None:
            leaves.extend(_flatten_with_guard(bound, seen, namespace))
        for constraint in constraints:
            leaves.extend(_flatten_with_guard(constraint, seen, namespace))
        return leaves or [annotation]
    if typing.get_origin(annotation) is Literal:
        # `Literal["approved"]` 의 인자는 **타입이 아니라 값**이다 — 잎 자리로 보내면
        # 해석 실패로 붉어진다(통제군 실측). 여기서 잎으로 세운다.
        return [annotation]
    args = typing.get_args(annotation)
    if not args:
        return [annotation]
    leaves: list[object] = []
    for arg in args:
        leaves.extend(_flatten_with_guard(arg, seen, namespace))
    return leaves


def _resolved_bound(bound: object, namespace: dict[str, object]) -> object:
    """문자열로 적은 `TypeVar` 경계·제약을 **같은 namespace 로** 푼다.

    푸는 자리를 `get_type_hints` 로 통일한다(직접 `eval` 하지 않는다): 빈 함수에 그
    표현식을 주석으로 달아 해석시키면, 이 저장소가 전 모듈에 쓰는
    `from __future__ import annotations` 와 **같은 경로**로 풀린다. 못 풀면
    `_UNRESOLVED_BOUND` — 호출부가 해석 실패로 올린다.

    **예외를 좁게 열거하지 않는다**(cr r9 L-1): 여기서 평가되는 것은 소스가 적은 **임의
    표현식**이라 실패의 종류가 닫히지 않는다 — `bound="[][1]"` 는 `IndexError` 로 열거 밖에서
    터졌다(실측). 종류를 세는 자리는 선언된 해석 실패의 **사유 test** 이고, 여기는 fail-closed
    가지다."""
    if not isinstance(bound, str | typing.ForwardRef):
        return bound
    expression = bound if isinstance(bound, str) else bound.__forward_arg__

    def probe() -> None: ...

    probe.__annotations__ = {"return": expression}
    try:
        return typing.get_type_hints(probe, globalns=dict(namespace))["return"]
    except Exception:  # noqa: BLE001 — 임의 표현식 평가, 위 docstring 참조
        return _UNRESOLVED_BOUND


_UNRESOLVABLE_ANNOTATIONS: Final[frozenset[tuple[str, str]]] = frozenset(
    {
        # 넷 다 **같은 구조적 사유**다: 주석이 생성 stub 의 모듈 속성을 이름으로 가리키는데
        # 그 이름이 모듈 속성으로 존재하지 않는다(중첩 메시지). `AttributeError` 이고,
        # 파이썬 버전이 아니라 **생성 stub 의 모양**이 정한다 — 아래 test 가 그 사유까지 잰다.
        ("ml_engine.serving.prediction", "_validate_selector"),
        ("ml_engine.training.jobs.servicer", "_fill_failure"),
        ("ml_engine.training.jobs.servicer", "_fill_store_unavailable"),
        (
            "ml_engine.training.jobs.servicer",
            "TrainingJobServicer._validate_get_or_cancel",
        ),
    }
)
"""`typing.get_type_hints` 가 해석하지 못하는 함수 전수 — **등식으로 잠근다**(cr r5 M-1).

**앞 판은 여기에 PEP 695 제네릭 둘**(`features.schema.require_declared` ·
`features.encoding._accumulate`)**이 더 있었고, 그래서 이 등식이 환경 의존이었다** —
로컬(3.12.3)에서는 `NameError` 로 실패하고 CI(더 뒤 3.12 패치)에서는 해석돼 **CI 만
붉었다**(PR #139 실측). `_resolved_hints` 가 타입 파라미터를 직접 넘겨 둘 다 **어느
파이썬에서도** 해석되게 했고, 그래서 이 집합은 환경과 무관한 넷만 남는다.

집합 등식은 **발견된 것**만 비교하므로, 모집단에서 빠지는 함수는 등식을 붉히지 않는다
(없는 것과 같은 값이다). 그래서 해석 실패를 「모집단 밖」으로 조용히 흘리지 않고 **이 목록과
같은지** 본다. 우회가 쓰는 길이 바로 그것이었다: 반환 타입을 `if TYPE_CHECKING:` 에서만
import 하면(이 저장소는 전 모듈이 `from __future__ import annotations`) `NameError` 가 나고
그 입구가 조용히 모집단에서 빠진다 — `paired_t_threshold` 를 낱개로 받는 public 입구를
더해도 게이트가 초록이었다(cr 실측)."""


def _resolution_localns(func: object) -> dict[str, object]:
    """PEP 695 타입 파라미터를 이름으로 — 해석 namespace 로 넘길 몫.

    **두 자리가 같은 namespace 를 써야 한다**(v r7 참고): 등식이 쓰는 `_resolved_hints` 와
    사유를 재는 test 가 다른 namespace 로 부르면, 「PEP 695 제네릭이면서 stub 속성 부재」인
    함수가 선언 목록에 들어오는 날 그 test 가 **다시 파이썬 버전에 따라 갈린다**(먼저 나는
    예외가 `NameError` 냐 `AttributeError` 냐가 버전마다 다르다). 오늘 선언된 넷에는
    해당하지 않지만, 한 줄로 그 자리를 없앤다."""
    return {param.__name__: param for param in getattr(func, "__type_params__", ())}


def _annotation_namespace(func: object) -> dict[str, object]:
    """문자열 경계를 푸는 namespace — `get_type_hints` 가 쓰는 것과 **같은 자리**.

    `get_type_hints` 는 함수의 `__globals__` 에 `localns` 를 얹어 주석을 푼다. 경계는 그
    함수가 들어가지 않는 구멍이므로 여기서 같은 조합을 만들어 `_resolved_bound` 에 넘긴다 —
    두 자리가 다른 namespace 를 쓰면 「주석은 풀리는데 경계는 안 풀린다」가 사유가 아니라
    namespace 차이로 생긴다."""
    namespace: dict[str, object] = dict(getattr(func, "__globals__", {}))
    namespace.update(_resolution_localns(func))
    return namespace


def _resolved_hints(
    func: object, namespace: dict[str, object]
) -> dict[str, object] | None:
    """해석된 주석, 해석 불가면 `None`.

    `namespace` 는 **호출부가 만들어 넘긴다**(cr r9 L-3): 모집단 판정은 같은 함수에 대해
    해석과 펼치기를 잇달아 하므로, 안에서 또 만들면 `__globals__` 복사가 함수당 두 번이다.

    **PEP 695 타입 파라미터를 직접 넣어 준다**(`def f[T](...)`). 이 저장소는 전 모듈이
    `from __future__ import annotations` 이라 주석이 문자열이고, `get_type_hints` 는 그것을
    모듈 전역에서 찾는데 `T` 는 **함수의 타입 파라미터 스코프**에 있다. CPython 3.12 는 그
    스코프를 보는 시점이 패치 버전에 따라 **다르다** — 3.12.3 에서는 `NameError`, 더 뒤
    패치에서는 해석된다(실측: 로컬 3.12.3 실패 ↔ CI 성공). 그 차이가 아래 해석 실패 등식을
    **환경 의존**으로 만들었다(CI 만 붉었다).

    `__type_params__` 를 `localns` 로 넘기면 **어느 파이썬에서도** 해석된다 — 등식에 남는
    실패는 환경과 무관한 것(생성 stub 의 모듈 속성 부재)뿐이다.

    **`get_type_hints` 가 성공해도 끝이 아니다**(v r8 M-1'): 그것은 `TypeVar` 의 경계
    안으로 들어가지 않으므로, 문자열 경계는 풀리지 않은 채 남는다. 펴 본 잎에 해석 실패
    표지가 있으면 **여기서 해석 실패를 선언한다** — 그 함수는 모집단에서 빠지는 대신 해석
    실패 등식을 붉힌다."""
    try:
        hints = typing.get_type_hints(  # type: ignore[arg-type]
            func, localns=_resolution_localns(func)
        )
    except (NameError, TypeError, AttributeError):
        return None
    if any(
        leaf is _UNRESOLVED_BOUND
        for hint in hints.values()
        for leaf in _flatten_annotation(hint, namespace)
    ):
        return None
    return hints


def _returns_a_policy(func: object, policy_types: tuple[type, ...]) -> bool:
    namespace = _annotation_namespace(func)
    hints = _resolved_hints(func, namespace)
    if hints is None:
        return False
    returned = hints.get("return")
    if returned is None:
        return False
    return any(
        _is_policy_type(part, policy_types)
        for part in _flatten_annotation(returned, namespace)
    )


def _is_policy_type(part: object, policy_types: tuple[type, ...]) -> bool:
    """정책 타입 판정 — **동일성에 기대지 않는다**(cr r5 M-1 ⑶).

    `issubclass` 는 class **객체 동일성**을 보므로, 모듈 캐시가 갈린 상태에서 해석된 주석이
    **옛 class** 를 가리키면 거짓이 된다(`tests/training/conftest.py` 가 `ml_engine.training.*`
    를 지운다). 그래서 `(모듈, 이름)` 좌표로도 함께 본다 — 캐시 상태와 무관해진다."""
    if not isinstance(part, type):
        return False
    if issubclass(part, policy_types):
        return True
    return (part.__module__, part.__name__) in _POLICY_TYPE_NAMES


def _src_functions() -> list[tuple[str, str, object]]:
    """src 전체의 함수 전수 — 모듈 수준 함수 + **클래스 멤버**(함수·`staticmethod`·
    `classmethod`).

    앞 판은 `vars(module)` 의 `inspect.isfunction` 만 봤고(클래스 멤버 전부 밖), 그 다음 판은
    클래스 멤버를 `inspect.isfunction` 으로 걸러 **`@classmethod` 를 놓쳤다** —
    `getattr(cls, name)` 이 bound method 를 주기 때문이다(cr 실측: `@staticmethod` 는 RED,
    `@classmethod` 는 GREEN). `inspect.isroutine` 은 둘을 함께 받고, `__module__` 비교와
    `get_type_hints` 는 bound method 에도 그대로 돈다.

    **덮지 못하는 모양**(등재): `property` getter(인자를 못 받아 주입 통로로 약하다) · 모듈
    수준 `lambda` · 중첩 클래스의 멤버. 이 전수가 지는 것은 **유입 층**이라 그 잔여는 리뷰의
    자리다(파일 머리 참조)."""
    found: list[tuple[str, str, object]] = []
    for info in pkgutil.walk_packages(ml_engine.__path__, "ml_engine."):
        module = importlib.import_module(info.name)
        for name, obj in vars(module).items():
            if inspect.isroutine(obj) and (
                getattr(obj, "__module__", None) == module.__name__
            ):
                found.append((module.__name__, name, obj))
            elif inspect.isclass(obj) and obj.__module__ == module.__name__:
                found.extend(
                    (module.__name__, f"{name}.{member_name}", member)
                    for member_name, member in inspect.getmembers(
                        obj, inspect.isroutine
                    )
                    if getattr(member, "__module__", None) == module.__name__
                    and not member_name.startswith("__")
                )
    return found


def _policy_types_now() -> tuple[type, ...]:
    return tuple(
        getattr(importlib.import_module(module_name), type_name)
        for module_name, type_name in _POLICY_TYPE_NAMES
    )


def _policy_factories() -> set[tuple[str, str]]:
    """src 전체에서 「정책류를 돌려주는 함수」 전수 — `(모듈, 이름)`."""
    functions = _src_functions()
    policy_types = _policy_types_now()
    return {
        (module_name, name)
        for module_name, name, func in functions
        if _returns_a_policy(func, policy_types)
    }


def _unresolvable_annotations() -> set[tuple[str, str]]:
    return {
        (module_name, name)
        for module_name, name, func in _src_functions()
        if _resolved_hints(func, _annotation_namespace(func)) is None
    }


def test_annotation_resolution_failures_are_exactly_the_declared_ones() -> None:
    """해석 실패가 **늘면 붉다**(cr r5 M-1 ⑴). 이것이 없으면 「반환 타입을 TYPE_CHECKING
    에서만 import 한 입구」가 조용히 모집단에서 빠진다 — 집합 등식은 발견된 것만 비교한다."""
    assert _unresolvable_annotations() == _UNRESOLVABLE_ANNOTATIONS


def test_every_declared_resolution_failure_has_the_same_structural_cause() -> None:
    """**등식이 환경에 흔들리지 않는 이유**를 함께 잰다(PR #139).

    넷 다 `AttributeError` 여야 한다 — 주석이 생성 stub 의 없는 모듈 속성을 가리키는 모양이고,
    그것은 파이썬 패치 버전이 아니라 **생성 stub 의 모양**이 정한다. 다른 종류의 실패
    (예: PEP 695 타입 파라미터의 `NameError`)가 섞이면 그 순간 **이 test 가 붉다** — 위
    등식만 있으면 그런 실패는 「선언했으니 괜찮다」로 조용히 묻힌다.

    같은 이유로 **문자열 경계가 안 풀려 생긴 해석 실패는 이 목록에 등재할 수 없다**(v r8
    M-1' 처방의 짝, 실측): 그런 함수는 `get_type_hints` 자체는 성공하므로 여기서 「이제
    해석된다 — 목록에서 빼라」로 붉다. 위 등식을 피하려고 선언 목록에 적는 길이 막힌다."""
    for module_name, name in sorted(_UNRESOLVABLE_ANNOTATIONS):
        func = _resolve(module_name, name)
        try:
            typing.get_type_hints(  # type: ignore[arg-type]
                func, localns=_resolution_localns(func)
            )
        except AttributeError:
            continue
        except Exception as exc:  # 사유 **종류**를 보고하려고 넓게 받는다
            raise AssertionError(
                f"{module_name}.{name} 의 해석 실패 사유가 AttributeError 가 아니다: "
                f"{type(exc).__name__}"
            ) from exc
        raise AssertionError(f"{module_name}.{name} 이 이제 해석된다 — 목록에서 빼라")


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

_POLICY_ENTRY_EXCEPTIONS = frozenset(
    {
        # 경로를 **인자로** 받지 않는다 — 검사 대상 객체가 들고 온 경로에서 재구성한다
        # (cr r5 L-1 — 「이미 로드된 것에서 파생한다」는 r4 뒤 거짓이 됐다).
        ("ml_engine.evaluation.policy_source", "verify_policy_source"),
        ("ml_engine.inference.policy", "_coerce_values"),
        ("ml_engine.inference.policy", "_inference_policy_of"),
        # 뿌리 로더 — `known_keys`(스키마 전수)를 받는다. 값이 아니라 **어떤 키를 아는가**이고,
        # 소비 로더가 자기 스키마를 넘기는 자리다.
        ("ml_engine.registry.policy", "load_policy"),
        # backtest CLI 의 합성 로더 — 경로 **둘**(판정 정책 + 추론 정책)을 받는다. 오프라인
        # 분석 경로이고 **승격 판정에 닿지 않는다**(verifier r4 실측: `evaluation/backtest/**`
        # 와 `app/backtest_*.py` 에 `run_holdout`·`EvaluationPolicy`·promotion 참조 0).
        # 그 축의 판정 정책 `strategy-backtest-v2` 는 일부러 미등록이다(6G 판정 입력 불간섭).
        ("ml_engine.app.backtest_job", "_load_policies"),
    }
)
"""`path` 하나만 받는 모양이 아닌 정책 자리 — **사유와 함께** 명시 예외로 둔다. 새 함수가
파일 입구도 예외도 아니면 아래 등식이 붉다."""


def _resolve(module_name: str, name: str) -> object:
    """`(모듈, 이름)` 좌표를 객체로 — 이름이 `Class.member` 꼴이면 점으로 쪼갠다.

    cr r6 L-5 — `_src_functions` 가 클래스 멤버를 그 꼴로 좌표화하므로, 입구·예외 집합에
    클래스 멤버가 들어오는 날 `getattr(module, "Class.member")` 가 `AttributeError` 로
    죽는다(단언이 아니라 사고다). 지금 열하나는 전부 모듈 수준이라 도달 불가지만, 그
    도달 불가에 기대지 않는다."""
    resolved: object = sys.modules[module_name]
    for part in name.split("."):
        resolved = getattr(resolved, part)
    return resolved


def test_policy_factories_split_into_file_entry_points_and_derivations() -> None:
    """src 전체의 정책 파생 함수 전수 == 파일 입구 + 명시 예외.

    **측정한 모양**에 대해 이 등식이 먼저 붉다: 모듈 수준 함수 · 클래스 멤버
    (`staticmethod`·`classmethod`) · 어느 모듈에 두든 · 반환 주석을 PEP 695 **무첨자
    별칭**으로 가려도(해석이 풀고, 자기참조는 가드 — 첨자를 단 별칭은 아래 잔여다,
    v r10 M-B) · 인자를 **개명**해도 · 반환 타입을
    `TYPE_CHECKING` 에서만 import 해도(해석 실패 등식이 받는다) · **PEP 695 제네릭 반환**
    (`def f[P: EvaluationPolicy](…) -> P`)이나 구식 `TypeVar(bound=…)` 반환으로 가려도
    (경계·제약을 펴서 받는다 — v r7 M-1, 두 인터프리터에서 RED 실측) · 그 경계를
    **따옴표로 적어도**(`[P: "EvaluationPolicy"]` · `TypeVar("_P", bound="EvaluationPolicy")`
    — 풀리면 이 등식이, 안 풀리면 해석 실패 등식이 받는다; v r8 M-1', 두 인터프리터 RED 실측)
    · 그 문자열이 **한 겹 더 깊어도**(`[P: list["EvaluationPolicy"]]` ·
    `[P: Optional["EvaluationPolicy"]]` · `[P: tuple["EvaluationPolicy", int]]` ·
    `TypeVar("_T", bound=Optional["EvaluationPolicy"])` · `type Pol = "EvaluationPolicy"`
    — 잎 자리에서 푼다; cr r9 M-2, 두 인터프리터 RED 실측).

    **잔여 반례 — 닫지 않았다**(측정해서 등재한다, r6 L-2 계열): `@classmethod … -> Self` ·
    `NewType("Pol", EvaluationPolicy)` 반환 · **무경계** 제네릭 `def f[P](…) -> P`. 셋 다
    ruff·`mypy --strict` 를 통과하는 모양으로 두 인터프리터에서 **GREEN** 이다(v r8 실측).
    **여기에 별칭 두 모양을 더 등재한다**(v r10 M-B, 두 버전 GREEN 실측 — 이 라운드가 만든
    것이 아니라 처음부터 있던 틈이다): ⑴ **첨자를 단** 제네릭 별칭
    (`type Pol[T] = EvaluationPolicy` → `-> Pol[int]` · `type Pol[T] = T | EvaluationPolicy`)
    — `get_type_hints` 가 내주는 것이 `TypeAliasType` 이 아니라 그것을 origin 으로 하는
    첨자 꼴이라, 아래 별칭 가지가 받지 못하고 `get_args` 가 **첨자 인자만**(`(int,)`) 낸다.
    ⑵ `typing_extensions.TypeAliasType("Pol", EvaluationPolicy)` — 3.12 에서 이 타입은
    `typing.TypeAliasType` 이 **아니라** 같은 가지로 가지 않는다. 둘 다 정책 타입을 반환하는
    함수를 모집단 **밖**에 둔다(`paired_t_threshold` 인자까지 실린 모양으로 실측).

    반대편 대가도 등재한다: **클래스 스코프 이름의 문자열 경계** · **`Annotated` 의 문자열
    메타데이터**(별칭 값·`TypeVar` 경계 안) · **제네릭 별칭의 자기 파라미터 문자열**은 통과도
    등재도 아닌 해석 실패로 붉다(`_UNRESOLVED_BOUND` docstring 「주장의 범위」, v r9 C4 ·
    v r10 M-A).
    무경계 제네릭은 반환 주석에 정책 타입이 한 번도 나오지 않으므로 **반환 타입 술어로는
    원리상** 못 닫는다. 이 전수가 지는 것은
    **유입 층**이고 방어선이 아니다 — 그 잔여는 리뷰의 자리이고, 미승인 값이 판정에 닿는 것은
    `evaluation.policy_source` 의 재구성 승인 축이 막는다(파일 머리 참조)."""
    assert _policy_factories() == _POLICY_FILE_ENTRY_POINTS | _POLICY_ENTRY_EXCEPTIONS
    assert frozenset() == _POLICY_FILE_ENTRY_POINTS & _POLICY_ENTRY_EXCEPTIONS


def test_every_policy_file_entry_point_takes_only_a_path() -> None:
    """ML-07 acceptance 4 — CLI·env 로 값을 주입하는 추가 매개변수가 없다."""
    for module_name, name in sorted(_POLICY_FILE_ENTRY_POINTS):
        signature = inspect.signature(_resolve(module_name, name))
        assert list(signature.parameters) == ["path"], (module_name, name)


def test_no_policy_entry_exception_takes_a_threshold_shaped_parameter() -> None:
    """명시 예외 쪽도 **임계 이름**은 받지 않는다 — 예외 선언이 주입 통로가 되지 않게."""
    violations: list[str] = []
    for module_name, name in sorted(_POLICY_ENTRY_EXCEPTIONS):
        signature = inspect.signature(_resolve(module_name, name))
        violations.extend(
            f"{module_name}.{name}({parameter})"
            for parameter in signature.parameters
            if parameter in _FORBIDDEN_PARAMETER_NAMES
        )
    assert not violations, violations
