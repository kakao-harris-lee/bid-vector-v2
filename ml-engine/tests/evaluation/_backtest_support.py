"""M6/6G test 지원 — 합성 스냅숏 행 조립기(비식별). 실 데이터를 쓰지 않는다.

여기서 만드는 값은 전부 지어낸 것이고 공고 식별자 자리에는 고정 해시 문자열을 넣는다
(`data-extract.md` §7 — 스냅숏은 저장소 밖, test fixture 는 합성)."""

from __future__ import annotations

import ast
import dataclasses
import hashlib
import json
import math
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.sample_list import BUSINESS_DIVISIONS
from ml_engine.evaluation.backtest.strategies import (
    BidAmount,
    StrategyInput,
    StrategyOutcome,
)
from tests.evaluation._backtest_fixture import (
    INELIGIBLE_BID_RATE,
    LOSING_BID_RATE,
    WINNING_BID_RATE,
)

_BASE_AMOUNT = 1_000_000_000
_FLOOR_RATE = 0.87745

SHIPPED_BACKTEST_POLICY_PATH = (
    Path(__file__).resolve().parents[2] / "policy" / "strategy-backtest-v1.yaml"
)


def notice_key_hash(label: str) -> str:
    return hashlib.sha256(label.encode("utf-8")).hexdigest()


def notice_payload(
    label: str = "n-1",
    *,
    category: str = "SERVICE",
    noticed_on: str | None = "2026-06-01",
    bid_close_at: str | None = "2026-06-10T10:00:00+09:00",
    base_amount: Any = _BASE_AMOUNT,
    floor_rate: float | None = _FLOOR_RATE,
    a_value: dict[str, Any] | None = None,
    successful_bid_method_code: str = "낙030001",
    successful_bid_method_name: str = "적격심사제-추정가격 2억원 미만인 용역",
    prearranged_price_decision_method: str = "복수예가",
    notice_ordinal: int = 0,
    procurement_class_code: str | None = None,
    demand_agency_code: str | None = "A0001",
    bid_price_formula_a_applicable: bool | None = None,
    has_award_method_application_standard: bool = True,
    has_application_basis_content: bool = False,
    base_amount_disclosed_at: str | None = "2026-06-05T09:00:00+09:00",
    reserve_range_begin_rate: float | None = -0.02,
    reserve_range_end_rate: float | None = 0.02,
    pure_construction_cost: int | None = None,
) -> dict[str, Any]:
    return {
        "notice_key_hash": notice_key_hash(label),
        "category": category,
        "noticed_on": noticed_on,
        "bid_close_at": bid_close_at,
        "base_amount": base_amount,
        "base_amount_disclosed_at": base_amount_disclosed_at,
        "floor_rate": floor_rate,
        "reserve_range_begin_rate": reserve_range_begin_rate,
        "reserve_range_end_rate": reserve_range_end_rate,
        "a_value": a_value,
        "successful_bid_method_code": successful_bid_method_code,
        "successful_bid_method_name": successful_bid_method_name,
        "prearranged_price_decision_method": prearranged_price_decision_method,
        "notice_ordinal": notice_ordinal,
        "procurement_class_code": procurement_class_code,
        "demand_agency_code": demand_agency_code,
        "bid_price_formula_a_applicable": bid_price_formula_a_applicable,
        "pure_construction_cost": pure_construction_cost,
        "has_award_method_application_standard": (
            has_award_method_application_standard
        ),
        "has_application_basis_content": has_application_basis_content,
    }


def reserve_prices(base_amount: float = _BASE_AMOUNT) -> list[float]:
    """±2% 를 15구간으로 나눈 구간 중앙값 — 합성이지만 제도 형태는 지킨다."""
    low = base_amount * 0.98
    step = base_amount * 0.04 / 15
    return [low + step * (index + 0.5) for index in range(15)]


def outcome_payload(
    *,
    opened_on: str | None = "2026-06-15",
    progress_division: str | None = None,
    planned_price: float | None = None,
    planned_price_null: bool = False,
    prices: list[float] | None = None,
    prices_null: bool = False,
    opening_base_amount_null: bool = False,
    opening_base_amount: int | None = None,
    drawn: list[int] | None = None,
    participant_count: int = 12,
    bidder_amounts: list[float] | None = None,
) -> dict[str, Any]:
    values = reserve_prices() if prices is None else prices
    drawn_numbers = [1, 4, 8, 12] if drawn is None else drawn
    if planned_price is None:
        planned_price = sum(values[number - 1] for number in drawn_numbers) / len(
            drawn_numbers
        )
    amounts = (
        [planned_price * rate for rate in (0.879, 0.881, 0.884, 0.890)]
        if bidder_amounts is None
        else bidder_amounts
    )
    return {
        "opened_on": opened_on,
        "planned_price": None if planned_price_null else int(planned_price),
        "progress_division": progress_division,
        "opening_base_amount": (
            None
            if opening_base_amount_null
            else int(
                _BASE_AMOUNT if opening_base_amount is None else opening_base_amount
            )
        ),
        "reserve_prices": None if prices_null else [int(value) for value in values],
        "drawn_serial_numbers": drawn_numbers,
        "participant_count": participant_count,
        "bidder_rows": [
            {"ordinal": index + 1, "rank": index + 1, "amount": int(amount)}
            for index, amount in enumerate(sorted(amounts))
        ],
    }


def row_payload(label: str = "n-1", **kwargs: Any) -> dict[str, Any]:
    notice_kwargs = {
        key: value for key, value in kwargs.items() if key.startswith("notice_")
    }
    outcome_kwargs = {
        key: value for key, value in kwargs.items() if key.startswith("outcome_")
    }
    return {
        "notice": notice_payload(
            label, **{key[len("notice_") :]: v for key, v in notice_kwargs.items()}
        ),
        "outcome": outcome_payload(
            **{key[len("outcome_") :]: v for key, v in outcome_kwargs.items()}
        ),
    }


def rows_bytes(payloads: list[dict[str, Any]]) -> bytes:
    return (
        "\n".join(json.dumps(payload, sort_keys=True) for payload in payloads) + "\n"
    ).encode("utf-8")


def sample_list_bytes(
    rows: bytes,
    *,
    extra_keys: tuple[str, ...] = (),
    division: str = "SERVICE",
    divisions: tuple[str, ...] = (),
) -> bytes:
    """`sample-list.tsv` 바이트(v4, 스키마 §2.1) — 헤더 없는 TSV, 해시 오름차순,
    끝 줄 개행 포함. `extra_keys` 로 **행이 없는 표본**(상세를 못 받았거나 공고
    canonical 이 없는 공고)을 넣어 진부분집합 상태를 만든다.

    `divisions` 를 주면 키 순서대로 **돌려 가며** 붙인다 — 업무 축이 여럿인 목록을
    만들어 최소 표본 결정식이 그 수를 어디서 세는지 가른다."""
    keys = sorted({*_row_keys(rows), *extra_keys})
    if divisions:
        lines = [
            f"{key}\t{divisions[index % len(divisions)]}\t2026-W25"
            for index, key in enumerate(keys)
        ]
    else:
        lines = [f"{key}\t{division}\t2026-W25" for key in keys]
    return ("\n".join(lines) + "\n").encode("utf-8") if lines else b""


def _row_keys(rows: bytes) -> list[str]:
    keys: list[str] = []
    for line in rows.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        try:
            keys.append(json.loads(line)["notice"]["notice_key_hash"])
        except (json.JSONDecodeError, KeyError, TypeError):
            # 일부러 깨뜨린 입력을 쓰는 test 가 있다 — 그 줄은 건너뛴다. 그런 입력은
            # 판독기가 표본 목록 대조보다 먼저 `MALFORMED_JSON` 으로 거부한다.
            continue
    return keys


def _opening_range(rows: bytes) -> tuple[str, str]:
    """행에서 계산한 개찰일 범위 — 판독기가 같은 식으로 재계산해 대조한다."""
    days: list[str] = []
    for line in rows.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        try:
            day = json.loads(line)["outcome"]["opened_on"]
        except (json.JSONDecodeError, KeyError, TypeError):
            continue
        if day:
            days.append(day)
    if not days:
        return ("2026-06-01", "2026-06-01")
    return (min(days), max(days))


def manifest_bytes(
    rows: bytes,
    *,
    snapshot_id: str = "snapshot-test-1",
    row_count: int | None = None,
    period_start: str | None = None,
    period_end: str | None = None,
    rows_sha256: str | None = None,
    sample_list_sha256: str | None = None,
    sample_list: bytes | None = None,
    sample_size: int | None = None,
    sampled_without_detail: int = 0,
    sampled_without_notice: int = 0,
    incomplete_axis: int = 0,
    sample_scope_divisions: tuple[str, ...] | None = None,
    schema_version: str = "snapshot-v5",
) -> bytes:
    """`sample_scope_divisions` 를 주지 않으면 **표본 목록에 나타난 업무**로 채운다 —
    범위와 표본이 같은 판이 기본이고, 둘을 **다르게** 두는 것은 그것을 재는 test 의
    몫이다(D-6G-66: 문턱이 어느 쪽에서 오는지는 둘이 다를 때만 갈린다)."""
    listing = sample_list_bytes(rows) if sample_list is None else sample_list
    payload = {
        "schema_version": schema_version,
        "snapshot_id": snapshot_id,
        "row_count": (
            len(rows.decode("utf-8").strip().splitlines())
            if row_count is None
            else row_count
        ),
        "period_start": (
            _opening_range(rows)[0] if period_start is None else period_start
        ),
        "period_end": _opening_range(rows)[1] if period_end is None else period_end,
        "rows_sha256": (
            hashlib.sha256(rows).hexdigest() if rows_sha256 is None else rows_sha256
        ),
        "sample_list_sha256": (
            hashlib.sha256(listing).hexdigest()
            if sample_list_sha256 is None
            else sample_list_sha256
        ),
        "sample_size": (
            len(_row_keys(rows))
            + sampled_without_detail
            + sampled_without_notice
            + incomplete_axis
            if sample_size is None
            else sample_size
        ),
        "sampled_without_detail": sampled_without_detail,
        "sampled_without_notice": sampled_without_notice,
        "incomplete_axis": incomplete_axis,
        "sample_scope_divisions": (
            sorted(_default_scope(listing, rows))
            if sample_scope_divisions is None
            else list(sample_scope_divisions)
        ),
    }
    return json.dumps(payload, sort_keys=True).encode("utf-8")


def _default_scope(listing: bytes, rows: bytes) -> set[str]:
    """기본 확정 범위 — **표본 목록의 층 축과 행의 업무를 합친 집합**.

    둘은 다른 축이지만(스키마 §2.1) 어휘가 같고, 판독은 **둘 다** 범위 안이기를
    요구한다(범위 밖 행은 채점에 들어가면서 업무 대표 공시에서 사라진다). 그러니
    기본값은 그 데이터를 실제로 덮는 집합이어야 한다 — 둘을 **다르게** 두는 것은
    그것을 재는 test 의 몫이다.

    일부러 깨뜨린 목록(어휘 밖 값·칸 수 위반)을 쓰는 test 가 있다 — 그 값을 범위 칸에
    옮겨 적으면 manifest 판독이 먼저 걸려 test 가 겨눈 자리를 못 본다. 어휘에 맞는
    값만 모으고, 하나도 없으면 판이 서는 기본값을 쓴다."""
    divisions = {
        columns[1]
        for columns in (
            line.split("\t")
            for line in listing.decode("utf-8", errors="replace").splitlines()
        )
        if len(columns) == len(("key", "division", "week"))
        and columns[1] in BUSINESS_DIVISIONS
    }
    for line in rows.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        try:
            category = json.loads(line)["notice"]["category"]
        except (json.JSONDecodeError, KeyError, TypeError):
            continue
        if category in BUSINESS_DIVISIONS:
            divisions.add(category)
    return divisions or {"SERVICE"}


def write_snapshot_dir(
    directory: Path, payloads: list[dict[str, Any]], **manifest_kwargs: Any
) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    rows = rows_bytes(payloads)
    (directory / "rows.jsonl").write_bytes(rows)
    (directory / "sample-list.tsv").write_bytes(sample_list_bytes(rows))
    (directory / "manifest.json").write_bytes(manifest_bytes(rows, **manifest_kwargs))
    return directory


@dataclass(frozen=True)
class PlannedBidStrategy:
    """**계획대로 투찰하는** test 전략(M6/6G-2a, D-6G2a-4) — 공고마다 투찰률 하나.

    출하 전략 다섯(S0·S1·S2 셋·S4)은 난수와 분포 엔진에 달려 있어 「이 창에서 후보가
    기준선을 몇 번 이긴다」를 지정할 수 없다. 그래서 **판정 입력의 경계를 올라타는 판**에는
    이 전략을 주입한다 — `StrategyLike` 는 Protocol 이고 전략은 조립 근이 넣는 것이라
    (`run` 의 층 경계) 출하 코드를 건드리지 않는다.

    **판정 입력을 읽는 자리는 전략이 아니다**: 일곱 값은 `verdict`·`run`·`windows`·
    `policy_values` 에서 읽히고 전략 모듈에서는 읽히지 않는다(착수 AST 전수). 그래서 이
    전략을 쓰는 것이 측정 범위를 줄이지 않는다. 전략 모듈에서만 읽히는 값
    (`strategy.*` · `institution.*` · `floor.pure_construction_cost_ratio`)은 출하 전략을
    그대로 돌리는 판에서 잰다.

    `seed_sensitive` 가 참이면 `seeds` 밖의 seed 로 불릴 때 승패를 뒤집는다 — 정책의
    seed 다섯이 **전부** 전략에 닿는지를 거동으로 잰다(하나만 닿으면 seed 안정성 판정이
    흔들리지 않아 그 자리의 상수가 보이지 않는다)."""

    name: str
    plan: Mapping[str, float]
    seeds: tuple[int, ...] = ()
    seed_sensitive: bool = False

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome:
        del policy  # 이 전략은 정책을 읽지 않는다 — 계획이 투찰률을 정한다.
        rate = self.plan[request.notice.notice_key_hash]
        if self.seed_sensitive and request.seed not in self.seeds:
            rate = _FLIPPED_RATES[rate]
        return BidAmount(float(math.ceil(request.base_amount * rate)))


_FLIPPED_RATES: dict[float, float] = {
    WINNING_BID_RATE: LOSING_BID_RATE,
    LOSING_BID_RATE: WINNING_BID_RATE,
    INELIGIBLE_BID_RATE: WINNING_BID_RATE,
}
"""seed 민감 전략이 계획 밖 seed 에서 쓰는 반대쪽 투찰률 — 승패가 뒤집히므로 seed 안정성
판정이 그 사실을 본다."""


# ── 정책 값 **쓰임 명단** (M6/6G-2a r1, D-6G2a-12) ──────────────────────────────
# 손으로 쓴 명단이 r1 의 막는 결함이었다: 「읽는 자리」를 셌더니 **읽은 값이 흘러가 쓰이는
# 자리**가 가려졌다. `alpha_for(...)` 가 한 번 읽은 유의수준은 검정력 계산과 판정식 **두
# 곳에서** 쓰이고, 창 최소 수는 창 계획·판정·**최소 표본식** 세 곳에서 쓰인다. 한 곳만
# 상수로 바꾼 변이가 전체 suite 를 지났다(verifier r1 H-1 · code-review r1 H-1·H-2).
#
# 그래서 명단을 **생성**한다. 아래 함수들은 출하 코드의 AST 와 **로드된 정책 객체**에서만
# 사실을 가져온다 — test 에 적은 목록이 없다. 새 쓰임이 생기면 삼중이 하나 늘고, 그것을
# 덮는 probe·변이 등재가 없으면 등식 test 가 붉어진다.

_POLICY_MODULE_ROOT = Path(__file__).resolve().parents[2] / "src" / "ml_engine"
_POLICY_VALUES_PATH = (
    _POLICY_MODULE_ROOT / "evaluation" / "backtest" / "policy_values.py"
)
_VERDICT_PATH_MODULES: tuple[Path, ...] = (
    *sorted((_POLICY_MODULE_ROOT / "evaluation" / "backtest").glob("*.py")),
    *sorted((_POLICY_MODULE_ROOT / "app").glob("backtest_*.py")),
)
"""명단의 대상 — 위협 모델이 「방어하는 것」으로 든 판정 경로 전부.

app 쪽도 **glob** 이다(code-review r2 M-3). 파일 하나를 적어 두면 선언한 범위(위협 모델의
`ml_engine.app.backtest_*`)와 기계가 보는 범위가 갈리고, 새 `app/backtest_*.py` 가 정책 값을
읽어도 명단에 오르지 않은 채 등식이 초록으로 남는다."""

_POLICY_ROOT_NAMES: frozenset[str] = frozenset({"policy", "backtest_policy"})
"""정책 객체 전체를 가리키는 지역 이름들. 속성 체인 `*.policy` 도 같이 본다."""


@dataclass(frozen=True)
class PolicyUse:
    """정책 값 하나가 **쓰이는 자리** 하나.

    `site` 는 계약(D-6G2a-12)이 든 단위 — `모듈.함수`. `consumer` 는 그 함수 안에서 값이
    흘러 들어가는 소비자 이름이고, 같은 함수에 쓰임이 둘일 때 그 둘을 가른다(적합도 seed 와
    seed 순회가 같은 함수에 있다 — 소비자 축이 없으면 한쪽 상수가 보이지 않는다)."""

    key: str
    site: str
    consumer: str


def policy_file_keys(path: Path | None = None) -> tuple[str, ...]:
    """평면 정책 파일의 키 순서 그대로 — 지어낸 목록이 아니다."""
    source = SHIPPED_BACKTEST_POLICY_PATH if path is None else path
    keys: list[str] = []
    for line in source.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            keys.append(line.split(":", 1)[0].strip())
    return tuple(keys)


def _group_classes(
    policy: StrategyBacktestPolicy,
) -> dict[str, tuple[str, frozenset[str]]]:
    """정책 객체의 그룹 속성 이름 -> (클래스 이름, 그 클래스의 필드 이름들)."""
    groups: dict[str, tuple[str, frozenset[str]]] = {}
    for info in dataclasses.fields(policy):
        value = getattr(policy, info.name)
        if dataclasses.is_dataclass(value) and not isinstance(value, type):
            groups[info.name] = (
                type(value).__name__,
                frozenset(inner.name for inner in dataclasses.fields(value)),
            )
    return groups


def _shared_prefix_length(left: str, right: str) -> int:
    count = 0
    for first, second in zip(left, right, strict=False):
        if first != second:
            break
        count += 1
    return count


def _leaf_keys(
    policy: StrategyBacktestPolicy, keys: tuple[str, ...]
) -> dict[tuple[str, str], frozenset[str]]:
    """(클래스 이름, 필드 이름) -> 그 필드가 담는 정책 키 집합. **정책 객체에서 도출**한다.

    같은 잎 이름을 두 그룹이 쓰면(`alpha` 가 판정과 적합도에 둘 다 있다) 키 접두와 **공통
    접두가 가장 긴** 속성으로 가른다 — 속성 이름이 접두의 복수형일 수 있어 접두 일치로는
    갈리지 않는다(`strategies` 는 `strategy` 로 시작하지 않는다)."""
    groups = _group_classes(policy)
    top = type(policy).__name__
    out: dict[tuple[str, str], set[str]] = {}
    for key in keys:
        if "." not in key:
            out.setdefault((top, key), set()).add(key)
            continue
        prefix, leaf = key.split(".", 1)
        holders = [
            (attr, cls) for attr, (cls, fields) in groups.items() if leaf in fields
        ]
        if not holders:
            # 평탄 인덱스 목록(`stability_seeds.N`)은 top-level tuple 필드다.
            out.setdefault((top, prefix), set()).add(key)
            continue
        if len(holders) > 1:
            holders.sort(
                key=lambda item: _shared_prefix_length(item[0], prefix), reverse=True
            )
        out.setdefault((holders[0][1], leaf), set()).add(key)
    return {name: frozenset(found) for name, found in out.items()}


def _providers(
    leaf_keys: dict[tuple[str, str], frozenset[str]],
) -> dict[tuple[str, str], frozenset[str]]:
    """(클래스, 메서드·프로퍼티) -> 그것이 읽는 정책 키 집합.

    `policy_values` 의 AST 에서 `self.<필드>` 를 모으고 `self.<다른 프로퍼티>` 를 전이
    닫힘으로 따라간다 — `alpha_for` 가 `primary_alpha` 를 거쳐 분모까지 읽는 것이 그래서
    명단에 잡힌다. 불변식 검사(`__post_init__`)는 쓰임이 아니므로 뺀다."""
    tree = ast.parse(_POLICY_VALUES_PATH.read_text(encoding="utf-8"))
    direct: dict[tuple[str, str], set[str]] = {}
    refs: dict[tuple[str, str], set[str]] = {}
    for holder in (node for node in ast.walk(tree) if isinstance(node, ast.ClassDef)):
        for method in (n for n in holder.body if isinstance(n, ast.FunctionDef)):
            if method.name == "__post_init__":
                continue
            found: set[str] = set()
            seen: set[str] = set()
            for node in ast.walk(method):
                if (
                    isinstance(node, ast.Attribute)
                    and isinstance(node.value, ast.Name)
                    and node.value.id == "self"
                ):
                    if (holder.name, node.attr) in leaf_keys:
                        found |= leaf_keys[(holder.name, node.attr)]
                    else:
                        seen.add(node.attr)
            direct[(holder.name, method.name)] = found
            refs[(holder.name, method.name)] = seen
    changed = True
    while changed:
        changed = False
        for name, referenced in refs.items():
            for ref in referenced:
                source = (name[0], ref)
                if source in direct and not direct[source] <= direct[name]:
                    direct[name] |= direct[source]
                    changed = True
    return {name: frozenset(found) for name, found in direct.items() if found}


def _parents(tree: ast.AST) -> dict[int, tuple[ast.AST, str]]:
    table: dict[int, tuple[ast.AST, str]] = {}
    for node in ast.walk(tree):
        for field, value in ast.iter_fields(node):
            items = value if isinstance(value, list) else [value]
            for item in items:
                if isinstance(item, ast.AST):
                    table[id(item)] = (node, field)
    return table


_CLIMBED_NODES = (
    ast.Subscript,
    ast.IfExp,
    ast.BinOp,
    ast.BoolOp,
    ast.UnaryOp,
    ast.Tuple,
    ast.comprehension,
    ast.JoinedStr,
    ast.FormattedValue,
)
"""값을 **그대로 나르는** 노드들 — 소비자를 찾을 때 뚫고 올라간다."""


def _consumer_name(parent: ast.AST, parents: dict[int, tuple[ast.AST, str]]) -> str:
    """값이 흘러 들어가는 소비자 이름. 호출이면 피호출자, 비교면 `compare`."""
    node: ast.AST = parent
    for _ in range(6):
        if isinstance(node, ast.Compare):
            return "compare"
        if isinstance(node, ast.Return):
            return "return"
        if isinstance(node, ast.Call):
            func = node.func
            return getattr(func, "attr", getattr(func, "id", "call"))
        if isinstance(node, ast.keyword):
            outer = parents.get(id(node))
            if outer is not None and isinstance(outer[0], ast.Call):
                func = outer[0].func
                callee = getattr(func, "attr", getattr(func, "id", "call"))
                return f"{callee}({node.arg})"
            return f"kw:{node.arg}"
        if isinstance(node, _CLIMBED_NODES):
            outer = parents.get(id(node))
            if outer is None:
                break
            node = outer[0]
            continue
        break
    return type(node).__name__.lower()


def _literal_index(
    node: ast.AST, parents: dict[int, tuple[ast.AST, str]]
) -> int | None:
    """`stability_seeds[0]` 처럼 **상수 첨자**로 집은 원소의 번호. 아니면 `None`."""
    outer = parents.get(id(node))
    if outer is None or not isinstance(outer[0], ast.Subscript):
        return None
    index = outer[0].slice
    if isinstance(index, ast.Constant) and isinstance(index.value, int):
        return index.value
    return None


@dataclass(frozen=True)
class _Resolver:
    """한 함수 안에서 「이 식이 정책에서 온 값인가」를 답하는 해석기.

    닫힘이 아니라 값으로 들고 다니는 이유는 함수마다 지역 바인딩이 다르기 때문이다 —
    루프 변수를 닫는 중첩 함수는 바인딩이 늦게 평가돼 자리를 섞는다."""

    leaf_keys: Mapping[tuple[str, str], frozenset[str]]
    providers: Mapping[tuple[str, str], frozenset[str]]
    groups: Mapping[str, tuple[str, frozenset[str]]]
    top: str
    group_names: dict[str, str]
    value_names: dict[str, frozenset[str]]

    def is_policy(self, node: ast.AST) -> bool:
        if isinstance(node, ast.Name):
            return node.id in _POLICY_ROOT_NAMES
        return isinstance(node, ast.Attribute) and node.attr == "policy"

    def group_of(self, node: ast.AST) -> str | None:
        if isinstance(node, ast.Name):
            return self.group_names.get(node.id)
        if (
            isinstance(node, ast.Attribute)
            and node.attr in self.groups
            and self.is_policy(node.value)
        ):
            return self.groups[node.attr][0]
        return None

    def resolve(self, node: ast.AST) -> tuple[str, frozenset[str]] | None:
        if isinstance(node, ast.Call):
            # **제공자 호출의 결과도 정책 유래 값이다**(code-review r2 H-1). 이 분기가 없으면
            # `alpha = policy.verdict.alpha_for(...)` 같은 지역 대입에서 기계가 조용히
            # 「읽기」로 되돌아간다 — 호출 자리는 삼중이 되지만 그 값이 **같은 함수 안에서**
            # 흘러가는 소비자들은 삼중이 아니게 되고, 거기 상수를 박아도 명단이 그대로다.
            # r1 의 막는 결함과 같은 모양이라 같은 층에서 닫는다.
            return self.resolve(node.func)
        if isinstance(node, ast.Name):
            if node.id in self.value_names:
                return ("value", self.value_names[node.id])
            if node.id in self.group_names:
                return ("group", frozenset())
            return None
        if not isinstance(node, ast.Attribute):
            return None
        if node.attr in self.groups and self.is_policy(node.value):
            return ("group", frozenset())
        owner = self.group_of(node.value)
        if owner is not None:
            if (owner, node.attr) in self.leaf_keys:
                return ("value", self.leaf_keys[(owner, node.attr)])
            if (owner, node.attr) in self.providers:
                return ("value", self.providers[(owner, node.attr)])
            return None
        if self.is_policy(node.value) and (self.top, node.attr) in self.leaf_keys:
            return ("value", self.leaf_keys[(self.top, node.attr)])
        return None

    def bind_locals(self, function: ast.FunctionDef) -> None:
        """지역 대입을 모은다 — 세 번 훑어 연쇄 대입까지 받는다."""
        for _ in range(3):
            for node in ast.walk(function):
                if not (
                    isinstance(node, ast.Assign)
                    and len(node.targets) == 1
                    and isinstance(node.targets[0], ast.Name)
                ):
                    continue
                found = self.resolve(node.value)
                if found is None:
                    continue
                name = node.targets[0].id
                if found[0] != "group":
                    self.value_names[name] = found[1]
                    continue
                owner = self.group_of(node.value)
                if owner is None and isinstance(node.value, ast.Attribute):
                    owner = self.groups.get(node.value.attr, (None, frozenset()))[0]
                if owner is not None:
                    self.group_names[name] = owner


def _enclosing_class(
    function: ast.FunctionDef, parents: Mapping[int, tuple[ast.AST, str]]
) -> str | None:
    walker: ast.AST = function
    while id(walker) in parents:
        walker, _ = parents[id(walker)]
        if isinstance(walker, ast.ClassDef):
            return walker.name
    return None


def _scan_function(
    function: ast.FunctionDef,
    *,
    module: str,
    parents: Mapping[int, tuple[ast.AST, str]],
    resolver: _Resolver,
) -> set[PolicyUse]:
    resolver.bind_locals(function)
    uses: set[PolicyUse] = set()
    for node in ast.walk(function):
        found = resolver.resolve(node)
        if found is None or found[0] != "value" or not found[1]:
            continue
        if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Store):
            continue
        parent_entry = parents.get(id(node))
        if parent_entry is None:
            continue
        parent, field = parent_entry
        if isinstance(parent, ast.Assign) and field == "value":
            continue  # 바인딩은 쓰임이 아니다
        if isinstance(parent, ast.Attribute):
            continue  # 속성 체인 중간
        keys = found[1]
        index = _literal_index(node, parents)
        if index is not None:
            indexed = {key for key in keys if key.endswith(f".{index}")}
            if indexed:
                keys = frozenset(indexed)
        consumer = _consumer_name(parent, parents)
        for key in keys:
            uses.add(PolicyUse(key, f"{module}.{function.name}", consumer))
    return uses


def _scan_module(
    path: Path,
    leaf_keys: dict[tuple[str, str], frozenset[str]],
    providers: dict[tuple[str, str], frozenset[str]],
    groups: dict[str, tuple[str, frozenset[str]]],
    top: str,
) -> set[PolicyUse]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    parents = _parents(tree)
    module = path.stem
    uses: set[PolicyUse] = set()
    for function in (n for n in ast.walk(tree) if isinstance(n, ast.FunctionDef)):
        if function.name == "__post_init__":
            continue
        holder = _enclosing_class(function, parents)
        resolver = _Resolver(
            leaf_keys=leaf_keys,
            providers=providers,
            groups=groups,
            top=top,
            group_names=(
                {"self": holder}
                if holder is not None and module == "policy_values"
                else {}
            ),
            value_names={},
        )
        uses |= _scan_function(
            function, module=module, parents=parents, resolver=resolver
        )
    return uses


def policy_value(policy: StrategyBacktestPolicy, key: str) -> object:
    """정책 키 -> **로드된 정책 객체의 값**. 그룹·필드 대응은 쓰임 명단과 같은 도출을 쓴다.

    공시 칸 단언이 「판정문에 실린 값 == 정책이 실제로 들고 있는 값」을 재는 데 쓴다 — 기대값을
    test 에 적으면 공시 칸이 상수가 된 것과 구별되지 않는다."""
    if "." not in key:
        return getattr(policy, key)
    prefix, leaf = key.split(".", 1)
    if prefix == "stability_seeds":
        return policy.stability_seeds[int(leaf)]
    groups = _group_classes(policy)
    holders = [attr for attr, (_, fields) in groups.items() if leaf in fields]
    if len(holders) > 1:
        holders.sort(key=lambda attr: _shared_prefix_length(attr, prefix), reverse=True)
    return getattr(getattr(policy, holders[0]), leaf)


def policy_use_census(policy: StrategyBacktestPolicy) -> tuple[PolicyUse, ...]:
    """판정 경로에서 정책 값이 **쓰이는 자리** 전부 — AST 와 정책 객체에서 생성한다."""
    keys = policy_file_keys()
    leaf_keys = _leaf_keys(policy, keys)
    providers = _providers(leaf_keys)
    groups = _group_classes(policy)
    top = type(policy).__name__
    found: set[PolicyUse] = set()
    for path in _VERDICT_PATH_MODULES:
        found |= _scan_module(path, leaf_keys, providers, groups, top)
    return tuple(sorted(found, key=lambda use: (use.key, use.site, use.consumer)))
