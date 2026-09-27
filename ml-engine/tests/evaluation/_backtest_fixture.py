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


def _reserve_draw(rng: Random) -> tuple[float, list[float], list[int]]:
    """예비가격 15개와 추첨 번호 4개, 그 평균인 예정가격 — 제도 그대로."""
    low = _BASE_AMOUNT * (1.0 - _HALF_WIDTH)
    step = _BASE_AMOUNT * 2.0 * _HALF_WIDTH / _RESERVE_BIN_COUNT
    prices = [
        low + step * index + rng.random() * step for index in range(_RESERVE_BIN_COUNT)
    ]
    drawn = sorted(rng.sample(range(1, _RESERVE_BIN_COUNT + 1), _DRAW_COUNT))
    planned = sum(prices[number - 1] for number in drawn) / _DRAW_COUNT
    return planned, prices, drawn


def _row(label: str, opened: date, rng: Random) -> dict[str, Any]:
    planned, prices, drawn = _reserve_draw(rng)
    floor_rate = 0.87995
    floor_price = planned * floor_rate
    amounts = sorted(
        floor_price * (1.0 + rng.uniform(-0.004, 0.02))
        for _ in range(_BIDDERS_PER_NOTICE)
    )
    noticed = opened - timedelta(days=_OPENING_LAG_DAYS)
    return {
        "notice": {
            "notice_key_hash": hashlib.sha256(label.encode("utf-8")).hexdigest(),
            "category": "SERVICE",
            "noticed_on": noticed.isoformat(),
            "bid_close_at": f"{(opened - timedelta(days=1)).isoformat()}T10:00:00+09:00",
            "base_amount": int(_BASE_AMOUNT),
            "base_amount_disclosed_at": (
                f"{(opened - timedelta(days=_OPENING_LAG_DAYS - 2)).isoformat()}"
                "T09:00:00+09:00"
            ),
            "floor_rate": floor_rate,
            "reserve_range_begin_rate": -_HALF_WIDTH,
            "reserve_range_end_rate": _HALF_WIDTH,
            "a_value": None,
            "successful_bid_method_code": "낙030001",
            "successful_bid_method_name": "적격심사제-추정가격 2억원 미만인 용역",
            "prearranged_price_decision_method": "복수예가",
            "notice_ordinal": 1,
            "progress_division": "일반",
            "procurement_class_code": "0600",
            "demand_agency_code": "A0001",
            "bid_price_formula_a_applicable": None,
            "pure_construction_cost": None,
            "award_method_application_standard": "적격심사 세부기준",
            "application_basis_content": None,
        },
        "outcome": {
            "opened_on": opened.isoformat(),
            "planned_price": int(planned),
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


def build_rows() -> list[dict[str, Any]]:
    """결정적 생성 — 같은 seed 면 같은 바이트. 커밋된 fixture 와 대조된다."""
    rng = Random(_FIXTURE_SEED)
    rows: list[dict[str, Any]] = []
    for block, start in enumerate(_BLOCK_STARTS):
        for index in range(_NOTICES_PER_BLOCK):
            opened = start + timedelta(days=index % 5)
            rows.append(_row(f"m6-6g-synthetic-{block}-{index}", opened, rng))
    return rows


def build_files() -> tuple[bytes, bytes]:
    rows = build_rows()
    rows_bytes = (
        "\n".join(json.dumps(row, sort_keys=True, ensure_ascii=False) for row in rows)
        + "\n"
    ).encode("utf-8")
    manifest = {
        "schema_version": "snapshot-v2",
        "snapshot_id": "m6-6g-synthetic-v1",
        "row_count": len(rows),
        "period_start": _BLOCK_STARTS[0].isoformat(),
        "period_end": (_BLOCK_STARTS[-1] + timedelta(days=6)).isoformat(),
        "rows_sha256": hashlib.sha256(rows_bytes).hexdigest(),
        "sample_list_sha256": hashlib.sha256(
            b"m6-6g-synthetic-sample-list"
        ).hexdigest(),
    }
    return (
        json.dumps(manifest, sort_keys=True, ensure_ascii=False).encode("utf-8"),
        rows_bytes,
    )


def write_fixture(directory: Path) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    manifest_bytes, rows_bytes = build_files()
    (directory / "manifest.json").write_bytes(manifest_bytes)
    (directory / "rows.jsonl").write_bytes(rows_bytes)
    return directory


def fixture_dir() -> Path:
    return Path(__file__).resolve().parent / "fixtures" / FIXTURE_DIR_NAME


if __name__ == "__main__":  # pragma: no cover - 재생성용 진입점
    print(write_fixture(fixture_dir()))
