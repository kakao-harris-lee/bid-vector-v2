"""M6/6G 합성 스냅숏 fixture 생성기(비식별). **실 데이터를 쓰지 않는다** — 공고 키는
지어낸 라벨의 sha256 이고 금액·날짜는 전부 합성이다(`data-extract.md` §7 — 실 스냅숏은
저장소 밖, test fixture 는 합성).

제도 형태는 지킨다: 예가 범위를 15구간으로 나눠 구간마다 균등 난수 하나를 뽑고 그중
무작위 4개의 평균을 예정가격으로 쓴다 — 그래야 P-4 적합도 검정이 통과하고, 그 뒤 판정
경로 전체가 돈다(적합도에서 멈추면 재현 test 가 재는 것이 없다).

창 배치(정책 `window.days=7`, `embargo_days=7` 기준):

| 블록 | 개찰 주 | 공고 수 | 이 fixture 에서의 자리 |
|---|---|---|---|
| W0 | 2026-06-15~21 | 30 | 이력 전용(자기 이력이 없어 `NO_HISTORY` 로 빠진다) |
| W1 | 2026-06-22~28 | 0 | 빈 창(`INSUFFICIENT_ROWS`) — 제외가 기록되는지 본다 |
| W2~W4 | 06-29~, 07-06~, 07-13~ | 30씩 | 채점 창 셋 |
"""

from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from random import Random
from typing import Any

FIXTURE_DIR_NAME = "backtest-snapshot"
_FIXTURE_SEED = 20260927
_BASE_AMOUNT = 1_000_000_000.0
_RESERVE_BIN_COUNT = 15
_DRAW_COUNT = 4
_HALF_WIDTH = 0.02
_NOTICES_PER_BLOCK = 30
_BIDDERS_PER_NOTICE = 6
_OPENING_LAG_DAYS = 14
# 공고일은 개찰일 14일 전이다 — 블록 시작을 06-15 로 잡아야 첫 블록의 공고일(06-01)이
# 일반용역 하한율 시행일(2026-05-26) 뒤에 온다(제외 ⑬). 블록 사이에 한 주를 비워
# 「빈 창」이 제외 사유와 함께 기록되는지도 함께 잰다.
_BLOCK_STARTS = (
    date(2026, 6, 15),
    date(2026, 6, 29),
    date(2026, 7, 6),
    date(2026, 7, 13),
)


def _reserve_draw(
    rng: Random, *, half_width: float = _HALF_WIDTH
) -> tuple[float, list[float], list[int]]:
    """예비가격 15개와 추첨 번호 4개, 그 평균인 예정가격 — 제도 그대로.

    `half_width` 는 예가 범위 반폭이다(기본값은 기존 fixture 의 ±2%). 적합도 검정은
    사정률을 **범위 안 위치로 정규화**해서 보므로(`fit.normalized_assessment_ratios`)
    폭이 달라도 분포의 모양은 같다 — 넓은 범위 공고를 섞는 판이 그 사실에 기댄다."""
    low = _BASE_AMOUNT * (1.0 - half_width)
    step = _BASE_AMOUNT * 2.0 * half_width / _RESERVE_BIN_COUNT
    prices = [
        low + step * index + rng.random() * step for index in range(_RESERVE_BIN_COUNT)
    ]
    drawn = sorted(rng.sample(range(1, _RESERVE_BIN_COUNT + 1), _DRAW_COUNT))
    planned = sum(prices[number - 1] for number in drawn) / _DRAW_COUNT
    return planned, prices, drawn


FLOOR_RATE = 0.87995
"""이 생성기가 쓰는 낙찰하한율 — 정책의 개연 밴드(`floor.rate_band_*`) 안쪽이다."""

# 업무별 낙찰방법명 — 제외 규칙의 **적격심사 표지**를 만족하고 소액수의·중소기업자간·
# 선박 표지는 피한다(`rules._QUALIFICATION_SCREENING_MARKERS` 와 그 앞의 셋).
_METHOD_NAMES: dict[str, str] = {
    "SERVICE": "적격심사제-추정가격 2억원 미만인 용역",
    "CONSTRUCTION": "적격심사제-추정가격 100억원 미만 공사",
    "GOODS": "적격심사제-추정가격 2억원 미만인 물품",
}
# 공사 행의 순공사원가 — 기초금액 대비 비율. `0.89` 는 **배제 비율의 경계를 가로지르도록**
# 고른 값이다: 출하 비율(0.98)에서는 순공사원가선(`0.89 * 사정률 * 비율`)이 하한가
# (`사정률 * 0.87995`)보다 **아래**라 묶이지 않고, 비율을 1 에 가깝게 올리면 **위**로
# 올라가 적격 투찰자 집합이 바뀐다 — 그래서 그 값이 판정 경로에 닿는지 거동으로 잴 수 있다.
_PURE_COST_RATIO_OF_BASE = 0.89


def _row(
    label: str,
    opened: date,
    rng: Random,
    *,
    category: str = "SERVICE",
    lowest_bid_rate: float | None = None,
    half_width: float = _HALF_WIDTH,
) -> dict[str, Any]:
    """합성 행 하나. 기본값은 **기존 fixture 그대로**다(`build_rows()` 의 바이트 불변).

    `lowest_bid_rate` 를 주면 투찰금액을 **기초금액 대비 고정 비율**로 둔다 — 그러면
    「어떤 투찰률이 그 공고를 이기는가」가 판에서 정해지므로, 전략 쪽에서 승패를 계획할 수
    있다(경계 위의 판, D-6G2a-4). 주지 않으면 하한가 주변 난수(기존 모양)다.
    둘 다 같은 난수 흐름을 쓰므로 호출 순서가 바이트를 정한다."""
    planned, prices, drawn = _reserve_draw(rng, half_width=half_width)
    floor_price = planned * FLOOR_RATE
    if lowest_bid_rate is None:
        amounts = sorted(
            floor_price * (1.0 + rng.uniform(-0.004, 0.02))
            for _ in range(_BIDDERS_PER_NOTICE)
        )
    else:
        # 최저가는 **유일**해야 한다 — 동값이 둘이면 `TIED_LOWEST` 로 제외된다.
        lowest = float(math.ceil(_BASE_AMOUNT * lowest_bid_rate))
        amounts = [lowest + 1000.0 * index for index in range(_BIDDERS_PER_NOTICE)]
    noticed = opened - timedelta(days=_OPENING_LAG_DAYS)
    construction = category == "CONSTRUCTION"
    return {
        "notice": {
            "notice_key_hash": hashlib.sha256(label.encode("utf-8")).hexdigest(),
            "category": category,
            "noticed_on": noticed.isoformat(),
            "bid_close_at": f"{(opened - timedelta(days=1)).isoformat()}T10:00:00+09:00",
            "base_amount": int(_BASE_AMOUNT),
            "base_amount_disclosed_at": (
                f"{(opened - timedelta(days=_OPENING_LAG_DAYS - 2)).isoformat()}"
                "T09:00:00+09:00"
            ),
            "floor_rate": FLOOR_RATE,
            "reserve_range_begin_rate": -half_width,
            "reserve_range_end_rate": half_width,
            "a_value": None,
            "successful_bid_method_code": "낙030001",
            "successful_bid_method_name": _METHOD_NAMES[category],
            "prearranged_price_decision_method": "복수예가",
            "notice_ordinal": 0,
            "procurement_class_code": "0600",
            "demand_agency_code": "A0001",
            # 공사는 `bidPrceCalclAYn` 이 정본이고 **거짓이면 A 값 공고가 아니라 A = 0** 이다
            # (`rules.resolve_a_value`) — 판정 불가(`None`)면 제외되므로 반드시 채운다.
            "bid_price_formula_a_applicable": False if construction else None,
            "pure_construction_cost": (
                int(_BASE_AMOUNT * _PURE_COST_RATIO_OF_BASE) if construction else None
            ),
            "has_award_method_application_standard": True,
            "has_application_basis_content": False,
        },
        "outcome": {
            "opened_on": opened.isoformat(),
            "planned_price": int(planned),
            "progress_division": "일반",
            "opening_base_amount": int(_BASE_AMOUNT),
            "reserve_prices": [int(price) for price in prices],
            "drawn_serial_numbers": drawn,
            "participant_count": _BIDDERS_PER_NOTICE,
            "bidder_rows": [
                {"ordinal": index + 1, "rank": index + 1, "amount": int(amount)}
                for index, amount in enumerate(amounts)
            ],
        },
    }


# 행이 되지 못한 표본 둘 — 상세를 못 받은 것과 공고 canonical 이 없던 것.
# 해시는 결정적으로 만든다(같은 seed 면 같은 바이트).
_MISSING_DETAIL_KEY = hashlib.sha256(b"m6-6g-sampled-without-detail").hexdigest()
_MISSING_NOTICE_KEY = hashlib.sha256(b"m6-6g-sampled-without-notice").hexdigest()
_INCOMPLETE_AXIS_KEY = hashlib.sha256(b"m6-6g-incomplete-axis").hexdigest()


def build_rows() -> list[dict[str, Any]]:
    """결정적 생성 — 같은 seed 면 같은 바이트. 커밋된 fixture 와 대조된다."""
    rng = Random(_FIXTURE_SEED)
    rows: list[dict[str, Any]] = []
    for block, start in enumerate(_BLOCK_STARTS):
        for index in range(_NOTICES_PER_BLOCK):
            opened = start + timedelta(days=index % 5)
            rows.append(_row(f"m6-6g-synthetic-{block}-{index}", opened, rng))
    return rows


def build_files() -> tuple[bytes, bytes, bytes]:
    rows = build_rows()
    rows_bytes = (
        "\n".join(json.dumps(row, sort_keys=True, ensure_ascii=False) for row in rows)
        + "\n"
    ).encode("utf-8")
    # v4 — 표본 목록이 파일이다. **일부러 「표본 != 행」으로 둔다**: 표본 둘이 행이
    # 되지 못한 판이라야 `sample_size`(행+2) · `notice_observed_count`(행+1) · 행 수가
    # 셋 다 다른 값이 되고, 판정 JSON 이 분모를 어디서 가져오는지 test 가 가를 수
    # 있다. 셋이 같은 판에서는 분모를 무엇으로 적든 같은 수가 나와 아무것도 잠기지
    # 않는다 — 왕복 golden 이 10/10/0/0 이던 동안 겪은 것과 같은 함정이다.
    extra = (_MISSING_DETAIL_KEY, _MISSING_NOTICE_KEY, _INCOMPLETE_AXIS_KEY)
    listing = (
        "\n".join(
            f"{key}\tSERVICE\t2026-W25"
            for key in sorted(
                [row["notice"]["notice_key_hash"] for row in rows] + list(extra)
            )
        )
        + "\n"
    ).encode("utf-8")
    manifest = {
        "schema_version": "snapshot-v5",
        "snapshot_id": "m6-6g-synthetic-v1",
        "row_count": len(rows),
        # 기간은 **행들의 개찰일 범위**다 — 판독기가 재계산해 대조하므로 블록 경계를
        # 적으면 거부된다(D-6G-32).
        "period_start": min(row["outcome"]["opened_on"] for row in rows),
        "period_end": max(row["outcome"]["opened_on"] for row in rows),
        "rows_sha256": hashlib.sha256(rows_bytes).hexdigest(),
        # v4 — 표본 목록 **파일 바이트**의 해시다(행에서 재계산한 값이 아니다).
        # v3 의 재계산 대조는 원형이라 성립하지 않았다 — D-6G-39.
        "sample_list_sha256": hashlib.sha256(listing).hexdigest(),
        "sample_size": len(rows) + len(extra),
        "sampled_without_detail": 1,
        "sampled_without_notice": 1,
        # v5 — 축이 반쪽인 표본도 하나 둔다. 0 만 지나가는 fixture 는 그 항을 검사하지
        # 않는다(왕복 golden 이 10/10/0/0 이던 때와 같은 함정).
        "incomplete_axis": 1,
        # v5 — 확정 범위(D-6G-66). 이 fixture 는 층도 행도 용역 하나라 범위도 하나다.
        # 문턱이 이 **수**로 정해지므로, 둘이 갈리는 판은 전용 test 가 따로 짓는다.
        "sample_scope_divisions": ["SERVICE"],
    }
    return (
        json.dumps(manifest, sort_keys=True, ensure_ascii=False).encode("utf-8"),
        rows_bytes,
        listing,
    )


def write_fixture(directory: Path) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    manifest_bytes, rows_bytes, sample_list = build_files()
    (directory / "manifest.json").write_bytes(manifest_bytes)
    (directory / "rows.jsonl").write_bytes(rows_bytes)
    (directory / "sample-list.tsv").write_bytes(sample_list)
    return directory


def fixture_dir() -> Path:
    return Path(__file__).resolve().parent / "fixtures" / FIXTURE_DIR_NAME


# ── 경계 위의 판 (M6/6G-2a, D-6G2a-4) ────────────────────────────────────────
# 위의 `build_rows()` 는 **커밋된 fixture 와 대조되는 판** 이라 바이트가 고정이다. 아래는
# 같은 행 생성기(`_row`)로 **판정 입력마다 경계를 올라타는 판**을 짓는다 — 생성기를 하나 더
# 만들지 않는다(6G r5 「manifest 생성기 둘」 제한을 늘리지 않는다).
#
# 판의 승패는 **전략이 아니라 판이 정한다**: 공고마다 적격 최저 투찰금액을 기초금액 대비
# `_GATE_BID_RATE` 로 고정해 두면, 투찰률 `_WINNING_BID_RATE` 는 그 공고를 이기고
# `_LOSING_BID_RATE` 는 진다. 그래서 (기준선 승/후보 승) 2x2 분할표를 공고 단위로 지정할 수
# 있고, 그것이 승률·불일치 쌍·p 값·필요 표본 수를 모두 정한다.
_GATE_BID_RATE = 0.9050
_WINNING_BID_RATE = 0.9000
_LOSING_BID_RATE = 0.9100
_INELIGIBLE_BID_RATE = 0.8000
"""하한가(사정률 x 0.87995, 최대 약 0.898)보다 **확실히 낮은** 투찰률 — 실격을 만든다.
비열등 한계가 판정에 닿는지는 실격률 차이로만 잴 수 있다."""

WINNING_BID_RATE = _WINNING_BID_RATE
LOSING_BID_RATE = _LOSING_BID_RATE
INELIGIBLE_BID_RATE = _INELIGIBLE_BID_RATE

_BOARD_SEED = 20261001
_BOARD_FIRST_BLOCK = date(2026, 6, 15)
_WIDE_HALF_WIDTH = 0.03
"""지방 계열로 알려진 예가 범위 반폭(±3%) — `sensitivity.wide_reserve_half_width`(0.025)
보다 넓어서 그 값이 판별 표본을 실제로 걸러낸다."""


@dataclass(frozen=True)
class BoardWindow:
    """채점 창 하나의 2x2 분할표. 네 수가 승률·불일치 쌍·p 값을 전부 정한다.

    `candidate_ineligible` 은 **둘 다 지는** 공고인데 후보만 하한가 아래로 투찰한 자리다 —
    불일치 쌍을 건드리지 않고 실격률 차이만 올린다(비열등 한계의 경계)."""

    candidate_only: int
    baseline_only: int
    both: int
    neither: int
    candidate_ineligible: int = 0

    @property
    def notice_count(self) -> int:
        return (
            self.candidate_only
            + self.baseline_only
            + self.both
            + self.neither
            + self.candidate_ineligible
        )


@dataclass(frozen=True)
class BoardSpec:
    """판 하나의 명세.

    `history` 는 첫 블록에 두는 공고 수다 — 창 하나는 **자기보다 embargo 만큼 앞서 개찰된
    공고**가 있어야 선택되므로(`windows.plan_backtest_windows`), 첫 블록이 그 원천이다.
    `pad` 는 같은 블록에 더 얹는 행이고, **창 규칙과 최소 표본 결정식을 가르는** 데 쓴다:
    표본 하한은 스냅숏 행 **전부**를 세고 창 규칙은 창 안의 공고만 세므로, 창 밖 행을 늘리면
    한쪽만 묶이는 판이 된다. `pad_categories` 는 그 행들의 업무를 돌려 가며 붙인다."""

    windows: tuple[BoardWindow, ...]
    history: int = 20
    pad: int = 0
    pad_categories: tuple[str, ...] = ("SERVICE",)
    wide_reserve_pad: int = 0
    """예가 범위가 넓은(±3%) 공고 수 — 판별 표본 걸러내기가 실제로 지울 행."""


@dataclass(frozen=True)
class BoardRows:
    """판의 행들과, 그 판에서 두 전략이 따를 **계획**(공고 해시 -> 투찰률)."""

    payloads: tuple[dict[str, Any], ...]
    baseline_plan: dict[str, float]
    candidate_plan: dict[str, float]


def _plan_pairs(window: BoardWindow) -> list[tuple[float, float]]:
    win, lose = _WINNING_BID_RATE, _LOSING_BID_RATE
    return (
        [(lose, win)] * window.candidate_only
        + [(win, lose)] * window.baseline_only
        + [(win, win)] * window.both
        + [(lose, lose)] * window.neither
        + [(lose, _INELIGIBLE_BID_RATE)] * window.candidate_ineligible
    )


def build_board_rows(spec: BoardSpec) -> BoardRows:
    """명세 하나에서 행들과 계획을 짓는다 — 같은 명세면 같은 바이트(고정 seed).

    블록 배치는 `build_rows()` 와 같다: 첫 블록이 이력, 그 다음 주는 비우고, 그 뒤 주마다
    채점 창 하나. 빈 주를 두는 이유는 embargo(7일)가 바로 앞 주의 이력을 끊기 때문이다."""
    rng = Random(_BOARD_SEED)
    payloads: list[dict[str, Any]] = []
    baseline_plan: dict[str, float] = {}
    candidate_plan: dict[str, float] = {}

    def add(
        label: str,
        opened: date,
        baseline_rate: float,
        candidate_rate: float,
        *,
        category: str = "SERVICE",
        half_width: float = _HALF_WIDTH,
    ) -> None:
        payload = _row(
            label,
            opened,
            rng,
            category=category,
            lowest_bid_rate=_GATE_BID_RATE,
            half_width=half_width,
        )
        key = payload["notice"]["notice_key_hash"]
        baseline_plan[key] = baseline_rate
        candidate_plan[key] = candidate_rate
        payloads.append(payload)

    win = _WINNING_BID_RATE
    for index in range(spec.history):
        add(
            f"board-history-{index}",
            _BOARD_FIRST_BLOCK + timedelta(days=index % 5),
            win,
            win,
        )
    for index in range(spec.pad):
        add(
            f"board-pad-{index}",
            _BOARD_FIRST_BLOCK + timedelta(days=index % 5),
            win,
            win,
            category=spec.pad_categories[index % len(spec.pad_categories)],
        )
    for index in range(spec.wide_reserve_pad):
        add(
            f"board-wide-{index}",
            _BOARD_FIRST_BLOCK + timedelta(days=index % 5),
            win,
            win,
            half_width=_WIDE_HALF_WIDTH,
        )
    for order, window in enumerate(spec.windows):
        start = _BOARD_FIRST_BLOCK + timedelta(days=14 + 7 * order)
        for index, (baseline_rate, candidate_rate) in enumerate(_plan_pairs(window)):
            add(
                f"board-w{order}-{index}",
                start + timedelta(days=index % 5),
                baseline_rate,
                candidate_rate,
            )
    return BoardRows(tuple(payloads), baseline_plan, candidate_plan)


def build_mixed_category_rows(
    *, per_category: int = 12, history: int = 20, windows: int = 3
) -> tuple[dict[str, Any], ...]:
    """업무 셋(공사·용역·물품)을 섞은 판 — 투찰금액은 **하한가 주변 난수**(기존 모양)다.

    출하 전략 다섯을 그대로 돌리는 판이라 승패를 계획하지 않는다. 이 판이 있어야 업무 축이
    공사·물품일 때만 도는 자리(시행일 셋 · 순공사원가 배제 비율 · 업무별 공고당 호출 수 ·
    S1 의 공사 전용 offset)가 **실제로 돈다** — 용역 하나뿐인 판에서는 그 값들이 읽히지
    않아 민감도를 잴 수 없다."""
    rng = Random(_BOARD_SEED)
    categories = ("CONSTRUCTION", "SERVICE", "GOODS")
    payloads: list[dict[str, Any]] = []
    for index in range(history):
        payloads.append(
            _row(
                f"mixed-history-{index}",
                _BOARD_FIRST_BLOCK + timedelta(days=index % 5),
                rng,
                category=categories[index % len(categories)],
            )
        )
    for order in range(windows):
        start = _BOARD_FIRST_BLOCK + timedelta(days=14 + 7 * order)
        for index in range(per_category * len(categories)):
            payloads.append(
                _row(
                    f"mixed-w{order}-{index}",
                    start + timedelta(days=index % 5),
                    rng,
                    category=categories[index % len(categories)],
                )
            )
    return tuple(payloads)


if __name__ == "__main__":  # pragma: no cover - 재생성용 진입점
    print(write_fixture(fixture_dir()))
