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

from pathlib import Path

import pytest

from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.policy import (
    EvaluationPolicy,
    PolicyRejected,
    PolicyRejectionReason,
    load_evaluation_policy,
)

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
