"""M5/5D golden 통합(M-3) — `fixtures/manifest.yaml`의 `ml-kernel-*` case 입력·기대값을
production 타입으로 잇는 어댑터. **production 코드에 golden 전용 표면을 만들지 않는다**
(팀장 통합 라운드 지시) — 대응 규칙은 전부 이 파일에 있다.

정책 변환: case 마다 `InferencePolicy`의 일부 필드만 synthetic 값으로 선언한다(임계 24·
κ 15/30·z 1.25·클램프 0.6/1.3 등 — legacy·출하 값과 의도적으로 다르다, `5d-golden/scope.md`
「이 레인이 세운 규율」). 선언하지 않은 필드는 출하 `inference-v1.yaml` 값을 그대로 채운다
— `dataclasses.replace`로 필요한 필드만 덮어써서 매 case 마다 20 필드를 다시 쓰지 않는다.
"""

from __future__ import annotations

import dataclasses
import json
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

import yaml

from ml_engine.inference.assessment import AssessmentProvenance
from ml_engine.inference.policy import InferencePolicy, load_inference_policy

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[3]
_REPO_ROOT = _ML_ENGINE_ROOT.parent
_MANIFEST_PATH = _REPO_ROOT / "fixtures" / "manifest.yaml"
_SHIPPED_POLICY_PATH = _ML_ENGINE_ROOT / "policy" / "inference-v1.yaml"

_SIGN_MARKERS: dict[str, int] = {"NEGATIVE": -1, "NEUTRAL": 0, "POSITIVE": 1}


def load_ml_kernel_cases() -> dict[str, dict[str, Any]]:
    """`fixtures/manifest.yaml`에서 `domain: ml-kernel` case 를 읽어 `{id: {input, expected}}`.
    파일 경로는 각 case 의 `input_file`/`expected_file`(저장소 루트 기준)을 그대로 따른다."""
    raw = yaml.safe_load(_MANIFEST_PATH.read_text(encoding="utf-8"))
    cases: dict[str, dict[str, Any]] = {}
    for entry in raw["cases"]:
        if entry.get("domain") != "ml-kernel":
            continue
        case_id = entry["id"]
        input_path = _REPO_ROOT / entry["input_file"]
        expected_path = _REPO_ROOT / entry["expected_file"]
        cases[case_id] = {
            "input": json.loads(input_path.read_text(encoding="utf-8")),
            "expected": json.loads(expected_path.read_text(encoding="utf-8")),
        }
    return cases


def shipped_policy() -> InferencePolicy:
    """출하 `inference-v1.yaml` — case 가 선언하지 않은 필드의 기본값 공급원."""
    policy = load_inference_policy(_SHIPPED_POLICY_PATH)
    assert isinstance(policy, InferencePolicy), f"출하 정책 로드 실패: {policy!r}"
    return policy


def policy_with(base: InferencePolicy, **overrides: Any) -> InferencePolicy:
    """`base`에서 `overrides`만 바꾼 새 `InferencePolicy` — case 가 선언한 synthetic 값만
    덮어쓴다(`dataclasses.replace`, `__post_init__` 불변식 검사 없음 — 테스트 전용 조립,
    알려진 제한 10 과 같은 갈래)."""
    return dataclasses.replace(base, **overrides)


def sign_marker_to_int(marker: str) -> int:
    """`"NEGATIVE"`/`"NEUTRAL"`/`"POSITIVE"` → `-1`/`0`/`1`(curator 부호 표기, `-1.0` 리터럴
    회피 — `data-dictionary.md` §12.1)."""
    return _SIGN_MARKERS[marker]


def provenance_from_label(label: str) -> AssessmentProvenance:
    """case 가 나르는 provenance 라벨은 승인 어휘가 아니다(5d-golden not_covered) — 이
    커널이 실제로 가르는 축은 `CLEAN`이냐 아니냐 하나뿐이므로, `CLEAN`만 그대로 매핑하고
    나머지는 전부 `UNKNOWN`(닫힌 5값 enum 안의 임의의 비-CLEAN 대표값)으로 접는다."""
    return (
        AssessmentProvenance.CLEAN if label == "CLEAN" else AssessmentProvenance.UNKNOWN
    )


def fraction(value: dict[str, str] | None) -> Decimal | None:
    """`{"fraction": "0.87"}` → `Decimal("0.87")`(scale 보존), `null` → `None`."""
    if value is None:
        return None
    return Decimal(value["fraction"])


def parse_instant(value: str) -> datetime:
    """ISO-8601(`+09:00` 오프셋 또는 `Z`) → aware `datetime`(Python 3.11+ `fromisoformat`
    가 둘 다 받는다)."""
    return datetime.fromisoformat(value)
