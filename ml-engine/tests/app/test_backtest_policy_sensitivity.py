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
from ml_engine.evaluation.backtest.mcnemar import DiscordantCounts
from ml_engine.evaluation.backtest.observations import LoadedSnapshot
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
    strategy_backtest_policy_checksum,
)
from ml_engine.evaluation.backtest.records import BacktestRequest, SampleVariant
from ml_engine.evaluation.backtest.report import canonical_verdict_bytes
from ml_engine.evaluation.backtest.run import run_strategy_backtest
from ml_engine.evaluation.backtest.snapshot import load_snapshot
from ml_engine.evaluation.backtest.verdict import (
    StrategyNotEvaluable,
    WindowOutcome,
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


def _echoed_forms(before: str, after: str) -> frozenset[str]:
    return frozenset(
        repr(parsed) for raw in (before, after) for parsed in _echo_forms(raw)
    )


def _document_view(payload_bytes: bytes, echoed: frozenset[str]) -> object:
    """**판정문 투영** — 재현성 메아리 두 칸을 뺀 뒤 흔든 값의 메아리 잎을 지운 나머지.

    남는 차이는 그 값이 판정·산출을 **실제로 움직인 몫**이다."""
    payload = json.loads(payload_bytes)
    if isinstance(payload, dict):
        payload = {
            key: value
            for key, value in payload.items()
            if key not in _REPRODUCIBILITY_ECHO_KEYS
        }
    return _strip_echo(payload, echoed)


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
    # 예가 범위가 넓은 공고를 섞고 **판별 표본 걸러내기**로 도는 판.
    "wide-reserve": lambda _: _planned_board(
        BoardSpec(_uniform(BoardWindow(10, 0, 40, 0)), wide_reserve_pad=12),
        variant=SampleVariant.EXCLUDE_WIDE_RESERVE_RANGE,
    ),
}


# ── 부류 셋 (D-6G2a-2) ───────────────────────────────────────────────────────


@dataclass(frozen=True)
class _Probe:
    """값 하나를 어느 판에서 어떤 값으로 흔드는가."""

    board: str
    value: str


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
    ),
    # 창 규칙 — 창 폭이 바뀌면 창 경계가 바뀌고, embargo 를 넓히면 이력이 끊긴다.
    "window.days": (_Probe("shipped", "8"),),
    "window.embargo_days": (_Probe("pass", "60"),),
    # 제도 상수 — 예비가격 수·추첨 수가 제외 ⑮ 와 적합도와 S4 의 분포에 동시에 닿는다.
    "institution.reserve_price_count": (_Probe("shipped", "16"),),
    "institution.draw_count": (_Probe("shipped", "5"),),
    # 하한율 개연 밴드 — 밴드 밖이면 공고가 제외된다.
    "floor.rate_band_low": (_Probe("shipped", "0.9"),),
    "floor.rate_band_high": (_Probe("shipped", "0.4975"),),
    # 제외 규칙 상수 — 첫 공고 차수가 어긋나면 전량이 ③ 으로 빠진다.
    "exclusion.first_notice_ordinal": (_Probe("shipped", "1"),),
    # 시행일 셋 — 업무마다 다른 자리에서 읽힌다(`rules.effective_date_for`).
    "effective.service": (_Probe("shipped", '"2026-07-01"'),),
    "effective.construction": (_Probe("mixed", '"2026-07-01"'),),
    "effective.goods": (_Probe("mixed", '"2026-07-01"'),),
    # P-4 적합도 — 어긋나면 판정 대신 멈춤이다.
    "fit.alpha": (_Probe("shipped", "0.999"),),
    "fit.min_sample_count": (_Probe("shipped", "9999"),),
    "fit.max_bin_ratio_deviation": (_Probe("shipped", "0.000001"),),
    # 표본 예산 상한 — 넘으면 판정 대신 멈춤이다.
    "sampling.max_total_calls": (_Probe("shipped", "100"),),
    # 전략 상수 중 **판정을 뒤집는** 셋 — 격자 수는 S4 의 투찰금액을, 경쟁자 표본 하한은
    # S4 의 기권을 만든다(기권은 「부적격이고 못 이겼다」라 승률이 0 이 되고, 「못 쟀다」의
    # 사유가 seed 불안정에서 검정력 미달로 옮겨간다 — 사유를 담는 투영이라 보인다).
    "strategy.s4_grid_size": (_Probe("shipped", "42"),),
    "strategy.s4_min_competitor_samples": (_Probe("shipped", "100000"),),
    # 몬테카를로 반복 수 — 난수 흐름이 달라져 S4 의 투찰금액이 바뀌고, 그 승패가
    # seed 안정성을 뒤집는다(사유가 seed 불안정 -> 검정력 미달로 옮겨간다).
    "strategy.s4_iteration_count": (_Probe("shipped", "101"),),
    # seed 다섯 — **전부** 전략에 닿아야 seed 안정성 판정이 성립한다.
    "stability_seeds.0": (_Probe("seeded", "999001"),),
    "stability_seeds.1": (_Probe("seeded", "999002"),),
    "stability_seeds.2": (_Probe("seeded", "999003"),),
    "stability_seeds.3": (_Probe("seeded", "999004"),),
    "stability_seeds.4": (_Probe("seeded", "999005"),),
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
    # S1 의 공사 전용 offset — 투찰금액과 적격 여부를 바꾸지만, 이 판에서 S1 의 결말은
    # 양쪽 모두 「못 이겼다」다(바뀌는 것은 승률·bp 사분위 같은 산출 칸이다).
    "strategy.s1_offset_bp": _Probe("mixed", "900.0"),
    # 공사의 **두 번째 실격선** 비율 — 1 에 가깝게 올리면 순공사원가선이 하한가 위로
    # 올라가 적격 투찰자 집합과 최저 적격 금액이 바뀐다. 결말은 그대로다.
    "floor.pure_construction_cost_ratio": _Probe("mixed", "0.999"),
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

_MULTI_SITE_READS: Final[dict[str, tuple[str, ...]]] = {
    # 착수 AST 전수에서 **읽는 자리가 둘 이상**인 값 전부와 그 자리들. 자리마다 따로
    # 단언하는 것은 판정 입력 셋이고(아래 전용 test 셋), 나머지 다섯의 둘째·셋째 자리는
    # 사유와 함께 등재한다 — 등재된 사유가 틀리면 이 목록이 낡는다.
    "verdict.primary_hypothesis_count": (
        "Bonferroni 분모",
        "job 조립의 주 가설 수 대조",
    ),
    "verdict.min_window_count": (
        "창 계획 뒤의 멈춤 판정",
        "「못 쟀다」의 창 부족 갈래",
    ),
    "verdict.min_window_rows": ("최소 필요 표본 결정식", "창 제외의 표본 하한"),
    "institution.reserve_price_count": (
        "제외 ⑮ 예비가격 수",
        "적합도 기준 분포",
        "S4 분포",
    ),
    "institution.draw_count": ("제외 ⑮ 추첨 수", "적합도 기준 분포", "S4 분포"),
    "floor.pure_construction_cost_ratio": ("제외 단계의 순공사원가선", "S4 의 실격선"),
    "sampling.list_call_count": ("호출 합계", "판정문 공시(메아리)"),
    "sampling.max_total_calls": ("예산 초과 판정", "표본 기록", "판정문 공시(메아리)"),
}

_SITE_ASSERTED: Final[frozenset[str]] = frozenset(
    {
        "verdict.primary_hypothesis_count",
        "verdict.min_window_count",
        "verdict.min_window_rows",
    }
)
"""자리마다 **따로** 단언하는 값들 — 판정식이 읽는 값이라 한 자리만 상수가 되면 다른
자리가 움직여 「바뀌었다」가 된다. 전용 test 셋이 자리를 가른다."""

_SITE_NOT_ASSERTED: Final[dict[str, str]] = {
    # 둘째·셋째 자리를 **따로** 단언하지 않는 다중 자리 값과 그 사유. `_SITE_ASSERTED` 와
    # 합치면 `_MULTI_SITE_READS` 와 등식이다 — 새 다중 자리 값이 생기면 둘 중 하나에
    # 등재해야 하고, 안 하면 `test_multi_site_reads_are_enumerated_from_the_kickoff_census`
    # 가 RED 다.
    "institution.reserve_price_count": (
        "세 자리(제외 ⑮ · 적합도 기준 분포 · S4 분포)가 **같은 판에서 함께 돈다** —"
        " 예비가격 수를 바꾸면 ⑮ 가 먼저 전량을 제외해 뒤의 둘에 도달하지 못하므로,"
        " 자리별 판을 지으려면 제외 규칙을 우회해야 한다(그 자체가 범위 밖)"
    ),
    "institution.draw_count": "같은 이유 — 추첨 수도 ⑮ 가 먼저 본다",
    "floor.pure_construction_cost_ratio": (
        "두 자리(제외 단계의 순공사원가선 · S4 의 실격선)가 같은 공사 행에서 함께 돈다 —"
        " 제외가 먼저 발화하면 S4 가 그 행을 보지 못한다"
    ),
    "sampling.list_call_count": "둘째 자리가 판정문 공시(메아리)라 투영이 지운다",
    "sampling.max_total_calls": "둘째·셋째 자리가 표본 기록과 판정문 공시(메아리)다",
}


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
        if name not in self.boards:
            self.boards[name] = _BOARD_BUILDERS[name](self.inference)
        return self.boards[name]

    def values(self, name: str, **changes: str) -> dict[str, str]:
        values = _flat_policy(_SHIPPED_BACKTEST_POLICY)
        values.update(_BASE_OVERRIDES)
        values.update(self.board(name).overrides)
        values.update(changes)
        return values

    def verdict(self, name: str, **changes: str) -> bytes:
        token = json.dumps(changes, sort_keys=True)
        if (name, token) in self.verdicts:
            return self.verdicts[(name, token)]
        board = self.board(name)
        values = self.values(name, **changes)
        slug = f"{name}-{abs(hash(token)):x}"
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
        self.verdicts[(name, token)] = canonical_verdict_bytes(outcome)
        return self.verdicts[(name, token)]


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
            assert probe.board in _BOARD_BUILDERS, f"{key}: 모르는 판 {probe.board}"
    for key, probe in _OUTPUT_INPUTS.items():
        assert probe.board in _BOARD_BUILDERS, f"{key}: 모르는 판 {probe.board}"
    for key, reason in _UNREAD.items():
        assert reason.strip(), f"{key} 의 사유가 비어 있다"


def test_multi_site_reads_are_enumerated_from_the_kickoff_census() -> None:
    """D-6G2a-6 — 읽는 자리가 둘 이상인 값은 **전수 목록**으로 둔다.

    한 자리만 움직여도 「바뀌었다」가 되면 다른 자리의 상수는 보이지 않는다. 자리마다
    따로 단언하는 셋은 전용 test 가 있고(아래), 나머지는 사유와 함께 등재한다."""
    keys = set(_flat_policy(_SHIPPED_BACKTEST_POLICY))
    assert set(_MULTI_SITE_READS) <= keys, sorted(set(_MULTI_SITE_READS) - keys)
    for key, sites in _MULTI_SITE_READS.items():
        assert len(sites) >= 2, f"{key} 의 자리가 둘 미만이다: {sites}"
        assert len(set(sites)) == len(sites), f"{key} 의 자리 이름이 중복이다: {sites}"
    overlap = _SITE_ASSERTED & set(_SITE_NOT_ASSERTED)
    assert not overlap, f"자리별 단언 여부가 양쪽에 등재됐다: {sorted(overlap)}"
    assert _SITE_ASSERTED | set(_SITE_NOT_ASSERTED) == set(_MULTI_SITE_READS), (
        "다중 자리 값마다 「자리별로 단언한다」 또는 「사유와 함께 안 한다」 중 하나여야 "
        f"한다 — 빠진 값: "
        f"{sorted(set(_MULTI_SITE_READS) - _SITE_ASSERTED - set(_SITE_NOT_ASSERTED))}"
    )
    for key, reason in _SITE_NOT_ASSERTED.items():
        assert reason.strip(), f"{key} 의 사유가 비어 있다"


@pytest.mark.parametrize("key", sorted(_JUDGEMENT_INPUTS))
def test_judgement_inputs_flip_the_decision(key: str, harness: _Harness) -> None:
    """ⓐ — 판정 입력은 **결말**을 바꾼다(D-6G2a-3).

    극단으로 밀면 정직한 코드에서는 결말이 뒤집힌다. 그 자리에 코드에 박힌 수가
    닿아 있으면 정책을 아무리 밀어도 결말이 그대로라 RED 다 — 숨긴 수의 **형태**와
    무관하게 잡힌다(리터럴 게이트가 형태로 못 잡는 자리를 이 층이 받는다)."""
    for probe in _JUDGEMENT_INPUTS[key]:
        baseline = _decision_view(harness.verdict(probe.board))
        moved = _decision_view(harness.verdict(probe.board, **{key: probe.value}))
        assert moved != baseline, (
            f"{key} 를 판 {probe.board} 에서 {probe.value} 로 밀었는데 **결말이 "
            f"그대로다** — 그 자리가 정책을 읽지 않는다(코드에 박힌 수가 판정에 닿았다). "
            f"결말: {baseline}"
        )


@pytest.mark.parametrize("key", sorted(_OUTPUT_INPUTS))
def test_output_inputs_move_a_non_echo_field_without_flipping(
    key: str, harness: _Harness
) -> None:
    """ⓑ — 산출 입력은 결말을 **그대로 두고** 판정문의 비메아리 칸을 바꾼다(D-6G2a-3).

    두 단언을 함께 둔다: 결말이 바뀌면 그 값은 판정 입력이라 부류가 틀렸고, 판정문이
    그대로면 그 자리가 정책을 읽지 않는다. 어느 쪽이든 표가 낡았다는 뜻이다."""
    probe = _OUTPUT_INPUTS[key]
    before = harness.values(probe.board)[key]
    echoed = _echoed_forms(before, probe.value)
    baseline = harness.verdict(probe.board)
    moved = harness.verdict(probe.board, **{key: probe.value})
    assert _decision_view(moved) == _decision_view(baseline), (
        f"{key} 가 결말을 바꿨다 — 산출 입력이 아니라 **판정 입력**이다(부류를 옮겨라)"
    )
    assert _document_view(moved, echoed) != _document_view(baseline, echoed), (
        f"{key} 를 {before} -> {probe.value} 로 바꿨는데 판정문이 그대로다 — 그 자리가 "
        "정책을 읽지 않는다(메아리를 뺀 투영에서 아무 칸도 움직이지 않았다)"
    )


@pytest.mark.parametrize("key", sorted(_UNREAD))
def test_unread_values_change_nothing(key: str, harness: _Harness) -> None:
    """ⓒ — 판독 밖 값은 **아무것도 바꾸지 않는다**(D-6G2a-3).

    목록이 낡지 않게 하는 자리다: 등재된 값이 무언가를 움직이기 시작하면 여기서 RED 가
    되고, 그때 부류를 ⓐ 나 ⓑ 로 옮겨야 한다. 「못 잰다」는 선언이 아니라 「쓰이지
    않는다」는 측정이다."""
    values = harness.values("shipped")
    moved_value = _perturb(key, values[key])
    echoed = _echoed_forms(values[key], moved_value)
    baseline = harness.verdict("shipped")
    moved = harness.verdict("shipped", **{key: moved_value})
    assert _document_view(moved, echoed) == _document_view(baseline, echoed), (
        f"{key} 가 판정문을 움직였다 — 「판독 밖」 등재가 틀렸다. 사유: {_UNREAD[key]}"
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


def test_min_window_count_is_read_at_both_sites(harness: _Harness) -> None:
    """D-6G2a-6 — `verdict.min_window_count` 는 **두 자리**에서 읽힌다.

    자리 ① 창 계획 뒤의 멈춤 판정(`run`) — 선택된 창이 최소 수에 못 미치면 판정 대신 멈춤.
    자리 ② 「못 쟀다」의 창 부족 갈래(`verdict`) — 그 자리는 ① 이 먼저 멈추므로 전체 실행으로
    도달할 수 없다. 그래서 판정 함수를 **직접** 불러 잰다: 한 자리만 상수로 바뀌어도
    다른 자리가 움직여 「바뀌었다」가 되는 것을 막는다."""
    stopped = json.loads(harness.verdict("pass", **{"verdict.min_window_count": "4"}))
    assert stopped.get("stopped") == "INSUFFICIENT_WINDOWS", stopped.get("stopped")

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
    assert {
        item["reason"]
        for item in json.loads(harness.verdict("window-rows"))["excluded_windows"]
    } <= {"NO_HISTORY", "INSUFFICIENT_ROWS"}

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
        echoed = _echoed_forms(base_values[key], moved[key])
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
        moved_view = _document_view(
            _verdict_bytes(snapshot, strategies, policy), echoed
        )
        if moved_view == _document_view(baseline_bytes, echoed):
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
