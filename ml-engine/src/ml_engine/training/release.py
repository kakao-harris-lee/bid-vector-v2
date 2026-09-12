"""`ml_engine.training.release` — 결정적 release 식별(D-5C-10). UUID 를 쓰지 않는다 —
같은 입력의 두 학습이 같은 id 를 가져야 재현성 test(⑩)가 바이트 동일을 잴 수 있다.

`ReleaseIdentity`는 2B `ModelRelease`(다섯 성분: release_id·artifact_checksum·
feature_schema_version·code_version·dataset_id)에서 **`artifact_checksum`을 뺀 넷만**
갖는다(D-5C-9 — 아티팩트 바이트 안에 자기 checksum 을 넣지 않는다. checksum 은
`ArtifactBytes.sha256`이 낸다, `artifact_writer.py`)."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass


@dataclass(frozen=True)
class ReleaseInputs:
    """`derive_release_id`의 입력 다섯. `code_version`은 호출자가 문자열로 준다 — 이
    모듈은 git 을 호출하지 않는다(D-5C-2 인계, 「만들지 않는 것」)."""

    dataset_id: str
    training_spec_version: str
    training_spec_checksum: str
    seed: int
    code_version: str

    def __post_init__(self) -> None:
        if not self.dataset_id:
            raise ValueError("dataset_id 는 비어 있을 수 없습니다.")
        if not self.training_spec_version:
            raise ValueError("training_spec_version 은 비어 있을 수 없습니다.")
        if not self.training_spec_checksum:
            raise ValueError("training_spec_checksum 은 비어 있을 수 없습니다.")
        if not self.code_version or not self.code_version.strip():
            raise ValueError("code_version 은 비어 있거나 공백일 수 없습니다.")


@dataclass(frozen=True)
class ReleaseIdentity:
    """2B `ModelRelease`에서 `artifact_checksum`을 뺀 넷(D-5C-9)."""

    release_id: str
    feature_schema_version: str
    code_version: str
    dataset_id: str


def derive_release_id(inputs: ReleaseInputs) -> str:
    """sha256 앞 16 hex — 다섯 입력 중 하나만 바뀌어도 다른 id(결정적, D-5C-10)."""
    payload = "\0".join(
        (
            inputs.dataset_id,
            inputs.training_spec_version,
            inputs.training_spec_checksum,
            str(inputs.seed),
            inputs.code_version,
        )
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]
