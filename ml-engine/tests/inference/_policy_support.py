"""M5/5D-2 — 출하 정책 로드 test 지원(golden `_adapter.py`와 같은 관례, `tests/inference/`
스코프 파일이라 5D 소유 모듈 편집 없이 추가한다).

M5/5F-1(2026-09-16) — `assessment.agency_sample_threshold`가 이제 출하
`inference-v1.yaml`에 잠정값 `10`으로 있다(`OPEN-5D2-POLICY-VALUES` 종결, 운영자
결정 ③). 아래 `setdefault` 는 이 키가 이미 있을 때 아무것도 하지 않으므로 **이제
no-op** 다 — 출하 파일을 그대로 로드해도 이 helper 와 같은 값(threshold `10`)이
나온다. 임시 사본 경로는 값이 아니라 "출하 YAML 파일 자체는 건드리지 않는다"는
불변식을 지키려 남겨 둔다(다른 5D 모듈 test 가 이 helper 를 계속 쓸 수 있게).
`agency_sample_threshold` 자체를 잠그는 test(`tests/inference/test_policy.py::
test_shipped_policy_file_loads_with_lowered_clamp_and_agency_sample_threshold`)는
이 helper 를 쓰지 않고 출하 파일을 직접 읽는다."""

from __future__ import annotations

import tempfile
from pathlib import Path

import yaml

from ml_engine.inference.policy import InferencePolicy, load_inference_policy

_POLICY_PATH = Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"

# 5F-1 이전의 가장 관대한 placeholder(`1`) — 이제 출하 파일이 이미 `agency_sample_
# threshold`를 선언해(잠정값 `10`) 아래 `setdefault`가 이 값을 쓸 일이 없다(no-op).
# 값을 지우지 않는 이유: 출하 파일이 다시 이 키를 잃는 회귀가 나도(예: 수동 편집
# 실수) 이 helper 는 계속 로드 가능한 상태를 유지해 다른 5D 모듈 test 를 안전하게
# 지킨다(fallback 이지 판정 근거 아님).
_TEST_AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER = 1


def shipped_inference_policy_for_test() -> InferencePolicy:
    """출하 값(5F-1 이후 `agency_sample_threshold` 포함) 그대로 로드한
    `InferencePolicy`. `agency_sample_threshold` 자체를 잠그는 test 는 이 helper 를
    쓰지 않는다."""
    raw_values = dict(yaml.safe_load(_POLICY_PATH.read_text(encoding="utf-8")))
    raw_values.setdefault(
        "assessment.agency_sample_threshold",
        _TEST_AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER,
    )
    with tempfile.TemporaryDirectory(prefix="bidvector-inference-policy-") as tmp:
        temp_path = Path(tmp) / "inference-v1.yaml"
        temp_path.write_text(yaml.safe_dump(raw_values), encoding="utf-8")
        policy = load_inference_policy(temp_path)
    assert isinstance(policy, InferencePolicy), f"출하 정책 로드 실패: {policy!r}"
    return policy
