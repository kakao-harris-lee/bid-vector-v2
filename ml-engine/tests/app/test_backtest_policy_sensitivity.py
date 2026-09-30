"""D-6G-63 — **정책 값 민감도**. 리터럴 게이트를 형태로 넓히는 일을 멈추고, 거동으로
잰다.

게이트는 r3·r4 두 번 열렸다. 파서를 늘리고 상수 접기를 더해도 「수가 아닌 상수에서
수를 짓는 길」은 남는다(`len("ghijk")` · `int.from_bytes` · f-string · `struct.unpack` ·
`eval`). 열거로 닫는 술어는 새 형태 하나마다 다시 열린다.

이 test 는 반대쪽에서 잰다: **정책 파일의 값을 바꾸면 판정이 바뀌는가.** 코드에 박힌
수가 판정에 닿으면 그 자리는 정책을 따라가지 않으므로, 값을 바꿔도 판정이 그대로여서
RED 가 된다. 수를 **어떤 형태로 숨겼든** 상관없다 — 숨긴 수가 쓰이는 순간 잡힌다.

대상 값 집합은 지어내지 않는다. 정책 파일의 키 집합과 로더가 만든 구조의 필드 집합을
각각 도출해 **등식**으로 맞댄다(`test_target_value_set_is_derived_from_the_schema`) —
정책에 값을 더하면서 이 test 를 손대지 않으면 그 자리에서 걸린다.

기존 리터럴 게이트는 **그대로 둔다**(보조층). 이 test 가 정본이다.
"""

from __future__ import annotations

import contextlib
import dataclasses
import json
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Final

import pytest

from ml_engine.app.backtest_distribution import S2_STRATEGY_NAMES
from ml_engine.app.backtest_job import build_strategies
from ml_engine.evaluation.backtest.observations import LoadedSnapshot
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
    strategy_backtest_policy_checksum,
)
from ml_engine.evaluation.backtest.records import BacktestRequest
from ml_engine.evaluation.backtest.report import canonical_verdict_bytes
from ml_engine.evaluation.backtest.run import run_strategy_backtest
from ml_engine.evaluation.backtest.snapshot import load_snapshot
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from tests.evaluation._backtest_fixture import build_rows
from tests.evaluation._backtest_support import (
    manifest_bytes,
    rows_bytes,
    sample_list_bytes,
)

_POLICY_DIR = Path(__file__).resolve().parents[2] / "policy"
_SHIPPED_BACKTEST_POLICY = _POLICY_DIR / "strategy-backtest-v1.yaml"
_SHIPPED_INFERENCE_POLICY = _POLICY_DIR / "inference-v1.yaml"

# 판정이 실제로 서려면 창 규칙을 낮춰야 한다(출하 값은 창당 483행을 요구한다).
# **이 셋은 판정이 성립하는 판을 만드는 값이고, 민감도의 대상에서 빠지지 않는다** —
# 아래 perturbation 이 이 키들도 그대로 흔든다.
_BASE_OVERRIDES: Final[dict[str, str]] = {
    "verdict.min_window_rows": "10",
    "verdict.min_window_count": "3",
    "strategy.s4_iteration_count": "100",
    "fit.min_sample_count": "20",
    "fit.max_bin_ratio_deviation": "0.20",
}

_ROW_STRIDE: Final[int] = 3
"""행을 솎는 간격. `build_rows()` 가 블록 순서대로 내므로 등간격 추출은 창 구조를
보존한다(창 셋이 유지되고 창당 행 수가 하한을 넘는다). 민감도 test 는 정책 값 하나당
판정을 한 번씩 돌리므로 행 수가 그대로면 이 test 하나가 분 단위가 된다."""


_DECISION_DRIVERS: Final[dict[str, str]] = {
    # 계약(D-6G-63)이 이름으로 든 「판정에 쓰이는 값」들과, 그 값을 **판정이 뒤집히는
    # 쪽으로** 미는 극단값. 위의 작은 흔들기는 판정문 어딘가가 움직이는지만 보는데,
    # 정책 값 다수는 판정문에 **메아리**로 실리므로(`alpha_used` 는 `alpha/3` 이라
    # 값으로 지울 수도 없다) 그것만으로는 「값이 **판정**에 닿는가」가 가려진다 —
    # alpha 를 코드 상수로 바꿔도 메아리가 움직여 초록이었다(실측).
    #
    # 그래서 이 층은 **판정 투영**(전략별 outcome·passed)만 본다. 극단으로 밀면
    # 정직한 코드에서는 판정이 반드시 뒤집히고, 그 자리가 정책을 읽지 않으면 그대로다.
    "verdict.alpha": "0.999",
    "verdict.primary_hypothesis_count": "1",
    "verdict.min_relative_improvement": "0.000001",
    "verdict.ineligibility_noninferiority_margin": "1.0",
    "verdict.min_window_count": "99",
    "verdict.min_window_rows": "99999",
}

_DECISION_FROZEN: Final[dict[str, str]] = {
    # **판정 투영을 움직이지 못하는 것이 옳은** 판정 입력들과 그 사유(D-6G-63 이
    # 요구한 등재). 이 합성 스냅숏에서는 어떤 후보도 기준선을 이기지 못한다
    # (`strategy_win_rate` 0.0 · `relative_gain` -1.0 · `p_value` 1.0). 통과 조건은
    # 여러 레그의 논리곱이라, 이미 다른 레그에서 떨어진 판정은 이 셋을 아무리 밀어도
    # 뒤집히지 않는다. 값이 판정에 **닿는다**는 것은 위 층(판정문 변화)이 잰다.
    "verdict.min_relative_improvement": (
        "상대 개선 하한 — 이 판의 개선은 -1.0 이라 하한을 0 에 붙여도 넘지 못한다"
    ),
    "verdict.primary_hypothesis_count": (
        "Bonferroni 분모 — p 가 1.0 이라 보정을 없애 alpha 를 올려도 p <= alpha 가"
        " 서지 않는다"
    ),
    "verdict.ineligibility_noninferiority_margin": (
        "비열등 여유 — 이 판의 실격률 차이가 이미 여유 안쪽이라 여유를 넓혀도 같은"
        " 쪽에 남는다"
    ),
}

_INSENSITIVE: Final[dict[str, str]] = {
    # 여기 등재된 값은 **판정 바이트를 바꾸지 않는 것이 옳다**. 사유를 함께 적는다 —
    # 사유 없는 등재는 「안 잡히는 값」을 숨기는 자리가 된다. evidence 에도 같은 표가
    # 있다. 실측으로 비었으면 비운다.
}


def _echo_forms(raw: str) -> tuple[object, ...]:
    """정책 파일의 한 값이 판정문에 실릴 수 있는 꼴들(문자열·수). 따옴표는 벗긴다."""
    bare = raw[1:-1] if raw[:1] in {'"', "'"} and raw[-1:] == raw[:1] else raw
    forms: list[object] = [bare]
    for parse in (int, float):
        with contextlib.suppress(ValueError):
            forms.append(parse(bare))
    return tuple(forms)


def _flat_policy(path: Path) -> dict[str, str]:
    """`key: value` 평면 파일을 그대로 읽는다 — 주석과 빈 줄만 버린다."""
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        key, _, value = line.partition(":")
        values[key.strip()] = value.strip()
    return values


def _schema_field_count(policy: StrategyBacktestPolicy) -> int:
    """로더가 만든 구조의 스칼라 필드 수 — 튜플은 원소 수로 센다."""
    total = 0
    for field in dataclasses.fields(policy):
        value = getattr(policy, field.name)
        if dataclasses.is_dataclass(value) and not isinstance(value, type):
            total += len(dataclasses.fields(value))
        elif isinstance(value, tuple):
            total += len(value)
        else:
            total += 1
    return total


def _write_policy(directory: Path, values: dict[str, str]) -> Path:
    lines = []
    for line in _SHIPPED_BACKTEST_POLICY.read_text(encoding="utf-8").splitlines():
        key = line.split(":", 1)[0].strip()
        lines.append(f"{key}: {values[key]}" if key in values else line)
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "strategy-backtest-sensitivity.yaml"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def _perturb(key: str, raw: str) -> str:
    """값 하나를 **정책 파일 안에서만** 흔든다. 불변식을 깨지 않는 방향으로 민다."""
    # YAML 이 따옴표를 쓰는 값이 있다(시행일·version). 벗겨서 흔들고 다시 씌운다 —
    # 따옴표째 수로 읽으려 하면 날짜가 실수 파싱으로 떨어진다.
    quote = raw[0] if raw[:1] in {'"', "'"} and raw[-1:] == raw[:1] else ""
    bare = raw[1:-1] if quote else raw

    def wrap(value: str) -> str:
        return f"{quote}{value}{quote}"

    if key == "version":
        return wrap(f"{bare}-perturbed")
    # `-` 가 있을 때만 날짜로 본다. `date.fromisoformat` 은 기본형식도 받으므로
    # `20260812`(seed)가 `2026-08-13` 으로 접혀 seed 가 날짜가 됐다 — 그러면 로더가
    # 거부하고, 거부를 「민감하다」로 세는 이 test 의 갈래 때문에 **seed 의 민감도가
    # 재지지 않은 채 초록**이 된다.
    if "-" in bare:
        try:
            moved = date.fromisoformat(bare) + timedelta(days=1)
        except ValueError:
            pass
        else:
            return wrap(moved.isoformat())
    number = float(bare)
    if "." in bare or "e" in bare.lower():
        # 비율·확률은 위로 밀면 상한(1.0)을 넘을 수 있다 — 아래로 민다.
        return wrap(repr(round(number * 0.5, 10)))
    return wrap(str(int(number) + 1))


def _small_snapshot() -> LoadedSnapshot:
    """판정이 서는 가장 작은 판 — `build_rows()` 를 등간격으로 솎는다."""
    payloads: list[dict[str, Any]] = [
        row for index, row in enumerate(build_rows()) if index % _ROW_STRIDE == 0
    ]
    rows = rows_bytes(payloads)
    listing = sample_list_bytes(rows)
    loaded = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(loaded, LoadedSnapshot), loaded
    return loaded


def _strip_echo(node: object, echoed: frozenset[str]) -> object:
    """판정문에서 **입력값의 메아리**를 지운다.

    정책 값 다수는 판정문에 그대로 실린다(`alpha_used` · 창 규칙 · 표본 예산…).
    그래서 전체 바이트를 맞대면 「값이 판정에 쓰이는가」가 아니라 「값이 실리는가」를
    재게 된다 — 실제로 alpha 를 코드 상수로 바꿔도 `alpha_used` 가 움직여 초록이었다.
    메아리를 지우면 남는 차이는 **그 값이 판정·산출을 실제로 움직인 몫**이다.

    지우는 기준은 값 자체다(이름 열거가 아니다) — 흔들기 전후의 값과 같은 잎을 뺀다."""
    if isinstance(node, dict):
        return {
            key: _strip_echo(value, echoed)
            for key, value in node.items()
            if repr(value) not in echoed
        }
    if isinstance(node, list):
        return [
            _strip_echo(value, echoed) for value in node if repr(value) not in echoed
        ]
    return node


def _verdict_bytes(
    snapshot: LoadedSnapshot,
    strategies: tuple[Any, tuple[Any, ...]],
    policy: StrategyBacktestPolicy,
) -> bytes:
    baseline, candidates = strategies
    outcome = run_strategy_backtest(
        BacktestRequest(
            snapshot=snapshot,
            baseline=baseline,
            candidates=candidates,
            primary_names=S2_STRATEGY_NAMES,
            policy=policy,
            policy_checksum=strategy_backtest_policy_checksum(policy),
        )
    )
    return canonical_verdict_bytes(outcome)


@pytest.fixture(scope="module")
def _inference() -> InferencePolicy:
    loaded = load_inference_policy(_SHIPPED_INFERENCE_POLICY)
    assert isinstance(loaded, InferencePolicy), loaded
    return loaded


def _decision_view(payload_bytes: bytes) -> object:
    """**판정만** 남긴 투영 — 전략별 결말과 통과 여부. 입력 메아리는 들어오지 않는다."""
    payload = json.loads(payload_bytes)
    if "strategies" not in payload:
        # 멈춤도 판정이다 — 「판정하지 않기로 했다」는 결말이므로 투영에 담는다.
        return ("STOPPED", payload.get("stopped"))
    return [
        (item["strategy"], item["outcome"], item["pooled"]["passed"])
        for item in payload["strategies"]
    ]


def test_named_judgement_values_move_the_decision_itself(
    tmp_path: Path, _inference: InferencePolicy
) -> None:
    """D-6G-63 — 계약이 이름으로 든 판정 입력들은 **판정 자체**를 움직인다.

    극단으로 밀면 정직한 코드에서는 결말이 뒤집힌다. 그 자리에 코드에 박힌 수가
    닿아 있으면 정책을 아무리 밀어도 결말이 그대로라 RED 다 — 숨긴 수의 **형태**와
    무관하게 잡힌다(리터럴 게이트가 형태로 못 잡는 자리를 이 층이 받는다)."""
    flat = _flat_policy(_SHIPPED_BACKTEST_POLICY)
    base_values = dict(flat)
    base_values.update(_BASE_OVERRIDES)
    snapshot = _small_snapshot()
    strategies = build_strategies(_inference)
    base_policy = load_strategy_backtest_policy(_write_policy(tmp_path, base_values))
    assert isinstance(base_policy, StrategyBacktestPolicy), base_policy
    baseline = _decision_view(_verdict_bytes(snapshot, strategies, base_policy))

    frozen: list[str] = []
    for key, extreme in sorted(_DECISION_DRIVERS.items()):
        moved = dict(base_values)
        moved[key] = extreme
        policy = load_strategy_backtest_policy(
            _write_policy(tmp_path / f"d_{key.replace('.', '_')}", moved)
        )
        assert isinstance(policy, StrategyBacktestPolicy), (
            f"{key}={extreme} 가 로더에 거부됐다: {policy}"
        )
        if _decision_view(_verdict_bytes(snapshot, strategies, policy)) == baseline:
            frozen.append(key)
    assert sorted(frozen) == sorted(_DECISION_FROZEN), (
        "정책 값을 극단으로 밀었는데 **판정이 그대로다** — 그 자리가 정책을 읽지 "
        f"않는다(코드에 박힌 수가 판정에 닿았다): {sorted(set(frozen) - set(_DECISION_FROZEN))}"
        f" · 등재됐는데 실제로는 판정을 움직이는 값: "
        f"{sorted(set(_DECISION_FROZEN) - set(frozen))}"
    )


def test_target_value_set_is_derived_from_the_schema() -> None:
    """대상 집합이 **도출**이라는 것 — 정책 파일의 키 수와 로더 구조의 필드 수가 같다.

    지어낸 목록이면 정책에 값을 더할 때 조용히 빠진다. 두 자리에서 도출해 맞대면
    한쪽만 늘어나는 순간 여기서 걸린다."""
    flat = _flat_policy(_SHIPPED_BACKTEST_POLICY)
    loaded = load_strategy_backtest_policy(_SHIPPED_BACKTEST_POLICY)
    assert isinstance(loaded, StrategyBacktestPolicy), loaded
    assert len(flat) == _schema_field_count(loaded), (
        f"정책 파일 키 {len(flat)} != 로더 구조 필드 {_schema_field_count(loaded)} — "
        "값이 한쪽에만 늘었다"
    )
    assert set(_INSENSITIVE) <= set(flat), (
        f"등재된 둔감 값이 정책에 없다: {sorted(set(_INSENSITIVE) - set(flat))}"
    )
    assert set(_DECISION_FROZEN) <= set(_DECISION_DRIVERS), (
        "판정 투영을 못 움직인다고 등재한 값이 판정 입력 목록에 없다: "
        f"{sorted(set(_DECISION_FROZEN) - set(_DECISION_DRIVERS))}"
    )
    assert set(_DECISION_DRIVERS) <= set(flat), (
        f"판정 입력으로 등재된 키가 정책에 없다: "
        f"{sorted(set(_DECISION_DRIVERS) - set(flat))}"
    )


def test_every_policy_value_changes_the_verdict(
    tmp_path: Path, _inference: InferencePolicy
) -> None:
    """**정책 값 하나를 바꾸면 판정 바이트가 바뀐다.**

    바뀌지 않는 값은 그 자리가 정책을 읽지 않는다는 뜻이다 — 코드에 박힌 수가 판정에
    닿았거나(리터럴 게이트가 형태로는 못 잡는 자리), 값이 판정에 전혀 쓰이지 않는다.
    둘 다 알아야 하므로 실패 메시지에 키를 싣고, 후자는 사유와 함께 `_INSENSITIVE` 에
    등재한다."""
    flat = _flat_policy(_SHIPPED_BACKTEST_POLICY)
    base_values = dict(flat)
    base_values.update(_BASE_OVERRIDES)

    snapshot = _small_snapshot()
    strategies = build_strategies(_inference)
    base_policy = load_strategy_backtest_policy(_write_policy(tmp_path, base_values))
    assert isinstance(base_policy, StrategyBacktestPolicy), base_policy
    baseline_bytes = _verdict_bytes(snapshot, strategies, base_policy)

    unchanged: list[str] = []
    for key in sorted(flat):
        moved = dict(base_values)
        moved[key] = _perturb(key, base_values[key])
        if moved[key] == base_values[key]:
            unchanged.append(f"{key}(흔들리지 않았다)")
            continue
        echoed = frozenset(
            repr(parsed)
            for raw in (base_values[key], moved[key])
            for parsed in _echo_forms(raw)
        )
        policy = load_strategy_backtest_policy(
            _write_policy(tmp_path / key.replace(".", "_"), moved)
        )
        # **거부를 통과로 세지 않는다.** 「로더가 거부했으니 민감하다」로 접으면 판정
        # 경로를 한 번도 돌지 않고 초록이 된다 — 실제로 그 갈래가 seed 하나를 덮어
        # 민감도가 재지지 않은 적이 있다(perturbation 이 seed 를 날짜로 접었다).
        # 불변식을 깨지 않는 방향으로 미는 것은 이 test 의 몫이다.
        assert isinstance(policy, StrategyBacktestPolicy), (
            f"{key} 를 흔든 정책이 로더에 거부됐다 — 불변식을 깨지 않는 방향으로 "
            f"밀어야 판정 경로가 실제로 돈다: {policy}"
        )
        moved_view = _strip_echo(
            json.loads(_verdict_bytes(snapshot, strategies, policy)), echoed
        )
        base_view = _strip_echo(json.loads(baseline_bytes), echoed)
        if moved_view == base_view:
            unchanged.append(key)

    assert sorted(unchanged) == sorted(_INSENSITIVE), (
        "정책 값을 바꿨는데 판정이 그대로다 — 그 자리가 정책을 읽지 않는다: "
        f"{sorted(set(unchanged) - set(_INSENSITIVE))} · "
        f"등재됐는데 실제로는 민감한 값: {sorted(set(_INSENSITIVE) - set(unchanged))}"
    )
