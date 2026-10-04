"""RED — M6/6G-2e D-6G2e-6b·10. `stability_seeds` 의 **개수가 정책 스키마에 고정**된다.

`OPEN-6G2A-SEED-COUNT-NOT-PINNED` — 평탄 색인 목록 판독기(`collect_indexed_list`)는
인덱스가 0 부터 **연속**일 것만 요구하고 개수를 모른다. 그래서 뒤에서부터 지우면 seed 가
**하나까지** 줄고, 그때 seed 안정성 레그가 자명하게 참이 된다(부호 집합의 크기가 1 이라
`SEED_UNSTABLE` 이 불가능하다). 승인 문면(A-3)은 「seed 5」이고, 줄어든 수로 돈 판정은
거부가 아니라 판정문의 `seeds` 목록으로만 드러났다.

길이는 **공용 판독기에 넣을 수 없다** — 같은 판독기가 길이 다른 목록 셋
(`stability_seeds` 다섯 · `amount_band_edges` 넷 · `segment_axes` 둘)을 읽는다. 그래서
**정책별 키 스키마**가 길이를 진다: `stability_seeds` 를 읽는 로더가 둘이므로 두 자리다
(`strategy-backtest-v1.yaml` 의 판정 정책 · `evaluation-v1.yaml` 의 평가 정책 — 뒤쪽은
M5 학습 안정성 레그가 같은 구멍을 가졌다, D-6G2e-10).

여기서 재는 것은 **거동**이다(모듈의 비공개 이름을 읽지 않는다): 출하 다섯은 서고,
하나·넷·여섯은 선다. 「리터럴 `5` 가 아니라 스키마에서 온다」는 숫자 리터럴 게이트가
따로 잠근다 — `5` 는 출하 임계(`max_origins`)라 `evaluation/**` 소스에 적으면 그 게이트가
붉어진다.
"""

from __future__ import annotations

import ast
from pathlib import Path

import pytest

import ml_engine.evaluation.backtest.policy as backtest_policy
import ml_engine.evaluation.policy as evaluation_policy
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.policy import (
    APPROVED_SEED_KEYS,
    SEED_PREFIX,
    EvaluationPolicy,
    PolicyRejected,
    PolicyRejectionReason,
    load_evaluation_policy,
    seed_key_mismatch,
)

_SRC_ROOT = Path(evaluation_policy.__file__).resolve().parents[2]
_POLICY_DIR = Path(__file__).resolve().parents[2] / "policy"
_SHIPPED_BACKTEST = _POLICY_DIR / "strategy-backtest-v1.yaml"
_SHIPPED_EVALUATION = _POLICY_DIR / "evaluation-v1.yaml"
_SEED_PREFIX = "stability_seeds."
_APPROVED_SEED_COUNT = 5
_EXTRA_SEED_VALUE = "999"


def _seed_lines(text: str) -> list[str]:
    return [line for line in text.splitlines() if line.startswith(_SEED_PREFIX)]


def _shipped_seed_values(text: str) -> list[str]:
    return [line.split(":", maxsplit=1)[1].strip() for line in _seed_lines(text)]


def _with_seed_count(text: str, count: int) -> str:
    """출하 문면에서 **seed 줄만** 다시 쓴다 — 다른 값·주석은 그대로다. 값은 출하 seed
    그대로 쓰고(중복은 따로 거부되는 갈래다) 여섯째만 합성한다."""
    values = [*_shipped_seed_values(text), _EXTRA_SEED_VALUE]
    kept = [line for line in text.splitlines() if not line.startswith(_SEED_PREFIX)]
    seeds = [f"{_SEED_PREFIX}{index}: {values[index]}" for index in range(count)]
    return "\n".join([*kept, *seeds]) + "\n"


def _write(tmp_path: Path, name: str, text: str) -> Path:
    path = tmp_path / name
    path.write_text(text, encoding="utf-8")
    return path


def test_the_approved_seed_key_enumeration_lives_in_exactly_one_place() -> None:
    """D-6G2c-21 ③ — 승인 seed 키 **열거는 한 자리**다.

    앞 판은 두 로더가 각자 한 벌씩 들고 있었다. 두 벌은 조용히 갈릴 수 있다 — 한쪽에만
    여섯째를 더하면 그 로더만 여섯을 받고 다른 쪽 test 는 그대로 초록이다. 자리를 세는 것은
    **문자열 grep 이 아니라 AST** 다: 주석·문면에 같은 이름이 나와도 세지 않고, 할당이
    어디에 있든 잡는다. 자리는 **저장소 상대 경로**로 센다(cr r1 P-7) — basename 으로 세면
    저장소에 `policy.py` 가 셋 있어(`evaluation/` · `evaluation/backtest/` · `registry/`)
    열거를 **옮겨도** 초록이었다.

    더해서 판정 로더가 **같은 술어 객체**를 쓰는지 본다 — 이름만 import 하고 자기 비교를
    따로 두면 열거는 하나인데 판단이 둘이다."""
    assigned: list[str] = []
    for path in sorted((_SRC_ROOT / "ml_engine").rglob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            targets: list[ast.expr] = []
            if isinstance(node, ast.Assign):
                targets = list(node.targets)
            elif isinstance(node, ast.AnnAssign):
                targets = [node.target]
            assigned.extend(
                str(path.relative_to(_SRC_ROOT))
                for target in targets
                if isinstance(target, ast.Name)
                and target.id.lstrip("_") == "APPROVED_SEED_KEYS"
            )
    expected = str(Path(evaluation_policy.__file__).resolve().relative_to(_SRC_ROOT))
    assert assigned == [expected], (
        f"승인 seed 키 열거가 한 자리가 아니다: {assigned}(기대: {expected})"
    )
    assert backtest_policy.seed_key_mismatch is evaluation_policy.seed_key_mismatch, (
        "판정 로더가 공용 술어를 쓰지 않는다 — 열거는 하나인데 판단이 둘이다"
    )


def test_seed_key_mismatch_is_a_set_equality_not_a_length_check() -> None:
    """verifier r1 F-4 — **길이는 같고 키가 다른** 판이 거부된다.

    막는 결함: `seed_key_mismatch` 를 `len(present) == len(approved)` 로 되돌려도 전체
    suite 가 초록이었다. 두 로더를 거치는 판은 전부 **연속 색인**이라(판독기가 그것만 받는다)
    길이와 집합이 같은 답을 내고, 그래서 집합 등식 **자체**를 잠그는 자리가 없었다. 승인
    목록이 비연속으로 바뀌는 날 그 차이가 난다.

    술어를 직접 부른다 — 로더를 거치면 연속 색인 검사가 먼저 서서 이 축이 가려진다.
    기대값은 승인 목록에서 만든다(`.5` 는 그 목록의 길이에서 나온다)."""
    approved: dict[str, int] = dict.fromkeys(APPROVED_SEED_KEYS, 0)
    assert seed_key_mismatch(approved) is None, "승인 전수가 거부됐다"

    missing = APPROVED_SEED_KEYS[-1]
    extra = f"{SEED_PREFIX}.{len(APPROVED_SEED_KEYS)}"
    assert extra not in approved, extra
    swapped = {key: value for key, value in approved.items() if key != missing}
    swapped[extra] = 0
    assert len(swapped) == len(approved), "판을 잘못 지었다 — 길이가 같아야 축이 갈린다"

    message = seed_key_mismatch(swapped)
    assert message is not None, (
        "길이는 같고 키가 다른 판이 통과했다 — 술어가 집합이 아니라 길이를 본다"
    )
    assert missing in message and extra in message, message


def test_the_rejection_names_the_missing_seed_key(tmp_path: Path) -> None:
    """D-6G2c-21 ③ — 거부가 **집합 등식**이다: 없는 키를 이름으로 적는다.

    길이 비교는 수 둘(`5 != 4`)만 남겨 읽는 쪽이 어느 색인이 빠졌는지 알 수 없었다. 두
    로더가 같은 술어를 쓰므로 두 문면이 같은 모양으로 나온다 — 그것도 함께 잰다."""
    missing = APPROVED_SEED_KEYS[-1]
    short = _APPROVED_SEED_COUNT - 1
    rejections = (
        load_strategy_backtest_policy(
            _write(
                tmp_path,
                "backtest-named.yaml",
                _with_seed_count(_SHIPPED_BACKTEST.read_text(encoding="utf-8"), short),
            )
        ),
        load_evaluation_policy(
            _write(
                tmp_path,
                "evaluation-named.yaml",
                _with_seed_count(
                    _SHIPPED_EVALUATION.read_text(encoding="utf-8"), short
                ),
            )
        ),
    )
    for rejected in rejections:
        assert isinstance(rejected, PolicyRejected), rejected
        assert missing in rejected.detail, (
            f"거부 문면이 빠진 키를 이름으로 적지 않는다: {rejected.detail}"
        )


def test_both_shipped_policies_carry_exactly_the_approved_five() -> None:
    """D-6G2e-10 의 착수 조건 — 출하 파일이 정확히 다섯이 아니면 길이를 고정하는 순간
    학습·판정 경로가 깨진다. 두 파일을 함께 잰다(둘이 조용히 갈리는 자리다)."""
    for path in (_SHIPPED_BACKTEST, _SHIPPED_EVALUATION):
        text = path.read_text(encoding="utf-8")
        assert len(_seed_lines(text)) == _APPROVED_SEED_COUNT, path.name


@pytest.mark.parametrize("count", [1, 4, 6])
def test_the_backtest_loader_refuses_a_seed_count_other_than_five(
    count: int, tmp_path: Path
) -> None:
    rejected = load_strategy_backtest_policy(
        _write(
            tmp_path,
            f"backtest-{count}.yaml",
            _with_seed_count(_SHIPPED_BACKTEST.read_text(encoding="utf-8"), count),
        )
    )
    assert isinstance(rejected, PolicyRejected), f"seed {count} 개가 통과했다"
    assert rejected.reason is PolicyRejectionReason.INVALID_VALUE, rejected


@pytest.mark.parametrize("count", [1, 4, 6])
def test_the_evaluation_loader_refuses_a_seed_count_other_than_five(
    count: int, tmp_path: Path
) -> None:
    rejected = load_evaluation_policy(
        _write(
            tmp_path,
            f"evaluation-{count}.yaml",
            _with_seed_count(_SHIPPED_EVALUATION.read_text(encoding="utf-8"), count),
        )
    )
    assert isinstance(rejected, PolicyRejected), f"seed {count} 개가 통과했다"
    assert rejected.reason is PolicyRejectionReason.INVALID_VALUE, rejected


def test_five_seeds_still_load_in_both_loaders(tmp_path: Path) -> None:
    """양성 대조 — 거부가 모든 것을 거부하는 것이 아니다. 출하 다섯을 **다시 쓴 문면**으로
    태워, 거부가 「seed 줄을 다시 썼다」가 아니라 「개수」에 걸린 것임을 가른다."""
    backtest = load_strategy_backtest_policy(
        _write(
            tmp_path,
            "backtest-5.yaml",
            _with_seed_count(
                _SHIPPED_BACKTEST.read_text(encoding="utf-8"), _APPROVED_SEED_COUNT
            ),
        )
    )
    assert isinstance(backtest, StrategyBacktestPolicy), backtest
    assert len(backtest.stability_seeds) == _APPROVED_SEED_COUNT

    evaluation = load_evaluation_policy(
        _write(
            tmp_path,
            "evaluation-5.yaml",
            _with_seed_count(
                _SHIPPED_EVALUATION.read_text(encoding="utf-8"), _APPROVED_SEED_COUNT
            ),
        )
    )
    assert isinstance(evaluation, EvaluationPolicy), evaluation
    assert len(evaluation.stability_seeds) == _APPROVED_SEED_COUNT
