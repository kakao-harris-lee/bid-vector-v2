"""D-6G-64 (vr r4 L-14) — **해시의 용도 구분자**가 문서와 코드에서 같은가.

표본 추첨(Kotlin)과 S0 밴드 내 난수(Python)가 같은 `seed|key` 형태를 쓰면, 두 seed 가
우연히 같을 때 「어떤 공고가 뽑혔나」와 「S0 가 그 공고에 낸 값」이 **같은 digest** 에서
나온다. 구분자를 맨 앞에 붙여 두 영역을 가른다(스키마 §2.2).

이 test 가 정본으로 삼는 것은 **스키마 문서의 표**다 — 두 레인이 같이 읽는 자리가 거기
하나뿐이기 때문이다(업무 구분 어휘를 문서에 맞대는 것과 같은 갈래). 그래서 문서만
고치거나 코드만 고치면 **둘 중 하나가 RED** 다:

- 코드의 구분자 상수를 바꾸면 문서와 갈려 RED
- 해시 재료에서 구분자(또는 `|`)를 빼면, 문서 표로 지은 기대 digest 와 갈려 RED
- 문서 표를 고치고 코드를 두면 같은 두 단언이 RED

Kotlin 쪽 구분자는 Python 이 **쓰지 않아야 하는 이름**이다 — 스트림 이름이 곧 구분자가
됐으므로, 어떤 전략이 `sample-draw` 를 스트림으로 쓰면 가른 영역이 다시 붙는다. 스트림
이름은 AST 로 전수 수집한다(이름 열거가 아니라 호출 자리에서 도출).
"""

from __future__ import annotations

import ast
import hashlib
import re
from pathlib import Path
from typing import Final

import numpy as np
from _backtest_support import (
    SHIPPED_BACKTEST_POLICY_PATH,
    manifest_bytes,
    row_payload,
    rows_bytes,
    sample_list_bytes,
)

from ml_engine.evaluation.backtest.exclusions import admit_rows
from ml_engine.evaluation.backtest.observations import LoadedSnapshot
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.snapshot import load_snapshot
from ml_engine.evaluation.backtest.strategies import (
    S0_BAND_PURPOSE,
    StrategyInput,
    build_strategy_input,
    notice_rng,
)

_TESTS_ROOT = Path(__file__).resolve().parents[1]
_SOURCE_ROOT = _TESTS_ROOT.parents[0] / "src" / "ml_engine"
_SCHEMA_DOCUMENT = (
    _TESTS_ROOT.parents[1] / "reports" / "evidence" / "m6" / "6g" / "snapshot-schema.md"
)

_SECTION_MARKER: Final[str] = "## 2.2"
_TABLE_ROW: Final[re.Pattern[str]] = re.compile(
    r"^\|[^|]+\|\s*`sha256\((?P<expr>[^`]+)\)`\s*\|\s*(?P<lane>[A-Za-z]+)\s*\|\s*$"
)
_SEED_TOKEN: Final[str] = "seed"
_KEY_TOKEN: Final[str] = "공고 키 해시"
_RNG_FUNCTION: Final[str] = "notice_rng"
_STREAM_KEYWORD: Final[str] = "stream"


def _section() -> str:
    text = _SCHEMA_DOCUMENT.read_text(encoding="utf-8")
    assert _SECTION_MARKER in text, "스키마 문서에서 §2.2 절을 찾지 못했다"
    start = text.index(_SECTION_MARKER)
    rest = text.index("\n## ", start + len(_SECTION_MARKER))
    return text[start:rest]


def _documented_material(expr: str, *, seed: int, key: str) -> str:
    """문서의 `sha256(...)` 인자식을 **그대로** 재료 문자열로 짓는다.

    토큰을 이름으로 알아보고 값으로 바꾸기만 한다 — 형태(구분자 위치·개수·순서)는
    문서가 정한다. 코드가 형태를 바꾸면 여기서 지은 digest 와 갈린다."""
    parts: list[str] = []
    for token in (item.strip() for item in expr.split("+")):
        if token.startswith('"') and token.endswith('"'):
            # 마크다운 표 안에서 `|` 는 `\|` 로 escape 된다.
            parts.append(token[1:-1].replace("\\|", "|"))
        elif token == _SEED_TOKEN:
            parts.append(str(seed))
        elif token == _KEY_TOKEN:
            parts.append(key)
        else:  # pragma: no cover - 문서에 새 토큰이 생기면 여기서 멈춘다
            raise AssertionError(f"§2.2 표의 알 수 없는 토큰: {token!r}")
    return "".join(parts)


def _documented_purposes() -> dict[str, str]:
    """레인 -> 용도 구분자. 문서 표에서만 온다."""
    purposes: dict[str, str] = {}
    for line in _section().splitlines():
        matched = _TABLE_ROW.match(line)
        if matched is None:
            continue
        quoted = re.findall(r'"((?:[^"\\]|\\.)*)"', matched.group("expr"))
        assert quoted, f"§2.2 표의 행에 용도 구분자가 없다: {line}"
        purposes[matched.group("lane")] = quoted[0]
    return purposes


def _documented_expressions() -> dict[str, str]:
    expressions: dict[str, str] = {}
    for line in _section().splitlines():
        matched = _TABLE_ROW.match(line)
        if matched is not None:
            expressions[matched.group("lane")] = matched.group("expr")
    return expressions


def test_schema_document_declares_both_lanes() -> None:
    """표가 두 레인을 다 싣는다 — 한 줄이 사라지면 아래 단언들이 헛돈다."""
    assert set(_documented_purposes()) == {"Kotlin", "Python"}


def test_python_purpose_separator_equals_the_schema_document() -> None:
    """문서 ↔ 코드 등식. 한쪽만 움직이면 RED 다(변이: 상수 값을 바꾼다)."""
    documented = _documented_purposes()
    assert documented["Python"] == S0_BAND_PURPOSE, (
        f"문서 §2.2 의 S0 구분자 {documented['Python']!r} != 코드 상수 "
        f"{S0_BAND_PURPOSE!r} — 두 레인이 같이 움직여야 한다"
    )


def test_s0_random_source_hashes_the_material_the_document_declares() -> None:
    """난수원이 **문서가 적은 재료**에서 나온다 — 구분자를 빼거나 `|` 를 지우면
    여기서 지은 기대 digest 와 갈려 RED 다(변이: 재료에서 구분자 제거)."""
    seed = 7
    request = _strategy_input(seed=seed)
    key = request.notice.notice_key_hash
    material = _documented_material(
        _documented_expressions()["Python"], seed=seed, key=key
    )
    assert S0_BAND_PURPOSE in material and material.count("|") == 2, (
        f"문서에서 지은 재료가 §2.2 의 형태가 아니다: {material!r}"
    )
    expected = np.random.default_rng(
        int.from_bytes(hashlib.sha256(material.encode("utf-8")).digest(), "big")
    )
    actual = notice_rng(request, stream=S0_BAND_PURPOSE)
    assert actual.random() == expected.random()


def test_kotlin_purpose_is_not_used_as_a_python_stream_name() -> None:
    """스트림 이름이 곧 용도 구분자다 — 어떤 전략이 Kotlin 의 구분자를 쓰면 가른
    영역이 다시 붙는다. 호출 자리에서 전수로 모은다(이름 열거가 아니다)."""
    reserved = _documented_purposes()["Kotlin"]
    streams = _stream_names()
    assert streams, "`notice_rng` 호출 자리를 하나도 찾지 못했다"
    assert reserved not in streams, (
        f"Python 스트림 이름이 Kotlin 구분자 {reserved!r} 와 같다 — 표본 추첨과 전략 "
        "난수가 같은 digest 에서 나온다"
    )
    assert len(set(streams.values())) == len(streams), (
        f"두 호출 자리가 같은 스트림 이름을 쓴다: {sorted(streams.items())}"
    )


def _strategy_input(*, seed: int) -> StrategyInput:
    """합성 행 하나로 만든 전략 입력 — 여기서 필요한 것은 공고 키 해시와 seed 뿐이다."""
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy), loaded
    rows = rows_bytes([row_payload("n-hash-purpose")])
    snapshot = load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows))
    assert isinstance(snapshot, LoadedSnapshot), snapshot
    admission = admit_rows(snapshot.rows, loaded)
    assert admission.admitted, admission.excluded
    return build_strategy_input(admission.admitted[0], (), seed=seed)


def _stream_names() -> dict[str, str]:
    """`notice_rng(..., stream=X)` 의 X 를 **모듈 상수로 해석**해 모은다.

    상수로 풀리지 않는 형태(속성·식)는 실패로 둔다 — 구분자는 사전 등록 값이라
    실행 중에 생겨서는 안 된다."""
    found: dict[str, str] = {}
    for path in sorted(_SOURCE_ROOT.rglob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        constants = _module_constants(tree)
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            if not (isinstance(node.func, ast.Name) and node.func.id == _RNG_FUNCTION):
                continue
            keyword = next(
                (item for item in node.keywords if item.arg == _STREAM_KEYWORD), None
            )
            assert keyword is not None, (
                f"{path.name}: `{_RNG_FUNCTION}` 호출에 `{_STREAM_KEYWORD}=` 가 없다"
            )
            found[f"{path.name}:{ast.unparse(keyword.value)}"] = _resolve(
                keyword.value, constants, path.name
            )
    return found


def _module_constants(tree: ast.Module) -> dict[str, str]:
    constants: dict[str, str] = {}
    for node in tree.body:
        target: ast.expr | None = None
        if isinstance(node, ast.AnnAssign):
            target = node.target
            value = node.value
        elif isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
            value = node.value
        else:
            continue
        if (
            isinstance(target, ast.Name)
            and isinstance(value, ast.Constant)
            and isinstance(value.value, str)
        ):
            constants[target.id] = value.value
    return constants


def _resolve(node: ast.expr, constants: dict[str, str], where: str) -> str:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.Name) and node.id in constants:
        return constants[node.id]
    raise AssertionError(
        f"{where}: 스트림 이름이 모듈 상수로 풀리지 않는다 "
        f"({ast.unparse(node)}) — 용도 구분자는 사전 등록 값이다"
    )
