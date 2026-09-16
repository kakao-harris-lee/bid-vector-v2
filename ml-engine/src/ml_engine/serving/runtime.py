"""`ml_engine.serving.runtime` — `PredictionRuntime`(D-5E2-1) + `build_derived_release`
(D-5E2-2~4). `ModelRelease`는 엔진 결과가 아니라 **런타임 상수**(정책 + code_version)다
— `Success`에 release 필드가 없고(`ml_engine.inference.results`), `DistributionRelease`
(엔진 내부 타입)의 생산자가 없다(grep 0). 조립 근(`ml_engine.app.server`)이 preload
성공 시 `build_derived_release`를 **한 번** 불러 `PredictionRuntime`을 만들고, 요청마다
재계산하지 않는다 — 같은 정책은 항상 같은 release(결정적, test 가 확인).

`PredictionRuntime.release`는 `GetModelMetadata.promoted`와 `CalculateOptimalBidResponse
.success.release` 양쪽이 **같은 객체의 복사본**(`CopyFrom`)을 쓴다(설계 검토 (1) 「한
함수 한 입력」) — 두 자리가 다른 코드 경로로 벌어질 수 없다."""

from __future__ import annotations

import dataclasses
import hashlib
import json
from dataclasses import dataclass
from decimal import Decimal

from ml_engine.contracts import prediction_pb2
from ml_engine.features import SUPPORTED_FEATURE_SCHEMAS
from ml_engine.inference.policy import InferencePolicy

_DERIVED_RELEASE_ID_PREFIX = "distribution/"

# D-5E2-8 — DERIVED release 의 feature_schema_version 은 5B 지원 집합의 값을 쓴다(분포
# 엔진은 5B 피처 벡터를 쓰지 않지만, wire FeatureInputs 해석 규약을 나르는 축으로 유지한다는
# 승계 결정, 5E-1 ③). 지원 집합이 지금 유일하기 때문에 그 값을 그대로 쓸 수 있다 — 둘
# 이상이 되면 "어느 것을 promoted 의 release 에 실을지"가 더는 자명하지 않아 이 상수
# 선택 자체를 재검토해야 한다(조용히 `next(iter(...))`로 넘기지 않는다, 값 지어내기 금지).
if len(SUPPORTED_FEATURE_SCHEMAS) != 1:
    raise AssertionError(
        "SUPPORTED_FEATURE_SCHEMAS 가 하나가 아니다 — DERIVED release 의 "
        "feature_schema_version 선택 규칙(D-5E2-8)을 재검토해야 한다: "
        f"{sorted(SUPPORTED_FEATURE_SCHEMAS)}"
    )
_DERIVED_FEATURE_SCHEMA_VERSION = next(iter(SUPPORTED_FEATURE_SCHEMAS))


type _PolicyJsonValue = str | int | list["_PolicyJsonValue"]


def _jsonable(
    value: Decimal | tuple[Decimal, ...] | tuple[int, ...] | int | str,
) -> _PolicyJsonValue:
    """`InferencePolicy` 필드 값을 canonical JSON 이 다룰 수 있는 형태로 — `Decimal`은
    문자열로(정밀도 보존, `json.dumps`가 float 로 반올림하지 않게), `tuple`은 재귀적으로
    `list`로. 반환 타입을 재귀 별칭으로 좁혀 `Any`/약한 경계를 쓰지 않는다(design
    ratchet)."""
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, tuple):
        return [_jsonable(item) for item in value]
    return value


def derived_release_checksum(policy: InferencePolicy) -> str:
    """sha256 hex(소문자 64자, `sha256:` 접두 없음) — 5C-2
    `evaluation/policy.py::policy_checksum`과 같은 규칙(canonical JSON: 키 정렬,
    구분자 `(",", ":")`, `allow_nan=False`, D-5E2-4). 필드는 `dataclasses.fields`로
    **기계 열거**한다(손 목록 금지) — `InferencePolicy`에 필드가 늘어나도 이 함수가
    낡지 않는다. 정책 값이 하나라도 바뀌면 checksum 이 바뀐다(test)."""
    payload = {
        field.name: _jsonable(getattr(policy, field.name))
        for field in dataclasses.fields(policy)
    }
    encoded = json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    )
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def build_derived_release(
    policy: InferencePolicy, code_version: str
) -> prediction_pb2.ModelRelease:
    """DERIVED release 채움 규약(D-2F-2, D-5E2-2~4) — `release_id =
    "distribution/<policy.version>"`(proto 주석 규약, 2F testdata 의 특정 문자열은
    예시일 뿐 규약이 아니다) · `artifact_checksum = "sha256:" + derived_release_checksum`
    · `feature_schema_version`은 5B 지원 집합의 값(위) · `code_version`은 호출자가
    준다(D-5E2-3, 환경 `ML_ENGINE_CODE_VERSION` 단일 출처 — 이 함수는 그 출처를 모른다)
    · `dataset_id = ""`(아티팩트 없는 release, D-2F-2가 DERIVED 에서만 허용) ·
    `release_kind = RELEASE_KIND_DERIVED`."""
    if not code_version:
        raise ValueError("code_version 은 비어 있을 수 없다(D-5E2-3).")
    release = prediction_pb2.ModelRelease()
    release.release_id = f"{_DERIVED_RELEASE_ID_PREFIX}{policy.version}"
    release.artifact_checksum = f"sha256:{derived_release_checksum(policy)}"
    release.feature_schema_version = _DERIVED_FEATURE_SCHEMA_VERSION
    release.code_version = code_version
    release.dataset_id = ""
    release.release_kind = prediction_pb2.RELEASE_KIND_DERIVED
    return release


@dataclass(frozen=True)
class PredictionRuntime:
    """preload 성공 시 조립 근(`ml_engine.app.server`)이 **한 번** 만든다(D-5E2-1) —
    요청마다 재계산하지 않는다. `release`는 스냅샷이다 — 호출자는 `CopyFrom`으로만
    옮겨야 한다(원본을 그대로 반환·직접 참조하지 않는다, (2b) 경계). `PredictionRuntime`
    자체와 `BidPredictionServicer(runtime=)` 주입은 (2b) 표의 「경계로 처리」 행 —
    유일 호출자는 `app/server.py`다(실측)."""

    policy: InferencePolicy
    release: prediction_pb2.ModelRelease
    supported_feature_schema_versions: tuple[str, ...]
