"""`ml_engine.serving.policy` — `ServingPolicy`(frozen) + `load_serving_policy`(D-5E-6).
5A `registry.policy.load_policy`(fail-closed, known_keys 전수) 위에 값 불변식을 얹는다
(`reports/evidence/m5/5e/policy-values.md` §4). `dataset_uri_schemes`는 평탄 인덱스 키
(evaluation/policy.py·5C-2 관례)로 저장한다 — 5A `known_keys`가 가변 길이 시퀀스를
표현하지 못하기 때문이다.
"""

from __future__ import annotations

import dataclasses
from pathlib import Path
from typing import Final

import yaml

from ml_engine.registry.policy import PolicyError, PolicyScalar
from ml_engine.registry.policy import load_policy as _load_raw_policy

SHIPPED_SERVING_POLICY_VERSION: Final[str] = "serving-v1"

# 실제 값(1개, `file`)보다 넉넉한 정적 상한 — evaluation/policy.py `_MAX_INDEXED_LIST_LENGTH`
# 와 같은 근거.
_MAX_DATASET_URI_SCHEMES: Final[int] = 8

_SCALAR_KEYS: Final[frozenset[str]] = frozenset(
    {
        "max_workers",
        "max_concurrent_rpcs",
        "shutdown_grace_seconds",
        "job_workers",
        "idempotency_key_max_chars",
        "embedding_text_max_chars",
    }
)
_KNOWN_KEYS: Final[frozenset[str]] = _SCALAR_KEYS | frozenset(
    f"dataset_uri_schemes.{index}" for index in range(_MAX_DATASET_URI_SCHEMES)
)


@dataclasses.dataclass(frozen=True)
class ServingPolicy:
    """D-5E-6 값 전체. `dataset_uri_schemes`는 소문자·비어 있지 않음·중복 없음(§4)."""

    version: str
    max_workers: int
    max_concurrent_rpcs: int
    shutdown_grace_seconds: float
    job_workers: int
    idempotency_key_max_chars: int
    embedding_text_max_chars: int
    dataset_uri_schemes: tuple[str, ...]

    def __post_init__(self) -> None:
        if self.max_workers < 1:
            raise ValueError(f"max_workers 는 1 이상이어야 합니다: {self.max_workers}")
        if self.max_concurrent_rpcs < 1:
            raise ValueError(
                f"max_concurrent_rpcs 는 1 이상이어야 합니다: {self.max_concurrent_rpcs}"
            )
        if self.shutdown_grace_seconds < 0:
            raise ValueError(
                f"shutdown_grace_seconds 는 음수일 수 없습니다: {self.shutdown_grace_seconds}"
            )
        if self.job_workers < 1:
            raise ValueError(f"job_workers 는 1 이상이어야 합니다: {self.job_workers}")
        if self.idempotency_key_max_chars < 1:
            raise ValueError(
                "idempotency_key_max_chars 는 1 이상이어야 합니다: "
                f"{self.idempotency_key_max_chars}"
            )
        if self.embedding_text_max_chars < 1:
            raise ValueError(
                f"embedding_text_max_chars 는 1 이상이어야 합니다: "
                f"{self.embedding_text_max_chars}"
            )
        if not self.dataset_uri_schemes:
            raise ValueError("dataset_uri_schemes 는 비어 있을 수 없습니다.")
        if len(self.dataset_uri_schemes) != len(set(self.dataset_uri_schemes)):
            raise ValueError(
                f"dataset_uri_schemes 에 중복이 있습니다: {self.dataset_uri_schemes}"
            )
        for scheme in self.dataset_uri_schemes:
            if not scheme or scheme != scheme.lower():
                raise ValueError(
                    f"dataset_uri_schemes 는 비어 있지 않은 소문자여야 합니다: {scheme!r}"
                )


@dataclasses.dataclass(frozen=True)
class PolicyRejected:
    """정책 로드·값 불변식 실패 — 예외가 아니라 결과 타입."""

    reason: str


def _to_int(value: PolicyScalar, key: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{key} 는 정수여야 합니다: {value!r}")
    return value


def _to_number(value: PolicyScalar, key: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(f"{key} 는 숫자여야 합니다: {value!r}")
    return float(value)


def _dataset_uri_schemes(values: dict[str, PolicyScalar]) -> tuple[str, ...]:
    schemes: list[str] = []
    for index in range(_MAX_DATASET_URI_SCHEMES):
        key = f"dataset_uri_schemes.{index}"
        if key not in values:
            continue
        value = values[key]
        if not isinstance(value, str) or not value:
            raise TypeError(f"{key} 는 비어 있지 않은 문자열이어야 합니다: {value!r}")
        schemes.append(value)
    return tuple(schemes)


def load_serving_policy(path: Path) -> ServingPolicy | PolicyRejected:
    """`path`의 YAML 을 `ServingPolicy`로 검증한다. 미지 키·타입 오류·값 불변식 위반은
    전부 `PolicyRejected`(예외로 새지 않는다).

    verifier r1 H-1 — `_load_raw_policy`(→ `yaml.safe_load`)는 문법이 깨진 YAML 에서
    `yaml.YAMLError`(또는 하위 클래스)를 던진다. `training/policy.py`(PR #13 HIGH-2)·
    `evaluation/policy.py`가 이미 같은 구멍을 `yaml.YAMLError`까지 잡아 막았는데, 이
    slice 가 신설한 로더에서 같은 버그가 세 번째로 재발했었다 — 여기서도 잡는다."""
    try:
        raw = _load_raw_policy(path, known_keys=_KNOWN_KEYS)
    except (PolicyError, OSError, yaml.YAMLError) as exc:
        return PolicyRejected(str(exc))

    missing = _SCALAR_KEYS - set(raw.values)
    if missing:
        return PolicyRejected(f"정책 키 누락: {sorted(missing)}")

    try:
        schemes = _dataset_uri_schemes(raw.values)
        return ServingPolicy(
            version=raw.version,
            max_workers=_to_int(raw.values["max_workers"], "max_workers"),
            max_concurrent_rpcs=_to_int(
                raw.values["max_concurrent_rpcs"], "max_concurrent_rpcs"
            ),
            shutdown_grace_seconds=_to_number(
                raw.values["shutdown_grace_seconds"], "shutdown_grace_seconds"
            ),
            job_workers=_to_int(raw.values["job_workers"], "job_workers"),
            idempotency_key_max_chars=_to_int(
                raw.values["idempotency_key_max_chars"], "idempotency_key_max_chars"
            ),
            embedding_text_max_chars=_to_int(
                raw.values["embedding_text_max_chars"], "embedding_text_max_chars"
            ),
            dataset_uri_schemes=schemes,
        )
    except (TypeError, ValueError) as exc:
        return PolicyRejected(str(exc))
