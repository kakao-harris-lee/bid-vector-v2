"""RED — `ml_engine.training.release`(M9/ml-lifecycle, ML-05 acceptance 「학습 표본
정의가 바뀌면 `model_version` 이 올라간다」).

`release_id` 가 **model_version 축**이라는 선언을 test 로 세운다. 수정 전 `derive_release_id`
의 입력은 다섯(dataset·spec version·spec checksum·seed·code version)이었고 **학습 표본
범위(`sample_scope`)가 빠져 있었다** — 같은 데이터셋·spec·seed·code 로 범위만 다른 두
아티팩트가 같은 식별자를 가질 수 있었고(manifest 에는 `sample_scope` 가 있으니 내용은
달랐다), 그러면 그 acceptance 가 성립하지 않는다.

**모집단을 열거하지 않는다** — 전수 민감도 sweep 의 입력 목록은 `inspect.signature` 에서
나온다. 입력을 더하고 해시 payload 에 넣지 않으면 이 test 가 붉어지므로, 축이 조용히
늘어나지 않는다.

**문면 보정**: V2 의 식별자는 순서 있는 번호가 아니라 결정적 해시다(같은 입력의 두 학습이
같은 id 를 가져야 재현성 test 가 바이트 동일을 잴 수 있다, D-5C-10) — acceptance 의
「올라간다」는 「달라진다」로 읽는다.
"""

from __future__ import annotations

import inspect

from ml_engine.training.release import derive_release_id

_BASE: dict[str, object] = {
    "dataset_id": "dataset-1",
    "training_spec_version": "award-rate-gbm-training-v1",
    "training_spec_checksum": "a" * 64,
    "seed": 20260812,
    "code_version": "sha-1",
    "sample_scope": "clean-base",
}


def _perturbed(value: object) -> object:
    if isinstance(value, int) and not isinstance(value, bool):
        return value + 1
    return f"{value}-changed"


def test_declared_inputs_are_exactly_the_sweep_population() -> None:
    """모집단이 시그니처와 어긋나면 아래 sweep 이 새 축을 놓친다 — 먼저 잰다."""
    declared = set(inspect.signature(derive_release_id).parameters)
    assert declared == set(_BASE), declared ^ set(_BASE)


def test_every_declared_input_changes_the_model_version() -> None:
    """선언된 입력 **전부**가 식별자를 바꾼다 — 해시 payload 에 빠진 입력이 있으면
    그 축에서 붉어진다."""
    base_id = derive_release_id(**_BASE)  # type: ignore[arg-type]
    unchanged: list[str] = []
    for name in _BASE:
        variant = dict(_BASE)
        variant[name] = _perturbed(_BASE[name])
        if derive_release_id(**variant) == base_id:  # type: ignore[arg-type]
            unchanged.append(name)
    assert not unchanged, f"식별자를 바꾸지 않는 선언 입력이 있다: {unchanged}"


def test_sample_scope_is_part_of_the_model_version_axis() -> None:
    """ML-05 acceptance 그 자체 — 학습 표본 범위가 달라지면 다른 식별자다."""
    narrowed = dict(_BASE) | {"sample_scope": "all-strata"}
    assert derive_release_id(**narrowed) != derive_release_id(**_BASE)  # type: ignore[arg-type]


def test_same_inputs_give_the_same_model_version() -> None:
    """결정적 — UUID 를 쓰지 않는 이유(재현성 test 가 바이트 동일을 잰다, D-5C-10)."""
    first = derive_release_id(**_BASE)  # type: ignore[arg-type]
    second = derive_release_id(**_BASE)  # type: ignore[arg-type]
    assert first == second
    assert len(first) == len(second)
    assert first == first.lower()
    assert all(character in "0123456789abcdef" for character in first)


def test_derive_release_id_takes_no_positional_arguments() -> None:
    """여섯 입력이 전부 키워드다 — 자리 순서가 바뀌어도 조용히 다른 값을 내지 않는다."""
    parameters = inspect.signature(derive_release_id).parameters
    assert all(
        parameter.kind is inspect.Parameter.KEYWORD_ONLY
        for parameter in parameters.values()
    )
