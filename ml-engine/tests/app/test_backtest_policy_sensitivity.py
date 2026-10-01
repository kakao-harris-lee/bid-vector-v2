"""D-6G-63 · M6/6G-2a — **정책 값 민감도**. 리터럴 게이트를 형태로 넓히는 일을 멈추고,
거동으로 잰다.

게이트는 r3·r4 두 번 열렸다. 파서를 늘리고 상수 접기를 더해도 「수가 아닌 상수에서
수를 짓는 길」은 남는다(`len("ghijk")` · `int.from_bytes` · f-string · `struct.unpack` ·
`eval`). 열거로 닫는 술어는 새 형태 하나마다 다시 열린다.

이 test 는 반대쪽에서 잰다: **정책 파일의 값을 바꾸면 판정이 바뀌는가.** 코드에 박힌
수가 판정에 닿으면 그 자리는 정책을 따라가지 않으므로, 값을 바꿔도 판정이 그대로여서
RED 가 된다. 수를 **어떤 형태로 숨겼든** 상관없다 — 숨긴 수가 쓰이는 순간 잡힌다.

## 6G-2a 가 고친 것

6G 의 이 test 는 **재지 못했다**(verifier r5 H-2). 윗층이 판정 JSON 을 통째로 비교했고 그
안의 `policy_checksum` 은 정책 파일 **전체**의 sha256 이라 어느 값을 흔들어도 바뀐다 —
「39 개 전부 민감」은 그 메아리를 센 것이었다. 그 칸을 빼면 39 중 20 이 판정문을 움직이지
않는다. 아랫층은 판정 투영을 봤지만 합성 판에서 **어떤 후보도 기준선을 이기지 못해**
통과 갈래를 한 번도 지나지 않았고, 판정 입력 셋이 「못 움직인다」로 등재돼 있었다.

세 가지가 바뀌었다.

1. **투영**(D-6G2a-1) — 비교에서 `policy_checksum`·`policy_version` 을 뺀다. 산출물에는
   남는다(`test_the_artefact_still_carries_the_reproducibility_echo`).
2. **부류**(D-6G2a-2·3) — 값마다 셋 중 **정확히 하나**다: ⓐ 판정 입력 · ⓑ 산출 입력 ·
   ⓒ 판독 밖. 세 부류의 합집합은 정책 스키마에서 도출한 키 집합과 **등식**이고, 부류마다
   단언이 다르다. ⓒ 의 값이 무언가를 움직이기 시작하면 RED 다.
3. **판**(D-6G2a-4·5) — 판정 입력 일곱마다 그 값을 흔들면 결말이 뒤집히는 **경계 위의
   판**을 둔다. 적어도 한 판은 후보가 기준선을 실제로 이겨 통과 갈래를 지난다.
   `_DECISION_FROZEN` 등재는 **없앴다** — 등재가 사각을 영구화했다.

대상 값 집합은 지어내지 않는다. 정책 파일의 키 집합과 로더가 만든 구조의 필드 집합을
각각 도출해 **등식**으로 맞댄다(`test_target_value_set_is_derived_from_the_schema`) —
정책에 값을 더하면서 이 test 를 손대지 않으면 그 자리에서 걸린다.

기존 리터럴 게이트는 **그대로 둔다**(보조층). 이 test 가 정본이다.
"""

from __future__ import annotations

import contextlib
import dataclasses
import json
import tempfile
from collections.abc import Mapping
from dataclasses import dataclass, field
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Final

import pytest

from ml_engine.app.backtest_distribution import S2_STRATEGY_NAMES
from ml_engine.app.backtest_job import (
    JobFailureReason,
    build_strategies,
    run_backtest_job,
)
from ml_engine.evaluation.backtest.exclusions import admit_rows
from ml_engine.evaluation.backtest.fit import _reference_sample, check_institutional_fit
from ml_engine.evaluation.backtest.mcnemar import DiscordantCounts
from ml_engine.evaluation.backtest.metrics import NoticeScore, StrategyScores
from ml_engine.evaluation.backtest.observations import BusinessCategory, LoadedSnapshot
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
    strategy_backtest_policy_checksum,
)
from ml_engine.evaluation.backtest.records import BacktestRequest, SampleVariant
from ml_engine.evaluation.backtest.report import canonical_verdict_bytes
from ml_engine.evaluation.backtest.run import run_strategy_backtest
from ml_engine.evaluation.backtest.snapshot import load_snapshot
from ml_engine.evaluation.backtest.strategies import (
    BidAmount,
    InstitutionalMonteCarloStrategy,
    StrategyInput,
    build_strategy_input,
)
from ml_engine.evaluation.backtest.verdict import (
    StrategyNotEvaluable,
    WindowOutcome,
    evaluate_window,
    passes_window,
    strategy_verdict,
)
from ml_engine.evaluation.policy import PolicyRejected
from ml_engine.evaluation.verdict import NotEvaluableReason
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from tests.evaluation._backtest_fixture import (
    BoardSpec,
    BoardWindow,
    build_board_rows,
    build_mixed_category_rows,
    build_rows,
)
from tests.evaluation._backtest_support import (
    PlannedBidStrategy,
    manifest_bytes,
    policy_file_keys,
    policy_use_census,
    rows_bytes,
    sample_list_bytes,
)

_POLICY_DIR = Path(__file__).resolve().parents[2] / "policy"
_SHIPPED_BACKTEST_POLICY = _POLICY_DIR / "strategy-backtest-v1.yaml"
_SHIPPED_INFERENCE_POLICY = _POLICY_DIR / "inference-v1.yaml"

# 판정이 실제로 서려면 창 규칙을 낮춰야 한다(출하 값은 창당 483행을 요구한다).
# **이 넷은 판정이 성립하는 판을 만드는 값이고, 민감도의 대상에서 빠지지 않는다** —
# 아래 흔들기가 이 키들도 그대로 민다.
#
# 창 최소 수는 **여기 없다**: 출하 값이 이미 3 이라 override 가 무효였고, 무효 override 는
# 「이 판은 그 값을 낮춰 쓴다」는 거짓 신호를 남긴다(code-review r1 L-1).
_BASE_OVERRIDES: Final[dict[str, str]] = {
    "verdict.min_window_rows": "10",
    "strategy.s4_iteration_count": "100",
    "fit.min_sample_count": "20",
    "fit.max_bin_ratio_deviation": "0.20",
}

_ROW_STRIDE: Final[int] = 3
"""행을 솎는 간격. `build_rows()` 가 블록 순서대로 내므로 등간격 추출은 창 구조를
보존한다(창 셋이 유지되고 창당 행 수가 하한을 넘는다). 민감도 test 는 정책 값 하나당
판정을 한 번씩 돌리므로 행 수가 그대로면 이 test 하나가 분 단위가 된다."""


# ── 투영 셋 ──────────────────────────────────────────────────────────────────

_REPRODUCIBILITY_ECHO_KEYS: Final[tuple[str, ...]] = (
    "policy_checksum",
    "policy_version",
)
"""**비교 투영에서만** 빼는 두 칸(D-6G2a-1). 판정 JSON 산출물에는 **남는다** — 재현성
공시다(`test_the_artefact_still_carries_the_reproducibility_echo` 가 그것을 잠근다).

`policy_checksum` 은 정책 파일 **전체**의 sha256 이라 어느 값을 흔들어도 바뀐다.
`_strip_echo` 는 흔든 값과 같은 **잎**을 지우는데 checksum 은 값의 꼴이 아니라 걸러지지
않았다 — 가장 강한 메아리를 빼먹은 것이 「39 개 전부 민감」이라는 초록의 정체다
(6G verifier r5 H-2). 이 둘을 빼면 39 중 20 이 판정문을 움직이지 않는다(착수 실측)."""


def _echo_forms(raw: str) -> tuple[object, ...]:
    """정책 파일의 한 값이 판정문에 실릴 수 있는 꼴들(문자열·수). 따옴표는 벗긴다."""
    bare = raw[1:-1] if raw[:1] in {'"', "'"} and raw[-1:] == raw[:1] else raw
    forms: list[object] = [bare]
    for parse in (int, float):
        with contextlib.suppress(ValueError):
            forms.append(parse(bare))
    return tuple(forms)


def _is_echo_leaf(
    base: object, moved: object, before: frozenset[str], after: frozenset[str]
) -> bool:
    """이 잎이 **흔든 값의 메아리**인가 — 같은 자리에서 값이 정책 값을 따라갔는가."""
    return repr(base) in before and repr(moved) in after


def _without_echo_paths(
    base: object, moved: object, before: frozenset[str], after: frozenset[str]
) -> tuple[object, object]:
    """두 판정문에서 **메아리 경로만** 지운 쌍.

    6G 에서 물려받은 `_strip_echo` 는 흔든 값과 `repr` 이 같은 **모든 잎**을 지웠다 — 이름이
    아니라 값이 기준이라, 정책 값이 작은 정수면 판정문의 무관한 칸이 함께 사라졌다
    (`stability_seeds.1` 을 1 -> 2 로 흔들면 승률 `1.0` · p 값 `1.0` 같은 **판정의 핵심
    칸**까지 빠진다). 오차 방향이 「움직이지 않았다」 쪽이어서 ⓒ 단언이 공허해질 수 있고,
    불변 목록에 삭제가 만든 항목이 섞일 수 있다(code-review r1 M-4 · verifier r1 M-5).

    그래서 **경로**로 지운다: 같은 자리에서 흔들기 전 값이 정책의 옛 값이고 흔든 뒤 값이
    정책의 새 값인 잎만 메아리다. 그 둘 중 하나라도 어긋나면 그 칸은 **그 값이 움직인 몫**이고
    투영에 남는다."""
    if isinstance(base, dict) and isinstance(moved, dict):
        pruned_base: dict[str, object] = {}
        pruned_moved: dict[str, object] = {}
        for key in sorted(set(base) | set(moved)):
            if key not in base or key not in moved:
                if key in base:
                    pruned_base[key] = base[key]
                if key in moved:
                    pruned_moved[key] = moved[key]
                continue
            if _is_echo_leaf(base[key], moved[key], before, after):
                continue
            left, right = _without_echo_paths(base[key], moved[key], before, after)
            pruned_base[key] = left
            pruned_moved[key] = right
        return pruned_base, pruned_moved
    if isinstance(base, list) and isinstance(moved, list) and len(base) == len(moved):
        left_items: list[object] = []
        right_items: list[object] = []
        for first, second in zip(base, moved, strict=True):
            if _is_echo_leaf(first, second, before, after):
                continue
            left, right = _without_echo_paths(first, second, before, after)
            left_items.append(left)
            right_items.append(right)
        return left_items, right_items
    return base, moved


def _echoed_forms(before: str, after: str) -> frozenset[str]:
    return frozenset(
        repr(parsed) for raw in (before, after) for parsed in _echo_forms(raw)
    )


def _drop_reproducibility_echo(payload_bytes: bytes) -> object:
    payload = json.loads(payload_bytes)
    if isinstance(payload, dict):
        return {
            key: value
            for key, value in payload.items()
            if key not in _REPRODUCIBILITY_ECHO_KEYS
        }
    return payload


def _document_pair(
    base_bytes: bytes, moved_bytes: bytes, before: str, after: str
) -> tuple[object, object]:
    """**판정문 투영 쌍** — 재현성 메아리 두 칸을 빼고, 흔든 값의 **메아리 경로**를 지운다.

    같은 자리에서 쟤야 메아리를 가릴 수 있으므로 투영은 한 쪽이 아니라 **쌍**으로 낸다."""
    return _without_echo_paths(
        _drop_reproducibility_echo(base_bytes),
        _drop_reproducibility_echo(moved_bytes),
        frozenset(repr(form) for form in _echo_forms(before)),
        frozenset(repr(form) for form in _echo_forms(after)),
    )


def _outcome_view(payload_bytes: bytes) -> object:
    """**결말 부류만** 남긴 투영 — 계약 D-6G2a-2 ⓐ 의 문면 그대로
    (`Passed | Failed | NotEvaluable`)와 통과 여부. 「못 쟀다」의 **사유는 담지 않는다**.

    부류 ⓐ 는 이 투영으로 재고(계약 문면), 사유 축은 `_decision_view` 가 담는다 — 사유만
    옮겨가는 값은 ⓐ 가 아니라 ⓑ 이고, 그 사유 이동은 전용 단언이 따로 잠근다(r1 cr M-5)."""
    payload = json.loads(payload_bytes)
    if "strategies" not in payload:
        return ("STOPPED", payload.get("stopped"))
    return [
        (item["strategy"], item["outcome"], (item.get("pooled") or {}).get("passed"))
        for item in payload["strategies"]
    ]


def _decision_view(payload_bytes: bytes) -> object:
    """**판정만** 남긴 투영 — 전략별 결말과 **그 사유**, 통과 여부. 메아리는 안 들어온다.

    사유를 담는 이유: 「못 쟀다」는 결말 하나가 아니라 **넷**이다(창 부족 · seed 불안정 ·
    기준선 무승 · 검정력 미달). 사유를 빼면 `SEED_UNSTABLE` 에서 `UNDERPOWERED` 로 옮겨간
    판정이 「바뀌지 않았다」로 읽혀, 그 사유를 고른 자리의 상수가 보이지 않는다 — 실측으로
    드러난 자리다(전략의 경쟁자 표본 하한을 밀면 사유만 바뀐다)."""
    payload = json.loads(payload_bytes)
    if "strategies" not in payload:
        # 멈춤도 판정이다 — 「판정하지 않기로 했다」는 결말이므로 투영에 담는다.
        return ("STOPPED", payload.get("stopped"), payload.get("detail"))
    return [
        (
            item["strategy"],
            item["outcome"],
            item.get("reason"),
            (item.get("pooled") or {}).get("passed"),
        )
        for item in payload["strategies"]
    ]


# ── 정책 파일 읽기·쓰기·흔들기 ──────────────────────────────────────────────


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
    for field_info in dataclasses.fields(policy):
        value = getattr(policy, field_info.name)
        if dataclasses.is_dataclass(value) and not isinstance(value, type):
            total += len(dataclasses.fields(value))
        elif isinstance(value, tuple):
            total += len(value)
        else:
            total += 1
    return total


def _write_policy(directory: Path, values: Mapping[str, str]) -> Path:
    lines = []
    for line in _SHIPPED_BACKTEST_POLICY.read_text(encoding="utf-8").splitlines():
        key = line.split(":", 1)[0].strip()
        lines.append(f"{key}: {values[key]}" if key in values else line)
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "strategy-backtest-sensitivity.yaml"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def _drop_key(directory: Path, values: Mapping[str, str], dropped: str) -> Path:
    """키 하나를 **지운** 정책 파일 — 로더가 fail-closed 인지 재는 입력(우회 ⑤)."""
    lines: list[str] = []
    for line in _SHIPPED_BACKTEST_POLICY.read_text(encoding="utf-8").splitlines():
        key = line.split(":", 1)[0].strip()
        if key == dropped:
            continue
        lines.append(f"{key}: {values[key]}" if key in values else line)
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "strategy-backtest-dropped.yaml"
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


# ── 판 ────────────────────────────────────────────────────────────────────────


def _snapshot_of(payloads: list[dict[str, Any]]) -> LoadedSnapshot:
    rows = rows_bytes(payloads)
    listing = sample_list_bytes(rows)
    loaded = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(loaded, LoadedSnapshot), loaded
    return loaded


def _small_snapshot() -> LoadedSnapshot:
    """판정이 서는 가장 작은 판 — `build_rows()` 를 등간격으로 솎는다."""
    return _snapshot_of(
        [row for index, row in enumerate(build_rows()) if index % _ROW_STRIDE == 0]
    )


@dataclass(frozen=True)
class _Board:
    """판 하나 — 스냅숏·전략·주 가설 이름·그 판이 쓰는 정책 override·판별 표본.

    `overrides` 는 **그 판이 서기 위한** 값이고 민감도의 대상에서 빠지지 않는다(흔들기는
    override 를 적용한 값 위에서 다시 민다)."""

    snapshot: LoadedSnapshot
    baseline: Any
    candidates: tuple[Any, ...]
    primary_names: tuple[str, ...] = ()
    overrides: Mapping[str, str] = field(default_factory=dict)
    variant: SampleVariant = SampleVariant.MAIN


_BASELINE_NAME: Final[str] = "S0-planned"
_CANDIDATE_NAME: Final[str] = "C-planned"


def _planned_board(
    spec: BoardSpec,
    *,
    primary: bool = False,
    overrides: Mapping[str, str] | None = None,
    seed_sensitive: bool = False,
    variant: SampleVariant = SampleVariant.MAIN,
) -> _Board:
    """계획 전략 둘을 올린 판 — 승패가 판에서 정해지므로 경계를 올라탈 수 있다."""
    rows = build_board_rows(spec)
    seeds = tuple(
        int(_flat_policy(_SHIPPED_BACKTEST_POLICY)[f"stability_seeds.{index}"])
        for index in range(5)
    )
    return _Board(
        snapshot=_snapshot_of(list(rows.payloads)),
        baseline=PlannedBidStrategy(_BASELINE_NAME, rows.baseline_plan),
        candidates=(
            PlannedBidStrategy(
                _CANDIDATE_NAME,
                rows.candidate_plan,
                seeds=seeds,
                seed_sensitive=seed_sensitive,
            ),
        ),
        primary_names=(_CANDIDATE_NAME,) if primary else (),
        overrides=dict(overrides or {}),
        variant=variant,
    )


def _shipped_board(snapshot: LoadedSnapshot, inference: InferencePolicy) -> _Board:
    baseline, candidates = build_strategies(inference)
    return _Board(
        snapshot=snapshot,
        baseline=baseline,
        candidates=candidates,
        primary_names=S2_STRATEGY_NAMES,
    )


def _uniform(window: BoardWindow, count: int = 3) -> tuple[BoardWindow, ...]:
    return (window,) * count


# 판 이름 -> 그 판을 짓는 함수. 이름을 쓰는 이유는 부류 표가 **판을 가리켜야** 하기
# 때문이다 — 같은 값이 판에 따라 다른 부류가 되는 일을 막는다(한 값당 판 하나).
_BOARD_BUILDERS: Final[dict[str, Any]] = {
    # 출하 전략 다섯을 그대로 돌리는 판(용역 하나). 전략 모듈에서만 읽히는 값이 여기서 돈다.
    "shipped": lambda inference: _shipped_board(_small_snapshot(), inference),
    # 업무 셋을 섞은 출하 전략 판 — 공사·물품에서만 도는 자리가 여기서 돈다.
    "mixed": lambda inference: _shipped_board(
        _snapshot_of(list(build_mixed_category_rows())), inference
    ),
    # 통과 갈래를 지나는 판(보조 가설) — 창마다 후보가 10 번 더 이긴다.
    "pass": lambda _: _planned_board(BoardSpec(_uniform(BoardWindow(10, 0, 40, 0)))),
    # 통과 갈래를 지나는 판(주 가설) — Bonferroni 분모가 걸린 유의수준에서도 검정력이 선다.
    "pass-primary": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(18, 0, 63, 0))), primary=True
    ),
    # 검정력 경계 **바로 아래** — 필요 표본이 하나 줄면 판정이 선다.
    "edge": lambda _: _planned_board(BoardSpec(_uniform(BoardWindow(7, 0, 28, 0)))),
    "edge-primary": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0))), primary=True
    ),
    # 상대 개선이 하한에 **못 미치는** 판 — 하한을 내리면 판정이 선다.
    "gain-short": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(12, 0, 70, 0)))
    ),
    # 실격률 차이가 비열등 한계를 **넘는** 판 / **안쪽인** 판.
    "margin-breach": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(13, 0, 52, 0, 1)))
    ),
    "margin-inside": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(18, 0, 82, 0, 1)))
    ),
    # 창이 둘뿐인 판 — 창 최소 수를 둘로 내리면 판정이 선다.
    "two-windows": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0), 2))
    ),
    # 창당 행이 **정확히 하한**인 판. 창 밖 행을 넉넉히 둬서 표본 하한은 묶이지 않는다 —
    # 그래서 창 하한을 올리면 **창 제외**만 발화한다(`verdict.min_window_rows` 자리 ②).
    "window-rows": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0)), history=20, pad=30),
        overrides={"verdict.min_window_rows": "50"},
    ),
    # 행 수가 **최소 필요 표본 바로 위**인 판. 창당 행은 하한보다 하나 많아서 창 제외는
    # 발화하지 않는다 — 그래서 하한을 올리면 **표본 하한**만 발화한다(자리 ①).
    "sample-floor": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 41, 0)), history=30),
        overrides={"verdict.min_window_rows": "50"},
    ),
    # seed 다섯이 전부 전략에 닿는지 재는 판 — 계획 밖 seed 에서 승패가 뒤집힌다.
    "seeded": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0))), seed_sensitive=True
    ),
    # 순공사원가선이 출하 비율에서 이미 묶이는 공사 공고를 섞은 **계획 전략** 판 —
    # 전략이 정책을 읽지 않으므로 배제 비율의 **제외 단계 읽기 하나**만 살아 있다.
    "construction-pad": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0)), construction_pad=12)
    ),
    # 예가 범위가 넓은 공고를 섞고 **판별 표본 걸러내기**로 도는 판.
    "wide-reserve": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0)), wide_reserve_pad=12),
        variant=SampleVariant.EXCLUDE_WIDE_RESERVE_RANGE,
    ),
    # 창당 행이 하한 **하나 아래**인 판 — 하한을 내리면 창이 선다(내리는 방향, D-6G2a-15).
    "window-rows-short": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 39, 0)), history=20, pad=30),
        overrides={"verdict.min_window_rows": "50"},
    ),
}

_DERIVED_BOARDS: Final[dict[str, tuple[str, dict[str, str]]]] = {
    # **막힌 기준선** 판들(D-6G2a-15) — 바탕 판의 스냅숏을 그대로 쓰고 정책 override 만
    # 다르다. 기준선이 이미 멈춤이므로 그 값을 **되돌리는 방향**으로 흔들면 판정이 선다.
    # 우회 ④(`max(정책, 상수)`·`min(정책, 상수)` 처럼 한쪽으로만 따른다)는 **반대 방향**이
    # 있어야 잡힌다 — r0 은 판정식의 일곱만 양방향이었다(verifier r1 M-3).
    #
    # 스냅숏을 다시 짓지 않으므로 판 하나당 비용은 판정 두 번이다.
    "budget-tight": ("shipped", {"sampling.max_total_calls": "700"}),
    "fit-loose": ("shipped", {"fit.alpha": "0.95"}),
    "fit-starved": ("shipped", {"fit.min_sample_count": "9999"}),
    "fit-narrow": ("shipped", {"fit.max_bin_ratio_deviation": "0.000001"}),
    "band-low-raised": ("shipped", {"floor.rate_band_low": "0.9"}),
    "band-high-lowered": ("shipped", {"floor.rate_band_high": "0.4975"}),
    "ordinal-off": ("shipped", {"exclusion.first_notice_ordinal": "1"}),
    "reserve-count-off": ("shipped", {"institution.reserve_price_count": "16"}),
    "draw-count-off": ("shipped", {"institution.draw_count": "5"}),
    "service-late": ("shipped", {"effective.service": '"2026-07-01"'}),
    "mixed-late": (
        "mixed",
        {"effective.construction": '"2026-07-01"', "effective.goods": '"2026-07-01"'},
    ),
    "embargo-wide": ("pass", {"window.embargo_days": "60"}),
    "window-span-wide": ("shipped", {"window.days": "8"}),
    "s4-starved": ("shipped", {"strategy.s4_min_competitor_samples": "100000"}),
    "s4-grid-off": ("shipped", {"strategy.s4_grid_size": "42"}),
}


# ── 부류 셋 (D-6G2a-2) ───────────────────────────────────────────────────────


_ALL_BOARDS: Final[frozenset[str]] = frozenset(_BOARD_BUILDERS) | frozenset(
    _DERIVED_BOARDS
)


@dataclass(frozen=True)
class _Probe:
    """값 하나를 어느 판에서 어떤 값으로 흔드는가."""

    board: str
    value: str


_STRONG_VALUES: Final[dict[str, tuple[str, ...]]] = {
    "version": ("strategy-backtest-v1-perturbed",),
    "verdict.min_relative_improvement": ("0.30", "0.15"),
    "verdict.alpha": ("0.005", "0.10"),
    "verdict.primary_hypothesis_count": ("4", "1"),
    "verdict.ineligibility_noninferiority_margin": ("0.02", "0.005"),
    "verdict.target_power": ("0.95", "0.50"),
    "verdict.min_window_count": ("4", "2"),
    "verdict.min_window_rows": ("51", "49"),
    "window.days": ("8", "7"),
    "window.embargo_days": ("60", "7"),
    "institution.reserve_price_count": ("16", "15"),
    "institution.draw_count": ("5", "4"),
    "floor.rate_band_low": ("0.9", "0.30"),
    "floor.rate_band_high": ("0.4975", "0.995"),
    "floor.pure_construction_cost_ratio": ("0.90",),
    # 시행일은 YAML 이 따옴표를 쓰는 값이다 — 따옴표째 적는다(흔들기가 파일에 그대로 쓰인다).
    "effective.construction": ('"2026-07-01"', '"2026-01-30"'),
    "effective.service": ('"2026-07-01"', '"2026-05-26"'),
    "effective.goods": ('"2026-07-01"', '"2026-05-29"'),
    "exclusion.first_notice_ordinal": ("1", "0"),
    "strategy.s1_offset_bp": ("900.0",),
    "strategy.s4_iteration_count": ("101",),
    "strategy.s4_grid_size": ("42", "41"),
    "strategy.s4_grid_span_bp": ("200.0",),
    "strategy.s4_min_competitor_samples": ("100000", "30"),
    "sampling.list_call_count": ("601",),
    "sampling.calls_per_notice_construction": ("5",),
    "sampling.calls_per_notice_service": ("4",),
    "sampling.calls_per_notice_goods": ("4",),
    "sampling.max_total_calls": ("100", "80000"),
    "sampling.headroom_ratio": ("0.1",),
    "sensitivity.wide_reserve_half_width": ("0.035",),
    "fit.alpha": ("0.999", "0.05"),
    "fit.min_sample_count": ("9999", "20"),
    "fit.max_bin_ratio_deviation": ("0.000001", "0.20"),
    "stability_seeds.0": ("999001",),
    "stability_seeds.1": ("999002",),
    "stability_seeds.2": ("999003",),
    "stability_seeds.3": ("999004",),
    "stability_seeds.4": ("999005",),
}
"""정책 값마다 **흔들기의 세기** — 부류와 **독립인** 단일 출처다(verifier r1 M-1 처방).

r0 에서 ⓒ 단언은 출하 판 하나에서 `_perturb` 의 작은 흔들기만 했다. 그래서 출하 판을
움직이지 않는 19 값은 ⓐ 에서 ⓒ 로 옮겨도 초록이었다 — 우회 ⑥(값을 ⓒ 로 옮겨 단언을
피한다) 방어가 그만큼 비어 있었다. 세기를 부류 표에서 떼어 두면 값을 다른 부류로 옮겨도
**흔들기는 그대로**이고, ⓒ 단언이 모든 판에서 그 세기로 돈다.

부류 표의 흔들기 값은 이 표의 부분집합이어야 한다 — 등식으로 잠근다."""


_JUDGEMENT_INPUTS: Final[dict[str, tuple[_Probe, ...]]] = {
    # ⓐ **판정 입력** — 값을 흔들면 어느 판에서든 전략별 결말이 바뀐다. 판정식의 일곱은
    # **양쪽 방향**을 둔다(우회 ④ `max(policy.x, 상수)` — 한쪽만 따르는 코드를 잡는다).
    "verdict.alpha": (_Probe("pass", "0.005"), _Probe("edge", "0.10")),
    "verdict.primary_hypothesis_count": (
        _Probe("pass-primary", "4"),
        _Probe("edge-primary", "1"),
    ),
    "verdict.min_relative_improvement": (
        _Probe("pass", "0.30"),
        _Probe("gain-short", "0.15"),
    ),
    "verdict.ineligibility_noninferiority_margin": (
        _Probe("margin-breach", "0.02"),
        _Probe("margin-inside", "0.005"),
    ),
    "verdict.target_power": (_Probe("pass", "0.95"), _Probe("edge", "0.50")),
    "verdict.min_window_count": (_Probe("pass", "4"), _Probe("two-windows", "2")),
    "verdict.min_window_rows": (
        _Probe("window-rows", "51"),
        _Probe("sample-floor", "51"),
        _Probe("window-rows-short", "49"),
    ),
    # 창 규칙 — 창 폭이 바뀌면 창 경계가 바뀌고, embargo 를 넓히면 이력이 끊긴다.
    "window.days": (_Probe("shipped", "8"), _Probe("window-span-wide", "7")),
    "window.embargo_days": (_Probe("pass", "60"), _Probe("embargo-wide", "7")),
    # 제도 상수 — 예비가격 수·추첨 수가 제외 ⑮ 와 적합도와 S4 의 분포에 동시에 닿는다.
    "institution.reserve_price_count": (
        _Probe("shipped", "16"),
        _Probe("reserve-count-off", "15"),
    ),
    "institution.draw_count": (
        _Probe("shipped", "5"),
        _Probe("draw-count-off", "4"),
    ),
    # 하한율 개연 밴드 — 밴드 밖이면 공고가 제외된다.
    "floor.rate_band_low": (
        _Probe("shipped", "0.9"),
        _Probe("band-low-raised", "0.30"),
    ),
    "floor.rate_band_high": (
        _Probe("shipped", "0.4975"),
        _Probe("band-high-lowered", "0.995"),
    ),
    # 제외 규칙 상수 — 첫 공고 차수가 어긋나면 전량이 ③ 으로 빠진다.
    "exclusion.first_notice_ordinal": (
        _Probe("shipped", "1"),
        _Probe("ordinal-off", "0"),
    ),
    # 시행일 셋 — 업무마다 다른 자리에서 읽힌다(`rules.effective_date_for`).
    "effective.service": (
        _Probe("shipped", '"2026-07-01"'),
        _Probe("service-late", '"2026-05-26"'),
    ),
    "effective.construction": (
        _Probe("mixed", '"2026-07-01"'),
        _Probe("mixed-late", '"2026-01-30"'),
    ),
    "effective.goods": (
        _Probe("mixed", '"2026-07-01"'),
        _Probe("mixed-late", '"2026-05-29"'),
    ),
    # P-4 적합도 — 어긋나면 판정 대신 멈춤이다.
    "fit.alpha": (_Probe("shipped", "0.999"), _Probe("fit-loose", "0.05")),
    "fit.min_sample_count": (
        _Probe("shipped", "9999"),
        _Probe("fit-starved", "20"),
    ),
    "fit.max_bin_ratio_deviation": (
        _Probe("shipped", "0.000001"),
        _Probe("fit-narrow", "0.20"),
    ),
    # 표본 예산 상한 — 넘으면 판정 대신 멈춤이다.
    "sampling.max_total_calls": (
        _Probe("shipped", "100"),
        _Probe("budget-tight", "80000"),
    ),
    # 전략 상수 중 **결말 부류를 뒤집는** 하나 — 격자 수가 S4 의 투찰금액을 바꾼다.
    "strategy.s4_grid_size": (_Probe("shipped", "42"), _Probe("s4-grid-off", "41")),
    # seed 다섯 — **전부** 전략에 닿아야 seed 안정성 판정이 성립한다.
    "stability_seeds.0": (_Probe("seeded", "999001"),),
    "stability_seeds.1": (_Probe("seeded", "999002"),),
    "stability_seeds.2": (_Probe("seeded", "999003"),),
    "stability_seeds.3": (_Probe("seeded", "999004"),),
    "stability_seeds.4": (_Probe("seeded", "999005"),),
}

_REASON_MOVERS: Final[dict[str, _Probe]] = {
    # 흔들면 **「못 쟀다」의 사유**가 옮겨가지만 결말 부류는 그대로인 값들(code-review r1 M-5).
    # 부류는 ⓑ 다(계약 D-6G2a-2 ⓐ 의 문면이 「결말」이고, 사유는 결말 부류가 아니다). 그래도
    # 사유 축을 버리지 않는다 — 「못 쟀다」는 결말 하나가 아니라 넷이고(창 부족 · seed 불안정 ·
    # 기준선 무승 · 검정력 미달), 사유를 고르는 자리도 정책을 따라야 한다. 전용 단언이 그것을
    # 잠근다.
    "strategy.s4_min_competitor_samples": _Probe("shipped", "100000"),
    "strategy.s4_iteration_count": _Probe("shipped", "101"),
}


_NO_OPPOSITE_DIRECTION: Final[dict[str, str]] = {
    # ⓐ 값 중 **반대 방향 흔들기를 둘 수 없는** 값과 그 사유(D-6G2a-15). 우회 ④
    # (`max(정책, 상수)`·`min(정책, 상수)` 처럼 한쪽으로만 따른다)는 값이 **문턱과 비교되는**
    # 자리에서만 성립한다 — 아래 값들은 비교 대상이 아니라 난수의 재료이거나 반복 수라
    # 상수 바닥·천장이라는 형태 자체가 없다.
    **{
        f"stability_seeds.{index}": (
            "난수 seed 다 — 크기 비교가 없으므로 한쪽 클램프라는 형태가 없다. seed 가"
            " **전부** 전략에 닿는지는 seed 민감 판이, 첫 seed 가 적합도에 닿는지는"
            " 적합도 칸 probe 가 잡는다"
        )
        for index in range(5)
    },
}


_OUTPUT_INPUTS: Final[dict[str, _Probe]] = {
    # ⓑ **산출 입력** — 결말은 그대로이고 판정문의 **비메아리** 칸이 바뀐다. 흔든 값
    # 자체와 같은 잎은 투영이 지우므로, 남는 차이는 그 값에서 **파생된** 수다.
    "sampling.list_call_count": _Probe("shipped", "601"),
    "sampling.calls_per_notice_service": _Probe("shipped", "4"),
    "sampling.calls_per_notice_construction": _Probe("mixed", "5"),
    "sampling.calls_per_notice_goods": _Probe("mixed", "4"),
    "sampling.headroom_ratio": _Probe("shipped", "0.1"),
    "strategy.s4_grid_span_bp": _Probe("shipped", "200.0"),
    # S4 의 기권 문턱과 몬테카를로 반복 수 — 결말 부류는 그대로이고 사유·승률 같은 산출 칸이
    # 움직인다. 사유 이동은 `_REASON_MOVERS` 의 전용 단언이 따로 잠근다.
    "strategy.s4_min_competitor_samples": _Probe("shipped", "100000"),
    "strategy.s4_iteration_count": _Probe("shipped", "101"),
    # S1 의 공사 전용 offset — 투찰금액과 적격 여부를 바꾸지만, 이 판에서 S1 의 결말은
    # 양쪽 모두 「못 이겼다」다(바뀌는 것은 승률·bp 사분위 같은 산출 칸이다).
    "strategy.s1_offset_bp": _Probe("mixed", "900.0"),
    # 공사의 **두 번째 실격선** 비율 — 내리면 순공사원가선이 적격 최저 투찰금액 아래로
    # 내려와 제외가 풀린다. 판은 **계획 전략** 판이다: 출하 전략 판에서는 S4 가 같은 값을
    # 따로 읽어서, 제외 단계에 상수를 박아도 S4 쪽이 움직여 초록이 된다(변이 P4 로 실측).
    "floor.pure_construction_cost_ratio": _Probe("construction-pad", "0.90"),
    # 판별 표본 걸러내기의 반폭 — 넓은 범위(±3%) 공고가 남거나 빠진다. 이 판의 넓은 공고는
    # 채점 창 **밖**(이력 블록)에 있어 결말에 닿지 않고, 판별 기록의 제거 수가 바뀐다.
    "sensitivity.wide_reserve_half_width": _Probe("wide-reserve", "0.035"),
}

_UNREAD: Final[dict[str, str]] = {
    # ⓒ **판독 밖** — 이 Python 판정이 그 값으로 **아무것도 하지 않는다**. 사유를 적는다;
    # 사유 없는 등재는 「안 잡히는 값」을 숨기는 자리가 된다.
    #
    # 착수 실측(AST 전수)에서 판정 경로의 읽는 자리가 **0 인 값은 없었다**. 그래서 이
    # 부류에 남는 것은 「읽지만 판정·산출을 만들지 않는」 한 칸뿐이다.
    "version": (
        "판정식·산출식이 쓰지 않는다 — 읽는 자리는 판정문의 공시 칸 `policy_version`"
        " 하나이고, 그 칸은 D-6G2a-1 이 비교 투영에서 뺀 재현성 메아리다. 그 칸이"
        " 산출물에 **남아 있다**는 것은"
        " `test_the_artefact_still_carries_the_reproducibility_echo` 가 따로 잠근다"
    ),
}


# ── 다중 자리 (D-6G2a-6) ─────────────────────────────────────────────────────

_SITE_ASSERTED: Final[frozenset[str]] = frozenset(
    {
        "verdict.alpha",
        "verdict.primary_hypothesis_count",
        "verdict.min_relative_improvement",
        "verdict.min_window_count",
        "verdict.min_window_rows",
        "institution.reserve_price_count",
        "institution.draw_count",
        "floor.pure_construction_cost_ratio",
    }
)
"""자리마다 **따로** 단언하는 값들 — 한 자리만 상수가 되면 다른 자리가 움직여 「바뀌었다」가
되기 때문이다. r1 이 그 위험을 두 번 실증했다: 배제 비율을 제외 단계에서만 상수로 바꾼
변이(P4)와 예비가격 수를 적합도 기준 표본에서만 상수로 바꾼 변이(P9)가 초록이었다."""

_SITE_NOT_ASSERTED: Final[dict[str, str]] = {
    # 쓰임이 둘 이상인데 자리별로 **따로** 단언하지 않는 값과 그 사유. 자리의 전수는 생성된
    # 쓰임 명단이 내고(`_USE_COVERAGE`), 이 표와 `_SITE_ASSERTED` 의 합이 그 명단의 다중 자리
    # 값 집합과 **등식**이다 — 손으로 적은 전수 목록을 두지 않는다(code-review r1 M-1).
    "floor.rate_band_low": (
        "둘째 자리는 밴드 술어를 **위임**하는 자리다(제외 규칙이 정책 객체의 술어를 부른다)"
        " — 술어 자체가 첫 자리이고 거기서 비교가 난다"
    ),
    "floor.rate_band_high": "같은 이유 — 술어 위임이다",
    "sampling.calls_per_notice_construction": (
        "둘째 자리는 업무 이름을 호출 수로 바꾸는 **표 조회**이고, 첫 자리가 그 결과를 합산한다"
        " — 둘이 한 사슬이라 표를 상수로 바꾸면 합계가 따라 움직인다"
    ),
    "sampling.calls_per_notice_service": "같은 이유 — 표 조회와 합산이 한 사슬이다",
    "sampling.calls_per_notice_goods": "같은 이유 — 표 조회와 합산이 한 사슬이다",
    "sampling.headroom_ratio": (
        "둘째 자리는 최소 필요 표본의 **곱셈**이고 첫 자리가 그 결과를 비교한다 — 한 사슬이다"
    ),
    **{
        f"stability_seeds.{index}": (
            "둘째 자리는 판정문의 seed 목록 **공시**다(메아리) — 비교 투영이 지우고, 산출물에"
            " 남는 것은 재현성 공시 test 가 본다"
        )
        for index in range(5)
    },
}


def test_multi_site_values_are_derived_and_each_has_a_disposition() -> None:
    """D-6G2a-6·12 · code-review r1 M-1 — 다중 자리 값의 전수는 **생성**이다.

    r0 은 다중 자리 값을 손으로 적었고 그 표가 이미 틀렸다(창 최소 수 2 vs 실제 3 · 상대
    개선 하한 누락 · 예비가격 수 3 과 4 가 같은 문서에서 충돌 · 첫 seed 누락). 「다중 자리
    값이 표에 다 있는가」를 아무 test 도 보지 않았기 때문이다.

    이제 전수는 쓰임 명단에서 나오고, 값마다 **자리별 단언** 또는 **사유** 중 하나를 등식으로
    요구한다."""
    policy = load_strategy_backtest_policy(_SHIPPED_BACKTEST_POLICY)
    assert isinstance(policy, StrategyBacktestPolicy), policy
    sites: dict[str, set[str]] = {}
    for use in policy_use_census(policy):
        sites.setdefault(use.key, set()).add(use.site)
    multi = {key for key, found in sites.items() if len(found) > 1}
    overlap = _SITE_ASSERTED & set(_SITE_NOT_ASSERTED)
    assert not overlap, f"처분이 양쪽에 등재됐다: {sorted(overlap)}"
    assert _SITE_ASSERTED | set(_SITE_NOT_ASSERTED) == multi, (
        f"처분이 없는 다중 자리 값: {sorted(multi - _SITE_ASSERTED - set(_SITE_NOT_ASSERTED))} · "
        f"다중 자리가 아닌데 등재된 값: "
        f"{sorted((_SITE_ASSERTED | set(_SITE_NOT_ASSERTED)) - multi)}"
    )
    for key, reason in _SITE_NOT_ASSERTED.items():
        assert reason.strip(), f"{key} 의 사유가 비어 있다"


# ── 판·정책 캐시 ─────────────────────────────────────────────────────────────


@pytest.fixture(scope="module")
def _inference() -> InferencePolicy:
    loaded = load_inference_policy(_SHIPPED_INFERENCE_POLICY)
    assert isinstance(loaded, InferencePolicy), loaded
    return loaded


@dataclass
class _Harness:
    """판·정책·판정 바이트를 **한 번만** 짓고 재사용한다 — 판 하나를 짓는 데 스냅숏 판독과
    적합도 기준 표본이 들어가고, 같은 (판, 정책) 쌍이 여러 단언에서 쓰인다."""

    inference: InferencePolicy
    directory: Path
    boards: dict[str, _Board] = field(default_factory=dict)
    verdicts: dict[tuple[str, str], bytes] = field(default_factory=dict)

    def board(self, name: str) -> _Board:
        if name in self.boards:
            return self.boards[name]
        if name in _DERIVED_BOARDS:
            base_name, overrides = _DERIVED_BOARDS[name]
            base = self.board(base_name)
            # 스냅숏·전략을 **그대로 공유**한다 — 막힌 기준선 판은 정책만 다르다.
            self.boards[name] = dataclasses.replace(
                base, overrides={**base.overrides, **overrides}
            )
            return self.boards[name]
        self.boards[name] = _BOARD_BUILDERS[name](self.inference)
        return self.boards[name]

    def values(self, name: str, **changes: str) -> dict[str, str]:
        values = _flat_policy(_SHIPPED_BACKTEST_POLICY)
        values.update(_BASE_OVERRIDES)
        values.update(self.board(name).overrides)
        values.update(changes)
        return values

    def policy(self, name: str, slug: str, **changes: str) -> StrategyBacktestPolicy:
        """그 판의 override 위에 `changes` 를 얹은 정책 — 자리별 직접 호출이 쓴다."""
        loaded = load_strategy_backtest_policy(
            _write_policy(self.directory / slug, self.values(name, **changes))
        )
        assert isinstance(loaded, StrategyBacktestPolicy), (slug, loaded)
        return loaded

    def verdict(self, name: str, **changes: str) -> bytes:
        signature = json.dumps(changes, sort_keys=True)
        if (name, signature) in self.verdicts:
            return self.verdicts[(name, signature)]
        board = self.board(name)
        values = self.values(name, **changes)
        slug = f"{name}-{abs(hash(signature)):x}"
        policy = load_strategy_backtest_policy(
            _write_policy(self.directory / slug, values)
        )
        assert isinstance(policy, StrategyBacktestPolicy), (
            f"판 {name} · 변경 {changes} 이 로더에 거부됐다 — 불변식을 깨지 않는 방향으로 "
            f"밀어야 판정 경로가 실제로 돈다: {policy}"
        )
        outcome = run_strategy_backtest(
            BacktestRequest(
                snapshot=board.snapshot,
                baseline=board.baseline,
                candidates=board.candidates,
                primary_names=board.primary_names,
                policy=policy,
                policy_checksum=strategy_backtest_policy_checksum(policy),
                variant=board.variant,
            )
        )
        self.verdicts[(name, signature)] = canonical_verdict_bytes(outcome)
        return self.verdicts[(name, signature)]


@pytest.fixture(scope="module")
def harness(_inference: InferencePolicy) -> _Harness:
    return _Harness(_inference, Path(tempfile.mkdtemp(prefix="6g2a-sensitivity-")))


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


# ── 단언 ──────────────────────────────────────────────────────────────────────


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


def test_every_policy_value_is_classified_exactly_once() -> None:
    """D-6G2a-2 — 세 부류의 합집합이 정책 키 집합과 **등식**이고, 교집합은 비어 있다.

    「39개 전부」라는 한 문장이 20 개의 사각을 가렸다. 부류를 나누면 사각이 **목록**으로
    드러나고, 정책에 값을 더하면서 이 표를 손대지 않으면 그 자리에서 걸린다."""
    keys = set(_flat_policy(_SHIPPED_BACKTEST_POLICY))
    classes = {
        "판정 입력": set(_JUDGEMENT_INPUTS),
        "산출 입력": set(_OUTPUT_INPUTS),
        "판독 밖": set(_UNREAD),
    }
    union: set[str] = set()
    for name, members in classes.items():
        overlap = union & members
        assert not overlap, f"{name} 이 다른 부류와 겹친다: {sorted(overlap)}"
        union |= members
    assert union == keys, (
        f"부류에 없는 정책 값: {sorted(keys - union)} · "
        f"정책에 없는 등재: {sorted(union - keys)}"
    )
    for key, probes in _JUDGEMENT_INPUTS.items():
        assert probes, f"{key} 의 흔들기가 비어 있다"
        for probe in probes:
            assert probe.board in _ALL_BOARDS, f"{key}: 모르는 판 {probe.board}"
    for key, probe in _OUTPUT_INPUTS.items():
        assert probe.board in _ALL_BOARDS, f"{key}: 모르는 판 {probe.board}"
    for key, reason in _UNREAD.items():
        assert reason.strip(), f"{key} 의 사유가 비어 있다"
    assert set(_STRONG_VALUES) == keys, (
        f"흔들기 세기 표가 정책 키와 어긋난다: 빠진 키 {sorted(keys - set(_STRONG_VALUES))} · "
        f"정책에 없는 키 {sorted(set(_STRONG_VALUES) - keys)}"
    )
    for key, probes in _JUDGEMENT_INPUTS.items():
        for probe in probes:
            assert probe.value in _STRONG_VALUES[key], (
                f"{key} 의 ⓐ 흔들기 {probe.value} 가 세기 표에 없다 — 부류를 옮기면 세기가 "
                "따라가야 한다"
            )
    for key, probe in _OUTPUT_INPUTS.items():
        assert probe.value in _STRONG_VALUES[key], (
            f"{key} 의 ⓑ 흔들기 {probe.value} 가 세기 표에 없다"
        )
    # **우회 ④ 는 양쪽 방향으로만 닫힌다**(D-6G2a-15) — ⓐ 값마다 반대 방향 흔들기가 있거나,
    # 없는 사유가 등재돼 있어야 한다. 등식이라 둘 다 빠뜨릴 수 없다.
    one_way = {key for key, probes in _JUDGEMENT_INPUTS.items() if len(probes) < 2}
    assert one_way == set(_NO_OPPOSITE_DIRECTION), (
        f"반대 방향도 사유도 없는 ⓐ 값: {sorted(one_way - set(_NO_OPPOSITE_DIRECTION))} · "
        f"반대 방향이 있는데 사유가 등재된 값: "
        f"{sorted(set(_NO_OPPOSITE_DIRECTION) - one_way)}"
    )
    for key, reason in _NO_OPPOSITE_DIRECTION.items():
        assert reason.strip(), f"{key} 의 사유가 비어 있다"
    assert set(_REASON_MOVERS) <= set(_OUTPUT_INPUTS), (
        "사유만 움직이는 값은 ⓑ 로 등재해야 한다: "
        f"{sorted(set(_REASON_MOVERS) - set(_OUTPUT_INPUTS))}"
    )
    for key, probe in _REASON_MOVERS.items():
        assert probe.value in _STRONG_VALUES[key], key


# ── 쓰임 명단의 덮개 등재 (D-6G2a-12) ────────────────────────────────────────
# 명단은 **생성**된다(`_backtest_support.policy_use_census`). 아래 표는 그 삼중마다
# 「무엇이 이 자리를 덮는가」를 선언하는 자리이고, 등식 test 가 생성 결과와 맞댄다 —
# 새 쓰임이 생기면 덮개 등재가 없어 RED 다. r1 의 막는 결함이 바로 **손으로 쓴 명단**이었다.
#
# 덮개 어휘(이 다섯 뿐이고 test 가 어휘를 검사한다):
#   `PROBE:<이름>` — 그 자리를 **직접** 겨눈 단언이 있다(함수 직접 호출이거나 그 자리만
#                   묶이는 판).
#   `CLASS`       — 그 키의 부류 단언(ⓐ/ⓑ)이 그 자리를 덮는다. **키마다 한 자리까지만**
#                   허용한다 — 두 자리를 부류 단언 하나로 덮으면 한 자리의 상수가 다른
#                   자리의 움직임에 가린다(r1 의 결함 그 자체).
#   `PASS:<이름>` — 정책 값을 **그대로 넘기는** 자리(helper 의 `return`·표 조회·술어 위임).
#                   판정은 그 아래에서 나고, 그 아래 자리가 따로 등재돼 있다.
#   `ECHO`        — 판정문에 **공시만** 한다. 비교 투영이 지우고, 산출물에 남는 것은 전용
#                   test 가 값까지 단언한다.
_USE_COVERAGE: Final[dict[tuple[str, str, str], str]] = {
    ("effective.construction", "rules.effective_date_for", "return"): "CLASS",
    ("effective.goods", "rules.effective_date_for", "return"): "CLASS",
    ("effective.service", "rules.effective_date_for", "return"): "CLASS",
    ("exclusion.first_notice_ordinal", "rules._is_rebid", "compare"): "CLASS",
    ("fit.alpha", "fit._fit_rejection", "compare"): "CLASS",
    ("fit.max_bin_ratio_deviation", "fit._fit_rejection", "compare"): "CLASS",
    ("fit.min_sample_count", "fit.check_institutional_fit", "compare"): "CLASS",
    (
        "floor.pure_construction_cost_ratio",
        "rules.pure_cost_floor",
        "pure_construction_floor(ratio)",
    ): "PROBE:construction-pad",
    (
        "floor.pure_construction_cost_ratio",
        "strategies._simulated_floors",
        "assign",
    ): "PROBE:s4-bid",
    ("floor.rate_band_high", "policy_values.contains", "compare"): "CLASS",
    (
        "floor.rate_band_high",
        "rules._is_floor_rate_unusable",
        "contains",
    ): "PASS:contains",
    ("floor.rate_band_low", "policy_values.contains", "compare"): "CLASS",
    (
        "floor.rate_band_low",
        "rules._is_floor_rate_unusable",
        "contains",
    ): "PASS:contains",
    (
        "institution.draw_count",
        "fit._reference_sample",
        "sample_assessment_ratios(draw_count)",
    ): "PROBE:fit-direct",
    (
        "institution.draw_count",
        "rules._is_reserve_draw_incomplete",
        "compare",
    ): "PROBE:admit-rows",
    (
        "institution.draw_count",
        "strategies._win_probabilities",
        "sample_assessment_ratios(draw_count)",
    ): "PROBE:s4-bid",
    (
        "institution.reserve_price_count",
        "fit._reference_sample",
        "sample_assessment_ratios(reserve_price_count)",
    ): "PROBE:fit-direct",
    (
        "institution.reserve_price_count",
        "fit.check_institutional_fit",
        "bin_proportions(bin_count)",
    ): "PROBE:fit-bin-count",
    (
        "institution.reserve_price_count",
        "rules._is_reserve_draw_incomplete",
        "compare",
    ): "PROBE:admit-rows",
    (
        "institution.reserve_price_count",
        "strategies._win_probabilities",
        "sample_assessment_ratios(reserve_price_count)",
    ): "PROBE:s4-bid",
    (
        "sampling.calls_per_notice_construction",
        "policy_values.calls_per_notice_for",
        "dict",
    ): "PASS:calls_per_notice_for",
    (
        "sampling.calls_per_notice_construction",
        "run._sampling_record",
        "calls_per_notice_for",
    ): "CLASS",
    (
        "sampling.calls_per_notice_goods",
        "policy_values.calls_per_notice_for",
        "dict",
    ): "PASS:calls_per_notice_for",
    (
        "sampling.calls_per_notice_goods",
        "run._sampling_record",
        "calls_per_notice_for",
    ): "CLASS",
    (
        "sampling.calls_per_notice_service",
        "policy_values.calls_per_notice_for",
        "dict",
    ): "PASS:calls_per_notice_for",
    (
        "sampling.calls_per_notice_service",
        "run._sampling_record",
        "calls_per_notice_for",
    ): "CLASS",
    (
        "sampling.headroom_ratio",
        "policy_values.minimum_required_sample",
        "ceil",
    ): "PASS:minimum_required_sample",
    (
        "sampling.headroom_ratio",
        "run._sampling_record",
        "minimum_required_sample",
    ): "CLASS",
    (
        "sampling.list_call_count",
        "run._sampling_record",
        "SamplingRecord(list_call_count)",
    ): "ECHO",
    ("sampling.list_call_count", "run._sampling_record", "assign"): "CLASS",
    (
        "sampling.max_total_calls",
        "run._sampling_record",
        "SamplingRecord(max_total_calls)",
    ): "ECHO",
    ("sampling.max_total_calls", "run._sampling_record", "compare"): "CLASS",
    ("sensitivity.wide_reserve_half_width", "run._variant_record", "compare"): "CLASS",
    ("stability_seeds.0", "run._assemble_verdict", "BacktestVerdict(seeds)"): "ECHO",
    (
        "stability_seeds.0",
        "run.run_strategy_backtest",
        "check_institutional_fit(seed)",
    ): "PROBE:fit-seed",
    ("stability_seeds.0", "run.run_strategy_backtest", "listcomp"): "CLASS",
    ("stability_seeds.1", "run._assemble_verdict", "BacktestVerdict(seeds)"): "ECHO",
    ("stability_seeds.1", "run.run_strategy_backtest", "listcomp"): "CLASS",
    ("stability_seeds.2", "run._assemble_verdict", "BacktestVerdict(seeds)"): "ECHO",
    ("stability_seeds.2", "run.run_strategy_backtest", "listcomp"): "CLASS",
    ("stability_seeds.3", "run._assemble_verdict", "BacktestVerdict(seeds)"): "ECHO",
    ("stability_seeds.3", "run.run_strategy_backtest", "listcomp"): "CLASS",
    ("stability_seeds.4", "run._assemble_verdict", "BacktestVerdict(seeds)"): "ECHO",
    ("stability_seeds.4", "run.run_strategy_backtest", "listcomp"): "CLASS",
    ("strategy.s1_offset_bp", "strategies.bid", "rate_from_basis_points"): "CLASS",
    ("strategy.s4_grid_size", "strategies._candidate_rates", "linspace"): "CLASS",
    (
        "strategy.s4_grid_span_bp",
        "strategies._candidate_rates",
        "rate_from_basis_points",
    ): "CLASS",
    (
        "strategy.s4_iteration_count",
        "strategies._win_probabilities",
        "choice(size)",
    ): "CLASS",
    ("strategy.s4_iteration_count", "strategies._win_probabilities", "full"): "CLASS",
    (
        "strategy.s4_iteration_count",
        "strategies._win_probabilities",
        "sample_assessment_ratios(count)",
    ): "CLASS",
    ("strategy.s4_min_competitor_samples", "strategies.bid", "compare"): "CLASS",
    ("verdict.alpha", "policy_values.alpha_for", "return"): "PASS:alpha_for",
    ("verdict.alpha", "policy_values.primary_alpha", "return"): "PASS:primary_alpha",
    ("verdict.alpha", "verdict.evaluate_window", "alpha_for"): "CLASS",
    ("verdict.alpha", "verdict.passes_window", "alpha_for"): "PROBE:passes_window",
    (
        "verdict.ineligibility_noninferiority_margin",
        "verdict.strategy_verdict",
        "compare",
    ): "CLASS",
    (
        "verdict.min_relative_improvement",
        "verdict.evaluate_window",
        "alternative_success_probability(relative_improvement)",
    ): "PROBE:evaluate_window",
    (
        "verdict.min_relative_improvement",
        "verdict.passes_window",
        "compare",
    ): "PROBE:passes_window",
    ("verdict.min_window_count", "run._plan_or_stop", "compare"): "CLASS",
    (
        "verdict.min_window_count",
        "run._sampling_record",
        "minimum_required_sample(window_count)",
    ): "PROBE:sample-floor-window-count",
    (
        "verdict.min_window_count",
        "verdict._not_evaluable",
        "compare",
    ): "PROBE:strategy-verdict-direct",
    (
        "verdict.min_window_rows",
        "run._sampling_record",
        "minimum_required_sample(rows_per_window)",
    ): "PROBE:sample-floor-rows",
    (
        "verdict.min_window_rows",
        "windows.plan_backtest_windows",
        "compare",
    ): "PROBE:window-rows",
    (
        "verdict.primary_hypothesis_count",
        "backtest_job._load_policies",
        "JobFailed",
    ): "ECHO",
    (
        "verdict.primary_hypothesis_count",
        "backtest_job._load_policies",
        "compare",
    ): "PROBE:job-direct",
    (
        "verdict.primary_hypothesis_count",
        "policy_values.alpha_for",
        "return",
    ): "PASS:alpha_for",
    (
        "verdict.primary_hypothesis_count",
        "policy_values.primary_alpha",
        "return",
    ): "PASS:primary_alpha",
    (
        "verdict.primary_hypothesis_count",
        "verdict.evaluate_window",
        "alpha_for",
    ): "CLASS",
    (
        "verdict.primary_hypothesis_count",
        "verdict.passes_window",
        "alpha_for",
    ): "PROBE:passes_window",
    (
        "verdict.target_power",
        "verdict.evaluate_window",
        "required_discordant_pairs(target_power)",
    ): "CLASS",
    ("version", "run._assemble_verdict", "BacktestVerdict(policy_version)"): "ECHO",
    ("window.days", "windows._calendar_windows", "timedelta(days)"): "CLASS",
    (
        "window.embargo_days",
        "windows.plan_backtest_windows",
        "timedelta(days)",
    ): "CLASS",
}

_COVERAGE_KINDS: Final[tuple[str, ...]] = ("PROBE:", "CLASS", "PASS:", "ECHO")


def test_the_use_census_is_generated_and_every_site_is_covered() -> None:
    """D-6G2a-12 — 쓰임 명단은 **생성**이고, 자리마다 덮개가 있다(등식).

    r1 의 막는 결함: 명단을 손으로 적고 「읽는 자리」를 셌더니, 읽은 값이 흘러가 쓰이는
    자리가 가려졌다. `alpha_for` 가 한 번 읽은 유의수준은 검정력 계산과 판정식 **두
    곳에서** 쓰이고, 창 최소 수는 창 계획·판정·**최소 표본식** 세 곳에서 쓰인다. 한 곳만
    상수로 바꾼 변이가 전체 suite 를 지났다.

    이제 명단은 출하 코드의 AST 와 로드된 정책 객체에서만 나온다. 새 쓰임이 생기면 삼중이
    늘고 덮개 등재가 없어 여기서 RED 다 — 목록이 낡지 않는다."""
    policy = load_strategy_backtest_policy(_SHIPPED_BACKTEST_POLICY)
    assert isinstance(policy, StrategyBacktestPolicy), policy
    census = policy_use_census(policy)
    generated = {(use.key, use.site, use.consumer) for use in census}
    assert generated == set(_USE_COVERAGE), (
        f"덮개 등재가 없는 쓰임: {sorted(generated - set(_USE_COVERAGE))} · "
        f"출하 코드에 없는 등재: {sorted(set(_USE_COVERAGE) - generated)}"
    )
    keys = set(policy_file_keys())
    assert {use.key for use in census} == keys, (
        f"쓰임이 하나도 없는 정책 값: {sorted(keys - {use.key for use in census})}"
    )
    for triple, coverage in _USE_COVERAGE.items():
        assert any(coverage.startswith(kind) for kind in _COVERAGE_KINDS), (
            f"{triple} 의 덮개 어휘가 낯설다: {coverage}"
        )
    # **키마다 부류 단언으로 덮는 자리는 하나까지.** 둘을 부류 단언 하나로 덮으면 한 자리의
    # 상수가 다른 자리의 움직임에 가린다 — r1 의 결함을 구조로 막는 자리다.
    class_sites: dict[str, set[str]] = {}
    for (key, site, _), coverage in _USE_COVERAGE.items():
        if coverage == "CLASS":
            class_sites.setdefault(key, set()).add(site)
    crowded = {
        key: sorted(sites) for key, sites in class_sites.items() if len(sites) > 1
    }
    assert not crowded, (
        "부류 단언 하나로 두 자리를 덮으려 한다 — 자리마다 전용 probe 나 변이가 필요하다: "
        f"{crowded}"
    )


# ── 자리별 probe (D-6G2a-12) ────────────────────────────────────────────────
# 아래 표는 덮개 등재의 `PROBE:<이름>` 이 **실제로 존재하는 단언**임을 구조로 잠근다 —
# 이름만 적고 단언을 안 쓰면 `test_every_probe_named_in_the_coverage_table_exists` 가 RED 다.
_PROBE_TESTS: Final[dict[str, str]] = {
    "PROBE:passes_window": "test_passes_window_follows_the_policy_at_its_own_site",
    "PROBE:evaluate_window": "test_evaluate_window_follows_the_improvement_at_the_power_site",
    "PROBE:fit-seed": "test_the_fit_seed_site_follows_the_first_stability_seed",
    "PROBE:sample-floor-window-count": "test_min_window_count_is_read_at_three_sites",
    "PROBE:sample-floor-rows": "test_min_window_rows_is_read_at_both_sites",
    "PROBE:window-rows": "test_min_window_rows_is_read_at_both_sites",
    "PROBE:admit-rows": "test_institution_constants_are_read_at_every_site",
    "PROBE:fit-direct": "test_institution_constants_are_read_at_every_site",
    "PROBE:fit-bin-count": "test_the_fit_bin_count_site_follows_the_reserve_price_count",
    "PROBE:s4-bid": "test_institution_constants_are_read_at_every_site",
    "PROBE:construction-pad": "test_pure_construction_cost_ratio_is_read_at_both_sites",
    "PROBE:strategy-verdict-direct": "test_min_window_count_is_read_at_three_sites",
    "PROBE:job-direct": "test_primary_hypothesis_count_is_read_at_both_sites",
}


def test_every_probe_named_in_the_coverage_table_exists() -> None:
    """덮개 등재의 `PROBE:` 이름마다 **그 단언이 이 모듈에 있다**.

    이름만 적고 단언을 안 쓰면 명단 등식이 초록인데 자리는 비어 있다 — 그 조합이 r1 의
    결함 모양이다. 여기서 이름과 함수를 맞댄다."""
    named = {
        coverage for coverage in _USE_COVERAGE.values() if coverage.startswith("PROBE:")
    }
    assert named == set(_PROBE_TESTS), (
        f"등재됐는데 구현 표에 없는 probe: {sorted(named - set(_PROBE_TESTS))} · "
        f"구현 표에만 있는 probe: {sorted(set(_PROBE_TESTS) - named)}"
    )
    for probe, function_name in _PROBE_TESTS.items():
        assert function_name in globals(), (
            f"{probe} 가 가리키는 {function_name} 가 없다"
        )
        assert callable(globals()[function_name]), function_name


def test_passes_window_follows_the_policy_at_its_own_site(harness: _Harness) -> None:
    """D-6G2a-12 — 판정식(`passes_window`)은 **그 자리에서** 정책을 따른다.

    `alpha_for(...)` 가 한 번 낸 유의수준은 **두 곳에서** 쓰인다: 필요 표본 수 계산과 이
    판정식이다. 판으로는 검정력 쪽만 보인다 — 검정력이 서면 p 가 유의수준보다 훨씬 아래로
    내려가 판정식 쪽 유의수준이 결말을 못 바꾼다(구조적 사실, evidence 「판을 지을 수 없었던
    방향」). 그래서 판정식을 **직접** 부르고 p 를 두 유의수준 **사이**에 둔다.

    이 자리가 비어 있던 것이 r1 의 막는 결함이다: 분모만 상수로 바꾼 변이(E1 r5 형)와
    유의수준만 상수로 바꾼 변이(V1n)가 전체 suite 를 지났다."""
    base = harness.policy("pass", "pw-base")
    assert base.verdict.alpha == 0.05, base.verdict.alpha
    assert base.verdict.primary_hypothesis_count == 3, base.verdict

    # p = 0.03 은 보조 유의수준(0.05) 아래이고 주 유의수준(0.05/3) 위다.
    assert passes_window(0.25, 0.03, base, primary=False) is True
    assert passes_window(0.25, 0.03, base, primary=True) is False

    # 분모를 1 로 내리면 주 유의수준이 0.05 가 되어 주에서도 선다 — **분모가 이 자리에서
    # 읽히지 않으면** 여전히 거짓이다(E1 r5 형을 잡는 단언).
    loosened = harness.policy(
        "pass", "pw-loose", **{"verdict.primary_hypothesis_count": "1"}
    )
    assert passes_window(0.25, 0.03, loosened, primary=True) is True

    # 유의수준을 0.02 로 내리면 보조에서도 떨어진다 — **유의수준이 이 자리에서 읽히지
    # 않으면** 여전히 참이다(V1n 을 잡는 단언).
    tightened = harness.policy("pass", "pw-tight", **{"verdict.alpha": "0.02"})
    assert passes_window(0.25, 0.03, tightened, primary=False) is False

    # 상대 개선 하한도 같은 자리에서 비교된다.
    raised = harness.policy(
        "pass", "pw-gain", **{"verdict.min_relative_improvement": "0.30"}
    )
    assert passes_window(0.25, 0.03, raised, primary=False) is False


def _window_scores(name: str, wins: tuple[bool, ...]) -> StrategyScores:
    """창 하나의 채점 묶음 — 승패만 지정한다(적격·bp 는 이 단언에 들어오지 않는다)."""
    return StrategyScores(
        name=name,
        scores=tuple(
            NoticeScore(f"n-{index}", True, 0.0, won, None)
            for index, won in enumerate(wins)
        ),
    )


def test_evaluate_window_follows_the_improvement_at_the_power_site(
    harness: _Harness,
) -> None:
    """D-6G2a-12 — 상대 개선 하한은 **두 자리**에서 쓰인다.

    자리 ① 판정식의 비교(위 test) 자리 ② **필요 표본 수** 계산 — 사전 등록한 효과 크기가
    대립가설의 성공 확률을 정하므로, 하한을 올리면 필요 표본 수가 줄어든다. 같은 창 점수로
    두 정책을 재면 그 자리가 정책을 따르는지 보인다."""
    baseline = _window_scores("S0", (True,) * 30 + (False,) * 10)
    strategy = _window_scores("C", (True,) * 36 + (False,) * 4)
    measured = {
        value: evaluate_window(
            window_index=0,
            baseline=baseline,
            strategy=strategy,
            policy=harness.policy(
                "pass", f"ew-{value}", **{"verdict.min_relative_improvement": value}
            ),
            primary=False,
        ).required_discordant_pairs
        for value in ("0.05", "0.40")
    }
    assert None not in measured.values(), measured
    assert measured["0.05"] != measured["0.40"], (
        f"자리 ② — 필요 표본 수가 상대 개선 하한을 따르지 않는다: {measured}"
    )


def test_the_fit_seed_site_follows_the_first_stability_seed(harness: _Harness) -> None:
    """D-6G2a-12 — 적합도의 seed 자리는 `stability_seeds.0` 을 따른다.

    그 자리와 seed 순회가 **같은 함수**에 있어 판으로는 묶여 보인다. 가르는 방법은 seed 에
    **민감하지 않은** 판이다: 계획 전략은 seed 를 보지 않으므로 순회 쪽은 아무것도 바꾸지
    못하고, 첫 seed 를 흔들면 **적합도 칸만** 움직인다.

    대조도 함께 둔다 — `stability_seeds.1` 은 적합도에 닿지 않으므로 적합도 칸이 그대로다.
    그래서 「첫 seed 가 적합도에 간다」가 선언이 아니라 측정이다."""

    def fit_fields(**changes: str) -> tuple[float, float]:
        payload = json.loads(harness.verdict("pass", **changes))
        fit = payload["distribution_fit"]
        return (fit["ks_statistic"], fit["max_bin_deviation"])

    base = fit_fields()
    moved_first = fit_fields(**{"stability_seeds.0": "999101"})
    moved_second = fit_fields(**{"stability_seeds.1": "999102"})
    assert moved_first != base, (
        f"적합도 seed 자리가 첫 seed 를 따르지 않는다: {base} == {moved_first}"
    )
    assert moved_second == base, (
        f"둘째 seed 가 적합도에 닿는다 — 자리 등재가 틀렸다: {base} != {moved_second}"
    )
    assert _outcome_view(harness.verdict("pass")) == _outcome_view(
        harness.verdict("pass", **{"stability_seeds.0": "999101"})
    ), "이 판은 seed 비민감이어야 한다(결말이 움직이면 자리가 섞인다)"


def test_the_fit_bin_count_site_follows_the_reserve_price_count(
    harness: _Harness, monkeypatch: pytest.MonkeyPatch
) -> None:
    """D-6G2a-14 — 적합도의 **구간 수** 자리가 예비가격 수를 따른다.

    r0 에서 「이 층에서 가를 수 없다」로 등재했다. 그 논증은 **정책 입력만 쓸 때** 참이다:
    공시 스칼라 둘이 기준 표본과 구간 수에 동시에 의존하고 정책 값 하나가 둘을 정한다.
    verifier r1 M-2 가 test 쪽 seam 을 지적했다 — **기준 표본을 고정**하면 구간 수만 남는다.
    그러면 정직한 코드에서 구간 편차가 움직이고, 구간 수를 상수로 박은 변이에서는 그대로다.
    `OPEN-6G2A-FIT-BIN-COUNT-SITE` 는 이 단언으로 닫힌다."""
    board = harness.board("mixed")
    shipped = harness.policy("mixed", "bins-base")
    admission = admit_rows(board.snapshot.rows, shipped)
    assert admission.admitted, admission.excluded
    pinned = _reference_sample(shipped, shipped.stability_seeds[0])
    monkeypatch.setattr(
        "ml_engine.evaluation.backtest.fit._reference_sample",
        lambda policy, seed: pinned,
    )
    measured = {
        value: check_institutional_fit(
            admission.admitted,
            harness.policy(
                "mixed", f"bins-{value}", **{"institution.reserve_price_count": value}
            ),
            seed=shipped.stability_seeds[0],
        ).max_bin_deviation
        for value in ("15", "16")
    }
    assert measured["15"] != measured["16"], (
        f"구간 수 자리가 정책을 따르지 않는다(기준 표본 고정): {measured}"
    )


@pytest.mark.parametrize("key", sorted(_JUDGEMENT_INPUTS))
def test_judgement_inputs_flip_the_decision(key: str, harness: _Harness) -> None:
    """ⓐ — 판정 입력은 **결말 부류**를 바꾼다(D-6G2a-2·3).

    투영은 계약 문면 그대로다 — `Passed | Failed | NotEvaluable` 과 통과 여부이고 「못
    쟀다」의 **사유는 담지 않는다**. r0 은 사유까지 담은 투영으로 재서, 사유만 옮겨가는 값
    셋이 ⓐ 로 통과했다(code-review r1 M-5 — 계약 문면과 구현의 불일치). 사유 축은 버리지
    않는다: 그 셋은 ⓑ 로 옮기고 **사유가 움직인다**는 전용 단언이 따로 잠근다.

    극단으로 밀면 정직한 코드에서는 결말이 뒤집힌다. 그 자리에 코드에 박힌 수가
    닿아 있으면 정책을 아무리 밀어도 결말이 그대로라 RED 다 — 숨긴 수의 **형태**와
    무관하게 잡힌다(리터럴 게이트가 형태로 못 잡는 자리를 이 층이 받는다)."""
    for probe in _JUDGEMENT_INPUTS[key]:
        baseline = _outcome_view(harness.verdict(probe.board))
        moved = _outcome_view(harness.verdict(probe.board, **{key: probe.value}))
        assert moved != baseline, (
            f"{key} 를 판 {probe.board} 에서 {probe.value} 로 밀었는데 **결말이 "
            f"그대로다** — 그 자리가 정책을 읽지 않는다(코드에 박힌 수가 판정에 닿았다). "
            f"결말: {baseline}"
        )


@pytest.mark.parametrize("key", sorted(_REASON_MOVERS))
def test_reason_movers_move_the_unmeasurable_reason(
    key: str, harness: _Harness
) -> None:
    """「못 쟀다」의 **사유**도 정책을 따른다(code-review r1 M-5).

    사유는 결말 부류가 아니므로 이 값들은 ⓑ 다. 그래도 사유를 고르는 자리는 정책을 따라야
    한다 — 「못 쟀다」는 결말 하나가 아니라 넷이고, 사유만 옮겨가는 변화를 아무도 보지 않으면
    그 자리의 상수가 숨는다. 그래서 부류 단언과 **별도로** 사유 이동을 단언한다."""
    probe = _REASON_MOVERS[key]
    base = _decision_view(harness.verdict(probe.board))
    moved = _decision_view(harness.verdict(probe.board, **{key: probe.value}))
    assert moved != base, (
        f"{key} 를 {probe.value} 로 밀었는데 사유까지 그대로다 — 그 자리가 정책을 읽지 않는다"
    )
    assert _outcome_view(harness.verdict(probe.board)) == _outcome_view(
        harness.verdict(probe.board, **{key: probe.value})
    ), f"{key} 가 결말 부류를 바꿨다 — ⓐ 로 옮겨라"


@pytest.mark.parametrize("key", sorted(_OUTPUT_INPUTS))
def test_output_inputs_move_a_non_echo_field_without_flipping(
    key: str, harness: _Harness
) -> None:
    """ⓑ — 산출 입력은 결말을 **그대로 두고** 판정문의 비메아리 칸을 바꾼다(D-6G2a-3).

    두 단언을 함께 둔다: 결말이 바뀌면 그 값은 판정 입력이라 부류가 틀렸고, 판정문이
    그대로면 그 자리가 정책을 읽지 않는다. 어느 쪽이든 표가 낡았다는 뜻이다."""
    probe = _OUTPUT_INPUTS[key]
    before = harness.values(probe.board)[key]
    baseline = harness.verdict(probe.board)
    moved = harness.verdict(probe.board, **{key: probe.value})
    assert _outcome_view(moved) == _outcome_view(baseline), (
        f"{key} 가 **결말 부류**를 바꿨다 — 산출 입력이 아니라 판정 입력이다(부류를 옮겨라)"
    )
    pruned_base, pruned_moved = _document_pair(baseline, moved, before, probe.value)
    assert pruned_moved != pruned_base, (
        f"{key} 를 {before} -> {probe.value} 로 바꿨는데 판정문이 그대로다 — 그 자리가 "
        "정책을 읽지 않는다(메아리를 뺀 투영에서 아무 칸도 움직이지 않았다)"
    )


@pytest.mark.parametrize(
    ("key", "board"),
    [(key, board) for key in sorted(_UNREAD) for board in sorted(_ALL_BOARDS)],
)
def test_unread_values_change_nothing(key: str, board: str, harness: _Harness) -> None:
    """ⓒ — 판독 밖 값은 **어느 판에서도** 아무것도 바꾸지 않는다(D-6G2a-3·13).

    목록이 낡지 않게 하는 자리다: 등재된 값이 무언가를 움직이기 시작하면 RED 가 되고, 그때
    부류를 ⓐ 나 ⓑ 로 옮겨야 한다. 「못 잰다」는 선언이 아니라 「쓰이지 않는다」는 측정이다.

    r0 은 **출하 판 하나 · 작은 흔들기 하나**로만 쟀다. 그래서 출하 판을 움직이지 않는
    19 값은 ⓐ 에서 ⓒ 로 옮겨도 초록이었다 — 우회 ⑥ 방어가 그만큼 비어 있었다(verifier r1
    M-1). 이제 **판 전부와 세기 표의 값 전부**로 돈다."""
    base_value = harness.values(board)[key]
    for moved_value in _STRONG_VALUES[key]:
        if moved_value == base_value:
            continue
        baseline = harness.verdict(board)
        moved = harness.verdict(board, **{key: moved_value})
        pruned_base, pruned_moved = _document_pair(
            baseline, moved, base_value, moved_value
        )
        assert pruned_moved == pruned_base, (
            f"{key} 가 판 {board} 에서 {moved_value} 로 판정문을 움직였다 — 「판독 밖」 "
            f"등재가 틀렸다. 사유: {_UNREAD[key]}"
        )


def test_the_pass_branch_actually_runs(harness: _Harness) -> None:
    """D-6G2a-4 — 적어도 한 판에서 후보가 기준선을 **실제로 이겨** 통과 갈래를 지난다.

    6G 의 합성 판에서는 어떤 후보도 기준선을 이기지 못해(승률 0 · 상대 개선 -1.0 · p 1.0)
    통과 조건의 다른 레그에서 먼저 떨어졌다. 그래서 판정 입력 셋이 「못 움직인다」로
    등재됐다 — 게이트 술어의 문제가 아니라 **판의 문제**였다."""
    for name in ("pass", "pass-primary"):
        payload = json.loads(harness.verdict(name))
        assert "strategies" in payload, payload.get("stopped")
        outcomes = {item["outcome"] for item in payload["strategies"]}
        assert outcomes == {"StrategyPassed"}, (
            f"판 {name} 이 통과 갈래를 지나지 않는다: {outcomes}"
        )
        pooled = payload["strategies"][0]["pooled"]
        assert pooled["strategy_win_rate"] > pooled["baseline_win_rate"], pooled
        assert pooled["passed"] is True, pooled


def test_min_window_count_is_read_at_three_sites(harness: _Harness) -> None:
    """D-6G2a-6·12 — `verdict.min_window_count` 는 **세 자리**에서 쓰인다.

    자리 ① 창 계획 뒤의 멈춤 판정(`run`) — 선택된 창이 최소 수에 못 미치면 판정 대신 멈춤.
    자리 ② 「못 쟀다」의 창 부족 갈래(`verdict`) — ① 이 먼저 멈추므로 전체 실행으로 도달할 수
    없고, 판정 함수를 **직접** 불러 잰다.
    자리 ③ **최소 필요 표본 결정식**(`run` 의 표본 기록) — r0 의 손 등재가 빠뜨린 자리다
    (code-review r1 H-1). 창 밖 행 수로 가른 판에서 그 자리만 묶인다: 창당 하한 50 · 창 3 ·
    업무 1 · 여유 0.20 이면 최소 필요 표본이 180 이고 행이 183 이라 선다. 창 최소 수를 4 로
    올리면 최소가 240 > 183 이 되어 **표본 쪽** 멈춤이 먼저 난다 — 그 자리를 상수로 박으면
    최소가 180 에 묶여 표본 멈춤이 나지 않고 창 쪽 멈춤으로 떨어진다."""
    stopped = json.loads(harness.verdict("pass", **{"verdict.min_window_count": "4"}))
    assert stopped.get("stopped") == "INSUFFICIENT_WINDOWS", stopped.get("stopped")

    by_sample = json.loads(
        harness.verdict("sample-floor", **{"verdict.min_window_count": "4"})
    )
    assert by_sample.get("stopped") == "SAMPLE_SIZE_BELOW_MINIMUM", by_sample.get(
        "stopped"
    )

    window = _window_outcome()
    values = harness.values("pass")
    for threshold, expected in (
        ("3", NotEvaluableReason.NO_EVALUABLE_WINDOW),
        ("2", None),
    ):
        policy = load_strategy_backtest_policy(
            _write_policy(
                harness.directory / f"wcount-{threshold}",
                {**values, "verdict.min_window_count": threshold},
            )
        )
        assert isinstance(policy, StrategyBacktestPolicy), policy
        verdict = strategy_verdict(
            strategy_name="direct",
            primary=False,
            windows=(window, window),
            pooled=window,
            ineligibility_delta=0.0,
            seed_sign_consistent=True,
            policy=policy,
        )
        if expected is None:
            assert not isinstance(verdict, StrategyNotEvaluable) or (
                verdict.reason is not NotEvaluableReason.NO_EVALUABLE_WINDOW
            ), verdict
        else:
            assert isinstance(verdict, StrategyNotEvaluable), verdict
            assert verdict.reason is expected, verdict.reason


def _window_outcome() -> WindowOutcome:
    """창 하나의 결과 — 창 부족 갈래가 **창 수만** 보는지 재는 입력이다. 통과·검정력
    레그는 모두 만족시켜 두고 창 수 하나만 문턱에 걸리게 한다."""
    return WindowOutcome(
        window_index=0,
        row_count=50,
        baseline_win_rate=0.8,
        strategy_win_rate=1.0,
        relative_gain=0.25,
        discordant=DiscordantCounts(strategy_only=20, baseline_only=0),
        p_value=0.0,
        alpha_used=0.05,
        required_discordant_pairs=8,
        passed=True,
    )


def test_min_window_rows_is_read_at_both_sites(harness: _Harness) -> None:
    """D-6G2a-6 — `verdict.min_window_rows` 는 **두 자리**에서 읽힌다.

    자리 ① 최소 필요 표본 결정식(`run`) — 창당 하한 x 창 최소 수 x 업무 수 x (1 + 여유).
    자리 ② 창 제외의 표본 하한(`windows`) — 창당 행이 하한에 못 미치면 그 창이 빠진다.
    두 판은 **한쪽만 묶이게** 지었다(창 밖 행 수로 가른다) — 그래서 멈춤 사유가 다르고,
    한 자리만 상수가 되면 그 판 하나만 초록이 된다."""
    by_window = json.loads(
        harness.verdict("window-rows", **{"verdict.min_window_rows": "51"})
    )
    assert by_window.get("stopped") == "INSUFFICIENT_WINDOWS", by_window.get("stopped")
    reasons = [
        item["reason"]
        for item in json.loads(harness.verdict("window-rows"))["excluded_windows"]
    ]
    # 공집합에서도 통과하는 장식 단언을 두지 않는다(code-review r1 L-2) — 제외된 창이
    # **적어도 하나** 있고 그 사유가 닫힌 어휘 안이다.
    assert reasons, "창 제외가 하나도 기록되지 않았다"
    assert set(reasons) <= {"NO_HISTORY", "INSUFFICIENT_ROWS"}, reasons

    by_sample = json.loads(
        harness.verdict("sample-floor", **{"verdict.min_window_rows": "51"})
    )
    assert by_sample.get("stopped") == "SAMPLE_SIZE_BELOW_MINIMUM", by_sample.get(
        "stopped"
    )


def test_primary_hypothesis_count_is_read_at_both_sites(
    tmp_path: Path, harness: _Harness
) -> None:
    """D-6G2a-6 — `verdict.primary_hypothesis_count` 는 **두 자리**에서 읽힌다.

    자리 ① Bonferroni 분모(`policy_values.VerdictThresholds.primary_alpha`) — 주 가설의
    유의수준을 나눈다. 자리 ② job 조립의 주 가설 수 대조(`app.backtest_job`) — 분모와 S2
    후보 수가 어긋나면 job 이 거부한다. ② 는 판정을 돌리지 않으므로 ① 의 판으로는
    보이지 않는다."""
    flipped = _decision_view(
        harness.verdict("pass-primary", **{"verdict.primary_hypothesis_count": "4"})
    )
    assert flipped != _decision_view(harness.verdict("pass-primary")), flipped

    values = _flat_policy(_SHIPPED_BACKTEST_POLICY)
    values.update(_BASE_OVERRIDES)
    mismatched = _write_policy(
        tmp_path / "hypothesis-count",
        {**values, "verdict.primary_hypothesis_count": "4"},
    )
    outcome = run_backtest_job(
        snapshot_uri=str(tmp_path / "missing-snapshot"),
        backtest_policy_path=mismatched,
        inference_policy_path=_SHIPPED_INFERENCE_POLICY,
    )
    assert getattr(outcome, "reason", None) is (
        JobFailureReason.PRIMARY_HYPOTHESIS_COUNT_MISMATCH
    ), outcome


_REMOVAL_ACCEPTED: Final[dict[str, str]] = {
    # 키를 **지워도 로더가 받는** 값과 그 사유. 실측으로 하나뿐이다. 등재는 등식으로
    # 쓰이므로(아래 두 test), 받는 키가 늘거나 줄면 둘 중 하나가 RED 다.
    "stability_seeds.4": (
        "평탄 인덱스 목록의 **길이가 스키마에 고정돼 있지 않다**: 판독기"
        " (`evaluation.policy.collect_indexed_list`)는 인덱스가 0 부터 **연속**일 것만"
        " 요구하고 개수는 보지 않는다. 그래서 마지막 인덱스를 지우면 seed 넷짜리 정책이"
        " 조용히 선다(중간 인덱스를 지우면 구멍이 생겨 거부된다). A-3 승인 문면은"
        " 「seed 5」이고, 넷으로 돈 판정은 거부가 아니라 판정문의 `seeds` 목록으로만"
        " 드러난다 — `OPEN-6G2A-SEED-COUNT-NOT-PINNED` 로 등재한다(출하 코드 변경은"
        " 이 slice 의 out_scope, D-6G2a-9 ⓑ)"
    ),
}


def _dropped_policy(
    tmp_path: Path, dropped: str
) -> StrategyBacktestPolicy | PolicyRejected:
    values = _flat_policy(_SHIPPED_BACKTEST_POLICY)
    values.update(_BASE_OVERRIDES)
    return load_strategy_backtest_policy(
        _drop_key(tmp_path / dropped.replace(".", "_"), values, dropped)
    )


def _construction_strategy_input(
    harness: _Harness, seed: int = 20260812
) -> StrategyInput:
    """업무 셋 판에서 **공사** 공고 하나의 전략 입력 — S4 가 순공사원가선과 제도 분포를
    쓰는 자리를 직접 겨눈다. 판정 전체를 돌리지 않으므로 제외 단계의 읽기가 끼어들지
    않는다(자리를 가르는 유일한 방법이다 — 두 자리는 같은 ratio 구간에서 함께 묶인다)."""
    board = harness.board("mixed")
    policy = load_strategy_backtest_policy(
        _write_policy(harness.directory / "strategy-input", harness.values("mixed"))
    )
    assert isinstance(policy, StrategyBacktestPolicy), policy
    admission = admit_rows(board.snapshot.rows, policy)
    construction = [
        item
        for item in admission.admitted
        if item.row.notice.category is BusinessCategory.CONSTRUCTION
        and item.row.notice.pure_construction_cost is not None
    ]
    assert construction, "업무 셋 판에 승인된 공사 행이 없다"
    target = construction[-1]
    history = tuple(item for item in admission.admitted if item is not target)
    return build_strategy_input(target, history, seed=seed)


def _policy_with(
    harness: _Harness, slug: str, **changes: str
) -> StrategyBacktestPolicy:
    policy = load_strategy_backtest_policy(
        _write_policy(harness.directory / slug, harness.values("mixed", **changes))
    )
    assert isinstance(policy, StrategyBacktestPolicy), policy
    return policy


def test_pure_construction_cost_ratio_is_read_at_both_sites(harness: _Harness) -> None:
    """D-6G2a-6 — 공사 순공사원가 배제 비율은 **두 자리**에서 읽힌다.

    자리 ① 제외 단계의 실격선(`rules.pure_cost_floor`) — 적격 투찰자가 없으면 그 공고가
    빠진다. 자리 ② S4 의 실격선 — 몬테카를로의 후보 투찰률 격자에 같은 선이 걸린다.

    **자리를 가르지 않으면 한쪽의 상수가 보이지 않는다**: 출하 전략 판에서 ① 에만 상수를
    박은 변이(P4)가 초록이었다 — ② 가 정책을 계속 따라서 판정문이 움직였다. 그래서 ① 은
    전략이 정책을 읽지 않는 **계획 전략 판**에서, ② 는 S4 의 투찰금액을 **직접** 비교해
    잰다."""
    key = "floor.pure_construction_cost_ratio"
    probe = _OUTPUT_INPUTS[key]
    assert probe.board == "construction-pad", probe
    base = harness.verdict(probe.board)
    moved = harness.verdict(probe.board, **{key: probe.value})
    pruned_base, pruned_moved = _document_pair(
        base, moved, harness.values(probe.board)[key], probe.value
    )
    assert pruned_moved != pruned_base, (
        "자리 ① — 계획 전략 판에서 배제 비율을 내렸는데 판정문이 그대로다(제외 단계가 "
        "정책을 읽지 않는다)"
    )

    request = _construction_strategy_input(harness)
    strategy = InstitutionalMonteCarloStrategy()
    bids = {
        value: strategy.bid(
            request,
            _policy_with(
                harness,
                f"s4-ratio-{value}",
                **{"floor.pure_construction_cost_ratio": value},
            ),
        )
        for value in ("0.98", "0.9999")
    }
    assert all(isinstance(item, BidAmount) for item in bids.values()), bids
    assert bids["0.98"] != bids["0.9999"], (
        f"자리 ② — S4 의 투찰금액이 배제 비율을 따르지 않는다: {bids}"
    )


@pytest.mark.parametrize(
    "key", ["institution.reserve_price_count", "institution.draw_count"]
)
def test_institution_constants_are_read_at_every_site(
    key: str, harness: _Harness
) -> None:
    """D-6G2a-6 — 제도 상수 둘은 **세 자리**에서 읽힌다.

    자리 ① 제외 ⑮(`rules` — 예비가격 수·추첨 수가 맞지 않는 행을 빼낸다) 자리 ② P-4 적합도의
    기준 분포와 구간 수(`fit`) 자리 ③ S4 의 사정률 표본(`strategies`).

    ① 은 전체 실행에서 전량 제외로 드러나지만(부류 ⓐ), ① 이 먼저 발화하므로 ②③ 은 그
    경로로 **도달할 수 없다** — 그래서 두 함수를 직접 부른다. 셋 중 하나만 상수가 되는 변이를
    전체 실행으로는 잡을 수 없다는 것이 실측이다(같은 계열의 P4)."""
    board = harness.board("mixed")
    shipped = _policy_with(harness, f"inst-{key}-base")
    nudged = _policy_with(
        harness,
        f"inst-{key}-moved",
        **{key: str(int(harness.values("mixed")[key]) + 1)},
    )

    # 자리 ① — 제외 ⑮. **전체 실행이 아니라 제외 단계를 직접 부른다**: 전체 실행은 ①②③ 이
    # 모두 같은 값을 읽어 움직임의 출처를 가리지 못한다(code-review r1 H-2).
    first = admit_rows(board.snapshot.rows, shipped)
    second = admit_rows(board.snapshot.rows, nudged)
    assert len(first.admitted) != len(second.admitted), (
        f"자리 ① — 제외 ⑮ 가 {key} 를 따르지 않는다: "
        f"{len(first.admitted)} == {len(second.admitted)}"
    )

    # 자리 ② — 적합도. 승인 집합을 고정해 두고 정책만 바꾼다.
    admission = admit_rows(board.snapshot.rows, shipped)
    assert admission.admitted, admission.excluded
    first = check_institutional_fit(
        admission.admitted, shipped, seed=shipped.stability_seeds[0]
    )
    second = check_institutional_fit(
        admission.admitted, nudged, seed=nudged.stability_seeds[0]
    )
    # **둘 다** 움직여야 한다 — 하나만 보면 적합도의 두 읽기(기준 표본 · 구간 수) 중
    # 한쪽만 정책을 따라도 초록이 된다(변이 P9 실측). 구간 수 자리는 기준 표본을 고정한
    # 전용 probe 가 따로 가른다(D-6G2a-14 — `OPEN-6G2A-FIT-BIN-COUNT-SITE` 종결).
    assert first.ks_statistic != second.ks_statistic, (
        f"자리 ② — 적합도의 **기준 표본**이 {key} 를 따르지 않는다: "
        f"{first.ks_statistic} == {second.ks_statistic}"
    )
    assert first.max_bin_deviation != second.max_bin_deviation, (
        f"자리 ② — 적합도의 **구간 비교**가 {key} 를 따르지 않는다: "
        f"{first.max_bin_deviation} == {second.max_bin_deviation}"
    )

    # 자리 ③ — S4 의 투찰금액.
    request = _construction_strategy_input(harness)
    strategy = InstitutionalMonteCarloStrategy()
    bids = (strategy.bid(request, shipped), strategy.bid(request, nudged))
    assert all(isinstance(item, BidAmount) for item in bids), bids
    assert bids[0] != bids[1], (
        f"자리 ③ — S4 의 투찰금액이 {key} 를 따르지 않는다: {bids}"
    )


@pytest.mark.parametrize(
    "dropped",
    sorted(set(_flat_policy(_SHIPPED_BACKTEST_POLICY)) - set(_REMOVAL_ACCEPTED)),
)
def test_the_loader_refuses_a_policy_with_a_key_removed(
    dropped: str, tmp_path: Path
) -> None:
    """우회 ⑤ — 로더가 **기본값을 채우면** 파일에서 키를 빼도 같은 값이 나온다.

    그러면 그 값은 정책 파일이 아니라 코드가 정하는 값이 되고, 민감도 test 전체가
    그 자리를 지나친다. 키를 하나 지운 정책은 **거부돼야 한다**(fail-closed) — 값마다
    전수로 잰다. 받는 것으로 **실측된** 키는 사유와 함께 등재하고 아래 test 가 그 등재를
    등식으로 잠근다."""
    loaded = _dropped_policy(tmp_path, dropped)
    assert isinstance(loaded, PolicyRejected), (
        f"{dropped} 를 지운 정책이 로더에 통과했다 — 그 키에 기본값이 채워졌거나 스키마가 "
        f"그 키를 요구하지 않는다는 뜻이고, 그 값은 더 이상 정책 파일이 정하는 값이 "
        f"아니다: {loaded}"
    )


@pytest.mark.parametrize("dropped", sorted(_REMOVAL_ACCEPTED))
def test_the_registered_removable_keys_really_load(
    dropped: str, tmp_path: Path
) -> None:
    """등재된 예외의 **양성 대조** — 「받는다」가 선언이 아니라 측정이다.

    로더가 나중에 이 키를 거부하게 되면(길이를 고정하면) 여기서 RED 가 되고, 그때 등재를
    지워야 한다. 등재가 낡은 채로 남아 다른 키의 구멍을 가리는 일을 막는 자리다."""
    loaded = _dropped_policy(tmp_path, dropped)
    assert isinstance(loaded, StrategyBacktestPolicy), (
        f"{dropped} 를 지운 정책이 이제 거부된다 — 등재를 지워라: {loaded}"
    )
    assert len(loaded.stability_seeds) == 4, loaded.stability_seeds
    assert _REMOVAL_ACCEPTED[dropped].strip()


def test_the_artefact_still_carries_the_reproducibility_echo(
    tmp_path: Path, _inference: InferencePolicy
) -> None:
    """D-6G2a-1 의 **뒷문면** — 비교 투영에서 뺀 두 칸은 **산출물에는 남는다**.

    빼는 자리가 test 의 비교 함수 하나임을 거동으로 잠근다. 판정 JSON 에서 이 둘이
    사라지면 재현성 공시가 사라지고, 「어느 정책으로 낸 판정인가」를 산출물만 보고 말할 수
    없게 된다 — 투영을 고치는 일이 산출물을 고치는 일로 번지지 않게 여기서 막는다."""
    base_values = _flat_policy(_SHIPPED_BACKTEST_POLICY)
    base_values.update(_BASE_OVERRIDES)
    policy = load_strategy_backtest_policy(_write_policy(tmp_path, base_values))
    assert isinstance(policy, StrategyBacktestPolicy), policy
    payload = json.loads(
        _verdict_bytes(_small_snapshot(), build_strategies(_inference), policy)
    )
    assert payload["policy_version"] == policy.version
    assert payload["policy_checksum"] == strategy_backtest_policy_checksum(policy)
    assert set(_REPRODUCIBILITY_ECHO_KEYS) <= set(payload), sorted(payload)


def test_every_policy_value_changes_the_verdict(
    tmp_path: Path, _inference: InferencePolicy
) -> None:
    """**정책 값 하나를 바꾸면 판정 바이트가 바뀐다** — 출하 전략 판의 전수 훑기.

    부류 표가 값마다 **겨눈 판**에서 재는 것과 달리, 이 층은 출하 전략 판 하나에서 39 개를
    같은 작은 흔들기로 훑는다. 두 층의 결과가 다른 것이 정상이다(이 판에서 돌지 않는
    자리가 있다) — 그래서 여기서 움직이지 않은 값의 목록을 **수로** 고정한다. 그 수가
    바뀌면 출하 코드의 어떤 자리가 정책을 읽거나 읽지 않게 됐다는 뜻이다.

    `policy_checksum` 을 뺀 투영이라 이 수는 6G 의 「39개 전부」가 아니다(D-6G2a-8)."""
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
        assert moved[key] != base_values[key], f"{key} 가 흔들리지 않았다"
        policy = load_strategy_backtest_policy(
            _write_policy(tmp_path / key.replace(".", "_"), moved)
        )
        # **거부를 통과로 세지 않는다.** 「로더가 거부했으니 민감하다」로 접으면 판정
        # 경로를 한 번도 돌지 않고 초록이 된다 — 실제로 그 갈래가 seed 하나를 덮어
        # 민감도가 재지지 않은 적이 있다(perturbation 이 seed 를 날짜로 접었다).
        assert isinstance(policy, StrategyBacktestPolicy), (
            f"{key} 를 흔든 정책이 로더에 거부됐다 — 불변식을 깨지 않는 방향으로 "
            f"밀어야 판정 경로가 실제로 돈다: {policy}"
        )
        pruned_base, pruned_moved = _document_pair(
            baseline_bytes,
            _verdict_bytes(snapshot, strategies, policy),
            base_values[key],
            moved[key],
        )
        if pruned_moved == pruned_base:
            unchanged.append(key)

    assert sorted(unchanged) == sorted(_SHIPPED_BOARD_UNMOVED), (
        "출하 전략 판에서 움직이지 않은 값의 목록이 바뀌었다 — "
        f"새로 멈춘 값: {sorted(set(unchanged) - set(_SHIPPED_BOARD_UNMOVED))} · "
        f"이제 움직이는 값: {sorted(set(_SHIPPED_BOARD_UNMOVED) - set(unchanged))}"
    )


_SHIPPED_BOARD_UNMOVED: Final[frozenset[str]] = frozenset(
    {
        # 출하 전략 판(용역 하나, `SampleVariant.MAIN`)에서 **작은 흔들기로는** 판정문이
        # 움직이지 않는 값들. 「판정에 쓰이지 않는다」가 아니다 — 이 판에서 그 자리가 돌지
        # 않는다는 뜻이고, 부류 표가 각 값을 **겨눈 판**에서 다시 잰다. 어느 판에서 잡히는지
        # 아래 사유에 적는다.
        #
        # 업무 축이 공사·물품일 때만 도는 자리(판 `mixed`):
        "effective.construction",
        "effective.goods",
        "floor.pure_construction_cost_ratio",
        "sampling.calls_per_notice_construction",
        "sampling.calls_per_notice_goods",
        "strategy.s1_offset_bp",
        # 문턱을 **넘겨야** 발화하는 자리(작은 흔들기로는 못 넘는다; 부류 표가 넘긴다):
        "effective.service",
        "fit.alpha",
        "fit.max_bin_ratio_deviation",
        "fit.min_sample_count",
        "floor.rate_band_low",
        "sampling.max_total_calls",
        "strategy.s4_min_competitor_samples",
        "verdict.ineligibility_noninferiority_margin",
        "window.embargo_days",
        # 판별 표본 걸러내기에서만 도는 자리(판 `wide-reserve`):
        "sensitivity.wide_reserve_half_width",
        # 출하 전략의 난수가 **그 seed 에서** 같은 승패를 내는 자리(판 `seeded` 가 잰다):
        "stability_seeds.1",
        "stability_seeds.2",
        "stability_seeds.4",
        # 판정문의 공시 칸에만 실리는 값(부류 ⓒ):
        "version",
    }
)
