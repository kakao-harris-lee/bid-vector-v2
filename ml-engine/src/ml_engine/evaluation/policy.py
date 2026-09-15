"""`ml_engine.evaluation.policy` — 평가 게이트 임계 정책 로더(D-5C2-3, ML-07 acceptance
③④). legacy(`award_rate_holdout.py`·`award_rate_diagnostics.py`·`award_rate_windows.py`·
`award_rate_scoring.py`)는 이 값 전부를 **코드 상수**로 선언했다 — CLI 도 env 도 두지
않는 것 자체가 「사후에 느슨하게 만들지 않는다」 규율이었다(조사 02 §2-6). V2 는 그 규율을
지키되 값을 코드가 아니라 **로더로만 읽는 versioned 파일**로 옮긴다(ADR 0006 D-7 —
판정 정책은 artifact 에 박히는 값이 아니다).

목록 값(`stability_seeds`·`amount_band_edges`·`segment_axes`)은 YAML 시퀀스가 아니라
평탄 인덱스 키(`stability_seeds.0` 등)로 저장한다 — 5A `registry.policy.load_policy`의
`known_keys`는 문자열 집합 전수 대조라 가변 길이 시퀀스 타입을 표현하지 못한다(5C-1
`policy.py`와 같은 제약). 이 모듈이 접두로 모아 정렬된 tuple 로 조립한다.

**임계를 낱개 인자로 받는 public 함수는 없다**(설계 검토 (1) 첫 행) — `load_evaluation_policy`
는 `path` 하나만 받고, 이 파일을 편집해야만 값이 바뀐다.
"""

from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from typing import Final, cast

import yaml

from ml_engine.registry.policy import PolicyError, PolicyScalar
from ml_engine.registry.policy import load_policy as _load_raw_policy

SHIPPED_EVALUATION_POLICY_VERSION: Final[str] = "evaluation-v1"

# 평탄 인덱스 목록 값의 상한 — 실제 값(seeds 5개·edges 4개·axes 2개)보다 넉넉한 정적
# 상한이다(5A `known_keys`는 문자열 집합 전수라 가변 길이를 표현할 수 없다). 이 값을
# 넘는 인덱스는 미지 키로 거부된다.
_MAX_INDEXED_LIST_LENGTH: Final[int] = 32

_SCALAR_KEYS: Final[frozenset[str]] = frozenset(
    {
        "paired_t_threshold",
        "gate_baseline",
        "gate_model",
        "gate_stratum",
        "maturity_threshold",
        "min_evaluation_rows",
        "max_origins",
        "agency_baseline_min_count",
    }
)
_INDEXED_LIST_PREFIXES: Final[tuple[str, ...]] = (
    "stability_seeds",
    "amount_band_edges",
    "segment_axes",
)
_KNOWN_KEYS: Final[frozenset[str]] = _SCALAR_KEYS | frozenset(
    f"{prefix}.{index}"
    for prefix in _INDEXED_LIST_PREFIXES
    for index in range(_MAX_INDEXED_LIST_LENGTH)
)

# 베이스라인 표 이름(evaluation/baselines.py 와 같은 값 — 순환 import 를 피하려고
# 여기서 리터럴로 한 번 더 선언한다, `gate_baseline` 불변식 전용).
_BASELINE_NAMES: Final[frozenset[str]] = frozenset(
    {"global_mean", "category", "amount_band", "category_x_band", "agency"}
)
_SEGMENT_AXIS_NAMES: Final[frozenset[str]] = frozenset({"category", "amount_band"})


@dataclass(frozen=True)
class EvaluationPolicy:
    """평가 게이트 임계 한 벌 — 값은 `reports/evidence/m5/5c2/policy-values.md` §1 과
    대조된다. 기본값 없음("안 넘겼다"와 "미공시"를 구별, 5C-1 관행 계승)."""

    version: str
    paired_t_threshold: float
    gate_baseline: str
    gate_model: str
    gate_stratum: str
    maturity_threshold: float
    min_evaluation_rows: int
    max_origins: int
    agency_baseline_min_count: int
    stability_seeds: tuple[int, ...]
    amount_band_edges: tuple[float, ...]
    segment_axes: tuple[str, ...]

    def __post_init__(self) -> None:
        self._validate_scalars()
        self._validate_collections()

    def _validate_scalars(self) -> None:
        # verifier r1 M-1 — NaN/Infinity 비교는 부호 있는 범위 검사를 조용히
        # 통과한다(`nan <= 0`은 항상 거짓). 범위 검사보다 먼저 유한성을 강제한다.
        if not math.isfinite(self.paired_t_threshold):
            raise ValueError(
                f"paired_t_threshold 는 유한값이어야 합니다: {self.paired_t_threshold}"
            )
        if not math.isfinite(self.maturity_threshold):
            raise ValueError(
                f"maturity_threshold 는 유한값이어야 합니다: {self.maturity_threshold}"
            )
        if self.paired_t_threshold <= 0:
            raise ValueError(
                f"paired_t_threshold 는 양수여야 합니다: {self.paired_t_threshold}"
            )
        if not (0.0 < self.maturity_threshold <= 1.0):
            raise ValueError(
                f"maturity_threshold 는 (0, 1] 안이어야 합니다: {self.maturity_threshold}"
            )
        if self.min_evaluation_rows < 2:
            raise ValueError(
                "min_evaluation_rows 는 2 이상이어야 합니다(대응 t 는 n>=2 를 "
                f"요구합니다): {self.min_evaluation_rows}"
            )
        if self.max_origins < 1:
            raise ValueError(f"max_origins 는 1 이상이어야 합니다: {self.max_origins}")
        if self.agency_baseline_min_count < 1:
            raise ValueError(
                "agency_baseline_min_count 는 1 이상이어야 합니다: "
                f"{self.agency_baseline_min_count}"
            )
        if not self.gate_stratum:
            raise ValueError("gate_stratum 은 비어 있을 수 없습니다.")
        if self.gate_baseline not in _BASELINE_NAMES:
            raise ValueError(
                f"gate_baseline 은 베이스라인 표 이름이어야 합니다: {self.gate_baseline!r}"
            )

    def _validate_collections(self) -> None:
        if not self.stability_seeds:
            raise ValueError("stability_seeds 는 비어 있을 수 없습니다.")
        if len(set(self.stability_seeds)) != len(self.stability_seeds):
            raise ValueError(
                f"stability_seeds 에 중복이 있습니다: {self.stability_seeds!r}"
            )
        if not self.amount_band_edges:
            raise ValueError("amount_band_edges 는 비어 있을 수 없습니다.")
        if any(not math.isfinite(edge) for edge in self.amount_band_edges):
            raise ValueError(
                f"amount_band_edges 는 전부 유한값이어야 합니다: {self.amount_band_edges!r}"
            )
        if any(edge <= 0 for edge in self.amount_band_edges):
            raise ValueError(
                f"amount_band_edges 는 전부 양수여야 합니다: {self.amount_band_edges!r}"
            )
        if list(self.amount_band_edges) != sorted(set(self.amount_band_edges)):
            raise ValueError(
                f"amount_band_edges 는 엄격 오름차순이어야 합니다: {self.amount_band_edges!r}"
            )
        if not self.segment_axes:
            raise ValueError("segment_axes 는 비어 있을 수 없습니다.")
        if len(set(self.segment_axes)) != len(self.segment_axes):
            raise ValueError(f"segment_axes 에 중복이 있습니다: {self.segment_axes!r}")
        if not set(self.segment_axes) <= _SEGMENT_AXIS_NAMES:
            raise ValueError(
                f"segment_axes 는 {_SEGMENT_AXIS_NAMES} 의 부분집합이어야 합니다: "
                f"{self.segment_axes!r}"
            )


class PolicyRejectionReason(StrEnum):
    MALFORMED = "MALFORMED"
    INVALID_VALUE = "INVALID_VALUE"


@dataclass(frozen=True)
class PolicyRejected:
    reason: PolicyRejectionReason
    detail: str


def _collect_indexed_list(
    values: dict[str, PolicyScalar], prefix: str
) -> tuple[PolicyScalar, ...] | None:
    """`{prefix}.{N}` 형태의 키를 모아 인덱스 오름차순 tuple 로. 구멍·중복 인덱스는
    `None`(호출부가 `INVALID_VALUE`로 거부한다)."""
    entries: dict[int, PolicyScalar] = {}
    dotted_prefix = f"{prefix}."
    for key, value in values.items():
        if not key.startswith(dotted_prefix):
            continue
        suffix = key[len(dotted_prefix) :]
        if not suffix.isdigit():
            return None
        index = int(suffix)
        if index in entries:
            return None
        entries[index] = value
    if not entries:
        return None
    if sorted(entries) != list(range(len(entries))):
        return None
    return tuple(entries[index] for index in sorted(entries))


def _require_str(values: dict[str, PolicyScalar], key: str) -> str | None:
    value = values.get(key)
    return value if isinstance(value, str) and value else None


def _require_int(values: dict[str, PolicyScalar], key: str) -> int | None:
    value = values.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        return None
    return value


def _require_number(values: dict[str, PolicyScalar], key: str) -> float | None:
    value = values.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return float(value)


def _int_tuple(raw: tuple[PolicyScalar, ...] | None) -> tuple[int, ...] | None:
    if raw is None:
        return None
    if any(isinstance(item, bool) or not isinstance(item, int) for item in raw):
        return None
    return tuple(int(item) for item in raw)


def _float_tuple(raw: tuple[PolicyScalar, ...] | None) -> tuple[float, ...] | None:
    if raw is None:
        return None
    if any(
        isinstance(item, bool) or not isinstance(item, (int, float)) for item in raw
    ):
        return None
    return tuple(float(item) for item in raw)


def _str_tuple(raw: tuple[PolicyScalar, ...] | None) -> tuple[str, ...] | None:
    if raw is None:
        return None
    if any(not isinstance(item, str) or not item for item in raw):
        return None
    return tuple(str(item) for item in raw)


@dataclass(frozen=True)
class _ParsedFields:
    """형 검사를 통과한 원시 필드 한 벌 — `EvaluationPolicy.__post_init__`의 값
    불변식은 아직 거치지 않았다."""

    paired_t_threshold: float
    gate_baseline: str
    gate_model: str
    gate_stratum: str
    maturity_threshold: float
    min_evaluation_rows: int
    max_origins: int
    agency_baseline_min_count: int
    stability_seeds: tuple[int, ...]
    amount_band_edges: tuple[float, ...]
    segment_axes: tuple[str, ...]


def _parse_fields(values: dict[str, PolicyScalar]) -> _ParsedFields | None:
    """평탄 인덱스 목록 조립 + 스칼라 형 검사 — 형이 맞지 않으면 `None`(설계 래칫
    함수 길이 한도로 `load_evaluation_policy`에서 분리, verifier r1 L-5 형식)."""
    fields = (
        _require_number(values, "paired_t_threshold"),
        _require_str(values, "gate_baseline"),
        _require_str(values, "gate_model"),
        _require_str(values, "gate_stratum"),
        _require_number(values, "maturity_threshold"),
        _require_int(values, "min_evaluation_rows"),
        _require_int(values, "max_origins"),
        _require_int(values, "agency_baseline_min_count"),
        _int_tuple(_collect_indexed_list(values, "stability_seeds")),
        _float_tuple(_collect_indexed_list(values, "amount_band_edges")),
        _str_tuple(_collect_indexed_list(values, "segment_axes")),
    )
    if any(field is None for field in fields):
        return None
    return _assemble_parsed_fields(fields)


def _assemble_parsed_fields(
    fields: tuple[
        float | None,
        str | None,
        str | None,
        str | None,
        float | None,
        int | None,
        int | None,
        int | None,
        tuple[int, ...] | None,
        tuple[float, ...] | None,
        tuple[str, ...] | None,
    ],
) -> _ParsedFields:
    """`_parse_fields`가 `None` 없음을 확인한 뒤에만 부른다 — `cast`는 그 불변식을
    타입에 반영할 뿐 재검증하지 않는다(설계 래칫 함수 길이 한도로 분리)."""
    (
        paired_t_threshold,
        gate_baseline,
        gate_model,
        gate_stratum,
        maturity_threshold,
        min_evaluation_rows,
        max_origins,
        agency_baseline_min_count,
        stability_seeds,
        amount_band_edges,
        segment_axes,
    ) = fields
    return _ParsedFields(
        paired_t_threshold=cast(float, paired_t_threshold),
        gate_baseline=cast(str, gate_baseline),
        gate_model=cast(str, gate_model),
        gate_stratum=cast(str, gate_stratum),
        maturity_threshold=cast(float, maturity_threshold),
        min_evaluation_rows=cast(int, min_evaluation_rows),
        max_origins=cast(int, max_origins),
        agency_baseline_min_count=cast(int, agency_baseline_min_count),
        stability_seeds=cast("tuple[int, ...]", stability_seeds),
        amount_band_edges=cast("tuple[float, ...]", amount_band_edges),
        segment_axes=cast("tuple[str, ...]", segment_axes),
    )


def load_evaluation_policy(path: Path) -> EvaluationPolicy | PolicyRejected:
    """`path` 의 YAML 을 읽어 `EvaluationPolicy` 로 검증한다. 미지 키·값 불변식 위반은
    전부 `PolicyRejected`(예외로 새지 않는다, v2-지침서.md §5)."""
    try:
        raw = _load_raw_policy(path, known_keys=_KNOWN_KEYS)
    except (PolicyError, OSError, yaml.YAMLError) as exc:
        return PolicyRejected(PolicyRejectionReason.MALFORMED, str(exc))

    parsed = _parse_fields(raw.values)
    if parsed is None:
        return PolicyRejected(
            PolicyRejectionReason.INVALID_VALUE, f"malformed values: {raw.values!r}"
        )

    try:
        return EvaluationPolicy(
            version=raw.version,
            paired_t_threshold=parsed.paired_t_threshold,
            gate_baseline=parsed.gate_baseline,
            gate_model=parsed.gate_model,
            gate_stratum=parsed.gate_stratum,
            maturity_threshold=parsed.maturity_threshold,
            min_evaluation_rows=parsed.min_evaluation_rows,
            max_origins=parsed.max_origins,
            agency_baseline_min_count=parsed.agency_baseline_min_count,
            stability_seeds=parsed.stability_seeds,
            amount_band_edges=parsed.amount_band_edges,
            segment_axes=parsed.segment_axes,
        )
    except ValueError as exc:
        return PolicyRejected(PolicyRejectionReason.INVALID_VALUE, str(exc))


def policy_checksum(policy: EvaluationPolicy) -> str:
    """sha256 hex(소문자 64자) — 5B `features/manifest.py::canonical_json`·5C-1
    `spec_checksum`과 같은 규칙(키 정렬·구분자 `(",", ":")`·`allow_nan=False`). 값이
    전부 정책 불변식을 통과한 유한값이라 `CanonicalizationRejected` 갈래가 성립하지
    않는다 — 결과 타입이 아니라 `str` 을 낸다(5C-1 `spec_checksum`과 같은 판단)."""
    payload = json.dumps(
        {
            "version": policy.version,
            "paired_t_threshold": policy.paired_t_threshold,
            "gate_baseline": policy.gate_baseline,
            "gate_model": policy.gate_model,
            "gate_stratum": policy.gate_stratum,
            "maturity_threshold": policy.maturity_threshold,
            "min_evaluation_rows": policy.min_evaluation_rows,
            "max_origins": policy.max_origins,
            "agency_baseline_min_count": policy.agency_baseline_min_count,
            "stability_seeds": list(policy.stability_seeds),
            "amount_band_edges": list(policy.amount_band_edges),
            "segment_axes": list(policy.segment_axes),
        },
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()
