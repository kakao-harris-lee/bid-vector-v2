"""`ml_engine.features.schema` — versioned feature schema(신규, D-5B-1·D-5B-8).

legacy `AWARD_RATE_FEATURE_NAMES`(이름·순서만의 튜플)를 `FeatureSchema`(이름+kind+range+
missing 정책을 갖는 구조체)로 승격한다 — 「versioned feature schema」는 이식이 아니라
신규 작성이다(조사 §c-5 함의). `verify_feature_names`는 legacy predictor의 fail-closed
이름 대조(`artifact.feature_names != AWARD_RATE_FEATURE_NAMES` 체크, `award_rate_gbm.py:
200-206`)를 결과 타입으로 옮긴 것이다(원래는 `ValueError`). `require_declared`는 legacy
`sample_scope` 기본값 금지 규율(`artifact_contracts.py:200-208`)을 범용 헬퍼로 옮겼다.
"""

from __future__ import annotations

import math
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from enum import StrEnum
from types import MappingProxyType


class FeatureKind(StrEnum):
    """열이 범주(어휘 위치 코드)인지 연속값인지."""

    CATEGORICAL = "CATEGORICAL"
    CONTINUOUS = "CONTINUOUS"


class MissingPolicy(StrEnum):
    """결측을 이 열이 어떻게 흡수하는가 — `rows.py`가 이 표를 어긴 행을 만들면 안 된다는
    선언(강제는 rows.py의 몫, 이 값은 두 자리가 일치해야 한다는 계약)."""

    NAN_ON_MISSING = "NAN_ON_MISSING"
    REJECT_ROW_ON_MISSING = "REJECT_ROW_ON_MISSING"


@dataclass(frozen=True)
class ClosedRange:
    """닫힌 구간 — `math.inf`로 위/아래가 열린 쪽을 표현할 수 있다."""

    minimum: float
    maximum: float


@dataclass(frozen=True)
class FeatureColumn:
    name: str
    kind: FeatureKind
    range: ClosedRange | None
    missing: MissingPolicy


@dataclass(frozen=True)
class FeatureSchema:
    version: str
    columns: tuple[FeatureColumn, ...]


FEATURE_SCHEMA_V2: FeatureSchema = FeatureSchema(
    version="award-rate-features-v2",
    columns=(
        FeatureColumn(
            "category", FeatureKind.CATEGORICAL, None, MissingPolicy.NAN_ON_MISSING
        ),
        FeatureColumn(
            "log_amount",
            FeatureKind.CONTINUOUS,
            ClosedRange(0.0, math.inf),
            MissingPolicy.REJECT_ROW_ON_MISSING,
        ),
        FeatureColumn(
            "agency_encoding",
            FeatureKind.CONTINUOUS,
            ClosedRange(0.0, 1.0),
            MissingPolicy.NAN_ON_MISSING,
        ),
        FeatureColumn(
            "agency_sample_count",
            FeatureKind.CONTINUOUS,
            ClosedRange(0.0, math.inf),
            MissingPolicy.NAN_ON_MISSING,
        ),
        FeatureColumn(
            "denominator_source",
            FeatureKind.CATEGORICAL,
            None,
            MissingPolicy.REJECT_ROW_ON_MISSING,
        ),
    ),
)

SUPPORTED_FEATURE_SCHEMAS: Mapping[str, FeatureSchema] = MappingProxyType(
    {FEATURE_SCHEMA_V2.version: FEATURE_SCHEMA_V2}
)


@dataclass(frozen=True)
class UnsupportedSchema:
    """미지원 schema version — 예외가 아니라 결과 타입(서빙은 5E가 UNSUPPORTED_SCHEMA로 옮김)."""

    version: str


def resolve_schema(version: str) -> FeatureSchema | UnsupportedSchema:
    schema = SUPPORTED_FEATURE_SCHEMAS.get(version)
    return schema if schema is not None else UnsupportedSchema(version)


@dataclass(frozen=True)
class Verified:
    pass


@dataclass(frozen=True)
class NameMismatch:
    expected: tuple[str, ...]
    actual: tuple[str, ...]


def verify_feature_names(
    artifact_names: Sequence[str], schema: FeatureSchema
) -> Verified | NameMismatch:
    """legacy fail-closed 이름 대조(`award_rate_gbm.py:200-206`)를 결과 타입으로."""
    expected = tuple(column.name for column in schema.columns)
    actual = tuple(artifact_names)
    return Verified() if actual == expected else NameMismatch(expected, actual)


@dataclass(frozen=True)
class Undeclared:
    """`sample_scope` 등 기본값이 없어야 하는 필드가 `None`으로 온 상태(legacy
    `artifact_contracts.py:200-208` — 기본값을 주면 미학습 공종 가드가 조용히 열린다)."""

    field: str


def require_declared[T](field: str, value: T | None) -> T | Undeclared:
    """`value`가 `None`이면 `Undeclared(field)`, 아니면 그대로 통과(falsy 값도 통과)."""
    return Undeclared(field) if value is None else value
