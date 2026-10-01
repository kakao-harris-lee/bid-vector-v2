"""RED — `ml_engine.evaluation.policy`(D-5C2-3). `load_evaluation_policy`는 5A
`load_policy`(known_keys 전수) 위에 evaluation 임계 불변식을 얹는다. 목록 값
(`stability_seeds`·`amount_band_edges`·`segment_axes`)은 평탄 인덱스 키
(`stability_seeds.0` 등)로 저장되고 로더가 접두로 모아 tuple 로 조립한다."""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.evaluation.policy import (
    SHIPPED_EVALUATION_POLICY_VERSION,
    EvaluationPolicy,
    PolicyRejected,
    load_evaluation_policy,
    policy_checksum,
)

_SHIPPED_YAML = Path(__file__).resolve().parents[2] / "policy" / "evaluation-v1.yaml"


def test_shipped_policy_file_matches_policy_values_md() -> None:
    """policy-values.md §1 의 값을 이 test 에 하드코딩해 실제 YAML 과 대조한다(5A/5B/5C-1
    과 같은 관행 — 문서를 파싱하지 않는다)."""
    result = load_evaluation_policy(_SHIPPED_YAML)
    assert isinstance(result, EvaluationPolicy)
    assert result.version == SHIPPED_EVALUATION_POLICY_VERSION
    assert result.paired_t_threshold == 2.58
    assert result.gate_baseline == "category_x_band"
    assert result.gate_model == "gbm_all_strata"
    assert result.gate_stratum == "clean-base"
    assert result.maturity_threshold == 0.70
    assert result.min_evaluation_rows == 100
    assert result.max_origins == 5
    assert result.agency_baseline_min_count == 10
    assert result.stability_seeds == (20260812, 1, 7, 42, 2026)
    assert result.amount_band_edges == (1e8, 5e8, 1e9, 5e9)
    assert result.segment_axes == ("category", "amount_band")


def test_policy_checksum_is_deterministic_and_changes_with_value() -> None:
    result = load_evaluation_policy(_SHIPPED_YAML)
    assert isinstance(result, EvaluationPolicy)
    first = policy_checksum(result)
    second = policy_checksum(result)
    assert first == second
    assert len(first) == 64

    mutated = EvaluationPolicy(
        version=result.version,
        paired_t_threshold=1.0,
        gate_baseline=result.gate_baseline,
        gate_model=result.gate_model,
        gate_stratum=result.gate_stratum,
        maturity_threshold=result.maturity_threshold,
        min_evaluation_rows=result.min_evaluation_rows,
        max_origins=result.max_origins,
        agency_baseline_min_count=result.agency_baseline_min_count,
        stability_seeds=result.stability_seeds,
        amount_band_edges=result.amount_band_edges,
        segment_axes=result.segment_axes,
    )
    assert policy_checksum(mutated) != first


def test_load_evaluation_policy_missing_file_is_rejected(tmp_path: Path) -> None:
    result = load_evaluation_policy(tmp_path / "does-not-exist.yaml")
    assert isinstance(result, PolicyRejected)


def test_load_evaluation_policy_unknown_key_is_rejected(tmp_path: Path) -> None:
    path = tmp_path / "policy.yaml"
    path.write_text("version: evaluation-v1\npaired_t_threshold: 2.58\nextra_key: 1\n")
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


def _valid_values() -> dict[str, object]:
    """로더가 **받는** 값 한 벌. 아래 거부 test 는 전부 여기서 한 칸만 비틀어 만들고,
    `test_the_valid_values_load` 가 이 한 벌이 실제로 선다는 것을 잰다 — 그 양성 대조가
    없으면 거부 test 가 비튼 칸과 **무관한 이유로** 통과한다(M6/6G-2e 실측: seed 가
    둘뿐이어서, 개수를 다섯으로 고정한 뒤 여섯 test 가 전부 seed 개수로 거부됐다).

    `stability_seeds` 는 출하 다섯 그대로다(D-6G2e-6b·10 — 로더가 개수를 요구한다)."""
    return {
        "version": "evaluation-v1",
        "paired_t_threshold": 2.58,
        "gate_baseline": "category_x_band",
        "gate_model": "gbm_all_strata",
        "gate_stratum": "clean-base",
        "maturity_threshold": 0.70,
        "min_evaluation_rows": 100,
        "max_origins": 5,
        "agency_baseline_min_count": 10,
        "stability_seeds.0": 20260812,
        "stability_seeds.1": 1,
        "stability_seeds.2": 7,
        "stability_seeds.3": 42,
        "stability_seeds.4": 2026,
        "amount_band_edges.0": 100000000,
        "amount_band_edges.1": 500000000,
        "amount_band_edges.2": 1000000000,
        "amount_band_edges.3": 5000000000,
        "segment_axes.0": "category",
    }


def _write_values(path: Path, values: dict[str, object]) -> None:
    lines = [f"{key}: {value!r}" for key, value in values.items()]
    path.write_text("\n".join(lines) + "\n")


def _write_full_policy(
    path: Path, *, dropped: str | None = None, **overrides: object
) -> None:
    """`_valid_values()` 에서 키 하나를 지우거나(`dropped`) 값을 덮어쓴 정책 파일."""
    values = _valid_values()
    if dropped is not None:
        del values[dropped]
    values.update(overrides)
    _write_values(path, values)


def test_the_valid_values_load(tmp_path: Path) -> None:
    """양성 대조 — 거부 test 들의 기준 한 벌이 실제로 선다. 이것이 붉어지면 아래 거부
    test 전부가 「비튼 칸과 무관한 이유로」 통과하고 있다는 뜻이다."""
    path = tmp_path / "policy.yaml"
    _write_full_policy(path)
    result = load_evaluation_policy(path)
    assert isinstance(result, EvaluationPolicy), result


@pytest.mark.parametrize(
    "overrides",
    [
        {"paired_t_threshold": 0},
        {"paired_t_threshold": -1.0},
        {"maturity_threshold": 0.0},
        {"maturity_threshold": 1.5},
        {"min_evaluation_rows": 1},
        {"min_evaluation_rows": 0},
        {"max_origins": 0},
        {"agency_baseline_min_count": 0},
        {"gate_baseline": "not_a_real_baseline"},
        {"gate_stratum": ""},
    ],
)
def test_load_evaluation_policy_rejects_invalid_scalars(
    tmp_path: Path, overrides: dict[str, object]
) -> None:
    path = tmp_path / "policy.yaml"
    _write_full_policy(path, **overrides)
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected), overrides


def test_load_evaluation_policy_rejects_empty_stability_seeds(tmp_path: Path) -> None:
    path = tmp_path / "policy.yaml"
    _write_values(
        path,
        {
            key: value
            for key, value in _valid_values().items()
            if not key.startswith("stability_seeds.")
        },
    )
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


def test_load_evaluation_policy_rejects_duplicate_stability_seeds(
    tmp_path: Path,
) -> None:
    path = tmp_path / "policy.yaml"
    _write_full_policy(
        path,
        **{"stability_seeds.0": 1, "stability_seeds.1": 1},
    )
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


def test_load_evaluation_policy_rejects_non_ascending_amount_band_edges(
    tmp_path: Path,
) -> None:
    path = tmp_path / "policy.yaml"
    _write_full_policy(
        path,
        **{"amount_band_edges.0": 500000000, "amount_band_edges.1": 100000000},
    )
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


def test_load_evaluation_policy_rejects_gapped_indexed_list(tmp_path: Path) -> None:
    """색인에 **구멍**이 있으면 거부. 앞 판은 `.0` 을 덮어쓰고 `.2` 를 더했을 뿐이어서
    색인이 0·1·2 로 연속이었고, 거부는 구멍이 아니라 「엄격 오름차순」에서 났다 — 구멍
    경로를 재지 못했다(6G-2e 에서 실측). 가운데 키를 **지워** 구멍을 만든다."""
    path = tmp_path / "policy.yaml"
    _write_full_policy(path, dropped="amount_band_edges.1")
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


def test_load_evaluation_policy_rejects_unknown_segment_axis(tmp_path: Path) -> None:
    path = tmp_path / "policy.yaml"
    _write_full_policy(path, **{"segment_axes.0": "agency"})
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


def test_load_evaluation_policy_rejects_duplicate_segment_axis(
    tmp_path: Path,
) -> None:
    path = tmp_path / "policy.yaml"
    _write_full_policy(
        path, **{"segment_axes.0": "category", "segment_axes.1": "category"}
    )
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected)


@pytest.mark.parametrize(
    "override_line",
    [
        "paired_t_threshold: .nan",
        "paired_t_threshold: .inf",
        "maturity_threshold: .nan",
        "amount_band_edges.3: .inf",
    ],
)
def test_load_evaluation_policy_rejects_non_finite_values(
    tmp_path: Path, override_line: str
) -> None:
    """M-1 재현 — `paired_t_threshold: .nan`은 `<= 0` 비교가 NaN 에서
    항상 거짓이라 그 불변식을 통과했고(재현: 수정 전 `EvaluationPolicy` 생성 성공,
    `policy_checksum`에서 `ValueError: Out of range float values are not JSON
    compliant`로 나중에 터짐), `amount_band_edges.3: .inf`도 「양수·오름차순」을
    통과했다. 로더가 값 불변식 비교 **전에** 유한성을 검사해 `PolicyRejected`로
    막아야 한다."""
    key, _, _ = override_line.partition(":")
    key = key.strip()
    values = _valid_values()
    values.pop(key, None)
    lines = [f"{k}: {v!r}" for k, v in values.items()]
    lines.append(override_line)
    path = tmp_path / "policy.yaml"
    path.write_text("\n".join(lines) + "\n")
    result = load_evaluation_policy(path)
    assert isinstance(result, PolicyRejected), override_line


def test_policy_checksum_never_raises_because_non_finite_cannot_be_constructed() -> (
    None
):
    """M-1 — `policy_checksum`의 docstring 전제(「값이 전부 정책 불변식을
    통과한 유한값」)가 실제로 강제된다는 것을 `EvaluationPolicy` 직접 생성에서도
    확인한다(로더 경유 없이도 비유한 값은 생성 자체가 막힌다)."""
    with pytest.raises(ValueError):
        EvaluationPolicy(
            version="x",
            paired_t_threshold=float("nan"),
            gate_baseline="category_x_band",
            gate_model="gbm_all_strata",
            gate_stratum="clean-base",
            maturity_threshold=0.7,
            min_evaluation_rows=2,
            max_origins=5,
            agency_baseline_min_count=2,
            stability_seeds=(1,),
            amount_band_edges=(1.0, 2.0),
            segment_axes=("category",),
        )


def test_evaluation_policy_direct_construction_enforces_invariants() -> None:
    """직접 생성도 방어선 — Python 가시성 한계는 여전하나 명백한 위반은 막는다."""
    with pytest.raises(ValueError):
        EvaluationPolicy(
            version="x",
            paired_t_threshold=0.0,
            gate_baseline="category_x_band",
            gate_model="gbm_all_strata",
            gate_stratum="clean-base",
            maturity_threshold=0.7,
            min_evaluation_rows=100,
            max_origins=5,
            agency_baseline_min_count=10,
            stability_seeds=(1,),
            amount_band_edges=(1.0, 2.0),
            segment_axes=("category",),
        )


def test_no_cli_or_env_seam_for_thresholds() -> None:
    """ML-07 acceptance ④ — 완화 표면 부재를 정적으로 확인. `load_evaluation_policy`는
    `path` 하나만 받는다(임계를 낱개 인자·env 로 받는 시그니처가 없다)."""
    import inspect

    signature = inspect.signature(load_evaluation_policy)
    assert list(signature.parameters) == ["path"]
