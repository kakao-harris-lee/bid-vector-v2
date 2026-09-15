"""`ml_engine.training.release` — 결정적 release 식별(D-5C-10). UUID 를 쓰지 않는다 —
같은 입력의 두 학습이 같은 id 를 가져야 재현성 test(⑩)가 바이트 동일을 잴 수 있다.

`ReleaseIdentity`는 2B `ModelRelease`(다섯 성분: release_id·artifact_checksum·
feature_schema_version·code_version·dataset_id)에서 **`artifact_checksum`을 뺀 넷만**
갖는다(D-5C-9 — 아티팩트 바이트 안에 자기 checksum 을 넣지 않는다. checksum 은
`ArtifactBytes.sha256`이 낸다, `artifact_writer.py`).

verifier r1 H-1 — 이 모듈은 더 이상 호출자가 넘기는 `ReleaseInputs` 를 받지 않는다.
`derive_release_id`의 다섯 입력은 전부 `TrainedArtifact`(호출부는 `artifact_writer.py`)에서만
읽힌다 — 계약 우회 후보 (12)(「`release.dataset_id` 를 요청과 다르게」)가 시그니처 차원에서
성립하지 않는다."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass


@dataclass(frozen=True)
class ReleaseIdentity:
    """2B `ModelRelease`에서 `artifact_checksum`을 뺀 넷(D-5C-9)."""

    release_id: str
    feature_schema_version: str
    code_version: str
    dataset_id: str


def derive_release_id(
    *,
    dataset_id: str,
    training_spec_version: str,
    training_spec_checksum: str,
    seed: int,
    code_version: str,
) -> str:
    """sha256 앞 16 hex — 다섯 입력 중 하나만 바뀌어도 다른 id(결정적, D-5C-10). 값 자체의
    유효성(비어있음 등)은 각 값의 원 출처 타입이 이미 강제한다 — `dataset_id`는
    `DatasetManifestV1.__post_init__`, `code_version`은 `CodeVersion.__post_init__`,
    `training_spec_version`/`training_spec_checksum`은 유효한 `TrainingSpec`에서만 나온다."""
    payload = "\0".join(
        (
            dataset_id,
            training_spec_version,
            training_spec_checksum,
            str(seed),
            code_version,
        )
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]
