"""`ml_engine.training.policy` — 학습 정책(도메인 값) 로더(D-5C-3·D-5C-7). 하이퍼파라미터
(모델 정의)는 여기 없다 — `training/spec.py`의 코드 선언이 그 자리다. 이 모듈은 5A
`registry.policy.load_policy`(fail-closed, known_keys 전수) 위에 `min_training_rows`
불변식(≥ 1)만 얹는다.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from typing import Final

import yaml

from ml_engine.registry.policy import PolicyError
from ml_engine.registry.policy import load_policy as _load_raw_policy

SHIPPED_TRAINING_POLICY_VERSION: Final[str] = "training-v1"

_KNOWN_KEYS: Final[frozenset[str]] = frozenset({"min_training_rows"})


@dataclass(frozen=True)
class TrainingPolicy:
    """`min_training_rows`는 학습을 시작할 최소 행 자격이다(legacy `config.py:634-635`가
    선언만 하고 걸지 않았던 값 — 조사 01 §8-2, 이 slice 가 실제로 건다). 기본값 없음."""

    version: str
    min_training_rows: int

    def __post_init__(self) -> None:
        if self.min_training_rows < 1:
            raise ValueError(
                f"min_training_rows 는 1 이상이어야 합니다: {self.min_training_rows}"
            )


class PolicyRejectionReason(StrEnum):
    MALFORMED = "MALFORMED"
    INVALID_MIN_TRAINING_ROWS = "INVALID_MIN_TRAINING_ROWS"


@dataclass(frozen=True)
class PolicyRejected:
    reason: PolicyRejectionReason
    detail: str


def load_training_policy(path: Path) -> TrainingPolicy | PolicyRejected:
    """`path`의 YAML 을 읽어 `TrainingPolicy`로 검증한다. 미지 키·버전 없음·매핑 아님은
    5A `load_policy`가 던지는 `PolicyError`로 걸러진다(여기서 결과 타입으로 옮긴다) —
    `min_training_rows` 자체의 도메인 불변식(≥ 1)은 이 모듈이 추가로 검사한다.

    code-reviewer PR #13 HIGH-2 — 5A `load_policy`는 `yaml.safe_load`가 던지는
    `yaml.YAMLError`(문법이 깨진 YAML, 예: 닫히지 않은 flow sequence)를 잡지 않고
    그대로 전파한다. `PolicyError`도 `OSError`도 아니므로 여기서 별도로 잡는다 —
    5A `registry/policy.py`는 편집 금지(5C 경계에서 잡는다, 인계 대상은
    `OPEN-5C-YAML-ERROR-5D`). 새 enum 값을 만들지 않고 기존 `MALFORMED`(파일을 정상
    구조로 읽어낼 수 없다는 뜻이 이미 이 값과 같다)로 매핑한다."""
    try:
        raw = _load_raw_policy(path, known_keys=_KNOWN_KEYS)
    except (PolicyError, OSError, yaml.YAMLError) as exc:
        return PolicyRejected(PolicyRejectionReason.MALFORMED, str(exc))

    min_training_rows = raw.values.get("min_training_rows")
    if (
        not isinstance(min_training_rows, int)
        or isinstance(min_training_rows, bool)
        or min_training_rows < 1
    ):
        return PolicyRejected(
            PolicyRejectionReason.INVALID_MIN_TRAINING_ROWS, repr(min_training_rows)
        )

    return TrainingPolicy(version=raw.version, min_training_rows=min_training_rows)
