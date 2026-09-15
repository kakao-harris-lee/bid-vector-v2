"""RED — `ml_engine.training.residual`·`ml_engine.training.release`(scope ⑦⑨,
D-5C-10). verifier r1 H-1 뒤 — `ReleaseInputs` 타입은 삭제됐다. `derive_release_id`는
다섯 값을 키워드 인자로 직접 받는다(값의 유효성은 각 값의 원 출처 타입이 강제 —
이 모듈은 더 이상 검증하지 않는다)."""

from __future__ import annotations

import numpy as np
import pytest

from ml_engine.training.release import ReleaseIdentity, derive_release_id
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


def _kwargs(**overrides: object) -> dict[str, object]:
    base: dict[str, object] = dict(
        dataset_id="ds-1",
        training_spec_version="award-rate-gbm-training-v1",
        training_spec_checksum="a" * 64,
        seed=20260812,
        code_version="abc123",
    )
    base.update(overrides)
    return base


def test_derive_release_id_is_deterministic() -> None:
    assert derive_release_id(**_kwargs()) == derive_release_id(**_kwargs())  # type: ignore[arg-type]


def test_derive_release_id_changes_when_any_input_changes() -> None:
    baseline = derive_release_id(**_kwargs())  # type: ignore[arg-type]
    assert derive_release_id(**_kwargs(dataset_id="ds-2")) != baseline  # type: ignore[arg-type]
    assert derive_release_id(**_kwargs(training_spec_version="v2")) != baseline  # type: ignore[arg-type]
    assert (
        derive_release_id(**_kwargs(training_spec_checksum="b" * 64))  # type: ignore[arg-type]
        != baseline
    )
    assert derive_release_id(**_kwargs(seed=1)) != baseline  # type: ignore[arg-type]
    assert derive_release_id(**_kwargs(code_version="def456")) != baseline  # type: ignore[arg-type]


def test_derive_release_id_is_16_hex_chars() -> None:
    release_id = derive_release_id(**_kwargs())  # type: ignore[arg-type]
    assert len(release_id) == 16
    int(release_id, 16)  # raises if not hex


def test_release_identity_has_no_artifact_checksum_field() -> None:
    """D-5C-9 — `release` 안에 자기 checksum 을 넣지 않는다."""
    fields = {field for field in ReleaseIdentity.__dataclass_fields__}
    assert fields == {
        "release_id",
        "feature_schema_version",
        "code_version",
        "dataset_id",
    }
