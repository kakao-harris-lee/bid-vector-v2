"""M5/5D-2 — 출하 정책 로드 test 지원(golden `_adapter.py`와 같은 관례, `tests/inference/`
스코프 파일이라 5D 소유 모듈 편집 없이 추가한다).

`assessment.agency_sample_threshold`(D-5D2-3)는 출하 `inference-v1.yaml`에 없다
(`OPEN-5D2-POLICY-VALUES` 값 미정, 운영자 결정 2026-09-13 (c) — 5C 재학습 지표 뒤 값을
정한다). 그래서 출하 파일을 그대로 로드하면 이제 항상 `PolicyRejected`다(의도된 상태,
`tests/inference/test_policy.py::
test_shipped_policy_file_is_rejected_missing_agency_sample_threshold`가 그 자체를
고정한다). 이 모듈은 다른 5D 모듈(assessment·scenario·predict)의 test 가 출하 값(threshold
제외)을 계속 쓸 수 있도록, **출하 YAML 파일을 편집하지 않고** placeholder 를 얹은 임시
사본에서 읽는 공유 helper 를 낸다."""

from __future__ import annotations

import tempfile
from pathlib import Path

import yaml

from ml_engine.inference.policy import InferencePolicy, load_inference_policy

_POLICY_PATH = Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"

# 가장 관대한 placeholder(`1`) — `agency_sample_threshold` 자체를 검증하는 test 는
# `test_policy.py`에 이미 있고, 이 값은 그 test 의 답이 아니다(`OPEN-5D2-POLICY-VALUES`).
_TEST_AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER = 1


def shipped_inference_policy_for_test() -> InferencePolicy:
    """출하 값 스물셋 + placeholder `agency_sample_threshold` 로 로드한 `InferencePolicy`.
    `agency_sample_threshold` 자체를 잠그는 test 는 이 helper 를 쓰지 않는다."""
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
