"""`ml_engine.training.dataset` — dataset 입구(fail-closed, D-5C-8·scope ①). legacy 에는
dataset manifest·checksum 개념이 없었다(조사 01 §5-4 「checksum 은 release manifest 층」) —
이 모듈은 이식이 아니라 신규 작성이다.

dataset 은 디렉터리 하나(`manifest.json` + `rows.jsonl`)다. `adapters/dataset_files.py`가
`file://` 경로에서 두 파일의 바이트를 읽어 오고, 이 모듈이 그 바이트를 검증한다 —
**거부 시 어떤 객체도 만들지 않는다**(2C ①② 「checksum 불일치는 job 안에서 FAILED」).

`rows.jsonl` 형식(신규, legacy 에 대응물 없음): 한 행 = 한 JSON 객체 —
``{"feature_inputs": <FeatureInputs 의 proto3 JSON>, "label": <float>,
"opened_at": <ISO-8601, tz 포함>, "stratum": <str>}``. `feature_inputs`는 2B
`FeatureInputs`(`base_amount`·`category_code`·`agency_id`·
`base_amount_provenance_label`, 각 `oneof {value, missing}`)의 표준 proto3 JSON 이다.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum

from google.protobuf import json_format  # type: ignore[import-untyped]

from ml_engine.contracts import features_pb2
from ml_engine.features import UnsupportedSchema, resolve_schema


@dataclass(frozen=True)
class DatasetReference:
    """2C `DatasetReference`(`uri`·`manifest_checksum`·`dataset_id`)에서 `load_dataset`이
    필요로 하는 두 성분만(`uri`는 어댑터가 이미 소비했다)."""

    dataset_id: str
    manifest_checksum: str


@dataclass(frozen=True)
class DatasetManifestV1:
    """`manifest.json`의 검증된 내용. 기본값 없음 — "안 넘겼다"와 "미공시"를 구별한다
    (legacy `artifact_contracts.py:200-208` 규율 계승)."""

    dataset_id: str
    sample_scope: str
    feed_origin_only: bool
    row_count: int
    rows_checksum: str
    opened_at_first: datetime
    opened_at_last: datetime
    feature_schema_version: str
    settlements_checksum: str = ""
    """M5/5E-1 D-5E-4 — `settlements.jsonl`(정산 관측, 5C-2 성숙도 입력의 실체) 세
    번째 파일의 checksum. **파일 로딩 경로(`_parse_manifest`)는 이 키를 필수로 요구한다**
    (미공시 = 거부) — 여기 기본값 `""`은 기존 5C-1 test fixture(이 필드를 모르는 채
    `DatasetManifestV1`을 직접 생성)와의 생성자 호환을 위한 것일 뿐, 파일 로딩 경로에서는
    도달하지 않는다(hunk 격리 — `dataset.py`·`dataset_files.py` 외 test 파일은 편집
    금지, `manifest_checksum`류 다른 필수 필드와 달리 `__post_init__`이 비어 있음을
    강제하지 않는 이유)."""

    def __post_init__(self) -> None:
        if not self.dataset_id:
            raise ValueError("dataset_id 는 비어 있을 수 없습니다.")
        if not self.sample_scope:
            raise ValueError("sample_scope 는 비어 있을 수 없습니다.")
        if self.row_count < 0:
            raise ValueError(f"row_count 는 음수일 수 없습니다: {self.row_count}")
        if not self.rows_checksum:
            raise ValueError("rows_checksum 은 비어 있을 수 없습니다.")
        if not self.feature_schema_version:
            raise ValueError("feature_schema_version 은 비어 있을 수 없습니다.")


@dataclass(frozen=True)
class RawTrainingRow:
    """`rows.jsonl` 한 줄의 구조적으로 유효한 파싱 결과 — fact·라벨의 **도메인** 검증은
    아직 하지 않았다(그것은 `training/corpus.py::admit_corpus`의 몫)."""

    feature_inputs: features_pb2.FeatureInputs
    label_value: float
    opened_at: datetime
    stratum: str


@dataclass(frozen=True)
class RawSettlementRow:
    """`settlements.jsonl` 한 줄(D-5E-4) — `{opened_at, settled}`. `inference.maturity.
    SettlementObservation`과 형태가 같지만 **다른 타입**이다 — `training`은 `inference`를
    import 할 수 없다(forbidden 계약). 조립 근(`ml_engine.app`)이 이 값을 그 타입으로
    옮긴다."""

    opened_at: datetime
    settled: bool


class DatasetRejectionReason(StrEnum):
    CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH"
    UNREADABLE = "UNREADABLE"
    SCHEMA_UNSUPPORTED = "SCHEMA_UNSUPPORTED"
    ID_MISMATCH = "ID_MISMATCH"
    ROWS_CHECKSUM_MISMATCH = "ROWS_CHECKSUM_MISMATCH"
    SETTLEMENTS_CHECKSUM_MISMATCH = "SETTLEMENTS_CHECKSUM_MISMATCH"


@dataclass(frozen=True)
class DatasetRejected:
    reason: DatasetRejectionReason
    detail: str


@dataclass(frozen=True)
class LoadedDataset:
    """`load_dataset`만 만든다 — 검증(checksum·id·schema)을 전부 통과한 뒤의 상태."""

    manifest: DatasetManifestV1
    raw_rows: tuple[RawTrainingRow, ...]
    settlement_rows: tuple[RawSettlementRow, ...] = ()
    """D-5E-4 — 파일 로딩 경로(`load_dataset`)만 채운다. 기본값 `()`은 5C-2
    `_holdout_fit.py`(out_of_scope, 무편집)의 기존 `LoadedDataset(manifest=…,
    raw_rows=…)` 호출과의 생성자 호환을 위한 것이다(hunk 격리)."""


type _ManifestScalar = str | int | float | bool | None
"""JSON 원시 값 — 스칼라 유니온이라 설계 래칫의 약한 경계 판정(`dict`/`Any`/`object`)에
걸리지 않는다(`is_weak_annotation`은 멤버별로 재귀 판정하고 구체 스칼라는 통과시킨다)."""


def _require_nonempty_string(value: _ManifestScalar, name: str) -> str:
    if not isinstance(value, str) or not value:
        raise TypeError(f"{name} 는 비어 있지 않은 문자열이어야 합니다: {value!r}")
    return value


def _require_bool(value: _ManifestScalar, name: str) -> bool:
    if not isinstance(value, bool):
        raise TypeError(f"{name} 는 boolean 이어야 합니다: {value!r}")
    return value


def _require_int(value: _ManifestScalar, name: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool):
        raise TypeError(f"{name} 는 정수여야 합니다: {value!r}")
    return value


def _optional_string(value: _ManifestScalar, name: str) -> str:
    """D-5E-4 `settlements_checksum` — 부재 시 `""`(`DatasetManifestV1` 필드 docstring
    참고, 선택적으로 읽고 `load_dataset`이 필요 시에만 강제한다)."""
    if value is None:
        return ""
    if not isinstance(value, str):
        raise TypeError(f"{name} 는 문자열이어야 합니다: {value!r}")
    return value


def _parse_manifest(manifest_bytes: bytes) -> DatasetManifestV1 | DatasetRejected:
    """code-reviewer PR #13 MEDIUM-1 — 문자열·boolean·정수 필드의 **타입**을 `_require_*`
    헬퍼(스칼라 매개변수, 설계 래칫 약한 경계 판정 밖)로 강제한다."""
    try:
        raw = json.loads(manifest_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        return DatasetRejected(DatasetRejectionReason.UNREADABLE, f"manifest: {exc}")
    if not isinstance(raw, dict):
        return DatasetRejected(
            DatasetRejectionReason.UNREADABLE, "manifest: 최상위는 객체여야 합니다"
        )
    try:
        for key in (
            "dataset_id",
            "sample_scope",
            "rows_checksum",
            "feature_schema_version",
        ):
            _require_nonempty_string(raw[key], key)
        feed_origin_only = _require_bool(raw["feed_origin_only"], "feed_origin_only")
        row_count = _require_int(raw["row_count"], "row_count")
        settlements_checksum = _optional_string(
            raw.get("settlements_checksum"), "settlements_checksum"
        )
        return DatasetManifestV1(
            dataset_id=raw["dataset_id"],
            sample_scope=raw["sample_scope"],
            feed_origin_only=feed_origin_only,
            row_count=row_count,
            rows_checksum=raw["rows_checksum"],
            opened_at_first=datetime.fromisoformat(raw["opened_at_first"]),
            opened_at_last=datetime.fromisoformat(raw["opened_at_last"]),
            feature_schema_version=raw["feature_schema_version"],
            settlements_checksum=settlements_checksum,
        )
    except (KeyError, TypeError, ValueError) as exc:
        return DatasetRejected(DatasetRejectionReason.UNREADABLE, f"manifest: {exc}")


def _parse_row(line: str) -> RawTrainingRow | None:
    try:
        raw = json.loads(line)
        if not isinstance(raw, dict):
            return None
        feature_inputs = features_pb2.FeatureInputs()
        json_format.ParseDict(raw["feature_inputs"], feature_inputs)
        opened_at = datetime.fromisoformat(raw["opened_at"])
        if opened_at.tzinfo is None:
            return None
        stratum = raw["stratum"]
        if not isinstance(stratum, str) or not stratum:
            return None
        label_value = raw["label"]
        if not isinstance(label_value, (int, float)) or isinstance(label_value, bool):
            return None
        return RawTrainingRow(
            feature_inputs=feature_inputs,
            label_value=float(label_value),
            opened_at=opened_at,
            stratum=stratum,
        )
    except (
        json.JSONDecodeError,
        json_format.ParseError,
        KeyError,
        TypeError,
        ValueError,
    ):
        return None


def _parse_settlement_row(line: str) -> RawSettlementRow | None:
    """`settlements.jsonl` 한 줄 — `{opened_at, settled}`(D-5E-4)."""
    try:
        raw = json.loads(line)
        if not isinstance(raw, dict):
            return None
        opened_at = datetime.fromisoformat(raw["opened_at"])
        if opened_at.tzinfo is None:
            return None
        settled = raw["settled"]
        if not isinstance(settled, bool):
            return None
        return RawSettlementRow(opened_at=opened_at, settled=settled)
    except (json.JSONDecodeError, KeyError, TypeError, ValueError):
        return None


def _parse_settlements(
    settlements_bytes: bytes,
) -> tuple[RawSettlementRow, ...] | DatasetRejected:
    try:
        text = settlements_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        return DatasetRejected(DatasetRejectionReason.UNREADABLE, f"settlements: {exc}")
    rows: list[RawSettlementRow] = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        parsed = _parse_settlement_row(stripped)
        if parsed is None:
            return DatasetRejected(
                DatasetRejectionReason.UNREADABLE,
                f"settlements: 파싱 실패한 행 — {stripped[:80]!r}",
            )
        rows.append(parsed)
    return tuple(rows)


def _parse_rows(rows_bytes: bytes) -> tuple[RawTrainingRow, ...] | DatasetRejected:
    try:
        text = rows_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        return DatasetRejected(DatasetRejectionReason.UNREADABLE, f"rows: {exc}")
    rows: list[RawTrainingRow] = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        parsed = _parse_row(stripped)
        if parsed is None:
            return DatasetRejected(
                DatasetRejectionReason.UNREADABLE,
                f"rows: 파싱 실패한 행 — {stripped[:80]!r}",
            )
        rows.append(parsed)
    return tuple(rows)


def _load_settlements(
    manifest: DatasetManifestV1, settlements_bytes: bytes
) -> tuple[RawSettlementRow, ...] | DatasetRejected:
    """D-5E-4 — settlements checksum 대조 → 파싱(`load_dataset`을 50줄 안에 두려는
    분리, design ratchet)."""
    if not manifest.settlements_checksum:
        return DatasetRejected(
            DatasetRejectionReason.SETTLEMENTS_CHECKSUM_MISMATCH,
            "manifest 에 settlements_checksum 미공시",
        )
    actual_settlements_checksum = hashlib.sha256(settlements_bytes).hexdigest()
    if actual_settlements_checksum != manifest.settlements_checksum:
        return DatasetRejected(
            DatasetRejectionReason.SETTLEMENTS_CHECKSUM_MISMATCH,
            actual_settlements_checksum,
        )
    return _parse_settlements(settlements_bytes)


def _validate_manifest(
    manifest_bytes: bytes, expected: DatasetReference
) -> DatasetManifestV1 | DatasetRejected:
    """manifest checksum → 파싱 → dataset_id 대조(`load_dataset`을 50줄 안에 두려는
    분리, design ratchet)."""
    actual_manifest_checksum = hashlib.sha256(manifest_bytes).hexdigest()
    if actual_manifest_checksum != expected.manifest_checksum:
        return DatasetRejected(
            DatasetRejectionReason.CHECKSUM_MISMATCH, actual_manifest_checksum
        )
    manifest = _parse_manifest(manifest_bytes)
    if isinstance(manifest, DatasetRejected):
        return manifest
    if manifest.dataset_id != expected.dataset_id:
        return DatasetRejected(DatasetRejectionReason.ID_MISMATCH, manifest.dataset_id)
    return manifest


def _validate_rows(
    rows_bytes: bytes, manifest: DatasetManifestV1
) -> tuple[RawTrainingRow, ...] | DatasetRejected:
    """rows checksum → schema 지원 여부 → row 파싱 → row_count 대조(같은 분리 사유)."""
    actual_rows_checksum = hashlib.sha256(rows_bytes).hexdigest()
    if actual_rows_checksum != manifest.rows_checksum:
        return DatasetRejected(
            DatasetRejectionReason.ROWS_CHECKSUM_MISMATCH, actual_rows_checksum
        )
    schema = resolve_schema(manifest.feature_schema_version)
    if isinstance(schema, UnsupportedSchema):
        return DatasetRejected(
            DatasetRejectionReason.SCHEMA_UNSUPPORTED, manifest.feature_schema_version
        )
    raw_rows = _parse_rows(rows_bytes)
    if isinstance(raw_rows, DatasetRejected):
        return raw_rows
    if len(raw_rows) != manifest.row_count:
        return DatasetRejected(
            DatasetRejectionReason.UNREADABLE,
            f"ROW_COUNT_MISMATCH: manifest={manifest.row_count} actual={len(raw_rows)}",
        )
    return raw_rows


def load_dataset(
    manifest_bytes: bytes,
    rows_bytes: bytes,
    expected: DatasetReference,
    settlements_bytes: bytes | None = None,
) -> LoadedDataset | DatasetRejected:
    """dataset 유일 진입점(D-5C-8). 순서: manifest 검증(`_validate_manifest`) → rows
    검증(`_validate_rows`) → (D-5E-4) `settlements_bytes`가 주어지면 settlements
    checksum 대조 → 파싱.

    `settlements_bytes=None`(기본값)은 5E-1 이전 호출자(2C 이하 test)와의 호환
    자리다 — D-5E-4 파이프라인(`ml_engine.app`)만 이 인자를 채운다."""
    manifest = _validate_manifest(manifest_bytes, expected)
    if isinstance(manifest, DatasetRejected):
        return manifest

    raw_rows = _validate_rows(rows_bytes, manifest)
    if isinstance(raw_rows, DatasetRejected):
        return raw_rows

    settlement_rows: tuple[RawSettlementRow, ...] = ()
    if settlements_bytes is not None:
        loaded_settlements = _load_settlements(manifest, settlements_bytes)
        if isinstance(loaded_settlements, DatasetRejected):
            return loaded_settlements
        settlement_rows = loaded_settlements

    return LoadedDataset(
        manifest=manifest, raw_rows=raw_rows, settlement_rows=settlement_rows
    )
