"""RED — `ml_engine.training.residual`·`ml_engine.training.release`(scope ⑦⑨,
D-5C-10)."""

from __future__ import annotations

import numpy as np
import pytest

from ml_engine.training.release import ReleaseIdentity, ReleaseInputs, derive_release_id
from ml_engine.training.residual import residual_std


def test_residual_std_floors_when_sample_too_small() -> None:
    assert residual_std(np.array([]), floor=0.002) == 0.002
    assert residual_std(np.array([0.1]), floor=0.002) == 0.002


def test_residual_std_matches_sample_stddev_above_floor() -> None:
    residuals = np.array([0.1, -0.1, 0.2, -0.2])
    expected = float(np.std(residuals, ddof=1))
    assert residual_std(residuals, floor=0.0001) == pytest.approx(expected)


def test_residual_std_never_below_floor() -> None:
    tiny = np.array([0.0001, -0.0001])
    assert residual_std(tiny, floor=0.002) == 0.002


def _inputs(**overrides: object) -> ReleaseInputs:
    base: dict[str, object] = dict(
        dataset_id="ds-1",
        training_spec_version="award-rate-gbm-training-v1",
        training_spec_checksum="a" * 64,
        seed=20260812,
        code_version="abc123",
    )
    base.update(overrides)
    return ReleaseInputs(**base)  # type: ignore[arg-type]


def test_derive_release_id_is_deterministic() -> None:
    assert derive_release_id(_inputs()) == derive_release_id(_inputs())


def test_derive_release_id_changes_when_any_input_changes() -> None:
    baseline = derive_release_id(_inputs())
    assert derive_release_id(_inputs(dataset_id="ds-2")) != baseline
    assert derive_release_id(_inputs(training_spec_version="v2")) != baseline
    assert derive_release_id(_inputs(training_spec_checksum="b" * 64)) != baseline
    assert derive_release_id(_inputs(seed=1)) != baseline
    assert derive_release_id(_inputs(code_version="def456")) != baseline


def test_derive_release_id_is_16_hex_chars() -> None:
    release_id = derive_release_id(_inputs())
    assert len(release_id) == 16
    int(release_id, 16)  # raises if not hex


@pytest.mark.parametrize(
    "overrides",
    [
        {"dataset_id": ""},
        {"training_spec_version": ""},
        {"training_spec_checksum": ""},
        {"code_version": ""},
        {"code_version": "   "},
    ],
)
def test_release_inputs_rejects_blank_fields(overrides: dict[str, object]) -> None:
    with pytest.raises(ValueError):
        _inputs(**overrides)


def test_release_identity_has_no_artifact_checksum_field() -> None:
    """D-5C-9 — `release` 안에 자기 checksum 을 넣지 않는다."""
    fields = {field for field in ReleaseIdentity.__dataclass_fields__}
    assert fields == {
        "release_id",
        "feature_schema_version",
        "code_version",
        "dataset_id",
    }
